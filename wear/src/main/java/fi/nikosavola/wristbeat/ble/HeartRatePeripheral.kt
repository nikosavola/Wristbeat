package fi.nikosavola.wristbeat.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import java.util.UUID

private const val TAG = "HeartRatePeripheral"

/**
 * The advertiser, as this class uses it.
 *
 * An interface rather than the platform type because a BluetoothLeAdvertiser cannot be built outside
 * the Bluetooth stack, so a JVM test that wants to drive the callbacks below has nothing to put in
 * its place. Naming the two calls also keeps the choice between the platform's two overloads - one
 * with a scan response to carry the device name and one without - in one place.
 */
internal interface Advertiser {
  fun start(
    settings: AdvertiseSettings,
    data: AdvertiseData,
    scanResponse: AdvertiseData?,
    callback: AdvertiseCallback,
  )

  fun stop(callback: AdvertiseCallback)
}

/** Suppressed for the same reason [HeartRatePeripheral] is: one of these is only ever built from an
 * adapter the peripheral reached after checking the permissions it needs to touch it. */
@SuppressLint("MissingPermission")
private class PlatformAdvertiser(private val advertiser: BluetoothLeAdvertiser) : Advertiser {
  override fun start(
    settings: AdvertiseSettings,
    data: AdvertiseData,
    scanResponse: AdvertiseData?,
    callback: AdvertiseCallback,
  ) {
    if (scanResponse == null) {
      advertiser.startAdvertising(settings, data, callback)
    } else {
      advertiser.startAdvertising(settings, data, scanResponse, callback)
    }
  }

  override fun stop(callback: AdvertiseCallback) = advertiser.stopAdvertising(callback)
}

/**
 * Presents the watch as a standard BLE heart rate sensor: a GATT server holding the Heart Rate
 * Service, plus an advertisement carrying that service's UUID, so receivers can find it by service
 * rather than by device name.
 *
 * Not thread-safe by design: the BluetoothGattServer and advertiser callbacks both arrive on the
 * main thread, so this is only ever touched from there.
 *
 * The class-wide MissingPermission suppression rests on one invariant: every Bluetooth call below
 * happens while [gattServer] or [advertiser] is non-null, and both are only assigned after [start]
 * has verified the permissions. Lint cannot follow the check out of [BroadcastPermissions] to see
 * that, and it cannot see it through the two seams above either, which is why [PlatformAdvertiser]
 * carries the same suppression.
 */
@SuppressLint("MissingPermission")
internal class HeartRatePeripheral(
  context: Context,
  /** Called with the full set of subscribed centrals whenever it changes. */
  private val onDevicesChanged: (List<ConnectedDevice>) -> Unit,
  /** Called when the peripheral breaks after [start] has already reported success: advertising
   * dying, or the GATT service failing to register. Mirrors [start]'s return type. */
  private val onFailed: (BroadcastRejection) -> Unit,
  /** Monotonic, for stamping when a central subscribed. */
  private val clock: () -> Long = SystemClock::elapsedRealtime,
  /**
   * Where the GATT server comes from. Injectable because Robolectric will hand one back whose
   * callbacks can then be driven by hand, which is the only way to exercise the request handling
   * below without a radio.
   */
  private val openGattServer: (Context, BluetoothGattServerCallback) -> BluetoothGattServer? =
    { ctx, callback ->
      ctx.getSystemService(BluetoothManager::class.java)?.openGattServer(ctx, callback)
    },
  /** Where the advertiser comes from, for the same reason [openGattServer] is a parameter. */
  private val advertiserFor: (BluetoothAdapter) -> Advertiser? = {
    it.bluetoothLeAdvertiser?.let(::PlatformAdvertiser)
  },
) {
  private val appContext = context.applicationContext
  private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)

  private var gattServer: BluetoothGattServer? = null
  private var advertiser: Advertiser? = null

  /** Centrals with notifications enabled, against the details to show for each. BluetoothDevice
   * equality is by address, so the map keeps one entry per receiver across repeated subscribe and
   * unsubscribe writes. */
  private val subscribed = SubscribedCentrals<BluetoothDevice> { device -> describe(device) }

  /** Last reading from the sensor, or null before the first one arrives. */
  private var lastBpm: Int? = null

  /** Cleared when a scan response turns out not to fit, so the retry omits the device name. */
  private var includeDeviceName = true

  private val measurement =
    BluetoothGattCharacteristic(
        HeartRateProfile.MEASUREMENT,
        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ,
      )
      .apply {
        addDescriptor(
          BluetoothGattDescriptor(
            HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
          )
        )
      }

  // A GATT server's static characteristic value has no non-deprecated setter: the API 33
  // replacement is passing the value to the read/write calls, which is a client-side concept.
  @Suppress("DEPRECATION")
  private val bodySensorLocation =
    BluetoothGattCharacteristic(
        HeartRateProfile.BODY_SENSOR_LOCATION,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
      )
      .apply { value = byteArrayOf(HeartRateProfile.BODY_SENSOR_LOCATION_WRIST) }

  /** Starts advertising and serving. Returns null on success, otherwise why it could not start.
   * Failures after this returns arrive through [onFailed] instead. */
  fun start(): BroadcastRejection? {
    if (gattServer != null) return null
    // Verified here, not just by the caller, so this class is safe to start on its own and the
    // class-wide suppression above holds.
    if (BroadcastPermissions.missing(appContext, BroadcastPermissions.bluetooth).isNotEmpty()) {
      return BroadcastRejection.MissingPermission
    }

    val bluetooth = manager ?: return BroadcastRejection.BluetoothOff
    val adapter = bluetooth.adapter ?: return BroadcastRejection.BluetoothOff
    if (!adapter.isEnabled) return BroadcastRejection.BluetoothOff
    // The peripheral role is optional in the Bluetooth spec and some hardware omits it. Asking
    // first turns "advertising silently never starts" into a reason the user can act on.
    if (!adapter.isMultipleAdvertisementSupported) return BroadcastRejection.PeripheralUnavailable
    val advertiser = advertiserFor(adapter) ?: return BroadcastRejection.PeripheralUnavailable

    val server = openGattServer(appContext, serverCallback)
      ?: return BroadcastRejection.PeripheralUnavailable
    val service =
      BluetoothGattService(HeartRateProfile.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
    service.addCharacteristic(measurement)
    service.addCharacteristic(bodySensorLocation)
    if (!server.addService(service)) {
      server.close()
      return BroadcastRejection.PeripheralUnavailable
    }

    gattServer = server
    this.advertiser = advertiser
    includeDeviceName = true
    // Starting an advertisement reports nothing, so a failure here can only arrive later through
    // the callback, and through onFailed.
    advertise()
    return null
  }

  /** Sends one measurement to every subscribed central. Readings with nobody listening are dropped
   * rather than queued: a stale pulse is worth less than the next one. */
  fun send(bpm: Int) {
    lastBpm = bpm
    val payload = HeartRateProfile.measurementPayload(bpm)
    for (device in receivers()) notify(device, payload)
  }

  fun stop() {
    advertiser?.stop(advertiseCallback)
    advertiser = null
    gattServer?.let { server ->
      server.clearServices()
      server.close()
    }
    gattServer = null
    includeDeviceName = true
    lastBpm = null
    if (synchronized(subscribed) { subscribed.clear() }) onDevicesChanged(emptyList())
  }

  private fun receivers(): List<BluetoothDevice> =
    synchronized(subscribed) { subscribed.receivers() }

  private fun connectedDevices(): List<ConnectedDevice> =
    synchronized(subscribed) { subscribed.devices() }

  /** Reads the central's identity as far as the platform knows it. A peripheral learns a central's
   * name from the local bond or cache rather than from the connection, so an unbonded central
   * usually has none: that is why the address, which is always there, is the second line of the
   * row it ends up in. */
  private fun describe(device: BluetoothDevice) =
    ConnectedDevice(
      name = device.name ?: device.alias,
      address = device.address,
      connectedAtElapsedMillis = clock(),
    )

  private fun notify(device: BluetoothDevice, payload: ByteArray) {
    val server = gattServer ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      server.notifyCharacteristicChanged(device, measurement, false, payload)
    } else {
      @Suppress("DEPRECATION")
      measurement.value = payload
      @Suppress("DEPRECATION")
      server.notifyCharacteristicChanged(device, measurement, false)
    }
  }

  private fun advertise() {
    val advertiser = advertiser ?: return
    val settings =
      AdvertiseSettings.Builder()
        // BALANCED rather than LOW_LATENCY: on the emulator, a continuously scanning receiver found
        // the watch just as fast either way (117-228ms vs 124-199ms over five trials), because the
        // scanner's own duty cycle is the bottleneck once the advertiser is reasonably quick.
        // BALANCED advertises every ~238ms against LOW_LATENCY's ~96ms, so it costs well under half
        // the radio time for the same practical discoverability. LOW_POWER was measurably worse and
        // more variable (186-879ms), which a receiver scanning in short bursts could miss.
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        // Left at maximum: the emulator synthesises RSSI, so there is no way to measure what a
        // lower level would cost in range here, and a wrist is not always near the receiver.
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .setTimeout(0)
        .build()
    // The device name never fits alongside anything else in the 31-byte advertisement, so it rides
    // in the scan response. Receivers filtering by name see it only after an active scan; receivers
    // filtering by service UUID see it either way.
    val data =
      AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(HeartRateProfile.SERVICE))
        .setIncludeDeviceName(false)
        .build()
    val scanResponse =
      if (includeDeviceName) AdvertiseData.Builder().setIncludeDeviceName(true).build() else null
    advertiser.start(settings, data, scanResponse, advertiseCallback)
  }

  private val advertiseCallback =
    object : AdvertiseCallback() {
      override fun onStartSuccess(settingsInEffect: AdvertiseSettings) = Unit

      override fun onStartFailure(errorCode: Int) {
        if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE && includeDeviceName) {
          Log.w(TAG, "scan response too large, retrying without the device name")
          includeDeviceName = false
          advertise()
          return
        }
        Log.w(TAG, "advertising failed (error $errorCode)")
        onFailed(BroadcastRejection.AdvertiseFailed)
      }
    }

  private val serverCallback =
    object : BluetoothGattServerCallback() {
      override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        if (newState != BluetoothProfile.STATE_DISCONNECTED) return
        if (synchronized(subscribed) { subscribed.remove(device) }) {
          onDevicesChanged(connectedDevices())
        }
      }

      override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        // addService returns whether the call was accepted, not whether the stack registered the
        // service. Without this the app would report success while no receiver could find the
        // heart rate service at all.
        if (status == BluetoothGatt.GATT_SUCCESS) return
        Log.w(TAG, "service ${service.uuid} failed to register (status $status)")
        onFailed(BroadcastRejection.PeripheralUnavailable)
      }

      override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
      ) {
        val value = GattReplies.characteristicRead(characteristic.uuid, lastBpm)
        respond(device, requestId, offset, value)
      }

      override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
      ) {
        val isSubscribed = synchronized(subscribed) { subscribed.contains(device) }
        val value = GattReplies.descriptorRead(descriptor.uuid, isSubscribed)
        respond(device, requestId, offset, value)
      }

      override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
      ) {
        val outcome = GattReplies.descriptorWrite(descriptor.uuid, preparedWrite, value)
        if (outcome == DescriptorWrite.Refused) {
          if (responseNeeded) {
            writeResponse(device, requestId, offset, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED)
          }
          return
        }
        val nowSubscribed = outcome == DescriptorWrite.Subscribe
        val changed =
          synchronized(subscribed) {
            if (nowSubscribed) subscribed.add(device) else subscribed.remove(device)
          }
        if (responseNeeded) {
          writeResponse(device, requestId, offset, BluetoothGatt.GATT_SUCCESS)
        }
        // Send the last reading straight away so a receiver that just connected shows a number
        // instead of waiting for the next sensor event. Skipped when there is no reading yet: a
        // fabricated zero would show as a stopped pulse, and the real one lands within a second.
        val last = lastBpm
        if (changed && nowSubscribed && last != null) {
          notify(device, HeartRateProfile.measurementPayload(last))
        }
        if (changed) onDevicesChanged(connectedDevices())
      }

      /** Sends the answer to a read of [value], where a null [value] names nothing this peripheral
       * serves. */
      private fun respond(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        value: ByteArray?,
      ) {
        val reply = GattReplies.read(offset, value)
        gattServer?.sendResponse(device, requestId, reply.status, offset, reply.body)
      }

      /** Sends the answer to a write, which carries no value whichever way it went. */
      private fun writeResponse(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        status: Int,
      ) {
        gattServer?.sendResponse(device, requestId, status, offset, null)
      }
    }
}

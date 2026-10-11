package fi.nikosavola.wristbeat.ble

import android.app.Application
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
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBluetoothDevice

/**
 * The peripheral, driven through the two stack pieces it is given.
 *
 * Robolectric hands back a real GATT server whose callbacks can be called by hand, so the request
 * handling - subscribing, unsubscribing, disconnecting, reading - is exercised here rather than only
 * on a watch. The advertiser is a recording stand-in, because the platform's cannot be built outside
 * the stack.
 *
 * Two things this cannot see. It does not record a notification, so "who a reading goes to" is
 * covered by [SubscribedCentralsTest] and the payload it carries by [HeartRateProfileTest], while
 * that a reading reaches a real receiver is on the emulator. And it does not record the status a
 * response was sent with, which is why the rules that choose one live in [GattReplies] and are tested
 * there.
 */
@RunWith(AndroidJUnit4::class)
class HeartRatePeripheralTest {

  private val context: Application = ApplicationProvider.getApplicationContext()
  private val manager = requireNotNull(context.getSystemService(BluetoothManager::class.java))
  private val adapter = requireNotNull(manager.adapter)

  private val laptop = device("AA:AA:AA:AA:AA:AA", name = "Laptop")
  private val bike = device("BB:BB:BB:BB:BB:BB")

  private val advertiser = RecordingAdvertiser()

  /** Every set of receivers the peripheral has published, in order. */
  private val published = mutableListOf<List<ConnectedDevice>>()
  private val failures = mutableListOf<BroadcastRejection>()

  private var requestId = 1
  private var refuseToOpenServer = false
  private var noAdvertiser = false
  private var nowMillis = 1_000L
  private var callback: BluetoothGattServerCallback? = null
  private var server: BluetoothGattServer? = null

  @Before
  fun setUp() {
    shadowOf(context).grantPermissions(*BroadcastPermissions.required.toTypedArray())
    shadowOf(adapter).setEnabled(true)
    shadowOf(adapter).setIsMultipleAdvertisementSupported(true)
  }

  private fun peripheral(): HeartRatePeripheral =
    HeartRatePeripheral(
      context = context,
      onDevicesChanged = { published += it },
      onFailed = { failures += it },
      clock = { nowMillis },
      openGattServer = { ctx, opened ->
        callback = opened
        if (refuseToOpenServer) null else manager.openGattServer(ctx, opened).also { server = it }
      },
      advertiserFor = { if (noAdvertiser) null else advertiser },
    )

  /** A peripheral that has started, which is the state most of these are about. */
  private fun started(): HeartRatePeripheral {
    val peripheral = peripheral()
    assertNull("the session did not start", peripheral.start())
    return peripheral
  }

  private val requests: BluetoothGattServerCallback
    get() = requireNotNull(callback) { "the peripheral never opened a server" }

  private val answers: List<ByteArray?>
    get() = shadowOf(requireNotNull(server) { "the peripheral never opened a server" }).responses

  // --- starting ---------------------------------------------------------------------------------

  @Test
  fun `a watch without the Bluetooth permissions cannot be a peripheral`() {
    shadowOf(context).denyPermissions(*BroadcastPermissions.required.toTypedArray())
    assertEquals(BroadcastRejection.MissingPermission, peripheral().start())
  }

  @Test
  fun `a watch with Bluetooth off says so rather than advertising into nothing`() {
    shadowOf(adapter).setEnabled(false)
    assertEquals(BroadcastRejection.BluetoothOff, peripheral().start())
  }

  @Test
  fun `a watch that cannot advertise says so, rather than never starting quietly`() {
    // The peripheral role is optional in the Bluetooth spec, so this is the difference between a
    // button that does nothing and a reason the wearer can act on.
    shadowOf(adapter).setIsMultipleAdvertisementSupported(false)
    assertEquals(BroadcastRejection.PeripheralUnavailable, peripheral().start())
  }

  @Test
  fun `a watch with no advertiser says so`() {
    noAdvertiser = true
    assertEquals(BroadcastRejection.PeripheralUnavailable, peripheral().start())
  }

  @Test
  fun `a GATT server that will not open is reported rather than served`() {
    refuseToOpenServer = true
    assertEquals(BroadcastRejection.PeripheralUnavailable, peripheral().start())
  }

  @Test
  fun `a session that starts advertises the heart rate service, and not the device name`() {
    // By UUID so a receiver finds it without a name, and without the name in the advertisement
    // because it does not fit there: it rides in the scan response instead.
    started()
    val advertisement = advertiser.only
    assertEquals(listOf(ParcelUuid(HeartRateProfile.SERVICE)), advertisement.data.serviceUuids)
    assertFalse(advertisement.data.includeDeviceName)
    assertTrue(requireNotNull(advertisement.scanResponse).includeDeviceName)
  }

  @Test
  fun `the advertisement is balanced, at full power, connectable and open-ended`() {
    // The four choices the settings make, each for a reason that was measured rather than guessed.
    started()
    val settings = advertiser.only.settings
    assertEquals(AdvertiseSettings.ADVERTISE_MODE_BALANCED, settings.mode)
    assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, settings.txPowerLevel)
    assertTrue(settings.isConnectable)
    assertEquals(0, settings.timeout)
  }

  @Test
  fun `starting twice does not advertise twice`() {
    val peripheral = started()
    assertNull(peripheral.start())
    assertEquals(1, advertiser.count)
  }

  @Test
  fun `a service that fails to register takes the session down instead of leaving it claiming to serve`() {
    // addService answers whether the call was accepted, not whether the stack took the service, so
    // this is the only report that the heart rate service is not there to be found.
    started()
    requests.onServiceAdded(BluetoothGatt.GATT_FAILURE, aService())
    assertEquals(listOf(BroadcastRejection.PeripheralUnavailable), failures)
  }

  // --- the advertisement's retry ------------------------------------------------------------------

  @Test
  fun `an advertisement that does not fit is retried once, without the device name`() {
    // A watch whose Bluetooth name overflows the scan response fails the whole advertisement there.
    // The advertisement alone is two bytes of service UUID, so dropping the name is the retry.
    started()
    advertiser.fail(AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
    assertEquals(2, advertiser.count)
    assertNull(advertiser.last.scanResponse)
    assertTrue("the retry was reported as a failure", failures.isEmpty())
  }

  @Test
  fun `a retry that fails too is reported, not retried again`() {
    started()
    advertiser.fail(AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
    advertiser.fail(AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
    assertEquals(2, advertiser.count)
    assertEquals(listOf(BroadcastRejection.AdvertiseFailed), failures)
  }

  @Test
  fun `an advertisement that failed for another reason is reported rather than retried`() {
    started()
    advertiser.fail(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
    assertEquals(1, advertiser.count)
    assertEquals(listOf(BroadcastRejection.AdvertiseFailed), failures)
  }

  // --- subscribing ------------------------------------------------------------------------------

  @Test
  fun `a receiver that subscribes is published, with the name the platform has for it`() {
    started()
    subscribe(laptop)
    assertEquals(listOf("Laptop"), published.last().map { it.name })
  }

  @Test
  fun `a receiver with no name is published by its address`() {
    started()
    subscribe(bike)
    val only = published.last().single()
    assertNull(only.name)
    assertEquals("BB:BB:BB:BB:BB:BB", only.address)
  }

  @Test
  fun `receivers are published in the order they subscribed`() {
    started()
    subscribe(laptop)
    subscribe(bike)
    assertEquals(listOf(laptop.address, bike.address), published.last().map { it.address })
  }

  @Test
  fun `a receiver is stamped with the time it subscribed`() {
    started()
    nowMillis = 7_000L
    subscribe(laptop)
    assertEquals(7_000L, published.last().single().connectedAtElapsedMillis)
  }

  @Test
  fun `a receiver that subscribes twice is published once`() {
    started()
    subscribe(laptop)
    subscribe(laptop)
    assertEquals(1, published.last().size)
  }

  @Test
  fun `a receiver that unsubscribes is taken off the list`() {
    started()
    subscribe(laptop)
    subscribe(laptop, value = byteArrayOf(0x00, 0x00))
    assertTrue(published.last().isEmpty())
  }

  @Test
  fun `a receiver that disconnects is taken off the list`() {
    started()
    subscribe(laptop)
    subscribe(bike)
    disconnect(laptop)
    assertEquals(listOf(bike.address), published.last().map { it.address })
  }

  @Test
  fun `a disconnect from a receiver that was never subscribed is not published as news`() {
    started()
    val before = published.size
    disconnect(bike)
    assertEquals(before, published.size)
  }

  @Test
  fun `a receiver that is only connecting is left alone until it subscribes`() {
    // Listing a connection that has not asked for readings would put a device on the page that
    // nothing is being read by.
    started()
    requests.onConnectionStateChange(bike, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
    assertTrue(published.none { it.isNotEmpty() })
  }

  @Test
  fun `a receiver that unsubscribes and comes back is published at the end`() {
    started()
    subscribe(laptop)
    subscribe(bike)
    subscribe(laptop, value = byteArrayOf(0x00, 0x00))
    subscribe(laptop)
    assertEquals(listOf(bike.address, laptop.address), published.last().map { it.address })
  }

  // --- answering requests -----------------------------------------------------------------------

  @Test
  fun `a read of the measurement is answered with the last reading`() {
    val peripheral = started()
    peripheral.send(79)
    read(HeartRateProfile.MEASUREMENT)
    assertArrayEquals(byteArrayOf(0x00, 79), answers.single())
  }

  @Test
  fun `a read before anything has been read is answered with a zero pulse`() {
    started()
    read(HeartRateProfile.MEASUREMENT)
    assertArrayEquals(byteArrayOf(0x00, 0), answers.single())
  }

  @Test
  fun `a read of the body sensor location is answered with the wrist`() {
    started()
    read(HeartRateProfile.BODY_SENSOR_LOCATION)
    assertArrayEquals(byteArrayOf(2), answers.single())
  }

  @Test
  fun `a read of a characteristic this peripheral does not serve is answered with no value`() {
    started()
    read(uuid = somewhereElse)
    assertNull(answers.single())
  }

  @Test
  fun `a read of the configuration is answered with what that receiver is set to`() {
    started()
    subscribe(laptop)
    readDescriptor(laptop)
    assertArrayEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, answers.last())

    readDescriptor(bike)
    assertArrayEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, answers.last())
  }

  @Test
  fun `a configuration write that needs no answer is taken without one being sent`() {
    // A write command expects nothing back, and answering one is a protocol error.
    started()
    subscribe(laptop, responseNeeded = false)
    assertEquals(listOf("Laptop"), published.last().map { it.name })
    assertTrue(answers.isEmpty())
  }

  @Test
  fun `a write to a descriptor this peripheral does not serve is refused, and changes nothing`() {
    started()
    subscribe(laptop, uuid = somewhereElse)
    assertNull(answers.single())
    assertTrue(published.none { it.isNotEmpty() })
  }

  @Test
  fun `a prepared write is refused, and changes nothing`() {
    started()
    subscribe(laptop, preparedWrite = true)
    assertNull(answers.single())
    assertTrue(published.none { it.isNotEmpty() })
  }

  @Test
  fun `a read starting past the end of a value is answered without one`() {
    started()
    read(uuid = HeartRateProfile.MEASUREMENT, offset = 9)
    assertNull(answers.single())
  }

  // --- stopping ---------------------------------------------------------------------------------

  @Test
  fun `stopping clears the receivers it was serving, and closes the server`() {
    val peripheral = started()
    subscribe(laptop)
    peripheral.stop()
    assertTrue(published.last().isEmpty())
    assertTrue(shadowOf(requireNotNull(server)).isClosed)
  }

  @Test
  fun `stopping stops the advertisement`() {
    val peripheral = started()
    peripheral.stop()
    assertEquals(advertiser.only.callback, advertiser.stopped)
  }

  @Test
  fun `stopping with nobody connected does not publish an empty list nobody asked for`() {
    val peripheral = started()
    peripheral.stop()
    assertTrue(published.isEmpty())
  }

  @Test
  fun `a peripheral that has stopped answers nothing`() {
    val peripheral = started()
    peripheral.stop()
    val before = answers.size
    read(HeartRateProfile.MEASUREMENT)
    assertEquals(before, answers.size)
  }

  // --- driving the server -----------------------------------------------------------------------

  private fun subscribe(
    device: BluetoothDevice,
    value: ByteArray = byteArrayOf(0x01, 0x00),
    preparedWrite: Boolean = false,
    responseNeeded: Boolean = true,
    uuid: UUID = HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG,
  ) {
    val descriptor = aDescriptor(uuid)
    requests.onDescriptorWriteRequest(
      device,
      requestId++,
      descriptor,
      preparedWrite,
      responseNeeded,
      0,
      value,
    )
  }

  private fun read(uuid: UUID, offset: Int = 0) {
    val characteristic =
      BluetoothGattCharacteristic(
        uuid,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ,
      )
    requests.onCharacteristicReadRequest(bike, requestId++, offset, characteristic)
  }

  private fun readDescriptor(device: BluetoothDevice) {
    val descriptor = aDescriptor(HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG)
    requests.onDescriptorReadRequest(device, requestId++, 0, descriptor)
  }

  private fun disconnect(device: BluetoothDevice) {
    requests.onConnectionStateChange(
      device,
      BluetoothGatt.GATT_SUCCESS,
      BluetoothProfile.STATE_DISCONNECTED,
    )
  }

  private fun aDescriptor(uuid: UUID) =
    BluetoothGattDescriptor(
      uuid,
      BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    )

  private fun aService() =
    BluetoothGattService(HeartRateProfile.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

  /** What the peripheral asked the radio for, and a way to report back what the platform would. */
  private class RecordingAdvertiser : Advertiser {
    data class Start(
      val settings: AdvertiseSettings,
      val data: AdvertiseData,
      val scanResponse: AdvertiseData?,
      val callback: AdvertiseCallback,
    )

    private val starts = mutableListOf<Start>()

    var stopped: AdvertiseCallback? = null
      private set

    val count: Int
      get() = starts.size

    /** The most recent advertisement. */
    val last: Start
      get() = starts.last()

    /** The one advertisement, for the tests where there is only one. */
    val only: Start
      get() = starts.single()

    override fun start(
      settings: AdvertiseSettings,
      data: AdvertiseData,
      scanResponse: AdvertiseData?,
      callback: AdvertiseCallback,
    ) {
      starts += Start(settings, data, scanResponse, callback)
    }

    override fun stop(callback: AdvertiseCallback) {
      stopped = callback
    }

    /** Reports to the advertisement just made what the platform would report to it. */
    fun fail(errorCode: Int) {
      last.callback.onStartFailure(errorCode)
    }
  }

  private companion object {
    /** Any UUID that is not one this peripheral serves. */
    val somewhereElse: UUID = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")

    fun device(address: String, name: String? = null): BluetoothDevice {
      val device = ShadowBluetoothDevice.newInstance(address)
      if (name != null) shadowOf(device).setName(name)
      return device
    }
  }
}

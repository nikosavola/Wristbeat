package fi.nikosavola.wristbeat.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

/**
 * What to answer a GATT request with.
 *
 * Kept out of the callback that sends it, so the interop rules - which characteristics are served,
 * what a configuration write means, and how a read that starts part-way into a value is answered -
 * are decided by something that can be exercised without a radio. The callback is then only the
 * mapping from a reply to a [BluetoothGatt] status, which is the part that cannot be got wrong
 * quietly.
 */
internal sealed interface GattReply {
  /** Answer with these bytes. A plain class rather than a data class: bytes have no structural
   * equality in Kotlin, so claiming one here would be a lie a test could trip over. */
  class Value(val bytes: ByteArray) : GattReply

  /** The request named something this peripheral does not serve. */
  data object NotSupported : GattReply

  /** A read starting past the end of a value. GATT gives this a status of its own rather than
   * folding it in with a request the peripheral does not serve at all. */
  data object InvalidOffset : GattReply

  /** The status the reply is sent with. */
  val status: Int
    get() =
      when (this) {
        is Value -> BluetoothGatt.GATT_SUCCESS
        NotSupported -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
        InvalidOffset -> BluetoothGatt.GATT_INVALID_OFFSET
      }

  /** The bytes the reply carries, or null for a status with nothing behind it. */
  val body: ByteArray?
    get() = (this as? Value)?.bytes
}

/** What a write to the client characteristic configuration means. */
internal enum class DescriptorWrite {
  Subscribe,
  Unsubscribe,

  /** Not a descriptor this peripheral serves, or a long write it has no use for. */
  Refused,
}

internal object GattReplies {
  /** The value of a characteristic read, or null when the UUID is not one of ours. GATT has no "not
   * ready yet" status, so a read before the first sensor event answers with a zero pulse rather than
   * failing the read. */
  fun characteristicRead(uuid: UUID, lastBpm: Int?): ByteArray? =
    when (uuid) {
      HeartRateProfile.MEASUREMENT -> HeartRateProfile.measurementPayload(lastBpm ?: 0)
      HeartRateProfile.BODY_SENSOR_LOCATION ->
        byteArrayOf(HeartRateProfile.BODY_SENSOR_LOCATION_WRIST)
      else -> null
    }

  /** The value of a configuration read: what the central asking is currently set to. */
  fun descriptorRead(uuid: UUID, subscribed: Boolean): ByteArray? =
    when (uuid) {
      HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG ->
        if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
      else -> null
    }

  /**
   * What a configuration write means.
   *
   * Nothing here is long enough to need a prepared write, so one is refused rather than half-applied.
   */
  fun descriptorWrite(uuid: UUID, preparedWrite: Boolean, value: ByteArray): DescriptorWrite =
    when {
      uuid != HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG -> DescriptorWrite.Refused
      preparedWrite -> DescriptorWrite.Refused
      HeartRateProfile.isSubscribe(value) -> DescriptorWrite.Subscribe
      else -> DescriptorWrite.Unsubscribe
    }

  /** How a read of [value] starting at [offset] is answered. A read starting exactly at the end is
   * not past it: that is an empty remainder, which GATT answers with an empty value. */
  fun read(offset: Int, value: ByteArray?): GattReply =
    when {
      value == null -> GattReply.NotSupported
      offset > value.size -> GattReply.InvalidOffset
      else -> GattReply.Value(value.copyOfRange(offset, value.size))
    }
}

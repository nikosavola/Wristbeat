package fi.nikosavola.wristbeat.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a receiver meets: which requests this peripheral answers, with what bytes, and with what
 * status.
 *
 * The status is the reason these are decided away from the callback. Robolectric's GATT server
 * records the value a response carries but not the status it was sent with, so a test driving the
 * callback cannot tell Invalid Offset from Request Not Supported. Here it can, and the mapping from
 * a reply to a status is pinned in one place.
 */
@RunWith(AndroidJUnit4::class)
class GattRepliesTest {

  private val cccd = HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG
  private val somewhereElse = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb")

  private fun assertAnswered(status: Int, body: ByteArray?, reply: GattReply) {
    assertEquals(status, reply.status)
    assertArrayEquals(body, reply.body)
  }

  @Test
  fun `a measurement read answers with the last reading`() {
    assertArrayEquals(
      byteArrayOf(0x00, 79),
      GattReplies.characteristicRead(HeartRateProfile.MEASUREMENT, lastBpm = 79),
    )
  }

  @Test
  fun `a measurement read before the first reading answers with a zero pulse, not a failure`() {
    // GATT has no "not ready yet" status, and failing the read would leave a receiver with an error
    // where a pulse is about to be.
    assertArrayEquals(
      byteArrayOf(0x00, 0),
      GattReplies.characteristicRead(HeartRateProfile.MEASUREMENT, lastBpm = null),
    )
  }

  @Test
  fun `the body sensor location answers with the wrist`() {
    assertArrayEquals(
      byteArrayOf(2),
      GattReplies.characteristicRead(HeartRateProfile.BODY_SENSOR_LOCATION, lastBpm = 79),
    )
  }

  @Test
  fun `a characteristic this peripheral does not serve is answered with nothing`() {
    assertNull(GattReplies.characteristicRead(somewhereElse, lastBpm = 79))
  }

  @Test
  fun `a configuration read says what the asking central is set to`() {
    assertArrayEquals(
      BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
      GattReplies.descriptorRead(cccd, subscribed = false),
    )
    assertArrayEquals(
      BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
      GattReplies.descriptorRead(cccd, subscribed = true),
    )
  }

  @Test
  fun `a descriptor this peripheral does not serve is answered with nothing`() {
    assertNull(GattReplies.descriptorRead(somewhereElse, subscribed = true))
  }

  @Test
  fun `the notification bit is a subscribe and its absence is an unsubscribe`() {
    assertEquals(
      DescriptorWrite.Subscribe,
      GattReplies.descriptorWrite(cccd, preparedWrite = false, byteArrayOf(0x01, 0x00)),
    )
    assertEquals(
      DescriptorWrite.Unsubscribe,
      GattReplies.descriptorWrite(cccd, preparedWrite = false, byteArrayOf(0x00, 0x00)),
    )
  }

  @Test
  fun `a write to a descriptor this peripheral does not serve is refused`() {
    assertEquals(
      DescriptorWrite.Refused,
      GattReplies.descriptorWrite(somewhereElse, preparedWrite = false, byteArrayOf(0x01, 0x00)),
    )
  }

  @Test
  fun `a prepared write is refused rather than half-applied`() {
    // Nothing here needs a long write, so one is turned down instead of being taken for a subscribe
    // of whatever fragment arrived first.
    assertEquals(
      DescriptorWrite.Refused,
      GattReplies.descriptorWrite(cccd, preparedWrite = true, byteArrayOf(0x01, 0x00)),
    )
  }

  @Test
  fun `a read of the whole value answers with all of it and the success status`() {
    assertAnswered(
      BluetoothGatt.GATT_SUCCESS,
      byteArrayOf(0x00, 79),
      GattReplies.read(offset = 0, value = byteArrayOf(0x00, 79)),
    )
  }

  @Test
  fun `a read into the middle of a value answers with the rest of it`() {
    assertAnswered(
      BluetoothGatt.GATT_SUCCESS,
      byteArrayOf(79),
      GattReplies.read(offset = 1, value = byteArrayOf(0x00, 79)),
    )
  }

  @Test
  fun `a read starting at the end of a value answers with nothing, and successfully`() {
    // Not past the end: a zero-length remainder is a legal read.
    assertAnswered(
      BluetoothGatt.GATT_SUCCESS,
      byteArrayOf(),
      GattReplies.read(offset = 2, value = byteArrayOf(0x00, 79)),
    )
  }

  @Test
  fun `a read past the end of a value is answered as an invalid offset`() {
    assertAnswered(
      BluetoothGatt.GATT_INVALID_OFFSET,
      null,
      GattReplies.read(offset = 3, value = byteArrayOf(0x00, 79)),
    )
  }

  @Test
  fun `a read of a characteristic this peripheral does not serve is not supported`() {
    assertAnswered(
      BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
      null,
      GattReplies.read(offset = 0, value = null),
    )
  }

  @Test
  fun `the two refusal statuses are different, and neither is success`() {
    // The whole point of keeping them apart: a central can tell a wrong request from a wrong offset,
    // and a test can tell either from an answer.
    val notSupported = GattReplies.read(offset = 0, value = null).status
    val invalidOffset = GattReplies.read(offset = 9, value = byteArrayOf(1)).status
    assertFalse(notSupported == invalidOffset)
    assertFalse(notSupported == BluetoothGatt.GATT_SUCCESS)
    assertFalse(invalidOffset == BluetoothGatt.GATT_SUCCESS)
  }
}

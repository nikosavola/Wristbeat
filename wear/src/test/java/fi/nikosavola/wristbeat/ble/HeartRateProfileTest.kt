package fi.nikosavola.wristbeat.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateProfileTest {

  @Test
  fun `a measurement carries the 8-bit flag then the bpm`() {
    assertArrayEquals(byteArrayOf(0x00, 72), HeartRateProfile.measurementPayload(72))
  }

  @Test
  fun `zero is sent as a real reading rather than omitted`() {
    assertArrayEquals(byteArrayOf(0x00, 0), HeartRateProfile.measurementPayload(0))
  }

  @Test
  fun `a bpm past the 8-bit range clamps instead of wrapping to a low number`() {
    // Wrapping would turn 256 into 0 and 300 into 44, both of which read as a plausible pulse.
    assertArrayEquals(byteArrayOf(0x00, 255.toByte()), HeartRateProfile.measurementPayload(256))
    assertArrayEquals(byteArrayOf(0x00, 255.toByte()), HeartRateProfile.measurementPayload(300))
  }

  @Test
  fun `a negative bpm clamps to zero`() {
    assertArrayEquals(byteArrayOf(0x00, 0), HeartRateProfile.measurementPayload(-5))
  }

  @Test
  fun `enabling notifications reads as a subscribe`() {
    assertTrue(HeartRateProfile.isSubscribe(byteArrayOf(0x01, 0x00)))
  }

  @Test
  fun `the indication bit also reads as a subscribe`() {
    // The characteristic only declares NOTIFY, but a central asking for indications wants the same
    // thing here, and treating it as an unsubscribe would silently mute the stream.
    assertTrue(HeartRateProfile.isSubscribe(byteArrayOf(0x02, 0x00)))
  }

  @Test
  fun `an all-zero write reads as an unsubscribe`() {
    assertFalse(HeartRateProfile.isSubscribe(byteArrayOf(0x00, 0x00)))
  }

  @Test
  fun `an empty write reads as an unsubscribe`() {
    assertFalse(HeartRateProfile.isSubscribe(byteArrayOf()))
  }

  @Test
  fun `the characteristic UUIDs are the assigned 16-bit numbers on the Bluetooth base UUID`() {
    // A typo anywhere in the base UUID would still look plausible and would silently stop every
    // receiver from finding the service, so pin the whole thing rather than the short form.
    assertEquals("0000180d-0000-1000-8000-00805f9b34fb", HeartRateProfile.SERVICE.toString())
    assertEquals("00002a37-0000-1000-8000-00805f9b34fb", HeartRateProfile.MEASUREMENT.toString())
    assertEquals(
      "00002a38-0000-1000-8000-00805f9b34fb",
      HeartRateProfile.BODY_SENSOR_LOCATION.toString(),
    )
    assertEquals(
      "00002902-0000-1000-8000-00805f9b34fb",
      HeartRateProfile.CLIENT_CHARACTERISTIC_CONFIG.toString(),
    )
  }

  @Test
  fun `the body sensor location says wrist`() {
    assertEquals(2, HeartRateProfile.BODY_SENSOR_LOCATION_WRIST.toInt())
  }
}

package fi.nikosavola.wristbeat.ble

import java.util.UUID

/**
 * Bluetooth SIG Heart Rate Service (HRS) identifiers and payload encoding.
 *
 * These are the assigned numbers from the Bluetooth GATT specification, so any standard receiver -
 * gym equipment, bike computers, phone apps - decodes what the watch broadcasts without knowing
 * anything about this app.
 */
object HeartRateProfile {
  // Assigned 16-bit UUIDs spelled out in full, which is what the Android BLE API expects.
  val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
  val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
  val BODY_SENSOR_LOCATION: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")
  val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

  /** HRS 3.1 body sensor location: 2 = wrist, which is where a watch sits. */
  const val BODY_SENSOR_LOCATION_WRIST: Byte = 2

  private const val MAX_8_BIT_BPM = 255

  /** Flags byte with the "8-bit heart rate value format" bit clear, meaning the bpm byte that
   * follows is a plain uint8. Eight bits covers any human pulse and keeps the notify payload at the
   * two bytes every receiver expects. */
  private const val MEASUREMENT_FLAGS_8_BIT: Byte = 0x00

  fun measurementPayload(bpm: Int): ByteArray =
    byteArrayOf(MEASUREMENT_FLAGS_8_BIT, bpm.coerceIn(0, MAX_8_BIT_BPM).toByte())

  /**
   * Whether a write to the client characteristic configuration means "start notifying".
   *
   * The measurement characteristic declares NOTIFY rather than INDICATE, so a well-behaved central
   * writes 0x0001. Treating any non-zero value as subscribe means one that writes the indication
   * bit still gets notifications instead of a silent unsubscribe, and only a genuine all-zero
   * write turns them off.
   */
  fun isSubscribe(descriptorValue: ByteArray): Boolean = descriptorValue.any { it != 0.toByte() }
}

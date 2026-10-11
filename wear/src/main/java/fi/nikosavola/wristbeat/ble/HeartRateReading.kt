package fi.nikosavola.wristbeat.ble

import android.hardware.SensorManager

/**
 * Whether a heart rate sensor event is a reading worth broadcasting.
 *
 * The accuracy test is `>= ACCURACY_LOW` rather than `!= UNRELIABLE` on purpose: an off-wrist watch
 * reports `SENSOR_STATUS_NO_CONTACT`, which is -1, while `SENSOR_STATUS_UNRELIABLE` is 0. They are
 * distinct constants, so checking only for unreliable would let a stale pulse broadcast while the
 * watch sits on a table.
 */
internal fun isBroadcastableReading(bpm: Int, accuracy: Int): Boolean =
  bpm > 0 && accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW

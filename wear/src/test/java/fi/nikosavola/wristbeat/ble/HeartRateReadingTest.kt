package fi.nikosavola.wristbeat.ble

import android.hardware.SensorManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateReadingTest {

  @Test
  fun `a real pulse at full accuracy is broadcast`() {
    assertTrue(isBroadcastableReading(bpm = 72, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
  }

  @Test
  fun `a low accuracy pulse is still broadcast`() {
    // Low accuracy on a wrist sensor means a noisy reading, not an absent one, and a noisy pulse
    // beats no pulse for anything watching a workout.
    assertTrue(isBroadcastableReading(bpm = 72, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW))
    assertTrue(
      isBroadcastableReading(bpm = 72, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
    )
  }

  @Test
  fun `an off-wrist reading is not broadcast`() {
    // NO_CONTACT is -1, which is not the constant an `== UNRELIABLE` check would catch.
    assertFalse(
      isBroadcastableReading(bpm = 72, accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT)
    )
  }

  @Test
  fun `an unreliable reading is not broadcast`() {
    assertFalse(
      isBroadcastableReading(bpm = 72, accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE)
    )
  }

  @Test
  fun `zero and negative bpm are not broadcast however good the accuracy looks`() {
    assertFalse(isBroadcastableReading(bpm = 0, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
    assertFalse(
      isBroadcastableReading(bpm = -1, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    )
  }

  @Test
  fun `the two rejecting accuracy constants really are different values`() {
    // The whole reason the check is written as a threshold rather than an equality test. If a
    // future platform collapsed these into one value the threshold would still hold, but this
    // pins the assumption the comment in HeartRateReading.kt makes.
    assertFalse(
      SensorManager.SENSOR_STATUS_NO_CONTACT == SensorManager.SENSOR_STATUS_UNRELIABLE
    )
    assertTrue(SensorManager.SENSOR_STATUS_NO_CONTACT < SensorManager.SENSOR_STATUS_ACCURACY_LOW)
  }
}

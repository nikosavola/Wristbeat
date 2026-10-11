package fi.nikosavola.wristbeat.ble

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/** The wiring between the watch's sensor and the broadcast: whether there is one, being on it, and
 * which events coming off it are worth handing on. */
@RunWith(AndroidJUnit4::class)
class HeartRateSourceTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager: SensorManager = context.getSystemService(SensorManager::class.java)

  private val readings = mutableListOf<Int>()

  private fun source() = HeartRateSource(context) { readings += it }

  @Test
  fun `a watch with no heart rate sensor says so, and cannot be started`() {
    val source = HeartRateSource(context) { fail("nothing should have been read") }
    assertFalse(source.isAvailable)
    assertFalse(source.start())
  }

  @Test
  fun `a watch with one can be started and stopped`() {
    addHeartRateSensor()
    val source = HeartRateSource(context) { fail("nothing should have been read") }
    assertTrue(source.isAvailable)
    assertTrue(source.start())
    assertTrue(shadowOf(manager).hasListener(source))
    source.stop()
    assertFalse(shadowOf(manager).hasListener(source))
  }

  @Test
  fun `a sensor of another kind is not mistaken for a heart rate one`() {
    addSensor(Sensor.TYPE_STEP_COUNTER)
    val source = HeartRateSource(context) { fail("nothing should have been read") }
    assertFalse(source.isAvailable)
  }

  @Test
  fun `a sensor that will not take the listener says so rather than pretending to read`() {
    // registerListener answering no is what the service has to notice: nothing would ever arrive
    // after it.
    addHeartRateSensor()
    shadowOf(manager).setForceListenersToFail(true)
    assertFalse(source().start())
  }

  @Test
  fun `a reading is handed on`() {
    val sensor = addHeartRateSensor()
    val source = source()
    source.start()
    deliver(sensor, bpm = 79)
    assertEquals(listOf(79), readings)
  }

  @Test
  fun `an off-wrist reading is not handed on`() {
    val sensor = addHeartRateSensor()
    val source = source()
    source.start()
    deliver(sensor, bpm = 79, accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT)
    assertTrue(readings.isEmpty())
  }

  @Test
  fun `a reading of zero is not handed on`() {
    val sensor = addHeartRateSensor()
    val source = source()
    source.start()
    deliver(sensor, bpm = 0)
    assertTrue(readings.isEmpty())
  }

  @Test
  fun `an event of another kind does not become a reading`() {
    // Delivered to the listener that is registered, which is the only way this guard is reached:
    // the platform routes an event by its sensor.
    val heartRate = addHeartRateSensor()
    val steps = addSensor(Sensor.TYPE_STEP_COUNTER)
    val source = source()
    source.start()
    val event =
      SensorEventBuilder.newBuilder(steps, floatArrayOf(79f))
        .setAccuracy(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        .build()
    shadowOf(manager).sendSensorEventToListeners(event, heartRate)
    assertTrue(readings.isEmpty())
  }

  @Test
  fun `a reading that arrives after the listener is off is not handed on`() {
    val sensor = addHeartRateSensor()
    val source = source()
    source.start()
    source.stop()
    deliver(sensor, bpm = 79)
    assertTrue(readings.isEmpty())
  }

  /** One sensor event, the way the platform delivers it. */
  private fun deliver(
    sensor: Sensor,
    bpm: Int,
    accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
  ) {
    val event =
      SensorEventBuilder.newBuilder(sensor, floatArrayOf(bpm.toFloat()))
        .setAccuracy(accuracy)
        .build()
    shadowOf(manager).sendSensorEventToListeners(event, sensor)
  }

  /** Robolectric's only way to make a Sensor: the platform's own constructor is hidden. */
  @Suppress("DEPRECATION")
  private fun addHeartRateSensor(): Sensor = addSensor(Sensor.TYPE_HEART_RATE)

  @Suppress("DEPRECATION")
  private fun addSensor(type: Int): Sensor {
    val sensor = ShadowSensor.newInstance(type)
    shadowOf(manager).addSensor(sensor)
    return sensor
  }

  private fun fail(message: String): Nothing = throw AssertionError(message)
}

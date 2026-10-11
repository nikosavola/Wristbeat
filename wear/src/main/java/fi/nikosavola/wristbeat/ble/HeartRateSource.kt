package fi.nikosavola.wristbeat.ble

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * The watch's own optical heart rate sensor, which is what there is to broadcast.
 *
 * [SensorManager.getDefaultSensor] returns null until the heart rate permission is granted, so
 * [isAvailable] is only meaningful once the caller has the permission in hand.
 */
class HeartRateSource(
  context: Context,
  private val onHeartRate: (Int) -> Unit,
) : SensorEventListener {
  private val sensorManager: SensorManager? =
    context.applicationContext.getSystemService(SensorManager::class.java)
  private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

  val isAvailable: Boolean
    get() = sensor != null

  /** Returns false when there is no sensor or the platform refused the listener. */
  fun start(): Boolean {
    val manager = sensorManager ?: return false
    val heartRate = sensor ?: return false
    return manager.registerListener(this, heartRate, SensorManager.SENSOR_DELAY_NORMAL)
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
    val bpm = event.values.firstOrNull()?.toInt() ?: return
    if (!isBroadcastableReading(bpm, event.accuracy)) return
    onHeartRate(bpm)
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}

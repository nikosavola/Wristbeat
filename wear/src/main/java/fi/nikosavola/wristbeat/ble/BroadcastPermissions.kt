package fi.nikosavola.wristbeat.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions this app needs, defined once: the activity asks for them, the service
 * checks them before it goes foreground, and the peripheral checks them before it touches the
 * Bluetooth stack.
 *
 * Which permissions those are depends on the API level, and that is written as a function of the
 * level rather than read from [Build.VERSION] at each site, so every level can be exercised
 * without a device running it.
 *
 * Naming a permission above the minimum level is what the suppression is about: these are String
 * constants the compiler inlines, so naming one on an older device is not a call into the platform
 * and cannot fail. Lint can only see that when the guard reads the running level, which is the one
 * thing this deliberately does not do.
 */
@Suppress("InlinedApi")
object BroadcastPermissions {
  /**
   * The Bluetooth LE peripheral role. The connectedDevice foreground service type additionally
   * requires at least one of these to be granted before startForeground, which is why the service
   * checks this list separately from [required].
   */
  fun bluetoothAt(sdk: Int): List<String> =
    if (sdk >= Build.VERSION_CODES.S) {
      listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      emptyList()
    }

  /**
   * What gates reading the watch's own heart rate sensor.
   *
   * Android 16 replaced BODY_SENSORS with granular Health Connect permissions for apps targeting
   * API 36, and the sensor APIs follow the target SDK rather than the device, so the cut is on the
   * API level.
   */
  fun heartRateAt(sdk: Int): String =
    if (sdk >= Build.VERSION_CODES.BAKLAVA) {
      HEALTH_READ_HEART_RATE
    } else {
      Manifest.permission.BODY_SENSORS
    }

  /**
   * Asked for alongside the rest but never gating: the foreground service runs with its
   * notification hidden, so refusing this only costs the user the on-watch indicator.
   */
  fun optionalAt(sdk: Int): List<String> =
    if (sdk >= Build.VERSION_CODES.TIRAMISU) {
      listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      emptyList()
    }

  val bluetooth: List<String> = bluetoothAt(Build.VERSION.SDK_INT)

  val heartRate: String = heartRateAt(Build.VERSION.SDK_INT)

  /** Everything broadcasting cannot work without. */
  val required: List<String> = bluetooth + heartRate

  val optional: List<String> = optionalAt(Build.VERSION.SDK_INT)

  /** Only declared from API 36, so it has no Manifest.permission constant to depend on. */
  const val HEALTH_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

  fun missing(context: Context, permissions: List<String>): List<String> =
    permissions.filterNot { context.holds(it) }

  fun allGranted(context: Context, permissions: List<String>): Boolean =
    missing(context, permissions).isEmpty()

  private fun Context.holds(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

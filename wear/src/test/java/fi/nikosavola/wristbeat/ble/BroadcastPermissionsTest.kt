package fi.nikosavola.wristbeat.ble

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The API level cuts, exercised at every level that matters rather than only at whatever the test
 * device runs. Robolectric cannot run at API 36 yet, so the branch that only exists there would
 * otherwise never be executed.
 */
class BroadcastPermissionsTest {

  @Test
  fun `the Bluetooth permissions are absent before API 31 and present from it`() {
    assertTrue(BroadcastPermissions.bluetoothAt(30).isEmpty())
    assertEquals(
      listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT),
      BroadcastPermissions.bluetoothAt(31),
    )
  }

  @Test
  fun `later levels keep asking for both Bluetooth permissions`() {
    // Advertising without connecting is not enough: the GATT server needs the second one too.
    assertEquals(BroadcastPermissions.bluetoothAt(31), BroadcastPermissions.bluetoothAt(36))
  }

  @Test
  fun `the heart rate permission is BODY_SENSORS up to API 35 and Health Connect's from 36`() {
    assertEquals(Manifest.permission.BODY_SENSORS, BroadcastPermissions.heartRateAt(35))
    assertEquals(
      BroadcastPermissions.HEALTH_READ_HEART_RATE,
      BroadcastPermissions.heartRateAt(36),
    )
  }

  @Test
  fun `a later level does not fall back to BODY_SENSORS`() {
    // The cut is on the target SDK, so an app targeting 36 keeps asking for the granular permission
    // however new the device gets.
    assertEquals(
      BroadcastPermissions.HEALTH_READ_HEART_RATE,
      BroadcastPermissions.heartRateAt(40),
    )
  }

  @Test
  fun `notifications are optional before API 33 and asked for from it`() {
    assertTrue(BroadcastPermissions.optionalAt(32).isEmpty())
    assertEquals(
      listOf(Manifest.permission.POST_NOTIFICATIONS),
      BroadcastPermissions.optionalAt(33),
    )
    assertEquals(BroadcastPermissions.optionalAt(33), BroadcastPermissions.optionalAt(36))
  }

  @Test
  fun `the Health Connect permission is spelled the way the platform spells it`() {
    // Hand-written because it is declared only from API 36, so there is no Manifest.permission
    // constant for it. A typo here would make the request fail silently, with no pulse to broadcast.
    assertEquals(
      "android.permission.health.READ_HEART_RATE",
      BroadcastPermissions.HEALTH_READ_HEART_RATE,
    )
  }

  @Test
  fun `every gating permission is asked for alongside the others, never instead of them`() {
    val required = BroadcastPermissions.bluetoothAt(36) + BroadcastPermissions.heartRateAt(36)
    assertEquals(3, required.size)
    assertEquals(required.size, required.toSet().size)
  }
}

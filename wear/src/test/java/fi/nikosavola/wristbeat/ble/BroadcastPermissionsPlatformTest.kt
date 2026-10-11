package fi.nikosavola.wristbeat.ble

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The permission sets as the platform sees them, on a device at the level the test resources pin.
 * This is the half [BroadcastPermissionsTest] cannot reach: which of them are actually granted.
 */
@RunWith(RobolectricTestRunner::class)
class BroadcastPermissionsPlatformTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val application: Application = ApplicationProvider.getApplicationContext()

  private val everything =
    BroadcastPermissions.required + BroadcastPermissions.optional + Manifest.permission.BODY_SENSORS

  @Before
  fun denyEverything() {
    shadowOf(application).denyPermissions(*everything.toTypedArray())
  }

  @Test
  fun `on this level what broadcasting needs is the two Bluetooth permissions and the Health Connect one`() {
    assertEquals(
      listOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        BroadcastPermissions.HEALTH_READ_HEART_RATE,
      ),
      BroadcastPermissions.required,
    )
  }

  @Test
  fun `BODY_SENSORS is not asked for on top of the Health Connect permission`() {
    // The manifest caps it at the levels where it still applies, so asking for both would be asking
    // for a permission this device does not grant.
    assertFalse(BroadcastPermissions.required.contains(Manifest.permission.BODY_SENSORS))
  }

  @Test
  fun `nothing granted reports every gating permission as missing, in the order they were asked about`() {
    assertEquals(BroadcastPermissions.required, BroadcastPermissions.missing(context, BroadcastPermissions.required))
  }

  @Test
  fun `granting one leaves only the others missing`() {
    shadowOf(application).grantPermissions(BroadcastPermissions.HEALTH_READ_HEART_RATE)
    assertFalse(
      BroadcastPermissions.missing(context, BroadcastPermissions.required)
        .contains(BroadcastPermissions.HEALTH_READ_HEART_RATE)
    )
  }

  @Test
  fun `all granted is all granted, and one refused is not`() {
    shadowOf(application).grantPermissions(*BroadcastPermissions.required.toTypedArray())
    assertTrue(BroadcastPermissions.allGranted(context, BroadcastPermissions.required))
    shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_ADVERTISE)
    assertFalse(BroadcastPermissions.allGranted(context, BroadcastPermissions.required))
  }

  @Test
  fun `the notification permission is asked about but gates nothing`() {
    // Refusing it only costs the on-watch indicator, so it must stay out of the set that decides
    // whether a session can start.
    assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), BroadcastPermissions.optional)
    assertFalse(BroadcastPermissions.required.contains(Manifest.permission.POST_NOTIFICATIONS))
  }

  @Test
  fun `an empty ask is trivially satisfied`() {
    assertTrue(BroadcastPermissions.allGranted(context, emptyList()))
    assertTrue(BroadcastPermissions.missing(context, emptyList()).isEmpty())
  }
}

package fi.nikosavola.wristbeat.appfunctions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The functions an AI agent can call, driven through the service the appfunctions compiler generates
 * from [BroadcastAppFunctions] rather than through the base class, so what is exercised is the class
 * the platform actually binds.
 *
 * What is not here is the platform calling them: an agent has to hold a system permission this test
 * cannot be given, so the registration is pinned against the identifiers the compiler generated and
 * the invocation itself is checked on a device.
 */
@RunWith(AndroidJUnit4::class)
class BroadcastAppFunctionsTest {

  private val application = ApplicationProvider.getApplicationContext<android.app.Application>()

  @Before
  fun startFromNothing() {
    // Granted first because the state under test is a session that could start, and each test that
    // wants the other one denies them again.
    shadowOf(application).grantPermissions(*BroadcastPermissions.required.toTypedArray())
    BroadcastStatus.started()
    BroadcastStatus.stopped()
  }

  private fun functions(): BroadcastAppFunctions =
    Robolectric.buildService(BroadcastAppFunctionsService::class.java).create().get()

  @Test
  fun `it reports a watch that is not broadcasting as not broadcasting`() = runTest {
    assertFalse(functions().isBroadcasting())
  }

  @Test
  fun `it reports a watch that is broadcasting as broadcasting`() = runTest {
    BroadcastStatus.started()
    assertTrue(functions().isBroadcasting())
  }

  @Test
  fun `a session that cannot start is reported as not started rather than as started`() = runTest {
    // Nothing here can serve the peripheral role, so false is the honest answer. An optimistic true
    // would be the one failure an agent cannot recover from: it would report the watch as sharing a
    // heart rate it is not sharing.
    assertFalse(functions().toggleBroadcasting())
  }

  @Test
  fun `a session the wearer has not granted the permissions for is never started`() = runTest {
    // Not a nicety: the service bails before going foreground when the Bluetooth permission is
    // missing, and a service that does that is killed by the platform, taking the process with it.
    // So the refusal has to happen here, before anything is asked to start.
    shadowOf(application).denyPermissions(*BroadcastPermissions.required.toTypedArray())
    assertFalse(functions().toggleBroadcasting())
    assertNull("a service was started it could not keep", shadowOf(application).nextStartedService)
    // The wearer sees why on the watch, which is the only place the permission can be granted.
    assertEquals(BroadcastRejection.MissingPermission, BroadcastStatus.rejection.value)
  }

  @Test
  fun `a session that is already running is stopped rather than started again`() = runTest {
    BroadcastStatus.started()
    val service = functions()
    var running = true
    val toggle = launch { running = service.toggleBroadcasting() }
    runCurrent()
    assertEquals(
      "fi.nikosavola.wristbeat.action.STOP",
      shadowOf(application).nextStartedService?.action,
    )
    // What the service publishes when it tears the session down.
    BroadcastStatus.stopped()
    toggle.join()
    assertFalse(running)
    assertTrue("waited out the timeout", testScheduler.currentTime < 5_000L)
  }

  @Test
  fun `a refusal from the service ends the wait rather than the clock running out`() = runTest {
    val service = functions()
    var running = true
    val toggle = launch { running = service.toggleBroadcasting() }
    runCurrent()
    // What the service publishes when it cannot start, on its own thread.
    BroadcastStatus.rejected(BroadcastRejection.NoHeartRateSensor)
    toggle.join()
    assertFalse(running)
    assertTrue("waited out the timeout", testScheduler.currentTime < 5_000L)
  }

  @Test
  fun `the platform addresses these functions by the identifiers the compiler generated`() {
    // The identifier is part of what an agent calls, and it embeds the declaring class name, so
    // moving the class silently breaks callers rather than failing the build.
    assertEquals(
      "fi.nikosavola.wristbeat.appfunctions.BroadcastAppFunctions#toggleBroadcasting",
      BroadcastAppFunctionsService.FUNCTION_ID_TOGGLE_BROADCASTING,
    )
    assertEquals(
      "fi.nikosavola.wristbeat.appfunctions.BroadcastAppFunctions#isBroadcasting",
      BroadcastAppFunctionsService.FUNCTION_ID_IS_BROADCASTING,
    )
  }
}

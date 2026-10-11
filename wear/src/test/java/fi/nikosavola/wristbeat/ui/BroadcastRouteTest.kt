package fi.nikosavola.wristbeat.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.nikosavola.wristbeat.HeartRateBroadcastService
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import fi.nikosavola.wristbeat.ui.theme.WristbeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Back, pressed inside the app, on both pages. On a watch that gesture is how an app is left, and
 * here it is spent inside the app instead: on the devices page by going back to the session, and on
 * the session page by toggling it, since there is nothing further back to go. What these check is
 * which of the two a press reaches, and that the app is not left instead.
 */
@RunWith(AndroidJUnit4::class)
class BroadcastRouteTest {

  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val haptics = RecordingHaptics()

  @Before
  fun startFromNothing() {
    shadowOf(application).grantPermissions(*BroadcastPermissions.required.toTypedArray())
    BroadcastStatus.stopped()
    haptics.performed.clear()
  }

  private fun route() {
    rule.setContent {
      CompositionLocalProvider(LocalHapticFeedback provides haptics) {
        WristbeatTheme { BroadcastRoute() }
      }
    }
  }

  private fun pressBack() {
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    rule.waitForIdle()
  }

  @Test
  fun `back starts the session instead of leaving the app`() {
    route()
    pressBack()
    val started = shadowOf(application).nextStartedService
    assertNotNull("back did not reach the service", started)
    assertEquals(HeartRateBroadcastService::class.java.name, started?.component?.className)
    assertFalse("the app was left instead", rule.activity.isFinishing)
  }

  @Test
  fun `back stops a session that is already running`() {
    BroadcastStatus.started()
    route()
    pressBack()
    assertEquals("fi.nikosavola.wristbeat.action.STOP", shadowOf(application).nextStartedService?.action)
  }

  @Test
  fun `back buzzes the way the button does`() {
    route()
    pressBack()
    assertEquals(listOf(HapticFeedbackType.ToggleOn), haptics.performed)
  }

  @Test
  fun `back on the devices page returns to the session rather than toggling it`() {
    BroadcastStatus.started()
    route()
    showDevices()
    pressBack()
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
    assertNull("back reached the service from the devices page", shadowOf(application).nextStartedService)
    assertFalse("the app was left instead", rule.activity.isFinishing)
  }

  @Test
  fun `back on the devices page leaves the session alone rather than buzzing`() {
    BroadcastStatus.started()
    route()
    showDevices()
    pressBack()
    assertEquals("returning to the session was felt as a toggle", emptyList<HapticFeedbackType>(), haptics.performed)
  }

  @Test
  fun `back after returning to the session is the toggle again`() {
    // The two meanings are per page rather than a mode the app gets stuck in.
    BroadcastStatus.started()
    route()
    showDevices()
    pressBack()
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
    pressBack()
    assertEquals("fi.nikosavola.wristbeat.action.STOP", shadowOf(application).nextStartedService?.action)
  }

  /** Turns to the devices page the way the screen offers: by tapping the receiver count. */
  private fun showDevices() {
    rule.onNodeWithContentDescription("No receivers connected").performClick()
    rule.waitForIdle()
  }

  @Test
  fun `the button starts the session through the service`() {
    route()
    rule.onNodeWithContentDescription("Start broadcasting").performClick()
    rule.waitForIdle()
    assertEquals(
      HeartRateBroadcastService::class.java.name,
      shadowOf(application).nextStartedService?.component?.className,
    )
  }

  @Test
  fun `the button stops a session that is already running`() {
    BroadcastStatus.started()
    route()
    rule.onNodeWithContentDescription("Stop broadcasting").performClick()
    rule.waitForIdle()
    assertEquals("fi.nikosavola.wristbeat.action.STOP", shadowOf(application).nextStartedService?.action)
  }

  @Test
  fun `what the session publishes is what the screen shows`() {
    // The last link in the chain, from the service's own state to the page: the reading the watch's
    // sensor produced is the one on screen, announced as a heart rate rather than as a number.
    BroadcastStatus.started()
    BroadcastStatus.heartRateChanged(72)
    route()
    rule.onNodeWithContentDescription("Heart rate 72 beats per minute").assertIsDisplayed()
  }

  @Test
  fun `the screen shows why a session that could not start did not`() {
    BroadcastStatus.rejected(BroadcastRejection.NoHeartRateSensor)
    route()
    rule.onNodeWithText("No heart rate sensor").assertIsDisplayed()
  }
}

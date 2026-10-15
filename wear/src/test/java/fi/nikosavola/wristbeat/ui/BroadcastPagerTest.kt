package fi.nikosavola.wristbeat.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.foundation.pager.rememberPagerState
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import fi.nikosavola.wristbeat.ui.theme.WristbeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The two pages together, which is the whole app: that the session page is what opens, and that the
 * devices page has two ways of being reached - the swipe the design assumes and the tap that is
 * there for anyone who cannot make the swipe.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w227dp-h227dp")
class BroadcastPagerTest {

  @get:Rule val rule = createComposeRule()

  private val laptop = ConnectedDevice("Laptop", ADDRESS, 0L)

  private fun pager(devices: List<ConnectedDevice> = listOf(laptop)) {
    rule.setContent {
      WristbeatTheme {
        BroadcastPager(
          pagerState = rememberPagerState(pageCount = { PAGE_COUNT }),
          broadcasting = true,
          heartRateBpm = 68,
          connectedDevices = devices,
          rejection = null,
          activeMode = false,
          onToggle = {},
          onActiveModeChange = {},
        )
      }
    }
  }

  @Test
  fun `the app opens on the session, not on the list of receivers`() {
    pager()
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
    rule.onNodeWithText(ADDRESS).assertDoesNotExist()
  }

  @Test
  fun `tapping the count turns to the devices page`() {
    pager()
    rule.onNodeWithContentDescription("Receivers connected: 1").performClick()
    rule.waitForIdle()
    rule.onNodeWithText(ADDRESS).assertIsDisplayed()
  }

  @Test
  fun `swiping the session page away turns to the devices page`() {
    pager()
    rule.onRoot().performTouchInput { swipeLeft() }
    rule.waitForIdle()
    rule.onNodeWithText(ADDRESS).assertIsDisplayed()
  }

  @Test
  fun `swiping back turns to the session page again`() {
    pager()
    rule.onRoot().performTouchInput { swipeLeft() }
    rule.waitForIdle()
    rule.onRoot().performTouchInput { swipeRight() }
    rule.waitForIdle()
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
  }

  @Test
  fun `the bezel turns to the devices page`() {
    pager()
    turnBezel(BEZEL_DETENT)
    rule.onNodeWithText(ADDRESS).assertIsDisplayed()
  }

  @Test
  fun `the bezel turns back to the session page`() {
    pager()
    turnBezel(BEZEL_DETENT)
    turnBezel(-BEZEL_DETENT)
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
  }

  @Test
  fun `an empty devices page does not keep the bezel from turning back`() {
    // The list only takes the bezel when it has somewhere to scroll, which with nobody connected it
    // does not, so the pager still gets it.
    pager(devices = emptyList())
    turnBezel(BEZEL_DETENT)
    rule.onNodeWithContentDescription(EMPTY_PAGE_DESCRIPTION).assertIsDisplayed()
    turnBezel(-BEZEL_DETENT)
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
  }

  /** Rotary input is an experimental corner of the test API, and the only way to turn a bezel here. */
  @OptIn(ExperimentalTestApi::class)
  private fun turnBezel(pixels: Float) {
    rule.onRoot().performRotaryScrollInput { rotateToScrollVertically(pixels) }
    rule.waitForIdle()
  }

  private companion object {
    const val ADDRESS = "AA:AA:AA:AA:AA:AA"

    /** The empty page's single rune, which is what says the devices page is the one showing. */
    const val EMPTY_PAGE_DESCRIPTION =
      "Connected devices. No receiver has connected yet. Pair this watch as a heart rate sensor from the device you want to read it."

    /** One detent: the platform reports a bezel turn in pixels along the scroll axis. */
    const val BEZEL_DETENT = 128f
  }
}

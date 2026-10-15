package fi.nikosavola.wristbeat.ui

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import fi.nikosavola.wristbeat.ui.theme.WristbeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The devices page on a 227dp round screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w227dp-h227dp")
class DevicesScreenTest {

  @get:Rule val rule = createComposeRule()

  /** What the toggle was last asked to become. */
  private val activeModeChanges = mutableListOf<Boolean>()

  private fun screen(
    devices: List<ConnectedDevice>,
    activeMode: Boolean = false,
    onActiveModeChange: (Boolean) -> Unit = { activeModeChanges += it },
  ) {
    rule.setContent {
      WristbeatTheme {
        DevicesScreen(
          devices = devices,
          activeMode = activeMode,
          onActiveModeChange = onActiveModeChange,
        )
      }
    }
  }

  private fun connected(millisAgo: Long, name: String? = null, address: String = ADDRESS) =
    ConnectedDevice(
      name = name,
      address = address,
      connectedAtElapsedMillis = SystemClock.elapsedRealtime() - millisAgo,
    )

  @Test
  fun `a page with nobody on it names itself and says what to do about it, in one node`() {
    screen(emptyList())
    // One rune, carrying both the page's name and what to do next. An empty page that drew a header
    // as well would be showing the same rune twice, which reads as a rendering mistake.
    rule.onNodeWithContentDescription(EMPTY_PAGE_DESCRIPTION).assertIsDisplayed()
    rule.onNodeWithContentDescription("Connected devices").assertDoesNotExist()
    rule.onNode(hasContentDescription("Pair this watch", substring = true)).assertIsDisplayed()
  }

  @Test
  fun `a page with somebody on it names itself in its header instead`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")))
    rule.onNodeWithContentDescription("Connected devices").assertIsDisplayed()
    rule.onNodeWithContentDescription(EMPTY_PAGE_DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun `a receiver is listed by its name`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")))
    rule.onNodeWithText("Laptop").assertIsDisplayed()
  }

  @Test
  fun `a receiver with no name is listed by its address, and not twice`() {
    // The ordinary case: a peripheral has no name for a central it is not bonded to. The address
    // stands in for one and must not then be repeated as the third line.
    screen(listOf(connected(millisAgo = 0, name = null)))
    assertEquals(1, rule.onAllNodesWithText(ADDRESS).fetchSemanticsNodes().size)
  }

  @Test
  fun `a named receiver shows its address underneath`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")))
    rule.onNodeWithText("Laptop").assertIsDisplayed()
    rule.onNodeWithText(ADDRESS).assertIsDisplayed()
  }

  @Test
  fun `how long a receiver has been connected is on the page`() {
    // Worded by the platform rather than by this app, which is why the expected wording here is
    // DateUtils' and not a string resource.
    screen(listOf(connected(millisAgo = 5 * 60_000L, name = "Laptop")))
    rule.onNodeWithText("5 minutes ago").assertIsDisplayed()
  }

  @Test
  fun `every receiver is listed, oldest first`() {
    screen(
      listOf(
        connected(millisAgo = 60_000L, name = "Laptop", address = "AA:AA:AA:AA:AA:AA"),
        connected(millisAgo = 0, name = "Bike computer", address = "BB:BB:BB:BB:BB:BB"),
      )
    )
    rule.onNodeWithText("Laptop").assertIsDisplayed()
    rule.onNodeWithText("Bike computer").assertIsDisplayed()
  }

  @Test
  fun `the active mode switch is on the page, and off until it is asked for`() {
    screen(emptyList())
    rule.onNodeWithContentDescription(ACTIVE_MODE_DESCRIPTION).assertIsDisplayed().assertIsOff()
  }

  @Test
  fun `a switch that is on says so`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")), activeMode = true)
    rule.onNodeWithContentDescription(ACTIVE_MODE_DESCRIPTION).assertIsOn()
  }

  @Test
  fun `pressing the switch asks for the state it is not in`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")), activeMode = false)
    rule.onNodeWithContentDescription(ACTIVE_MODE_DESCRIPTION).performClick()
    assertEquals(listOf(true), activeModeChanges)
  }

  @Test
  fun `pressing the switch while it is on asks for it to go off`() {
    screen(listOf(connected(millisAgo = 0, name = "Laptop")), activeMode = true)
    rule.onNodeWithContentDescription(ACTIVE_MODE_DESCRIPTION).performClick()
    assertEquals(listOf(false), activeModeChanges)
  }

  private companion object {
    const val ADDRESS = "AA:AA:AA:AA:AA:AA"

    /** What a screen reader says about the session's one setting. */
    const val ACTIVE_MODE_DESCRIPTION =
      "Keep the watch awake. Uses more battery, and keeps the heart rate going while the screen is off."

    /** The empty page's rune says what the page is and what to do about it, in one sentence. */
    const val EMPTY_PAGE_DESCRIPTION =
      "Connected devices. No receiver has connected yet. Pair this watch as a heart rate sensor from the device you want to read it."
  }
}

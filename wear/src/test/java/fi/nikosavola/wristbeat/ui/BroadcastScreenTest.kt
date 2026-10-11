package fi.nikosavola.wristbeat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import fi.nikosavola.wristbeat.ui.theme.WristbeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The session page on a 227dp round screen.
 *
 * The assertions are mostly about what a screen reader is told, because that is where this screen's
 * words live now: the visible side of it is icons, a number and a dash.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w227dp-h227dp")
class BroadcastScreenTest {

  @get:Rule val rule = createComposeRule()

  private val laptop = ConnectedDevice("Laptop", "AA:AA:AA:AA:AA:AA", 0L)
  private val bike = ConnectedDevice("Bike computer", "BB:BB:BB:BB:BB:BB", 0L)

  private var toggles = 0
  private var devicesOpened = 0
  private val haptics = RecordingHaptics()

  @Before
  fun countFromZero() {
    toggles = 0
    devicesOpened = 0
    haptics.performed.clear()
  }

  private fun screen(
    broadcasting: Boolean = false,
    bpm: Int? = null,
    devices: List<ConnectedDevice> = emptyList(),
    rejection: BroadcastRejection? = null,
  ) {
    rule.setContent {
      // Provided rather than left to the host, so the haptic this screen asks for is what the test
      // reads, and not a vibration on whatever machine is running it.
      CompositionLocalProvider(LocalHapticFeedback provides haptics) {
        WristbeatTheme {
          BroadcastScreen(
            broadcasting = broadcasting,
            heartRateBpm = bpm,
            connectedDevices = devices,
            rejection = rejection,
            onToggle = { toggles++ },
            onShowDevices = { devicesOpened++ },
          )
        }
      }
    }
  }

  @Test
  fun `starting buzzes with the platform's own toggle-on haptic`() {
    screen()
    rule.onNodeWithContentDescription("Start broadcasting").performClick()
    assertEquals(listOf(HapticFeedbackType.ToggleOn), haptics.performed)
  }

  @Test
  fun `stopping buzzes differently from starting`() {
    screen(broadcasting = true, bpm = 68)
    rule.onNodeWithContentDescription("Stop broadcasting").performClick()
    assertEquals(listOf(HapticFeedbackType.ToggleOff), haptics.performed)
  }

  @Test
  @Config(sdk = [30])
  fun `a watch too old for the toggle haptics still buzzes`() {
    // The toggle haptics are API 34, and this app runs on API 30. A silent wrist is worse than a
    // blunter haptic.
    screen()
    rule.onNodeWithContentDescription("Start broadcasting").performClick()
    assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
  }

  @Test
  fun `nothing else on the screen buzzes`() {
    // The count is a second control on this page, and it opens a read-only page. A wrist that buzzes
    // for that is a wrist that buzzes for everything.
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    rule.onNodeWithContentDescription("Receivers connected: 1").performClick()
    assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
  }

  @Test
  fun `an idle screen says it is not broadcasting, and offers to start`() {
    screen()
    rule.onNodeWithContentDescription("Not broadcasting").assertIsDisplayed()
    rule.onNodeWithContentDescription("Start broadcasting").assertIsDisplayed()
  }

  @Test
  fun `the control is named by what pressing it does, not by a label`() {
    screen()
    rule.onNodeWithContentDescription("Start broadcasting").performClick()
    assertEquals(1, toggles)
    rule.onNodeWithContentDescription("Stop broadcasting").assertDoesNotExist()
  }

  @Test
  fun `a running broadcast offers to stop instead`() {
    screen(broadcasting = true, bpm = 68)
    rule.onNodeWithContentDescription("Stop broadcasting").assertIsDisplayed()
    rule.onNodeWithContentDescription("Stop broadcasting").performClick()
    assertEquals(1, toggles)
    rule.onNodeWithContentDescription("Start broadcasting").assertDoesNotExist()
  }

  @Test
  fun `a reading is announced as a heart rate, not read out as a bare number`() {
    screen(broadcasting = true, bpm = 68)
    rule.onNodeWithContentDescription("Heart rate 68 beats per minute").assertIsDisplayed()
  }

  @Test
  fun `a single beat is announced in the singular`() {
    screen(broadcasting = true, bpm = 1)
    rule.onNodeWithContentDescription("Heart rate 1 beat per minute").assertIsDisplayed()
  }

  @Test
  fun `the digits are not exposed on their own when the icon beside them says what they are`() {
    screen(broadcasting = true, bpm = 68)
    // The row is one node with a description, so a screen reader does not land on a stray "68".
    rule.onNodeWithText("68").assertDoesNotExist()
  }

  @Test
  fun `a broadcast with no reading yet says it is waiting rather than showing a number`() {
    screen(broadcasting = true)
    rule.onNodeWithContentDescription("Waiting for a heart rate reading").assertIsDisplayed()
  }

  @Test
  fun `the count is announced as receivers rather than as a bare number`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop, bike))
    rule.onNodeWithContentDescription("Receivers connected: 2").assertIsDisplayed()
    // The number is part of that one announcement rather than a node a screen reader lands on after
    // it, which would read out "2" on its own.
    rule.onNodeWithText("2").assertDoesNotExist()
  }

  @Test
  fun `the count is not read out as the words it used to be`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop, bike))
    rule.onNodeWithText("2 connected").assertDoesNotExist()
  }

  @Test
  fun `nobody connected is announced without a zero on the screen`() {
    screen(broadcasting = true, bpm = 68)
    rule.onNodeWithContentDescription("No receivers connected").assertIsDisplayed()
    rule.onNodeWithText("0").assertDoesNotExist()
  }

  @Test
  fun `nothing is offered to connect to before the broadcast is running`() {
    screen()
    rule.onNodeWithContentDescription("No receivers connected").assertDoesNotExist()
  }

  @Test
  fun `tapping the count opens the devices page`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(bike))
    rule.onNodeWithContentDescription("Receivers connected: 1").performClick()
    assertEquals(1, devicesOpened)
  }

  @Test
  fun `the reading sits just above the middle of the screen`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    val screen = rule.onRoot().getUnclippedBoundsInRoot()
    val reading =
      rule.onNodeWithContentDescription("Heart rate 68 beats per minute").getUnclippedBoundsInRoot()
    val readingCentre = (reading.top + reading.bottom) / 2
    val screenCentre = (screen.top + screen.bottom) / 2
    assertReadingIsJustAboveTheMiddle(readingCentre, screenCentre, "with a count on the row")
  }

  @Test
  fun `the count on the row does not change where the reading sits`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop, bike))
    val screen = rule.onRoot().getUnclippedBoundsInRoot()
    val reading =
      rule.onNodeWithContentDescription("Heart rate 68 beats per minute").getUnclippedBoundsInRoot()
    val readingCentre = (reading.top + reading.bottom) / 2
    val screenCentre = (screen.top + screen.bottom) / 2
    assertReadingIsJustAboveTheMiddle(readingCentre, screenCentre, "with two connected")
  }

  @Test
  fun `the reading does not move when a session is toggled`() {
    // The count row is the only thing that comes and goes, so it is the only thing that could move
    // the reading, and the reading is what the wearer is looking at when they press the button.
    screen()
    val screen = rule.onRoot().getUnclippedBoundsInRoot()
    val reading = rule.onNodeWithContentDescription("Not broadcasting").getUnclippedBoundsInRoot()
    val readingCentre = (reading.top + reading.bottom) / 2
    val screenCentre = (screen.top + screen.bottom) / 2
    assertReadingIsJustAboveTheMiddle(readingCentre, screenCentre, "with no session running")
  }

  /** The reading is drawn a little above the middle of the screen, and by the same little in every
   * state, which is the whole of what these three tests are about. */
  private fun assertReadingIsJustAboveTheMiddle(readingCentre: Dp, screenCentre: Dp, where: String) {
    val above = screenCentre - readingCentre
    assertTrue(
      "the reading sits $above above the middle $where, not the $READING_LIFT it is drawn at",
      above.value in (READING_LIFT - CENTRE_TOLERANCE).value..(READING_LIFT + CENTRE_TOLERANCE).value,
    )
  }

  @Test
  @Config(qualifiers = "w192dp-h192dp")
  fun `the reading sits the same way on the smallest screen the app supports`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    val screen = rule.onRoot().getUnclippedBoundsInRoot()
    val reading =
      rule.onNodeWithContentDescription("Heart rate 68 beats per minute").getUnclippedBoundsInRoot()
    val readingCentre = (reading.top + reading.bottom) / 2
    val screenCentre = (screen.top + screen.bottom) / 2
    assertReadingIsJustAboveTheMiddle(readingCentre, screenCentre, "at 192dp")
  }

  @Test
  fun `the count sits above the reading rather than below it`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    val reading =
      rule.onNodeWithContentDescription("Heart rate 68 beats per minute").getUnclippedBoundsInRoot()
    val count =
      rule.onNodeWithContentDescription("Receivers connected: 1").getUnclippedBoundsInRoot()
    assertTrue(
      "the count at $count is not above the reading at $reading",
      count.bottom <= reading.top,
    )
  }

  @Test
  fun `a refusal replaces the readings with the reason, in words`() {
    screen(broadcasting = false, rejection = BroadcastRejection.NoHeartRateSensor)
    rule.onNodeWithText("No heart rate sensor").assertIsDisplayed()
    rule.onNodeWithContentDescription("Not broadcasting").assertDoesNotExist()
  }

  @Test
  fun `the control is a large enough target to hit`() {
    screen()
    rule.onNodeWithContentDescription("Start broadcasting").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `the control stays a large enough target while the broadcast is running`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    rule.onNodeWithContentDescription("Stop broadcasting").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `the count is a large enough target to hit`() {
    screen(broadcasting = true, bpm = 68, devices = listOf(laptop))
    rule.onNodeWithContentDescription("Receivers connected: 1").assertHeightIsAtLeast(48.dp)
  }

  private companion object {
    /** How far above the middle the reading is drawn, and how far off that it may measure. The
     * tolerance is for a rounding difference rather than for a layout that is nearly right: the
     * mistakes this catches are 20dp and up, whether from putting the count on the wrong side of the
     * reading or from losing the lift when the count row comes and goes. */
    val READING_LIFT = 8.dp
    val CENTRE_TOLERANCE = 4.dp
  }
}

package fi.nikosavola.wristbeat.ui

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * The haptic a session toggle asks for, in the direction it is going.
 *
 * Shared rather than written twice, because the button and the back gesture are the same action
 * reached two ways and a wrist should not be able to tell them apart. The platform's own toggle
 * haptics are used rather than a generic tap: this is the one thing the app does, and doing it or
 * undoing it are worth telling apart without looking.
 *
 * Those two are API 34 and are no-ops below it, which the app's floor of API 30 reaches into, so the
 * long-press haptic stands in there. It is blunter than a toggle haptic, but a wrist that feels
 * nothing at all is the one outcome not worth shipping.
 */
internal fun HapticFeedback.toggleHaptic(broadcasting: Boolean) {
  val toggleHapticsExist = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
  performHapticFeedback(
    when {
      !toggleHapticsExist -> HapticFeedbackType.LongPress
      broadcasting -> HapticFeedbackType.ToggleOff
      else -> HapticFeedbackType.ToggleOn
    }
  )
}

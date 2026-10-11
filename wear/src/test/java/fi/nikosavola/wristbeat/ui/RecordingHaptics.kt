package fi.nikosavola.wristbeat.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Records what a screen asked the wrist to do, so a test never vibrates anything. */
internal class RecordingHaptics : HapticFeedback {
  val performed = mutableListOf<HapticFeedbackType>()

  override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
    performed += hapticFeedbackType
  }
}

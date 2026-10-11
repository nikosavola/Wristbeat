package fi.nikosavola.wristbeat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * dynamicColorScheme derives the palette from the colours the watch is themed with, and returns null
 * when the device cannot supply one. That gate is the platform's rather than this app's: API 35,
 * which is Wear OS 5.1, and the dynamic_color_theme_enabled global setting. A Galaxy Watch 8 leaves
 * that setting unset, so the fallback is what a real watch runs, while the emulator switches it on
 * and is where the dynamic palette was measured. Falling through to no colorScheme argument keeps
 * Wear Compose Material 3's own default in the first case, which is the black-backgrounded dark
 * scheme this app wants anyway.
 *
 * Called on every composition of this rather than remembered, which is what the library shows and
 * what its contract implies: it reads the system colours each time, so a palette that changed with
 * the watch face is picked up rather than frozen for as long as this composition lives. The cost is
 * a read of the system colour resources, and this has no parameters, so it composes about once.
 *
 * The scheme is built from the platform's dark colour resources throughout, so it follows the watch's
 * accent but never its light mode. Its background role is the darkest shade of the watch's own
 * neutral palette rather than anything chosen here: pure black in the fallback, and near it but not
 * black - 0x121318 under the stock AOSP resources - when a dynamic palette is in effect. The black
 * background WO-V13 asks for is the window behind all of this, set in themes.xml, which is black
 * either way.
 */
@Composable
fun WristbeatTheme(content: @Composable () -> Unit) {
  val dynamicScheme = dynamicColorScheme(LocalContext.current)
  if (dynamicScheme != null) {
    MaterialTheme(colorScheme = dynamicScheme, content = content)
  } else {
    MaterialTheme(content = content)
  }
}

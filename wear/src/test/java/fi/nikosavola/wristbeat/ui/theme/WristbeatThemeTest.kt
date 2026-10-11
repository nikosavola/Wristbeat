package fi.nikosavola.wristbeat.ui.theme

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The palette, and the gate the platform puts in front of it.
 *
 * The gate is the watch's own `dynamic_color_theme_enabled` setting, so a watch that has never had it
 * turned on runs the fallback scheme while one that has runs its own accent. Which of the two is
 * running is invisible in the app, so it is pinned here rather than on a screen.
 *
 * What the background does under each is not the same thing, and the difference is the platform's
 * rather than this app's: see [the background role]. The window behind it all is black either way,
 * which is what the black background is asked of a Wear app for.
 */
@RunWith(AndroidJUnit4::class)
class WristbeatThemeTest {

  @get:Rule val rule = createComposeRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  @Test
  fun `a watch that offers no accent gets the default scheme`() {
    setDynamicAccent(false)
    assertNull("an accent was offered anyway", dynamicColorScheme(context))
    val scheme = composedScheme()
    // Wear Compose Material 3's own default, measured off the watch: a lavender on black.
    assertEquals(ComposeColor(0xFFE9DDFF), scheme.primary)
    assertEquals(Color.BLACK, scheme.background.toArgb())
  }

  @Test
  fun `a watch with an accent gets that accent`() {
    setDynamicAccent(true)
    val accent = requireNotNull(dynamicColorScheme(context)) { "no accent was offered" }
    assertEquals(accent.primary, composedScheme().primary)
  }

  @Test
  fun `the background role comes from the watch's palette, and is dark`() {
    // Not this app's choice: the scheme's background is the darkest shade of the platform's neutral
    // palette. Pure black on the fallback and on the emulator, and #121318 under the stock AOSP
    // resources this runs against, so what is pinned here is that it stays a dark background rather
    // than the exact value, which is the watch's to decide.
    setDynamicAccent(true)
    val background = composedScheme().background
    assertTrue("not a dark background: $background", background.luminance() < 0.02f)
  }

  @Test
  fun `the window behind the app is black, which is what a Wear app is asked for`() {
    // The one black background the app does choose, and the one that covers the window between the
    // process starting and Compose drawing.
    val background =
      context
        .obtainStyledAttributes(
          null,
          intArrayOf(android.R.attr.windowBackground),
          0,
          fi.nikosavola.wristbeat.R.style.Theme_Wristbeat,
        )
        .let {
          try {
            it.getDrawable(0)
          } finally {
            it.recycle()
          }
        }
    assertEquals(Color.BLACK, (background as ColorDrawable).color)
  }

  private fun setDynamicAccent(enabled: Boolean) {
    Settings.Global.putInt(
      context.contentResolver,
      "dynamic_color_theme_enabled",
      if (enabled) 1 else 0,
    )
  }

  private fun composedScheme(): ColorScheme {
    var scheme: ColorScheme? = null
    rule.setContent { Capture { scheme = it } }
    rule.waitForIdle()
    return requireNotNull(scheme) { "the theme composed without a scheme" }
  }

  @Composable
  private fun Capture(record: (ColorScheme) -> Unit) {
    WristbeatTheme { record(MaterialTheme.colorScheme) }
  }
}

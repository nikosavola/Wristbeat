package fi.nikosavola.wristbeat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one setting the app has: that a watch which has never been asked leaves itself to idle, and
 * that an answer outlives the process that gave it.
 */
@RunWith(AndroidJUnit4::class)
class ActiveModeTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun startFromNothingSaid() {
    // The store is process-wide and so outlives a test method.
    ActiveMode.forget()
  }

  @Test
  fun `a watch that has never been asked leaves itself to idle`() {
    ActiveMode.load(context)
    assertFalse(ActiveMode.enabled.value)
  }

  @Test
  fun `a choice is live as soon as it is made`() {
    ActiveMode.set(context, true)
    assertTrue(ActiveMode.enabled.value)
    ActiveMode.set(context, false)
    assertFalse(ActiveMode.enabled.value)
  }

  @Test
  fun `a choice is read back when the process starts again`() {
    ActiveMode.set(context, true)
    // What the next process does with what this one left behind.
    ActiveMode.forget()
    ActiveMode.load(context)
    assertTrue(ActiveMode.enabled.value)
  }
}

package fi.nikosavola.wristbeat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the ongoing notification says. The wording is decided here, on its own, because it is the one
 * place the app says anything at all; how often it is allowed to say it is the service's, and is
 * tested there, where it runs.
 */
@RunWith(AndroidJUnit4::class)
class NotificationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `it says the reading once there is one`() {
    assertEquals("79 bpm", notificationContent(context, bpm = 79, receivers = 0))
  }

  @Test
  fun `it says it is waiting rather than showing a reading it does not have`() {
    assertEquals("No reading yet", notificationContent(context, bpm = null, receivers = 0))
  }

  @Test
  fun `it names the receiver reading it, in the singular`() {
    assertEquals("79 bpm, 1 receiver", notificationContent(context, bpm = 79, receivers = 1))
  }

  @Test
  fun `it counts the receivers rather than naming one twice`() {
    assertEquals("79 bpm, 2 receivers", notificationContent(context, bpm = 79, receivers = 2))
  }

  @Test
  fun `receivers are counted even before the first reading arrives`() {
    // A receiver can subscribe before the sensor has said anything, and "no reading yet, 1 receiver"
    // is the honest state rather than one that hides the connection.
    assertEquals("No reading yet, 1 receiver", notificationContent(context, bpm = null, receivers = 1))
  }
}

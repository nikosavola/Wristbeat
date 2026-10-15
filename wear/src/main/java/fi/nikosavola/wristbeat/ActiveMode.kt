package fi.nikosavola.wristbeat

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a session keeps the watch awake instead of letting it idle.
 *
 * Off by default, because a session is meant to be left running and idling is what makes that
 * affordable. On, the sensor keeps reporting at the rate the watch reports it while the screen is
 * off, which a receiver that wants a steady pulse is better served by than the readings a suspended
 * watch delivers in bursts.
 *
 * Written down rather than held in memory, so the choice outlives the process that made it.
 */
internal object ActiveMode {
  private const val PREFERENCES = "wristbeat"
  private const val ENABLED = "active_mode"

  val enabled: StateFlow<Boolean>
    field = MutableStateFlow(false)

  /** Set once the store has been read, so every entry point can call [load] without stat-ing a file
   * on the main thread more than once. */
  @Volatile private var loaded = false

  /** Reads the stored choice. Idempotent: the activity and the service both get here, and whichever
   * arrives first does the reading. */
  fun load(context: Context) {
    if (loaded) return
    loaded = true
    enabled.value = preferences(context).getBoolean(ENABLED, false)
  }

  fun set(context: Context, value: Boolean) {
    loaded = true
    enabled.value = value
    preferences(context).edit { putBoolean(ENABLED, value) }
  }

  /** Back to unread, for tests that share one process with each other. Nothing at runtime forgets a
   * choice the wearer made. */
  internal fun forget() {
    loaded = false
    enabled.value = false
  }

  private fun preferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

package fi.nikosavola.wristbeat.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import fi.nikosavola.wristbeat.HeartRateBroadcastService
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long to wait for the broadcast service to say what it did. Starting a foreground service is
 * asynchronous: the request is queued and run on this app's main thread, so the answer arrives a
 * moment after the call returns.
 */
private const val SETTLE_MILLIS = 5_000L

/**
 * The app's session, exposed to Android's AI agents.
 *
 * Deliberately thin: every function here does what the one button on the watch's screen does, and
 * nothing more, so an agent cannot put the watch into a state the wearer could not have reached by
 * tapping it. Nothing here starts a session the wearer has not already granted the permissions for,
 * and a refusal comes back as `false` rather than as an exception, because why a session would not
 * start is already reported on the watch.
 *
 * Nothing is stored between calls: whether the watch is broadcasting is a fact about this process,
 * which the running service keeps alive.
 *
 * The API level is the platform's, not this app's: AppFunctions does not exist before Android 16, so
 * the system has nothing to bind this to on an older watch. Declaring it below that is harmless,
 * which is why the manifest can stay ungated.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
  serviceName = "BroadcastAppFunctionsService",
  appFunctionXmlFileName = "broadcast_app_function_service",
)
abstract class BroadcastAppFunctions : AppFunctionService() {

  /**
   * Turns the heart rate broadcast on if it is off, and off if it is on.
   *
   * This is the same action as pressing the play or pause button on the watch, so it needs the
   * wearer to have granted the Bluetooth and heart rate permissions already. Use it when asked to
   * share the watch's heart rate with gym equipment or a bike computer, and when asked to stop
   * sharing it.
   *
   * @return whether the watch is broadcasting heart rate once the change has taken effect. False
   * means the session did not start, which is also what the watch will be showing a reason for.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = true)
  suspend fun toggleBroadcasting(): Boolean {
    val was = BroadcastStatus.broadcasting.value
    if (was) {
      HeartRateBroadcastService.stop(this)
    } else {
      // An agent cannot be shown the permission prompts the button raises, so a start that would
      // need them is refused here. Starting it anyway is not merely useless: a service that is
      // asked to go foreground and then stops before doing so is killed by the platform, which
      // takes the whole process with it, so the caller would get a crash instead of an answer.
      if (BroadcastPermissions.missing(this, BroadcastPermissions.required).isNotEmpty()) {
        // Published so the reason reaches the wearer on the watch, which is the only place it can
        // be acted on.
        BroadcastStatus.rejected(BroadcastRejection.MissingPermission)
        return false
      }
      BroadcastStatus.attempting()
      try {
        HeartRateBroadcastService.start(this)
      } catch (_: IllegalStateException) {
        // Android refuses a foreground service asked for from the background, which is where an
        // agent invocation lands when the app is not on screen. Reported rather than thrown so the
        // caller gets an answer instead of a stack trace.
        return false
      }
    }
    return awaitSettled(was)
  }

  /**
   * Whether the watch is broadcasting its heart rate right now.
   *
   * Read this before assuming a session needs starting, and to tell a wearer who has forgotten
   * whether they left it running.
   *
   * @return true while the watch is advertising itself as a heart rate sensor.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = true)
  suspend fun isBroadcasting(): Boolean = BroadcastStatus.broadcasting.value

  /**
   * Waits for the session to stop being what it was, then reports what it is now.
   *
   * Both the state and any refusal are written on the main thread, and a StateFlow replays its
   * current value, so an outcome that landed before this began collecting is still seen rather than
   * waited out. A start that cannot even be attempted is caught above; this covers the one that is
   * attempted and refused, which the service reports a reason for.
   */
  private suspend fun awaitSettled(was: Boolean): Boolean {
    withTimeoutOrNull(SETTLE_MILLIS) {
      combine(BroadcastStatus.broadcasting, BroadcastStatus.rejection) { broadcasting, rejection ->
        broadcasting != was || rejection != null
      }
        .first { it }
    }
    return BroadcastStatus.broadcasting.value
  }
}

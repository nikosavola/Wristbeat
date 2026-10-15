package fi.nikosavola.wristbeat.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.rememberPagerState
import fi.nikosavola.wristbeat.ActiveMode
import fi.nikosavola.wristbeat.HeartRateBroadcastService
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import kotlinx.coroutines.launch

/**
 * Wires [BroadcastPager] to the service and to the runtime permissions the service needs.
 *
 * Starting is gated on the permissions here rather than failing inside the service, so the first
 * tap on a fresh install turns into the system permission prompts instead of an error.
 */
@Composable
fun BroadcastRoute(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
  val broadcasting by BroadcastStatus.broadcasting.collectAsStateWithLifecycle()
  val heartRateBpm by BroadcastStatus.heartRateBpm.collectAsStateWithLifecycle()
  val connectedDevices by BroadcastStatus.connectedDevices.collectAsStateWithLifecycle()
  val rejection by BroadcastStatus.rejection.collectAsStateWithLifecycle()
  val activeMode by ActiveMode.enabled.collectAsStateWithLifecycle()

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      // Re-checked against the platform rather than read from the result map: there are two
      // permission controllers in play (the notification one and, on API 36, Health Connect's), and
      // they do not report through this contract consistently.
      if (BroadcastPermissions.allGranted(context, BroadcastPermissions.required)) {
        HeartRateBroadcastService.start(context)
      } else {
        BroadcastStatus.rejected(BroadcastRejection.MissingPermission)
      }
    }

  val toggle = {
    if (broadcasting) {
      HeartRateBroadcastService.stop(context)
    } else {
      val missing = BroadcastPermissions.missing(context, BroadcastPermissions.required)
      if (missing.isEmpty()) {
        HeartRateBroadcastService.start(context)
      } else {
        // The optional ones ride along so the user answers every prompt once, in one flow.
        permissionLauncher.launch(
          (missing + BroadcastPermissions.optional).toTypedArray()
        )
      }
    }
  }

  // Back is page-aware, because on a watch it is also the dismiss gesture and the two pages are not
  // equally far from the app's edge. On the devices page it goes back to the session, which is what
  // a swipe or a bezel turn would have done and what anyone whose last action was opening the list
  // expects. On the session page there is nothing to go back to, so it is spent on the session's
  // own action and buzzes like the button, being the same action reached a different way. That
  // spends the app's exit on the app: the watch face is then reachable from a home button or a
  // crown, but not from this gesture. That is the trade, and it is deliberate rather than
  // overlooked.
  BackHandler {
    if (pagerState.currentPage != SESSION_PAGE) {
      // Navigation, so no haptic: nothing about the session changed.
      scope.launch { pagerState.animateScrollToPage(SESSION_PAGE) }
    } else {
      haptics.toggleHaptic(broadcasting)
      toggle()
    }
  }

  BroadcastPager(
    pagerState = pagerState,
    broadcasting = broadcasting,
    heartRateBpm = heartRateBpm,
    connectedDevices = connectedDevices,
    rejection = rejection,
    activeMode = activeMode,
    onToggle = toggle,
    // Stored before the service is told anything: the service reads the store when a session
    // starts and follows it while one runs, so there is nothing to push at it either way.
    onActiveModeChange = { ActiveMode.set(context, it) },
    modifier = modifier,
  )
}

package fi.nikosavola.wristbeat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PagerScaffoldDefaults
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import kotlinx.coroutines.launch

internal const val SESSION_PAGE = 0
internal const val DEVICES_PAGE = 1
internal const val PAGE_COUNT = DEVICES_PAGE + 1

/**
 * The whole UI: two pages, the session and who is reading it, in one pager.
 *
 * The AppScaffold / PagerScaffold / HorizontalPager / AnimatedPage nesting is the order Wear
 * Compose Material 3 requires. The scaffold and the pager share one PagerState, which is what draws
 * the page indicator and gives the second page a way to be discovered.
 *
 * The state is the caller's, because back has to know which page is showing before it decides what
 * it means.
 */
@Composable
fun BroadcastPager(
  pagerState: PagerState,
  broadcasting: Boolean,
  heartRateBpm: Int?,
  connectedDevices: List<ConnectedDevice>,
  rejection: BroadcastRejection?,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  AppScaffold(modifier = modifier) {
    HorizontalPagerScaffold(
      pagerState = pagerState,
      pageIndicator = {
        // Only on the devices page. The session page's bottom edge is where its EdgeButton sits,
        // and the indicator brings its own background, which draws over the button.
        if (pagerState.currentPage == DEVICES_PAGE) {
          HorizontalPageIndicator(pagerState = pagerState)
        }
      },
    ) {
      HorizontalPager(
        state = pagerState,
        flingBehavior =
          PagerDefaults.snapFlingBehavior(
            state = pagerState,
            maxFlingPages = 1,
            snapPositionalThreshold = PagerScaffoldDefaults.HighSnapPositionalThreshold,
            snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
          ),
        // The bezel turns between the pages, one page per detent. On the session page that is the
        // only way to reach the devices page without leaving go of the screen, and the session page
        // has nothing of its own for the bezel to scroll. The devices page's list is the only other
        // thing here that wants it, and a list only takes the bezel when it has somewhere to scroll:
        // with nothing connected the pager keeps it. The library's default for a pager is to leave
        // the bezel alone, which is why this is asked for rather than assumed.
        rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(pagerState),
      ) { page ->
        AnimatedPage(pageIndex = page, pagerState = pagerState) {
          when (page) {
            DEVICES_PAGE -> DevicesScreen(devices = connectedDevices)
            else ->
              BroadcastScreen(
                broadcasting = broadcasting,
                heartRateBpm = heartRateBpm,
                connectedDevices = connectedDevices,
                rejection = rejection,
                onToggle = onToggle,
                // Also reachable by tapping the count, so the second page is not swipe-only.
                onShowDevices = { scope.launch { pagerState.animateScrollToPage(DEVICES_PAGE) } },
              )
          }
        }
      }
    }
  }
}

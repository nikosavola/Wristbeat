package fi.nikosavola.wristbeat.ui

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fi.nikosavola.wristbeat.R
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import kotlinx.coroutines.delay

private val HEADER_ICON_SIZE = 20.dp
private val META_ICON_SIZE = 14.dp
private val EMPTY_ICON_SIZE = 32.dp
private val ICON_GAP = 4.dp
private val ITEM_VERTICAL_PADDING = 4.dp
private val ITEM_HORIZONTAL_PADDING = 8.dp
private val LINE_GAP = 2.dp

/** Once a second is enough for a label that reads "3 minutes ago". */
private const val TICK_MILLIS = 1_000L

/**
 * Who is reading the broadcast. The swipe target of the session page, so it is deliberately
 * read-only: nothing here changes what the watch is doing.
 *
 * A row is a name, the age of the connection and an address, with no words between them: the clock
 * glyph and the platform's own relative time say what the second line is.
 */
@Composable
fun DevicesScreen(devices: List<ConnectedDevice>, modifier: Modifier = Modifier) {
  val columnState = rememberTransformingLazyColumnState()
  // Nothing to age when nobody is connected, so the clock does not run for an empty page.
  val ageClock = rememberAgeClock(ticking = devices.isNotEmpty())
  ScreenScaffold(scrollState = columnState, modifier = modifier) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      modifier = Modifier.fillMaxSize(),
    ) {
      if (devices.isEmpty()) {
        // One glyph rather than a header and a body: an empty page drawing the same rune twice reads
        // as a rendering mistake rather than as an empty state. This is the page's rune, and the
        // pairing instructions are in its description.
        item { NothingConnected() }
      } else {
        // The same glyph the session page shows beside the count, so the two pages are visibly about
        // the same thing. Drawn only when there is something under it to head.
        item {
          ListHeader {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              Icon(
                painter = painterResource(R.drawable.ic_receivers),
                contentDescription = stringResource(R.string.devices_header_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(HEADER_ICON_SIZE),
              )
            }
          }
        }
        // Keyed by address: it is the only part of a connected device that is always there and
        // never repeats.
        items(devices, key = { it.address }) { device -> DeviceRow(device, ageClock) }
      }
    }
  }
}

@Composable
private fun DeviceRow(
  device: ConnectedDevice,
  ageClock: State<Long>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(
          horizontal = ITEM_HORIZONTAL_PADDING,
          vertical = ITEM_VERTICAL_PADDING,
        ),
    verticalArrangement = Arrangement.spacedBy(LINE_GAP),
  ) {
    // A peripheral has no name for a central it is not bonded to, so the address standing in for
    // one is the ordinary case rather than the fallback. It is not repeated on the last line when it
    // is already up here.
    Text(
      text = device.name ?: device.address,
      style = MaterialTheme.typography.titleSmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        painter = painterResource(R.drawable.ic_clock),
        // Decorative: the relative time beside it is the whole of what this line says.
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(META_ICON_SIZE),
      )
      Spacer(Modifier.width(ICON_GAP))
      RelativeAge(device.connectedAtElapsedMillis, ageClock)
    }
    if (device.name != null) {
      Text(
        text = device.address,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun NothingConnected(modifier: Modifier = Modifier) {
  Icon(
    painter = painterResource(R.drawable.ic_receivers),
    // The pairing instructions are here rather than on screen. No glyph says "pair this watch", and
    // a sentence would be the only body text in the app, but a screen reader still has to be told
    // what to do next.
    contentDescription = stringResource(R.string.devices_empty_description),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.size(EMPTY_ICON_SIZE),
  )
}

/**
 * How long ago the connection was made, worded by the platform.
 *
 * DateUtils owns the wording, so it comes out in the user's own language without this app carrying
 * a translation of it. The monotonic connection time is turned into wall time for the comparison,
 * and the second resolution is what stops a connection made moments ago reading as "0 minutes ago".
 *
 * Reads the clock here rather than being handed a value, so the once-a-second tick recomposes this
 * text and nothing else: not the row it sits in, and not the list around that.
 */
@Composable
private fun RelativeAge(connectedAtElapsedMillis: Long, ageClock: State<Long>) {
  Text(
    text = relativeAge(ageClock.value, connectedAtElapsedMillis),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private fun relativeAge(nowElapsedMillis: Long, connectedAtElapsedMillis: Long): String =
  DateUtils.getRelativeTimeSpanString(
    System.currentTimeMillis() - (nowElapsedMillis - connectedAtElapsedMillis),
    System.currentTimeMillis(),
    DateUtils.SECOND_IN_MILLIS,
  ).toString()

/**
 * The monotonic clock the ages are read against.
 *
 * Bound to the lifecycle rather than left looping. A composition outlives the screen: with the app
 * in the background this page is still composed, so an unbounded loop keeps waking the watch once a
 * second to redraw a screen nobody is looking at, for hours, which is exactly the shape of drain
 * this app cannot afford. `delay` is cancellable, so the tick ends with the page or with the
 * lifecycle, whichever comes first.
 */
@Composable
private fun rememberAgeClock(ticking: Boolean): State<Long> {
  val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner, ticking) {
    if (!ticking) return@LaunchedEffect
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        delay(TICK_MILLIS)
        clock.longValue = SystemClock.elapsedRealtime()
      }
    }
  }
  return clock
}

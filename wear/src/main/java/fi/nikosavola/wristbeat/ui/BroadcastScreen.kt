package fi.nikosavola.wristbeat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fi.nikosavola.wristbeat.R
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.ConnectedDevice

private val CONTENT_GAP = 4.dp
private val HEART_ICON_SIZE = 32.dp
private val RECEIVERS_ICON_SIZE = 16.dp
private val ICON_GAP = 6.dp

/** The minimum touch target the guidelines ask for, as a floor rather than padding: padding would
 * grow with the font size and push the edge button off the smallest screens. */
private val MIN_TOUCH_TARGET = 48.dp

/** Placeholder for "nothing to read yet". A dash is a symbol rather than a word, so unlike the
 * labels it is deliberately not a string resource. */
private const val NO_READING = "--"

/** Half of the count row plus the gap above it, which is how far the reading is nudged down when
 * there is no count row to hold it in the middle of the screen. */
private val HALF_COUNT_ROW = (MIN_TOUCH_TARGET + CONTENT_GAP) / 2

/** How far above the middle of the screen the reading is drawn. Nothing about a round screen puts
 * its centre where it looks, and the edge button takes the bottom of it, so the reading reads as
 * centred from a little above. Small on purpose: at 227dp this is under 4% of the height. */
private val READING_LIFT = 8.dp

/** What an inert state is drawn at. Wear's own disabled controls sit at this opacity, so a dimmed
 * heart reads as "nothing happening" the same way a dimmed button does. */
private const val INACTIVE_ALPHA = 0.38f

private val inactiveTint: Color
  @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INACTIVE_ALPHA)

/**
 * The session page: what is being broadcast right now, and the one control that turns it on or off.
 *
 * There is no prose. The heart and the receiver count carry their meaning from their icon and their
 * number, and their colour says whether they are live, so every word this screen used to show is now
 * a content description instead. State is hoisted, so this stays a pure function of its arguments
 * and can be previewed or tested without a service.
 */
@Composable
fun BroadcastScreen(
  broadcasting: Boolean,
  heartRateBpm: Int?,
  connectedDevices: List<ConnectedDevice>,
  rejection: BroadcastRejection?,
  onToggle: () -> Unit,
  onShowDevices: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnState = rememberTransformingLazyColumnState()
  ScreenScaffold(
    scrollState = columnState,
    modifier = modifier,
    edgeButton = { BroadcastToggle(broadcasting = broadcasting, onClick = onToggle) },
  ) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      // Centre is what the screen looks like at an ordinary font size. At a large one the content
      // is taller than 192dp can hold, and a list at least scrolls it rather than clipping it: the
      // scaffold shrinks the edge button until the user scrolls, which is its designed behaviour.
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
          // The offset is on the pile rather than on the reading, so that the gap between the count
          // and the reading stays the gap and not whatever the lift would leave of it.
          modifier =
            Modifier.offset(
              y = if (broadcasting) -READING_LIFT else HALF_COUNT_ROW - READING_LIFT
            ),
        ) {
          if (rejection != null) {
            RejectionText(rejection)
          } else {
            // The count goes above the reading rather than below it, which is what holds the reading
            // near the middle: the pile is centred, so the lower of the two is the one that lands
            // there. Both states then lift the reading by [READING_LIFT], by the same amount, so
            // that toggling a session does not move the number the wearer is looking at.
            //
            // With no count row above it, the reading is lifted by the half-row it does not have as
            // well, which is what would otherwise leave it sitting high. That is drawn rather than
            // laid out: a filler slot of that height draws the same thing and costs more than it
            // looks, because the scaffold shrinks the edge button as the content grows, and 52dp
            // more content took the button under the 48dp a touch target needs.
            //
            // Nothing can be connected to a broadcast that is not running, so the count is only
            // there once there is a session to be connected to.
            if (broadcasting) {
              ReceiversSummary(connectedDevices, onShowDevices = onShowDevices)
            }
            HeartRateReadout(broadcasting = broadcasting, bpm = heartRateBpm)
          }
        }
      }
    }
  }
}

@Composable
private fun HeartRateReadout(
  broadcasting: Boolean,
  bpm: Int?,
  modifier: Modifier = Modifier,
) {
  // Resolved here rather than inside the semantics block, which is not a composable scope.
  val description =
    when {
      !broadcasting -> stringResource(R.string.heart_rate_idle_description)
      bpm == null -> stringResource(R.string.heart_rate_absent_description)
      else -> pluralStringResource(R.plurals.heart_rate_description, bpm, bpm)
    }
  val tint = if (broadcasting) MaterialTheme.colorScheme.primary else inactiveTint
  Row(
    modifier = modifier.clearAndSetSemantics { contentDescription = description },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_heart),
      // The reading itself is the accessible name for the pair, so the glyph is decoration.
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(HEART_ICON_SIZE),
    )
    Spacer(Modifier.width(ICON_GAP))
    Text(
      text = bpm?.toString() ?: NO_READING,
      style = MaterialTheme.typography.displayMedium,
      color = tint,
    )
  }
}

@Composable
private fun ReceiversSummary(
  devices: List<ConnectedDevice>,
  // Not called onClick, because the semantics action this has to offer after clearing is called
  // that, and the parameter would shadow it.
  onShowDevices: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val count = devices.size
  val clickLabel = stringResource(R.string.show_devices)
  val description =
    if (count == 0) stringResource(R.string.receivers_none_description)
    else stringResource(R.string.receivers_connected_description, count)
  // The name is set on a wrapper whose semantics are cleared rather than on the row itself. A row
  // that contains a number ends up with the name on one node and the press on another, and the
  // number read out on its own; clearing replaces all of it with the single node a screen reader
  // should focus. The press has to be offered again after clearing, which is what the action below
  // is, and it lands on the same callback the touch path uses.
  Box(
    modifier =
      modifier.clearAndSetSemantics {
        contentDescription = description
        role = Role.Button
        // The count changes on its own when a receiver arrives, so it has to be announced.
        liveRegion = LiveRegionMode.Polite
        onClick(label = clickLabel) {
          onShowDevices()
          true
        }
      }
  ) {
    Row(
      // A floor on the height rather than padding around the label, so the finger target holds at a
      // normal font size and does not grow at a large one.
      modifier = Modifier.clickable(onClick = onShowDevices).heightIn(min = MIN_TOUCH_TARGET),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_receivers),
        // The wrapper above is named by the only place the count is spelled out, so this is
        // decoration.
        contentDescription = null,
        tint = if (count == 0) inactiveTint else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(RECEIVERS_ICON_SIZE),
      )
      // No number while nobody is listening: the dimmed icon alone is the "none" reading, which
      // keeps a zero off a screen that has no other numbers competing with it.
      if (count > 0) {
        Spacer(Modifier.width(ICON_GAP))
        Text(
          text = count.toString(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RejectionText(rejection: BroadcastRejection, modifier: Modifier = Modifier) {
  Text(
    text = stringResource(rejection.messageRes()),
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.error,
    textAlign = TextAlign.Center,
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Composable
private fun BroadcastToggle(
  broadcasting: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val label =
    stringResource(
      if (broadcasting) R.string.broadcast_stop_description
      else R.string.broadcast_start_description
    )
  val haptics = LocalHapticFeedback.current
  EdgeButton(
    onClick = {
      // Wrist first, then the session changes underneath it. This is the one control on the screen and
      // it is pressed without looking, so the buzz is how the wearer knows it landed.
      haptics.toggleHaptic(broadcasting)
      onClick()
    },
    // The name goes on the button rather than on the glyph inside it, so it sits on the node a
    // screen reader focuses and not only on the icon within it.
    modifier = modifier.semantics { contentDescription = label },
  ) {
    Icon(
      painter = painterResource(if (broadcasting) R.drawable.ic_pause else R.drawable.ic_play),
      // The button above is named and pressable, so this is decoration.
      contentDescription = null,
      modifier = Modifier.size(EdgeButtonDefaults.LargeIconSize),
    )
  }
}

@StringRes
private fun BroadcastRejection.messageRes(): Int =
  when (this) {
    BroadcastRejection.BluetoothOff -> R.string.rejection_bluetooth_off
    BroadcastRejection.PeripheralUnavailable -> R.string.rejection_peripheral_unavailable
    BroadcastRejection.AdvertiseFailed -> R.string.rejection_advertise_failed
    BroadcastRejection.MissingPermission -> R.string.rejection_missing_permission
    BroadcastRejection.NoHeartRateSensor -> R.string.rejection_no_heart_rate_sensor
  }

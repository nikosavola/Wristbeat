package fi.nikosavola.wristbeat.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI shows, published by the foreground service and read by the activity.
 *
 * A process-wide singleton rather than a bound service: both run in the same process, and the only
 * things crossing between them are a few flags. Nothing here survives process death, which is the
 * point - a restart should not claim to still be broadcasting.
 */
object BroadcastStatus {
  val broadcasting: StateFlow<Boolean>
    field = MutableStateFlow(false)

  /** Last reading from the watch's sensor, or null before the first one arrives. */
  val heartRateBpm: StateFlow<Int?>
    field = MutableStateFlow<Int?>(null)

  /** Every receiver currently subscribed, in the order they subscribed. */
  val connectedDevices: StateFlow<List<ConnectedDevice>>
    field = MutableStateFlow(emptyList())

  /** Why the last start attempt was refused; null once an attempt succeeds. */
  val rejection: StateFlow<BroadcastRejection?>
    field = MutableStateFlow<BroadcastRejection?>(null)

  fun started() {
    rejection.value = null
    heartRateBpm.value = null
    connectedDevices.value = emptyList()
    broadcasting.value = true
  }

  /**
   * A session has been asked for. Clears the previous refusal so that while this attempt is in
   * flight, nothing describes the last one, and so a caller waiting on an outcome cannot be handed
   * the stale answer.
   */
  fun attempting() {
    rejection.value = null
  }

  fun stopped() {
    broadcasting.value = false
    heartRateBpm.value = null
    connectedDevices.value = emptyList()
  }

  fun rejected(reason: BroadcastRejection) {
    rejection.value = reason
    broadcasting.value = false
    heartRateBpm.value = null
    connectedDevices.value = emptyList()
  }

  fun heartRateChanged(bpm: Int) {
    heartRateBpm.value = bpm
  }

  fun connectedDevicesChanged(devices: List<ConnectedDevice>) {
    connectedDevices.value = devices
  }
}

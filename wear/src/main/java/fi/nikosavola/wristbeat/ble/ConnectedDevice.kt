package fi.nikosavola.wristbeat.ble

/**
 * One receiver that has subscribed to the heart rate measurement.
 *
 * [name] is whatever the central chooses to share and is often absent: a peripheral learns a
 * central's name only from the connection or a bond, never from an advertisement, so [address] is
 * the identifier that is always there.
 *
 * [connectedAtElapsedMillis] is on the monotonic clock rather than wall time, so the age of a
 * connection does not jump if the user changes the watch's clock mid-session.
 */
data class ConnectedDevice(
  val name: String?,
  val address: String,
  val connectedAtElapsedMillis: Long,
)

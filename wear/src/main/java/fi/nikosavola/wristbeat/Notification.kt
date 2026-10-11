package fi.nikosavola.wristbeat

import android.content.Context

/**
 * How often the ongoing notification may be re-posted.
 *
 * A heart rate sensor can report several times a second, and every re-post is a binder call into the
 * notification service plus a pass over the watch's notification stream and the watch face chip, for
 * a number nobody reads at that rate. Readings still reach receivers at whatever rate the sensor
 * gives them; this paces only what is drawn.
 */
internal const val NOTIFICATION_INTERVAL_MILLIS = 5_000L

/**
 * What the ongoing notification says under its title: the reading first, because that is what shows
 * the session is doing something rather than merely still running, then who is reading it.
 *
 * A top-level function rather than a method on the service so the wording can be tested on the JVM,
 * where a service cannot get past the Bluetooth stack to reach a running session.
 */
internal fun notificationContent(context: Context, bpm: Int?, receivers: Int): String {
  val reading =
    bpm?.let { context.getString(R.string.notification_reading, it.toString()) }
      ?: context.getString(R.string.notification_waiting)
  if (receivers == 0) return reading
  val receiversText =
    context.resources.getQuantityString(R.plurals.notification_receivers, receivers, receivers)
  return "$reading, $receiversText"
}


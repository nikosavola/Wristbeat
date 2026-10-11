package fi.nikosavola.wristbeat

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import fi.nikosavola.wristbeat.ble.HeartRatePeripheral
import fi.nikosavola.wristbeat.ble.HeartRateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

private const val NOTIFICATION_CHANNEL_ID = "broadcast"
private const val NOTIFICATION_ID = 1
private const val ACTION_START = "fi.nikosavola.wristbeat.action.START"
private const val ACTION_STOP = "fi.nikosavola.wristbeat.action.STOP"

/**
 * Keeps the watch advertising as a heart rate sensor with the screen off, which is the only state
 * this app is useful in. A foreground service with the connectedDevice type is what the platform
 * requires for that: the advertiser and GATT server would otherwise be frozen along with the app.
 */
class HeartRateBroadcastService : Service() {

  private var peripheral: HeartRatePeripheral? = null
  private var source: HeartRateSource? = null

  /** Owns the notification updates. Cancelled with the service, because a coroutine still holding
   * this service past onDestroy is a leak. */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var notificationUpdates: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    startBroadcasting()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    stopBroadcasting()
    scope.cancel()
    super.onDestroy()
  }

  private fun startBroadcasting() {
    if (peripheral != null) return

    // Nothing from the previous attempt describes this one, so it is cleared before the gates below
    // can set a new reason.
    BroadcastStatus.attempting()

    // The activity and the app function both gate on these, so reaching this means a caller skipped
    // that. Bailed out before startForeground because the connectedDevice type would refuse to start
    // without them anyway. That makes the gate here the last line of defence and not the first: a
    // service that returns from onStartCommand without ever going foreground is killed by the
    // platform, and the process with it, so callers have to refuse before they call [start].
    if (BroadcastPermissions.missing(this, BroadcastPermissions.bluetooth).isNotEmpty()) {
      BroadcastStatus.rejected(BroadcastRejection.MissingPermission)
      stopSelf()
      return
    }

    startForegroundCompat()

    val sensor =
      HeartRateSource(this) { bpm ->
        BroadcastStatus.heartRateChanged(bpm)
        peripheral?.send(bpm)
      }
    if (!sensor.isAvailable) {
      BroadcastStatus.rejected(BroadcastRejection.NoHeartRateSensor)
      stopSelf()
      return
    }

    val ble =
      HeartRatePeripheral(
        context = this,
        onDevicesChanged = BroadcastStatus::connectedDevicesChanged,
        // A peripheral that breaks mid-session leaves the notification claiming something that is
        // no longer true, so take the whole service down and say why. Ignored once the peripheral
        // has been torn down, so a failure landing after the user pressed Stop is not reported
        // against a session that is already over.
        onFailed = { reason ->
          if (peripheral != null) {
            BroadcastStatus.rejected(reason)
            stopSelf()
          }
        },
      )
    val rejection = ble.start()
    if (rejection != null) {
      BroadcastStatus.rejected(rejection)
      stopSelf()
      return
    }

    peripheral = ble
    source = sensor
    // A sensor can be present and still refuse the listener, and nothing would ever arrive from
    // here. Reported rather than swallowed, so the session is not left claiming to be reading.
    if (!sensor.start()) {
      BroadcastStatus.rejected(BroadcastRejection.NoHeartRateSensor)
      stopSelf()
      return
    }
    BroadcastStatus.started()
    startNotificationUpdates()
  }

  private fun stopBroadcasting() {
    notificationUpdates?.cancel()
    notificationUpdates = null
    source?.stop()
    source = null
    peripheral?.stop()
    peripheral = null
    // Also covers the failure paths above, where nothing was ever started but the notification is
    // already up.
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    BroadcastStatus.stopped()
  }

  /**
   * Keeps the ongoing notification showing what the session is actually doing. A notification that
   * only says "heart rate broadcasting" is indistinguishable from one left behind by a session that
   * has quietly stopped reading, which is exactly what this app must never look like.
   *
   * Throttled by resting after each post rather than by turning arrivals away: a reading is a
   * number that mostly stays the same, so one that arrives inside an interval and is dropped is not
   * going to arrive again, and the notification would sit on the reading before it for as long as
   * the session runs. Anything arriving during the rest is kept, so the next post is the newest
   * reading rather than a queue of the ones skipped.
   */
  private fun startNotificationUpdates() {
    notificationUpdates =
      scope.launch {
        // What startForeground has just posted, so the emission that replays it is not a change.
        var posted = NotificationState(bpm = null, receivers = 0)
        combine(
          BroadcastStatus.heartRateBpm,
          BroadcastStatus.connectedDevices,
        ) { bpm, devices ->
          NotificationState(bpm, devices.size)
        }
          // Readings arrive several times a second and faster than they are posted. Conflating keeps
          // the latest of them rather than a queue of stale ones.
          .conflate()
          .collect { state ->
            // The teardown paths publish the cleared state before this is cancelled, and posting
            // that would put the notification back after stopForeground removed it.
            if (!BroadcastStatus.broadcasting.value) return@collect
            if (state == posted) return@collect
            posted = state
            notify(state)
            delay(NOTIFICATION_INTERVAL_MILLIS)
          }
      }
  }

  private data class NotificationState(val bpm: Int?, val receivers: Int)

  private fun startForegroundCompat() {
    createNotificationChannel()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      buildNotification(NotificationState(bpm = null, receivers = 0)),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
    )
  }

  /**
   * Re-posts the ongoing notification in place. A foreground service notification is exempt from
   * POST_NOTIFICATIONS, so updating it needs no permission check, unlike posting a new one.
   */
  @SuppressLint("MissingPermission")
  private fun notify(state: NotificationState) {
    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
  }

  /** What the notification says under its title. The reading first, because it is the one thing
   * that shows the session is doing something rather than merely running. */
  private fun contentText(state: NotificationState): String =
    notificationContent(this, state.bpm, state.receivers)

  private fun buildNotification(state: NotificationState): Notification {
    val open =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val stop = PendingIntent.getService(this, 0, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
    val builder =
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_heart)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(contentText(state))
        .setContentIntent(open)
        .addAction(R.drawable.ic_pause, getString(R.string.action_stop), stop)
        .setOngoing(true)
        .setSilent(true)
    // A broadcasting session is exactly the long-running operation the Wear app quality guidelines
    // want surfaced on the watch face, and this is what puts the chip there. apply() only writes
    // extras into the builder; startForeground is still what posts it.
    OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
      .setStaticIcon(R.drawable.ic_heart)
      .setTouchIntent(open)
      .setTitle(getString(R.string.notification_title))
      .setContentDescription(getString(R.string.notification_content_description))
      .build()
      .apply(this)
    return builder.build()
  }

  private fun createNotificationChannel() {
    val channel =
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      )
    getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
  }

  companion object {
    fun start(context: Context) {
      ContextCompat.startForegroundService(context, intent(context, ACTION_START))
    }

    /**
     * The button and the notification's action both land here, so there is one stop path rather
     * than two that have to be kept in step. Routing through the service instead of stopService
     * lets the service own its own teardown.
     */
    fun stop(context: Context) {
      context.startService(intent(context, ACTION_STOP))
    }

    private fun intent(context: Context, action: String) =
      Intent(context, HeartRateBroadcastService::class.java).setAction(action)
  }
}


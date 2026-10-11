package fi.nikosavola.wristbeat

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.nikosavola.wristbeat.ble.BroadcastPermissions
import fi.nikosavola.wristbeat.ble.BroadcastRejection
import fi.nikosavola.wristbeat.ble.BroadcastStatus
import fi.nikosavola.wristbeat.ble.ConnectedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowService
import java.time.Duration

/**
 * The service, from both ends.
 *
 * First its decision chain, in the order it makes it: permissions, then the sensor, then the
 * Bluetooth stack. Each step is exercised by withholding exactly one thing, which is also the only
 * way to reach the later steps, since the earlier ones refuse first.
 *
 * Then a session that actually runs. Robolectric's advertiser and GATT server are enough to carry
 * one, so a reading can be pushed in from a sensor event and read back out of the notification:
 * everything except the radio itself.
 *
 * What is not here is the Bluetooth stack's own behaviour - a receiver connecting, a descriptor
 * write - which the peripheral's tests cover one level down.
 */
@RunWith(AndroidJUnit4::class)
class HeartRateBroadcastServiceTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val application: Application = ApplicationProvider.getApplicationContext()
  private val sensorManager: SensorManager = context.getSystemService(SensorManager::class.java)

  /** The watch's sensor, once a test has given it one. */
  private var sensor: Sensor? = null

  private val laptop = ConnectedDevice("Laptop", "AA:AA:AA:AA:AA:AA", 0L)

  @Before
  fun startFromNothing() {
    // started() first because it is what clears a refusal left behind by the previous test.
    BroadcastStatus.started()
    BroadcastStatus.stopped()
    shadowOf(application).grantPermissions(*BroadcastPermissions.required.toTypedArray())
    setBluetoothEnabled(false)
  }

  @Test
  fun `without the permissions it refuses before it goes to the foreground`() {
    // The order matters: the connectedDevice foreground type would be refused without the Bluetooth
    // permission anyway, and a notification for a session that cannot start is worse than none.
    shadowOf(application).denyPermissions(*BroadcastPermissions.required.toTypedArray())
    val controller = startSession()
    assertEquals(BroadcastRejection.MissingPermission, BroadcastStatus.rejection.value)
    assertNull(controller.shadow.lastForegroundNotification)
    controller.destroy()
  }

  @Test
  fun `with no heart rate sensor to read it says so rather than broadcasting`() {
    val controller = startSession()
    assertEquals(BroadcastRejection.NoHeartRateSensor, BroadcastStatus.rejection.value)
    assertFalse(BroadcastStatus.broadcasting.value)
    assertTrue(controller.shadow.isStoppedBySelf)
    controller.destroy()
  }

  @Test
  fun `with Bluetooth off it says so rather than broadcasting`() {
    addHeartRateSensor()
    setBluetoothEnabled(false)
    val controller = startSession()
    assertEquals(BroadcastRejection.BluetoothOff, BroadcastStatus.rejection.value)
    assertFalse(BroadcastStatus.broadcasting.value)
    controller.destroy()
  }

  @Test
  fun `a refusal takes the session down rather than leaving the button on Stop`() {
    addHeartRateSensor()
    val controller = startSession()
    assertNotNull(BroadcastStatus.rejection.value)
    assertTrue(controller.shadow.isStoppedBySelf)
    assertFalse(BroadcastStatus.broadcasting.value)
    controller.destroy()
    assertTrue(controller.shadow.isForegroundStopped)
  }

  @Test
  fun `the notification is an ongoing activity, which is what puts the session on the watch face`() {
    addHeartRateSensor()
    val controller = startSession()
    val extras = requireNotNull(controller.notification().extras)
    assertTrue(
      "no ongoing activity in ${extras.keySet()}",
      extras.containsKey(ONGOING_ACTIVITY_EXTENSIONS),
    )
    controller.destroy()
  }

  @Test
  fun `the notification cannot be swiped away while the session runs`() {
    addHeartRateSensor()
    val controller = startSession()
    val flags = controller.notification().flags
    assertTrue("not ongoing", flags and Notification.FLAG_ONGOING_EVENT != 0)
    assertTrue("not a foreground service notification", flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
    controller.destroy()
  }

  @Test
  fun `the notification offers to stop the session, and that action does stop it`() {
    addHeartRateSensor()
    val controller = startSession()
    val stop = controller.notification().actions.single()
    assertEquals("Stop", stop.title)
    assertTrue("the action does not go to a service", shadowOf(stop.actionIntent).isService)
    assertEquals(
      HeartRateBroadcastService::class.java.name,
      shadowOf(stop.actionIntent).savedIntent?.component?.className,
    )

    // Driven through a second service instance, because this is the platform's route into the app
    // rather than the button's, and the first instance has already stopped itself by now.
    val fromNotification = runIntent(shadowOf(stop.actionIntent).savedIntent)
    assertTrue(fromNotification.shadow.isStoppedBySelf)
    controller.destroy()
  }

  // --- a session that runs ------------------------------------------------------------------------

  @Test
  fun `a session with everything it needs runs`() {
    val controller = runningSession()
    assertTrue(BroadcastStatus.broadcasting.value)
    assertNull(BroadcastStatus.rejection.value)
    assertNotNull(controller.notification())
    controller.destroy()
  }

  @Test
  fun `a session that opens says it has no reading rather than going quiet`() {
    val controller = runningSession()
    assertEquals("No reading yet", notificationText())
    controller.destroy()
  }

  @Test
  fun `a reading from the sensor is published`() {
    val controller = runningSession()
    readHeartRate(79)
    assertEquals(79, BroadcastStatus.heartRateBpm.value)
    controller.destroy()
  }

  @Test
  fun `a reading reaches the notification behind it`() {
    val controller = runningSession()
    readHeartRate(79)
    readHeartRate(80)
    assertEquals("80 bpm", notificationText())
    controller.destroy()
  }

  @Test
  fun `a reading that arrives just after the session opens is still shown`() {
    // The reading a session opens with lands inside the interval of its opening notification, and
    // the sensor reports the same number for as long as the pulse holds: a post that turned this one
    // away would leave the notification on "No reading yet" for the whole session. Seen on the
    // emulator before it was fixed, where the session broadcast a steady 81 that never appeared.
    val controller = runningSession()
    readHeartRate(79)
    settle()
    assertEquals("79 bpm", notificationText())
    controller.destroy()
  }

  @Test
  fun `a receiver that connects just after the session opens is still shown`() {
    // The likelier shape of the same thing: a receiver appears a moment after the session opens and
    // then stays, so a count that was turned away once would never be posted at all.
    val controller = runningSession()
    BroadcastStatus.connectedDevicesChanged(listOf(laptop))
    settle()
    assertEquals("No reading yet, 1 receiver", notificationText())
    controller.destroy()
  }

  @Test
  fun `a burst of readings does not turn into a post each`() {
    // Pacing, in the place it matters: the notification is re-posted once per interval however fast
    // the sensor reports, and the latest reading is the one left on it.
    val controller = runningSession()
    for (bpm in 70..80) readHeartRate(bpm, accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    assertEquals("80 bpm", notificationText())
    controller.destroy()
  }

  @Test
  fun `a reading the sensor would not stand behind is not published`() {
    val controller = runningSession()
    readHeartRate(79)
    readHeartRate(120, accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT)
    assertEquals(79, BroadcastStatus.heartRateBpm.value)
    controller.destroy()
  }

  @Test
  fun `a sensor that refuses the listener is reported rather than broadcast`() {
    // registerListener can answer no, and nothing else would ever arrive after it did. The session
    // has to come down rather than sit there waiting for a reading that cannot come.
    addHeartRateSensor()
    setBluetoothEnabled(true)
    shadowOf(sensorManager).setForceListenersToFail(true)
    val controller = startSession()
    assertEquals(BroadcastRejection.NoHeartRateSensor, BroadcastStatus.rejection.value)
    assertFalse(BroadcastStatus.broadcasting.value)
    controller.destroy()
  }

  @Test
  fun `stopping a running session clears the reading and says it is not broadcasting`() {
    val controller = runningSession()
    readHeartRate(79)
    val stop = Intent(context, HeartRateBroadcastService::class.java).setAction(STOP_ACTION)
    controller.withIntent(stop).startCommand(0, 0)
    assertTrue("the Stop action did not stop the session", controller.shadow.isStoppedBySelf)
    // The teardown itself is in onDestroy, which is what the platform calls after stopSelf. That is
    // the service owning its own teardown rather than splitting it across two paths.
    controller.destroy()
    assertFalse(BroadcastStatus.broadcasting.value)
    assertNull(BroadcastStatus.heartRateBpm.value)
    assertTrue("the session was still in the foreground", controller.shadow.isForegroundStopped)
  }

  private fun startSession(): ServiceController<HeartRateBroadcastService> {
    HeartRateBroadcastService.start(context)
    val intent =
      requireNotNull(shadowOf(application).nextStartedService) { "the button starts the service" }
    assertEquals(HeartRateBroadcastService::class.java.name, intent.component?.className)
    return runIntent(intent)
  }

  private fun runIntent(intent: android.content.Intent?): ServiceController<HeartRateBroadcastService> {
    val controller =
      Robolectric.buildService(HeartRateBroadcastService::class.java, requireNotNull(intent)).create()
    controller.startCommand(0, 0)
    return controller
  }

  private fun ServiceController<HeartRateBroadcastService>.notification(): Notification =
    requireNotNull(shadow.lastForegroundNotification) { "no foreground notification was posted" }

  private val ServiceController<HeartRateBroadcastService>.shadow: ShadowService
    get() = shadowOf(get())

  /** Robolectric's only way to make a Sensor: the platform's own constructor is hidden. */
  @Suppress("DEPRECATION")
  private fun addHeartRateSensor(): Sensor {
    val sensor = ShadowSensor.newInstance(Sensor.TYPE_HEART_RATE)
    shadowOf(sensorManager).addSensor(sensor)
    return sensor
  }

  /** A session that has started and is reading, which is what the tests below are about. */
  private fun runningSession(): ServiceController<HeartRateBroadcastService> {
    sensor = addHeartRateSensor()
    setBluetoothEnabled(true)
    val controller = startSession()
    assertTrue("the session did not start", BroadcastStatus.broadcasting.value)
    return controller
  }

  /** One sensor event, the way the platform delivers it. */
  private fun readHeartRate(bpm: Int, accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
    val event =
      SensorEventBuilder.newBuilder(requireNotNull(sensor), floatArrayOf(bpm.toFloat()))
        .setAccuracy(accuracy)
        .build()
    shadowOf(sensorManager).sendSensorEventToListeners(event, sensor)
    settle()
  }

  /** Lets whatever the service posted in the background run, including the pacing clock. */
  private fun settle() {
    shadowOf(Looper.getMainLooper()).idleFor(PAST_THE_PACING_INTERVAL)
  }

  /** What the ongoing notification currently says. */
  private fun notificationText(): String? =
    shadowOf(context.getSystemService(NotificationManager::class.java))
      .getNotification(NOTIFICATION_ID)
      ?.extras
      ?.getString(Notification.EXTRA_TEXT)

  private fun setBluetoothEnabled(enabled: Boolean) {
    shadowOf(adapter()).setEnabled(enabled)
  }

  private fun adapter(): BluetoothAdapter =
    requireNotNull(context.getSystemService(BluetoothManager::class.java)?.adapter)

  private companion object {
    /** The key the wear-ongoing library writes its extras under, which is what the watch face reads. */
    const val ONGOING_ACTIVITY_EXTENSIONS = "android.wearable.ongoingactivities.EXTENSIONS"

    /** The id the service posts its notification under. */
    const val NOTIFICATION_ID = 1

    /** The intent the button and the notification's Stop action both send. */
    const val STOP_ACTION = "fi.nikosavola.wristbeat.action.STOP"

    /** Past the interval a re-post is held back for, so a reading's post is due. */
    val PAST_THE_PACING_INTERVAL: Duration = Duration.ofSeconds(6)
  }
}

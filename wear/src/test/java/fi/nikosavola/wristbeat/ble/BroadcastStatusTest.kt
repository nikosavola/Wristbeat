package fi.nikosavola.wristbeat.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BroadcastStatusTest {

  private val laptop = ConnectedDevice("Laptop", "AA:AA", 1_000L)
  private val bike = ConnectedDevice("Bike computer", "BB:BB", 2_000L)

  /** A session that just started: the state most tests below are a change from. */
  @Before
  fun setUp() {
    BroadcastStatus.started()
  }

  @Test
  fun `a session starts with nothing read and nobody connected`() {
    assertTrue(BroadcastStatus.broadcasting.value)
    assertNull(BroadcastStatus.heartRateBpm.value)
    assertTrue(BroadcastStatus.connectedDevices.value.isEmpty())
    assertNull(BroadcastStatus.rejection.value)
  }

  @Test
  fun `starting again clears the readings and receivers of the session before it`() {
    BroadcastStatus.heartRateChanged(70)
    BroadcastStatus.connectedDevicesChanged(listOf(laptop))
    BroadcastStatus.started()
    assertNull(BroadcastStatus.heartRateBpm.value)
    assertTrue(BroadcastStatus.connectedDevices.value.isEmpty())
  }

  @Test
  fun `starting again clears a refusal from the attempt before it`() {
    BroadcastStatus.rejected(BroadcastRejection.BluetoothOff)
    BroadcastStatus.started()
    assertNull(BroadcastStatus.rejection.value)
  }

  @Test
  fun `stopping ends the session and drops the reading and the receivers`() {
    BroadcastStatus.heartRateChanged(70)
    BroadcastStatus.connectedDevicesChanged(listOf(laptop))
    BroadcastStatus.stopped()
    assertFalse(BroadcastStatus.broadcasting.value)
    assertNull(BroadcastStatus.heartRateBpm.value)
    assertTrue(BroadcastStatus.connectedDevices.value.isEmpty())
  }

  @Test
  fun `a refusal survives the teardown that follows it`() {
    // The service reports the refusal and then stops itself through the same path an ordinary Stop
    // takes. If that teardown cleared the refusal, the user would be left looking at a blank screen
    // instead of the reason nothing happened.
    BroadcastStatus.rejected(BroadcastRejection.NoHeartRateSensor)
    BroadcastStatus.stopped()
    assertEquals(BroadcastRejection.NoHeartRateSensor, BroadcastStatus.rejection.value)
    assertFalse(BroadcastStatus.broadcasting.value)
  }

  @Test
  fun `a refusal stops the session it was reported for`() {
    BroadcastStatus.rejected(BroadcastRejection.MissingPermission)
    assertFalse(BroadcastStatus.broadcasting.value)
    assertNull(BroadcastStatus.heartRateBpm.value)
    assertTrue(BroadcastStatus.connectedDevices.value.isEmpty())
  }

  @Test
  fun `a reading changes nothing but the reading`() {
    BroadcastStatus.connectedDevicesChanged(listOf(laptop))
    BroadcastStatus.heartRateChanged(64)
    assertEquals(64, BroadcastStatus.heartRateBpm.value)
    assertTrue(BroadcastStatus.broadcasting.value)
    assertEquals(listOf("AA:AA"), BroadcastStatus.connectedDevices.value.map { it.address })
  }

  @Test
  fun `the receiver list is replaced rather than added to`() {
    // A disconnect reports the whole set that is left, so an entry that is no longer in it has to go.
    BroadcastStatus.connectedDevicesChanged(listOf(laptop, bike))
    BroadcastStatus.connectedDevicesChanged(listOf(bike))
    assertEquals(listOf("BB:BB"), BroadcastStatus.connectedDevices.value.map { it.address })
  }

  @Test
  fun `receivers arrive in the order the peripheral reports them`() {
    BroadcastStatus.connectedDevicesChanged(listOf(bike, laptop))
    assertEquals(listOf("BB:BB", "AA:AA"), BroadcastStatus.connectedDevices.value.map { it.address })
  }
}

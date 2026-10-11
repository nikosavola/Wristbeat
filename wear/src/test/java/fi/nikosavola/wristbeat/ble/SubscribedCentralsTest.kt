package fi.nikosavola.wristbeat.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscribedCentralsTest {

  private val names = mutableMapOf<String, String?>("AA:AA" to "Laptop")
  private var nowMillis = 1_000L
  private var descriptions = 0

  private val centrals =
    SubscribedCentrals<String> { address ->
      descriptions++
      ConnectedDevice(
        name = names[address],
        address = address,
        connectedAtElapsedMillis = nowMillis,
      )
    }

  @Test
  fun `a subscribe is news the first time and not the second`() {
    assertTrue(centrals.add("AA:AA"))
    assertFalse(centrals.add("AA:AA"))
    assertEquals(1, centrals.devices().size)
  }

  @Test
  fun `receivers come back in the order they subscribed`() {
    centrals.add("AA:AA")
    centrals.add("BB:BB")
    centrals.add("CC:CC")
    assertEquals(listOf("AA:AA", "BB:BB", "CC:CC"), centrals.devices().map { it.address })
  }

  @Test
  fun `an unsubscribe is news only when the receiver was there`() {
    centrals.add("AA:AA")
    assertTrue(centrals.remove("AA:AA"))
    assertFalse(centrals.remove("AA:AA"))
  }

  @Test
  fun `a receiver that unsubscribes and comes back keeps its place at the end`() {
    centrals.add("AA:AA")
    centrals.add("BB:BB")
    centrals.remove("AA:AA")
    centrals.add("AA:AA")
    assertEquals(listOf("BB:BB", "AA:AA"), centrals.devices().map { it.address })
  }

  @Test
  fun `each receiver is stamped with the time it subscribed`() {
    nowMillis = 5_000L
    centrals.add("AA:AA")
    nowMillis = 9_000L
    centrals.add("BB:BB")
    assertEquals(listOf(5_000L, 9_000L), centrals.devices().map { it.connectedAtElapsedMillis })
  }

  @Test
  fun `a receiver the platform has no name for keeps its address`() {
    centrals.add("BB:BB")
    val device = centrals.devices().single()
    assertNull(device.name)
    assertEquals("BB:BB", device.address)
  }

  @Test
  fun `a repeated subscribe reads the receiver's details only the once`() {
    // Reading them means asking the platform for a name it usually does not have, so a receiver that
    // rewrites the descriptor should not make it ask again.
    centrals.add("AA:AA")
    assertEquals(1, descriptions)
    centrals.add("AA:AA")
    assertEquals(1, descriptions)
  }

  @Test
  fun `contains follows subscribe and unsubscribe`() {
    assertFalse(centrals.contains("AA:AA"))
    centrals.add("AA:AA")
    assertTrue(centrals.contains("AA:AA"))
    centrals.remove("AA:AA")
    assertFalse(centrals.contains("AA:AA"))
  }

  @Test
  fun `clearing reports whether there was anything there, and empties the list`() {
    assertFalse(centrals.clear())
    centrals.add("AA:AA")
    assertTrue(centrals.clear())
    assertTrue(centrals.devices().isEmpty())
    assertTrue(centrals.receivers().isEmpty())
    assertFalse(centrals.clear())
  }

  @Test
  fun `where a notification goes is the list the page shows, in the same order`() {
    centrals.add("AA:AA")
    centrals.add("BB:BB")
    assertEquals(centrals.receivers(), centrals.devices().map { it.address })
  }
}

package fi.nikosavola.wristbeat.ble

/**
 * The receivers currently subscribed to notifications, in the order they subscribed, each against
 * the details the devices page shows for it.
 *
 * Kept apart from the GATT server so the subscribe bookkeeping - which has to survive repeated
 * configuration writes, unsubscribes and disconnects - is testable without a Bluetooth stack. The
 * key is whatever identifies a receiver: a BluetoothDevice compares by address, so one receiver
 * lands here once however many times it writes the descriptor.
 *
 * Not thread-safe. Callers hold the lock.
 */
internal class SubscribedCentrals<T : Any>(private val describe: (T) -> ConnectedDevice) {
  /** Insertion-ordered, because both the count and the list on the devices page read oldest first.
   */
  private val byReceiver = LinkedHashMap<T, ConnectedDevice>()

  /** Records a subscribe. Returns true when this is a receiver the list did not already have, so a
   * receiver that writes the descriptor again is not reported as news. The details are only read for
   * a receiver that is genuinely new. */
  fun add(receiver: T): Boolean {
    if (byReceiver.containsKey(receiver)) return false
    byReceiver[receiver] = describe(receiver)
    return true
  }

  /** Records an unsubscribe or a disconnect. Returns true when there was one there to remove. */
  fun remove(receiver: T): Boolean = byReceiver.remove(receiver) != null

  fun contains(receiver: T): Boolean = byReceiver.containsKey(receiver)

  /** Subscribed receivers, oldest first. */
  fun devices(): List<ConnectedDevice> = byReceiver.values.toList()

  /** Where to send a notification, oldest first. */
  fun receivers(): List<T> = byReceiver.keys.toList()

  /** Returns true when there was anything to clear. */
  fun clear(): Boolean =
    if (byReceiver.isEmpty()) {
      false
    } else {
      byReceiver.clear()
      true
    }
}

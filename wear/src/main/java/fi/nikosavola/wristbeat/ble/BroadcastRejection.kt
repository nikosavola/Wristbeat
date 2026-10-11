package fi.nikosavola.wristbeat.ble

/** Why broadcasting could not be started, or why it stopped. The UI owns the wording, so this
 * stays a plain enum. */
enum class BroadcastRejection {
  BluetoothOff,

  /** The watch cannot be a BLE peripheral at all: no advertising support, or the heart rate service
   * would not register on the GATT server. */
  PeripheralUnavailable,

  /** Advertising started but then died, so receivers can no longer find the watch. */
  AdvertiseFailed,

  MissingPermission,
  NoHeartRateSensor,
}

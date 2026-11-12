# Wristbeat privacy policy

Effective 31 October 2026.

Wristbeat is a Wear OS app that makes a watch act as a Bluetooth heart rate sensor. This policy says
what the app does with information, which is very little.

## What the app does with your information

The app reads the heart rate from the watch's own optical sensor and broadcasts it over Bluetooth LE
as a standard Bluetooth heart rate sensor, so equipment paired with the watch can read it. That
broadcast is all the app does with the reading. It reaches only the Bluetooth devices connected to
the watch, which you control by pairing them.

While a session runs, the reading and the number of devices reading it appear on the watch's screen
and in its notification. Both live in memory until the session stops, and neither is written to
storage. There is no history. The one thing the app does store is whether you asked a session to keep
the watch awake, which stays on the watch and goes nowhere else.

## What the app does not do

- It does not connect to the internet. The app has no network access at all.
- It does not send anything to the developer or to anyone else. Nothing leaves the watch except the
  Bluetooth broadcast above, which reaches the devices you have connected to it.
- It does not collect analytics, crash reports, advertising identifiers, or any other telemetry.
- It has no accounts, no sign-in, and no servers of its own.
- It does not read from or write to Health Connect, or any other health record store. It reads the
  watch's sensor directly.
- It does not share information with third parties, because it has none to share.
- It is not directed at children, and collects nothing from anyone.

## Permissions, and why the app asks for them

- **Nearby devices** (Bluetooth advertise and connect). Advertises the heart rate service and serves
  readings to devices that connect. Both are required for the app to work.
- **Heart rate**. Reads the watch's optical heart rate sensor. Required. If refused, the app reports
  "No heart rate sensor" instead of guessing. Wear OS shows this permission on its Health Connect
  screen, but that is only where the system asks. The app still does not use Health Connect.
- **Notifications**. Keeps the ongoing notification, which shows on the watch while a session runs.
  Optional. Without it the app still broadcasts, and only the reminder is missing.

## Changes

If this policy changes, the date at the top changes with it. The current version stays at the same
public address.

## Contact

Questions, or a correction to this policy? Open an issue at
<https://github.com/nikosavola/Wristbeat/issues>.

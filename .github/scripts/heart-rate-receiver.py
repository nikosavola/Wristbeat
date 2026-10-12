#!/usr/bin/env -S uv run --with bleak
"""A heart rate receiver, as a gym machine or a bike computer would be.

It scans for the Bluetooth SIG Heart Rate Service, connects to the first watch advertising it, reads
the body sensor location, reads the measurement, subscribes to it, and prints what arrives. Nothing
in here knows anything about this app: that is the point, since what it shows is that the watch
speaks the standard profile rather than a shape of its own.

    .github/scripts/heart-rate-receiver.py                 # listen for 25 seconds
    .github/scripts/heart-rate-receiver.py --seconds 90

Needs uv, which fetches bleak on the first run. On macOS the terminal it runs from has to be allowed
Bluetooth access, or the scan comes back empty with no error at all.
"""

import argparse
import asyncio
import itertools
import sys
import time

from bleak import BleakClient, BleakScanner
from bleak.backends.characteristic import BleakGATTCharacteristic
from bleak.backends.device import BLEDevice

HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
BODY_SENSOR_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb"

# Heart Rate Service 3.1, the body sensor location values.
SENSOR_LOCATIONS = {
    0: "other",
    1: "chest",
    2: "wrist",
    3: "finger",
    4: "hand",
    5: "ear lobe",
    6: "foot",
}

SCAN_SECONDS = 10.0


def decode_measurement(data: bytearray) -> str:
    """A Heart Rate Measurement as the specification packs it: a flags byte, then the value, whose
    width the flags decide."""
    flags = data[0]
    wide = flags & 0x01
    bpm = int.from_bytes(data[1:3], "little") if wide else data[1]
    return f"{bpm} bpm, {'16-bit' if wide else '8-bit'}, raw {data.hex()}"


async def find_watch(seconds: float) -> BLEDevice | None:
    print(f"scanning for the Heart Rate Service, {seconds:.0f}s...")
    found = await BleakScanner.discover(
        timeout=seconds, service_uuids=[HEART_RATE_SERVICE], return_adv=True
    )
    for address, (device, advertisement) in found.items():
        print(
            f"found {address} name={device.name!r} rssi={advertisement.rssi} "
            f"services={advertisement.service_uuids}"
        )
    if not found:
        return None
    _, (device, _) = next(iter(found.items()))
    return device


async def listen(device: BLEDevice, seconds: float) -> int:
    print("\nconnecting...")
    async with BleakClient(device, timeout=20.0) as client:
        print("connected:", client.is_connected)
        for service in client.services:
            print(f"  service {service.uuid}  {service.description}")
            for characteristic in service.characteristics:
                print(f"    char {characteristic.uuid}  {characteristic.properties}")

        location = await client.read_gatt_char(BODY_SENSOR_LOCATION)
        print("\nbody sensor location:", SENSOR_LOCATIONS.get(location[0], location.hex()))
        measurement = await client.read_gatt_char(MEASUREMENT)
        print("read of the measurement:", decode_measurement(bytearray(measurement)))

        arrivals: list[float] = []
        start = time.monotonic()

        def on_notify(_characteristic: BleakGATTCharacteristic, data: bytearray) -> None:
            elapsed = time.monotonic() - start
            arrivals.append(elapsed)
            print(f"  notify at {elapsed:6.2f}s: {decode_measurement(data)}")

        await client.start_notify(MEASUREMENT, on_notify)
        print(f"\nsubscribed; listening for {seconds:.0f}s")
        await asyncio.sleep(seconds)
        await client.stop_notify(MEASUREMENT)

        print(f"\n{len(arrivals)} notifications in {seconds:.0f}s")
        gaps = [round(b - a, 2) for a, b in itertools.pairwise(arrivals)]
        if gaps:
            print("gaps:", gaps[:20])
        print("disconnecting")
    return 0


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=float, default=25.0, help="how long to listen")
    arguments = parser.parse_args()

    if (watch := await find_watch(SCAN_SECONDS)) is None:
        print(
            "nothing is advertising the Heart Rate Service: is a session running?",
            file=sys.stderr,
        )
        return 1
    return await listen(watch, arguments.seconds)


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))

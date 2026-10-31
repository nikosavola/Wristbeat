#!/usr/bin/env python3
"""Checks the store listing against what Play, F-Droid and Galaxy Store will take.

One set of files is read by three stores, each refusing an upload for its own reason: a title one
character too long, a screenshot at the wrong size, a permission that contradicts the privacy
policy. None of that is visible until an upload day, so this runs every check that can be made from
the repository alone.

    python3 .github/scripts/check-stores.py

Exits non-zero and prints one line per problem, as GitHub Actions annotations when run there.
"""

import os
import re
import struct
import sys
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "fastlane/metadata/android/en-US"
GRADLE = ROOT / "wear/build.gradle.kts"
MANIFEST = ROOT / "wear/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Play's caps, in characters. It trims whitespace before counting, so the checks below do too.
TEXT_LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
}

# Play's changelog, which F-Droid shows as the same file.
CHANGELOG_LIMIT = 500

# The sizes that satisfy every store that cares, in pixels.
ICON = (512, 512)
FEATURE_GRAPHIC = (1024, 500)
# What Play asks of a Wear OS listing: at least one screenshot, no more than eight, all this size.
WEAR_SCREENSHOT = (384, 384)
WEAR_SCREENSHOT_COUNT = (1, 8)

# Play refuses a new app or an update that targets less than this from 31 August 2026.
MIN_TARGET_SDK = 36

problems: list[str] = []


@dataclass(frozen=True, slots=True, kw_only=True)
class Release:
    """The identifiers every store keys on, as the build file states them."""

    app_id: str
    version_name: str
    version_code: int
    target_sdk: int


def problem(message: str) -> None:
    problems.append(message)


def png_size(path: Path) -> tuple[int, int] | None:
    """The size out of the PNG header: no imaging library, and no decoding of the pixels."""
    header = path.read_bytes()[:24]
    if header[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", header[16:24])


def read_release() -> Release | None:
    text = GRADLE.read_text()
    app_id = re.search(r'applicationId = "([^"]+)"', text)
    name = re.search(r'versionName = "([^"]+)"', text)
    code = re.search(r"versionCode = ([\d_]+)", text)
    target = re.search(r"targetSdk = (\d+)", text)
    if not (app_id and name and code and target):
        problem(f"{GRADLE.relative_to(ROOT)} has no applicationId, versionName, versionCode and targetSdk")
        return None
    return Release(
        app_id=app_id.group(1),
        version_name=name.group(1),
        # Kotlin writes the code's digits apart for legibility; a store sees the number.
        version_code=int(code.group(1).replace("_", "")),
        target_sdk=int(target.group(1)),
    )


def check_text() -> None:
    for name, limit in TEXT_LIMITS.items():
        path = METADATA / name
        if not path.is_file():
            problem(f"{path.relative_to(ROOT)} is missing")
        elif not (text := path.read_text().strip()):
            problem(f"{path.relative_to(ROOT)} is empty")
        elif len(text) > limit:
            problem(f"{path.relative_to(ROOT)} is {len(text)} characters, over the {limit} a store takes")


def check_changelog(version_code: int) -> None:
    path = METADATA / "changelogs" / f"{version_code}.txt"
    if not path.is_file():
        problem(f"{path.relative_to(ROOT)} is missing: Play and F-Droid read the file named after the versionCode")
    elif not (text := path.read_text().strip()):
        problem(f"{path.relative_to(ROOT)} is empty")
    elif len(text) > CHANGELOG_LIMIT:
        problem(f"{path.relative_to(ROOT)} is {len(text)} characters, over the {CHANGELOG_LIMIT} Play shows")


def check_image(relative: Path, expected: tuple[int, int]) -> None:
    path = METADATA / relative
    if not path.is_file():
        problem(f"{path.relative_to(ROOT)} is missing")
        return
    match png_size(path):
        case None:
            problem(f"{path.relative_to(ROOT)} is not a PNG")
        case size if size != expected:
            problem(
                f"{path.relative_to(ROOT)} is {size[0]}x{size[1]}, a store takes {expected[0]}x{expected[1]}"
            )


def check_images() -> None:
    check_image(Path("images/icon.png"), ICON)
    check_image(Path("images/featureGraphic.png"), FEATURE_GRAPHIC)
    screenshots = sorted((METADATA / "images/wearScreenshots").glob("*.png"))
    least, most = WEAR_SCREENSHOT_COUNT
    if not least <= len(screenshots) <= most:
        problem(f"there are {len(screenshots)} Wear OS screenshots, Play takes between {least} and {most}")
    for screenshot in screenshots:
        check_image(screenshot.relative_to(METADATA), WEAR_SCREENSHOT)


def check_manifest() -> None:
    root = ElementTree.parse(MANIFEST).getroot()

    features = {feature.get(f"{ANDROID}name") for feature in root.iter("uses-feature")}
    if "android.hardware.type.watch" not in features:
        problem("the manifest does not declare android.hardware.type.watch, so Play files this as a phone app")

    metadata = {
        entry.get(f"{ANDROID}name"): entry.get(f"{ANDROID}value")
        for entry in root.iter("meta-data")
    }
    if metadata.get("com.google.android.wearable.standalone") != "true":
        problem(
            "the manifest does not declare com.google.android.wearable.standalone=true, so Play expects a phone app alongside this one"
        )

    # The privacy policy, the data safety answers and the F-Droid recipe's "no anti-features" all
    # say the app has no network access, so a permission here is a lie in three places at once.
    permissions = {permission.get(f"{ANDROID}name") for permission in root.iter("uses-permission")}
    if "android.permission.INTERNET" in permissions:
        problem("the manifest asks for INTERNET, which contradicts the privacy policy and the data safety answers")


def report() -> None:
    prefix = "::error::" if os.environ.get("GITHUB_ACTIONS") else "error: "
    for message in problems:
        print(f"{prefix}{message}")


def main() -> int:
    if Path.cwd() != ROOT:
        print(f"run this from the repository root: {ROOT}", file=sys.stderr)
        return 2

    release = read_release()
    if release is not None:
        check_text()
        check_changelog(release.version_code)
        check_images()
        check_manifest()
        if release.target_sdk < MIN_TARGET_SDK:
            problem(f"targetSdk is {release.target_sdk}, and Play takes nothing under {MIN_TARGET_SDK}")

    if problems or release is None:
        report()
        return 1

    print(
        f"{release.app_id} {release.version_name} ({release.version_code}), "
        f"targetSdk {release.target_sdk}: listing text, changelog, graphics and manifest check out"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

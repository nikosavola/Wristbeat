# Contributing

## Setup

One Gradle module, the watch app in `wear/`. The wrapper is committed and provisions its own JDK 21
toolchain, so there is no manual setup step:

```bash
./gradlew :wear:assembleDebug
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
```

## Testing

```bash
./gradlew :wear:testDebugUnitTest :wear:lintDebug
```

190 tests run on the JVM through Robolectric, with no emulator and no watch. The same suite, lint and
both store artifacts run in CI on every push, and CI publishes coverage to Codecov and static analysis
to SonarCloud once those are enabled.

The two AI agent functions need a device, since the platform holds the system permission that invokes
one:

```sh
adb shell cmd app_function list-app-functions --package fi.nikosavola.wristbeat
adb shell cmd app_function execute-app-function --package fi.nikosavola.wristbeat \
  --function 'fi.nikosavola.wristbeat.appfunctions.BroadcastAppFunctions#isBroadcasting' \
  --parameters '{}'
```

## Receiving over the air

`.github/scripts/heart-rate-receiver.py` is a plain BLE receiver with no knowledge of this app, so it
tests the standard profile from the outside:

```sh
uv run --with bleak .github/scripts/heart-rate-receiver.py
```

## Before opening a pull request

- `./gradlew :wear:testDebugUnitTest :wear:lintDebug :wear:assembleRelease` passes.
- Keep commits atomic: one logical change per commit, with an imperative-mood message ("Add x",
  not "Added x" or "Adds x").
- New behavior gets a test; a bug fix gets a regression test.

## AI usage policy

Using AI tools to accelerate your workflow, whether for prototyping, writing tests, or improving
documentation, is **encouraged**.

However, as a contributor, you remain **fully responsible** for the code and content you submit. Please
ensure the following:

1. **No "AI slop"**: don't submit unreviewed, low-quality, or redundant AI-generated content.
1. **Verify and test**: all AI-generated code must be reviewed, tested, and verified to work as
   intended.
1. **Maintainability**: the content must be clear, idiomatic, and maintainable by a human.
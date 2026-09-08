# Validation

This repository contains OpenStar Android 1.1.6 (version code 8). The source split replaces the combined project's Python directory with the pinned `engine/` submodule; the app's UI and astronomy bridge are retained.

## Checks for the repository split

- The standalone OpenStar engine passes all 14 Python tests.
- The split Android project passes `assembleDebug` and `lintDebug` with zero errors and 48 warnings.
- All 172 native ELF libraries in the rebuilt APK pass the 16 KB compatibility check, and `zipalign -c -P 16 4` succeeds.
- The Android catalogue matches the pinned engine catalogue byte-for-byte. The engine public API and Android bridge are unchanged.
- The rebuilt APK was installed over the existing app on a 16 KB Android emulator without clearing app data. It generated the public Hong Kong reference sky and identified Polaris, with no Python import error or app crash.
- Repository and APK copies of the added NumPy LAPACK-lite and Dragon4 notices match. Machine-specific paths, local build settings, caches and signing keys are excluded from version control.

## Prior app validation

The original 1.1.6 build passed `assembleDebug` and `lintDebug` (zero errors, 48 warnings). It was exercised on an Android API 37 x86-64 emulator with 16 KB pages. Map generation successfully imported Skyfield/NumPy and calculated the sky; touch navigation, the hour bar, location picker and time-zone changes were checked. AR starts camera-off, has only Camera and options controls, and displays pointing guidance as a temporary message.

All 172 native ELF libraries passed the 16 KB alignment check, including libraries inside Chaquopy archives; APK zip alignment also passed. The offline time-zone database passed 28 known-city cases and comparison of 40,000 source-derived boundary sample points in the original validation environment.

Physical-device outdoor AR alignment and camera behavior still require testing. A compass overlay cannot guarantee exact alignment. This distribution is a debug build, not a production-signed Play Store release.

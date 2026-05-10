# Manager Build Install - DMBT Mirror Delete APK

Date: 2026-05-09
Device: Realme RMX3081 (`1a79dec0`)
Package: `com.example.devicetracker`

## Goal

Build and install the latest APK containing Agent 1's DMBT mirror-delete fix.

## Verification Before Install

- Device connected: `1a79dec0`.
- Targeted test: `DeviceLogRepositorySyncRulesTest` PASS.
- Debug build: `:app:assembleDebug` PASS.

APK:
- Path: `android-mvp/app/build/outputs/apk/debug/app-debug.apk`
- SHA256: `7A21EEEC63041687C804F3587D072B3B993F3290A288E60C88678B705335A70D`

## Install Result

- `adb install -r`: Success.
- App launched via monkey.
- PID after launch: `27419`.
- `firstInstallTime=2026-05-07 20:55:45`.
- `lastUpdateTime=2026-05-09 21:44:39`.
- `versionName=0.1.0`.

## UAT Note

Proceed with small deletion UAT first: delete 1-2 non-critical rows on Google Sheet, run full sync, and verify app count decreases by the same amount before testing larger deletion batches.

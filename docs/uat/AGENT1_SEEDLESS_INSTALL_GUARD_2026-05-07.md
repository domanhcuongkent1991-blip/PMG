# Agent 1 Seedless Install Guard (2026-05-07)

## Purpose

Prevent installing the wrong debug APK during empty-app UAT.

The stale APK path below must not be trusted unless it passes the seedless APK guard:

```text
android-mvp/app/build/outputs/apk/debug/app-debug.apk
```

The preferred APK for this UAT is the latest safe build output:

```text
android-mvp/.codex-build/<build-id>/app/outputs/apk/debug/app-debug.apk
```

## Required Guard Before Install

Run this before any `adb install`:

```powershell
.\scripts\verify-apk-seedless.ps1 -LatestSafeBuild
```

Expected PASS shape:

```text
assets/seed_device_logs.json len=4 prefix=[]
assets/seed_hgt_checks.json len=4 prefix=[]
PASS: APK seed assets are empty and safe to install.
```

If the script fails, stop immediately. Do not install the APK.

## Manual APK Path Variant

If Agent 1 chooses a specific APK path, verify that exact file:

```powershell
.\scripts\verify-apk-seedless.ps1 -ApkPath "F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\<build-id>\app\outputs\apk\debug\app-debug.apk"
```

## Forbidden Install Pattern

Do not install directly from this path unless this exact file is verified first:

```powershell
adb install -r -t -g "F:\codex_android_gsheet_full_pack\android-mvp\app\build\outputs\apk\debug\app-debug.apk"
```

Reason: this path was proven to contain old bundled seed data on 2026-05-07.

## Safe UAT Flow

1. Run `.\scripts\build-android-safe.ps1`.
2. Run `.\scripts\verify-apk-seedless.ps1 -LatestSafeBuild`.
3. Copy the verified APK path and SHA256 from the script output into the UAT result.
4. Install only that verified APK.
5. Ask user to clear app data in Android Settings.
6. Launch app.
7. Pull DB before sync.
8. Run pre-sync SQL gate.
9. Continue sync UAT only if:
   - `device_logs_before_sync = 0`
   - `seed_beta_rows_before_sync = 0`

## Stop Conditions

Stop and report to Manager if any of these happen:

- APK guard fails.
- APK output contains `seed-beta-*` or `seed-hgt-*`.
- Installed APK hash does not match the verified APK hash.
- Before-sync DB has any `device_logs`.
- Before-sync DB has any `recordId like 'seed-beta-%'`.
- Agent cannot pull DB before sync.

## Evidence Required In Agent 1 Report

Agent 1 must include:

- Verified APK absolute path.
- Verified APK SHA256.
- Seed asset guard output.
- Installed APK hash if pulled from device.
- DB dump path before sync.
- SQL output before sync.
- PASS/FAIL decision before any sync.

# Manager Root Cause: Stale APK Reseeded Old Data (2026-05-07)

## Context

User reported that after manually clearing app data in Android Settings, old data still appeared when opening the app.

Agent 1 reran the pre-sync clean gate and correctly stopped because the app was not empty before any sync:

- `device_logs_before_sync = 1891`
- `seed_beta_rows_before_sync = 1869`
- `sourceSheetId NULL = 1835`
- `sourceSheetId 1607125070 = 56`

This means the test device was not in a valid "empty app, remote-first" baseline.

## Evidence Reviewed

### 1. Agent 1 report

- Report: `docs/uat/results/AGENT1_SEEDLESS_EMPTY_APP_GATE_2026-05-07.md`
- Evidence DB: `docs/uat/evidence/seedless-empty-app-gate-2026-05-07/device_tracker_before_sync.db`
- SQL output: `docs/uat/evidence/seedless-empty-app-gate-2026-05-07/sql_pre_sync_gate_output.txt`

Agent 1 installed:

```text
android-mvp/app/build/outputs/apk/debug/app-debug.apk
```

### 2. Source assets are currently empty

The project source files are already seedless:

```text
android-mvp/app/src/main/assets/seed_device_logs.json = []
android-mvp/app/src/main/assets/seed_hgt_checks.json = []
```

So the stale data is not coming from the current source asset files.

### 3. Installed APK still contains old seed data

APK pulled from the device:

```text
docs/uat/evidence/installed-apk-check-2026-05-07/installed.apk
```

It contains old bundled seed assets:

```text
assets/seed_device_logs.json len=759152 prefix=[
  {
    "recordId": "seed-beta-dmbt-2022-r5",

assets/seed_hgt_checks.json len=4714 prefix=[
  {
    "id": "seed-hgt-hgt-inh-ky-r2",
```

### 4. Installed APK hash matches the stale local APK

```text
android-mvp/app/build/outputs/apk/debug/app-debug.apk
SHA256 65EACE2C2A628EAE11036F8375D568B61FEA72C1C2DDA55B95E43D90977837FC

docs/uat/evidence/installed-apk-check-2026-05-07/installed.apk
SHA256 65EACE2C2A628EAE11036F8375D568B61FEA72C1C2DDA55B95E43D90977837FC
```

This proves the device was running the same stale APK that still bundles old seed data.

### 5. The real seedless APK exists in the safe build output

Latest safe build APK:

```text
android-mvp/.codex-build/1778164235343/app/outputs/apk/debug/app-debug.apk
SHA256 56EB389A3A68F2CC1081C87E42C2B442F91BB5BD2BE1A81DBD285A7A2E0BACC3
```

It contains empty seed assets:

```text
assets/seed_device_logs.json len=4 prefix=[]
assets/seed_hgt_checks.json len=4 prefix=[]
```

## Root Cause

The user likely cleared app data correctly, but Agent 1 installed the stale APK from:

```text
android-mvp/app/build/outputs/apk/debug/app-debug.apk
```

That APK still contains old bundled seed JSON. When the app opens after Clear data, the bootstrap logic reads the bundled seed assets and reseeds old `seed-beta-*` records into the local database.

Therefore, old data reappears even before any Google Sheet sync runs.

## Conclusion

This is an APK selection/install problem, not proof that manual Clear data failed.

The correct next step is to install only an APK whose bundled seed assets have been verified as:

```text
seed_device_logs.json = []
seed_hgt_checks.json = []
```

Then the user should clear app data again and Agent 1 should rerun the pre-sync DB gate before any sync.

## Required Guard For Next UAT

Before installing any APK, Agent 1 must verify APK contents directly:

```text
assets/seed_device_logs.json len=4 prefix=[]
assets/seed_hgt_checks.json len=4 prefix=[]
```

If the APK contains `seed-beta-*`, Agent 1 must stop immediately and report the APK path as invalid.

## Manager Verdict

- Current Agent 1 clean gate result is valid as a FAIL.
- The fail reason is now identified: stale APK reseeded old bundled data.
- Do not use `android-mvp/app/build/outputs/apk/debug/app-debug.apk` for this UAT unless it has been rebuilt and asset-verified.
- Prefer the safe build output under `android-mvp/.codex-build/<build-id>/app/outputs/apk/debug/app-debug.apk`.

# AGENT1 Startup Sync Gate Result (2026-05-07)

## Task
- Task ID: `A1-STARTUP-SYNC-GATE`
- Goal: prevent auto pull at app startup so baseline after Clear data remains empty before manual sync.

## Files Changed (code)
- `android-mvp/app/src/main/java/com/example/devicetracker/DeviceTrackerApp.kt`
  - replaced startup call from `schedulePeriodicSync()` to `logStartupPeriodicSyncSkipped()`.
- `android-mvp/app/src/main/java/com/example/devicetracker/work/SyncScheduler.kt`
  - added `logStartupPeriodicSyncSkipped()` with explicit info log.
  - no change to manual sync entrypoint (`scheduleImmediateSync`).

## Build + Install
1. Built debug APK:
   - Command: `./scripts/build-android-safe.ps1`
   - Result: PASS
2. Verified seedless APK:
   - Command: `./scripts/verify-apk-seedless.ps1 -LatestSafeBuild`
   - APK: `F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\1778167294145\app\outputs\apk\debug\app-debug.apk`
   - SHA256: `DB6D26B80420FEB2B48168609D03E184C447F3425729EC226ACDE8069882BA98`
   - Result: PASS
3. Installed APK on device `RMX3081` (`1a79dec0`): PASS
4. User step confirmed:
   - Clear data manually in Android Settings
   - Open app
   - Wait 2-3 minutes without manual sync

## Evidence Collected
- Folder:
  - `docs/uat/evidence/startup-sync-gate-2026-05-07/`
- Startup log evidence:
  - `logcat_sync_tags.txt`
  - Key lines:
    - `Skipping startup periodic sync scheduling; sync remains manual via Sync Status.`
    - `Bundled snapshot seeding disabled or skipped; app relies on remote sync.`
- DB before sync:
  - `device_tracker_before_sync.db`
  - `device_tracker_before_sync.db-wal`
  - `device_tracker_before_sync.db-shm`
- SQL gate output:
  - `sql_before_sync_gate_output.txt`

## SQL Gate Results (before any manual sync)
- `device_logs_before_sync = 0`
- `seed_beta_rows_before_sync = 0`

## Verdict
- **PASS** for startup sync gate baseline.
- Condition satisfied: app does not auto-repopulate local DMBT data in first 2-3 minutes after clear data/open.
- Per task rule: stop here, **do not run sync** until further approval.

## Notes
- No changes made to:
  - `DeviceLogRepositoryImpl.kt`
  - `SheetsRemoteDataSource.kt`
  - DB schema/entity
  - API contract
  - Google Sheet

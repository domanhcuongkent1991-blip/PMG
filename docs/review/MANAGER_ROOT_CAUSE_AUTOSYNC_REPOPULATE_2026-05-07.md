# Manager Root Cause: Auto Sync Repopulates Data After Clear (2026-05-07)

## Context

After Agent 1 installed the verified seedless APK and user cleared app data, old data still appeared in app.

Agent 1 result:

- `docs/uat/results/AGENT1_SEEDLESS_INSTALL_GUARD_UAT_RESULT_2026-05-07.md`
- APK guard: PASS
- Installed APK hash == verified APK hash: PASS
- Before-sync DB gate: FAIL
  - `device_logs_before_sync = 145`
  - `seed_beta_rows_before_sync = 0`
  - all 145 rows with `sourceSheetId = 1607125070`

## Key Evidence

### 1. Stale APK reseed was already fixed

Agent 1 verified and installed seedless APK:

- `seed_device_logs.json = []`
- `seed_hgt_checks.json = []`
- installed hash matched verified hash.

So this is no longer "stale seed APK" behavior.

### 2. Rows are freshly written in the current run window

From `device_tracker_before_sync.db`:

- `sourceSheetId=1607125070` for all rows
- `syncStatus=SYNCED`
- `updatedAt` values are in current run time range.

This pattern matches remote pull merge behavior, not legacy seed snapshot.

### 3. App schedules WorkManager periodic sync at startup

Code evidence:

- `android-mvp/app/src/main/java/com/example/devicetracker/DeviceTrackerApp.kt`
  - `onCreate()` calls `syncScheduler.schedulePeriodicSync()`
- `android-mvp/app/src/main/java/com/example/devicetracker/work/SyncScheduler.kt`
  - `schedulePeriodicSync()` enqueues `SheetsSyncWorker` every 6 hours
- `android-mvp/app/src/main/java/com/example/devicetracker/work/SheetsSyncWorker.kt`
  - full sync worker runs push + pull (`refreshFromRemote()`).

### 4. Runtime scheduler evidence confirms app background job runs

- `docs/uat/evidence/manager-runtime-proof-2026-05-07/jobscheduler_excerpt.txt`

Excerpt includes:

- `JOB #u0a453/0 ... com.example.devicetracker/androidx.work.impl.background.systemjob.SystemJobService`
- historical `START` and `STOP-P ... app called jobFinished` for this exact service.

## Root Cause

The app repopulates data after clear because background sync is active and pulls remote rows from Google Sheet into local DB.

This is not a failure of clear-data itself.

## Why User Feels "Cannot Delete"

From user perspective:

1. Clear local data.
2. Open app.
3. Data appears again quickly.

Actual behavior:

1. Local data is cleared.
2. Background sync runs and pulls rows from remote source.
3. UI shows pulled rows again.

## Decision Impact

If source rows still exist in Google Sheet, local clear alone cannot keep app empty.

To keep app empty for debug/UAT baseline, we need one of:

1. Temporarily disable startup auto background sync, or
2. Add an explicit "pause sync" gate during baseline tests, or
3. Clean remote sheet rows first.

## Manager Verdict

- Current Agent 1 run is a valid FAIL for "empty-before-sync baseline".
- Root cause is confirmed as auto sync repopulation, not stale seed APK.
- Next fix should focus on sync scheduling behavior and/or controlled baseline mode.

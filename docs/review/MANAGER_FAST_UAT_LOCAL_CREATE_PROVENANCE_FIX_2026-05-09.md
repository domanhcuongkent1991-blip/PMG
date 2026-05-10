# Manager Fast UAT Blocker - Local Create Provenance Fix (2026-05-09)

## Context

During the Director-approved option 2 fast-track UAT, Manager attempted to create a DMBT 2025 marker record on the connected device before syncing.

## Evidence

- Device connected: `1a79dec0`.
- App launched successfully.
- Baseline before 2-way UAT:
  - `total_device_logs = 1852`
  - `NULL sourceSheetId = 0`
  - DMBT 2025 gid `989601207 = 558`
  - DMBT 2026 gid `1607125070 = 145`
  - `duplicate_groups = 17`
  - `rows_in_duplicate_groups = 34`
  - `PENDING = 5`
- After creating the DMBT 2025 marker through the installed app, DB inspection showed:
  - recordId `DMBT-68713fdf-01f8-475b-b793-a206759c4f2e`
  - `maThietBi = CODEX2025B`
  - `ngayPhatHien = 09/05/2025`
  - `syncStatus = PENDING`
  - `sourceSheetId = NULL`

## Finding

[P1] Locally-created DMBT records do not get `sourceSheetId` at save time.

This blocks safe multi-year App -> Sheet sync because `SheetsRemoteDataSource.pushLogs()` requires an unambiguous target sheet when multiple DMBT sheets are configured. A local record with `sourceSheetId = NULL` and non-safe recordId can cause an unresolved target sheet failure before pushing.

## Code Patch Applied

Files changed:

- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/edit/EditViewModel.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/SheetConfigMappingRulesTest.kt`

Patch intent:

1. Add year-to-DMBT-sheet resolver in `SheetConfig`.
2. Make `EditViewModel` assign `sourceSheetId` for new records based on normalized `ngayPhatHien`.
3. Make `DeviceLogRepositoryImpl.syncPending()` backfill `sourceSheetId` for existing pending records before push.
4. Add unit tests for 2025/2026 year-to-sheet mapping.

## Verification Status

- Direct Gradle targeted test was attempted but timed out after 240s due Kotlin daemon / filesystem access issues.
- Safe build was started but Director stopped it because it was taking too long.
- Lingering Java build processes were stopped afterward.

## Current Device Risk

The currently installed APK does not include this patch. The device also has the unsynced marker record `CODEX2025B` pending with `sourceSheetId = NULL` in the installed app's DB.

Do not press `Đồng bộ đầy đủ` on the currently installed APK until either:

1. The patched APK is built and installed, then it can backfill/route the pending record; or
2. The test pending record is removed/cleared intentionally.

## Manager Decision

- Code patch direction: correct and minimal.
- Build verification: pending due long build/environment issue.
- Fast-track UAT 2025/2026: still not approved until patched APK is built, installed, and retested.

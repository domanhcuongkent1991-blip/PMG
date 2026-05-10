# Manager Root Cause: Sync Only 2026 Due To Incomplete DMBT Sheet Config (2026-05-07)

## Symptom

User reported:

- App is empty after clear data (baseline fixed).
- After manual sync, only year 2026 is synced.
- Years 2022-2025 are not synced.

## Findings

### 1) Startup auto-sync issue is already fixed

From latest Agent 1 report:

- `docs/uat/results/AGENT1_STARTUP_SYNC_GATE_RESULT_2026-05-07.md`
- pre-sync gate passed:
  - `device_logs_before_sync = 0`
  - `seed_beta_rows_before_sync = 0`

So this is no longer caused by startup repopulation.

### 2) Runtime DMBT config previously had only 2026 active

Config check on `android-mvp/local.properties` showed:

- `SHEETS_DMBT_LOG_SHEET_ID=1607125070`
- `SHEETS_DMBT_SHEET_IDS` was missing/empty
- `SHEETS_DMBT_READONLY_SHEET_IDS` was empty

Given current `SheetConfig.parseDmbtSheetBindings(...)` behavior:

- if `SHEETS_DMBT_SHEET_IDS` is empty, app falls back to `SHEETS_DMBT_LOG_SHEET_ID` only.
- that means pull/push target set includes only `1607125070` (year 2026).

This exactly matches user symptom.

### 3) Code path that causes fallback

- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
  - `parseDmbtSheetBindings(...)` falls back to legacy primary when `rawSheetIds` is empty.

## Root Cause

The app was running with incomplete DMBT multi-year sheet binding config:

- no explicit `SHEETS_DMBT_SHEET_IDS` for years 2022-2025.
- runtime therefore used only legacy primary sheet ID (2026).

## Immediate Fix Applied

Updated only DMBT sheet binding keys in `android-mvp/local.properties`:

- `SHEETS_DMBT_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070`
- `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID=1607125070`
- `SHEETS_DMBT_READONLY_SHEET_IDS=`

Added guard script:

- `scripts/verify-dmbt-sheet-config.ps1`

Guard behavior:

- fail if `SHEETS_DMBT_SHEET_IDS` is empty.
- fail if required yearly IDs (2022-2026) are missing.
- warn if legacy monthly sheet id `1383308512` is still included.

## Verification

1. Guard output now PASS:
   - DMBT sheet list contains all yearly IDs 2022-2026.
2. Rebuild done via safe pipeline:
   - `.\scripts\build-android-safe.ps1 -SkipTests`
   - `BUILD SUCCESSFUL`
3. Generated BuildConfig of latest build confirms:
   - `SHEETS_DMBT_SHEET_IDS = "849979183,1783863163,1224276666,989601207,1607125070"`

## Manager Verdict

- Root cause is confirmed as configuration, not sync-core logic regression.
- Next required step is controlled rerun UAT sync1/sync2 with this build.
- Do not approve final fix until 2022-2025 counts are proven in DB evidence.

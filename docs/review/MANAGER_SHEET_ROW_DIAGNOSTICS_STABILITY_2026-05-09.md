# Manager Report - Sheet Row Diagnostics and Stability Recheck (2026-05-09)

## Goal
Continue the next work after user confirmed sync is stable: identify exact Google Sheet rows that still cause count mismatch risk, run repeated real-device sync checks, and prepare a safe cleanup decision for the Director.

## Scope
Changed only DMBT pull diagnostics. No mirror-delete core behavior was changed. No Google Sheet row was deleted or edited during this step.

## Code changes
Files changed:
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`

Added diagnostic fields:
- `duplicateRemoteRowSamples`
- `skippedInvalidRowSamples`

The app now logs duplicate record identities with concrete Google Sheet row numbers.

## Verification
- RED test observed for missing row diagnostic fields.
- Targeted diagnostic test: PASS.
- Remote record ID + sync rules tests: PASS.
- Debug APK build: PASS.
- APK install on real device: PASS.
- Device: `1a79dec0` / RMX3081.
- APK SHA256: `B6DDAED8EFFB3F7A6519139532F450ECCD619B19DC039C06D10FCBBAA1B71BB1`.
- Package `lastUpdateTime`: `2026-05-09 23:00:45`.

## Real-device sync result
Initial row diagnostic sync completed successfully.

Local DB after sync:
- DMBT total: `1823`
- HGT total: `35`
- Queue total: `0`
- `sourceSheetId IS NULL`: `0`
- All DMBT rows are `SYNCED`

DMBT by sheet:
- `849979183` / DMBT 2022: `37`
- `1783863163` / DMBT 2023: `532`
- `1224276666` / DMBT 2024: `583`
- `989601207` / DMBT 2025: `560`
- `1607125070` / DMBT 2026: `111`

## Repeat sync stability
Three more full-sync runs were executed after row diagnostic.

Results:
- Repeat 1: success, elapsed `12323ms`.
- Repeat 2: success, elapsed `11911ms`.
- Repeat 3: success, elapsed `11363ms`.
- No app crash or sync failure observed.
- The same duplicate/invalid data issues remained stable across repeats.

## Exact Sheet cleanup targets
### DMBT 2023
Sheet ID: `1783863163`

Duplicate identity groups:
- Rows `83` and `84`
- Rows `152` and `156`
- Rows `153` and `155`

### DMBT 2025
Sheet ID: `989601207`

Duplicate identity group:
- Rows `562` and `567`

### DMBT 2024
Sheet ID: `1224276666`

Invalid row:
- Row `580`: invalid/blank `ngay_phat_hien`

## Recommendation
Do not modify sync core further right now. Sync is stable across repeated real-device runs.

Before deleting or editing rows on Google Sheet, Director should decide cleanup policy:
- If duplicate rows are truly identical: delete one row from each duplicate pair.
- If duplicate rows contain different repair data/notes: keep both by assigning distinct real `record_id` values, or merge data manually then delete one.
- Fix DMBT 2024 row `580` by entering a valid `ngay_phat_hien` or removing the invalid row if it is not real data.

## Decision status
- App/sync: Nên duyệt cho controlled UAT continuation.
- Sheet data cleanup: Cần Giám đốc duyệt before destructive edit.

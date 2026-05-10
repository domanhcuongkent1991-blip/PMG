# Manager RCA - Large Delete Sync Check (2026-05-09)

## Request
User reported that when deleting too many rows on Google Sheet, sync still does not feel stable/accurate. Manager ran a real-device check on connected phone.

## Device / app
- Device: `1a79dec0` / Realme RMX3081.
- App package: `com.example.devicetracker`.
- Installed package `lastUpdateTime`: `2026-05-09 22:17:03`.

## Real sync run
- Cleared logcat before the run.
- UI was on Sync Status screen with Full Sync selected.
- Triggered `Đồng bộ đầy đủ` on device.
- Sync completed successfully in `11493ms`.

## Evidence from logcat
- `refreshFromRemote: DMBT fetched=1832, applied=2, skipped=1830`.
- Mirror delete per sheet:
  - `849979183`: remoteIds `37`, localSynced `37`, deleted `0`.
  - `1783863163`: remoteIds `532`, localSynced `532`, deleted `0`.
  - `1224276666`: remoteIds `583`, localSynced `583`, deleted `0`.
  - `989601207`: remoteIds `560`, localSynced `560`, deleted `0`.
  - `1607125070`: remoteIds `116`, localSynced `116`, deleted `0`.
- Warning:
  - DMBT 2024 has 1 skipped row because `ngay_phat_hien` is invalid/blank.

## Evidence from local DB after sync
- DMBT total: `1828`.
- HGT total: `35`.
- Combined local total: `1863`.
- Queue total: `0`.
- `sourceSheetId IS NULL`: `0`.
- Duplicate `recordId` in local DB: none.
- By sheet:
  - `849979183`: `37`
  - `1783863163`: `532`
  - `1224276666`: `583`
  - `989601207`: `560`
  - `1607125070`: `116`

## Root cause hypothesis from real evidence
The app did not fail during large-delete sync. It pulled the latest Google Sheet snapshot and local DMBT counts match the unique remote IDs seen by the app.

The unstable-looking count is likely caused by one or both of these real data issues:

1. Google Sheet still exposes `116` unique rows for DMBT 2026, so the app has no basis to delete below `116` for that sheet.
2. Google Sheet returned `1832` DMBT rows, but unique remote IDs per sheet total only `1828`. This indicates about `4` duplicate/identity-colliding remote rows. When rows are duplicated by identity, deleting many physical Sheet rows may not reduce app count one-for-one.

## What this means
If the user deletes 50 physical Sheet rows but some deleted rows are blank/invalid/duplicates or not part of app-valid DMBT identity set, the app count will not decrease by exactly 50.

For correct comparison, compare app DMBT count against valid unique DMBT rows on Google Sheet, not raw physical row count.

## Recommendation
Do not change mirror delete core yet. Next safe task should add diagnostic visibility:

- Log per-sheet duplicate identity count during pull.
- Optionally expose per-sheet DMBT counts in Sync Status for UAT.
- Add a warning when `fetched rows != unique remote IDs`, so user knows Sheet has duplicate/ambiguous rows.

## Decision
Chưa nên kết luận sync core sai. Cần làm thêm diagnostic duplicate/valid-row audit before another core fix.

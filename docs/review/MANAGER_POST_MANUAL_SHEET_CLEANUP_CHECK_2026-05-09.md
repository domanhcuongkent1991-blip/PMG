# Manager Review - Post Manual Google Sheet Cleanup Check (2026-05-09)

## Request
Director reported that risky rows were manually deleted on Google Sheet and asked to re-check.

## Device / app
- Device: `1a79dec0` / RMX3081.
- App package: `com.example.devicetracker`.
- Diagnostic APK installed earlier, package `lastUpdateTime`: `2026-05-09 23:00:45`.

## Real-device check
- App was already on Sync Status screen.
- Before sync:
  - Top total: `1858`
  - DMBT total: `1823`
  - Pending: `0`
- Full sync was triggered on the phone.
- Sync completed successfully in `12395ms`.

## Local DB after sync
- DMBT total: `1823`
- HGT total: `35`
- Combined total: `1858`
- Queue total: `0`
- `sourceSheetId IS NULL`: `0`
- All DMBT rows are `SYNCED`

By sheet:
- DMBT 2022 / `849979183`: `37`
- DMBT 2023 / `1783863163`: `532`
- DMBT 2024 / `1224276666`: `583`
- DMBT 2025 / `989601207`: `560`
- DMBT 2026 / `1607125070`: `111`

## Remaining Sheet data issues
Manual cleanup removed some rows, but risky rows still remain. Row numbers shifted after deletion.

### DMBT 2023
Sheet ID: `1783863163`

Still has 3 duplicate identity groups:
- Rows `80` and `81`
- Rows `148` and `152`
- Rows `149` and `151`

### DMBT 2025
Sheet ID: `989601207`

Still has 1 duplicate identity group:
- Rows `550` and `555`

### DMBT 2024
Sheet ID: `1224276666`

Still has 1 invalid row:
- Row `567`: blank/invalid `ngay_phat_hien`

## Conclusion
The app sync is stable and completed successfully. App-side local data is clean: queue `0`, all DMBT `SYNCED`, no null provenance.

However, Google Sheet still contains duplicate/invalid rows. The manual cleanup appears incomplete or row indices shifted after prior deletion.

## Recommendation
Continue manual Google Sheet cleanup using the updated row numbers above. After cleanup, run another full sync check. Do not change sync core at this time.

## Decision
- App/sync: Nên duyệt controlled UAT continuation.
- Sheet cleanup: Cần tiếp tục dọn dữ liệu Sheet theo row numbers mới.

# Manager RCA - DMBT delete count not fully matching expectation (2026-05-09)

## Context
User reported that after deleting rows on Google Sheet, the app count decreases but still does not look fully accurate.

## Evidence collected
- Device: `1a79dec0` / `RMX3081`.
- Latest sync logs show mirror delete is running per DMBT sheet.
- Example from device logcat:
  - `sourceSheetId=1607125070 remoteIds=67 localSynced=108 deletedStaleSynced=41 keptPendingOrFailed=0`.
- Local DB snapshot after sync:
  - DMBT total: `1779`.
  - HGT total: `35`.
  - UI top total: `1814 = 1779 + 35`.
  - Queue total: `0`.
  - `sourceSheetId IS NULL`: `0`.
  - All DMBT rows are `SYNCED`.
- DMBT split by source sheet:
  - `849979183`: `37`
  - `1783863163`: `532`
  - `1224276666`: `583`
  - `989601207`: `560`
  - `1607125070`: `67`

## Root cause conclusion
The sync delete logic did run and local DMBT now matches the remote snapshot counts seen by the app. The confusion comes from the Sync Status screen showing `Tổng bản ghi local` as a combined total of DMBT + HGT, while the user's Sheet deletion test is DMBT-only.

This means:
- The correct DMBT number to compare with Google Sheet is the DMBT split count, currently `1779`.
- The top total `1814` includes `35` HGT rows and should not be used as DMBT-only count.

## Safe UX fix applied
No sync-core logic was changed.

Changed files:
- `android-mvp/app/src/main/res/values/strings.xml`
  - Changed top label from `Tổng bản ghi local` to `Tổng local (DMBT + HGT)`.
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/sync/SyncStatusViewModel.kt`
  - If full sync changes DMBT/HGT/local totals, success message now shows counts as `DMBT before->after, HGT before->after, total before->after`.

## Verification
- `:app:testDebugUnitTest --tests com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest`: PASS.
- `:app:assembleDebug`: PASS.
- Installed APK on device with `adb install -r`: PASS.
- App launched after install: PASS.
- APK SHA256: `CE055D9E1DAA0970DB2E944F2DFE20C9E1DBA54CFDFA66426B05ACB3C480A979`.
- Package `lastUpdateTime`: `2026-05-09 22:04:56`.

## Remaining risk
If the user expects Google Sheet row count to equal app DMBT count exactly, the Sheet count must exclude:
- header row,
- blank rows,
- rows with invalid/missing `ngay_phat_hien`,
- rows outside configured DMBT sheet IDs,
- non-DMBT/HGT rows.

The app log currently shows at least one skipped row in `DMBT 2024` due invalid `ngay_phat_hien`.

## Decision
Nên duyệt UX clarification for controlled UAT.

Next UAT should compare Google Sheet DMBT valid row count against the DMBT split count, not against top combined total.

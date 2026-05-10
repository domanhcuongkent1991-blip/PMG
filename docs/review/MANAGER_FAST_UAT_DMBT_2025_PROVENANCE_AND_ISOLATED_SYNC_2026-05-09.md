# Manager Fast UAT - DMBT 2025 local create provenance + isolated pending sync

Date: 2026-05-09
Device: Realme RMX3081 / adb serial 1a79dec0
Package: com.example.devicetracker

## Goal
Validate the fast fix path for DMBT multi-year two-way sync, especially local-created DMBT 2025 records that previously had `sourceSheetId = null` and could not be safely pushed to the correct yearly Google Sheet.

## Code changes reviewed in this pass
- `SheetConfig`: resolves DMBT target sheet from discovery year. Mapping verified for 2025 -> `989601207`, 2026 -> `1607125070`.
- `EditViewModel`: new local DMBT records now store the resolved `sourceSheetId` at save time.
- `DeviceLogRepositoryImpl`: legacy pending rows with null `sourceSheetId` are backfilled before push.
- `DeviceLogRepositoryImpl`: ambiguous DMBT fallback failure is isolated per record, so one bad pending row no longer blocks the entire batch.

## Install evidence
- First install attempt failed because new APK was signed with a different debug key.
- Pulled installed APK and compared certificate.
- Correct signing key found at `android-mvp/.android-home/debug.keystore`.
- Rebuilt APK with matching signer fingerprint: `8124cd8faaecac25253f01e6a5de53ba8f53554c9351d5a6a532875737c81900`.
- Installed with `adb install -r`: Success.
- Package preserved data: firstInstallTime stayed `2026-05-07 20:55:45`, lastUpdateTime became `2026-05-09 19:16:37`.

## Pre-sync DB state
From `after_patch_install_read.db`:
- `CODEX2025B` existed as pending local marker.
- Before first sync attempt, marker had `sourceSheetId = null`.
- After provenance pre-push code ran, marker was backfilled to `sourceSheetId = 989601207`.

## First sync attempt result
The initial patched APK proved sourceSheetId backfill, but the batch still failed because one older pending record had an ambiguous fallback key:

`dmbt-auto-e2elo998-25_06_2026-jsjsjjss-jsjsjjssjnsnsn`

Failure mode:
- Queue snapshot: 6
- Candidate logs: 6
- All queue rows received the same ambiguous error.
- Root cause: batch push returned failure for one ambiguous row and did not allow the other valid rows to continue.

## Follow-up fix applied
Added isolated push fallback:
- Try normal batch push first.
- If the batch fails with `Ambiguous DMBT fallback key for push`, retry each pending record individually.
- Mark successful records as `SYNCED` and delete their queue items.
- Keep only failed ambiguous records in queue with `lastError`.
- Return success if at least one record synced, so full sync can continue to pull DMBT/HGT.

## Final UAT result
UI result after final sync:
- `Đồng bộ đầy đủ xong trong 25s: hàng đợi 6→1, pending 6→1.`
- Total local: 1905
- Total synced: 1904
- Total pending: 1
- DMBT total: 1853
- DMBT synced: 1852
- DMBT pending: 1

DB result from `after_isolated_sync_read.db`:
- `CODEX2025B | 09/05/2025 | sourceSheetId=989601207 | SYNCED`
- Remaining pending row: `E2ELO998 | 25/06/2026 | sourceSheetId=1607125070 | PENDING`
- Remaining queue row has targeted `lastError`: ambiguous fallback key for `e2elo998|25_06_2026|jsjsjjss|jsjsjjssjnsnsn`.

Logcat evidence:
- `syncPending ambiguous fallback isolated: successful=5, failed=1`
- `syncPending success: queueDeleted=5, keptQueue=0, markedSynced=5, staleLocal=0, failed=1`
- `syncNow step=pushLogs success=true`
- `syncNow step=pullLogs mode=FULL success=true`
- `syncNow step=pullHgt mode=FULL success=true`
- `syncNow success mode=FULL elapsedMs=25170`

## Evidence files
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/installed-base.apk`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/after_sync_logcat.txt`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/after_isolated_sync_logcat.txt`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/after_isolated_sync_screen.png`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/after_isolated_sync_read.db`

## Manager conclusion
Controlled UAT result: PASS with one known residual dirty pending row.

Approval recommendation: Should approve the provenance/isolation fix for controlled testing. Do not call the project fully clean yet because one historical duplicate/ambiguous row remains and should be handled as a data cleanup task, not as a sync-core blocker.

## Next recommended work
1. Add a small admin/debug cleanup flow for ambiguous pending rows: show exact record, allow director-approved delete or mark ignored.
2. Add unit test around partial pending sync isolation so future changes cannot reintroduce all-or-nothing failure.
3. Run one more clean app UAT after removing/ignoring the single ambiguous `E2ELO998` row.

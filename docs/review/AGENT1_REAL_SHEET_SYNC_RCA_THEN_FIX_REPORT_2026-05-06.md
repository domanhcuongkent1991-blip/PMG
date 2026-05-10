# AGENT1 REAL SHEET SYNC RCA THEN FIX REPORT (2026-05-06)

## 1) Scope & Safety
- Task ID: `A1-P0-REAL-SHEET-SYNC-RCA-THEN-FIX`
- This task did **not** write to real Google Sheet.
- RCA and fixes were validated only with local/unit fixtures and Android build pipeline.

## 2) Fail-first Evidence (Before Fix)
Pre-fix reproduction was executed with fixture-driven tests (real-sheet shape simulation):
- Command:
  - `./gradlew.bat -PcodexBuildId=agent1_p0_sync_fix_20260506_a :app:testDebugUnitTest --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRepairTest" --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRecordIdTest" --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest" --no-daemon --max-workers=1`
- Result:
  - `62 tests completed, 6 failed`
- Key failing points before final adjustments:
  - Repair parser behavior mismatch with real-sheet-like header detection and record_id-optional parsing.
  - Optional repair failure resolution did not treat `missing title` as recoverable optional case.

## 3) Confirmed Root Causes
### RC1 (Confirmed): Repair parser schema mismatch with real sheet format
- Real sheet can have:
  - title row at row 1
  - actual header at row 2
  - no stable `record_id`
  - no stable `updated_at`
- Old strict assumptions caused parse/merge instability.

### RC2 (Confirmed): Repair merge identity must support business-key fallback safely
- When repair row has no `record_id`, matching must use business key:
  - `ma_thiet_bi + ngay_phat_hien + hang_muc + tinh_trang_thiet_bi`
- If multiple local matches exist, merge must skip (fail-safe), not update random row.

### RC3 (Confirmed): DMBT fallback row mapping can be ambiguous
- If multiple sheet rows share same fallback key, unsafe map behavior can lead to wrong append/update.
- Needed explicit ambiguous-key detection and hard fail-safe.

### RC4 (Confirmed): Source provenance must still be carried when sheet lacks record_id/updated_at
- Pull path must still assign `sourceSheetId` and deterministic namespaced fallback id.

## 4) Files Changed
Production:
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/model/SheetValueMappers.kt`

Tests:
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRepairTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`

## 5) What Was Fixed (Minimal, P0-focused)
- Repair parser now scans header candidates in first 12 rows (same robustness class as DMBT parser).
- Repair parser supports 2 schema modes:
  - technical repair contract (with `record_id/updated_at`)
  - real DMBT-like full sheet contract (without required `record_id/updated_at`)
- Repair rows without `record_id` now carry business-key fields for safe resolver usage.
- Monthly repair merge only resolves to unique monthly DMBT candidate; ambiguous/not-found => skip + log.
- DMBT fallback row resolution now tracks ambiguous keys and throws fail-safe exception for push path.
- Added/kept test-only parser helper for missing `record_id/updated_at` pull simulation with `sourceSheetId` assignment.

## 6) Required Test Cases Coverage
Mapped to mandatory items:
1. Parse repair sheet with real header row 2 -> covered (`parseRepairRows_realSheetHeaderAtRow2_withoutRecordIdOrUpdatedAt_stillParsesRepairRow`).
2. Repair row without record_id + business key unique match -> covered.
3. Ambiguous business-key match -> skip/null -> covered.
4. DMBT push ambiguous fallback key -> fail-safe throw -> covered.
5. DMBT pull missing record_id/updated_at still assigns sourceSheetId -> covered.

## 7) Post-fix Verification
### Targeted unit verification
- Command:
  - `./gradlew.bat -PcodexBuildId=agent1_p0_sync_fix_20260506_b :app:testDebugUnitTest --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRepairTest" --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRecordIdTest" --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest" --no-daemon --max-workers=1`
- Result: **PASS** (exit code 0).

### Required project build verification
- Command:
  - `./scripts/build-android-safe.ps1`
- Result: **PASS** (exit code 0).

## 8) Residual Risks
- This task validates RCA/fix with fixtures and local tests only.
- Real-sheet data may still include edge schema drift beyond current fixture assumptions.
- Monthly/yearly partition logic is sensitive to bad legacy rows with incomplete business-key columns.

## 9) Explicit Release Note
- **Do not conclude “fully fixed in production” yet.**
- Real confirmation still requires controlled UAT on real Android phone + real Google Sheet with rollback marker protocol.

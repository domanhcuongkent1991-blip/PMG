# AGENT2 REPORT: Repair Merge UAT Review

**Task:** A2-UAT-REVIEW-SAFETY-FIX  
**Agent:** Agent 2 - Independent Review  
**Reviewed/Corrected by:** Managerial AI  
**Date:** 2026-05-05  
**Scope:** Repair merge review and safe real-sheet UAT rules

---

## 1. Current Decision

Director has approved controlled real UAT on Google Sheet and Android phone after both UAT runbooks are complete.

Approval does not mean uncontrolled testing. Every real-sheet test must:

- Use only a small number of rows.
- Use real `record_id` values that already exist in local/DMBT data.
- Put `CODEX_TEST_*` only in a note/marker field, not as `record_id`.
- Capture before/after evidence.
- Roll back only the test rows/values that were changed.

---

## 2. Build/Test Evidence

Command executed:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-safe.ps1
```

Result:

- `:app:testDebugUnitTest`: PASS.
- `:app:assembleDebug`: PASS.
- `BUILD SUCCESSFUL`.

Remaining non-blocking warnings:

- Android metrics warning in sandbox.
- Kotlin/Hilt deprecation warnings.
- Native symbol strip warning for one debug library.

---

## 3. Static Review Summary

| Item | Status | Evidence |
|---|---|---|
| Repository uses identity resolver | OK | `RepairRecordIdentityResolver.resolveRepairRecordId(...)` is used before local lookup |
| DAO exposes local IDs | OK | `DeviceLogDao.getAllRecordIds()` exists |
| Exact match behavior | Covered and test pass | Unit tests compiled and passed |
| Unique base ID behavior | Covered and test pass | Unit tests compiled and passed |
| Ambiguous base ID skip | Covered and test pass | Unit tests compiled and passed |
| Not found skip | Covered and test pass | Unit tests compiled and passed |
| PENDING/FAILED protection | Covered and test pass | `shouldMergeRepairIntoLocal(...)` tests passed |
| Repair pull failure reporting | OK | `refreshFromRemote()` returns failure if repair merge fails |

---

## 4. UAT Rules For Real Google Sheet

Allowed:

- Add one small test row to the repair sheet with a real existing `record_id`.
- Use `CODEX_TEST_REPAIR_*` in `ghi_chu` to identify the row.
- Pull/sync from the app and verify local data changed as expected.
- Delete only the test row that was added, or restore only the exact cell value recorded before the test.

Forbidden:

- Do not change sharing permissions.
- Do not remove access from any account.
- Do not delete, rename, hide, or reorder columns.
- Do not change header names.
- Do not intentionally create missing-column scenarios on the real workbook.
- Do not use fake `record_id` values such as `CODEX_TEST_EXACT` or `CODEX_TEST_BASE`.
- Do not write bulk rows.
- Do not test by changing workbook structure.

---

## 5. Safe UAT Cases

### UAT-01 Exact Namespaced Record ID

1. Select one DMBT record already synced locally.
2. Confirm `syncStatus = SYNCED`.
3. Confirm the local `recordId` is real, for example `readonly-dmbt-{sheetId}-{baseId}`.
4. Record current `ngaySuaChua` and `ghiChu`.
5. Add one repair-sheet row:
   - `record_id = <real local recordId>`
   - `ngay_sua_chua = <test date>`
   - `ghi_chu = CODEX_TEST_REPAIR_EXACT_<timestamp>`
6. Run full sync/pull in app.
7. Verify only the chosen local DMBT record was updated.
8. Roll back by deleting the exact test repair row and restoring previous local/sheet values if needed.

### UAT-02 Unique Base Record ID

1. Select one real namespaced DMBT record.
2. Extract `<baseId>` from `readonly-dmbt-{sheetId}-{baseId}`.
3. Verify that this base ID maps to exactly one local record.
4. Add one repair-sheet row:
   - `record_id = <real unique baseId>`
   - `ghi_chu = CODEX_TEST_REPAIR_BASE_<timestamp>`
5. Run full sync/pull in app.
6. Verify the one matching local record was updated.
7. Roll back the exact test row and changed values.

### UAT-03 Ambiguous Base ID Skip

Only run this if a controlled real/base ID has more than one local match.

1. Record all matching local records before the test.
2. Add one repair-sheet row with the ambiguous base ID and marker `CODEX_TEST_REPAIR_AMBIG_<timestamp>`.
3. Run full sync/pull.
4. Verify no matching local record was changed.
5. Roll back by deleting only that test row.

### UAT-04 PENDING Protection

Only run this if the app can create a local PENDING record safely.

1. Create or identify a local PENDING/FAILED record.
2. Record its current values.
3. Add one repair-sheet row using that real record ID and marker `CODEX_TEST_REPAIR_PENDING_<timestamp>`.
4. Run pull/sync.
5. Verify the local pending data was not overwritten.
6. Roll back the test row and local test data.

---

## 6. Evidence Template

| Field | Value |
|---|---|
| Test case | |
| Date/time | |
| Tester | |
| Sheet name | |
| Sheet gid/sheetId | |
| Real record_id used | |
| Marker | |
| Values before | |
| Values after | |
| App result | PASS / FAIL |
| Sheet rollback done | YES / NO |
| Notes/screenshots/logs | |

---

## 7. Release Decision

Static review and build/test can be accepted.

Release is still blocked until:

- Real-sheet DMBT/repair UAT passes.
- HGT sync UAT passes.
- HGT notification UAT passes on Android phone.
- Rollback evidence confirms no test data remains.

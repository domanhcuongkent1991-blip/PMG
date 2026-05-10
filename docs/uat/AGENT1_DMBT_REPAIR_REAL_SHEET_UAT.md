# UAT Runbook: DMBT And Repair Real Google Sheet

**Owner:** Agent 1  
**Reviewed/Corrected by:** Managerial AI  
**Date:** 2026-05-05  
**Status:** Approved for controlled real UAT after Manager review  
**Director approval:** Real Google Sheet UAT is allowed after both runbooks are complete.

---

## 1. Goal

Verify that DMBT and repair-sheet sync works safely on the real workbook:

- DMBT rows keep the correct `sourceSheetId`.
- Repair rows update only the intended existing DMBT record.
- Unique base ID can resolve to one matching local record.
- Ambiguous base ID is skipped.
- PENDING/FAILED local records are not overwritten.
- No bulk write, wrong sheet write, or duplicate row issue occurs.

---

## 2. Hard Safety Rules

Allowed:

- Use 1-2 controlled test rows.
- Use only real `record_id` values that already exist in local/DMBT data.
- Use `CODEX_TEST_*` only in `ghi_chu` or another note/marker field.
- Record before/after values.
- Roll back immediately after each test case.

Forbidden:

- Do not change sheet sharing permissions.
- Do not remove anyone's access.
- Do not delete, rename, hide, reorder, or add required columns.
- Do not change header names.
- Do not intentionally create missing-column errors on the real workbook.
- Do not use fake IDs like `CODEX_TEST_EXACT` as `record_id`.
- Do not rename monthly tabs during UAT unless Manager gives a separate command.
- Do not write bulk rows.
- Do not edit local database manually unless Manager explicitly instructs it.

---

## 3. Preconditions

- Debug build and unit tests have passed with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-safe.ps1
```

- APK is installed on the Android phone.
- Phone has stable internet.
- Google account has editor access to the workbook.
- App can run "dong bo day du".
- Tester can capture screenshots or logs.

---

## 4. How To Choose A Safe Real Record

Choose one DMBT record that satisfies all conditions:

- Exists in app/local data.
- Exists in the corresponding Google Sheet.
- `syncStatus = SYNCED`.
- `ngay_sua_chua` is blank if possible.
- `sourceSheetId` is known.
- Not already a test marker row.

Record this before testing:

| Field | Value |
|---|---|
| Sheet name | |
| gid/sheetId | |
| real record_id | |
| base ID | |
| ma_thiet_bi | |
| ngay_sua_chua before | |
| ghi_chu before | |

---

## 5. UAT Cases

### DMBT-01: Multi-Sheet Pull Keeps Correct sourceSheetId

1. Pick one real record from one DMBT sheet.
2. Run full sync in app.
3. Search by `ma_thiet_bi`.
4. Verify app record still belongs to the expected sheet/gid.
5. Repeat for one other DMBT sheet if time allows.

Pass:

- Record appears in app.
- `sourceSheetId` matches the sheet where the record came from.
- No record from another sheet is overwritten.

### REPAIR-01: Exact Namespaced record_id

1. Pick one real local namespaced record ID.
2. Add one row to repair sheet:
   - `record_id = <real namespaced record_id>`
   - `ngay_sua_chua = <test date>`
   - `ghi_chu = CODEX_TEST_REPAIR_EXACT_<timestamp>`
3. Run full sync in app.
4. Verify only that one DMBT record gets the repair date/note.
5. Roll back the test row and restore prior values if needed.

Pass:

- Intended record changed.
- Other records did not change.
- No false success message if sync fails.

### REPAIR-02: Unique Base record_id

1. Extract base ID from a real namespaced record.
2. Verify the base ID maps to exactly one local record.
3. Add one row to repair sheet:
   - `record_id = <real unique base ID>`
   - `ghi_chu = CODEX_TEST_REPAIR_BASE_<timestamp>`
4. Run full sync.
5. Verify the one matching local record changed.
6. Roll back.

Pass:

- Exactly one record changed.
- No duplicate or wrong-sheet change.

### REPAIR-03: Ambiguous Base record_id Skip

Run only if a controlled base ID has two or more local matches.

1. Record values for all matching local records.
2. Add one repair row with the ambiguous base ID and marker `CODEX_TEST_REPAIR_AMBIG_<timestamp>`.
3. Run full sync.
4. Verify no matching local record changed.
5. Roll back the repair test row.

Pass:

- App skips the repair row.
- No DMBT record is changed.

### REPAIR-04: PENDING/FAILED Protection

Run only if a safe local PENDING/FAILED test record is available.

1. Record current local values.
2. Add one repair row with that real record ID and marker `CODEX_TEST_REPAIR_PENDING_<timestamp>`.
3. Run full sync.
4. Verify local pending/failed values were not overwritten.
5. Roll back the test row.

Pass:

- PENDING/FAILED local data stays unchanged.

---

## 6. Rollback

After each test:

1. Delete only the test row that contains the exact `CODEX_TEST_*` marker.
2. Restore only the exact cells recorded in "before" values if they changed.
3. Run full sync again if needed.
4. Confirm no `CODEX_TEST_*` marker remains in the real workbook.

Do not bulk delete. Do not delete non-test production rows.

---

## 7. Evidence Table

| Test | Sheet | gid | record_id | Marker | Before | After | Rollback | Result |
|---|---|---|---|---|---|---|---|---|
| DMBT-01 | | | | | | | | |
| REPAIR-01 | | | | | | | | |
| REPAIR-02 | | | | | | | | |
| REPAIR-03 | | | | | | | | |
| REPAIR-04 | | | | | | | | |

---

## 8. Stop Conditions

Stop UAT immediately if:

- App writes to the wrong sheet.
- More rows than expected are changed.
- Sync reports success while expected data is missing.
- Any production row is changed without a recorded rollback.
- Any permission/header/structure issue appears.

Report the issue before continuing.

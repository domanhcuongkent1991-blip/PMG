# Manager Diagnostic - DMBT SheetStats Duplicate Audit (2026-05-09)

## Goal
Implement and run the next diagnostic step after the large-delete sync RCA. The aim is to identify whether Google Sheet physical row deletes map cleanly to app-valid unique DMBT identities.

## Code changes
Changed files:
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`

Added:
- `DmbtPullSheetStats` diagnostic model.
- `buildDmbtPullSheetStats(...)` helper.
- Per-sheet log line during DMBT pull:
  - `fetchedRows`
  - `uniqueRemoteIds`
  - `duplicateRemoteIds`
  - `duplicateRemoteIdSamples`
  - `skippedInvalidRows`
- Test coverage for duplicate remote identity counting.

No sync-core mirror-delete behavior was changed.

## Verification
- Red test was observed first: missing `buildDmbtPullSheetStats`.
- Red test was observed again for missing `duplicateRemoteIdSamples`.
- Targeted diagnostic test: PASS.
- Remote record ID + sync rules tests: PASS.
- `:app:assembleDebug`: PASS.
- APK installed on real device: PASS.
- APK SHA256: `83531FA02EEA74453285D8F0A3B88DCB8511DA3C2C5523EC52C540E08A78D86B`.
- Package `lastUpdateTime`: `2026-05-09 22:43:34`.

## Real-device sync diagnostic result
Device: `1a79dec0` / RMX3081.

After installing the diagnostic APK, full sync was run on the real app. Sync completed successfully in `13960ms`.

Per-sheet DMBT pull stats:

| Sheet | sheetId | fetchedRows | uniqueRemoteIds | duplicateRemoteIds | skippedInvalidRows |
|---|---:|---:|---:|---:|---:|
| DMBT 2022 | 849979183 | 37 | 37 | 0 | 0 |
| DMBT 2023 | 1783863163 | 535 | 532 | 3 | 0 |
| DMBT 2024 | 1224276666 | 583 | 583 | 0 | 1 |
| DMBT 2025 | 989601207 | 561 | 560 | 1 | 0 |
| DMBT 2026 | 1607125070 | 116 | 116 | 0 | 0 |

Mirror delete stats stayed safe:
- DMBT 2026: `remoteIds=116`, `localSynced=116`, `deletedStaleSynced=0`.
- Queue remained clean after sync.

## Duplicate identity samples
DMBT 2023 has 3 duplicate identities:

```text
readonly-dmbt-1783863163-dmbt-auto-474cc01-02_03_2023-lo_3_4-uong_dau_vao_van_ieu_khien_xy_lanh_thuy_luc_ro_dau_dang_tham_qua_vi_tri_au_noi_be_goc
readonly-dmbt-1783863163-dmbt-auto-474fn03-08_04_2023-lo_3_4-goi_quat_ro_dau_dang_tham_qua_co_truc_au_vao
readonly-dmbt-1783863163-dmbt-auto-474fn02-08_04_2023-lo_3_4-goi_quat_ro_dau_dang_tham_qua_mat_bich_2_nua
```

DMBT 2025 has 1 duplicate identity:

```text
readonly-dmbt-989601207-dmbt-auto-523bk01-26_12_2025-xuong_clk_3_4-day_cap_so_2_mon_ut_soi_nho
```

## Interpretation
The app is now proving that the Google Sheet currently contains duplicate app-identities in DMBT 2023 and DMBT 2025. Therefore, deleting many physical rows on Google Sheet may not reduce the app count one-for-one unless the deleted rows are valid unique identities.

DMBT 2026 itself currently has no duplicate identity according to the app (`116 fetchedRows`, `116 uniqueRemoteIds`). If DMBT 2026 is expected to be lower than 116, then Google Sheet is still returning 116 app-valid unique rows to the app.

## Decision
Chưa nên sửa mirror-delete core. The next safe step is data cleanup/audit on Google Sheet:
- Locate the duplicate identities above in DMBT 2023 and DMBT 2025.
- Decide whether duplicate rows should be deleted or assigned real `record_id` values.
- Fix the invalid/blank `ngay_phat_hien` row in DMBT 2024.
- Re-run full sync and compare valid unique DMBT row count against app DMBT count.

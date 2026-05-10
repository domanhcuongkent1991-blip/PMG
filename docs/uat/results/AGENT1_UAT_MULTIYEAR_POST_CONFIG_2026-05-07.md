# AGENT1 UAT Multiyear Post Config Result (2026-05-07)

## Task
- Task ID: `A1-UAT-RERUN-MULTIYEAR-POST-CONFIG`
- Scope: verify full sync after DMBT config fix can pull data for years 2022-2026 (not only 2026).

## 1) Preflight
- `node --version` -> `v24.12.0`
- `npm --version` -> `11.6.2`
- `npx --version` -> `11.6.2`
- `git --version` -> `2.53.0.windows.1`

## 2) Config Guard
- Command: `./scripts/verify-dmbt-sheet-config.ps1`
- Result: PASS
- Key output:
  - `SHEETS_DMBT_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070`
  - required yearly IDs 2022-2026 all present.

## 3) Build + Seedless Verify + Install
- Build: `./scripts/build-android-safe.ps1 -SkipTests` (previous run interrupted by user; UAT continued with verified safe build artifact)
- Seedless verify:
  - command: `./scripts/verify-apk-seedless.ps1 -LatestSafeBuild`
  - verified APK:
    - `F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\1778168351974\app\outputs\apk\debug\app-debug.apk`
  - SHA256:
    - `DC485F2FF31CFDAE8584491BA1B2D3869EADE3619438FED2A39333D33AB50E58`
  - guard: PASS
- Install result on device `1a79dec0`: PASS

## 4) Before-sync Gate (after user clear data + open app)
Evidence files:
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_before_sync.db`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_before_sync.db-wal`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_before_sync.db-shm`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/sql_before_sync_gate_output.txt`

SQL gate:
- `device_logs_before_sync = 0`
- `seed_beta_rows_before_sync = 0`

Gate verdict: PASS (allowed to continue sync1/sync2).

## 5) Sync1 Results
Evidence:
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync1.db`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync1.db-wal`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync1.db-shm`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/sql_after_sync1_output.txt`

Summary:
- `total_device_logs = 1852`
- `NULL sourceSheetId = 0`
- `duplicate_groups = 17`
- `rows_in_duplicate_groups = 34`

Count by gid:
- `849979183 (DMBT 2022) = 36`
- `1783863163 (DMBT 2023) = 531`
- `1224276666 (DMBT 2024) = 582`
- `989601207 (DMBT 2025) = 558`
- `1607125070 (DMBT 2026) = 145`
- `1383308512 (DMBT tháng) = 0`
- `157327514 (Sửa chữa tháng) = 0`

## 6) Sync2 Results
Evidence:
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync2.db`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync2.db-wal`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/device_tracker_after_sync2.db-shm`
- `docs/uat/evidence/multiyear-post-config-2026-05-07/sql_after_sync2_output.txt`

Summary:
- `total_device_logs = 1852`
- `NULL sourceSheetId = 0`
- `duplicate_groups = 17`
- `rows_in_duplicate_groups = 34`

Count by gid:
- `849979183 (DMBT 2022) = 36`
- `1783863163 (DMBT 2023) = 531`
- `1224276666 (DMBT 2024) = 582`
- `989601207 (DMBT 2025) = 558`
- `1607125070 (DMBT 2026) = 145`
- `1383308512 (DMBT tháng) = 0`
- `157327514 (Sửa chữa tháng) = 0`

## 7) Sync1 vs Sync2 Delta
- total_device_logs: `1852 -> 1852` (delta `0`)
- duplicate_groups: `17 -> 17` (delta `0`)
- rows_in_duplicate_groups: `34 -> 34` (delta `0`)
- per-year counts 2022-2026: unchanged (delta `0` each)

Idempotency verdict: PASS (sync2 did not increase duplicates).

## 8) PASS/FAIL Against Task Criteria
Criteria check:
1. 2022-2026 all > 0 after sync1: PASS
2. sync2 does not increase duplicate: PASS
3. evidence DB + SQL complete: PASS

Overall verdict: **PASS**

## 9) Notes
- No production code changes in this task.
- Monthly gids `1383308512` and `157327514` stayed at `0` in both sync1/sync2.

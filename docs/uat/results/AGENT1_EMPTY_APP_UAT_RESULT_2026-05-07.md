# AGENT1 Empty App UAT Result (2026-05-07)

## 1) Scope
- Task: `A1-UAT-EXECUTE-EMPTY-APP`
- Device: `RMX3081` (Android 12), serial `1a79dec0`
- App package: `com.example.devicetracker`
- Source packet: `docs/uat/AGENT2_EMPTY_APP_UAT_PACKET_2026-05-07.md`
- Constraint: no production code change, no real Google Sheet write.

## 2) Evidence Collected
- Sync 1 DB snapshot:
  - `docs/uat/evidence/empty-app-2026-05-07/device_tracker_after_sync1.db`
- Sync 1 SQL output:
  - `docs/uat/evidence/empty-app-2026-05-07/sql_sync1_output.txt`
- Sync 2 DB snapshots:
  - `docs/uat/evidence/empty-app-2026-05-07/device_tracker_after_sync2.db`
  - `docs/uat/evidence/empty-app-2026-05-07/device_tracker_after_sync2.db-wal`
  - `docs/uat/evidence/empty-app-2026-05-07/device_tracker_after_sync2.db-shm`
- Sync 2 SQL output:
  - `docs/uat/evidence/empty-app-2026-05-07/sql_sync2_output.txt`

## 3) SQL Summary

### Sync 1
- total_device_logs: `1891`
- sourceSheetId counts:
  - `NULL = 1835`
  - `1607125070 (DMBT 2026) = 56`
- null_sourceSheetId: `1835`
- duplicate_groups: `27`
- rows_in_duplicate_groups: `63`

### Sync 2
- total_device_logs: `1891`
- sourceSheetId counts:
  - `NULL = 1835`
  - `1607125070 (DMBT 2026) = 56`
- null_sourceSheetId: `1835`
- duplicate_groups: `27`
- rows_in_duplicate_groups: `63`

### Sync1 vs Sync2 delta
- total_device_logs: `0`
- null_sourceSheetId: `0`
- duplicate_groups: `0`
- rows_in_duplicate_groups: `0`
- Interpretation: no new duplicate introduced by sync lần 2 (idempotent for current pulled scope).

## 4) Count by Expected DMBT Yearly GIDs
- `849979183` (DMBT 2022): `0`
- `1783863163` (DMBT 2023): `0`
- `1224276666` (DMBT 2024): `0`
- `989601207` (DMBT 2025): `0`
- `1607125070` (DMBT 2026): `56`

## 5) Monthly GID Check
- `1383308512` (DMBT tháng): `0`
- `157327514` (Sửa chữa tháng): `0`

## 6) PASS/FAIL Verdict
- DMBT 2022: **FAIL** (không có row provenance theo gid 2022)
- DMBT 2023: **FAIL** (không có row provenance theo gid 2023)
- DMBT 2024: **FAIL** (không có row provenance theo gid 2024)
- DMBT 2025: **FAIL** (không có row provenance theo gid 2025)
- DMBT 2026: **PARTIAL PASS** (pull được 56 rows và stable qua sync 2)
- Monthly DMBT/Repair gid (`1383308512`, `157327514`): **N/A trong packet này** (đều = 0)
- Overall empty-app auto-pull + provenance: **FAIL**

Reason tổng: dữ liệu local sau sync vẫn có `sourceSheetId NULL = 1835`, trong khi kỳ vọng provenance phải xác định được gid rõ cho các sheet DMBT 2022-2026.

## 7) Safety Notes
- Không có ghi dữ liệu lên Google Sheet thật trong lượt này.
- Không có sửa code app/test.
- Không kết luận "đã hết lỗi" vì kết quả hiện tại cho thấy sync 2 chiều toàn bộ năm chưa đạt.

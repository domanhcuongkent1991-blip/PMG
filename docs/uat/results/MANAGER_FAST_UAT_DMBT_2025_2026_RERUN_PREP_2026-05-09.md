# Manager Fast UAT DMBT 2025-2026 Rerun Prep (2026-05-09)

## Context

- Director reported the Android phone is connected.
- This rerun targets the previously blocked fast-track UAT for DMBT 2025/2026 2-way sync.
- Device serial: `1a79dec0`.
- Package: `com.example.devicetracker`.

## Device / App Verification

- `adb devices -l` showed device `1a79dec0` connected.
- Package `com.example.devicetracker` is installed.
- Activity resolves to `com.example.devicetracker/.MainActivity`.
- Package metadata:
  - `versionCode=1`
  - `versionName=0.1.0`
  - `lastUpdateTime=2026-05-08 23:30:30`
- App launched successfully.
- `MainActivity` was resumed and focused.

## Evidence Captured

- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/app_before_2way.png`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/ui_before_2way.xml`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/drawer.png`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/ui_drawer.xml`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/sync_screen.png`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/ui_sync_screen.xml`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/device_tracker_before_2way.db`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/device_tracker_before_2way.db-wal`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/device_tracker_before_2way.db-shm`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/sql_before_2way_output.txt`
- `docs/uat/evidence/fast-uat-dmbt-2025-2026-rerun-2026-05-09/pending_before_2way.txt`

## Baseline Before 2-way UAT

From `sql_before_2way_output.txt`:

- `total_device_logs = 1852`
- `NULL sourceSheetId = 0`
- DMBT 2022 gid `849979183 = 36`
- DMBT 2023 gid `1783863163 = 531`
- DMBT 2024 gid `1224276666 = 582`
- DMBT 2025 gid `989601207 = 558`
- DMBT 2026 gid `1607125070 = 145`
- `duplicate_groups = 17`
- `rows_in_duplicate_groups = 34`
- `syncStatus PENDING = 5`
- `syncStatus SYNCED = 1847`

## Important Risk Before Pressing Sync

The sync screen shows 5 pending DMBT records. DB inspection confirms all 5 pending records have:

- `syncStatus = PENDING`
- `operation = UPSERT_LOG`
- `sourceSheetId = 1607125070` (DMBT 2026)

If Manager presses `Đồng bộ đầy đủ`, the app may write these 5 pending records to the real Google Sheet DMBT 2026 tab.

## Current Decision Point

- Device blocker: RESOLVED.
- App launch blocker: RESOLVED.
- Baseline DB evidence: CAPTURED.
- 2026 App -> Sheet can be tested by syncing the 5 pending DMBT 2026 rows, but this requires Director approval because it writes to the real Google Sheet.
- 2025 App -> Sheet still needs a deliberate 2025 marker edit before sync, or else it cannot be proven by the current pending queue.

## Recommendation

Ask the Director to approve one of the following:

1. Press sync now to test the existing 5 pending DMBT 2026 rows, then capture after-sync DB and Sheet-side evidence.
2. First create/prepare a clear DMBT 2025 marker edit, then sync once for both 2025 and 2026 evidence.
3. Do not write to the real Sheet yet; keep this as device-ready baseline evidence only.

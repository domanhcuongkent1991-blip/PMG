# AGENT1 Seedless Install Guard UAT Result (2026-05-07)

## Task
- Task ID: `A1-SEEDLESS-INSTALL-GUARD-UAT`
- Device: `RMX3081` (serial `1a79dec0`)
- Package: `com.example.devicetracker`

## 1) Preflight
- node: `v24.12.0`
- npm: `11.6.2`
- npx: `11.6.2`
- git: `2.53.0.windows.1`

## 2) Build + Guard
- Build command: `./scripts/build-android-safe.ps1` -> PASS
- Guard command: `./scripts/verify-apk-seedless.ps1 -LatestSafeBuild` -> PASS
- Guard output file:
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/verify_apk_seedless_output.txt`

### Verified APK
- Path:
  - `F:\codex_android_gsheet_full_pack\android-mvp\.codex-build\1778165752159\app\outputs\apk\debug\app-debug.apk`
- SHA256:
  - `56EB389A3A68F2CC1081C87E42C2B442F91BB5BD2BE1A81DBD285A7A2E0BACC3`

## 3) Install + Installed APK Hash Check
- Installed exactly verified APK path above via `adb install -r -t -g`.
- Pulled installed base APK (binary-safe) to:
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/installed_base_binary.apk`
- Hash compare evidence:
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/apk_hash_compare.txt`
- Result: `match=True` (installed hash == verified hash)

## 4) Before-sync DB Gate
- DB files pulled before sync:
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/device_tracker_before_sync.db`
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/device_tracker_before_sync.db-wal`
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/device_tracker_before_sync.db-shm`
- SQL output:
  - `docs/uat/evidence/seedless-install-guard-2026-05-07/sql_before_sync_gate_output.txt`

### SQL Results
- `device_logs_before_sync = 145`
- `seed_beta_rows_before_sync = 0`
- sourceSheetId breakdown:
  - `1607125070 = 145`

## 5) Gate Verdict
- Required PASS conditions:
  - `device_logs_before_sync = 0`
  - `seed_beta_rows_before_sync = 0`
- Actual:
  - `device_logs_before_sync = 145` -> FAIL
  - `seed_beta_rows_before_sync = 0` -> PASS

## Final Decision
- **FAIL** (clean baseline not achieved)
- As required, workflow is **stopped immediately**.
- **No sync executed**.

## Notes
- This run confirms stale-seed issue is fixed at APK level (no `seed-beta-*` reseed).
- Remaining issue is non-empty pre-sync data (`145` rows, all `sourceSheetId=1607125070`) still present after clear-data/open flow, so baseline is not empty yet.

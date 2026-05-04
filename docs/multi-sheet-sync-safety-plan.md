# Multi-Sheet Sync Safety Plan

Updated: 2026-05-01

## Goal

Mo rong dong bo Google Sheets ma khong lam hong luong DMBT/HGT dang hoat dong.

## Non-negotiable Safety Rules

1. Khong tu dong sync tat ca tab.
2. Tab/sheet moi mac dinh `INVENTORY_ONLY`.
3. Chi doc metadata/header khi chua co contract.
4. Chi bat `PULL_ONLY` sau dry-run pass.
5. Chi bat `TWO_WAY` khi co khoa chinh, conflict policy, rollback va test.
6. Loi mot sheet khong duoc lam lan sang sheet khac.
7. Khong ghi secret vao log, report, JSON output hoac Google Sheet.

## Implemented In This Pass

1. Added `SheetSyncRegistry`.
2. Added default safe modes:
   - `DMBT_LOG`: `TWO_WAY`
   - `HGT_CHECKS`: `TWO_WAY`
   - `DEVICE_MASTER`: `INVENTORY_ONLY`
   - `LOOKUP_OPTIONS`: `INVENTORY_ONLY`
   - `APP_CONFIG`: `INVENTORY_ONLY`
3. Added contract columns for future sheets.
4. Added `SheetDryRunValidator` for header-level dry-run.
5. Added `scripts/export-sheets-inventory.ps1`.
6. Added `npm run ops:sheets-inventory`.
7. Added read-only DMBT whitelist support via `SHEETS_DMBT_READONLY_SHEET_IDS`.
8. Added namespace rule for read-only DMBT records: `readonly-dmbt-{sheetId}-{recordId}`.

## Current Workbook Discovery

Online inventory on 2026-05-01 found these extra DMBT-like tabs:

| Sheet ID | Title | Detected kind | Valid rows | Safety mode |
|---:|---|---|---:|---|
| `849979183` | `DMBT 2022` | `DMBT_LOG_CANDIDATE` | 36 | Read-only candidate |
| `1783863163` | `DMBT 2023` | `DMBT_LOG_CANDIDATE` | 534 | Read-only candidate |
| `1224276666` | `DMBT 2024` | `DMBT_LOG_CANDIDATE` | 582 | Read-only candidate |
| `989601207` | `DMBT 2025` | `DMBT_LOG_CANDIDATE` | 559 | Read-only candidate |
| `1383308512` | `DMBT T4.2026` | `DMBT_LOG_CANDIDATE` | 21 | Read-only candidate |
| `157327514` | `Sua chua T4.2026` | `REPAIR_LOG_CANDIDATE` | 22 | Review before enabling |

Recommended first whitelist, after user accepts seeing historical DMBT records inside the app:

```properties
SHEETS_DMBT_READONLY_SHEET_IDS=849979183,1783863163,1224276666,989601207,1383308512
```

Do not enable the repair tab as DMBT until the app has a dedicated repair contract/UI path.

## Next Gates

### Gate 1: Inventory Online

Run:

```powershell
npm run ops:sheets-inventory
```

Expected:
- `docs/sheets-inventory.md` exists.
- DMBT/HGT sheet IDs resolve to titles.
- Header rows are visible.
- No secrets are printed.

Current note:
- Online inventory may fail on the host if PowerShell HTTPS cannot receive from Google.
- Offline/config inventory remains available and is covered by tests.

### Gate 2: Device Install Baseline

Install latest APK and verify DMBT/HGT still work before enabling any new sheet.

### Gate 3: DEVICE_MASTER Pull-Only

Only start after:
- `DEVICE_MASTER` sheetId is configured.
- Dry-run header passes.
- Local Room staging/transaction strategy is designed.

### Gate 4: Read-Only Historical DMBT Pull

Already implemented behind config:
- Primary `DMBT_LOG` remains two-way.
- `SHEETS_DMBT_READONLY_SHEET_IDS` is pull-only.
- Read-only rows are namespaced so they cannot overwrite primary/app-created DMBT records.
- A malformed read-only tab is skipped and logged instead of failing the primary DMBT pull.

## Rollback Policy

For new sheets:
- Do not overwrite main tables directly.
- Parse into memory or staging first.
- Validate counts and duplicate keys.
- Merge only inside transaction.
- If validation fails, keep old local data.

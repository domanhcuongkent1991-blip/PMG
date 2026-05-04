# Google Sheets Inventory

- Generated: 2026-05-01T14:10:26.224Z
- Status: PASS
- Online attempted: true
- Row stats attempted: true

| Role | Configured | Sheet ID | Title | Header status | Header row | Row stats | Headers | Error |
|---|---:|---:|---|---|---:|---|---|---|
| DEVICE_MASTER | false |  |  | NOT_CONFIGURED |  |  |  |  |
| DMBT_LOG | true | 1607125070 | DMBT 2026 | OK | 2 | {"total_data_rows":131,"valid_rows":128,"skipped_rows":3,"repaired_rows":61,"pending_rows":67} | STT, Hạng mục, Người báo cáo, Mã thiết bị, Tình trạng thiết bị, KTV phụ trách , Ngày phát hiện, Ngày sửa chữa, Ghi chú |  |
| HGT_CHECKS | true | 57428884 | HGT định kỳ | OK | 1 | {"total_data_rows":52,"valid_rows":49,"skipped_rows":3} | STT, Thiết bị , Chù kì(ngày), Lần gần nhất, Lần tiếp theo |  |
| LOOKUP_OPTIONS | false |  |  | NOT_CONFIGURED |  |  |  |  |
| APP_CONFIG | false |  |  | NOT_CONFIGURED |  |  |  |  |

Safety notes:
- This inventory only reads metadata/header rows.
- It never writes Google Sheets or local app data.
- Secrets are intentionally excluded from this report.

## All Spreadsheet Tabs

| Sheet ID | Title | Mapped role / detected kind | Header row | Row stats |
|---:|---|---|---:|---|
| 849979183 | DMBT 2022 | DMBT_LOG_CANDIDATE | 2 | {"total_data_rows":44,"valid_rows":36,"skipped_rows":8,"repaired_rows":0,"pending_rows":36} |
| 1783863163 | DMBT 2023 | DMBT_LOG_CANDIDATE | 2 | {"total_data_rows":546,"valid_rows":534,"skipped_rows":12,"repaired_rows":471,"pending_rows":63} |
| 1224276666 | DMBT 2024 | DMBT_LOG_CANDIDATE | 2 | {"total_data_rows":596,"valid_rows":582,"skipped_rows":14,"repaired_rows":474,"pending_rows":108} |
| 989601207 | DMBT 2025 | DMBT_LOG_CANDIDATE | 2 | {"total_data_rows":571,"valid_rows":559,"skipped_rows":12,"repaired_rows":371,"pending_rows":188} |
| 1607125070 | DMBT 2026 | DMBT_LOG | 2 | {"total_data_rows":131,"valid_rows":128,"skipped_rows":3,"repaired_rows":61,"pending_rows":67} |
| 1383308512 | DMBT T4.2026 | DMBT_LOG_CANDIDATE | 2 | {"total_data_rows":60,"valid_rows":21,"skipped_rows":39,"repaired_rows":6,"pending_rows":15} |
| 157327514 | Sửa chữa T4.2026 | REPAIR_LOG_CANDIDATE | 2 | {"total_data_rows":55,"valid_rows":22,"skipped_rows":33,"repaired_rows":22,"pending_rows":0} |
| 57428884 | HGT định kỳ | HGT_CHECKS | 1 | {"total_data_rows":52,"valid_rows":49,"skipped_rows":3} |

## Unmapped Tabs

- 849979183: DMBT 2022
- 1783863163: DMBT 2023
- 1224276666: DMBT 2024
- 989601207: DMBT 2025
- 1383308512: DMBT T4.2026
- 157327514: Sửa chữa T4.2026

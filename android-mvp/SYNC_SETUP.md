# SYNC SETUP (Google Sheets)

Cap nhat: 2026-05-03

## 1) Tao local config
1. Copy `local.properties.example` -> `local.properties`.
2. Dien toi thieu:
   - `SHEETS_SPREADSHEET_ID`
   - `SHEETS_DMBT_LOG_SHEET_ID`
   - Sau khi implement yeu cau moi: `SHEETS_DMBT_SHEET_IDS` va `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`
   - `SHEETS_HGT_CHECKS_SHEET_ID`
   - `SHEETS_OAUTH_CLIENT_ID` + `SHEETS_REFRESH_TOKEN`, hoac tam thoi `SHEETS_ACCESS_TOKEN` cho dev

## 1.1) Cau hinh sheet da duoc user chot

Workbook:

```properties
SHEETS_SPREADSHEET_ID=1WWZ3CoeJowlqGUiotwCwXIuijCHGbSCVwCcz3WacYGQ
```

Sheet DMBT hien tai trong code legacy:

```properties
# DMBT T4.2026
SHEETS_DMBT_LOG_SHEET_ID=1383308512
```

Yeu cau san pham moi: tat ca DMBT sheet sau phai tro thanh hai chieu. Code can doi sang cau hinh source-sheet-aware:

```properties
SHEETS_DMBT_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070,1383308512
SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID=1383308512
```

Luu y: `SHEETS_DMBT_READONLY_SHEET_IDS` chi la key legacy cua code hien tai, khong con dung voi yeu cau san pham cuoi.

Sheet HGT:

```properties
# HGT dinh ky
SHEETS_HGT_CHECKS_SHEET_ID=57428884
```

Sheet sua chua:

```properties
# Sua chua T4.2026
# Luu y: ten sheet thay doi theo thang. Dung gid/config, khong hard-code title.
# Nguoi dung se tu doi ten sheet theo thang. Neu chi rename tab hien co thi gid khong doi.
# Neu tao tab moi cho thang moi thi phai cap nhat gid trong config.
# Contract/config da co; mapper/network push-pull rieng can workbook test truoc khi bat production.
SHEETS_REPAIR_LOG_SHEET_ID=157327514
```

## 2) Lay sheetId an toan
- `sheetId` la `gid` cua tab.
- Mo tab trong Google Sheets, URL se co dang `...#gid=123456789`.
- Dung gia tri `123456789` cho cac key sheetId/gid trong config.

Danh sach `gid` da chot:

| Tab | gid | Mode |
|---|---:|---|
| `DMBT 2022` | `849979183` | `TWO_WAY` |
| `DMBT 2023` | `1783863163` | `TWO_WAY` |
| `DMBT 2024` | `1224276666` | `TWO_WAY` |
| `DMBT 2025` | `989601207` | `TWO_WAY` |
| `DMBT 2026` | `1607125070` | `TWO_WAY` |
| `DMBT T4.2026` | `1383308512` | `TWO_WAY`; ten sheet thay doi theo thang |
| `Sua chua T4.2026` | `157327514` | `TWO_WAY`; ten sheet thay doi theo thang |
| `HGT dinh ky` | `57428884` | `TWO_WAY` sau dry-run pass |

## 3) Token cho moi truong dev
- `SHEETS_ACCESS_TOKEN` chi dung tam thoi trong local dev.
- Khong commit token.
- Neu token het han/khong hop le, app se fail sync theo huong non-retryable.

## 4) Kiem tra nhanh truoc khi chay
1. Header tab `DMBT_LOG` phai co du:
   - `record_id`
   - `ma_thiet_bi`
   - `hang_muc`
   - `nguoi_bao_cao`
   - `tinh_trang_thiet_bi`
   - `ktv_phu_trach`
   - `ngay_phat_hien`
   - `ngay_sua_chua`
   - `ghi_chu`
   - `updated_at`
2. Header tab `HGT_CHECKS` (co the dung ten tab `HGT định kỳ`) phai co du toi thieu:
   - `thiet bi` (hoac `ma_thiet_bi`)
   - `chu ki(ngay)` (hoac `chu_ky_ngay`)
   - `lan gan nhat` (hoac `lan_gan_nhat`)
   - `lan tiep theo` (hoac `lan_tiep_theo`)
   - Khuyen nghi them 2 cot chuan de sync 2 chieu ben vung: `record_id`, `updated_at`
2. Build:
   - `.\gradlew.bat --no-daemon assembleDebug`
3. Test:
   - `.\gradlew.bat --no-daemon testDebugUnitTest`

## 5) Hanh vi sync hien tai
- Push idempotent theo `record_id`:
  - Co `record_id` tren sheet -> update row.
  - Chua co -> append row.
- Pull:
  - Doc header va map nguoc thanh `DeviceLog`.
  - Row sai contract -> dung sync va bao loi ro.
- HGT:
  - Push/pull 2 chieu cho khu `HGT định kỳ`.
  - Xoa HGT tren app se xoa dong tuong ung tren sheet theo `ma_thiet_bi`.
  - Neu sheet co `record_id` thi app uu tien dong bo theo `record_id` de tranh trung lap khi sua ma thiet bi.

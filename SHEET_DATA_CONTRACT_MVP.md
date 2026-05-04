# SHEET DATA CONTRACT (MVP)

Cap nhat: 2026-05-03

## 1) Muc tieu
- Dong bo du lieu an toan theo local-first.
- Khong phu thuoc ten tab hoac thu tu tab.
- Mapping du lieu ro rang theo `role + sheetId + column header`.
- Dap ung PRD: dong bo day du cac sheet duoc cau hinh cho app, khong tu dong dong bo tab la/khong co contract.

## 1.1) Dien giai yeu cau "tat ca sheet" trong PRD

PRD yeu cau "dong bo xong tat ca cac sheet ben trong Google Sheet vao app". De tranh mat du lieu, yeu cau nay duoc ap dung theo rule sau:

1. "Tat ca sheet" = tat ca sheet da duoc khai bao trong cau hinh app hoac whitelist an toan.
2. App khong tu dong quet toan workbook de sync moi tab.
3. Moi sheet moi phai co:
   - `sheetId` on dinh
   - role ro rang
   - mode sync ro rang
   - contract cot
   - dry-run header pass
   - rule conflict/rollback neu co ghi 2 chieu
4. Sheet nao chua co contract chi duoc o `INVENTORY_ONLY`, khong duoc doc du lieu nghiep vu va khong duoc ghi nguoc.

Ly do: workbook co the co tab nhap, tab tong hop, tab tam, tab sai cot. Tu dong keo/ghi moi tab se lam app de bi roi du lieu hoac ghi nham.

## 2) Role -> Sheet

| Role | Bat buoc cho sync | Sheet ID | Ghi chu |
|---|---|---|---|
| `DEVICE_MASTER` | Chua bat sync | Cau hinh sau | Mac dinh `INVENTORY_ONLY`, chi duoc bat `PULL_ONLY` sau dry-run pass |
| `DMBT_LOG` | Co | Bat buoc cau hinh | Bang nghiep vu chinh cua app, dang `TWO_WAY` |
| `HGT_CHECKS` | Co trong app | Bat buoc neu dung HGT | HGT dinh ky, dang `TWO_WAY`; can tiep tuc verify worker dinh ky tren may that |
| `LOOKUP_OPTIONS` | Chua bat sync | Cau hinh sau | Mac dinh `INVENTORY_ONLY`, co fallback hardcoded neu loi |
| `APP_CONFIG` | Chua bat sync | Cau hinh sau | Mac dinh `INVENTORY_ONLY`, chi cho phep whitelist key an toan |

### 2.0.1) DMBT multi-sheet configuration

Yeu cau san pham moi ngay 2026-05-03: tat ca sheet DMBT trong pham vi user chot deu can dong bo hai chieu giua app va Google Sheet.

Luu y lich su: plan cu tung coi cac sheet DMBT nam cu la `PULL_ONLY`. Gia dinh do khong con dung sau khi user mo ta lai ung dung. Tu nay khong duoc mac dinh DMBT 2022..2026 la read-only.

App van can cau hinh danh sach sheet DMBT bang `gid/sheetId`, nhung ten config can duoc doi lai trong code sau nay vi `SHEETS_DMBT_READONLY_SHEET_IDS` khong con dung nghia san pham nua:

```properties
SHEETS_DMBT_READONLY_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070
```

Quy tac an toan:
- App khong tu dong quet toan workbook de sync, tranh keo nham tab.
- Moi sheet hai chieu phai co role/config/contract cot/khoa chinh rieng truoc khi ghi.
- Neu mot sheet sai schema, app phai bao loi ro va khong duoc bao sync thanh cong gia.
- Cac sheet theo thang nhu `DMBT T4.2026` va `Sua chua T4.2026` khong duoc phu thuoc vao ten co dinh, vi ten thay doi theo thang.
- Nguoi dung se tu doi ten sheet theo tung thang. Neu chi rename tab hien co thi `gid/sheetId` giu nguyen; app phai dua vao `gid/sheetId` nen van sync duoc.
- Khi sang thang moi, user se rename tab cu, vi du `DMBT T4.2026` thanh `DMBT T5.2026`; `gid/sheetId` giu nguyen nen app khong duoc dua vao title co dinh.
- User cho phep test ghi truc tiep tren Google Sheet that, khong can workbook test copy. Van phai test theo tung buoc nho, co dry-run/evidence, khong ghi hang loat ngay.
- Voi cac sheet DMBT cu 2022..2025, thao tac chinh la dien `ngay_sua_chua` va `ghi_chu` de danh dau loi da sua. Khong duoc hieu la xoa row khoi Sheet.

### 2.0.2) Pham vi sheet da chot tu hinh anh ngay 2026-05-03

Nguoi dung da danh dau o do trong Google Sheets. Cac tab sau la pham vi can dong bo vao app:

| Ten tab trong anh | Nhom du lieu | Mode an toan mac dinh | Ghi chu |
|---|---|---|---|
| `DMBT 2022` | DMBT theo nam | `TWO_WAY` | `gid=849979183`; user da mo ta tat ca sheet deu cap nhat hai chieu |
| `DMBT 2023` | DMBT theo nam | `TWO_WAY` | `gid=1783863163`; hai chieu |
| `DMBT 2024` | DMBT theo nam | `TWO_WAY` | `gid=1224276666`; hai chieu |
| `DMBT 2025` | DMBT theo nam | `TWO_WAY` | `gid=989601207`; hai chieu |
| `DMBT 2026` | DMBT theo nam | `TWO_WAY` | `gid=1607125070`; hai chieu |
| `DMBT T4.2026` | DMBT theo thang | `TWO_WAY` | `gid=1383308512`; ten sheet thay doi theo thang, khong duoc hard-code theo ten |
| `Sua chua T4.2026` | Sua chua theo thang | `TWO_WAY` | `gid=157327514`; user da chot app duoc keo ve va ghi nguoc; can contract/mapper rieng neu cot khac DMBT |
| `HGT dinh ky` | HGT dinh ky | `TWO_WAY` | `gid=57428884`; da co luong HGT trong app |

Quy tac an toan:
- `gid/sheetId` that da duoc user cung cap ngay 2026-05-03, nhung khong code cung vao source production; chi dua vao `local.properties` hoac config an toan.
- Chua co dry-run header pass thi chua duoc bat ghi nguoc tren workbook that.
- Neu `Sua chua T4.2026` co schema khac `DMBT_LOG`, phai tao contract va mapper rieng, khong ep dung mapper DMBT.
- Tat ca DMBT sheet trong scope deu can hai chieu, nhung app phai ghi dung sheet nguon cua ban ghi, khong day moi moi ban ghi vao mot sheet duy nhat.
- Sheet theo thang hien tai da duoc user neu vi du la `DMBT T4.2026` va co the doi thanh `DMBT T5.2026` khi sang thang 5.
- User xac nhan viec doi ten theo thang se do nguoi dung tu thuc hien trong Google Sheets.

## 2.1) Sync mode an toan

Moi role phai di qua registry truoc khi sync:

| Mode | Y nghia | Duoc doc du lieu? | Duoc ghi sheet? |
|---|---|---:|---:|
| `DISABLED` | Bo qua hoan toan | Khong | Khong |
| `INVENTORY_ONLY` | Chi doc metadata/header de kiem ke | Chi header/metadata | Khong |
| `PULL_ONLY` | Doc du lieu ve app sau dry-run pass | Co | Khong |
| `TWO_WAY` | Doc va ghi hai chieu | Co | Co |

Mac dinh hien tai:

| Role | Mode |
|---|---|
| `DMBT_LOG` | `TWO_WAY` |
| `HGT_CHECKS` | `TWO_WAY` |
| `DEVICE_MASTER` | `INVENTORY_ONLY` |
| `LOOKUP_OPTIONS` | `INVENTORY_ONLY` |
| `APP_CONFIG` | `INVENTORY_ONLY` |

Rule bat buoc:
- Sheet moi khong duoc vuot qua `INVENTORY_ONLY` neu chua co contract cot.
- Sheet moi khong duoc len `PULL_ONLY` neu dry-run header fail.
- Sheet moi khong duoc len `TWO_WAY` neu chua co khoa chinh, conflict policy, rollback va test.
- Sheet read-only chi duoc `PULL_ONLY`; khong duoc push nguoc trong moi truong MVP.

## 3) DMBT_LOG column contract

| Column | Bat buoc ton tai | Bat buoc co gia tri | UI field | Ghi nguoc tu UI | Ghi chu |
|---|---|---|---|---|---|
| `record_id` | Co | Co | `recordId` | Co | ID duy nhat cho moi ban ghi |
| `ma_thiet_bi` | Co | Co | `maThietBi` | Co | Khoa tra cuu trung tam |
| `hang_muc` | Co | Khong | `hangMuc` | Co | Mo ta ngu canh |
| `nguoi_bao_cao` | Co | Khong | `nguoiBaoCao` | Co | Nguoi nhap bao cao |
| `tinh_trang_thiet_bi` | Co | Khong | `tinhTrangThietBi` | Co | Mo ta bat thuong |
| `ktv_phu_trach` | Co | Khong | `ktvPhuTrach` | Co | KTV chiu trach nhiem |
| `ngay_phat_hien` | Co | Co | `ngayPhatHien` | Co | Dinh dang `yyyy-MM-dd` |
| `ngay_sua_chua` | Co | Khong | `ngaySuaChua` | Co | Trong => chua sua |
| `ghi_chu` | Co | Khong | `ghiChu` | Co | Bo sung thong tin |
| `updated_at` | Co | Co | `updatedAt` | Tu dong | Unix epoch millis |

## 4) Field suy ra (khong ghi nguoc)
- `repair_status` la field suy ra:
  - `da_sua_chua` neu `ngay_sua_chua` co gia tri.
  - `chua_sua_chua` neu `ngay_sua_chua` rong/null.
- Tuyet doi khong luu cot `repair_status` de tranh lech nghiep vu.

## 4.1) DEVICE_MASTER column contract du kien

| Column | Bat buoc ton tai | Bat buoc co gia tri | Ghi chu |
|---|---:|---:|---|
| `device_code` | Co | Co | Khoa chinh danh muc thiet bi |
| `device_name` | Khong | Khong | Ten/mo ta thiet bi |
| `area` | Khong | Khong | Khu vuc |
| `line` | Khong | Khong | Day chuyen/line |
| `status` | Khong | Khong | Trang thai thiet bi |
| `updated_at` | Khong | Khong | Unix epoch millis neu co |

An toan:
- Thieu `device_code` trong header => fail dry-run.
- Row rong `device_code` => skip row.
- Trung `device_code` => chua merge vao DB chinh cho den khi co rule cu the.
- Giai doan dau chi `PULL_ONLY`, khong push nguoc.

## 4.2) LOOKUP_OPTIONS column contract du kien

| Column | Bat buoc ton tai | Bat buoc co gia tri | Ghi chu |
|---|---:|---:|---|
| `option_group` | Co | Co | Nhom option |
| `option_key` | Co | Co | Khoa option trong nhom |
| `option_label` | Co | Co | Ten hien thi |
| `sort_order` | Khong | Khong | Thu tu hien thi |
| `is_active` | Khong | Khong | Neu false thi bo qua |
| `updated_at` | Khong | Khong | Unix epoch millis neu co |

An toan:
- Loi schema => app dung default hardcoded.
- Row loi nhe => skip row, khong crash.
- Giai doan dau chi `PULL_ONLY`.

## 4.3) APP_CONFIG column contract du kien

| Column | Bat buoc ton tai | Bat buoc co gia tri | Ghi chu |
|---|---:|---:|---|
| `config_key` | Co | Co | Khoa config |
| `config_value` | Co | Co | Gia tri config |
| `value_type` | Co | Co | Kieu du lieu: string/int/bool |
| `updated_at` | Khong | Khong | Unix epoch millis neu co |

Chi chap nhan whitelist key:
- `sync_interval_hours`
- `enable_hgt_reminder`
- `hgt_warning_days`
- `schema_version`

Cam tuyet doi trong `APP_CONFIG`:
- token
- refresh token
- client secret
- password
- API key
- URL/lenh co the lam mat du lieu

## 5) Rule an toan khi sync
1. Validate `spreadsheetId` truoc.
2. Validate `sheetId` theo `role` truoc (toi thieu role `DMBT_LOG`).
3. Validate column contract truoc (thieu cot => fail non-retryable).
4. Validate payload key fields (`record_id`, `ma_thiet_bi`, `ngay_phat_hien`) truoc khi day.
5. Neu loi cau truc sheet: dung sync va bao loi ro, khong retry mu.
6. Mot role loi khong duoc lam hong role khac.
7. Sheet moi phai co inventory report truoc khi bat pull.
8. Pull sheet moi nen qua staging/transaction truoc khi merge vao bang chinh.
9. Local dang `PENDING`/`FAILED` khong bi remote ghi de khi pull.
10. Local da `SYNCED` chi nhan remote neu remote co `updated_at` moi hon hoac bang local.

## 6) Khuyen nghi API (tu tai lieu Google Sheets API qua Context7)
- Khi update theo key on dinh (`record_id`), uu tien cach update theo data filter/range bat bien, khong buoc vao row index.
- Co the dung:
  - `spreadsheets.values.batchUpdate`
  - `spreadsheets.values.batchUpdateByDataFilter`
  - `spreadsheets.batchUpdate`

## 7) Co che push idempotent da ap dung trong code
- Input push la danh sach `DeviceLog`.
- App doc sheet hien tai de lap map `record_id -> row`.
- Tung log duoc xu ly:
  - Neu `record_id` da ton tai: update row hien co.
  - Neu `record_id` chua ton tai: append row moi.
- Muc tieu: tranh tao ban ghi trung khi retry sync.

Nguon tham chieu:
- https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/batchUpdate
- https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets.values/batchUpdateByDataFilter
- https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/batchUpdate

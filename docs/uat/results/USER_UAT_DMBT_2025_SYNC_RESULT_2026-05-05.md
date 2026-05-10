# USER UAT DMBT 2025 SYNC RESULT - 2026-05-05

## Nguon bao cao
- Nguoi test: Director/User tren Android phone va Google Sheet that.
- Sau khi cai ban fix P0 duplicate moi.
- Anh bang chung trong thread:
  - Sheet DMBT 2025 row `523RF03`, ngay phat hien `08/01/2025`.
  - App search `523RF03`, khong con x2 loi, nhung app hien da sua `05/05/2026` trong khi row DMBT 2025 dang trong cot ngay sua chua.
  - Sheet DMBT 2025 row `463KL01`, ngay phat hien `07/01/2025`, ngay sua chua `05/05/2026`, ghi chu `test`.
  - App search `463KL01` van chua the xac nhan row `07/01/2025` da sync ve dung vi anh app dang hien item khac ngay `08/12/2025`.

## Ket luan nhanh
- Duplicate/x2 loi: **PASS tam thoi**. User xac nhan app hien khong con x2 loi.
- DMBT 2025 sync 2 chieu: **FAIL / BLOCK RELEASE**.
- Van de chinh: DMBT 2025 (`gid=989601207`) chua dong bo on dinh 2 chieu giua app va Google Sheet.

## Case UAT moi

### Case D25-01: `523RF03`
- Sheet/app data user cung cap:
  - Hang muc: `Xuat Clinker 3,4`
  - Nguoi bao cao: `Trinh Huu Cuong`
  - Ma thiet bi: `523RF03`
  - Tinh trang: `HGT ro dau ngam qua mat bich chan vong bi`
  - KTV: `Dao Van Thuan`
  - Ngay phat hien: `08/01/2025`
- Ket qua user thay:
  - App khong con x2 loi.
  - App hien record 2025, nhung ngay sua tren app/Sheet khong dong bo dung voi tab DMBT 2025.
- Danh gia Manager:
  - Can xac minh record nay dang lay ngay sua tu repair sheet hay local state.
  - Neu app da sua nhung tab DMBT 2025 khong duoc update, day la loi ghi nguoc theo `sourceSheetId`.

### Case D25-02: `463KL01`
- Sheet/app data user cung cap:
  - Hang muc: `Lo 3,4`
  - Nguoi bao cao: `Trinh Huu Cuong`
  - Ma thiet bi: `463KL01`
  - Tinh trang: `1 tam lot VBD 1 bi gay`
  - KTV: `Dao Van Thuan`
  - Ngay phat hien: `07/01/2025`
  - Ngay sua chua: `05/05/2026`
  - Ghi chu: `test`
- Ket qua user thay:
  - Google Sheet da sua nhung app khong keo ve dung.
- Danh gia Manager:
  - Anh app hien `463KL01` item ngay `08/12/2025` trong bo loc `Chua sua`, khong phai row `07/01/2025`.
  - Can debug bang `record_id/sourceSheetId/business key` de xac nhan row `07/01/2025` co ton tai local khong, bi skip, hay bi filter an.

## Rui ro ky thuat kha nghi
1. `sourceSheetId` cua DMBT 2025 khong duoc gan/giu dung khi pull hoac merge.
2. Push ghi nguoc record 2025 khong target dung sheet `989601207`.
3. Pull DMBT 2025 skip row do business key/date/header/record_id khong match.
4. Repair merge cap nhat local status nhung khong ghi nguoc ve tab DMBT nam goc.
5. UI filter/status co the lam user khong thay row da sua neu dang chon `Chua sua`.

## Trang thai thiet bi
- Sau khi user ket noi lai dien thoai, Manager da keo DB local debug:
  - `docs/uat/evidence/device_tracker_after_dmbt2025.db`
  - `docs/uat/evidence/device_tracker_after_dmbt2025.db-wal`
  - `docs/uat/evidence/device_tracker_after_dmbt2025.db-shm`

## DB evidence sau khi keo tu dien thoai
- `device_logs`: 1889 rows.
- `sync_queue`: 0 rows.
- `hgt_checks`: 51 rows.
- `sourceSheetId` trong `device_logs`:
  - `null`: 1869 rows.
  - `1607125070`: 20 rows.

### Exact target rows

| ma_thiet_bi | ngay_phat_hien | recordId | ngay_sua_chua | ghi_chu | sourceSheetId | syncStatus |
|---|---|---|---|---|---|---|
| 463KL01 | 07/01/2025 | `seed-beta-dmbt-2025-r16` | null | blank | null | SYNCED |
| 523RF03 | 08/01/2025 | `seed-beta-dmbt-2025-r17` | 05/05/2026 | test | null | SYNCED |

## Manager evidence conclusion
- Ca 2 record DMBT 2025 user test deu dang co `sourceSheetId = null`.
- `sourceSheetId = null` la bang chung quan trong: app khong biet chac 2 record nay thuoc tab DMBT 2025 `gid=989601207`.
- Vi vay push nguoc len dung DMBT 2025 va pull merge dung row DMBT 2025 se khong on dinh.
- `463KL01` row `07/01/2025` van dang `ngaySuaChua = null`, `ghiChu = blank` trong local DB, nen Sheet -> app chua merge ve row nay.
- `523RF03` row `08/01/2025` da co `ngaySuaChua = 05/05/2026`, `ghiChu = test` trong local DB, nhung do `sourceSheetId = null`, app khong co du bang chung de ghi nguoc chac chan ve DMBT 2025.

## Ket luan quan ly
- Khong giao tiep tuc duplicate fix nua vi user da xac nhan x2 loi het.
- Mo blocker moi: **DMBT 2025 two-way sync failure**.
- Nguyen nhan kha nghi cao nhat sau DB evidence: DMBT 2025 rows dang la legacy/local rows voi `sourceSheetId = null`, khong duoc backfill/update thanh `989601207`.
- Can fix co kiem soat:
  1. Khi pull DMBT tu sheet 2025, neu match vao local legacy row co `sourceSheetId = null`, phai set `sourceSheetId = 989601207`.
  2. Push record co `sourceSheetId = null` nhung co `recordId`/business key nam 2025 can resolve an toan ve dung sheet nguon, khong day ve default sheet thang.
  3. Them test rieng cho `seed-beta-dmbt-2025-r16` va `seed-beta-dmbt-2025-r17`.

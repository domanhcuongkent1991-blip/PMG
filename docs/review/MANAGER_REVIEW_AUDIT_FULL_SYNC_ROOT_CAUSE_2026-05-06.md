# MANAGER REVIEW - AUDIT-FULL SYNC ROOT CAUSE (2026-05-06)

## 1. Ket luan ngan

`docs/audit/AUDIT-FULL.md` dung mot phan, nhung chua dung trong diem quan trong nhat: bao cao danh dau nhieu hang muc sync la PASS dua tren viec "co code", trong khi UAT that cho thay Google Sheet va app van chua thong du lieu on dinh.

Audit dung o cac diem:

- Retry queue chua co gioi han.
- Pull chua co pagination/incremental sync.
- Monthly gid dang hard-code.
- Can rollback/audit log tot hon.

Audit sai hoac thieu o cac diem:

- Danh dau `Sheet sua roi nhung app khong keo ve` la PASS trong khi UAT cua user dang FAIL.
- Danh dau `Bam sync thi du lieu len Sheet` la PASS trong khi UAT van co case khong len/khong ve.
- Chua chi ra lech contract giua sheet that va code.
- Chua phan tich vi sao HGT on dinh hon DMBT.

## 2. Bang chung tu code va sheet inventory

### 2.1 DMBT sheet that khong co cot ky thuat on dinh

`docs/sheets-inventory.md` ghi header DMBT that gom:

- STT
- Hang muc
- Nguoi bao cao
- Ma thiet bi
- Tinh trang thiet bi
- KTV phu trach
- Ngay phat hien
- Ngay sua chua
- Ghi chu

Khong thay `record_id` va `updated_at`.

Trong khi code contract co `record_id` va `updated_at`:

- `SheetContract.kt`
- `SheetsRemoteDataSource.kt`

He qua:

- App phai tu sinh fallback `recordId` bang business key.
- Neu co 2 dong giong key hoac user sua cot nam trong key, app co the khong match dung dong.
- Push co the append thay vi update.
- Pull co the tao/cap nhat sai record local.

### 2.2 Repair sheet parser khong khop sheet that

`parseRepairSchema()` hien tai doc header bang `gridRows.first()`, tuc la dong 1.

Nhung cac sheet DMBT/Sua chua that co title/merged rows o tren va header nam o dong 2.

Ngoai ra parser repair bat buoc cac cot:

- `record_id`
- `ma_thiet_bi`
- `ngay_sua_chua`
- `ghi_chu`
- `updated_at`

Trong khi sheet sua chua that theo anh gui co dang DMBT day du:

- STT
- Hang muc
- Nguoi bao cao
- Ma thiet bi
- Tinh trang thiet bi
- KTV phu trach
- Ngay phat hien
- Ngay sua chua
- Ghi chu

He qua:

- Repair monthly co the bi skip/optional failure.
- Full sync van co the bao thanh cong vi repair dang optional.
- User thay `Sua chua T5.2026` khong ve app hoac dem so lech.

### 2.3 HGT on dinh hon vi co key don gian hon

HGT parser co `rowByDeviceCode`, match theo `maThietBi/deviceCode`.

DMBT khac HGT:

- Mot ma thiet bi co the co nhieu loi qua nhieu nam/thang.
- Khong the chi match theo ma thiet bi.
- Can `record_id` on dinh hoac business key that chat.

Vi vay khong the copy y nguyen HGT sang DMBT. Co the hoc cach HGT lam key ro rang, nhung DMBT can key phuc hop hon.

## 3. Root cause kha nang cao

Root cause chinh khong phai UI label, khong phai ten tab T5, va khong phai chi do monthly gid hard-code.

Root cause kha nang cao la:

1. Sheet DMBT/Sua chua that khong co `record_id/updated_at` on dinh.
2. Repair parser khong tu detect header row nhu DMBT parser.
3. Repair parser khong support dang sheet sua chua that co cac cot DMBT day du.
4. DMBT fallback key chua co co che ambiguity-safe du manh; duplicate business key co the lam update sai/append.
5. Full sync co the che giau repair failure vi repair optional.

## 4. Phuong an fix triệt de

### Phase A - Fix code de doc dung sheet hien tai

Khong sua cau truc Google Sheet that trong phase nay.

Lam:

1. Sua `parseRepairSchema()` de scan header trong 12 dong dau nhu `parseDmbtSchema()`.
2. Cho repair parser support ca 2 dang:
   - Dang ky thuat co `record_id/updated_at`.
   - Dang sheet that giong DMBT: `Hang muc`, `Ma thiet bi`, `Tinh trang`, `Ngay phat hien`, `Ngay sua chua`, `Ghi chu`.
3. Khi repair row khong co `record_id`, build fallback identity tu:
   - `ma_thiet_bi`
   - `ngay_phat_hien`
   - `hang_muc`
   - `tinh_trang_thiet_bi`
4. Merge repair vao DMBT monthly bang same business key, chi khi match duy nhat.
5. Neu ambiguous, skip va log ro rang, khong update sai.
6. Them test bang fixture header row 2 nhu sheet that.

### Phase B - Fix triệt de identity contract

Can Giam doc phe duyet rieng vi co the them cot ky thuat vao Google Sheet that.

Lam:

1. Them/backfill cot `record_id` va `updated_at` cho 5 sheet DMBT nam + DMBT thang + Sua chua thang.
2. Co dry-run truoc khi ghi that.
3. Chi ghi vao cot ky thuat, khong doi du lieu nghiep vu.
4. Co rollback plan: backup values truoc khi backfill.
5. Sau khi co `record_id`, app push/pull update dung dong on dinh hon nhieu.

## 5. Quyet dinh Manager

Chua nen giao Agent 1 fix retry/pagination ngay. Nhung muc do trong audit la quan trong, nhung khong giai quyet loi "Google Sheet va app chua thong du lieu" hien tai.

Nen giao Agent 1 task P0:

- Fix repair parser theo sheet that.
- Them test tai hien sheet header row 2.
- Them ambiguity guard cho DMBT fallback key.
- Khong tu y sua cau truc Google Sheet that.

Sau khi pass build/test, moi cai APK va UAT lai.

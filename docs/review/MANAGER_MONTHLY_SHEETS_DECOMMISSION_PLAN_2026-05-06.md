# Manager Plan - Decommission monthly DMBT/Repair sheets - 2026-05-06

## Boi canh

Giam doc nhan dinh cac loi sync lap lai dang lien quan nhieu den 2 sheet:

- `DMBT T5.2026` - gid `1383308512`
- `Sua chua T5.2026` - gid `157327514`

De xuat cua Giam doc: xoa 2 sheet nay de on dinh du an.

## Ket luan Manager

Khong nen xoa truc tiep 2 tab khi app van cau hinh gid cu.

Ly do:

1. `DMBT T5.2026` dang la mot DMBT pull target. Neu xoa tab nhung gid `1383308512` van nam trong config, `pullLatestLogs()` se fail vi khong resolve duoc sheet title theo gid.
2. `Sua chua T5.2026` dang la `DMBT_REPAIR_LOG`. Neu xoa tab nhung gid `157327514` van nam trong config, `pullRepairLogs()` se fail vi khong resolve duoc title theo gid.
3. Code hien tai coi repair merge failure la sync failure. Vi vay xoa tab repair ma khong disable config co the lam full sync bao loi nhieu hon.

Phuong an dung la: **decommission co kiem soat**.

## Phuong an fix on dinh

### Phase 1 - Dong bang 2 sheet thang

Muc tieu: khong cho 2 sheet thang tiep tuc tham gia sync de giam bien so.

Lam:

1. Backup/duplicate 2 tab truoc khi can thiep:
   - `DMBT T5.2026` -> `ARCHIVE_DMBT_T5.2026`
   - `Sua chua T5.2026` -> `ARCHIVE_Sua chua T5.2026`
2. Khong xoa ngay.
3. Khong cho user nhap them vao 2 tab nay trong giai do fix.

### Phase 2 - Rut monthly sheets khoi app sync config

Muc tieu: app khong pull/push vao gid `1383308512` va `157327514` nua.

Can lam trong app/config:

1. Loai `1383308512` khoi danh sach DMBT sheet IDs.
2. Khong dat default create target la `1383308512`.
3. Disable repair monthly gid `157327514` hoac cho repair sheet missing la optional.
4. Neu app can them loi moi, route mac dinh ve sheet nam hien tai `DMBT 2026` thay vi DMBT thang.

Ket qua mong muon:

- App sync on dinh voi:
  - DMBT 2022
  - DMBT 2023
  - DMBT 2024
  - DMBT 2025
  - DMBT 2026
  - HGT dinh ky
- Khong con phu thuoc DMBT T5/Sua chua T5.

### Phase 3 - Code guard de xoa tab khong lam full sync fail

Muc tieu: neu tab thang bi xoa/doi, app khong fail ca sync.

Can sua:

1. DMBT pull:
   - Neu mot optional sheetId khong con trong metadata, skip sheet do va ghi warning.
   - Required sheet nam van phai fail neu mat.
2. Repair pull:
   - Neu repair monthly disabled/missing, return empty repair logs thay vi failure.
3. UI:
   - Neu monthly disabled, an `DMBT thang` va `Sua chua thang` trong filter/sidebar.

### Phase 4 - UAT lai sau khi disable monthly

Test can lam:

1. DMBT 2025 Sheet -> app:
   - Sua ngay sua/ghi chu tren Sheet.
   - Bam sync.
   - App phai cap nhat dung.
2. App -> DMBT 2025:
   - Sua 1 record thuoc 2025 tren app.
   - Bam sync.
   - Sheet 2025 cap nhat dung row.
3. Sync lap lai 2-3 lan:
   - Khong duplicate.
4. HGT:
   - Van sync 2 chieu nhu cu.
5. Xac nhan khong co loi lien quan gid `1383308512`/`157327514`.

## Rui ro neu xoa truc tiep

Neu xoa 2 tab ngay lap tuc ma chua sua config/code:

- DMBT pull co the fail voi message dang `Cannot resolve title for configured DMBT sheetId=1383308512`.
- Repair pull co the fail voi message dang `Cannot resolve title for DMBT_REPAIR_LOG sheetId=157327514`.
- Full sync co the bao loi ngay ca khi cac sheet nam/HGT van tot.

## Quyet dinh san pham can chot

Viec decommission 2 sheet thang la thay doi scope so voi PRD ban dau.

Neu Giam doc chot:

- MVP tam thoi khong sync `DMBT thang` va `Sua chua thang`.
- Du lieu chinh se nam o DMBT nam va HGT.
- Sau khi 5 sheet nam + HGT on dinh, co the thiet ke lai monthly sheet sau.

Manager khuyen chon phuong an nay de on dinh release.

## Task tiep theo nen giao Agent 1

Task ID: `A1-DISABLE-MONTHLY-SHEETS-SAFE`

Muc tieu:

- Rut `DMBT T5.2026` va `Sua chua T5.2026` khoi luong sync app mot cach an toan.
- Khong xoa Google Sheet trong code task.
- Neu gid monthly khong ton tai, app khong fail full sync.

Pham vi code du kien:

- `SheetConfig.kt`
- `SheetsRemoteDataSource.kt`
- `DeviceLogRepositoryImpl.kt` neu can xu ly repair optional
- `CategoryFilterMapper.kt` / `SearchScreen.kt` neu can an monthly filter
- Tests lien quan.

Dieu kien hoan thanh:

- Build/test pass.
- DMBT nam va HGT sync khong phu thuoc 2 gid monthly.
- Full sync khong fail khi monthly gid bi disable/missing.

# AGENT2 COUNT MISMATCH ANALYSIS - 2026-05-05 (A2-P1-COUNT-ANALYSIS)

## Scope va nguon su that
- Nguon UAT su that: `docs/uat/results/USER_UAT_RESULT_2026-05-05.md`.
- Mismatch can phan tich:
  - UAT-11: Sua chua thang app 22 vs Sheet 19.
  - UAT-12: HGT Sheet 52 vs app 48.
- Luot nay chi phan tich, **khong sua code**, **khong sua Sheet that**.

## Ket luan tom tat
- **Chua du bang chung de ket luan PASS** cho 2 mismatch.
- Co nhieu dau hieu code cho thay mismatch la hop ly voi du lieu thuc te:
  1. HGT pull co nhieu nhanh `skip row` (blank ma_thiet_bi, chu_ky khong hop le, ngay khong hop le).
  2. HGT merge local theo `id` hoac theo `ma_thiet_bi` co the lam collapse/replace row trong local DB.
  3. "Sua chua thang" tren app khong doc truc tiep tu sheet sua chua de dem, ma la tap `device_logs` da merge, nen de lech so voi so dong trong sheet `Sua chua T*.2026`.

## 1) UAT-12: HGT Sheet 52, app 48

### Bang nguyen nhan kha nghi

| ID | Nguyen nhan kha nghi | Bang chung code | Muc do kha nghi |
|---|---|---|---|
| HGT-C1 | Row bi skip do `ma_thiet_bi` trong | `pullHgtChecks()` bo row neu `maThietBi.isBlank()` | Cao |
| HGT-C2 | Row bi skip do `chu_ky_ngay` khong phai so duong | `rawCycle.toIntOrNull(); if null or <=0 -> skip` | Cao |
| HGT-C3 | Row bi skip do `lan_gan_nhat` trong/parse ra `--` | `latestRaw.isBlank() -> skip`; normalize/format ra blank/`--` -> skip | Cao |
| HGT-C4 | Duplicate ma thiet bi tren sheet bi collapse/replace trong local | refresh merge tim local theo `id` hoac `localByDeviceCode`; `upsertAll(REPLACE)` | Trung binh-Cao |
| HGT-C5 | UI filter dang an bot | Query mac dinh rong (`query = ""`), DAO dung `LIKE %query%` => query rong se khong an theo ma | Thap |

### Phan tich chi tiet
- Luong pull HGT tu sheet:
  - `SheetsRemoteDataSource.pullHgtChecks()` bo row trong cac truong hop:
    - `ma_thiet_bi` blank
    - `chu_ky_ngay` khong parse duoc hoac <= 0
    - `lan_gan_nhat` blank/khong parse hop le
- Sau khi pull, `HgtCheckRepositoryImpl.refreshFromRemote()` merge local:
  - Tim local theo `remote.id`, neu khong co thi tim theo `maThietBi` (`localByDeviceCode`).
  - Neu co duplicate ma trong sheet ma cung map ve 1 `maThietBi`, local co the bi overwrite theo lan xu ly sau cung, dan den local count < sheet count.
- UI:
  - `HgtCheckUiState.query` mac dinh `""`.
  - `observeChecks(query.trim())` -> DAO `LIKE '%' || query || '%'`.
  - Query rong => hien full dataset local, khong co filter ngam giam tu 52 xuong 48.

### Khu vuc code can sua neu xac nhan
1. `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- Them telemetry dem chi tiet so row skip theo tung ly do (blank ma, cycle invalid, date invalid).
- Can co report pull summary de doi chieu voi sheet.

2. `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/HgtCheckRepositoryImpl.kt`
- Rà lai chien luoc merge theo `localByDeviceCode` (co the collapse duplicate logical rows).
- Neu nghiep vu cho phep nhieu dong cung `ma_thiet_bi`, khong duoc fallback merge theo device code don le.

3. `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/HgtCheckDao.kt`
- Xem xet schema/index uniqueness phu hop nghiep vu count (hien tai REPLACE theo PK `id`, khong co guard duplicate by source row).

## 2) UAT-11: Sua chua thang app 22, Sheet 19

### Bang nguyen nhan kha nghi

| ID | Nguyen nhan kha nghi | Bang chung code | Muc do kha nghi |
|---|---|---|---|
| REP-C1 | App dang dem tap `device_logs` da merge, khong phai dem thang tu sheet `Sua chua` | `observeLogs()` doc `device_logs`; repair chi la merge cap nhat field vao DMBT local | Cao |
| REP-C2 | Local duplicate trong `device_logs` lam tang count app | UAT da co duplicate DMBT (UAT-06/UAT-09), va view sua chua co the dua tren cung tap nay | Cao |
| REP-C3 | Repair merge tao record du | Code ghi ro "khong tao ban ghi moi"; chi `upsert` khi resolve duoc local record | Thap |
| REP-C4 | Repair row khong resolve duoc bi skip, gay lech huong nguoc (app < sheet) | Co skip notFound/ambiguous/pending, nhung case hien tai la app > sheet | Thap |

### Phan tich chi tiet
- `DeviceLogRepositoryImpl.refreshFromRemote()`:
  1. Pull DMBT -> upsert vao `device_logs`.
  2. Pull repair sheet -> `mergeRepairLogsFromRemote()` chi cap nhat `ngaySuaChua/ghiChu/updatedAt/syncStatus` cho record local da ton tai.
- Nghia la "Sua chua thang" tren app neu dua tren `device_logs` thi no la view sau merge tren tap DMBT local, khong dong nghia voi so dong trong tab `Sua chua T*.2026`.
- Trong boi canh user da bao duplicate DMBT, viec app > sheet (22 > 19) phu hop voi kha nang tap local DMBT dang co row trung/row them tu cac sheet khac, khong phai do repair parser tao moi.

### Khu vuc code can sua neu xac nhan
1. `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- Lam ro va tach khái niệm:
  - Count "repair monthly sheet rows" (neu nghiep vu can count theo tab Sua chua).
  - Count "DMBT records da co ngay_sua_chua" (view nghiep vu da sua).
- Hien tai 2 khái niệm de bi tron.

2. `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/DeviceLogDao.kt`
- Neu can count theo sheet nguon/thang, can bo sung query theo `sourceSheetId` + rule theo role thay vi count tong `device_logs`.

3. `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- Can co pull summary per sheet/role (fetched/applied/skipped) de doi chieu count voi user UAT.

## 3) Bang chung line-level (tham chieu nhanh)
- HGT skip rules:
  - `SheetsRemoteDataSource.kt` quanh `pullHgtChecks()` (dong ~331+), cac nhanh skip o ~349, ~353, ~359, ~363.
- HGT merge/collapse risk:
  - `HgtCheckRepositoryImpl.kt` ~157-178 (`localByDeviceCode`, `upsertAll`).
- UI filter HGT khong an ngam:
  - `HgtCheckUiState.kt` dong 6 (`query = ""`).
  - `HgtCheckViewModel.kt` dong 43, 232.
  - `HgtCheckDao.kt` dong 35 (`LIKE %query%`).
- Repair merge khong tao record moi:
  - `DeviceLogRepositoryImpl.kt` ~225+ (`mergeRepairLogsFromRemote`), chi `upsert(updatedEntity)` khi resolve local.
  - `RepairRecordIdentityResolver.kt` `resolveRepairRecordId()` tra null neu ambiguous/not found.

## 4) Du lieu can user/Manager cung cap de chot nguyen nhan

### Cho HGT mismatch (52 vs 48)
1. Snapshot tab `HGT dinh ky` (52 dong) gom cac cot:
- `record_id`, `ma_thiet_bi`, `chu_ky_ngay`, `lan_gan_nhat`, `lan_tiep_theo`, `updated_at`.
2. Danh sach 48 dong app dang hien (export/picture/DB dump local) tai cung thoi diem.
3. Log sync pull HGT co thong ke skip reason (neu chua co thi can bo sung instrumentation trong lan fix).

### Cho repair mismatch (22 vs 19)
1. Danh sach 19 dong trong tab `Sua chua T*.2026` user dang dem.
2. Danh sach 22 item app dang dem o man sua chua (bao gom `record_id`, `ma_thiet_bi`, `sourceSheetId`, `ngay_sua_chua`).
3. Rule dem nghiep vu mong muon:
- Dem theo so dong tab `Sua chua`?
- Hay dem so record DMBT da co `ngay_sua_chua`?

## 5) De xuat thu tu fix (chua code)
1. P0: bo sung pull/merge count telemetry cho HGT + DMBT/repair (fetched/applied/skipped-by-reason).
2. P0: khoa duplicate strategy (khong collapse sai theo `ma_thiet_bi` neu nghiep vu cho phep nhieu dong).
3. P1: chot definition count cho "Sua chua thang" va tach query/count theo definition.
4. P1: bo sung man/debug export count doi chieu nhanh app vs sheet theo role.

## Trang thai
- **Khong du bang chung de ket luan PASS cho UAT-11/UAT-12.**
- Can du lieu bo sung nhu muc 4 de xac nhan nguyen nhan goc truoc khi code fix.

# AUDIT FULL 2 - DeviceTracker Android + Google Sheets

Ngay audit: 2026-05-07  
Pham vi: `android-mvp`, `scripts`, `package.json`, tai lieu van hanh lien quan

## 1) Ket luan tong quan

- Tinh trang du an: **CAN SUA**
- Co nen tiep tuc phat trien: **Co**, nhung uu tien sua loi P0 truoc.
- Co nen deploy production ngay: **Chua nen**.

---

## 2) Loi nghiem trong (P0)

### P0-1: Validate sync role chua bat buoc du tat ca luong nghiep vu

- Van de:
  - `requiredRolesForSync` hien tai chi bat buoc `DMBT_LOG`.
  - Mot so luong nhu HGT/repair co nhieu diem `return success` neu chua cau hinh sheetId, de tao cam giac "sync thanh cong" nhung thuc te chua day du.
- File/khu vuc:
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetContract.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- Vi sao nguy hiem:
  - Co the gay sai lech nghiep vu 2 chieu app <-> sheet.
  - User thay thong bao thanh cong nhung data van thieu.
- Cach sua:
  1. Mo rong `requiredRolesForSync` toi thieu gom `DMBT_LOG`, `DMBT_REPAIR_LOG`, `HGT_CHECKS` (theo pha van hanh thuc te).
  2. Neu role bat buoc thieu `sheetId`, tra `NonRetryableSyncException` ro rang, khong `success` im lang.
  3. Them test bao ve: khi thieu role bat buoc => sync fail va co thong diep user-friendly.

### P0-2: Secret OAuth dang di qua BuildConfig o debug

- Van de:
  - Token/client secret duoc dua vao `BuildConfig` debug (`SHEETS_ACCESS_TOKEN`, `SHEETS_OAUTH_CLIENT_SECRET`, `SHEETS_REFRESH_TOKEN`).
- File/khu vuc:
  - `android-mvp/app/build.gradle.kts`
- Vi sao nguy hiem:
  - APK debug neu bi chia se sai cach co the bi trich xuat secret.
  - Rui ro lan truyen token that.
- Cach sua:
  1. Khong nhung secret that vao artifact build.
  2. Chuyen sang runtime secure storage (Android Keystore/EncryptedSharedPreferences) hoac co che cap token qua backend/proxy.
  3. Giu `local.properties` chi de local dev, khong dua gia tri that vao file mau.
  4. Tiep tuc bat buoc chay `node scripts/prevent-secrets.js` truoc commit.

---

## 3) Loi vua (P1/P2)

### P1-1: Luong save tren man hinh them moi chua bat loi runtime day du

- Van de:
  - `EditViewModel.save()` goi `saveDeviceLogUseCase(...)` trong `launch` nhung khong `runCatching`.
- Anh huong:
  - Loi DB/runtime co the khong duoc map sang thong bao de hieu cho user.
- File:
  - `android-mvp/app/src/main/java/com/example/devicetracker/ui/edit/EditViewModel.kt`
- Cach sua:
  1. Boc khoi save bang `runCatching`.
  2. Neu that bai, cap nhat `errorMessage` than thien va ghi log ky thuat.
  3. Them test cho truong hop save fail.

### P1-2: Hard-code sheet thang o nhieu diem

- Van de:
  - `MONTHLY_DMBT_SHEET_IDS` dang hard-code va lap lai.
- Anh huong:
  - De lech cau hinh khi doi sheet theo thang/gid.
- File:
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- Cach sua:
  1. Tap trung nguon cau hinh sheet vao mot noi duy nhat.
  2. Bo lap lai constant tai UI/domain layer.
  3. Them test mapping khi doi gid.

### P2-1: Script van hanh phu thuoc path tuyet doi

- Van de:
  - `package.json` co script dung duong dan `D:\...` va `F:\...`.
- Anh huong:
  - Kho chay tren may khac/CI, de vo pipeline.
- File:
  - `package.json`
- Cach sua:
  1. Chuyen sang path tuong doi + bien moi truong (`PROJECT_ROOT`, `GSD_ROOT`...).
  2. Kiem tra lai script tren may sach va tren CI.

### P2-2: Query tim kiem co the cham voi data lon

- Van de:
  - Tim kiem `LIKE '%...%'` + chua thay index phu tro day du cho pattern truy van.
- Anh huong:
  - Nguy co lag khi du lieu 10k+ ban ghi.
- File:
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/DeviceLogDao.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/local/entity/DeviceLogEntity.kt`
- Cach sua:
  1. Danh gia index cho cac cot tra cuu chinh.
  2. Can nhac FTS hoac chien luoc search toi uu hon neu can scale.
  3. Benchmark truoc/sau toi uu.

---

## 4) Loi nho / no ky thuat

### P3-1: Canh bao deprecated Compose/Hilt

- Van de:
  - Con su dung API da deprecated (`hiltViewModel` import cu, `Icons.Default.ArrowBack` o mot so man hinh).
- Anh huong:
  - Khong vo ngay, nhung tang no ky thuat.
- Cach sua:
  1. Cap nhat import/API theo package moi.
  2. Don sach canh bao compiler.

### P3-2: `allowBackup=true` can review theo muc bao mat du lieu

- Van de:
  - Manifest dang bat backup.
- Anh huong:
  - Neu data nhay cam, can policy backup chat hon.
- File:
  - `android-mvp/app/src/main/AndroidManifest.xml`
- Cach sua:
  1. Danh gia lai chinh sach backup theo nghiep vu.
  2. Neu can, tat backup hoac gioi han bang backup rules.

---

## 5) Rui ro tiem an

### R1: File logic qua lon, de phat sinh regression

- Mo ta:
  - `SheetsRemoteDataSource.kt` va mot so file UI/repository rat dai.
- Nguy co:
  - Sua nhanh de vo logic sync.
- Phong tranh:
  1. Tach module theo trach nhiem.
  2. Bo sung test integration quanh push/pull/merge.

### R2: Coverage test UI/integration chua sau

- Mo ta:
  - Unit test da co nhieu, nhung instrumentation/E2E cho luong nguoi dung quan trong con it.
- Nguy co:
  - Loi UX/sync chi lo khi UAT muon.
- Phong tranh:
  1. Them test cho: save offline, full sync, conflict, thong bao loi user.
  2. Dung workbook test rieng cho UAT truoc workbook that.

---

## 6) De xuat hanh dong tiep theo (uu tien)

1. Khoa P0-1: bat buoc role sync va fail-fast khi thieu cau hinh.
2. Khoa P0-2: bo nhung secret vao BuildConfig; chuyen secret sang runtime secure.
3. Bo sung test integration cho sync 2 chieu DMBT + repair + HGT.
4. Refactor giam hard-code sheetId thang.
5. Chuan hoa script van hanh, bo path tuyet doi.

---

## 7) Ket qua verify da thuc thi trong dot audit nay

- Preflight:
  - `node --version`
  - `npm --version`
  - `npx --version`
  - `git --version`
- Governance/ops:
  - `npm run workflow:test` -> PASS
  - `npm run ops:test` -> PASS
- Android:
  - `scripts/build-android-safe.ps1` -> PASS (`testDebugUnitTest` + `assembleDebug`)
- Secret guard:
  - `node scripts/prevent-secrets.js` -> PASS


# Manager Review - Agent 1 multi-sheet DMBT sync fix - 2026-05-05

## Ket luan

Trang thai: **Nen tiep tuc UAT tren dien thoai that, chua duyet release cuoi**.

Agent 1 da sua dung huong chinh: khong hard-code rieng DMBT 2025, ma xu ly tong quat cho multi-sheet bang `sourceSheetId`/gid. Huong nay phu hop PRD vi cac tab nam va tab thang deu phai dong bo hai chieu, va tab thang co the doi ten tu T4 sang T5 ma gid khong doi.

## Noi dung da review

File code chinh:

- `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/DeviceLogDao.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`

File test lien quan:

- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/SheetConfigMappingRulesTest.kt`

## Diem dat

1. Pull tu Google Sheet ve app co co che resolve row cu bang business key gom:
   - ma thiet bi
   - ngay phat hien da normalize
   - hang muc
   - tinh trang thiet bi

2. Fallback business key chi dung cho local row `sourceSheetId=null`, phu hop voi du lieu legacy dang loi.

3. Khi local row da co `sourceSheetId` khac remote sheet, code khong merge cheo. Day la rule quan trong de tranh sheet A ghi nham sang sheet B.

4. Push len Sheet da uu tien route theo `sourceSheetId`; neu khong co thi co the doc gid tu `readonly-dmbt-<gid>-...`.

5. Neu khong resolve duoc sheet dich trong che do multi-sheet, payload bi reject thay vi am tham day vao default sheet. Day la hanh vi an toan hon cho du lieu that.

6. Test da bao phu gid DMBT 2022/2023/2024/2025/2026 va DMBT thang hien tai.

## Manager da sua truc tiep

Phat hien:

- `resolveDmbtSheetRecordId()` strip prefix `readonly-dmbt-<gid>-` cho moi gid, ke ca khi `targetSheetId` khac gid trong `recordId`.
- Cach nay co rui ro lam app update sai row neu mot record bi route nham sheet.
- Test hien tai cung dang ky vong khi target sheet khac thi phai giu nguyen recordId.

Da sua:

- Chi strip prefix khi `recordId` co dung prefix cua chinh `targetSheetId`.
- Neu gid trong recordId khac target sheet, giu nguyen recordId de tranh match nham.

## Ket qua verify

Da chay:

```powershell
./scripts/build-android-safe.ps1
```

Ket qua:

- `testDebugUnitTest`: **PASS**
- `assembleDebug`: **PASS**

Ghi chu:

- Canh bao Android metrics van xuat hien do sandbox `C:\Users\CodexSandboxOnline\.android`, khong chan build.
- Canh bao Kotlin/Hilt deprecated va native strip warning khong chan build.
- Loi lock build lap lai truoc do duoc script build an toan xu ly bang Gradle home/tmp rieng va cleanup retry.

## Rui ro con lai

1. Du lieu legacy co `sourceSheetId=null` nhung business key khong sach co the bi skip an toan. Skip an toan tot hon ghi nham sheet, nhung user co the thay mot so dong chua cap nhat cho den khi backfill duoc.

2. Can cai APK moi len dien thoai va UAT lai cac case that:
   - 463KL01 ngay 07/01/2025 tren DMBT 2025
   - 523RF03 ngay 08/01/2025 tren DMBT 2025
   - DMBT T5.2026 gid 1383308512 hai chieu
   - Sua chua T5.2026 gid 157327514 hai chieu

3. Sidebar label T4.2026 la loi UX rieng, nen giao Agent 2 sua label trung tinh sau khi core sync da co APK moi.

## Quyet dinh tiep theo

1. Cai APK moi len dien thoai that.
2. User UAT lai nho, khong test hang loat:
   - Sheet -> app cho 2 dong DMBT 2025 da neu.
   - App -> Sheet cho 1 dong DMBT 2025 it rui ro.
   - Sheet/app hai chieu cho DMBT T5.2026 va Sua chua T5.2026.
3. Neu UAT pass, giao Agent 2 sua sidebar label T4.2026 thanh label trung tinh.

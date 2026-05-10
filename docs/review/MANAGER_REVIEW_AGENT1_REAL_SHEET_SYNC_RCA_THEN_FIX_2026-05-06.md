# MANAGER REVIEW - AGENT1 REAL SHEET SYNC RCA THEN FIX (2026-05-06)

## 1. Ket luan ngan

Verdict: **DUYET DE CHUYEN SANG UAT THAT CO KIEM SOAT**.

Chua duyet release production. Ly do: code da co bang chung test/build pass, nhung chua UAT lai tren dien thoai that + Google Sheet that sau fix.

## 2. Bao cao duoc review

- Agent report: `docs/review/AGENT1_REAL_SHEET_SYNC_RCA_THEN_FIX_REPORT_2026-05-06.md`
- Task ID: `A1-P0-REAL-SHEET-SYNC-RCA-THEN-FIX`
- Agent 1 tuyen bo:
  - Da viet fixture test lam loi truoc.
  - Da xac nhan root cause repair parser khong khop sheet that.
  - Da them fallback business key an toan cho repair.
  - Da chan ambiguous fallback key khi push DMBT.
  - Targeted tests pass.
  - `scripts/build-android-safe.ps1` pass.

## 3. File da kiem tra

Production:
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/model/SheetValueMappers.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/RepairRecordIdentityResolver.kt`

Tests:
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRepairTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/RepairRecordIdentityResolverTest.kt`

## 4. Danh gia ky thuat

### 4.1 Diem dung

- Repair parser da ho tro 2 dang schema:
  - contract ky thuat co `record_id`/`updated_at`;
  - sheet that dang DMBT day du, header nam o cac dong dau, khong bat buoc `record_id`/`updated_at`.
- Parser scan header trong 12 dong dau, phu hop voi sheet that co title o dong 1 va header o dong 2.
- `DmbtRepairUpdate` da mang them `ngayPhatHien`, `hangMuc`, `tinhTrangThietBi`, du de match bang business key khi sheet khong co `record_id`.
- Repair merge chi merge khi tim duoc dung 1 monthly DMBT candidate; ambiguous thi skip, khong update nham.
- `RepairRecordIdentityResolver.stripDmbtNamespace()` da giu nguyen malformed namespace, fix dung finding truoc do.
- DMBT pull voi `updatedAt <= 0` da co duong compare content de van cap nhat khi sheet that khong co `updated_at`.
- DMBT push co fail-safe khi fallback key ambiguous, giam nguy co append/update sai row.

### 4.2 Diem can theo doi

- `refreshFromRemote()` hien cho phep repair monthly fail ma yearly DMBT van success. Day la dung huong partition an toan, nhung UAT phai xac nhan UI/UX khong lam user hieu nham la tat ca sheet da sync day du.
- Repair monthly chi merge vao monthly DMBT candidates. DMBT 2022-2026 se duoc cap nhat truc tiep qua DMBT pull, khong di qua repair monthly.
- Neu du lieu legacy thieu qua nhieu cot trong business key, app se skip an toan thay vi merge bua. Neu UAT con thieu dong, can lay mau row that de bo sung fixture.

## 5. Verify da chay lai boi Manager

### 5.1 Targeted unit tests

Command:

```powershell
.\gradlew.bat -PcodexBuildId=manager_review_agent1_p0_sync_20260506 :app:testDebugUnitTest --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRepairTest" --tests "com.example.devicetracker.data.remote.SheetsRemoteDataSourceRecordIdTest" --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest" --no-daemon --max-workers=1
```

Result: **PASS** (exit code 0).

Note: first sandbox run failed because Gradle wrapper could not create lock file under `C:\Users\CodexSandboxOnline\.gradle`. Reran outside sandbox with approval.

### 5.2 Required Android safe build

Command:

```powershell
.\scripts\build-android-safe.ps1
```

Result: **PASS** (exit code 0).

Evidence:
- `:app:testDebugUnitTest` BUILD SUCCESSFUL.
- `:app:assembleDebug` BUILD SUCCESSFUL.
- Pipeline printed: `Build pipeline completed successfully.`

### 5.3 Diff hygiene

Command:

```powershell
git diff --check
```

Result: **PASS** (exit code 0).

Note: chi con canh bao CRLF/LF cua Git, khong co whitespace error.

## 6. Quyet dinh Manager

Khong giao them code fix ngay luc nay. Fix cua Agent 1 da du dieu kien de cai APK moi va UAT that co kiem soat.

Chua duoc ket luan "het loi production" vi chua co bang chung:
- DMBT 2025 sheet -> app;
- app -> DMBT 2025 sheet;
- DMBT T5.2026 sheet -> app;
- app -> DMBT T5.2026 sheet;
- Sua chua T5.2026 sheet -> app;
- sync lai 2 lan khong duplicate.

## 7. Buoc tiep theo de an toan

1. Cai APK debug moi len dien thoai user.
2. User test lai dung cac case that tung fail:
   - `523RF03` ngay phat hien `08/01/2025`;
   - `463KL01` ngay phat hien `07/01/2025`;
   - `754SC03` ngay phat hien `10/01/2026`;
   - 1 dong DMBT T5.2026;
   - 1 dong Sua chua T5.2026.
3. Neu UAT fail, lay anh + ma thiet bi + ngay phat hien + sheet/gid + ket qua mong doi, roi moi giao Agent fix tiep bang fixture that moi.


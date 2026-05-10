# AGENT1 MONTHLY SEPARATION PHASE 1 REPORT - 2026-05-06

## 1) File da sua
1. `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
2. `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
3. `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
4. `android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/SheetConfigMappingRulesTest.kt`
5. `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
6. `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`

## 2) Dieu gi da tach duoc cho DMBT monthly
1. Da bo sung phan loai binding theo stream trong config:
- `yearlyDmbtSheetBindings`
- `monthlyDmbtSheetBindings`
- helper `splitDmbtSheetBindings(...)`

2. Da tach orchestration pull trong `pullLatestLogs()`:
- Pull yearly truoc, yearly la bat buoc.
- Pull monthly sau, monthly la optional-by-warning.

3. Da them fault isolation theo sheet:
- Monthly missing title/parse loi -> `Log.w` + skip.
- Yearly missing title/parse loi -> throw, fail nhu hien tai.

## 3) Dieu gi da tach duoc cho Sua chua monthly
1. `pullRepairLogs()` da co tham so `optional`.
2. Trong Phase 1, repository goi `pullRepairLogs(optional = true)`.
3. Khi repair monthly missing gid/title/parse loi:
- remote tra `Result.success(emptyList())` neu optional
- repository khong lam fail full sync neu yearly da thanh cong.

## 4) Dieu gi chua tach o Phase 1
1. Chua tach merge scope repair theo monthly candidates vs yearly candidates.
- Hien tai repair merge van resolve tren toan bo `localRecordIds`.

2. Chua tach UI category/yearly-monthly theo `sourceSheetId`.

3. Chua doi push flow/yearly-monthly partition (giu nguyen theo yeu cau phase 1).

4. Chua doi DB schema, chua tao bang moi, chua them migration.

## 5) Test da them/chay
### Test them
1. `SheetConfigMappingRulesTest`
- `splitDmbtSheetBindings_yearlyDoesNotContainMonthlyGid`
- `splitDmbtSheetBindings_monthlyContainsOnlyMonthlyGid`

2. `SheetsRemoteDataSourceRecordIdTest`
- `yearlyMissingTitle_shouldBeFatal`
- `monthlyMissingTitle_shouldNotBeFatal`
- `repairMonthlyFailure_optional_returnsEmptySuccess`
- `repairMonthlyFailure_required_stillFails`

3. `DeviceLogRepositorySyncRulesTest`
- `shouldFailRefreshAfterRepairFailure_whenRepairIsOptional_returnsFalse`
- `shouldFailRefreshAfterRepairFailure_whenRepairIsMandatory_returnsTrue`

### Test/build da chay
Da chay `./scripts/build-android-safe.ps1`:
- `:app:testDebugUnitTest` PASS
- `:app:assembleDebug` PASS
- `BUILD SUCCESSFUL`

## 6) Rui ro con lai
1. Repair merge scope chua partition theo stream, van co rui ro merge cheo yearly/monthly neu identity mo ho.
2. Optional repair trong phase 1 uu tien availability, co the lam bo sot cap nhat repair trong mot so loi schema du lieu monthly.
3. UI van classify monthly theo heuristic cu, user co the thay nhom hien thi chua nhat quan voi stream moi.

## 7) De xuat Phase 2
1. Partition merge scope trong `DeviceLogRepositoryImpl`:
- repair monthly chi resolve/merge vao monthly DMBT candidates.
- cam merge cheo stream yearly <-> monthly.

2. Partition routing cho push:
- yearly push va monthly push tach batch rieng.
- record khong ro provenance -> skip-safe + thong bao ro.

3. UI/filter separation:
- category monthly/yearly dua theo `sourceSheetId` thay vi prefix/text heuristic.

4. Telemetry:
- thong ke skip reason theo stream (yearly/monthly/repair) de UAT truy vet nhanh.

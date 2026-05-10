# AGENT1 MONTHLY SEPARATION PHASE 2 REPORT (2026-05-06)

## 1. File đã sửa

Production code:
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/data/local/dao/DeviceLogDao.kt`

Tests:
- `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/SheetsRemoteDataSourceRecordIdTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositorySyncRulesTest.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositoryRepairPullMergeTest.kt`

## 2. Partition merge đã làm gì

### 2.1 Tách yearly/monthly ở tầng config
- Thêm helper trong `SheetConfig.kt`:
  - `MONTHLY_DMBT_SHEET_IDS = {1383308512}`
  - `isMonthlyDmbtSheetId(...)`
  - `splitDmbtSheetBindings(...)`
  - `yearlyDmbtSheetBindings` và `monthlyDmbtSheetBindings`
- Mục đích: phân hoạch rõ dữ liệu DMBT năm và DMBT tháng theo `gid/sourceSheetId`, không phụ thuộc tên tab.

### 2.2 Chặn merge chéo yearly <-> monthly
- Trong `DeviceLogRepositoryImpl.kt`, cập nhật `resolveExistingLocalForRemote(...)`:
  - Exact `recordId` match chỉ được nhận khi `sourceSheetId` local/remote tương thích (`isLocalSourceCompatibleWithRemote(...)`).
  - Nếu exact match nhưng source khác partition (yearly vs monthly) thì bỏ qua exact đó, không merge nhầm.
- Business-key matching tiếp tục đi qua logic lọc source sheet hiện có, nên không còn merge chéo chỉ vì key giống nhau.

Kết quả đạt mục tiêu:
- Remote monthly không merge vào local yearly chỉ vì business key giống.
- Remote yearly không merge vào local monthly chỉ vì business key giống.

## 3. Partition repair monthly đã làm gì

- Trong `mergeRepairLogsFromRemote()` (`DeviceLogRepositoryImpl.kt`):
  - Không còn dùng toàn bộ `getAllRecordIds()` để resolve repair.
  - Chỉ lấy candidate record IDs theo monthly DMBT gid (`1383308512`) qua DAO mới: `getRecordIdsBySourceSheetId(...)`.
  - Nếu không có monthly candidates: skip-safe toàn bộ repair monthly + log warning; không tạo DMBT record mới.
  - Nếu ambiguous hoặc không tìm thấy match: skip-safe theo từng log + log lý do.

Kết quả đạt mục tiêu:
- `Sửa chữa T5.2026` chỉ merge vào monthly scope.
- Không merge vào yearly dù base id có thể trùng.
- Không tạo row DMBT mới từ repair sheet.

## 4. Partition push đã làm gì

- Trong `SheetsRemoteDataSource.kt`, `groupDmbtLogsByTargetSheet(...)` được siết routing:
  1. Nếu `sourceSheetId` có giá trị:
     - Chỉ route khi `sourceSheetId` nằm trong tập DMBT bindings đã cấu hình.
     - Nếu không thuộc tập cấu hình: skip fail-safe + log cảnh báo.
  2. Nếu không có `sourceSheetId`:
     - Cho phép route theo record ID namespaced `readonly-dmbt-<gid>-...` nếu `gid` có cấu hình.
     - Trong multi-sheet mode, không cho rơi về default sheet với record provenance không rõ.
     - Chỉ giữ fallback an toàn cho pattern auto-create `dmbt-auto-*`.

Kết quả đạt mục tiêu:
- Record yearly chỉ push vào yearly gid tương ứng.
- Record monthly (`1383308512`) chỉ push vào monthly gid.
- Record unknown provenance không bị đẩy nhầm default trong multi-sheet mode.

## 5. Test đã thêm/chạy

### 5.1 Test bổ sung
- `SheetsRemoteDataSourceRecordIdTest.kt`:
  - `pushRouting_yearlySourceSheetId_routesOnlyToYearlySheet`
  - `pushRouting_monthlySourceSheetId_routesOnlyToMonthlySheet`
  - `pushRouting_unknownProvenanceInMultiSheetMode_isSkippedFailSafe`
  - `pushRouting_sourceSheetIdNotInConfiguredSet_isSkippedFailSafe`
- `DeviceLogRepositorySyncRulesTest.kt`:
  - `monthlyRemote_withSameBusinessKeyAsYearlyLocal_doesNotMergeCrossStream`
  - `yearlyRemote_withSameBusinessKeyAsMonthlyLocal_doesNotMergeCrossStream`
  - kiểm thử source-compatibility cho exact match
- `DeviceLogRepositoryRepairPullMergeTest.kt`:
  - monthly repair resolve chỉ với monthly candidates
  - monthly candidates missing thì không merge sang yearly

### 5.2 Build/Test pipeline
Đã chạy:
- `./scripts/build-android-safe.ps1`

Kết quả:
- `:app:testDebugUnitTest` PASS
- `:app:assembleDebug` PASS
- BUILD SUCCESSFUL

Ghi chú môi trường:
- Có warning metrics path `.android` và vài warning deprecation; không làm fail pipeline.

## 6. Rủi ro còn lại

1. Hard-code monthly DMBT gid hiện tại:
- Phase 2 đang khóa monthly DMBT theo `1383308512` (đúng yêu cầu hiện tại T5.2026).
- Khi sang tháng mới, nếu monthly chuyển sang gid khác mà chưa cập nhật config, luồng monthly sẽ skip-safe (không ghi nhầm nhưng có thể không sync monthly).

2. Unknown provenance bị skip:
- Đây là chủ đích an toàn để tránh ghi nhầm sheet.
- Hệ quả: một số record legacy thiếu `sourceSheetId` có thể cần backfill provenance ở Phase 3 để sync tiếp.

3. Repair monthly hiện là optional và scoped:
- Tránh làm fail yearly full sync, nhưng có thể làm người dùng thấy repair monthly “không ăn” khi monthly nguồn thiếu/cấu hình lỗi.
- Cần telemetry rõ hơn để vận hành phát hiện sớm.

## 7. Đề xuất Phase 3

1. Dynamic monthly binding registry theo role + gid active
- Không phụ thuộc tên tab.
- Có cơ chế cập nhật gid tháng mới an toàn, có validation trước khi bật.

2. Provenance backfill an toàn cho legacy rows
- Backfill `sourceSheetId` cho rows null theo luật strict business-key + uniqueness.
- Chỉ backfill khi không ambiguous, có log/audit trail.

3. Observability và operational guardrails
- Thêm counters/log structured cho các nhánh skip-safe (unknown provenance, source mismatch, ambiguous repair resolve).
- Thêm cảnh báo trong màn sync status để user/ops biết record nào bị skip vì an toàn.

4. UAT thật theo checklist nhỏ
- UAT yearly + monthly + repair monthly riêng từng ca.
- Mỗi ca có marker, before/after, rollback ngay.
- Xác nhận không có cross-sheet write và không duplicate khi retry sync.

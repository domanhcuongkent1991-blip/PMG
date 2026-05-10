# AGENT1 Ambiguous Pending Cleanup - 2026-05-09

## Scope
Task ID: ambiguous pending cleanup sau UAT thật 2026-05-09.
Mục tiêu: xử lý an toàn record pending ambiguous còn lại, không đụng sync core rộng, không xóa nhầm dữ liệu user.

## Root Cause (xác nhận từ code + log)
- `syncPending()` đã có cơ chế isolate ambiguous đúng hướng: record hợp lệ vẫn sync, record ambiguous giữ lại queue để tránh ghi sai/duplicate.
- Tuy nhiên UI chưa có thao tác để user xử lý dứt điểm queue item ambiguous đã biết.
- Pending item local cũng chưa luôn hiển thị `lastError` từ queue, nên user khó nhận biết record nào đang ambiguous.

## Files Updated
1. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\java\com\example\devicetracker\data\local\dao\SyncQueueDao.kt`
- Thêm delete có điều kiện chặt:
  - `deleteAmbiguousPushErrorByRecordId(recordId)`
  - Chỉ xóa khi:
    - `operation = 'UPSERT_LOG'`
    - `recordId` khớp
    - `lastError` bắt đầu bằng `Ambiguous DMBT fallback key for push:`

2. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\java\com\example\devicetracker\data\repository\DeviceLogRepositoryImpl.kt`
- Thêm API nội bộ:
  - `ignoreAmbiguousPendingRecord(recordId)`
- Logic:
  - Chỉ xử lý khi DAO delete strict-condition thành công.
  - Nếu local record còn `PENDING` thì chuyển `syncStatus` -> `FAILED` để không tiếp tục gây nhiễu pending/queue.
  - Ghi log marker rõ ràng cho skip/success.
- Cải thiện pending detail:
  - Khi build pending list cho local DMBT, ưu tiên hiển thị queue `lastError` theo `recordId` nếu có.
- Bóc helper nhận diện message ambiguous:
  - `isAmbiguousDmbtFallbackMessage(...)`

3. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\java\com\example\devicetracker\ui\sync\SyncStatusViewModel.kt`
- Inject `DeviceLogRepositoryImpl` cho action cleanup phạm vi hẹp.
- Thêm action:
  - `ignoreAmbiguousPending(itemId)`
  - Parse `log:<recordId>`
  - Gọi ignore strict-condition
  - Refresh overview sau khi thành công

4. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\java\com\example\devicetracker\ui\sync\SyncStatusUiState.kt`
- Thêm state `ignoringItemId` để disable nút theo item đang xử lý.

5. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\java\com\example\devicetracker\ui\sync\SyncStatusScreen.kt`
- Với pending item DMBT có detail ambiguous, hiển thị nút:
  - `Bỏ qua lỗi ambiguous này`
- Có loading nhỏ trên đúng item đang xử lý.

6. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\main\res\values\strings.xml`
- Thêm text cho nút ignore ambiguous.

7. `F:\codex_android_gsheet_full_pack\android-mvp\app\src\test\java\com\example\devicetracker\data\repository\DeviceLogRepositorySyncRulesTest.kt`
- Thêm test:
  - `isAmbiguousDmbtFallbackMessage_onlyMatchesExpectedAmbiguousSignature`
  - Bảo vệ chỉ match đúng lỗi ambiguous, không match lỗi chung chung.

## Safety Behavior
- Không tự động xóa hàng loạt queue.
- Không áp dụng cho lỗi chung chung.
- Không động vào routing/mapping DMBT 2022-2026.
- Record hợp lệ vẫn đi theo luồng sync cũ.

## Verification Commands & Results
Preflight:
- `node --version` -> `v24.12.0`
- `npm --version` -> `11.6.2`
- `npx --version` -> `11.6.2`
- `git --version` -> `git version 2.53.0.windows.1`

Build & tests:
- `./scripts/build-android-safe.ps1` -> **PASS**
  - `testDebugUnitTest` PASS
  - `assembleDebug` PASS

## Rollback Plan
Nếu cần rollback nhanh:
1. Revert các file đã sửa trong commit này.
2. Build lại bằng `./scripts/build-android-safe.ps1`.
3. Hành vi trở về trạng thái trước: ambiguous queue sẽ vẫn được giữ nhưng không có action bỏ qua trong UI.

## Remaining Risks
- Đây là cleanup an toàn cho pending ambiguous đã biết; không phải redesign toàn bộ cơ chế disambiguation business key.
- Nếu user vô tình bấm ignore cho case ambiguous thực sự cần xử lý dữ liệu, record sẽ chuyển sang `FAILED` (không mất data local, nhưng cần user edit/sync lại nếu muốn đẩy).
- Cần UAT thật 1 vòng trên thiết bị để xác nhận UX thông báo đủ rõ cho non-tech trước khi bấm ignore.

## Manager Follow-up (2026-05-09) - Added Targeted Cleanup Tests

### Tests Added
Trong `F:\codex_android_gsheet_full_pack\android-mvp\app\src\test\java\com\example\devicetracker\data\repository\DeviceLogRepositorySyncRulesTest.kt` đã bổ sung 3 test trực tiếp cho hành động cleanup chính:
1. `ignoreAmbiguousPendingRecord_deletesMatchingQueue_and_marksPendingLocalFailed`
- Case queue item `UPSERT_LOG` + đúng `recordId` + `lastError` ambiguous.
- Kỳ vọng: queue bị xóa, local `PENDING -> FAILED`.

2. `ignoreAmbiguousPendingRecord_doesNotDeleteQueue_forNonAmbiguousError`
- Case lỗi timeout/lỗi chung chung.
- Kỳ vọng: queue giữ nguyên, local không đổi `FAILED`.

3. `ignoreAmbiguousPendingRecord_onlyDeletesMatchingRecordIdQueue`
- Có 2 queue item khác `recordId`.
- Kỳ vọng: chỉ item target đúng điều kiện bị xử lý, item còn lại không bị xóa nhầm.

### Production Code Change Status
- Có sửa production code **tối thiểu, có chủ đích để testability**, không đổi hành vi nghiệp vụ:
  - Tách logic cleanup thành hàm internal testable:
    - `ignoreAmbiguousPendingRecord(recordId, deviceLogDao, syncQueueDao)`
  - Method trong repository giữ nguyên hành vi, chỉ delegate vào helper trên.
  - Không đổi schema, không đổi contract Google Sheet, không đổi mapping DMBT.

### Commands Executed
1. Targeted unit test:
- `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.example.devicetracker.data.repository.DeviceLogRepositorySyncRulesTest"`
- Kết quả: **PASS**

2. Optional build:
- `.\gradlew.bat --no-daemon :app:assembleDebug`
- Kết quả: **PASS**

### Notes on Environment
- Có cảnh báo lặp về Kotlin daemon (`AccessDeniedException` trong thư mục daemon marker), nhưng Gradle fallback compile vẫn chạy và task kết thúc PASS.

### Remaining Risks
- Cơ chế cleanup vẫn là fail-safe theo signature lỗi ambiguous hiện tại; nếu message format upstream thay đổi thì điều kiện match cần cập nhật tương ứng.
- Đây là cleanup điểm cho pending ambiguous, chưa thay thế cho chiến lược disambiguation business key tổng thể.

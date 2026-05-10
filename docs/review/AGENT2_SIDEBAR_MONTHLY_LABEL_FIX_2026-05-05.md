# AGENT2 Sidebar Monthly Label Fix (2026-05-05)

## Task
- Task ID: `A2-SIDEBAR-MONTHLY-LABEL-FIX`
- Scope: chỉ sửa UX label sidebar/filter monthly, không sửa sync core.

## Files đã sửa
- `android-mvp/app/src/main/res/values/strings.xml`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/ui/search/CategoryFilterMapperTest.kt` (đồng bộ tên hằng số ID monthly)

## Trước / Sau label hiển thị
- Trước:
  - `DMBT T4.2026`
  - `Sửa chữa T4.2026`
- Sau:
  - `DMBT tháng`
  - `Sửa chữa tháng`

## Xác nhận không sửa sync core
- Không sửa các file sync core bị cấm:
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
  - `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
- Không thay đổi logic `gid/sourceSheetId`, không đổi schema DB, không đổi API Google Sheet.

## Kiểm tra nhanh theo yêu cầu
- Đã search trong UI label scope (`SearchScreen` + `strings.xml`) và không còn text hiển thị `T4.2026`.
- Đã đổi label monthly sang dạng trung tính để dùng cho các tháng sau.

## Build/Test đã chạy
- Đã chạy:
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.devicetracker.ui.search.CategoryFilterMapperTest"`
- Kết quả: chưa pass do lỗi môi trường Gradle output cleanup (file lock), không phải lỗi logic sửa label.
  - Lỗi chính: `Execution failed for task ':app:parseDebugLocalResources'`
  - Chi tiết: `Failed to clean up output files ... AccessDeniedException ... R-def.txt`

## Kết luận
- Sidebar/filter đã bỏ hard-code tháng `T4.2026` ở phần hiển thị.
- Label monthly hiện tại là trung tính, không phụ thuộc tháng.
- Không can thiệp sync core.

# AGENT1 SIDEBAR MONTHLY LABEL OVERRIDE REPORT (2026-05-06)

## 1. Mục tiêu và phạm vi

Đã triển khai cơ chế cho user tự đổi tên hiển thị 2 mục Sidebar:
- DMBT tháng
- Sửa chữa tháng

Giải pháp chỉ dùng local preferences (SharedPreferences store nhỏ), không đụng sync core, không đụng DB schema, không đọc title tab Google Sheet.

## 2. File đã sửa

- `android-mvp/app/src/main/java/com/example/devicetracker/data/local/preferences/SidebarMonthlyLabelStore.kt` (mới)
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
- `android-mvp/app/src/main/res/values/strings.xml`
- `android-mvp/app/src/test/java/com/example/devicetracker/data/local/preferences/SidebarMonthlyLabelStoreTest.kt` (mới)

## 3. Cách hoạt động

### 3.1 Lưu override local
- Thêm `SidebarMonthlyLabelStore` để lưu 2 key override:
  - `monthly_dmbt_label`
  - `monthly_repair_label`
- Logic normalize:
  - trim/collapse whitespace
  - bỏ giá trị rỗng
  - giới hạn độ dài 64 ký tự

### 3.2 Hiển thị label
- `SearchScreen` tải override khi vào màn.
- Nếu chưa có override -> dùng mặc định từ `strings.xml`:
  - `DMBT tháng`
  - `Sửa chữa tháng`
- Sidebar dùng label hiệu lực này khi render category monthly.

### 3.3 UI đổi tên thủ công trong app
- Trong Sidebar thêm mục: `Đổi tên mục tháng`.
- Mở một modal sheet nhỏ gồm:
  - ô nhập tên hiển thị DMBT tháng
  - ô nhập tên hiển thị Sửa chữa tháng
  - nút `Lưu`
  - nút `Reset mặc định`
  - nút `Đóng`

`Reset mặc định` sẽ xóa override đã lưu và quay về nhãn mặc định.

## 4. Xác nhận không dùng auto Google Sheet title

- Không thêm bất kỳ logic fetch tên tab Google Sheet nào cho sidebar.
- Không sửa `SheetsRemoteDataSource`/`DeviceLogRepositoryImpl`/`DeviceLogDao`.
- Sync core vẫn theo `gid/sourceSheetId` như trước.

## 5. Test đã thêm

Thêm unit test:
- `SidebarMonthlyLabelStoreTest.kt`

Case chính:
1. Blank/null => normalize thành `null`.
2. Chuỗi nhiều khoảng trắng => normalize đúng.
3. Chuỗi dài => cắt còn 64 ký tự.

## 6. Build/Test đã chạy

Đã chạy:
- `./scripts/build-android-safe.ps1`

Kết quả:
- `:app:testDebugUnitTest` PASS
- `:app:assembleDebug` PASS
- BUILD SUCCESSFUL

## 7. Rủi ro còn lại

- Lượt này chưa thêm validation nâng cao cho nội dung label (ví dụ blacklist ký tự), mới dừng ở normalize + length cap.
- Vì scope tối giản, chưa tạo màn Settings riêng; dùng modal nhỏ ngay tại Sidebar để giảm thay đổi kiến trúc.

## 8. Kết luận

Task `A1-SIDEBAR-MONTHLY-LABEL-OVERRIDE` đã hoàn thành:
- User có thể đổi nhãn trong app.
- Có reset về mặc định.
- Không có auto đổi tên theo Google Sheet.
- Build/test pass.

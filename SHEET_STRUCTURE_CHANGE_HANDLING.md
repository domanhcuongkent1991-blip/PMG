# XỬ LÝ THAY ĐỔI CẤU TRÚC GOOGLE SHEET

## Mục tiêu
Giữ app ổn định khi người dùng:
- thêm tab mới
- xóa tab
- đổi tên tab
- đổi vị trí tab
- đổi cột hoặc cấu trúc sheet

## 1. Nguyên tắc nền
- App không được bám vào tên tab hoặc thứ tự tab.
- App phải bám vào:
  - `spreadsheetId`
  - vai trò tab
  - `sheetId`
- Tab mới lạ phải được coi là "unmapped" cho tới khi có mapping rõ.

## 2. Khi thêm tab mới
- Mặc định bỏ qua tab mới.
- Không tự dùng tab mới cho nghiệp vụ chính.
- Có thể hiển thị thông báo: phát hiện tab mới chưa được gán vai trò.

## 3. Khi xóa tab đang dùng
- Dừng sync cho module tương ứng.
- Không tự chọn tab khác thay thế.
- Báo lỗi rõ ràng và yêu cầu map lại hoặc khôi phục tab.

## 4. Khi đổi tên tab
- Nếu app bám `sheetId` thì không ảnh hưởng lớn.
- Chỉ cập nhật tên hiển thị nếu cần.

## 5. Khi đổi vị trí tab
- Không ảnh hưởng nếu app không dựa vào thứ tự tab.

## 6. Khi schema thay đổi
Ví dụ:
- đổi tên cột
- thiếu cột bắt buộc
- thêm cột lạ
- di chuyển vùng dữ liệu

### Cách xử lý
1. Quét metadata / header trước khi sync.
2. Kiểm tra cột bắt buộc có còn không.
3. Nếu thiếu cột bắt buộc:
   - dừng sync
   - giữ local an toàn
   - báo lỗi rõ
4. Nếu chỉ thêm cột lạ nhưng không ảnh hưởng:
   - có thể tiếp tục chạy nếu mapping cũ vẫn hợp lệ

## 7. Cấu hình đề xuất
Lưu local:
- `spreadsheet_id`
- `schema_version`
- mapping vai trò tab → `sheetId`
- lần cuối quét metadata

## 8. Gợi ý nâng cao
- Dùng developer metadata để gắn vai trò cho tab.
- Dùng named ranges cho vùng dữ liệu chính nếu cần.

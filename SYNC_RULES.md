# SYNC RULES

## Mục tiêu
Cho phép app:
- nhập dữ liệu được khi offline
- lưu an toàn trên điện thoại
- tự đồng bộ lên Google Sheet khi online
- đồng bộ tất cả sheet đã được cấu hình an toàn, có role và có contract cột rõ ràng

Theo PRD hiện tại, "đồng bộ tất cả sheet bên trong Google Sheet" được hiểu theo nghĩa an toàn:
- App đồng bộ tất cả sheet nằm trong cấu hình/whitelist của app.
- App không tự quét toàn bộ workbook để kéo mọi tab bất kỳ.
- Sheet mới chỉ được bật sync sau khi có `sheetId`, role, contract cột, dry-run pass và rule conflict rõ.

Lý do: trong một Google Sheet có thể có tab nháp, tab tạm, tab tổng hợp, tab sai cột hoặc tab không dành cho app. Nếu app tự kéo/ghi tất cả tab, rủi ro mất hoặc trộn dữ liệu rất cao.

## 1. Nguyên tắc tổng thể
- App hoạt động theo mô hình local-first.
- UI đọc từ local DB.
- Khi người dùng tạo/sửa dữ liệu, app ghi local trước.
- Sau đó app đưa thay đổi vào hàng chờ sync.

## 2. Khi offline
1. Người dùng nhập/sửa dữ liệu.
2. App lưu vào local DB.
3. App tạo entry trong `SYNC_QUEUE` với `sync_status = pending`.
4. UI vẫn hiển thị dữ liệu ngay.

## 3. Khi online
1. WorkManager hoặc quy trình sync chủ động chạy.
2. App lấy các item `pending` trong `SYNC_QUEUE`.
3. App kiểm tra cấu trúc Google Sheet còn hợp lệ không.
4. Nếu hợp lệ:
   - đẩy dữ liệu lên Google Sheet
   - cập nhật local thành `synced`
5. Nếu không hợp lệ:
   - dừng sync
   - giữ local an toàn
   - báo lỗi rõ

## 4. Conflict resolution
### Rule đã chốt cho MVP
- Nếu local có thay đổi chưa sync (`PENDING` hoặc `FAILED`): không cho dữ liệu remote ghi đè local.
- Nếu local đã sync (`SYNCED`): khi refresh, remote được áp dụng nếu `updated_at` remote mới hơn hoặc bằng local.
- Khi app vừa push một snapshot cũ nhưng user đã sửa tiếp cùng bản ghi trong lúc sync chạy: không mark local là `SYNCED` nếu `updated_at` local đã khác snapshot vừa push.
- Nếu cần xử lý xung đột thủ công, làm sau MVP bằng màn hình riêng. MVP ưu tiên không mất dữ liệu local.

Ví dụ dễ hiểu:
- Nhân viên sửa bản ghi trên điện thoại lúc mất mạng. Bản ghi đang chờ sync. Nếu Google Sheet cũng có thay đổi, app vẫn giữ bản trên điện thoại trước, vì đó là thay đổi chưa được đẩy lên.
- Nếu bản ghi trên điện thoại đã sync xong, sau đó Google Sheet có bản mới hơn, app có thể kéo bản mới hơn về.

## 5. Sync status
### Giá trị dùng trong code
- `PENDING`: có thay đổi local đang chờ đẩy lên Google Sheet.
- `SYNCED`: local và Google Sheet đã được coi là đồng bộ tại thời điểm sync gần nhất.
- `FAILED`: sync lỗi, dữ liệu local vẫn được giữ lại để retry hoặc báo lỗi.

Không dùng trạng thái sync để suy ra trạng thái sửa chữa thiết bị. Trạng thái sửa chữa phải suy ra từ `ngay_sua_chua`.

## 6. Retry
- Nếu sync lỗi vì mạng: giữ lại `pending` hoặc `failed`, retry lại.
- Nếu lỗi API tạm thời: dùng backoff tăng dần.
- Nếu lỗi cấu trúc sheet: không retry mù; cần người dùng hoặc app remap lại.
- Nếu lỗi quyền truy cập, token, spreadsheetId hoặc sheetId: báo lỗi rõ để sửa cấu hình, không giả vờ sync thành công.

## 7. Điều không nên làm
- Không ghi trực tiếp vào Google Sheet rồi mới hy vọng local theo sau.
- Không để app mất dữ liệu chỉ vì mất mạng giữa chừng.
- Không update dựa vào số dòng trong Google Sheet.
- Không tự động bật sync cho tab mới nếu chưa có contract.
- Không push ngược vào sheet chưa được cấu hình hai chiều. Theo PRD cập nhật ngày 2026-05-03, các sheet DMBT 2022..2026 không còn được mặc định là read-only; nếu bật hai chiều thì phải có contract, khóa chính, test và rollback rõ.

## 8. Dữ liệu tối thiểu cần cho sync
- `record_id`
- `ma_thiet_bi`
- `updated_at`
- `operation_type`
- `sync_status`

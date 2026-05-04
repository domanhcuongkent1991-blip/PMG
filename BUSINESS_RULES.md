# BUSINESS RULES

## 1. Quy tắc nền
- `ma_thiet_bi` là trung tâm tìm kiếm.
- Mỗi `ma_thiet_bi` có thể có nhiều bản ghi sự cố.
- Mỗi bản ghi sự cố phải có `record_id`.

## 2. Quy tắc sửa chữa
- Nếu `ngay_sua_chua` có giá trị thì bản ghi là `da_sua_chua`.
- Nếu `ngay_sua_chua` trống thì bản ghi là `chua_sua_chua`.
- Không cho người dùng nhập tay trạng thái sửa chữa.
- Trạng thái sửa chữa chỉ là giá trị suy ra để hiển thị và lọc.

## 3. Quy tắc tìm kiếm
- Tìm kiếm chính theo `ma_thiet_bi`.
- Cho phép lọc:
  - tất cả
  - đã sửa chữa
  - chưa sửa chữa
- Kết quả nên ưu tiên hiển thị bản ghi mới nhất trước.

## 4. Quy tắc dữ liệu
- Không dùng `STT` làm ID chính.
- `record_id` phải duy nhất.
- `ma_thiet_bi` là trường bắt buộc.
- `ngay_phat_hien` là trường bắt buộc.
- `ngay_sua_chua` là trường tùy chọn.
- `updated_at` phải luôn được cập nhật khi sửa bản ghi.

## 5. Quy tắc lịch sử
- Một mã thiết bị có thể phát sinh lỗi nhiều lần ở nhiều thời điểm.
- App phải phân biệt:
  - trạng thái của từng bản ghi
  - lịch sử của cùng một mã thiết bị
- Không gộp tất cả lịch sử thành một trạng thái duy nhất nếu chưa có rule rõ.

## 6. Quy tắc an toàn dữ liệu
- Khi offline, lưu vào local DB trước.
- Chỉ sync lên Google Sheet khi có mạng và cấu trúc sheet hợp lệ.
- Nếu phát hiện cấu trúc sheet thay đổi không khớp, ưu tiên dừng sync và báo lỗi rõ ràng.

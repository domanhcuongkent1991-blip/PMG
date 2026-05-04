# CHECKLIST CÔNG VIỆC CHO CODEX (MVP)

> Chưa code ngay nếu chưa xong phần phân tích, data model, sync rules và UI mapping.

## PHẦN A — PHÂN TÍCH
- [ ] 1. Phân tích lại bài toán của app Android dùng Google Sheets.
- [ ] 2. Tóm tắt mục tiêu chính của module hiện tại.
- [ ] 3. Xác định rõ dữ liệu trung tâm là `ma_thiet_bi`.
- [ ] 4. Xác định rõ quy tắc sửa chữa:
  - có `ngay_sua_chua` = đã sửa chữa
  - không có `ngay_sua_chua` = chưa sửa chữa
- [ ] 5. Chỉ ra rủi ro nếu dùng trực tiếp sheet hiện tại làm database duy nhất.

## PHẦN B — THIẾT KẾ DỮ LIỆU
- [ ] 6. Đề xuất data model chuẩn cho module thiết bị bất thường.
- [ ] 7. Tách rõ:
  - bảng danh mục thiết bị
  - bảng nhật ký sự cố / bất thường
- [ ] 8. Thiết kế cột cho bảng danh mục thiết bị (`DEVICE_MASTER`).
- [ ] 9. Thiết kế cột cho bảng nhật ký bất thường (`DMBT_LOG`).
- [ ] 10. Đảm bảo mọi bản ghi có:
  - `record_id`
  - `ma_thiet_bi`
  - `ngay_phat_hien`
  - `ngay_sua_chua`
  - `updated_at`
- [ ] 11. Không dùng `STT` làm khóa chính.
- [ ] 12. Đề xuất chuẩn hóa `ma_thiet_bi`.

## PHẦN C — LOGIC NGHIỆP VỤ
- [ ] 13. Viết business rules rõ ràng.
- [ ] 14. Thiết kế logic tìm kiếm theo `ma_thiet_bi`.
- [ ] 15. Thiết kế logic lọc:
  - tất cả
  - đã sửa chữa
  - chưa sửa chữa
- [ ] 16. Phân biệt rõ:
  - trạng thái của từng bản ghi
  - lịch sử của cùng một `ma_thiet_bi`
- [ ] 17. Nêu cách xử lý khi một thiết bị có nhiều bản ghi.

## PHẦN D — UI/UX
- [ ] 18. Thiết kế user flow cho thao tác tra cứu theo `ma_thiet_bi`.
- [ ] 19. Thiết kế user flow cho thao tác cập nhật `ngay_sua_chua`.
- [ ] 20. Liệt kê các màn hình cần có cho MVP.
- [ ] 21. Viết wireframe mức chữ cho từng màn hình.
- [ ] 22. Thiết kế ô tìm kiếm trung tâm cho `ma_thiet_bi`.
- [ ] 23. Thiết kế bộ lọc:
  - tất cả
  - đã sửa chữa
  - chưa sửa chữa
- [ ] 24. Thiết kế badge/trạng thái để nhìn ra đã sửa hay chưa sửa.
- [ ] 25. Thiết kế trạng thái màn hình:
  - loading
  - empty
  - error
  - success

## PHẦN E — MAPPING UI VỚI GOOGLE SHEET
- [ ] 26. Lập bảng mapping giữa từng field UI và cột trong Google Sheet.
- [ ] 27. Chỉ rõ field nào là dữ liệu gốc, field nào là dữ liệu suy ra.
- [ ] 28. Chỉ rõ field nào được phép chỉnh sửa từ UI.
- [ ] 29. Nếu UI lệch logic dữ liệu gốc, ưu tiên sửa UI trước khi code.

## PHẦN F — LOCAL DB VÀ SYNC
- [ ] 30. Đề xuất local DB trên điện thoại.
- [ ] 31. Đề xuất bảng queue đồng bộ.
- [ ] 32. Chỉ rõ khi offline thì lưu vào đâu.
- [ ] 33. Chỉ rõ khi online thì sync lên Google Sheet như thế nào.
- [ ] 34. Đề xuất quy tắc conflict resolution.
- [ ] 35. Chỉ rõ dùng `updated_at` và `sync_status` như thế nào.

## PHẦN G — GOOGLE SHEET STRUCTURE
- [ ] 36. Đề xuất mapping vai trò tab → `sheetId`.
- [ ] 37. Nêu cách xử lý khi thêm tab mới.
- [ ] 38. Nêu cách xử lý khi xóa tab đang dùng.
- [ ] 39. Nêu cách xử lý khi đổi tên tab.
- [ ] 40. Nêu cách phát hiện schema thay đổi.

## PHẦN H — VALIDATION
- [ ] 41. Xác định trường bắt buộc và trường tùy chọn.
- [ ] 42. Thiết kế validation cho `ma_thiet_bi`.
- [ ] 43. Thiết kế validation cho `ngay_phat_hien`.
- [ ] 44. Thiết kế validation cho `ngay_sua_chua`.
- [ ] 45. Ngăn lưu bản ghi nếu thiếu `ma_thiet_bi`.

## PHẦN I — TASK BREAKDOWN
- [ ] 46. Chia việc thành task nhỏ, độc lập, dễ code.
- [ ] 47. Sắp xếp task theo thứ tự ưu tiên cho MVP.
- [ ] 48. Với mỗi task, nêu:
  - mục tiêu
  - file liên quan
  - đầu vào
  - đầu ra
  - tiêu chí hoàn thành

## PHẦN J — TEST
- [ ] 49. Lập test plan cho module hiện tại.
- [ ] 50. Liệt kê test case cho:
  - tìm theo `ma_thiet_bi`
  - lọc đã sửa chữa
  - lọc chưa sửa chữa
  - thêm bản ghi mới
  - cập nhật `ngay_sua_chua`
  - offline save
  - sync lại khi online
  - thay đổi cấu trúc sheet

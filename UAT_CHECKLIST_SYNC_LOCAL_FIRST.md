# UAT CHECKLIST - SYNC LOCAL-FIRST (ANDROID + GOOGLE SHEETS)

## Mục tiêu
- Xác nhận app hoạt động đúng theo local-first:
  - lưu local trước
  - mất mạng vẫn dùng được
  - có mạng thì sync an toàn, không mất dữ liệu mới

## Chuẩn bị
- Điện thoại đã cài bản APK mới nhất.
- Có dữ liệu test trong app.
- Có thể bật/tắt mạng trên điện thoại.
- Có Google Sheet test riêng, không dùng dữ liệu thật để thử lỗi.
- Đã cấu hình đúng các sheetId bắt buộc trong `local.properties`.
- Không ghi token/secret vào ảnh chụp màn hình, log gửi đi hoặc worklog.
- (Tùy chọn) Mở log để quan sát:
  - `adb logcat -s SheetsSyncWorker DeviceLogRepository SheetsRemoteDataSource`

## Kịch bản 1 - Lưu khi offline
1. Tắt Wi-Fi/4G.
2. Sửa 1 bản ghi hoặc thêm 1 bản ghi mới.
3. Bấm lưu.
4. Đóng app và mở lại app.
- Kỳ vọng:
  - dữ liệu vừa lưu vẫn còn trong app
  - không bị mất sau khi mở lại

## Kịch bản 2 - Sync lại khi online
1. Sau kịch bản 1, bật mạng lại.
2. Chờ worker chạy sync (hoặc mở app để kích hoạt luồng).
3. Kiểm tra bản ghi vừa sửa.
- Kỳ vọng:
  - bản ghi vẫn giữ đúng nội dung mới nhất
  - không bị quay về dữ liệu cũ
  - log có thông tin `syncPending start` và `syncPending success`

## Kịch bản 3 - Chống ghi đè khi đang sync
1. Bật mạng.
2. Sửa một bản ghi, bấm lưu.
3. Ngay sau đó sửa lại cùng bản ghi lần nữa (nội dung khác), bấm lưu.
4. Chờ sync.
- Kỳ vọng:
  - dữ liệu cuối cùng là lần sửa mới nhất
  - không bị ghi đè bởi snapshot cũ

## Kịch bản 4 - Lọc theo năm + trạng thái sau sync
1. Mở sidebar, chọn `DMBT 2026`.
2. Đổi bộ lọc trạng thái `Tất cả / Đã sửa / Chưa sửa`.
- Kỳ vọng:
  - danh sách lọc đúng theo năm và trạng thái
  - không phát sinh dữ liệu lạ sau sync

## Kịch bản 5 - Trường hợp lỗi mạng/API
1. Tắt mạng hoặc dùng mạng chập chờn.
2. Thực hiện lưu bản ghi.
3. Bật mạng lại.
- Kỳ vọng:
  - dữ liệu local không mất
  - app retry sync ở lần sau
  - nếu lỗi cấu hình sheet/schema, app fail rõ ràng, không sync mù

## Kịch bản 6 - Đồng bộ tất cả sheet đã cấu hình an toàn
1. Kiểm tra danh sách sheet trong cấu hình app:
   - `DMBT_LOG`
   - `HGT_CHECKS`
   - tat ca sheet DMBT 2022..2026 va sheet DMBT theo thang hien tai
   - sheet sua chua theo thang hien tai
2. Chạy full sync.
3. Kiểm tra dữ liệu app sau sync.
- Kỳ vọng:
  - Tat ca sheet DMBT trong scope sync 2 chieu dung.
  - Ban ghi sua tu sheet nao phai ghi nguoc dung sheet do.
  - Sheet `HGT_CHECKS` sync 2 chiều đúng nếu đã cấu hình.
  - Sheet sua chua theo thang sync 2 chieu dung.
  - Tab không nằm trong cấu hình/whitelist không bị app tự động kéo hoặc ghi.

## Kịch bản 7 - Sheet sai cấu trúc
1. Trên Google Sheet test, tạo một bản copy tab DMBT rồi xóa một cột bắt buộc, ví dụ `ma_thiet_bi` hoặc `ngay_phat_hien`.
2. Trỏ cấu hình test vào tab sai cấu trúc đó.
3. Chạy sync.
- Kỳ vọng:
  - App báo lỗi rõ là thiếu cột/schema sai.
  - Không retry mù liên tục.
  - Dữ liệu local không bị xóa.
  - Không ghi dữ liệu mới vào tab sai cấu trúc.

## Kịch bản 8 - HGT reminder
1. Bật quyền notification cho app.
2. Vào màn HGT, bật cảnh báo lịch kiểm tra.
3. Tạo hoặc sửa một HGT có ngày kiểm tra gần.
4. Chờ đến thời điểm nhắc hoặc test bằng cấu hình thời gian ngắn.
- Kỳ vọng:
  - Có thông báo nhắc lịch.
  - Mở app từ thông báo không crash.
  - Sau khi reboot máy, lịch nhắc vẫn được đăng ký lại.

## Tiêu chí PASS tối thiểu
- PASS toàn bộ kịch bản 1, 2, 3.
- Không có tình trạng mất dữ liệu local.
- Không có ghi đè ngược dữ liệu mới bằng dữ liệu cũ sau sync.
- Sheet chưa được cấu hình/whitelist không bị app tự động sync.
- Lỗi schema/token/quyền truy cập phải hiện rõ, không được báo sync thành công giả.
- HGT reminder pass ít nhất trên một máy Android thật; Android 12/13/14 cần test bổ sung trước production.

# Architecture Decisions

## 1. Remote access strategy
Bản MVP chọn **Android app gọi Google APIs bằng quyền của chính người dùng**.
Chưa thêm Apps Script/backend ở giai đoạn đầu để giảm độ phức tạp.

## 2. Offline strategy
App chọn **offline-first**:
- UI đọc từ local database
- Ghi dữ liệu vào local trước
- Đưa thay đổi vào sync queue
- WorkManager xử lý sync nền khi có mạng

## 3. Google Sheet strategy
Bản đầu chọn:
- 1 spreadsheet chính cho mỗi người dùng
- vài tab có vai trò rõ ràng
- app bám vào `spreadsheetId` + `sheetId`
- không bám vào thứ tự tab

## 4. Android stack
- Kotlin
- Jetpack Compose
- MVVM
- Repository pattern
- Hilt
- Room
- WorkManager

## 5. Business rules đã khóa
- `ma_thiet_bi` là trung tâm tra cứu
- `record_id` là ID riêng của từng bản ghi
- có `ngay_sua_chua` => đã sửa chữa
- trống `ngay_sua_chua` => chưa sửa chữa
- không dùng `STT` làm ID chính

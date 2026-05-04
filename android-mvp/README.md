# Android MVP Skeleton

## Kiến trúc
- Compose UI
- MVVM
- Repository pattern
- Room local database
- WorkManager sync queue
- Hilt DI

## Luồng chính
1. User nhập dữ liệu trên app.
2. App lưu vào Room.
3. Bản ghi được đánh dấu `PENDING`.
4. WorkManager sync khi có mạng.
5. Remote Google Sheets integration sẽ được hoàn thiện sau.

## Tập trung trước
- Search theo `ma_thiet_bi`
- Lọc `đã sửa / chưa sửa`
- Thêm mới bản ghi
- Xem chi tiết bản ghi

## Chưa hoàn thiện
- OAuth thật
- Google Sheets API thật
- Mapping cột thật
- Pull dữ liệu 2 chiều đầy đủ
- Conflict resolution nâng cao

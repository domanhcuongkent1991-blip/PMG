# Codex Android + Google Sheets Full Pack

Gói này gồm 2 phần chính:

1. **Bộ tài liệu nền cho Codex**
   - AGENTS.md
   - MASTER_PROMPT.txt
   - checklist, business rules, data model, UI/UX rules, sync rules, test plan

2. **Khung project Android MVP** trong thư mục `android-mvp/`
   - Kotlin
   - Jetpack Compose
   - MVVM
   - Repository pattern
   - Room (local database)
   - WorkManager (sync queue)
   - Hilt (dependency injection)
   - Google Sheets remote integration ở mức khung/stub để bạn và Codex hoàn thiện tiếp

## Mục tiêu kiến trúc
- App hoạt động theo hướng **local-first**: nhập dữ liệu vào local DB trước.
- Khi có mạng, WorkManager sẽ đẩy dữ liệu chờ sync lên Google Sheet.
- Tìm kiếm xoay quanh `ma_thiet_bi`.
- Trạng thái sửa chữa được suy ra từ `ngay_sua_chua`.

## Điều đã có sẵn
- Cấu trúc project Android ban đầu
- Màn hình tra cứu MVP bằng Compose
- Room entities / DAO / Database
- Repository interface và implementation cơ bản
- Sync worker khung
- Hệ rule + docs để Codex bám đúng

## Điều bạn cần làm tiếp
- Điền Google OAuth / Google Sheets API integration thật
- Chốt mapping sheet thật trong `SheetConfig`
- Hoàn thiện screen thêm/sửa dữ liệu
- Hoàn thiện sync 2 chiều với Google Sheet
- Viết test và polish UI

## Ghi chú
- Gói này ưu tiên **khung đúng hướng** và **ít đập đi làm lại**.
- Phần tích hợp Google Sheets đang ở mức skeleton an toàn, chưa chứa secret hay OAuth client thật.
- Nếu Android Studio yêu cầu cập nhật dependency hoặc wrapper, hãy để Android Studio sync và nâng theo môi trường máy của bạn.

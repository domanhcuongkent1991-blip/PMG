# TASK BREAKDOWN CHO MVP

## Giai đoạn 1 — Nền móng dữ liệu
### Task 1
- Mục tiêu: chốt data model cho `DEVICE_MASTER`, `DMBT_LOG`, `SYNC_QUEUE`
- Đầu ra: tài liệu data model
- Tiêu chí hoàn thành: các field bắt buộc đã rõ, không còn mơ hồ về ID

### Task 2
- Mục tiêu: chốt business rules
- Đầu ra: tài liệu rule đã sửa/chưa sửa, rule tìm kiếm
- Tiêu chí hoàn thành: Codex không còn tạo trạng thái sửa chữa nhập tay

### Task 3
- Mục tiêu: chốt mapping Google Sheet
- Đầu ra: mapping vai trò tab → `sheetId`, cột bắt buộc
- Tiêu chí hoàn thành: không còn phụ thuộc vào tên tab

## Giai đoạn 2 — Local DB và sync
### Task 4
- Mục tiêu: tạo local DB schema
- Đầu ra: entity/model local
- Tiêu chí hoàn thành: lưu được dữ liệu khi offline

### Task 5
- Mục tiêu: tạo queue sync
- Đầu ra: bảng queue + repository xử lý enqueue
- Tiêu chí hoàn thành: mỗi thay đổi local đều có thể đẩy vào queue

### Task 6
- Mục tiêu: tạo worker sync
- Đầu ra: WorkManager job
- Tiêu chí hoàn thành: khi online có thể đẩy item pending lên Google Sheet

## Giai đoạn 3 — Màn hình MVP
### Task 7
- Mục tiêu: tạo màn hình tra cứu chính
- Đầu ra: ô tìm kiếm `ma_thiet_bi`
- Tiêu chí hoàn thành: nhập mã và chuyển tới danh sách kết quả

### Task 8
- Mục tiêu: tạo màn hình danh sách kết quả
- Đầu ra: danh sách log theo `ma_thiet_bi`, bộ lọc đã/chưa sửa
- Tiêu chí hoàn thành: lọc hoạt động đúng theo `ngay_sua_chua`

### Task 9
- Mục tiêu: tạo màn hình chi tiết
- Đầu ra: hiển thị đầy đủ field chính
- Tiêu chí hoàn thành: nhìn ra ngay trạng thái sửa chữa

### Task 10
- Mục tiêu: tạo form thêm bản ghi
- Đầu ra: form có validation cơ bản
- Tiêu chí hoàn thành: lưu local thành công, thêm queue sync

### Task 11
- Mục tiêu: tạo form cập nhật `ngay_sua_chua`
- Đầu ra: thao tác cập nhật sửa chữa
- Tiêu chí hoàn thành: đổi trạng thái hiển thị đúng sau khi lưu

## Giai đoạn 4 — Ổn định
### Task 12
- Mục tiêu: thêm kiểm tra schema Google Sheet
- Đầu ra: lớp validate cấu trúc sheet
- Tiêu chí hoàn thành: app không sync mù khi cấu trúc bị đổi

### Task 13
- Mục tiêu: xử lý lỗi sync
- Đầu ra: trạng thái lỗi + retry
- Tiêu chí hoàn thành: không mất dữ liệu khi lỗi mạng/API

### Task 14
- Mục tiêu: kiểm thử end-to-end
- Đầu ra: test plan + checklist test tay
- Tiêu chí hoàn thành: pass các luồng quan trọng của MVP

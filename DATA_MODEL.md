# DATA MODEL ĐỀ XUẤT

## 1. Bảng danh mục thiết bị: DEVICE_MASTER
Mục đích: lưu thông tin gốc của thiết bị.

### Cột đề xuất
- `ma_thiet_bi` (PK logic cho danh mục, unique)
- `ten_thiet_bi`
- `hang_muc`
- `khu_vuc`
- `mo_ta_thiet_bi`
- `is_active`
- `created_at`
- `updated_at`

## 2. Bảng nhật ký bất thường: DMBT_LOG
Mục đích: mỗi dòng là một lần phát sinh bất thường.

### Cột đề xuất
- `record_id` (ID duy nhất cho từng bản ghi)
- `ma_thiet_bi` (FK logic tham chiếu DEVICE_MASTER)
- `nguoi_bao_cao`
- `tinh_trang_thiet_bi`
- `ktv_phu_trach`
- `ngay_phat_hien`
- `ngay_sua_chua`
- `ghi_chu`
- `created_at`
- `updated_at`
- `source` (app / sheet / imported)

### Trạng thái suy ra
Không lưu cột trạng thái sửa chữa nhập tay.
App tự suy ra:
- `repair_status = da_sua_chua` nếu `ngay_sua_chua` có giá trị
- `repair_status = chua_sua_chua` nếu `ngay_sua_chua` trống

## 3. Bảng local cho sync queue: SYNC_QUEUE
Mục đích: lưu các thay đổi chờ đẩy lên Google Sheet.

### Cột đề xuất
- `queue_id`
- `record_id`
- `entity_type` (device_master / dmbt_log)
- `operation_type` (insert / update / delete_soft)
- `payload_json`
- `sync_status` (pending / syncing / synced / failed)
- `retry_count`
- `last_error`
- `created_at`
- `updated_at`
- `last_attempt_at`

## 4. Bảng local config: APP_LINK_CONFIG
Mục đích: lưu thông tin liên kết spreadsheet.

### Cột đề xuất
- `config_id`
- `spreadsheet_id`
- `schema_version`
- `device_master_sheet_id`
- `dmbt_log_sheet_id`
- `lookup_sheet_id`
- `last_metadata_refresh_at`
- `last_successful_sync_at`

## 5. Lưu ý
- Không dùng số dòng trong Google Sheet làm định danh.
- Không dùng `STT` làm khóa chính.
- Nếu cần báo cáo, có thể thêm tab `REPORT`, nhưng không để app phụ thuộc vào tab báo cáo để chạy nghiệp vụ chính.

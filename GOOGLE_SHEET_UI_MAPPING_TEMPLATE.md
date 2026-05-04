# MẪU MAPPING GIỮA UI VÀ GOOGLE SHEET

> Dùng mẫu này trước khi code UI.

## Bảng mapping

| UI Field | Mục đích hiển thị | Sheet Tab | Cột Google Sheet | Dữ liệu gốc hay suy ra | Có chỉnh sửa từ UI không | Ghi ngược về đâu |
|---|---|---|---|---|---|---|
| ma_thiet_bi | Tra cứu chính | DMBT_LOG / DEVICE_MASTER | ma_thiet_bi | Gốc | Có / Chọn | ma_thiet_bi |
| ten_thiet_bi | Hiển thị tên | DEVICE_MASTER | ten_thiet_bi | Gốc | Không / Tùy | Không |
| ngay_phat_hien | Ngày phát sinh | DMBT_LOG | ngay_phat_hien | Gốc | Có | ngay_phat_hien |
| ngay_sua_chua | Ngày sửa | DMBT_LOG | ngay_sua_chua | Gốc | Có | ngay_sua_chua |
| trang_thai_sua_chua | Hiển thị badge | Không lưu riêng | suy ra từ ngay_sua_chua | Suy ra | Không | Không |
| tinh_trang_thiet_bi | Mô tả lỗi | DMBT_LOG | tinh_trang_thiet_bi | Gốc | Có | tinh_trang_thiet_bi |
| ghi_chu | Ghi chú thêm | DMBT_LOG | ghi_chu | Gốc | Có | ghi_chu |

## Rule kiểm tra
1. Mỗi field trên UI phải map rõ về 1 cột hoặc 1 rule suy ra.
2. Field suy ra không được ghi ngược như dữ liệu gốc.
3. Nếu không giải thích được nguồn dữ liệu của 1 field UI, không được code field đó.

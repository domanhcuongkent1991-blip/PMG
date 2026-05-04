# UI / UX RULES

## 1. Nguyên tắc chung
- UI phải ưu tiên tra cứu nhanh theo `ma_thiet_bi`.
- Màn hình đầu tiên phải có ô tìm kiếm `ma_thiet_bi` nổi bật.
- MVP không cần đẹp cầu kỳ; cần rõ, nhanh, ít bấm.
- UI có thể khác cách trình bày của Google Sheet, nhưng không được đổi ý nghĩa dữ liệu gốc.

## 2. Quy tắc hiển thị trạng thái
- Không nhập tay trạng thái sửa chữa.
- UI suy ra trạng thái từ `ngay_sua_chua`:
  - có ngày = Đã sửa chữa
  - trống = Chưa sửa chữa
- Trạng thái phải hiển thị bằng chữ rõ ràng, không chỉ phụ thuộc vào màu.

## 3. Màn hình tối thiểu cho MVP
1. Màn hình tra cứu chính
2. Màn hình danh sách kết quả
3. Màn hình chi tiết bản ghi / thiết bị
4. Màn hình thêm bản ghi
5. Màn hình cập nhật `ngay_sua_chua`

## 4. Luồng dùng chính
### Tra cứu
- Mở app
- Nhập/chọn `ma_thiet_bi`
- Xem danh sách kết quả
- Lọc tất cả / đã sửa / chưa sửa
- Mở chi tiết

### Cập nhật sửa chữa
- Mở chi tiết bản ghi
- Chọn cập nhật ngày sửa chữa
- Lưu local
- Chờ sync nếu đang offline

## 5. Trạng thái màn hình bắt buộc
- loading
- empty
- error
- success

## 6. Mapping UI với dữ liệu
Mỗi field trên UI phải ghi rõ:
- lấy từ cột nào trong Google Sheet
- có ghi ngược lại cột nào không
- hay chỉ là dữ liệu suy ra

## 7. Điều không nên làm
- Không thiết kế UI theo logic quá xa dữ liệu thật.
- Không tạo thêm trạng thái nhập tay chỉ để UI nhìn có vẻ đẹp hơn.
- Không cố nhét quá nhiều module vào màn hình đầu tiên.

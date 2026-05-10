# AGENT1 MONTHLY PHASE3 MANUAL SIDEBAR LABEL REPORT (2026-05-06)

## 1) Sidebar hiện phân loại theo gì

Sau khi cập nhật Phase 3, phân loại trong `CategoryFilterMapper` dùng theo thứ tự an toàn:

1. `sourceSheetId` của record (ưu tiên cao nhất):
- `157327514` -> `MONTHLY_REPAIR` (Sửa chữa tháng)
- `1383308512` -> `MONTHLY_DMBT` (DMBT tháng)
- Các gid DMBT năm (`849979183`, `1783863163`, `1224276666`, `989601207`, `1607125070`) -> `YEARLY_DMBT`

2. Nếu `sourceSheetId` null, fallback theo gid namespaced trong `recordId` dạng `readonly-dmbt-<gid>-...`.

3. Nếu vẫn không có provenance rõ, fallback an toàn về `YEARLY_DMBT` (không đẩy vào monthly theo text nữa).

Kết luận: phân loại sidebar/filter không còn dựa vào text `T4/T5` trong `hangMuc` hay tên tab.

## 2) Đã loại bỏ/không dùng auto Google Sheet title chưa

Đã xác nhận:
- UI sidebar/filter không fetch title tab Google Sheet để hiển thị.
- Không có logic tự đổi label theo title tab ở `SearchScreen`/`CategoryFilterMapper`.
- Label hiển thị đang dùng string resource tĩnh an toàn:
  - `DMBT tháng`
  - `Sửa chữa tháng`

Đúng chỉ đạo: sync core vẫn route bằng `gid/sourceSheetId`, không dùng tên tab.

## 3) Cách người dùng sửa tên thủ công là gì

Trong phạm vi Phase 3 này, app chưa có module settings chung cho đổi label sidebar theo nhu cầu user. Vì vậy giữ default trung tính:
- `DMBT tháng`
- `Sửa chữa tháng`

Cách thủ công hiện tại (không code thêm trong lượt này):
- Sửa trực tiếp resource string trong app build nếu team muốn đổi wording cho bản phát hành nội bộ.
- Không dùng auto title từ Google Sheet.

## 4) Nếu chưa implement sửa tên trong app, lý do và phương án bước sau

### Lý do chưa implement input đổi tên trong app
- Scope task yêu cầu tránh tạo màn settings lớn/refactor rộng.
- Hiện chưa có hạ tầng preferences chung cho nhãn sidebar DMBT, chỉ có cụm cài đặt chuyên biệt HGT.
- Mục tiêu ưu tiên an toàn sync: giữ nhãn cố định, tránh thêm nguồn lỗi UI/config runtime.

### Phương án kỹ thuật bước sau (nhỏ, ít rủi ro)
1. Thêm 2 key DataStore riêng cho label tùy biến (optional):
- `sidebar_monthly_dmbt_label_override`
- `sidebar_monthly_repair_label_override`

2. Ở UI, hiển thị:
- Nếu override rỗng -> dùng mặc định resource (`DMBT tháng`, `Sửa chữa tháng`)
- Nếu có override -> dùng override

3. Thêm 1 màn mini-settings cực gọn (2 ô text + reset về mặc định), không đụng sync core.

## 5) Test/build đã chạy thế nào

### File đã sửa
- `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
- `android-mvp/app/src/test/java/com/example/devicetracker/ui/search/CategoryFilterMapperTest.kt`

### Test bổ sung/cập nhật
- Cập nhật test monthly/yearly/repair theo `sourceSheetId`.
- Thêm kiểm thử bảo vệ: record có text `DMBT T5.2026` nhưng `sourceSheetId` yearly thì không bị classify monthly.

### Build pipeline
Đã chạy:
- `./scripts/build-android-safe.ps1`

Kết quả:
- `:app:testDebugUnitTest` PASS
- `:app:assembleDebug` PASS
- BUILD SUCCESSFUL

Ghi chú:
- Có warning môi trường/metrics và deprecation như các lượt trước, không gây fail build.

# AGENT2 UAT EMPTY APP PLAN (READ-ONLY) - 2026-05-07

## 1) Mục tiêu
Chuẩn bị kịch bản UAT cho tình huống **app rỗng dữ liệu local** rồi tự đồng bộ từ Google Sheet thật để xác nhận:
- Pull dữ liệu DMBT 2022-2026 vào app đúng và có provenance `sourceSheetId`.
- Sync lần 2 không tạo duplicate (idempotent).
- DMBT tháng + Sửa chữa tháng hiện chưa dùng trong flow chính (decommission logic), nhưng chưa xóa vật lý tab để giữ rollback.

Phạm vi tài liệu này: chỉ kế hoạch/test checklist, không sửa code app, không ghi Google Sheet.

---

## 2) Tiền điều kiện
1. Có APK test đã build từ nhánh cần UAT.
2. Điện thoại test kết nối ADB.
3. Có quyền đọc Google Sheet thật.
4. Có baseline inventory sheet mới nhất (nếu có thể, regenerate `docs/sheets-inventory.md` trước UAT để lấy `valid_rows` mới).
5. Không chạy thao tác ghi/sửa cấu trúc Google Sheet trong bài test này.

---

## 3) Checklist thao tác UAT app rỗng

## A. Reset app về trạng thái rỗng
1. Gỡ app cũ:
   - `adb uninstall com.example.devicetracker`
2. Cài lại APK:
   - `adb install -r -t -g <duong_dan_apk>`
3. Mở app 1 lần để tạo DB local mới.
4. (Tuỳ chọn tương đương) Nếu không uninstall thì clear data:
   - `adb shell pm clear com.example.devicetracker`
5. Xác nhận DB local ban đầu gần rỗng (sẽ dump ở bước B).

## B. Sync lần 1 + dump DB
1. Trong app chọn full sync.
2. Chờ sync hoàn tất (pending=0, queue=0, retry error=0 nếu UI có hiển thị).
3. Kéo DB từ điện thoại về máy:
   - `device_tracker_after_sync1.db`
4. Query đếm:
   - Tổng `device_logs`.
   - Count theo `sourceSheetId`.
   - Count `sourceSheetId is null`.
   - Count duplicate business key:
     - `maThietBi + ngayPhatHien + hangMuc + tinhTrangThietBi`.

## C. Sync lần 2 + dump DB
1. Không chỉnh dữ liệu gì, chạy full sync lần 2 ngay sau lần 1.
2. Kéo DB:
   - `device_tracker_after_sync2.db`
3. Query lại cùng bộ chỉ số như sync1.
4. So sánh sync2 với sync1 để kiểm idempotency.

## D. So sánh với Google Sheet
1. Lấy `valid_rows` theo từng tab DMBT 2022-2026 từ inventory.
2. Đối chiếu với local count theo `sourceSheetId` tương ứng:
   - 2022 -> `849979183`
   - 2023 -> `1783863163`
   - 2024 -> `1224276666`
   - 2025 -> `989601207`
   - 2026 -> `1607125070`
3. Ghi lệch tuyệt đối và tỉ lệ lệch cho từng năm.

---

## 4) Tiêu chí PASS/FAIL rõ ràng cho DMBT 2022-2026

## PASS (cho từng năm)
1. Local có record với `sourceSheetId` đúng gid năm đó (`count > 0`).
2. Chênh lệch count giữa local và `valid_rows` của sheet nằm trong ngưỡng cho phép đã thống nhất trước UAT:
   - Khuyến nghị ban đầu: lệch <= 2% hoặc có danh sách row skip hợp lệ giải thích được.
3. Từ sync1 sang sync2:
   - Count theo `sourceSheetId` không tăng bất thường.
   - Số group duplicate business key không tăng.
   - Tổng rows `device_logs` không tăng bất thường khi không có thay đổi dữ liệu upstream.

## FAIL (chỉ cần 1 điều kiện)
1. Năm nào có `sourceSheetId` count = 0.
2. Lệch count lớn, không giải thích được bằng row invalid/skip.
3. Sync lần 2 làm tăng duplicate hoặc tăng tổng rows bất thường.
4. `sourceSheetId = null` vẫn chiếm đa số lớn như baseline cũ (1869/1889) sau khi fix provenance.

---

## 5) Kiểm chứng monthly/repair monthly chưa dùng nhưng giữ rollback

Mục tiêu: xác nhận app không còn phụ thuộc monthly cho flow chính, nhưng tab monthly vẫn tồn tại vật lý trên Sheet để rollback khi cần.

## Kiểm chứng dữ liệu
1. Sau sync1/sync2, query `device_logs`:
   - `sourceSheetId = 1383308512` (DMBT tháng cũ)
   - `sourceSheetId = 157327514` (Sửa chữa tháng cũ)
2. Kỳ vọng theo hướng decommission:
   - Hai count này = 0 hoặc không tăng sau sync2.
3. Nếu count > 0 hoặc tăng sau sync2:
   - Đánh dấu FAIL decommission guard.

## Kiểm chứng hành vi app
1. UI/sidebar không yêu cầu người dùng thao tác theo tab monthly để sync được DMBT 2022-2026.
2. Sync chính vẫn chạy khi monthly tab còn tồn tại trên workbook.
3. Không xóa vật lý 2 tab monthly trong test này.

---

## 6) Bộ số liệu bắt buộc lưu trong biên bản UAT
1. Ảnh Sync Status sau sync1 và sync2.
2. Hai file DB: `after_sync1.db`, `after_sync2.db`.
3. Bảng count:
   - `device_logs total`
   - `sourceSheetId` breakdown
   - `sourceSheetId is null`
   - duplicate groups + rows in duplicate groups
4. Bảng so sánh với `valid_rows` của DMBT 2022-2026.
5. Kết luận PASS/FAIL cho từng năm + tổng thể.

---

## 7) SQL mẫu (read-only) dùng khi cần

```sql
-- total
select count(*) as total_device_logs from device_logs;

-- count by sourceSheetId
select coalesce(cast(sourceSheetId as text), 'NULL') as source_sheet_id, count(*) as row_count
from device_logs
group by sourceSheetId
order by row_count desc;

-- null provenance
select count(*) as null_source_sheet_id
from device_logs
where sourceSheetId is null;

-- duplicate groups by business key
select count(*) as duplicate_groups
from (
  select maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi, count(*) c
  from device_logs
  group by maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi
  having c > 1
);
```

---

## 8) Kết luận vận hành
- Đây là plan read-only để chuẩn bị UAT sau fix.
- Không sửa code app trong task này.
- Trọng tâm quyết định go/no-go là:
  - provenance `sourceSheetId` theo từng năm DMBT,
  - idempotency sync lần 2,
  - decommission guard cho monthly/repair monthly.

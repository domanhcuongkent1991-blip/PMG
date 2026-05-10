# AGENT2 EMPTY APP UAT PACKET - 2026-05-07

Owner: Agent 2  
Audience: Director / Operator  
Mode: Controlled UAT execution packet (read-only verification focus)

## 1) Mục tiêu packet
Chạy UAT “app rỗng -> sync từ Google Sheet” sau khi Agent 1 fix xong, để kiểm chứng:
1. Count/provenance theo `sourceSheetId` cho DMBT 2022-2026.
2. Idempotency: sync lần 2 không làm tăng duplicate.
3. Monthly/repair monthly không còn tham gia flow chính (decommission logic), nhưng vẫn giữ tab vật lý để rollback.

---

## 2) STOP conditions (bắt buộc)
1. Không chạy UAT thật nếu Agent 1 chưa hoàn tất fix và chưa bàn giao build.
2. Không ghi/chỉnh Google Sheet thật trong packet này.
3. Không tiếp tục sync nếu DB sau reset chưa rỗng:
   - `device_logs` phải bằng `0` trước sync, hoặc phải có giải thích rõ được Manager duyệt.
   - Nếu còn record dạng `seed-beta-*` trước sync thì đây không phải UAT app rỗng, phải dừng.
4. Không tuyên bố PASS nếu thiếu evidence:
   - thiếu ảnh sync status,
   - thiếu DB dump trước sync, sync1, sync2,
   - thiếu bảng SQL output.
5. Dừng ngay và báo Manager nếu:
   - app crash liên tục,
   - sync báo success nhưng dữ liệu rõ ràng không tăng/không đúng provenance,
   - không pull được DB để kiểm chứng.

---

## 3) Tiền điều kiện
1. Có APK từ bản fix của Agent 1.
2. Điện thoại Android kết nối ADB.
3. Có quyền xem Google Sheet thật (read-only đủ cho packet này).
4. Máy có `adb` và `sqlite3`.

---

## 4) Lệnh ADB mẫu (copy/paste)

## A. Kiểm tra device
```powershell
adb devices -l
```

## B. Reset app về trạng thái rỗng
Chọn 1 trong 2 cách:

1. Uninstall + install lại:
```powershell
adb uninstall com.example.devicetracker
adb install -r -t -g "<ABSOLUTE_PATH_TO_APK>"
```

2. Hoặc clear data (giữ app):
```powershell
adb shell pm clear com.example.devicetracker
adb install -r -t -g "<ABSOLUTE_PATH_TO_APK>"
```

## C. Launch app
```powershell
adb shell am start -n com.example.devicetracker/.MainActivity
```

Ghi chú: thao tác sync full được thực hiện trên UI app bởi Operator.

## D. Pull DB trước/sau sync
Đường dẫn DB thường gặp:
- `/data/data/com.example.devicetracker/databases/device_tracker.db`

Lệnh mẫu:
```powershell
adb shell run-as com.example.devicetracker ls -la /data/data/com.example.devicetracker/databases
adb exec-out run-as com.example.devicetracker cat /data/data/com.example.devicetracker/databases/device_tracker.db > device_tracker_before_sync.db
adb exec-out run-as com.example.devicetracker cat /data/data/com.example.devicetracker/databases/device_tracker.db > device_tracker_after_sync1.db
adb exec-out run-as com.example.devicetracker cat /data/data/com.example.devicetracker/databases/device_tracker.db > device_tracker_after_sync2.db
```

Nếu `run-as` không khả dụng trên build hiện tại: dừng và báo Manager để cấp cách lấy DB phù hợp (không root phá rào).

---

## 5) Quy trình thực thi UAT từng bước

## Phase 0 - Gating
1. Xác nhận “Agent 1 fix done” + có APK mới.
2. Xác nhận ngày giờ test, tester, serial máy.

## Phase 1 - Sync lần 1
1. Reset app rỗng (mục 4B), mở app (mục 4C).
2. Pull DB trước sync thành `device_tracker_before_sync.db`.
3. Chạy SQL gate trước sync ở mục 6A.
4. Nếu `device_logs_before_sync != 0` hoặc `seed_beta_rows_before_sync != 0`, dừng UAT và báo Manager.
5. Trên app chạy full sync lần 1.
6. Chụp ảnh Sync Status (pending/queue/retry).
7. Pull DB thành `device_tracker_after_sync1.db`.
8. Chạy SQL read-only (mục 6B) và lưu output.

## Phase 2 - Sync lần 2 (idempotency)
1. Không thay đổi gì trên app/sheet.
2. Chạy full sync lần 2.
3. Chụp ảnh Sync Status lần 2.
4. Pull DB thành `device_tracker_after_sync2.db`.
5. Chạy lại SQL read-only (mục 6).
6. So sánh sync2 với sync1.

## Phase 3 - Đối chiếu DMBT 2022-2026
1. Đối chiếu local count theo `sourceSheetId` với `valid_rows` inventory:
   - `849979183` (2022)
   - `1783863163` (2023)
   - `1224276666` (2024)
   - `989601207` (2025)
   - `1607125070` (2026)
2. Ghi lệch và nhận định PASS/FAIL từng năm.

## Phase 4 - Decommission guard cho monthly
1. Kiểm tra count `sourceSheetId=1383308512` và `157327514`.
2. Kết luận “không dùng flow chính” nếu 2 count = 0 hoặc không tăng sync2 so với sync1.

---

## 6) SQL read-only bắt buộc

## A. Gate trước sync

Chạy trên `device_tracker_before_sync.db`.

```sql
-- DB phải rỗng trước sync trong bài UAT app rỗng
select count(*) as device_logs_before_sync from device_logs;

-- Nếu query này > 0 thì UAT không phải app rỗng thật
select count(*) as seed_beta_rows_before_sync
from device_logs
where recordId like 'seed-beta-%';
```

## B. Count sau sync

Chạy cho cả `device_tracker_after_sync1.db` và `device_tracker_after_sync2.db`.

```sql
-- 1) total device_logs
select count(*) as total_device_logs from device_logs;

-- 2) count theo sourceSheetId
select coalesce(cast(sourceSheetId as text), 'NULL') as source_sheet_id, count(*) as row_count
from device_logs
group by sourceSheetId
order by row_count desc;

-- 3) sourceSheetId null
select count(*) as null_source_sheet_id
from device_logs
where sourceSheetId is null;

-- 4) duplicate groups theo business key
select count(*) as duplicate_groups
from (
  select maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi, count(*) c
  from device_logs
  group by maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi
  having c > 1
);

-- 5) rows nằm trong duplicate groups
select coalesce(sum(c), 0) as rows_in_duplicate_groups
from (
  select count(*) c
  from device_logs
  group by maThietBi, ngayPhatHien, hangMuc, tinhTrangThietBi
  having c > 1
);
```

Lệnh PowerShell mẫu với `sqlite3`:
```powershell
sqlite3 .\device_tracker_after_sync1.db "<PASTE_SQL_ONE_LINE_OR_FILE>"
sqlite3 .\device_tracker_after_sync2.db "<PASTE_SQL_ONE_LINE_OR_FILE>"
```

---

## 7) PASS/FAIL tiêu chuẩn vận hành

## PASS
1. DMBT 2022-2026 đều có count `sourceSheetId` > 0.
2. Sync2 không làm tăng `duplicate_groups` và `rows_in_duplicate_groups`.
3. `NULL sourceSheetId` giảm rõ so với baseline cũ (tham chiếu audit trước fix).
4. Monthly gids (`1383308512`, `157327514`) không tăng bất thường.

## FAIL
1. Có năm DMBT nào count `sourceSheetId` = 0.
2. Sync2 làm tăng duplicate.
3. `NULL sourceSheetId` vẫn áp đảo như trước fix.
4. Không có đủ evidence ảnh + DB + SQL output.

---

## 8) Mẫu bảng báo cáo kết quả sync1/sync2

## A. Metadata test
| Field | Value |
|---|---|
| Date/time | |
| Operator | |
| Device serial | |
| APK path | |
| Git branch/commit (nếu có) | |
| before_sync DB path | |
| before_sync device_logs | |
| before_sync seed_beta_rows | |

## B. Sync status evidence
| Step | Screenshot path | Pending | Queue | Retry error | Note |
|---|---|---:|---:|---:|---|
| Sync1 | | | | | |
| Sync2 | | | | | |

## C. Count theo sourceSheetId
| sourceSheetId | Meaning | Sync1 count | Sync2 count | Delta | Pass/Fail |
|---:|---|---:|---:|---:|---|
| 849979183 | DMBT 2022 | | | | |
| 1783863163 | DMBT 2023 | | | | |
| 1224276666 | DMBT 2024 | | | | |
| 989601207 | DMBT 2025 | | | | |
| 1607125070 | DMBT 2026 | | | | |
| 1383308512 | DMBT tháng (legacy) | | | | |
| 157327514 | Sửa chữa tháng (legacy) | | | | |
| NULL | provenance null | | | | |

## D. Duplicate/idempotency
| Metric | Sync1 | Sync2 | Delta | Pass/Fail |
|---|---:|---:|---:|---|
| total_device_logs | | | | |
| duplicate_groups | | | | |
| rows_in_duplicate_groups | | | | |

## E. Đối chiếu sheet valid_rows
| DMBT year | gid | Sheet valid_rows | Local sync2 count | lệch | Pass/Fail | Ghi chú |
|---|---:|---:|---:|---:|---|---|
| 2022 | 849979183 | | | | | |
| 2023 | 1783863163 | | | | | |
| 2024 | 1224276666 | | | | | |
| 2025 | 989601207 | | | | | |
| 2026 | 1607125070 | | | | | |

---

## 9) Kết luận cuối packet
Chỉ kết luận PASS tổng khi:
1. Đủ evidence (ảnh + DB + SQL + bảng đối chiếu).
2. Qua được PASS criteria ở mục 7.
3. Không có STOP condition nào bị vi phạm.

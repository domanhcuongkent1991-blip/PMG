# AGENT2 DATA COUNT AUDIT (A2-P0-DATA-COUNT-AUDIT) - 2026-05-07

## 1) Phạm vi và nguồn sự thật đã đọc
- `AUDIT-FULL-2.md`
- `WORKLOG_2026-05-06.md`
- `docs/sheets-inventory.md`
- `docs/uat/results/USER_UAT_RESULT_2026-05-05.md`
- `docs/uat/results/USER_UAT_DMBT_2025_SYNC_RESULT_2026-05-05.md`
- DB evidence local sau UAT:
  - `docs/uat/evidence/device_tracker_after_dmbt2025.db`
  - Đối chiếu thêm: `docs/uat/evidence/device_tracker_uat.db`, `docs/uat/evidence/device_tracker_uat_latest.db`

Ghi chú:
- Không sửa code app.
- Không ghi Google Sheet thật.
- Thiết bị ADB tại thời điểm audit: không có device online (`adb devices -l` trả danh sách trống).

---

## 2) Cách đo đếm
- Google Sheet count: lấy từ `docs/sheets-inventory.md`, cột `valid_rows`.
- Local app count theo `sourceSheetId`: query `device_logs` trong `device_tracker_after_dmbt2025.db`.
- Local `sourceSheetId = null`: query `device_logs` cùng DB.
- Duplicate nghi vấn theo business key:
  - `maThietBi + ngayPhatHien + hangMuc + tinhTrangThietBi`
  - đếm group có `count(*) > 1`.

---

## 3) Bảng so sánh count (Sheet vs Local App)

Snapshot local dùng để audit chính: `device_tracker_after_dmbt2025.db` (đúng bối cảnh UAT DMBT 2025).

| Tab Google Sheet | gid/sourceSheetId | Valid rows trên Sheet | Local rows theo sourceSheetId | Lệch (Local - Sheet) | Nhận định |
|---|---:|---:|---:|---:|---|
| DMBT 2022 | 849979183 | 36 | 0 | -36 | Thiếu provenance 100% trong app |
| DMBT 2023 | 1783863163 | 534 | 0 | -534 | Thiếu provenance 100% trong app |
| DMBT 2024 | 1224276666 | 582 | 0 | -582 | Thiếu provenance 100% trong app |
| DMBT 2025 | 989601207 | 559 | 0 | -559 | Thiếu provenance 100% trong app (đúng với UAT fail 2 chiều) |
| DMBT 2026 | 1607125070 | 128 | 20 | -108 | Thiếu mạnh, mới map được một phần |
| DMBT T4.2026 (monthly) | 1383308512 | 21 | 0 | -21 | Không có map provenance (đang hướng decommission) |
| Sửa chữa T4.2026 (monthly) | 157327514 | 22 | 0 | -22 | Không có map provenance (đang hướng decommission) |
| HGT định kỳ | 57428884 | 49 | N/A trong `device_logs` | N/A | HGT nằm bảng riêng `hgt_checks` |

### Bổ sung cho HGT
- `hgt_checks` trong snapshot `device_tracker_after_dmbt2025.db`: **51 rows**.
- `docs/sheets-inventory.md` ghi HGT valid rows: **49**.
- Có lệch +2 theo snapshot này (khác với UAT-12 trước đó app 48 vs sheet 52), cần xác nhận lại thời điểm/nguồn snapshot khi retest.

---

## 4) Tổng quan local theo provenance

Từ `device_logs` của `device_tracker_after_dmbt2025.db`:
- Tổng `device_logs`: **1889**
- `sourceSheetId = null`: **1869**
- `sourceSheetId = 1607125070`: **20**
- `sourceSheetId` khác (2022/2023/2024/2025/monthly/repair/HGT): **0**

Kết luận ngắn:
- Dữ liệu local hiện tại gần như không gắn provenance sheet (null chiếm ~98.94%).
- Đây là bằng chứng mạnh cho nhận định sync 2 chiều DMBT multi-sheet không ổn định/khó target đúng tab.

---

## 5) Nghi vấn duplicate theo business key

Trên `device_logs` (`device_tracker_after_dmbt2025.db`):
- Số group duplicate (`count > 1`): **27**
- Tổng rows thuộc các group duplicate: **63**
- Phân bố provenance trong duplicate rows:
  - `sourceSheetId = null`: **55**
  - `sourceSheetId = 1607125070`: **8**

Nhận định:
- Duplicate chủ yếu nằm ở tập legacy/provenance-null.
- Khi retry pull/push, nhóm này có rủi ro bị append/merge sai cao hơn do thiếu neo `sourceSheetId`.

---

## 6) Tab/sheet nào đang thiếu dữ liệu trong app (theo bằng chứng count)

Thiếu rõ ràng theo `sourceSheetId`:
1. DMBT 2022 (`849979183`)
2. DMBT 2023 (`1783863163`)
3. DMBT 2024 (`1224276666`)
4. DMBT 2025 (`989601207`)
5. DMBT monthly T4 (`1383308512`) - đang chuẩn bị decommission
6. Repair monthly T4 (`157327514`) - đang chuẩn bị decommission
7. DMBT 2026 (`1607125070`) - mới có 20/128 rows có provenance

Lưu ý quan trọng:
- App có thể đang hiển thị bản ghi từ local/null nên “trông như có dữ liệu”, nhưng provenance theo tab gần như mất.
- Vì vậy phải tách 2 khái niệm:
  - Có record hiển thị.
  - Có record gắn đúng `sourceSheetId` để sync 2 chiều an toàn.

---

## 7) Checklist UAT tối thiểu sau khi Agent 1 fix

## A. Provenance/Count audit (bắt buộc)
1. Chạy full sync 1 lần, rồi dump DB local.
2. Xác nhận `sourceSheetId` count theo từng gid DMBT năm:
   - 2022/2023/2024/2025/2026 đều > 0 và tăng đúng theo pull.
3. Xác nhận `sourceSheetId = null` giảm mạnh so với baseline 1869.
4. Đối chiếu count local theo từng `sourceSheetId` với `valid_rows` sheet:
   - Cho phép lệch nhỏ có giải thích (row invalid/skip), không chấp nhận lệch lớn vô cớ.

## B. DMBT 2 chiều theo từng năm (ưu tiên 2025, 2026)
1. Sheet -> app: sửa 1 row thật (ngày sửa + ghi chú) trong DMBT 2025, sync, app phải cập nhật đúng row.
2. App -> sheet: sửa cùng row trong app, sync, phải ghi ngược đúng tab DMBT 2025 (không nhầm 2026/monthly).
3. Lặp lại cho DMBT 2026.

## C. Idempotency/duplicate (P0)
1. Chạy sync lại 2 lần liên tiếp.
2. Xác nhận:
   - Không tăng số row duplicate trên sheet.
   - Không tăng group duplicate local theo business key.

## D. Monthly decommission guard
1. Xác nhận flow chính không còn phụ thuộc monthly tab.
2. Dù monthly còn tồn tại trên sheet, app không được ghi nhầm vào monthly nếu contract mới bỏ monthly.

## E. Performance sanity (theo phản ánh lag/đơ)
1. Dùng liên tục 5-10 phút (search/filter/sync nhẹ).
2. Không freeze UI kéo dài, không ANR/crash.

---

## 8) Dữ liệu cần User/Manager cung cấp thêm nếu vẫn lệch
1. Ảnh/video trước-sau của cùng một row thật (bao gồm `ma_thiet_bi`, `ngay_phat_hien`, `hang_muc`).
2. gid/tab chính xác của row test.
3. Ảnh màn app đúng filter đang chọn (ALL/CHUA SUA/DA SUA) để loại trừ hiểu nhầm UI filter.
4. DB snapshot ngay sau mỗi bước sync (đặt timestamp rõ ràng).
5. Nếu có duplicate: cung cấp 2 record hiển thị trùng và `recordId` tương ứng.

---

## 9) Kết luận cho Giám đốc
- Lệch count hiện tại là **có thật** và **định lượng được**.
- Điểm nghẽn chính không chỉ là số lượng record, mà là **mất provenance `sourceSheetId` hàng loạt**:
  - `1869/1889` record đang `sourceSheetId = null`.
- Với trạng thái này, sync 2 chiều DMBT theo từng năm khó ổn định và dễ sinh duplicate/sai tab khi retry.
- Đề nghị Agent 1 ưu tiên fix theo hướng:
  - backfill/duy trì `sourceSheetId` đúng khi merge pull,
  - enforce target sheet theo provenance khi push,
  - kiểm soát idempotency bằng test/fixture trước khi retest UAT thật.

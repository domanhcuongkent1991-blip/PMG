# AGENT2 WS-VERIFY-02 Runtime Stability Validation (2026-05-08)

## Scope
- Reviewer/validator độc lập cho lỗi trắng màn hình.
- Không sửa app logic, chỉ review evidence hiện có.
- Nguồn chính:
  - `docs/uat/results/AGENT1_WHITE_SCREEN_RCA_2026-05-08.md`
  - `docs/uat/evidence/white-screen-2026-05-08/*`

---

## 1) Evidence checklist bắt buộc

## 1.1 Logcat full + runtime watch cùng mốc giờ
- Có:
  - `logcat_full_dump.txt` (21:19:46 -> có mốc 21:21:20+)
  - `logcat_runtime_watch.txt` (21:19:46 -> 21:24:58)
- Mốc sự cố Agent 1 ghi: `21:21:48 +07:00`.
- Các log freeze nằm sát cửa sổ sự cố:
  - `21:21:11` skipped `1216`
  - `21:21:33` skipped `720`
  - `21:22:32` skipped `2994`
  - `21:23:16` skipped `2113`
  - `21:24:58` skipped `5557`

Đánh giá: **PASS** (đủ log và mốc thời gian gần nhau).

## 1.2 Dumpsys activity top đúng lúc sự cố
- Có file `dumpsys_activity_top.txt`.
- Có dấu vết app: `ACTIVITY com.example.devicetracker/.MainActivity ... pid=13014`.
- Tuy nhiên file không ghi timestamp trực tiếp của snapshot nên “đúng ngay lúc sự cố” chưa chứng minh tuyệt đối.

Đánh giá: **PARTIAL PASS** (có foreground signal nhưng chưa chặt theo timestamp đồng bộ).

## 1.3 Ảnh/video cùng timestamp
- Có ảnh: `white_screen_live_capture.png` (timestamp filesystem 21:22:20).
- Không có video: `screenrecord` thiếu theo báo cáo Agent 1.

Đánh giá: **PARTIAL PASS**.

## 1.4 Bảng trước/sau >=20 vòng với số lần lỗi + frame skip lớn
- Agent 1 mô tả loop mục tiêu `20-30` vòng, nhưng chưa có counter trước/sau theo mẫu định lượng thống nhất.
- Chưa có bộ “before fix” định lượng để so sánh với “after fix”.

Đánh giá: **FAIL** (thiếu baseline định lượng trước/sau).

---

## 2) Số liệu định lượng trích từ evidence

## Runtime watch (`logcat_runtime_watch.txt`)
- Skipped-frame events: `11`
- Tổng skipped frames: `15318`
- Max skipped burst: `5557`
- Events >= 100 frames: `8`
- Events >= 500 frames: `6`
- Events >= 1000 frames: `5`

## Full dump (`logcat_full_dump.txt`)
- Skipped-frame events: `4`
- Tổng skipped frames: `3167`
- Max skipped burst: `1874`
- Events >= 1000 frames: `2`
- `FATAL EXCEPTION` for app: không thấy.
- ANR app-specific stacktrace: không thấy rõ cho `com.example.devicetracker`.

## Foreground signal gần cửa sổ sự cố
- `top=com.example.devicetracker.MainActivity` tại `21:21:20.662` trong `logcat_full_dump.txt`.

---

## 3) Bảng trước/sau (theo bằng chứng hiện có)

| Pha | Số vòng thao tác | Số lần trắng màn hình | Skipped-frame burst lớn nhất | Nhận định |
|---|---:|---:|---:|---|
| Before fix | Không có số liệu định lượng chuẩn hóa | N/A | N/A | Thiếu baseline |
| After fix (run 2026-05-08) | Mục tiêu 20-30 vòng (không có counter thực tế ghi lại) | >=1 (reproduced) | 5557 (runtime watch) | Freeze còn tái hiện |

Kết luận bảng: chưa đủ dữ liệu “before vs after” để chứng minh tần suất giảm.

---

## 4) Đánh giá hardening vừa làm có giảm tần suất không
- Chưa chứng minh được.
- Lý do:
  1. Không có baseline trước fix cùng kịch bản và cùng cách đo.
  2. Run sau fix vẫn tái hiện trắng màn hình/freeze.
  3. Burst skipped frame vẫn rất lớn (>=1000 nhiều lần).

---

## 5) Kết luận vòng hiện tại
- Runtime stability verdict: **FAIL**
- Mức tin cậy: **0.82 (high-medium)**
- Rủi ro còn lại:
  1. Freeze vẫn tái hiện trong loop thao tác nhanh.
  2. Chưa có call-path blocking cụ thể để sửa dứt điểm.
  3. Thiếu baseline định lượng trước/sau làm khó quyết định “đã ổn định”.

---

## 6) Top 3 đề xuất kỹ thuật ưu tiên cao nhất (vòng tiếp theo)
1. Chuẩn hóa harness test loop có bộ đếm bắt buộc (>=20 vòng, log từng vòng) và ghi KPI cố định: `white_screen_count`, `max_skipped_frames`, `events_ge_1000`.
2. Thu `perfetto` 20-30 giây đúng cửa sổ lỗi + marker event (`SAVE_CLICK`, `BACK_CLICK`, `SYNC_OPEN`, `SYNC_CLOSE`) để khoanh chính xác blocking section trên main thread.
3. Thêm guard chống chồng thao tác UI nhanh (debounce/throttle Save-Back-Sync transition) và đo lại theo cùng harness để xác nhận giảm tần suất định lượng.

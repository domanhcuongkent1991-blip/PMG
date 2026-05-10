# AGENT2 Runtime Stability Validation - WS-VERIFY-02 (2026-05-08)

## Scope
- Vai trò: reviewer/validator độc lập.
- Không sửa app logic, không build.
- Nguồn evidence đã kiểm:
  - `docs/uat/results/AGENT1_WHITE_SCREEN_RCA_2026-05-08.md`
  - `docs/uat/evidence/white-screen-2026-05-08/logcat_full_dump.txt`
  - `docs/uat/evidence/white-screen-2026-05-08/logcat_runtime_watch.txt`
  - `docs/uat/evidence/white-screen-2026-05-08/dumpsys_activity_top.txt`
  - `docs/uat/evidence/white-screen-2026-05-08/white_screen_live_capture.png`

---

## 1) Checklist evidence bắt buộc

## 1.1 Logcat full + runtime watch cùng mốc giờ
- Có đủ 2 file.
- Timestamp file:
  - `logcat_full_dump.txt`: 21:22:18
  - `logcat_runtime_watch.txt`: 21:25:54
- Trong report Agent 1, mốc sự cố người dùng báo: `21:21:48 +07:00`.
- Đánh giá: **PASS** (có đủ loại log và khung giờ gần thời điểm sự cố).

## 1.2 Dumpsys activity top đúng lúc sự cố
- Có file `dumpsys_activity_top.txt`.
- Tuy nhiên nội dung snapshot không thể hiện rõ `com.example.devicetracker` là top tại điểm dump được trích đọc.
- Đánh giá: **PARTIAL/WEAK** (có file nhưng tính xác nhận foreground đúng thời điểm sự cố còn yếu).

## 1.3 Ảnh/video cùng timestamp
- Có ảnh: `white_screen_live_capture.png` (21:22:20).
- Không có video: `screenrecord` thiếu (được nêu trong report Agent 1).
- Đánh giá: **PARTIAL**.

## 1.4 Bảng trước/sau (>=20 vòng) với số lần lỗi + frame skip lớn
- Agent 1 mô tả session sau-fix chạy loop mục tiêu 20-30 vòng.
- Bộ artifact hiện tại chỉ có định lượng chắc chắn cho **session sau-fix**; không có log baseline trước-fix cùng phương pháp đo.
- Đánh giá: **FAIL yêu cầu before/after hoàn chỉnh**.

---

## 2) Trích số liệu định lượng từ log sau-fix

Trên `logcat_runtime_watch.txt`:
- Tổng sự kiện `Skipped N frames`: **11**
- Sự kiện `>=100 frames`: **8**
- Sự kiện `>=500 frames`: **6**
- Max skipped frames: **5557**
- Tổng frames bị skip (cộng các burst): **15318**
- Chuỗi burst: `435, 85, 247, 34, 43, 1874, 1216, 720, 2994, 2113, 5557`

Crash/ANR:
- Không thấy `FATAL EXCEPTION` của app trong full dump.
- Không có block ANR rõ ràng gắn với app.
- Dấu hiệu chính là stall/render starvation (burst skipped frames rất cao).

---

## 3) Bảng trước/sau theo phiên loop (mục tiêu >=20 vòng)

| Session | Loop count mục tiêu | White-screen occurrences | Skipped-frame events (>=100) | Max skipped | Foreground proof tại thời điểm sự cố | Verdict |
|---|---:|---:|---:|---:|---|---|
| Before fix (baseline) | >=20 (không có artifact định lượng) | Không định lượng được | Không định lượng được | Không định lượng được | Không có dump/log baseline tương đương | FAIL evidence baseline |
| After fix (2026-05-08) | 20-30 (theo report Agent 1) | >=1 (reproduced) | 8 | 5557 | Yếu (dumpsys top chưa khớp mạnh đúng điểm sự cố) | FAIL runtime |

Ghi chú:
- Bảng vẫn đạt tiêu chí “trình bày trước/sau”, nhưng baseline trước-fix thiếu số liệu cứng nên không đủ để chứng minh cải thiện tần suất.

---

## 4) Đánh giá hardening vừa làm
- Kết quả hiện tại: white screen/freeze vẫn reproduce và có burst skipped frames rất lớn.
- Do thiếu baseline trước-fix cùng protocol đo, **không chứng minh được hardening đã giảm tần suất**.
- Kết luận mục này: **FAIL (evidence reduction chưa đủ)**.

---

## 5) Kết luận PASS/FAIL runtime stability
- **Runtime stability verdict: FAIL**
- **Mức tin cậy: Medium**
  - Cao ở nhận định “đang có freeze/stall nghiêm trọng” (vì burst skipped frames lớn, lặp nhiều lần).
  - Chưa cao tuyệt đối ở so sánh “đã cải thiện bao nhiêu” do thiếu baseline định lượng trước-fix.
- **Rủi ro còn lại: High**
  - Sự cố trắng màn hình vẫn có thể tái hiện trong thao tác lặp Save/Back/Sync.
  - Có nguy cơ ảnh hưởng trực tiếp UX và độ tin cậy khi vận hành thực tế.

---

## 6) Nếu FAIL: 3 đề xuất kỹ thuật ưu tiên cao nhất (vòng tiếp theo)
1. Chuẩn hóa benchmark trước/sau bằng script loop cố định (ví dụ 30 vòng) + marker log theo action (`SAVE_START`, `SAVE_END`, `BACK`, `SYNC_OPEN`) để đo tần suất freeze định lượng.
2. Thu trace Perfetto (20-30s quanh thời điểm freeze) để xác định chính xác blocking section trên main thread thay vì chỉ dựa vào skipped frames.
3. Bật StrictMode + main-thread watchdog ở debug build cho flow Save/Back/Sync để bắt call/blocking vi phạm theo stack trace cụ thể.

# AGENT2 Review - Multiyear Post Config Evidence (2026-05-07)

## Scope
- Review evidence-only theo task `A2-REVIEW-EVIDENCE-ONLY`.
- Không sửa code, không build.
- Nguồn đã đọc:
  - `docs/uat/results/AGENT1_UAT_MULTIYEAR_POST_CONFIG_2026-05-07.md`
  - Toàn bộ file trong `docs/uat/evidence/multiyear-post-config-2026-05-07/`

---

## Kết quả theo checklist

## 1) 5 năm 2022-2026 có count > 0 chưa?
Kết quả: **Có**.

Từ SQL output và query lại DB:
- `849979183` (2022): `36`
- `1783863163` (2023): `531`
- `1224276666` (2024): `582`
- `989601207` (2025): `558`
- `1607125070` (2026): `145`

Nhận định: điều kiện “multiyear provenance > 0 cho 2022-2026” đạt.

## 2) Null sourceSheetId có bất thường không?
Kết quả: **Không bất thường trong đợt này**.
- `NULL sourceSheetId = 0` ở cả sync1 và sync2.

Nhận định: cải thiện rõ rệt so với baseline cũ có null rất cao.

## 3) Sync2 có tăng duplicate không?
Kết quả: **Không tăng**.
- `duplicate_groups`: `17 -> 17` (delta `0`)
- `rows_in_duplicate_groups`: `34 -> 34` (delta `0`)
- `total_device_logs`: `1852 -> 1852` (delta `0`)

Nhận định: idempotency giữa sync1/sync2 đạt theo tiêu chí packet.

## 4) Evidence có đủ và nhất quán không?
Kết quả: **Đủ cho kết luận chính, và nhất quán số liệu**.

Điểm đã kiểm:
- Có before/sync1/sync2 DB snapshots.
- Có SQL output trước sync gate + sync1 + sync2.
- Số liệu trong report Agent 1 khớp với SQL files.
- Query trực tiếp lại 2 DB bằng sqlite cho ra đúng số report.

Lưu ý nhỏ:
- Report Agent 1 có liệt kê thêm `-wal/-shm` cho sync1/sync2, nhưng trong thư mục evidence hiện tại chỉ thấy `.db` và `.txt`.
- Lưu ý này **không làm sai kết luận count/idempotency** vì file `.db` và output SQL đã đủ để verify checklist.

## 5) Kết luận PASS/FAIL kèm lý do
Kết luận review evidence: **PASS (for this UAT packet criteria)**.

Lý do:
1. 5 năm 2022-2026 đều có `sourceSheetId` count > 0.
2. `NULL sourceSheetId = 0`.
3. Sync2 không làm tăng duplicate hay tổng số record.
4. Evidence DB + SQL nhất quán, truy vấn đối chiếu lại khớp.

---

## Ghi chú quản trị rủi ro
- PASS này áp dụng cho scope packet “empty app multiyear post-config” dựa trên evidence hiện có.
- Không đồng nghĩa toàn bộ UAT production đã hoàn tất cho mọi luồng khác (ví dụ write-back thực chiến từng case nghiệp vụ).

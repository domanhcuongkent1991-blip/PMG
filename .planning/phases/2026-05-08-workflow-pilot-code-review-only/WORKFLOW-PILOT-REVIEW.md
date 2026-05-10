# Workflow Pilot Review - Code Review Only

## Review Mode

This review is intentionally read-only. It checks whether the upgraded workflow can supervise AI code work before allowing real code edits.

## Findings

### F1 - Medium - Monthly DMBT sheet classification is still hard-coded

- File/khu vực liên quan: android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt
- Evidence: `MONTHLY_DMBT_SHEET_IDS` is defined as a fixed set with one sheet id.
- Vì sao cần chú ý: Nếu sau này có thêm tháng mới, dự án có nguy cơ phải sửa code hoặc rebuild app thay vì chỉ cấu hình. Với người non-tech, đây là rủi ro vì một thay đổi dữ liệu Google Sheet có thể kéo theo sửa app.
- Đề xuất xử lý: Chuyển danh sách sheet tháng sang cấu hình an toàn, ví dụ BuildConfig/env trong ngắn hạn hoặc DataStore/màn hình cấu hình trong dài hạn. Bổ sung test cho nhiều tháng và nhiều năm.

### F2 - Medium - Sheet/OAuth configuration remains static in BuildConfig

- File/khu vực liên quan: android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt
- Evidence: spreadsheet id, access token, refresh token, OAuth client id/secret are read from BuildConfig.
- Vì sao cần chú ý: Cách này ổn cho MVP nếu secret không nằm trong git, nhưng chưa thân thiện vận hành. Khi đổi Google Sheet hoặc token, app có thể cần rebuild/cài lại.
- Đề xuất xử lý: Giữ kiểm tra secret nghiêm ngặt, dùng `.env.example` cho mẫu, và lập phase riêng để đưa cấu hình sang nơi quản lý an toàn hơn. Không nên làm chung với sửa sync logic.

### F3 - Low - Test comments show mojibake/encoding noise

- File/khu vực liên quan: android-mvp/app/src/test/java/com/example/devicetracker/data/repository/RepairRecordIdentityResolverTest.kt
- Evidence: Vietnamese comments appear garbled in the inspected output.
- Vì sao cần chú ý: Không làm app sai ngay, nhưng làm người đọc test khó hiểu, nhất là khi giao cho AI/agent khác bảo trì.
- Đề xuất xử lý: Chuẩn hóa encoding UTF-8 và sửa comment trong một cleanup-only phase, không trộn với logic sync.

## Positive Observations

- Sync code has explicit error messages for missing auth, missing sheet ids, duplicate sheet id mapping, and Google Sheets HTTP failures.
- There are focused tests around SheetConfig mapping, record identity, repository sync rules, and repair merge behavior.
- Existing worklogs/review docs show the project already has a strong habit of recording evidence instead of relying only on chat.

## Review Decision

Decision: FLAG

Reason: Workflow is ready for a controlled read-only/code-review pilot, but should not yet auto-edit source code without the new GSD code supervision gate and a narrow phase plan.

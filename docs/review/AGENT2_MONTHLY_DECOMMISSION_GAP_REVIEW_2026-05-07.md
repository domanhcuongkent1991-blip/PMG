# AGENT2 STATIC DECOMMISSION GAP REVIEW - 2026-05-07

## Task
- Task ID: `A2-STATIC-DECOMMISSION-GAP-REVIEW`
- Scope: review tĩnh, không sửa code.
- Đã đọc trước khi review:
  - `docs/uat/results/AGENT1_EMPTY_APP_UAT_RESULT_2026-05-07.md`

## UAT gate trước khi kết luận decommission
- Kết quả UAT empty-app từ Agent 1:
  - DMBT 2022/2023/2024/2025 theo provenance gid: `0`
  - DMBT 2026 theo provenance gid: `56`
  - `sourceSheetId NULL = 1835`
  - Overall: **FAIL**
- Kết luận gate:
  - **Không đủ evidence để kết luận decommission PASS**.
  - Lý do: nền provenance yearly chưa đạt, nên chưa thể xác nhận tắt monthly mà vẫn an toàn dữ liệu.

---

## 1) Monthly gid còn xuất hiện ở đâu

## A. Sync config / routing (core)
1. `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
   - còn hard-code `MONTHLY_DMBT_SHEET_IDS = setOf(1383308512)`.
   - còn dùng tập monthly để tách binding yearly/monthly.
2. `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`
   - còn pull monthly riêng (`monthlyDmbtSheetBindings`) và skip optional khi lỗi.
   - nghĩa là monthly vẫn là 1 nhánh trong full sync orchestration.
3. `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`
   - merge repair còn khóa vào monthly candidates qua `SheetConfig.MONTHLY_DMBT_SHEET_IDS.first()`.
   - flow repair hiện vẫn gắn logic “repair monthly chỉ merge monthly DMBT”.

## B. UI/filter/count classification
1. `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/CategoryFilterMapper.kt`
   - còn hard-code:
     - `MONTHLY_DMBT_SHEET_IDS: setOf(1383308512)`
     - `MONTHLY_REPAIR_SHEET_ID: 157327514`
   - còn bucket `MONTHLY_DMBT` và `MONTHLY_REPAIR`.
2. `android-mvp/app/src/main/java/com/example/devicetracker/ui/search/SearchScreen.kt`
   - vẫn render section/filter monthly.
   - vẫn có logic đổi label monthly thủ công (override) thay vì disable section.
3. `android-mvp/app/src/main/res/values/strings.xml`
   - vẫn có string/section cho monthly (`DMBT tháng`, `Sửa chữa tháng`, settings label monthly).
4. `android-mvp/app/src/main/java/com/example/devicetracker/data/local/preferences/SidebarMonthlyLabelStore.kt`
   - còn lưu preference override cho 2 nhãn monthly.

## C. Tài liệu/vận hành
1. `android-mvp/SYNC_SETUP.md`
   - còn hướng dẫn env/config với:
     - `SHEETS_DMBT_LOG_SHEET_ID=1383308512`
     - `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID=1383308512`
     - `SHEETS_REPAIR_LOG_SHEET_ID=157327514`
2. Nhiều tài liệu review/plan cũ vẫn xem 2 gid monthly là active stream.

---

## 2) Đánh giá khoảng cách decommission (gap)

## Gap-1 (P0): Core sync vẫn còn phụ thuộc monthly path
- Monthly chưa chỉ là “label UI”, mà vẫn là nhánh pull/merge thật trong sync.
- Nếu decommission nóng mà chưa refactor:
  - nguy cơ full sync behavior thay đổi khó kiểm soát.
  - nguy cơ tạo hiểu nhầm PASS vì yearly vẫn fail provenance nhưng monthly path bị tắt.

## Gap-2 (P1): UI vẫn expose monthly category như first-class flow
- Người dùng vẫn thấy mục monthly + cài tên monthly.
- Không phù hợp mục tiêu “decommission trong app, giữ rollback ở sheet”.

## Gap-3 (P1): Count/reporting chưa tách rõ active vs legacy sources
- Count provenance hiện chủ yếu null + 2026.
- Nếu chưa có cờ “monthly disabled”, operator khó xác định zero monthly là do decommission hay do pull fail.

## Gap-4 (P2): Vận hành docs chưa đồng bộ trạng thái decommission
- SYNC_SETUP/runbook còn chỉ định monthly là active sync target.

---

## 3) Đề xuất phạm vi sửa code cho lượt sau (không thực hiện trong lượt này)

## Phase A - Guard bằng config (an toàn nhất)
1. Thêm cờ runtime/config: `monthlySyncEnabled` và `monthlyUiEnabled`.
2. Mặc định:
   - `monthlySyncEnabled=false` (decommission app flow),
   - giữ khả năng bật lại để rollback.
3. Không xóa gid khỏi code ngay; chuyển sang “disabled by config”.

## Phase B - Sync core decommission
1. `SheetConfig.kt`
   - chuyển monthly gid sang legacy list có cờ enable/disable.
2. `SheetsRemoteDataSource.kt`
   - skip hoàn toàn monthly pull khi disabled (không warning noisy như lỗi).
3. `DeviceLogRepositoryImpl.kt`
   - bypass mergeRepairLogsFromRemote monthly path khi disabled.
   - đảm bảo yearly + HGT không phụ thuộc monthly.

## Phase C - UI decommission
1. `CategoryFilterMapper.kt`
   - khi monthly disabled: không sinh option/bucket monthly.
2. `SearchScreen.kt` + `strings.xml` + `SidebarMonthlyLabelStore.kt`
   - ẩn section monthly + ẩn settings đổi nhãn monthly khi disabled.
   - giữ data prefs cũ nhưng không dùng.

## Phase D - Ops & observability
1. Cập nhật `SYNC_SETUP.md` và runbook:
   - monthly ở trạng thái `LEGACY/ROLLBACK_ONLY`.
2. Thêm sync-status marker:
   - hiển thị rõ “monthly disabled” để tránh hiểu nhầm pull fail.

---

## 4) Điều kiện tối thiểu trước khi xác nhận decommission PASS
1. Re-run empty-app UAT sau fix với evidence đầy đủ (ảnh + DB + SQL sync1/sync2).
2. Provenance yearly phải đạt:
   - DMBT 2022-2026 có count `sourceSheetId` > 0 theo đúng gid.
3. `sourceSheetId NULL` phải giảm đáng kể so với baseline (1835 từ UAT 2026-05-07).
4. Monthly gids `1383308512`, `157327514`:
   - không còn tham gia flow sync/UI mặc định,
   - nhưng rollback path còn bật lại được bằng config.

---

## 5) Kết luận
- Monthly gid `1383308512` và `157327514` vẫn còn dính ở nhiều lớp: config sync, remote pull, repository merge, mapper/UI/filter, prefs và docs vận hành.
- Với UAT hiện tại từ Agent 1 còn FAIL yearly provenance, **chưa thể kết luận decommission PASS**.
- Khuyến nghị lượt sau: làm decommission theo cờ enable/disable (không xóa cứng), rồi UAT lại empty-app để xác nhận an toàn trước khi tuyên bố hoàn tất.

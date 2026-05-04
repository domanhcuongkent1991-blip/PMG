# AUDIT REPORT — Dự án DeviceTracker Android + Google Sheets

**Ngày audit:** 2026-05-03  
**Người thực hiện:** AI Agent (Audit Mode)  
**Phạm vi:** Toàn bộ repo `f:\codex_android_gsheet_full_pack`  
**Mục đích:** Đánh giá trạng thái dự án từ góc nhìn người non-tech, không sửa code, không thêm feature, không refactor.

---

## 1. TỔNG QUAN DỰ ÁN

### Dự án làm gì?
- **App Android** để field staff tra cứu và quản lý thiết bị bất thường (DMBT)
- **Local-first**: nhập liệu trên điện thoại trước, lưu offline, sync lên Google Sheet sau
- **4 Khu vực dữ liệu**: DMBT theo năm, DMBT theo tháng, Sửa chữa, HGT định kỳ
- **Tìm kiếm** xoay quanh `ma_thiet_bi` (mã thiết bị)

### Công nghệ sử dụng
| Lớp | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Kiến trúc | MVVM + Repository pattern |
| Database | Room (local) |
| Sync | WorkManager + Google Sheets API |
| DI | Hilt |
| Test | JUnit 4 + Android Testing |

### Số lượng files
| Loại | Số lượng |
|---|---|
| Kotlin source (main) | 82 |
| Unit test | 16 |
| Android test | 1 |
| Markdown docs | Nhiều |
| Scripts JS | 2 |

---

## 2. CÁC MODULE TRONG DỰ ÁN

### 2.1 Module Android App (`android-mvp/app/`)

```
android-mvp/app/
├── src/main/java/com/example/devicetracker/
│   ├── DeviceTrackerApp.kt          # Application class (Hilt)
│   ├── MainActivity.kt              # Entry point
│   ├── data/
│   │   ├── bootstrap/              # Seed data loader
│   │   ├── local/                  # Room DB, DAOs, Entities
│   │   ├── model/                  # Mappers
│   │   ├── remote/                 # Sheets API integration
│   │   ├── repository/             # Repository implementations
│   │   └── sheet/                  # Sheet config, contract, sync mode
│   ├── di/                         # Hilt modules
│   ├── domain/
│   │   ├── model/                  # Domain models
│   │   ├── repository/             # Repository interfaces
│   │   └── usecase/               # Use cases
│   ├── reminder/                   # HGT reminder (AlarmManager)
│   ├── ui/                         # Compose UI
│   │   ├── components/             # Reusable components
│   │   ├── detail/                # Detail screen
│   │   ├── edit/                   # Edit screen
│   │   ├── hgt/                   # HGT screen
│   │   ├── navigation/             # Nav graph
│   │   ├── repair/                # Repair update screen
│   │   ├── search/                 # Main search screen
│   │   ├── sync/                   # Sync status screen
│   │   └── theme/                 # Compose theme
│   ├── util/                       # Utilities
│   └── work/                      # WorkManager workers
```

### 2.2 Module Scripts (`scripts/`)

| File | Chức năng |
|---|---|
| `prevent-secrets.js` | Hook chặn commit nếu có secret pattern |
| `export-sheets-inventory-node.js` | Xuất inventory Google Sheets |

### 2.3 Module Documentation

| File | Mục đích |
|---|---|
| `input/prd.md` | Product Requirements Document |
| `DATA_MODEL.md` | Thiết kế database |
| `BUSINESS_RULES.md` | Quy tắc nghiệp vụ |
| `UI_UX_RULES.md` | Quy tắc giao diện |
| `SHEET_DATA_CONTRACT_MVP.md` | Contract đồng bộ Sheet |
| `GOOGLE_SHEET_UI_MAPPING_TEMPLATE.md` | Mapping UI ↔ Sheet |
| `TEST_PLAN.md` | Kế hoạch test |
| `UAT_CHECKLIST_SYNC_LOCAL_FIRST.md` | Checklist UAT |
| `CHECKLIST_Codex_MVP.md` | Checklist cho AI |
| `PROJECT_PLAN_TIGHTENED_V3_4_2026-04-26.md` | Project plan |
| `PROJECT_SUMMARY.md` | Tóm tắt dự án |

### 2.4 Module Governance (`governance/`)

| File | Mục đích |
|---|---|
| `STOP_THE_LINE_POLICY.md` | Chính sách dừng khi lỗi lặp |
| `WORKFLOW_ARTIFACT_POLICY.md` | Quy tắc artifact |
| `WORKFLOW_BASELINE_LOCK_LATEST.md` | Baseline lock |
| `STANDARD_AI_COMMANDS.md` | Commands chuẩn |
| `ONE_PAGE_OPERATIONS_GUIDE.md` | Hướng dẫn vận hành |
| `E2E_WORKFLOW_IMPROVEMENT_PLAN_2026-04-26.md` | Cải tiến E2E |
| `WORKFLOW_KPI_SCOREBOARD_TEMPLATE.md` | KPI template |

---

## 3. MODULE ỔN ĐỊNH ✅

### 3.1 Data Layer — Ổn định cao

| Component | Trạng thái | Ghi chú |
|---|---|---|
| `AppDatabase.kt` | ✅ Ổn | Room DB version 2, có exportSchema |
| `DeviceLogEntity.kt` | ✅ Ổn | Đầy đủ fields, có syncStatus |
| `HgtCheckEntity.kt` | ✅ Ổn | Chuẩn schema |
| `SyncQueueEntity.kt` | ✅ Ổn | Queue-based sync |
| `DeviceLogDao.kt` | ✅ Ổn | Đầy đủ queries |
| `HgtCheckDao.kt` | ✅ Ổn | CRUD operations |
| `SyncQueueDao.kt` | ✅ Ổn | Queue management |

### 3.2 Domain Layer — Ổn định cao

| Component | Trạng thái | Ghi chú |
|---|---|---|
| `DeviceLog.kt` | ✅ Ổn | Domain model hoàn chỉnh |
| `HgtCheck.kt` | ✅ Ổn | Domain model hoàn chỉnh |
| `RepairFilter.kt` | ✅ Ổn | Filter enum |
| `SyncOverview.kt` | ✅ Ổn | Overview model |
| Repository interfaces | ✅ Ổn | Clean abstraction |

### 3.3 Business Rules Implementation — Ổn định cao

| Rule | Implementation | Trạng thái |
|---|---|---|
| `record_id` unique | ✅ | UUID-based, kiểm soát trùng |
| `ma_thiet_bi` là required | ✅ | Validation trong repository |
| `ngay_sua_chua` → trạng thái | ✅ | Suy ra, không nhập tay |
| Không dùng STT làm ID | ✅ | Tuân thủ hoàn toàn |
| Offline-first | ✅ | Lưu local trước, sync sau |

### 3.4 Sync Worker — Ổn định khá

| Aspect | Trạng thái | Ghi chú |
|---|---|---|
| Push DMBT | ✅ | Idempotent theo record_id |
| Push HGT | ✅ | Upsert + delete |
| Pull DMBT | ✅ | Với retry timeout |
| Pull HGT | ✅ | Với fallback calculation |
| Error handling | ✅ | NonRetryable vs retryable |
| Timeout handling | ✅ | 180s cho mỗi step |

### 3.5 UI Components — Ổn định khá

| Screen | Trạng thái | Ghi chú |
|---|---|---|
| SearchScreen | ✅ | Tìm kiếm + filter + sort |
| DetailScreen | ✅ | Xem chi tiết |
| EditLogScreen | ✅ | Thêm/sửa bản ghi |
| HgtCheckScreen | ✅ | HGT management |
| SyncStatusScreen | ✅ | Sync overview + manual trigger |

### 3.6 Scripts — Ổn định

| Script | Trạng thái | Ghi chú |
|---|---|---|
| `prevent-secrets.js` | ✅ | Hook chặn secret hiệu quả |
| `export-sheets-inventory-node.js` | ✅ | Inventory export |

---

## 4. MODULE RỦI RO ⚠️

### 4.1 SheetConfig — Rủi ro trung bình

**Vấn đề:**
- Config tĩnh trong code, cần `local.properties`
- Không có config screen trong app
- OAuth refresh token flow chưa production-ready

**Ảnh hưởng:**
- Khi Sheet thay đổi cấu trúc, cần build lại app
- Không thể thay đổi sheet mapping khi runtime

**Khuyến nghị:**
- Ưu tiên: Giữ nguyên cho MVP vì đây là thiết kế có chủ đích
- Sau MVP: Thêm config screen hoặc DataStore

### 4.2 SheetsRemoteDataSource — Rủi ro trung bình

**Vấn đề:**
- Code dài (1180 lines), nhiều responsibility
- Retry logic phân tán ở nhiều tầng
- Không có integration test thực sự

**Ảnh hưởng:**
- Khó debug khi sync lỗi
- Khó thêm feature mới

**Khuyến nghị:**
- Đánh dấu là "đừng đụng vào" nếu không cần
- Test kỹ trước khi thay đổi

### 4.3 HGT Reminder System — Rủi ro trung bình

**Vấn đề:**
- Sử dụng AlarmManager + BroadcastReceiver
- Android 12+ có restriction mới
- Phụ thuộc boot receiver

**Ảnh hưởng:**
- Có thể không hoạt động trên một số Android version
- Battery optimization có thể block

**Khuyến nghị:**
- Test trên Android 12, 13, 14
- Có fallback notification

### 4.4 Date Handling — Rủi ro thấp-trung bình

**Vấn đề:**
- Nhiều format: `dd/MM/yyyy` (UI), `yyyy-MM-dd` (Sheet), Unix epoch
- `DateTextFormatter` xử lý nhiều format nhưng phức tạp

**Ảnh hưởng:**
- Có thể sai nếu Sheet đổi format

**Khuyến nghị:**
- Test kỹ với các date format khác nhau
- Khóa format contract

---

## 5. MODULE BỊ LỖI HOẶC THIẾU ❌

### 5.1 Unit Test Coverage — Thiếu nghiêm trọng

**Tình trạng:**
- Chỉ có 16 unit test files
- Không có test cho sync logic
- Không có test cho date parsing
- Không có test cho conflict resolution

**Ảnh hưởng:**
- Không biết regression khi thay đổi
- Khó refactor an toàn

**Khuyến nghị:**
- **Ưu tiên cao**: Thêm unit tests cho:
  - `DeviceLogRepositoryImpl.syncPending()`
  - `SheetsRemoteDataSource`
  - `DateTextFormatter`
  - `HgtDateCalculator`

### 5.2 Conflict Resolution — Thiếu

**Tình trạng:**
- Logic trong `DeviceLogRepositoryImpl`:
  - `shouldMarkAsSynced()` — so sánh updatedAt
  - `shouldApplyRemoteLog()` — so sánh updatedAt + syncStatus
- Không có UI để resolve conflict thủ công

**Ảnh hưởng:**
- Khi local và remote cùng thay đổi, "ai thắng" phụ thuộc updatedAt
- Không có visibility cho user khi conflict xảy ra

**Khuyến nghị:**
- Nếu MVP không cần conflict UI, tài liệu rõ behavior hiện tại
- Sau MVP: Thêm conflict resolution screen

### 5.3 Auth/Secrets Management — Thiếu

**Tình trạng:**
- OAuth tokens trong `local.properties`
- Không có mechanism để refresh token tự động trong production
- Không có encrypted storage cho secrets

**Ảnh hưởng:**
- Token expires → app không sync được
- Secrets có thể leak nếu không cẩn thận

**Khuyến nghị:**
- Dùng Android Keystore cho tokens
- Implement proper OAuth flow (hiện tại dùng refresh token manual)

---

## 6. MODULE KHÔNG NÊN ĐỤNG VÀO 🔒

### 6.1 `SheetsRemoteDataSource.kt` (1180 lines)

**Lý do:**
- Quá phức tạp
- Nhiều responsibility (push, pull, schema parse, OAuth)
- Không có test

**Nếu cần thay đổi:**
1. Viết test trước
2. Tách thành nhiều class nhỏ hơn
3. Test từng phần riêng biệt

### 6.2 `DeviceLogRepositoryImpl.kt` (303 lines)

**Lý do:**
- Sync logic phức tạp
- Nhiều side effects
- Quan trọng cho data integrity

**Nếu cần thay đổi:**
1. Viết test trước
2. Review kỹ conflict resolution logic

### 6.3 `DateTextFormatter.kt`

**Lý do:**
- Nhiều format parsing
- Dễ break nếu format Sheet thay đổi

---

## 7. PHẦN CẦN TEST LẠI 🔄

### 7.1 Core Flow Tests — Ưu tiên cao

| Test Case | Mục đích | Tình trạng |
|---|---|---|
| Search theo `ma_thiet_bi` | Tìm kiếm chính | Cần test |
| Filter đã sửa / chưa sửa | Lọc trạng thái | Cần test |
| Sidebar 4 khu | Điều hướng | Cần test |
| Thêm bản ghi mới | CRUD | Cần test |
| Cập nhật `ngay_sua_chua` | Update repair date | Cần test |
| HGT auto-calc | Tính `lan_tiep_theo` | Cần test |

### 7.2 Sync Flow Tests — Ưu tiên cao

| Test Case | Mục đích | Tình trạng |
|---|---|---|
| Offline save → pending sync | Local-first | Cần test |
| Online → sync thành công | Push lên Sheet | Cần test |
| Sync thất bại → retry | Error handling | Cần test |
| Schema mismatch → fail rõ | NonRetryable | Cần test |

### 7.3 Integration Tests — Ưu tiên trung bình

| Test Case | Mục đích | Tình trạng |
|---|---|---|
| Push/Pull DMBT 2 chiều | Full sync cycle | Chưa có |
| Push/Pull HGT 2 chiều | Full sync cycle | Chưa có |
| Read-only DMBT sheets | Pull nhiều sheet | Chưa có |

### 7.4 Edge Case Tests — Ưu tiên trung bình

| Test Case | Mục đích | Tình trạng |
|---|---|---|
| Duplicate `record_id` | Khóa trùng | Cần test |
| Empty `ma_thiet_bi` | Validation | Cần test |
| Invalid date format | Error handling | Cần test |
| Token expires | Auth refresh | Cần test |

---

## 8. PHẦN LIÊN QUAN GOOGLE SHEET 📊

### 8.1 Đã Implement

| Role | Mode | File liên quan |
|---|---|---|
| `DMBT_LOG` | TWO_WAY | `SheetConfig.kt`, `SheetsRemoteDataSource.kt` |
| `HGT_CHECKS` | TWO_WAY | `SheetConfig.kt`, `SheetsRemoteDataSource.kt` |
| Multi-sheet DMBT | Can chuyen sang TWO_WAY theo PRD moi | Code hien tai van co logic read-only legacy; can sua de ghi nguoc dung source sheet |
| `DEVICE_MASTER` | INVENTORY_ONLY | `SheetConfig.kt` |
| `LOOKUP_OPTIONS` | INVENTORY_ONLY | `SheetConfig.kt` |
| `APP_CONFIG` | INVENTORY_ONLY | `SheetConfig.kt` |

### 8.2 Contract Columns

**DMBT_LOG:**
- `record_id` (required)
- `ma_thiet_bi` (required)
- `hang_muc`, `nguoi_bao_cao`, `tinh_trang_thiet_bi`, `ktv_phu_trach`
- `ngay_phat_hien` (required)
- `ngay_sua_chua` (optional)
- `ghi_chu`
- `updated_at` (Unix epoch)

**HGT_CHECKS:**
- `ma_thiet_bi` (required)
- `chu_ky_ngay` (required, > 0)
- `lan_gan_nhat` (required)
- `lan_tiep_theo` (calculated)

### 8.3 Sync Safety Rules

✅ Đã implement:
- Push idempotent theo `record_id`
- Fail non-retryable khi schema lỗi
- Skip malformed rows thay vì fail all
- Timeout cho mỗi sync step
- Dry-run validation

### 8.4 Cần Xác Nhận

| Câu hỏi | Tầm quan trọng |
|---|---|
| Sheet nào là primary cho DMBT? | Cao |
| Header row ở dòng nào trong mỗi tab? | Cao |
| Format ngày trong Sheet là gì? | Cao |
| Có cần sync ngược khi Sheet thay đổi không? | Trung |

---

## 9. PHẦN LIÊN QUAN EXCEL 📁

### 9.1 Tình trạng Excel

**Kết luận: Dự án KHÔNG liên quan Excel**

- Không có code đọc/ghi Excel
- Không có script import/export Excel
- Chỉ làm việc với Google Sheets
- Dữ liệu nằm hoàn toàn trên Google Sheets

### 9.2 Nếu Cần Thêm Excel Support

Các lựa chọn:
1. **POI Apache** — thư viện Java/Android
2. **Apache Commons CSV** — cho CSV thay vì Excel
3. **Manual export** — từ Google Sheets ra CSV/Excel

---

## 10. PHẦN LIÊN QUAN UI MOBILE 📱

### 10.1 Screens Đã Implement

| Screen | Route | Chức năng |
|---|---|---|
| Search | `/` | Tìm kiếm + filter + sort |
| Detail | `/detail/:recordId` | Xem chi tiết bản ghi |
| Edit | `/edit/:recordId?` | Thêm/sửa bản ghi |
| Update Repair | `/update-repair/:recordId` | Cập nhật ngày sửa |
| HGT | `/hgt` | Quản lý HGT định kỳ |
| Sync Status | `/sync` | Trạng thái sync + manual trigger |

### 10.2 UI Architecture

```
MainActivity
└── NavHost
    ├── SearchScreen (default)
    ├── DetailScreen
    ├── EditLogScreen
    ├── UpdateRepairDateScreen
    ├── HgtCheckScreen
    └── SyncStatusScreen
```

### 10.3 Sidebar Navigation

Sidebar chứa 4 khu vực:
- **Khu 1**: DMBT theo năm (2022-2026)
- **Khu 2**: DMBT theo tháng (T4.2026...)
- **Khu 3**: Sửa chữa theo tháng
- **Khu 4**: HGT định kỳ

### 10.4 UI Components

| Component | Mục đích |
|---|---|
| `DeviceLogCard` | Card hiển thị bản ghi |
| `StatusBadge` | Badge trạng thái sửa chữa |

### 10.5 Theme

| File | Mục đích |
|---|---|
| `Color.kt` | Bảng màu Material 3 |
| `Theme.kt` | Light/Dark theme |
| `Type.kt` | Typography |

---

## 11. WORKFLOW TẠI F:\CODEX-AUTO

### 11.1 Cấu trúc

```
F:\CODEX-AUTO\
├── codex-skill-store-v5.0.3-*/     # Skills store
├── PLAN-WORKFLOW-CODEX-PLAN-ONLY-V3.4.0/  # Workflow plan
│   └── PLAN-WORKFLOW/
│       ├── VERSION
│       ├── workflow_manifest.json
│       └── *.md
├── AUTO-MCP/                       # MCP server
│   └── workflow/
│       └── config/
│           ├── decision-matrix.json
│           ├── intent-classifier.json
│           ├── stability-controls.json
│           ├── project-profiles.json
│           └── project-registry.json
└── (other components)
```

### 11.2 Workflow hiện tại

**Điểm mạnh:**
- ✅ Có artifact policy rõ ràng
- ✅ Có stop-the-line policy
- ✅ Có skill store versioned
- ✅ Có workflow manifest
- ✅ Có MCP intent classification
- ✅ Có stability controls

**Điểm cần cải thiện:**
- ⚠️ Workflow khá phức tạp cho người non-tech
- ⚠️ Nhiều config files cần maintain
- ⚠️ Không có visual representation của workflow
- ⚠️ Không có automated workflow validation

### 11.3 Đánh giá Workflow

| Khía cạnh | Điểm (1-10) | Ghi chú |
|---|---|---|
| Governance | 9 | Stop-the-line, artifact policy tốt |
| Automation | 7 | MCP + skills store, nhưng phức tạp |
| Documentation | 8 | Đầy đủ docs |
| Usability | 6 | Phức tạp cho non-tech |
| Maintainability | 7 | Nhiều version cần track |

### 11.4 Khuyến nghị Workflow

**Cần sửa đổi? KHÔNG — Workflow ổn định**

Tuy nhiên, nếu muốn cải thiện:
1. Thêm diagram cho workflow
2. Simplified entry point cho người mới
3. Checklist ngắn gọn trong README

---

## 12. HIỆU QUẢ GSD FRAMEWORK

### 12.1 Ưu điểm của GSD

| Khía cạnh | Đánh giá |
|---|---|
| Phase-based planning | ✅ Rõ ràng, có milestone |
| Skill routing | ✅ Tự động route đến skill phù hợp |
| Artifact tracking | ✅ Đầy đủ docs và contracts |
| Governance gates | ✅ Có stop-the-line policy |
| Profile system | ✅ quality/balanced/budget profiles |

### 12.2 Nhược điểm

| Khía cạnh | Vấn đề |
|---|---|
| Cognitive load | Cao — nhiều skills, nhiều files |
| Tool fatigue | Có — quá nhiều lệnh `$gsd-*` |
| Context switching | Nhiều — giữa skills và main agent |
| Non-tech friendly | Trung bình — nhiều khái niệm |

### 12.3 Có gây ra vấn đề không?

**KHÔNG — GSD đã phát huy tác dụng**

Tuy nhiên, có một số điểm cần lưu ý:

1. **Quá nhiều skills** — 80+ skills, người dùng không biết dùng cái nào
2. **Workflow phức tạp** — nhiều bước cho một task đơn giản
3. **Docs trùng lặp** — nhiều file có nội dung tương tự

### 12.4 Khuyến nghị GSD

1. **Dùng `$gsd-next`** để tự động advance thay vì tự chọn command
2. **Dùng `$gsd-progress`** để xem context trước khi hỏi
3. **Dùng `$gsd-fast`** cho task nhỏ, không cần full workflow
4. **Tạo cheatsheet** cho các lệnh hay dùng

---

## 13. KẾT LUẬN

### 13.1 Có nên code tiếp không?

**CÓ — nhưng cần chuẩn bị trước**

| Yếu tố | Trạng thái |
|---|---|
| Core logic ổn định | ✅ Có |
| Business rules đúng | ✅ Có |
| Sync 2 chiều cơ bản | ✅ Có |
| UI core hoàn thành | ✅ Có |
| Test coverage đủ | ❌ Không |
| Conflict resolution UI | ❌ Thiếu |

### 13.2 Những gì cần sửa trước khi tiếp tục

#### Ưu tiên cao (trước khi thêm feature):

1. **Thêm unit tests cho sync logic**
   - File: `DeviceLogRepositoryImpl.kt`
   - Lý do: Tránh regression khi thay đổi

2. **Tài liệu rõ conflict resolution behavior**
   - Hiện tại: `updatedAt` wins
   - Cần xác nhận với user

3. **Test E2E trên thiết bị thật**
   - Offline → save → online → sync
   - Đã có checklist trong `UAT_CHECKLIST_SYNC_LOCAL_FIRST.md`

#### Ưu tiên trung bình (sau khi ổn định):

1. **Thêm integration tests**
2. **Cải thiện error messages trong sync**
3. **Thêm config screen cho Sheet mapping**

### 13.3 Tóm tắt

| Phân loại | Số lượng |
|---|---|
| Module ổn định | ~15 |
| Module rủi ro | ~5 |
| Module lỗi/thiếu | ~3 |
| Module cần test lại | ~10 |

### 13.4 Đánh giá tổng thể

| Khía cạnh | Điểm (1-10) |
|---|---|
| Code quality | 8 |
| Documentation | 8 |
| Architecture | 8 |
| Test coverage | 4 |
| Sync reliability | 7 |
| UI completeness | 8 |

**Tổng điểm: 7.2/10 — Khá tốt cho MVP**

---

## 14. GHI CHÚ SAU ĐỐI CHIẾU CODE — 2026-05-03

### 14.1 Phạm vi đối chiếu

Phần này được bổ sung sau khi đọc lại code thật trong `android-mvp/` và chạy kiểm tra cơ bản.

Đã kiểm tra:
- `node --version`, `npm --version`, `npx --version`, `git --version`: đủ công cụ.
- `input/prd.md`: vẫn là file mẫu, chưa có yêu cầu sản phẩm thật.
- `android-mvp`: build và unit test cơ bản.
- `docs/audit/AUDIT.md`: đối chiếu lại các nhận định trong audit với code hiện tại.

Kết quả kiểm tra kỹ thuật:
- `testDebugUnitTest`: PASS, 54 tests, 0 lỗi.
- `assembleDebug`: PASS, APK debug build được.

Lưu ý bảo mật:
- Không đọc `android-mvp/local.properties` vì file này có thể chứa token/secret.

---

### 14.2 Các mục CẦN SỬA

| Mục | Có cần sửa? | Mức ưu tiên | Lý do dễ hiểu | Hướng sửa an toàn |
|---|---:|---|---|---|
| `input/prd.md` còn là file mẫu | Có | Cao | Đây là "bản yêu cầu gốc" của dự án. Nếu file này chưa rõ, người làm sau dễ hiểu sai mục tiêu. | Viết lại PRD thật: app dùng cho ai, 3 tính năng quan trọng nhất, chỗ không được phép sai, tiêu chí Done. |
| Auth/secret cho Google Sheets | Có | Cao | Token/secret đang đi qua `local.properties` và `BuildConfig` debug. Cách này dùng tạm được khi dev, nhưng chưa an toàn cho production. | Sau MVP, chuyển token sang Android Keystore hoặc EncryptedSharedPreferences; không đóng gói secret thật vào APK. |
| Test sync thật với Google Sheet | Có | Cao | Unit test hiện pass nhưng chủ yếu kiểm tra logic nhỏ. Chưa đủ chứng minh toàn bộ luồng app ↔ Google Sheet chạy an toàn. | Thêm integration/E2E test với Google Sheet test riêng: push, pull, schema sai, token hết hạn, retry. |
| UAT trên thiết bị thật | Có | Cao | Android notification, WorkManager, mạng yếu, battery optimization chỉ test trên máy thật mới đáng tin. | Chạy checklist `UAT_CHECKLIST_SYNC_LOCAL_FIRST.md` trên Android thật. Ghi lại bằng chứng log/screenshot. |
| Conflict resolution behavior | Có | Cao | Code đang dùng rule `updatedAt` và `syncStatus`; nếu app và Sheet cùng sửa, cần ghi rõ ai thắng để tránh hiểu nhầm là app có màn xử lý xung đột. | Tài liệu hóa rule hiện tại trước. Sau MVP mới cân nhắc màn hình xử lý conflict thủ công. |
| HGT reminder trên Android 12/13/14 | Có | Trung bình | Code đã có fallback, nhưng nhắc lịch có thể bị chặn bởi quyền notification, exact alarm hoặc tiết kiệm pin. | Test trên Android 12, 13, 14; ghi rõ trường hợp bật/tắt permission và sau khi reboot máy. |
| Error message sync cho người non-tech | Có | Trung bình | Một số lỗi kỹ thuật có thể vẫn khó hiểu với người dùng cuối. | Chuẩn hóa thông báo: "thiếu quyền Sheet", "token hết hạn", "sai cột Sheet", "mất mạng". |
| Backup/restore plan | Có | Trung bình | Dữ liệu local-first cần kế hoạch dự phòng để tránh mất dữ liệu khi đổi máy/gỡ app. | Viết hướng dẫn backup dữ liệu local và Google Sheet trước khi deploy thật. |

---

### 14.3 Các mục KHÔNG CẦN SỬA NGAY

| Mục | Không cần sửa ngay vì sao? | Điều kiện để sau này mới sửa |
|---|---|---|
| Refactor lớn `SheetsRemoteDataSource.kt` | File dài và rủi ro, nhưng hiện build pass, test pass, đã có xử lý token refresh, timeout, schema validation và lỗi HTTP dễ hiểu hơn. Sửa lớn lúc này dễ làm hỏng sync. | Chỉ refactor sau khi có test bao quanh push/pull/schema/auth. |
| Thêm config screen cho Sheet mapping | MVP đang dùng config tĩnh để giảm rủi ro người dùng nhập sai. Với người non-tech, config screen có thể làm app dễ sai hơn nếu chưa có guardrail mạnh. | Làm sau MVP khi cần đổi Sheet runtime thường xuyên. |
| Thêm Excel support | Dự án hiện chỉ làm Google Sheets. Thêm Excel sẽ mở thêm phạm vi mới, không giúp ổn định MVP. | Chỉ làm nếu user xác nhận có quy trình Excel thật sự. |
| Thay kiến trúc data/domain/UI core | Kiến trúc hiện tại đã hợp lý: Compose, MVVM, Repository, Room, WorkManager, Hilt. Build và unit test pass. | Chỉ sửa khi có bug cụ thể hoặc yêu cầu sản phẩm mới. |
| Thay toàn bộ GSD/workflow | Workflow hơi phức tạp nhưng không chặn app chạy. Đây là vấn đề usability, không phải lỗi sản phẩm trực tiếp. | Cải thiện bằng cheatsheet ngắn thay vì đổi workflow lớn. |
| Làm conflict resolution UI ngay | Audit nói thiếu UI conflict là đúng, nhưng với MVP nên ưu tiên tài liệu hóa rule và test mất dữ liệu trước. | Làm UI conflict nếu nhiều người sửa cùng lúc trên app và Sheet là nhu cầu thật. |
| Đổi toàn bộ date handling | `DateTextFormatter` đã có test cơ bản và hỗ trợ `dd/MM/yyyy`, `yyyy-MM-dd`. Không nên đổi toàn bộ khi chưa có lỗi cụ thể. | Bổ sung test edge cases trước; chỉ đổi format nếu contract Sheet thay đổi. |

---

### 14.4 Các nhận định trong audit cần điều chỉnh

| Nhận định audit ban đầu | Trạng thái sau đối chiếu | Ghi chú |
|---|---|---|
| "Không có test cho sync logic" | Đúng một phần | Có test nhỏ cho rule sync/conflict trong `DeviceLogRepositorySyncRulesTest.kt`, nhưng chưa có integration test sync thật với Google Sheet. |
| "Không có test cho date parsing" | Không còn đúng hoàn toàn | Có `DateTextFormatterTest.kt`. Tuy nhiên vẫn nên bổ sung thêm edge cases nếu Sheet có format lạ. |
| "OAuth refresh token flow chưa production-ready" | Đúng một phần | Code đã có flow refresh token trong `SheetsRemoteDataSource.kt`, nhưng secret storage/login flow vẫn chưa production-ready. |
| "HGT reminder cần fallback notification" | Đã có một phần | Code đã có notification receiver và fallback alarm. Vẫn cần test trên Android thật. |
| "Không nên đụng `SheetsRemoteDataSource.kt`" | Đúng | Đây là file nhạy cảm. Chỉ nên sửa khi có test rõ ràng hoặc bug cụ thể. |
| "Workflow cần sửa đổi? KHÔNG" | Đồng ý | Không nên sửa workflow lớn lúc này; chỉ nên thêm hướng dẫn ngắn cho người non-tech. |

---

### 14.5 Phương án hoàn thiện an toàn nhất

Thứ tự nên làm:

1. **Chốt PRD thật**
   - Điền lại `input/prd.md`.
   - Đây là bước đầu tiên vì nó quyết định "làm xong" nghĩa là gì.

2. **Khóa rule dữ liệu và conflict**
   - Ghi rõ local pending có được remote ghi đè không.
   - Ghi rõ khi Google Sheet bị sửa tay thì app xử lý thế nào.
   - Ghi rõ sai schema thì app phải dừng, không sync mù.

3. **Bổ sung test trước khi thêm feature**
   - Test `syncPending()`.
   - Test push/pull DMBT.
   - Test push/pull HGT.
   - Test token hết hạn.
   - Test Sheet thiếu cột.

4. **Chạy UAT trên máy Android thật**
   - Offline → lưu local → mở lại app.
   - Online → sync lên Sheet.
   - Sửa liên tiếp cùng bản ghi khi sync đang chạy.
   - HGT reminder.

5. **Làm cứng bảo mật**
   - Không để secret thật trong APK production.
   - Dùng Android Keystore hoặc EncryptedSharedPreferences.
   - Có hướng dẫn setup `.env.example`/`local.properties.example`, không commit secret thật.

6. **Sau khi ổn định mới refactor**
   - Tách `SheetsRemoteDataSource.kt` nếu cần.
   - Thêm config screen nếu thật sự cần đổi Sheet trong app.

Kết luận sau đối chiếu:

**Dự án chưa nên thêm feature lớn ngay. Việc nên làm trước là khóa yêu cầu, tăng test sync/E2E, kiểm thử thiết bị thật, rồi mới xử lý bảo mật production.**

---

## PHỤ LỤC: CHECKLIST TRƯỚC KHI TIẾP TỤC

```markdown
□ Test coverage > 70% cho sync logic
□ E2E test trên thiết bị thật pass
□ Conflict resolution behavior được document
□ Sync error messages đã review
□ HGT reminder hoạt động trên Android 12+
□ Token refresh flow đã test
□ Read-only DMBT sheets đã verify
□ Backup/restore plan đã có
```

---

*Báo cáo này được tạo bởi AI Agent (Audit Mode) — không sửa code, không thêm feature.*
---

## 15. GHI CHU THUC HIEN PLAN SYNC - 2026-05-03

### 15.1 Muc da sua trong code

| Muc | Trang thai | Ly do |
|---|---|---|
| `Sua chua T4.2026` chua co role/config rieng | Da sua mot phan an toan | Da them `SheetRole.DMBT_REPAIR_LOG`, `SHEETS_REPAIR_LOG_SHEET_ID`, contract cot bat buoc va policy `TWO_WAY`. Viec nay giup app khong dung nham gid/role cua DMBT khi chuan bi sync sheet sua chua. |
| Risk trung gid giua DMBT write target va repair sheet | Da co test chan | Da them test phat hien neu `DMBT_LOG` va `DMBT_REPAIR_LOG` cung tro vao mot gid. Day la loi nguy hiem vi co the ghi sai tab. |
| Registry chua biet `Sua chua T4.2026` la sheet hai chieu | Da sua | Theo xac nhan cua user, sheet nay duoc pull va ghi nguoc, nen registry dat mode `TWO_WAY`. |

### 15.2 Muc chua nen sua tiep trong lap nay

| Muc | Chua sua vi sao? | Dieu kien de sua tiep |
|---|---|---|
| Push/pull network that cho `Sua chua T4.2026` | Chua co inventory header cua sheet test copy, neu doan schema sai co the ghi sai cot hoac sai tab. | Tao workbook test, lay header that, viet mapper/parser test, pass release gate roi moi bat ghi that. |
| Ghi nguoc vao production Google Sheet | Chua an toan neu chua qua workbook test gate. | Unit test pass, build pass, workbook test UAT pass, co evidence row/record_id. |
| Refactor lon `SheetsRemoteDataSource.kt` | File nhay cam, dang build/test pass; refactor lon luc nay tang rui ro. | Chi tach nho parser/helper khi co failing test cu the. |

### 15.3 Ket qua verify moi nhat

- `testDebugUnitTest`: PASS / `BUILD SUCCESSFUL`.
- `assembleDebug`: PASS / `BUILD SUCCESSFUL`.
- Canh bao Kotlin daemon va Android metrics van la canh bao moi truong sandbox; khong phai loi code neu dong cuoi la `BUILD SUCCESSFUL`.
- `node scripts\prevent-secrets.js` van can chay lai trong terminal binh thuong truoc commit vi sandbox co van de Node child_process goi git.

---

## 16. CAP NHAT YEU CAU SAN PHAM MOI - 2026-05-03

User da mo ta lai ung dung va chot lai cac diem sau:

- Tat ca 8 sheet trong scope deu can dong bo hai chieu, bao gom `DMBT 2022`, `DMBT 2023`, `DMBT 2024`, `DMBT 2025`, `DMBT 2026`, `DMBT T4.2026`, `Sua chua T4.2026`, `HGT dinh ky`.
- `DMBT T4.2026` va `Sua chua T4.2026` la ten theo thang, nen code khong duoc phu thuoc vao title co dinh.
- Tra cuu dung `ma_thiet_bi`.
- Trang thai sua chua suy ra tu `ngay_sua_chua`: trong la chua sua, co ngay la da sua.
- App phai nhap offline duoc va day len Google Sheet khi co mang sau khi bam dong bo day du.
- Du lieu co the len den khoang 10.000 loi, nen can kiem tra hieu nang.

Tac dong den audit:

| Muc | Trang thai moi | Ly do |
|---|---|---|
| DMBT 2022..2026 `PULL_ONLY` | Khong con dung | User da chot tat ca sheet nay deu co the cap nhat qua lai. |
| `SHEETS_DMBT_READONLY_SHEET_IDS` | Legacy, can thay | Ten key va behavior khong con khop PRD moi. |
| Mot DMBT write target duy nhat | Khong du yeu cau | Ban ghi sua tu sheet nao phai ghi nguoc dung sheet do; ban ghi moi moi dung default current-month sheet. |
| Dynamic sheet title | Can sua trong plan/code | Ten theo thang co the doi, nen phai dua vao gid/config/role thay vi title co dinh. |

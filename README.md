# PMG

DeviceTracker Android + Google Sheets Full Pack.

Du an gom 2 phan chinh:

1. Bo tai lieu nen cho Codex:
   - AGENTS.md
   - MASTER_PROMPT.txt
   - checklist, business rules, data model, UI/UX rules, sync rules, test plan

2. Khung project Android MVP trong thu muc `android-mvp/`:
   - Kotlin
   - Jetpack Compose
   - MVVM
   - Repository pattern
   - Room local database
   - WorkManager sync queue
   - Hilt dependency injection
   - Google Sheets remote integration

## Muc Tieu Kien Truc

- App hoat dong theo huong local-first: nhap du lieu vao local DB truoc.
- Khi co mang, WorkManager day du lieu cho sync len Google Sheet.
- Tim kiem xoay quanh `ma_thiet_bi`.
- Trang thai sua chua duoc suy ra tu `ngay_sua_chua`.

## Ghi Chu

- Khong commit file secret hoac `local.properties`.
- Cau hinh that nen nam trong file local rieng tren may phat trien.
- Xem `input/prd.md` de biet yeu cau san pham chinh thuc.

# Google Sheets Sync Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoan thien dong bo an toan giua app Android DeviceTracker va cac tab Google Sheets da duoc nguoi dung danh dau trong anh.

**Architecture:** Giu local-first lam trung tam: UI doc/ghi Room truoc, sync worker day/keo du lieu sau. Google Sheets chi duoc truy cap qua `sheetId/gid`, role va contract cot; khong quet workbook tuy tien. Theo PRD cap nhat ngay 2026-05-03, ca 8 sheet trong scope deu can dong bo `TWO_WAY`, nhung chi bat tren workbook that sau khi controlled production test pass.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Hilt, Google Sheets API, JUnit 4, Android instrumented tests.

## Stability Upgrade V4 - 2026-05-03

Tra loi cau hoi "co chac on dinh, an toan, khong co loi khong?":

- Khong the cam ket tuyet doi "khong co loi" khi chua chay sync that, controlled production test, va UAT tren may Android that.
- Co the lam phuong an tot hon bang cach bien moi rui ro thanh mot gate ro rang: test truoc, fail ro, rollback nhanh, khong ghi workbook that khi chua pass.
- Tu V4 tro di, khong duoc bat `TWO_WAY` production chi vi unit test pass. Unit test pass moi la muc 1.

### Cac nang cap bat buoc cua V4

| Nang cap | Ly do | Cach kiem tra |
|---|---|---|
| Source sheet identity | Ban ghi sua tu sheet nao phai ghi nguoc dung sheet do, tranh ghi het vao mot tab | Unit test `sourceSheetId`, UAT sua ban ghi tu `DMBT 2022` roi xac nhan chi `DMBT 2022` doi |
| Stable record id | Retry sync khong duoc tao duplicate hang loat | Test retry 2 lan cung payload, Google Sheet chi co 1 row |
| Optimistic conflict check | Neu app va Sheet cung sua mot ban ghi, khong duoc am tham ghi de du lieu moi hon | Test local `PENDING` khong bi remote overwrite; test remote newer chi ap dung khi local `SYNCED` |
| Schema fingerprint | Doi/bo cot trong Sheet phai fail ro, khong sync mu | Test thieu `ma_thiet_bi`, `ngay_sua_chua`, `updated_at` |
| Batch/chunk sync | 10.000 ban ghi khong duoc lam app treo | Test parser/merge voi 10.000 rows, do thoi gian va bo nho o muc chap nhan duoc |
| Dry-run write preview | Truoc khi ghi controlled production test/that, app hoac script phai biet se ghi tab nao, bao nhieu row | Log redacted: sheetId, count, operation; khong log token |
| Kill switch | Neu co loi sync that, tat ghi nguoc ngay ma khong xoa local DB | Config tat `TWO_WAY`, giu local data |
| Evidence log | Moi lan pass/fail phai co bang chung de truy vet | Ghi vao `WORKLOG_YYYY-MM-DD.md` lenh, ket qua, sheetId, record_id test |

### Phuong an cai tien sau V4

1. **Config layer an toan truoc** - DONE local slice 2026-05-03
   - Tao model `DmbtSheetBinding(sheetId, roleLabel, mode, isDefaultCreateTarget)`.
   - Doi concept `SHEETS_DMBT_READONLY_SHEET_IDS` sang `SHEETS_DMBT_SHEET_IDS`.
   - Chua dung workbook that.
   - Da them BuildConfig `SHEETS_DMBT_SHEET_IDS`, `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`.
   - Da them `SheetConfig.DmbtSheetBinding`, `dmbtSheetBindings`, `dmbtDefaultCreateSheetId`.
   - Da giu fallback legacy tu `SHEETS_DMBT_LOG_SHEET_ID` va `SHEETS_DMBT_READONLY_SHEET_IDS` de app khong bi gay dot ngot.

2. **Local data identity** - PARTIAL local slice 2026-05-03
   - Them cach luu `sourceSheetId` cho ban ghi DMBT.
   - Neu ban ghi tao moi tren app thi dung `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`.
   - Neu ban ghi keo tu sheet nao thi update nguoc sheet do.
   - Da them `sourceSheetId` nullable vao `DeviceLog`, `DeviceLogEntity`.
   - Da them Room migration 2 -> 3 va schema `3.json`.
   - Da gan `sourceSheetId = sheetId` khi pull DMBT tu Google Sheet.
   - Chua doi push engine de ghi nguoc theo `sourceSheetId`; day la buoc tiep theo.

3. **Parser/mapper tach khoi network**
   - Tach parser DMBT/HGT/repair ra helper test duoc offline.
   - Test schema, ngay sua chua, record id, updated_at, 10.000 rows.

4. **Sync engine theo batch**
   - Pull/push theo tung sheet, tung chunk.
   - Moi chunk co ket qua ro: success/fail/skipped.
   - Loi o sheet nao phai bao sheetId/tab do, khong bao thanh cong gia.

5. **Conflict policy ro**
   - Local `PENDING`/`FAILED` thang remote pull de tranh mat du lieu offline.
   - Remote chi cap nhat local khi local da `SYNCED` va remote moi hon.
   - Truong hop xung dot khong ro thi giu local, bao can kiem tra.

6. **controlled production test gate**
   - Copy workbook that thanh controlled production test.
   - Test tao moi, sua ngay sua, sua tu Sheet keo ve app, retry, schema sai.
   - Chi khi controlled production test pass moi chuyen sang workbook that.

7. **Performance gate**
   - Tao dataset test 10.000 rows.
   - Do thoi gian parse/search/sync planning.
   - Neu UI cham, them paging/index/luong nen truoc khi UAT.

8. **Production gate va rollback**
   - Truoc production: unit test pass, build pass, secret guard pass trong terminal that, UAT pass.
   - Sau production: co cach tat ghi nguoc nhanh, khong xoa local DB.

Ket luan V4:

Day la phuong an tot hon 6 muc cu vi no khong chi noi "lam sync", ma dat cac cong chan loi truoc khi du lieu that bi anh huong.

---

## Plan Hardening V2 — 2026-05-03

Ly do sua plan:
- Muc tieu cua user la "an toan, on dinh, khong co loi".
- Khong the cam ket tuyet doi "khong co loi" neu chua sync that va UAT may that.
- Cach tot nhat la them gate bat buoc de loi khong di vao du lieu that.

Thay doi chinh trong V2:
- Tach ro controlled production test va workbook that.
- Khong bat `TWO_WAY` tren workbook that cho den khi UAT controlled production test pass.
- `Sua chua T4.2026` phai co contract/mapper/config rieng truoc khi ghi nguoc.
- Them rollback plan.
- Them release gate voi tieu chi pass/fail ro rang.
- Them evidence log bat buoc cho moi lan verify.

---

## Requirement Correction V3 — 2026-05-03

User da mo ta lai ung dung va thay doi mot gia dinh quan trong:

- Tat ca 8 sheet `DMBT 2022`, `DMBT 2023`, `DMBT 2024`, `DMBT 2025`, `DMBT 2026`, `DMBT T4.2026`, `Sua chua T4.2026`, `HGT dinh ky` deu can dong bo hai chieu.
- Plan cu coi cac sheet DMBT lich su la `PULL_ONLY`. Gia dinh do khong con dung.
- `DMBT T4.2026` va `Sua chua T4.2026` la ten theo thang, co the doi thanh `DMBT T5.2026`, `Sua chua T5.2026`. Code khong duoc phu thuoc vao ten co dinh.
- User se tu doi ten sheet theo thang trong Google Sheets. Neu chi rename tab hien co, `gid/sheetId` giu nguyen nen app phai van sync duoc bang gid.
- User se doi ten tab cu khi sang thang moi, vi du `DMBT T4.2026` thanh `DMBT T5.2026`; `gid/sheetId` giu nguyen.
- User cho phep test truc tiep tren Google Sheet that, khong can tao controlled production test copy. De giu an toan, moi test ghi that phai co dry-run, ghi sheetId/record_id, va chi ghi tap nho co kiem soat.
- Voi DMBT 2022..2025, thao tac cap nhat chinh la dien ngay sua chua va ghi chu de danh dau da sua; khong xoa row.
- Trang thai da sua/chua sua phai dua vao cot ngay sua chua: trong la chua sua, co ngay la da sua.
- Du lieu co the len den 10.000 loi, nen can kiem soat hieu nang tra cuu va sync.

He qua voi plan:

- Phai doi tu concept "read-only DMBT sheet ids" sang "configured DMBT sheet ids with sync mode".
- Moi ban ghi DMBT can biet no thuoc sheet nao de push nguoc dung tab.
- Khong duoc day moi moi ban ghi DMBT vao mot sheet duy nhat neu ban ghi do den tu sheet khac.
- Vẫn giữ release gate: hai chieu tren workbook that chi bat sau khi controlled production test pass.

---

## Scope Da Chot Tu Anh

Nguoi dung da danh dau o do trong Google Sheets. Cac tab sau phai nam trong pham vi dong bo vao app:

| Tab | gid | Mode da chot | Ly do |
|---|---|---|
| `DMBT 2022` | `849979183` | `TWO_WAY` | User da cap nhat yeu cau: sheet nay cung co the chinh sua qua lai |
| `DMBT 2023` | `1783863163` | `TWO_WAY` | Hai chieu |
| `DMBT 2024` | `1224276666` | `TWO_WAY` | Hai chieu |
| `DMBT 2025` | `989601207` | `TWO_WAY` | Hai chieu |
| `DMBT 2026` | `1607125070` | `TWO_WAY` | Hai chieu |
| `DMBT T4.2026` | `1383308512` | `TWO_WAY` | Ten sheet thay doi theo thang, khong duoc hard-code ten |
| `Sua chua T4.2026` | `157327514` | `TWO_WAY` | Ten sheet thay doi theo thang; can support contract/mapper rieng neu schema khac DMBT |
| `HGT dinh ky` | `57428884` | `TWO_WAY` | App da co luong HGT |

Quyet dinh da chot ngay 2026-05-03:
- Tat ca sheet DMBT trong scope deu can hai chieu; app phai push nguoc dung sheet nguon cua ban ghi.
- Sheet sua chua: `Sua chua T4.2026` (`gid=157327514`) duoc keo ve va ghi nguoc.
- Sheet HGT: `HGT dinh ky` (`gid=57428884`) duoc sync 2 chieu sau dry-run pass.

---

## File Structure

### Files se can sua
- `android-mvp/local.properties.example`: them comment mau cho 8 tab trong anh, khong them secret.
- `android-mvp/SYNC_SETUP.md`: huong dan lay `gid` cho tung tab va dien vao local config.
- `SHEET_DATA_CONTRACT_MVP.md`: giu danh sach tab scope va mode sync da chot.
- `SYNC_RULES.md`: giu rule local-first, conflict, pull-only/two-way.
- `UAT_CHECKLIST_SYNC_LOCAL_FIRST.md`: checklist test tren may that.
- `android-mvp/app/build.gradle.kts`: neu can them BuildConfig cho nhom sheet moi.
- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`: parse cau hinh multi-sheet an toan.
- `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetContract.kt`: them role/contract neu can tach sheet sua chua.
- `android-mvp/app/src/main/java/com/example/devicetracker/data/remote/SheetsRemoteDataSource.kt`: chi sua sau khi co test, uu tien them helper nho thay vi refactor lon.
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`: ap dung merge an toan, khong ghi de local pending.
- `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/HgtCheckRepositoryImpl.kt`: verify HGT push/pull va delete.
- `android-mvp/app/src/test/java/com/example/devicetracker/...`: them unit tests cho config, parser, sync rules.
- `android-mvp/app/src/androidTest/java/com/example/devicetracker/...`: them migration/local database/instrumented tests neu can.

### Files khong duoc doc/sua neu khong co lenh ro
- `android-mvp/local.properties`
- `.env`
- `.env.*`
- file token, credential, secret, key theo `policies/secure-files.txt`

---

## Deployment Safety Model

Dung 3 muc moi truong, khong nhay thang vao du lieu that:

| Muc | Ten | Duoc ghi Google Sheet that? | Muc dich |
|---|---|---:|---|
| 1 | Local/unit test | Khong | Test parser, config, merge rule, date, HGT calculation |
| 2 | Production controlled test | Co, tren workbook that theo permission cua user | UAT push/pull/error/schema/rollback voi tap nho co kiem soat |
| 3 | Production broader rollout | Co sau controlled test gate | Du lieu that cua user o pham vi rong hon |

Rule bat buoc:
- Moi thay doi sync phai pass muc 1 truoc.
- Moi ghi nguoc phai bat dau bang muc 2: production controlled test voi sheetId/record_id ro rang.
- Chi sau khi muc 2 pass moi mo rong pham vi sync tren production.
- Neu muc 2 fail bat ky case nao lien quan mat du lieu/ghi sai tab, dung lai va khong sang muc 3.

---

## Rollback Plan

Neu sync loi sau khi bat tren workbook that:

1. Tat ghi nguoc ngay:

```properties
SHEETS_DMBT_LOG_SHEET_ID=
SHEETS_REPAIR_LOG_SHEET_ID=
SHEETS_HGT_CHECKS_SHEET_ID=
```

2. Neu can tam thoi chi xem du lieu, bat che do read-only bang config moi du kien:

```properties
SHEETS_DMBT_TWO_WAY_ENABLED=false
```

3. Khong xoa local DB tren dien thoai.
4. Export/copy Google Sheet truoc khi sua tay.
5. Ghi RCA vao worklog:
   - loi gi
   - tab nao bi anh huong
   - record_id nao bi anh huong
   - cach khoi phuc
   - test nao se chan tai phat

Stop-the-line neu co bat ky dau hieu:
- row bi ghi vao sai tab
- row bi duplicate hang loat
- local pending bi remote ghi de
- sheet chua duoc bat hai chieu bi ghi nguoc

---

## Phase 0: Khoa Config Sheet That

**Goal:** Ghi nhan day du `sheetId/gid` cho 8 tab trong anh va tao cau hinh local an toan.

- [x] **Step 1: User cung cap gid cua tung tab**

Gia tri da chot:

```properties
DMBT_2022_GID=849979183
DMBT_2023_GID=1783863163
DMBT_2024_GID=1224276666
DMBT_2025_GID=989601207
DMBT_2026_GID=1607125070
DMBT_T4_2026_GID=1383308512
SUA_CHUA_T4_2026_GID=157327514
HGT_DINH_KY_GID=57428884
```

- [ ] **Step 2: Dien cau hinh local**

Khong commit `local.properties`. Dien tam theo mode an toan:

```properties
SHEETS_SPREADSHEET_ID=1WWZ3CoeJowlqGUiotwCwXIuijCHGbSCVwCcz3WacYGQ
SHEETS_DMBT_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070,1383308512
SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID=1383308512
SHEETS_HGT_CHECKS_SHEET_ID=57428884
SHEETS_REPAIR_LOG_SHEET_ID=157327514
```

Note:
- Code hien tai van con ten config legacy `SHEETS_DMBT_READONLY_SHEET_IDS`; can doi sang cau hinh DMBT hai chieu truoc khi coi yeu cau moi la hoan tat.
- `SHEETS_REPAIR_LOG_SHEET_ID` da duoc production code doc o lap truoc, nhung network mapper/push-pull rieng cho repair sheet chua hoan tat.

- [x] **Step 3: Verify config khong trung sheetId nguy hiem**

Run:

```powershell
$env:GRADLE_USER_HOME='F:\codex_android_gsheet_full_pack\.gradle-local'
$env:ANDROID_USER_HOME='F:\codex_android_gsheet_full_pack\.android-local'
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --tests com.example.devicetracker.data.sheet.SheetConfigMappingRulesTest --no-daemon
```

Ket qua ngay 2026-05-03:

```text
BUILD SUCCESSFUL
```

---

## Phase 1: Dry-Run Header Inventory

**Goal:** Kiem tra tat ca tab trong scope co header doc duoc truoc khi sync data.

- [x] **Step 1: Xac nhan chien luoc controlled production test**

User da cho phep test truc tiep tren Google Sheet that, khong can tao controlled production test copy.

Yeu cau thay the:

```text
Moi lan test ghi that phai co sheetId, record_id, row du kien va thao tac du kien trong worklog.
```

Expected:

```text
Khong ghi hang loat ngay.
Khong ghi khi chua biet target sheetId.
Co evidence truoc/sau moi test.
```

- [ ] **Step 2: Chay inventory script offline truoc**

Run:

```powershell
npm run ops:verify-sheets
```

Expected:

```text
Khong co secret trong output.
Khong ghi vao Google Sheet.
```

- [ ] **Step 3: Tao inventory that cho cac tab trong controlled production test**

Neu can goi Google Sheets API that, chi chay sau khi token local da san sang va khong log token.

Run:

```powershell
npm run ops:sheets-inventory
```

Expected:

```text
docs/sheets-inventory.md duoc cap nhat voi header tung tab.
Khong log token/secret.
```

- [ ] **Step 4: Gate**

Chi tiep tuc neu:
- `DMBT 2022..2026` va `DMBT T4.2026` co cac cot toi thieu: `Ma thiet bi`, `Hang muc`, `Nguoi bao cao`, `Tinh trang thiet bi`, `KTV phu trach`, `Ngay phat hien`, `Ngay sua chua`, `Ghi chu`.
- `HGT dinh ky` co `ma_thiet_bi`, `chu_ky_ngay`, `lan_gan_nhat`, `lan_tiep_theo` hoac alias tieng Viet tuong ung.
- `Sua chua T4.2026` da duoc phan loai schema.
- Neu `Sua chua T4.2026` khac DMBT, Phase 3 bat buoc tao role/mapper rieng.

---

## Phase 2: Implement Multi-Sheet DMBT Two-Way An Toan

**Goal:** Tat ca tab DMBT trong anh duoc keo vao app va co the ghi nguoc dung tab nguon, khong bi day nham tat ca vao mot sheet duy nhat.

- [ ] **Step 1: Viet failing test cho sheet source identity**

Modify:

`android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/DmbtSheetIdentityTest.kt`

Test intent:

```kotlin
@Test
fun dmbtRecordIdentity_keepsSourceSheetIdForTwoWayPushback() {
    val identity = DmbtRecordIdentity(
        sheetId = 849979183,
        recordId = "same-record"
    )

    assertEquals("849979183", identity.sheetId.toString())
    assertEquals("same-record", identity.recordId)
}
```

Run:

```powershell
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --tests com.example.devicetracker.data.sheet.DmbtSheetIdentityTest --no-daemon
```

Expected before implementation if missing:

```text
FAIL
```

Expected if behavior already exists:

```text
PASS
```

- [ ] **Step 2: Them model/config cho DMBT sheet hai chieu**

Production intent:

```kotlin
data class DmbtSheetBinding(
    val sheetId: Int,
    val mode: SheetSyncMode,
    val isDefaultCreateTarget: Boolean
)
```

Rule:
- `DMBT 2022..2026` va `DMBT T*.YYYY` deu co the `TWO_WAY`.
- Ban ghi keo tu sheet nao thi khi update phai push nguoc sheet do.
- Ban ghi tao moi tren app khi chua co sheet nguon thi dung `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`.

- [ ] **Step 3: Them test sheet sai schema khong duoc bao sync thanh cong gia**

Can tach parser hoac tao fake data source de test duoc ma khong goi network. Neu file `SheetsRemoteDataSource.kt` qua kho test, tao helper parser nho sau khi co failing test.

Files:
- Test: `android-mvp/app/src/test/java/com/example/devicetracker/data/remote/DmbtSheetParserTest.kt`
- Production: chi tao `DmbtSheetParser.kt` neu can tach parser.

Expected behavior:

```text
Sheet dung schema van pull duoc.
Sheet sai schema phai tra loi ro sheetId nao loi.
Neu sheet do dang bat TWO_WAY thi sync khong duoc bao thanh cong gia.
```

---

## Phase 3: Sua Chua Sheet Contract — Bat Buoc Truoc Khi Ghi Nguoc

**Goal:** `Sua chua T4.2026` sync 2 chieu ma khong bi ep dung sai mapper neu schema khac DMBT.

- [ ] **Step 1: Xac dinh header `Sua chua T4.2026`**

Neu header giong DMBT, co the tai su dung DMBT parser nhung van can role/config rieng vi sheet nay duoc phep ghi 2 chieu.

Neu header khac, them role moi:

```kotlin
enum class SheetRole {
    DEVICE_MASTER,
    DMBT_LOG,
    DMBT_REPAIR_LOG,
    HGT_CHECKS,
    LOOKUP_OPTIONS,
    APP_CONFIG
}
```

Khong duoc push vao `Sua chua T4.2026` neu chua co ket qua cua Step 1.

- [x] **Step 2: Viet test contract cho repair sheet**

Create:

`android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/RepairSheetContractTest.kt`

Example expected columns if repair sheet is separate:

```kotlin
@Test
fun repair_sheet_requires_device_code_and_repair_date() {
    val required = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_REPAIR_LOG)

    assertTrue(required.contains(DmbtLogColumns.MA_THIET_BI))
    assertTrue(required.contains(DmbtLogColumns.NGAY_SUA_CHUA))
}
```

- [x] **Step 3: Implement minimal contract only after test fails correctly**

Modify:

`android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetContract.kt`

Do not change sync behavior yet.

- [x] **Step 4: Add config support for repair sheet id**

Files:
- Modify: `android-mvp/app/build.gradle.kts`
- Modify: `android-mvp/app/src/main/java/com/example/devicetracker/data/sheet/SheetConfig.kt`
- Test: `android-mvp/app/src/test/java/com/example/devicetracker/data/sheet/SheetConfigMappingRulesTest.kt`

Test intent:

```kotlin
@Test
fun repair_sheet_id_is_not_allowed_to_duplicate_dmbt_write_target() {
    val duplicates = SheetConfig.findDuplicateSheetIdRoles(
        mapOf(
            SheetRole.DMBT_LOG to 1383308512,
            SheetRole.DMBT_REPAIR_LOG to 1383308512
        )
    )

    assertTrue(duplicates.containsKey(1383308512))
}
```

Ket qua ngay 2026-05-03:

```text
Test fails before SheetRole/config support exists.
Test passes after minimal implementation.
```

Da them:
- `SheetRole.DMBT_REPAIR_LOG`
- `DmbtRepairLogColumns`
- `SHEETS_REPAIR_LOG_SHEET_ID` BuildConfig
- `SheetConfig` support cho repair sheet id
- `SheetSyncRegistry` policy `TWO_WAY` cho `DMBT_REPAIR_LOG`

Luu y: day moi la contract/config/policy. Chua goi network de ghi that vao `Sua chua T4.2026` khi chua qua controlled production test gate.

- [ ] **Step 5: Add repair push/pull tests before production behavior**

Do not call network in unit test.

Create parser/mapper tests for:
- row -> repair domain model
- repair update -> sheet row payload
- missing required columns -> non-retryable failure
- malformed row -> skipped or clear failure according to contract

---

## Phase 3.5: Release Gate Before Any Two-Way Write

**Goal:** Khong ghi vao sheet that khi chua pass controlled production test.

- [x] **Step 1: Unit gate**

Run:

```powershell
$env:GRADLE_USER_HOME='F:\codex_android_gsheet_full_pack\.gradle-local'
$env:ANDROID_USER_HOME='F:\codex_android_gsheet_full_pack\.android-local'
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --no-daemon
```

Ket qua ngay 2026-05-03:

```text
BUILD SUCCESSFUL
```

- [x] **Step 2: Build gate**

Run:

```powershell
$env:GRADLE_USER_HOME='F:\codex_android_gsheet_full_pack\.gradle-local'
$env:ANDROID_USER_HOME='F:\codex_android_gsheet_full_pack\.android-local'
android-mvp\gradlew.bat -p android-mvp assembleDebug --no-daemon
```

Ket qua ngay 2026-05-03:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Production controlled write gate**

On production workbook with user permission, but only controlled small-scope tests:
- Add DMBT record -> writes only default create sheet for current month.
- Edit DMBT record pulled from another DMBT sheet -> writes back to that source sheet.
- Edit repair date -> writes only `Sua chua T4.2026` after repair support exists.
- Edit HGT -> writes only `HGT dinh ky`.
- Break schema intentionally -> app fails clearly, no silent success.

Pass condition:

```text
No data loss.
No writes to wrong sheets.
No duplicate rows after retry.
No silent schema failure.
```

- [ ] **Step 4: Evidence gate**

Record in worklog:
- commands run
- APK build timestamp
- controlled production test copy URL or name
- screenshot/log evidence path
- exact rows/record_id tested
- remaining risk

Only after all four steps pass may production sync scope be expanded beyond controlled single-record tests.

---

## Phase 4: Two-Way Write Gate

**Goal:** Tat ca DMBT sheet trong scope co the ghi hai chieu, nhung moi update phai ghi dung sheet nguon cua ban ghi; ban ghi tao moi dung sheet mac dinh theo thang hien tai.

- [ ] **Step 1: Doi concept DMBT write target**

Concept cu:

```properties
SHEETS_DMBT_LOG_SHEET_ID=1383308512
```

Concept moi can implement:

```properties
SHEETS_DMBT_SHEET_IDS=849979183,1783863163,1224276666,989601207,1607125070,1383308512
SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID=1383308512
```

Rule:
- Tao ban ghi moi trong app: ghi vao `SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID`.
- Sua ban ghi keo tu sheet nao: ghi nguoc dung sheet do.
- Khong phu thuoc vao ten `DMBT T4.2026` vi ten thay doi theo thang.

- [x] **Step 2: Test duplicate sheet id across write roles**

Existing test:

`SheetConfigMappingRulesTest.findDuplicateSheetIdRoles_detects_conflicts_across_roles`

Run:

```powershell
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --tests com.example.devicetracker.data.sheet.SheetConfigMappingRulesTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: UAT write path on Controlled production test**

Use production Google Sheet only under controlled test rules approved by user.

Test:
1. Add DMBT record in app.
2. Run sync.
3. Confirm one row appears in default create sheet only.
4. Edit a row originally pulled from `DMBT 2022`.
5. Run sync.
6. Confirm update goes back to `DMBT 2022`, not `DMBT T4.2026`.
7. Confirm repair sheet unchanged during DMBT create flow.

Pass condition:

```text
Khong co duplicate row.
Khong ghi sai sheet nguon.
Khong day tat ca update ve mot sheet duy nhat.
Khong ghi vao Sua chua T4.2026 khi dang tao DMBT moi.
```

---

## Phase 5: HGT Reminder and HGT Sync

**Goal:** `HGT dinh ky` sync 2 chieu va canh bao lich kiem tra hoat dong tren Android that.

- [ ] **Step 1: Verify HGT unit tests**

Run:

```powershell
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --tests com.example.devicetracker.data.repository.HgtCheckRepositorySyncRulesTest --tests com.example.devicetracker.util.HgtDateCalculatorTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: UAT HGT sync**

On Controlled production test:
1. Edit `lan_gan_nhat` in app.
2. Confirm `lan_tiep_theo` auto-calculates.
3. Sync.
4. Confirm Google Sheet row updates.
5. Edit row in Google Sheet.
6. Full sync.
7. Confirm app receives update.

- [ ] **Step 3: UAT HGT reminder**

On Android device:
1. Grant notification permission.
2. Enable HGT reminder.
3. Set a near reminder.
4. Confirm notification appears.
5. Reboot device.
6. Confirm reminder reschedules.

Pass condition:

```text
Reminder appears, app does not crash, reboot does not lose schedule.
```

---

## Phase 6: Security Hardening Before Production

**Goal:** Khong de token/secret that nam trong APK production hoac log.

- [ ] **Step 1: Secret guard**

Run before any commit:

```powershell
node scripts\prevent-secrets.js
```

Expected in normal terminal:

```text
No output and exit code 0
```

Known sandbox caveat:

```text
Trong sandbox hien tai, script co the bao "Not inside a git repository" vi Node child_process goi git bi chan.
Khong duoc coi day la pass that.
```

- [ ] **Step 2: Auth production design**

Do not ship production APK with real `SHEETS_REFRESH_TOKEN` embedded.

Production-safe options:
1. Android Keystore + user setup flow.
2. EncryptedSharedPreferences for local token storage.
3. Backend proxy if later can operate server-side auth.

MVP debug build can continue using `local.properties`, but must not be treated as production security.

- [ ] **Step 3: Log redaction check**

Search logs and docs for secrets before sharing evidence:

```powershell
Select-String -Path WORKLOG_*.md,docs\*.md,docs\**\*.md -Pattern 'SHEETS_ACCESS_TOKEN|SHEETS_REFRESH_TOKEN|client_secret|Bearer ' -AllMatches
```

Expected:

```text
No real token values in logs/docs.
Only placeholder/config key names are allowed.
```

---

## Phase 7: Full Verification Gate

**Goal:** Chi coi du an hoan thien khi test va UAT deu pass.

- [ ] **Step 1: Unit tests**

Run:

```powershell
$env:GRADLE_USER_HOME='F:\codex_android_gsheet_full_pack\.gradle-local'
$env:ANDROID_USER_HOME='F:\codex_android_gsheet_full_pack\.android-local'
android-mvp\gradlew.bat -p android-mvp testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
0 failures
0 errors
```

- [ ] **Step 2: Build debug**

Run:

```powershell
$env:GRADLE_USER_HOME='F:\codex_android_gsheet_full_pack\.gradle-local'
$env:ANDROID_USER_HOME='F:\codex_android_gsheet_full_pack\.android-local'
android-mvp\gradlew.bat -p android-mvp assembleDebug --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: UAT**

Run all scenarios in:

`UAT_CHECKLIST_SYNC_LOCAL_FIRST.md`

Pass condition:

```text
Offline save pass.
Online sync pass.
No local data loss.
No wrong-sheet writes.
HGT reminder pass.
Schema/token errors are clear.
```

- [ ] **Step 4: Worklog**

Append result to:

`WORKLOG_YYYY-MM-DD.md`

Must include:
- command run
- pass/fail
- remaining risk
- screenshot/log evidence location
- note if sandbox warning is environmental

---

## Recommended Execution Order

1. Phase 0: lock real `gid` values and local config example.
2. Phase 1: inventory/dry-run all 8 tabs on production with no write.
3. Phase 2: finish multi-sheet DMBT two-way identity and pushback.
4. Phase 3: implement `Sua chua T4.2026` contract/mapper/config for two-way sync.
5. Phase 3.5: pass release gate on unit/build/Controlled production test.
6. Phase 4: enforce source-sheet writeback and default create target.
7. Phase 5: verify HGT sync/reminder.
8. Phase 6: harden secrets.
9. Phase 7: final verification gate.

## Stop-The-Line Rules

Stop implementation immediately if:
- Any sync test shows local data loss.
- App writes to the wrong sheet.
- Sheet schema mismatch causes silent success.
- Token/secret appears in logs, docs, screenshots, or commits.
- Same sync bug repeats twice within 48 hours.

## Definition of Done

Project is complete only when:
- All 8 tabs from the image are included in config scope.
- All 8 tabs sync two-way safely after Controlled production test gate pass.
- DMBT records update back to their source sheet; new records use the configured current-month default sheet.
- `Sua chua T4.2026` can pull and write back after contract/mapper tests pass.
- App can add/edit data and sync to Google Sheet.
- App can search by `ma_thiet_bi`.
- HGT reminder works on a real Android device.
- Unit tests pass.
- Debug build pass.
- UAT controlled production test pass before broad production sync is used.
- Rollback plan is documented and can disable all two-way writes quickly.
- Secret guard pass in a normal terminal before commit.

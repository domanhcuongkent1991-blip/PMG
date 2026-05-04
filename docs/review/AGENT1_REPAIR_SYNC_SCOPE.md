# AGENT 1: PHÂN TÍCH PHẠM VI REPAIR SHEET SYNC

**Ngày:** 2026-05-04  
**Agent:** Agent 1 - Code Review  
**Phạm vi:** `Sua chua T4.2026` (gid: 157327514)

---

## 1. TỔNG QUAN

### 1.1 Role và Contract đã có

| Thành phần | Trạng thái | Ghi chú |
|------------|-------------|---------|
| `SheetRole.DMBT_REPAIR_LOG` | ✅ Đã định nghĩa | SheetContract.kt:6 |
| `DmbtRepairLogColumns` | ✅ Đã định nghĩa | 5 cột: record_id, ma_thiet_bi, ngay_sua_chua, ghi_chu, updated_at |
| `SheetContract.requiredColumnsByRole[DMBT_REPAIR_LOG]` | ✅ Đã khai báo | 5 cột bat buoc |
| `SHEETS_REPAIR_LOG_SHEET_ID` BuildConfig | ✅ Đã thêm | GID: 157327514 |
| `SheetRoleConfig` cho DMBT_REPAIR_LOG | ✅ Đã đăng ký | SheetConfig.kt:52-54 |
| `SheetSyncRegistry` mode TWO_WAY | ✅ Đã đặt | Registry dat role là two-way |
| `DmbtRepairUpdate` data class | ✅ Đã có | SheetValueMappers.kt:9-15 |
| `toDmbtRepairLogRow()` mapper | ✅ Đã viết | SheetValueMappers.kt:86-92 |
| `toDmbtRepairUpdateFromRow()` mapper | ✅ Đã viết | SheetValueMappers.kt:94-129 |

### 1.2 Đánh giá tổng thể

**Mức độ hoàn thiện hiện tại: ~40-50%**

Phần contract, model, mapper đã có. Tuy nhiên, **phần sync engine chưa có** - tức là chưa có hàm pull/push repair sheet trong `SheetsRemoteDataSource`.

---

## 2. PHÂN TÍCH CHI TIẾT

### 2.1 Đã có gì

#### A. Contract và Columns (Hoàn thành)

```kotlin
// SheetContract.kt:25-31
object DmbtRepairLogColumns {
    const val RECORD_ID = "record_id"
    const val MA_THIET_BI = "ma_thiet_bi"
    const val NGAY_SUA_CHUA = "ngay_sua_chua"
    const val GHI_CHU = "ghi_chu"
    const val UPDATED_AT = "updated_at"
}
```

#### B. Data Model (Hoàn thành)

```kotlin
// SheetValueMappers.kt:9-15
data class DmbtRepairUpdate(
    val recordId: String,
    val maThietBi: String,
    val ngaySuaChua: String?,
    val ghiChu: String,
    val updatedAt: Long
)
```

#### C. Mappers (Hoàn thành)

- `DmbtRepairUpdate.toDmbtRepairLogRow()` - domain -> sheet row
- `Map<String, String>.toDmbtRepairUpdateFromRow()` - sheet row -> domain

### 2.2 Còn thiếu gì

#### A. Sync Engine - PULL (Chưa có)

**Thiếu hoàn toàn.** Cần thêm:

1. **Hàm `pullRepairLogs()`** trong `SheetsRemoteDataSource`:
   - Fetch header/data từ sheet `Sua chua T4.2026`
   - Parse schema riêng cho repair (không dùng `parseDmbtSchema`)
   - Map row -> `DmbtRepairUpdate`
   - Trả về `Result<List<DmbtRepairUpdate>>`

2. **Schema parser riêng cho repair sheet:**
   - Tương tự `parseHgtSchema()` hoặc viết lại từ đầu
   - Header aliases: `record_id`, `ma_thiet_bi`, `ngay_sua_chua`, `ghi_chu`, `updated_at`

3. **Logic merge vào local DB:**
   - `DeviceLogRepositoryImpl.refreshFromRemote()` chưa gọi pull repair
   - Cần cập nhật local `DeviceLog` với `ngaySuaChua` và `ghiChu` từ repair sheet

#### B. Sync Engine - PUSH (Chưa có)

**Thiếu hoàn toàn.** Cần thêm:

1. **Hàm `pushRepairLogs()`** trong `SheetsRemoteDataSource`:
   - Nhận `List<DmbtRepairUpdate>`
   - Group theo sheetId (repair sheet)
   - Update existing row hoặc append row mới
   - Dedupe để tránh duplicate

2. **Logic trong Repository:**
   - Khi `updateRepairDate()` được gọi, cần enqueue vào repair sync queue riêng
   - Hoặc dùng chung queue nhưng phân biệt operation type

#### C. Reconciliation Logic (Chưa có)

Khi pull repair sheet, cần:
- Tìm `DeviceLog` tương ứng trong local DB theo `record_id`
- Cập nhật `ngaySuaChua` và `ghiChu` của log đó
- Xử lý conflict: nếu local đang PENDING, không overwrite

---

## 3. RỦI RO GHI NHẦM SHEET / ROW / DUPLICATE

### 3.1 Rủi ro Ghi Nhầm Sheet

| Rủi ro | Mức độ | Mô tả |
|--------|---------|-------|
| Ghi nhầm sang DMBT sheet | **CAO** | Nếu dùng chung logic mapper mà không kiểm tra role |
| Ghi nhầm sang HGT sheet | **CAO** | Cùng cấu trúc generic nhưng khác sheetId |
| Ghi nhầm sang sheet khác | **TRUNG BÌNH** | Nếu sheetId config bị sai |

**Nguyên nhân:**
- Hiện tại `SheetsRemoteDataSource` chỉ có `pushLogs()` cho DMBT
- Chưa có hàm riêng cho repair với validation role

**Phòng ngừa bắt buộc:**
1. Tách hoàn toàn `pushRepairLogs()` khỏi `pushLogs()`
2. Validate `sheetId` phải thuộc role `DMBT_REPAIR_LOG` trước khi ghi
3. Log rõ ràng target sheet title + sheetId trước mỗi write

### 3.2 Rủi ro Ghi Nhầm Row

| Rủi ro | Mức độ | Mô tả |
|--------|---------|-------|
| Update nhầm dòng | **CAO** | Nếu `record_id` bị trùng hoặc không tìm thấy |
| Append sai vị trí | **TRUNG BÌNH** | Nếu không kiểm tra existing row |
| Ghi đè dữ liệu mới = cũ | **TRUNG BÌNH** | Nếu không so sánh `updated_at` |

**Nguyên nhân:**
- Repair sheet có thể có `record_id` trùng với DMBT sheet
- Nếu dùng chung `record_id` lookup mà không phân biệt sheet, sẽ nhầm

**Phòng ngừa bắt buộc:**
1. Dùng `record_id` với namespace riêng cho repair, ví dụ: `repair-{original_record_id}`
2. Hoặc lưu thêm `sourceSheetId = 157327514` trong local để phân biệt
3. Luôn verify existing row trước khi update

### 3.3 Rủi ro Duplicate Row

| Rủi ro | Mức độ | Mô tả |
|--------|---------|-------|
| Append trùng sau retry | **CAO** | Nếu không kiểm tra existing |
| Race condition khi đồng thời | **TRUNG BÌNH** | Nếu 2 thiết bị cùng tạo repair entry |

**Nguyên nhân:**
- Không có cơ chế dedupe như `dedupeDmbtLogsForPush()`
- Retry sync không kiểm tra đã tồn tại trên sheet

**Phòng ngừa bắt buộc:**
1. Triển khai `dedupeRepairLogsForPush()` tương tự DMBT
2. Fetch sheet trước khi write để check existing
3. Dùng `batchUpdate` thay vì `append` khi có thể

---

## 4. THỨ TỰ CODE AN TOÀN

### Phase 1: Pull Repair Sheet (Ưu tiên cao)

```
1.1. Viết parseRepairSchema()
    - Header aliases cho 5 cột contract
    - Validate required columns
    - Build rowByRecordId map

1.2. Viết pullRepairLogs()
    - Gọi parseRepairSchema()
    - Map row -> DmbtRepairUpdate
    - Return Result<List<DmbtRepairUpdate>>

1.3. Viết repository merge logic
    - Cập nhật DeviceLog với ngaySuaChua, ghiChu
    - Conflict resolution: local PENDING > remote
```

### Phase 2: Push Repair Sheet (Ưu tiên cao)

```
2.1. Viết dedupeRepairLogsForPush()
    - Giống pattern DMBT: latest by recordId

2.2. Viết pushRepairLogs()
    - Validate target sheetId = DMBT_REPAIR_LOG
    - Fetch existing rows
    - Update hoặc append
    - Batch update API

2.3. Tích hợp vào sync flow
    - Khi updateRepairDate() -> enqueue repair sync
    - syncPending() gọi pushRepairLogs()
```

### Phase 3: Dry-Run và Test (Bắt buộc trước production)

```
3.1. Dry-run mode
    - Log target sheet + record_id + giá trị trước khi ghi
    - Không thực sự gọi API

3.2. Unit tests
    - Test parseRepairSchema với header variants
    - Test dedupeRepairLogsForPush
    - Test conflict local PENDING không bị overwrite

3.3. Integration test (workbook test)
    - Pull repair sheet
    - Push 1 record nhỏ
    - Verify không duplicate
```

---

## 5. CHECKLIST IMPLEMENTATION

### 5.1 Code cần thêm trong `SheetsRemoteDataSource.kt`

| STT | Hàm/Logic | Ưu tiên |
|-----|-----------|---------|
| 1 | `parseRepairSchema()` | Cao |
| 2 | `pullRepairLogs()` | Cao |
| 3 | `dedupeRepairLogsForPush()` | Cao |
| 4 | `pushRepairLogs()` | Cao |
| 5 | Update `validateStructure()` để check DMBT_REPAIR_LOG columns | Cao |

### 5.2 Code cần thêm trong `DeviceLogRepositoryImpl.kt`

| STT | Hàm/Logic | Ưu tiên |
|-----|-----------|---------|
| 1 | `refreshRepairFromRemote()` | Cao |
| 2 | Merge repair update vào DeviceLog | Cao |
| 3 | Enqueue repair sync khi `updateRepairDate()` | Cao |

### 5.3 Test cần thêm

| STT | Test | Mục tiêu |
|-----|------|----------|
| 1 | `RepairSchemaParserTest` | Header variants |
| 2 | `RepairPushDedupeTest` | Không duplicate sau retry |
| 3 | `RepairConflictTest` | Local PENDING không bị overwrite |
| 4 | `RepairPullMergeTest` | Merge đúng record |

---

## 6. KẾT LUẬN

### 6.1 Những gì đã sẵn sàng
- Contract cột đầy đủ
- Data model `DmbtRepairUpdate`
- Mappers hai chiều
- Registry đã set TWO_WAY

### 6.2 Những gì cần hoàn thiện
- Sync engine (pull + push) trong `SheetsRemoteDataSource`
- Reconciliation logic trong repository
- Dry-run mode
- Unit tests đầy đủ

### 6.3 Rủi ro cao nhất
1. **Ghi nhầm sheet**: Cần tách hoàn toàn repair sync khỏi DMBT sync
2. **Duplicate row**: Cần triển khai dedupe trước khi push
3. **Không merge đúng record**: Cần verify `record_id` match trước khi update local

---

**Khuyến nghị:** Bắt đầu từ Phase 1 (Pull) trước vì không có rủi ro mất dữ liệu. Chỉ khi Pull đã ổn định mới triển khai Push.

---

*Báo cáo này chỉ phân tích, không viết code.*

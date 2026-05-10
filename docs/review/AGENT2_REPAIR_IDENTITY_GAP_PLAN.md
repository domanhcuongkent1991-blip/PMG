# AGENT2 REPORT: Repair Sheet Record Identity Gap Analysis

**Agent:** Agent 2 - Identity Gap Analysis
**Date:** 2026-05-04
**Scope:** Phân tích recordId identity gap giữa repair sheet và local DB
**Constraint:** Write-only to `docs/review/AGENT2_REPAIR_IDENTITY_GAP_PLAN.md`

---

## 1. Tổng quan Record Identity trong hệ thống

### 1.1 DMBT Pull RecordId Format

**DMBT pull sử dụng 2 format recordId:**

| Sheet Type | recordId Format | Ví dụ |
|------------|-----------------|-------|
| Non-default sheets (readonly) | `readonly-dmbt-{sheetId}-{baseRecordId}` | `readonly-dmbt-1607125070-TB001-001` |
| Default create sheet | `{baseRecordId}` (không prefix) | `TB001-001` |

**Logic namespace trong `dmbtPulledRecordIdentity()`:**

```kotlin
// SheetsRemoteDataSource.kt dòng 1260-1271
internal fun dmbtPulledRecordIdentity(
    sheetId: Int,
    baseRecordId: String,
    namespaceRecordIds: Boolean
): DmbtPulledRecordIdentity {
    val recordId = if (namespaceRecordIds) {
        buildNamespacedDmbtRecordId(sheetId = sheetId, recordId = baseRecordId)
    } else {
        baseRecordId
    }
    return DmbtPulledRecordIdentity(recordId = recordId, sourceSheetId = sheetId)
}

// dòng 1257-1258
internal fun buildNamespacedDmbtRecordId(sheetId: Int, recordId: String): String =
    "readonly-dmbt-$sheetId-${recordId.trim()}"
```

**Cấu hình namespace:**

```kotlin
// SheetConfig.kt - dmbtPullTargets()
internal fun dmbtPullTargets(bindings: List<SheetConfig.DmbtSheetBinding>): List<DmbtPullTarget> {
    return bindings.map { binding ->
        DmbtPullTarget(
            sheetId = binding.sheetId,
            namespaceRecordIds = !binding.isDefaultCreateTarget  // Non-default = true
        )
    }
}
```

### 1.2 Repair Sheet record_id

**`pullRepairLogs()` lấy record_id trực tiếp:**

```kotlin
// SheetsRemoteDataSource.kt dòng 1220-1222
val recordIdIndex = schema.headerIndexByColumn[DmbtRepairLogColumns.RECORD_ID]
val recordId = rowValues.getOrNull(recordIdIndex ?: return@mapNotNull null).orEmpty().trim()
if (recordId.isBlank()) return@mapNotNull null
```

**Vấn đề:** Repair sheet `record_id` được nhập trực tiếp bởi user, không có logic namespace.

---

## 2. Phân tích Identity Gap

### 2.1 Các kịch bản không khớp

| # | Kịch bản | Repair Sheet record_id | Local DB recordId | Kết quả |
|---|----------|------------------------|-------------------|---------|
| 1 | User nhập base recordId | `TB001-001` | `readonly-dmbt-1607125070-TB001-001` | ❌ Không khớp |
| 2 | User copy từ DMBT sheet | `readonly-dmbt-1607125070-TB001-001` | `readonly-dmbt-1607125070-TB001-001` | ✅ Khớp |
| 3 | Record nằm ở default sheet | `TB001-001` | `TB001-001` | ✅ Khớp |
| 4 | Record nằm ở readonly sheet, user nhập base | `TB001-001` | `readonly-dmbt-1383308512-TB001-001` | ❌ Khớp nhầm nếu fallback |

### 2.2 Tại sao có thể ghi nhầm

**Nguyên tắc:** Một `baseRecordId` có thể tồn tại trong nhiều sheets khác nhau (DMBT 2022, DMBT 2023, DMBT 2026, v.v.)

**Ví dụ:**
- DMBT 2022 có record: `readonly-dmbt-849979183-TB001-001`
- DMBT 2026 có record: `readonly-dmbt-1607125070-TB001-001`

**Nếu fallback chỉ dùng `baseRecordId`:**
- User nhập `TB001-001` vào repair sheet
- App fallback → tìm thấy record đầu tiên → update nhầm sheet!

### 2.3 Các trường hợp hợp lệ

| Trường hợp | Mô tả | Xử lý |
|-------------|-------|--------|
| Exact match | `record_id` khớp chính xác với `recordId` local | ✅ Apply |
| Blank record_id | User không nhập record_id | ❌ Skip |
| Record not found | record_id không tồn tại trong local | ❌ Skip |
| Ambiguous match | Nhiều records khớp base recordId | ❌ Skip (để tránh nhầm) |

---

## 3. Giải pháp Match An Toàn

### 3.1 Giải thuật Match

```
INPUT: repairRecordId (từ repair sheet), localRecordIds (từ local DB)
OUTPUT: matchedRecordId hoặc null

BƯỚC 1: Exact Match
  IF localRecordIds.contains(repairRecordId)
    THEN return repairRecordId

BƯỚC 2: Kiểm tra ambiguity
  Strip namespace prefix từ tất cả local recordIds → baseRecordIds
  count = baseRecordIds.count(repairRecordId)
  
  IF count == 0
    THEN return null  // Record không tồn tại
  
  IF count == 1
    THEN return foundRecordId  // Khớp chính xác 1 record
  
  IF count > 1
    THEN return null  // Ambiguous - skip để tránh ghi nhầm
```

### 3.2 Strip Namespace Helper

```kotlin
/**
 * Strip namespace prefix từ DMBT recordId.
 * 
 * Examples:
 *   "readonly-dmbt-1607125070-TB001-001" → "TB001-001"
 *   "readonly-dmbt-1383308512-TB001-001" → "TB001-001"
 *   "dmbt-auto-TB001-001-row" → "dmbt-auto-TB001-001-row" (giữ nguyên)
 *   "TB001-001" → "TB001-001" (không có prefix)
 */
internal fun stripDmbtNamespace(recordId: String): String {
    val prefixes = listOf("readonly-dmbt-")
    for (prefix in prefixes) {
        if (recordId.startsWith(prefix)) {
            // Lấy phần sau prefix, bỏ sheetId nếu có
            val withoutPrefix = recordId.removePrefix(prefix)
            // Format: {sheetId}-{baseRecordId}
            val dashIndex = withoutPrefix.indexOf('-')
            return if (dashIndex > 0) {
                withoutPrefix.substring(dashIndex + 1)
            } else {
                withoutPrefix
            }
        }
    }
    return recordId
}
```

### 3.3 Kiểm tra Ambiguous

```kotlin
/**
 * Kiểm tra xem repairRecordId có khớp với local records không.
 * Trả về recordId duy nhất nếu khớp, null nếu không hoặc ambiguous.
 */
internal fun resolveRepairRecordId(
    repairRecordId: String,
    localRecordIds: List<String>
): String? {
    // Bước 1: Exact match
    if (repairRecordId in localRecordIds) {
        return repairRecordId
    }
    
    // Bước 2: Strip namespace và tìm base match
    val baseRecordId = stripDmbtNamespace(repairRecordId)
    
    // Tìm tất cả local records có cùng base
    val matchingLocalIds = localRecordIds.filter { localId ->
        stripDmbtNamespace(localId) == baseRecordId
    }
    
    // Chỉ return nếu khớp chính xác 1 record
    return when (matchingLocalIds.size) {
        0 -> null  // Không tồn tại
        1 -> matchingLocalIds.first()  // Khớp duy nhất
        else -> null  // Ambiguous - skip
    }
}
```

---

## 4. Test Plan chi tiết

### Test 1: resolveRepairRecordId_exactMatch

**Mục tiêu:** Verify exact match hoạt động.

```kotlin
@Test
fun resolveRepairRecordId_exactMatch() {
    // Arrange
    val repairRecordId = "readonly-dmbt-1607125070-TB001-001"
    val localRecordIds = listOf(
        "readonly-dmbt-1607125070-TB001-001",
        "readonly-dmbt-1383308512-TB002-001"
    )
    
    // Act
    val result = resolveRepairRecordId(repairRecordId, localRecordIds)
    
    // Assert
    assertThat(result).isEqualTo(repairRecordId)
}
```

---

### Test 2: resolveRepairRecordId_namespaceMismatch

**Mục tiêu:** Verify base match hoạt động khi user nhập base recordId.

```kotlin
@Test
fun resolveRepairRecordId_namespaceMismatch() {
    // Arrange
    val repairRecordId = "TB001-001"  // User nhập base
    val localRecordIds = listOf(
        "readonly-dmbt-1607125070-TB001-001"
    )
    
    // Act
    val result = resolveRepairRecordId(repairRecordId, localRecordIds)
    
    // Assert
    assertThat(result).isEqualTo("readonly-dmbt-1607125070-TB001-001")
}
```

---

### Test 3: resolveRepairRecordId_ambiguousSkip

**Mục tiêu:** Verify ambiguous match được skip.

```kotlin
@Test
fun resolveRepairRecordId_ambiguousSkip() {
    // Arrange
    val repairRecordId = "TB001-001"
    val localRecordIds = listOf(
        "readonly-dmbt-1607125070-TB001-001",  // DMBT 2026
        "readonly-dmbt-1383308512-TB001-001"   // DMBT T4.2026
    )
    
    // Act
    val result = resolveRepairRecordId(repairRecordId, localRecordIds)
    
    // Assert
    assertThat(result).isNull()  // Skip vì ambiguous
}
```

---

### Test 4: resolveRepairRecordId_notFound

**Mục tiêu:** Verify record không tồn tại được skip.

```kotlin
@Test
fun resolveRepairRecordId_notFound() {
    // Arrange
    val repairRecordId = "UNKNOWN-999"
    val localRecordIds = listOf(
        "readonly-dmbt-1607125070-TB001-001"
    )
    
    // Act
    val result = resolveRepairRecordId(repairRecordId, localRecordIds)
    
    // Assert
    assertThat(result).isNull()
}
```

---

### Test 5: stripDmbtNamespace_variousFormats

**Mục tiêu:** Verify strip namespace xử lý đúng các format.

```kotlin
@Test
fun stripDmbtNamespace_variousFormats() {
    // Arrange & Act & Assert
    assertThat(stripDmbtNamespace("readonly-dmbt-1607125070-TB001-001"))
        .isEqualTo("TB001-001")
    
    assertThat(stripDmbtNamespace("readonly-dmbt-1383308512-XYZ-ABC"))
        .isEqualTo("XYZ-ABC")
    
    assertThat(stripDmbtNamespace("TB001-001"))
        .isEqualTo("TB001-001")  // Không có prefix
    
    assertThat(stripDmbtNamespace("dmbt-auto-TB001-001"))
        .isEqualTo("dmbt-auto-TB001-001")  // Auto-generated prefix khác
}
```

---

### Test 6: repairPull_skipOnAmbiguousMatch

**Mục tiêu:** Verify repair pull skip khi ambiguous.

```kotlin
@Test
fun repairPull_skipOnAmbiguousMatch() = runTest {
    // Arrange
    val ambiguousRecordId = "TB001-001"
    
    // Local: 2 records cùng base
    deviceLogDao.upsert(DeviceLogEntity(
        recordId = "readonly-dmbt-1607125070-TB001-001",
        maThietBi = "TB001",
        syncStatus = "SYNCED"
    ))
    deviceLogDao.upsert(DeviceLogEntity(
        recordId = "readonly-dmbt-1383308512-TB001-001",
        maThietBi = "TB001",
        syncStatus = "SYNCED"
    ))
    
    // Remote repair update
    val repairUpdate = DmbtRepairUpdate(
        recordId = ambiguousRecordId,
        maThietBi = "TB001",
        ngaySuaChua = "2026-04-15",
        ghiChu = "Test",
        updatedAt = 2000L
    )
    
    // Act
    val result = repository.applyRepairUpdate(repairUpdate)
    
    // Assert
    assertThat(result.isSuccess).isTrue()
    // Không record nào được update
    val record1 = deviceLogDao.getById("readonly-dmbt-1607125070-TB001-001")
    val record2 = deviceLogDao.getById("readonly-dmbt-1383308512-TB001-001")
    assertThat(record1?.ngaySuaChua).isNull()
    assertThat(record2?.ngaySuaChua).isNull()
}
```

---

## 5. Implementation Checklist

### 5.1 Thêm helper functions (DeviceLogRepositoryImpl.kt hoặc separate file)

```kotlin
internal fun stripDmbtNamespace(recordId: String): String
internal fun resolveRepairRecordId(repairRecordId: String, localRecordIds: List<String>): String?
```

### 5.2 Cập nhật `applyRepairUpdate()`

```kotlin
suspend fun applyRepairUpdate(repairUpdate: DmbtRepairUpdate): Result<Unit> {
    // Lấy tất cả recordIds từ local DB
    val localRecordIds = deviceLogDao.getAllRecordIds()
    
    // Resolve recordId
    val matchedRecordId = resolveRepairRecordId(repairUpdate.recordId, localRecordIds)
        ?: run {
            Log.w(TAG, "Repair update skipped: recordId=${repairUpdate.recordId} not found or ambiguous")
            return Result.success(Unit)
        }
    
    // Apply update với matchedRecordId
    val local = deviceLogDao.getById(matchedRecordId)
    ...
}
```

### 5.3 Thêm method vào DeviceLogDao

```kotlin
// DeviceLogDao.kt
@Query("SELECT recordId FROM device_logs")
suspend fun getAllRecordIds(): List<String>
```

---

## 6. Không đề xuất (Nếu không bắt buộc)

### 6.1 Không thay đổi schema repair sheet

- Không thêm `source_sheet_id` column vào repair sheet
- Không thay đổi cách user nhập record_id
- Giữ nguyên format hiện tại của repair sheet

### 6.2 Không tạo bảng mapping trung gian

- Không tạo `repair_record_mapping` table
- Không cần thêm metadata để track source sheet

### 6.3 Chỉ thay đổi khi bắt buộc

| Thay đổi | Lý do bắt buộc |
|-----------|-----------------|
| Thêm `source_sheet_id` vào repair sheet | Nếu nhiều sheet có cùng base recordId và user muốn chỉ định rõ |
| Tạo mapping table | Nếu repair sheet record_id format phức tạp và không thể strip |

---

## 7. Tóm tắt

| Vấn đề | Giải pháp |
|---------|------------|
| Repair sheet record_id không namespace | Strip prefix → so sánh base |
| Ambiguous match | Skip nếu nhiều records cùng base |
| Record không tồn tại | Skip, không tạo mới |
| Schema không đổi | Không cần thay đổi repair sheet structure |

**An toàn:** Luôn prefer skip over write nhầm.

---

## 8. Hardening Edge Cases (2026-05-04)

### 8.1 Malformed Namespace Handling

Sau khi review, phát hiện các malformed namespace có thể gây strip sai:

| Input | Expected Behavior | Hardened |
|-------|------------------|----------|
| `readonly-dmbt-1607125070-TB001-001` | → `TB001-001` | ✅ |
| `readonly-dmbt-1607125070` (thiếu base) | → giữ nguyên | ✅ |
| `readonly-dmbt-ABC-TB001-001` (non-numeric) | → giữ nguyên | ✅ |
| `readonly-dmbt--123-TB001-001` (negative) | → giữ nguyên | ✅ |

### 8.2 Logic Hardened

```kotlin
fun stripDmbtNamespace(recordId: String): String {
    if (!recordId.startsWith(NAMESPACE_PREFIX)) {
        return recordId
    }

    val withoutPrefix = recordId.removePrefix(NAMESPACE_PREFIX)
    val firstDashIndex = withoutPrefix.indexOf('-')

    // Không có dash → malformed → giữ nguyên
    if (firstDashIndex < 0) {
        return recordId
    }

    val potentialSheetId = withoutPrefix.substring(0, firstDashIndex)

    // SheetId phải là số dương
    if (!potentialSheetId.all { it.isDigit() } || potentialSheetId.isEmpty()) {
        return recordId  // Non-numeric → giữ nguyên
    }

    val afterSheetId = withoutPrefix.substring(firstDashIndex + 1)

    // Base recordId phải không rỗng
    if (afterSheetId.isEmpty()) {
        return recordId  // Malformed → giữ nguyên
    }

    return afterSheetId  // Hợp lệ → strip
}
```

### 8.3 Test Coverage

**Test file:** `RepairRecordIdentityResolverTest.kt`
**Tổng test cases:** 17

- `stripDmbtNamespace`: 7 tests
  - ✅ namespaced recordId strip đúng
  - ✅ base recordId giữ nguyên
  - ✅ auto-generated prefix giữ nguyên
  - ✅ empty string giữ nguyên
  - ✅ malformed missing base giữ nguyên
  - ✅ malformed non-numeric sheetId giữ nguyên
  - ✅ malformed negative sheetId giữ nguyên

- `resolveRepairRecordId`: 10 tests
  - ✅ exact match
  - ✅ base match duy nhất
  - ✅ ambiguous skip
  - ✅ not found skip
  - ✅ empty inputs skip
  - ✅ default sheet match
  - ✅ auto-generated match
  - ✅ sheetId without dash
  - ✅ exact match ưu tiên trước strip
  - ✅ malformed nhưng exact match vẫn hoạt động

---

**Reviewed by:** Agent 2
**Status:** Hardened, test pass ✅

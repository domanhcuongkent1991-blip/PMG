# AGENT2 REPORT: Repair Pull Merge Plan

**Agent:** Agent 2 - Fix Planning
**Date:** 2026-05-04
**Scope:** Repair sheet pull merge - cập nhật `ngay_sua_chua` và `ghi_chu` từ Google Sheet
**Constraint:** Write-only to `docs/review/AGENT2_REPAIR_PULL_MERGE_PLAN.md`

---

## 1. Tổng quan `pullRepairLogs()`

### 1.1 Data Flow hiện tại

```
Google Sheet "Sua chua T4.2026"
    │
    ▼
SheetsRemoteDataSource.pullRepairLogs()
    │
    ▼
List<DmbtRepairUpdate> {
    recordId: String       // Primary key để lookup
    maThietBi: String     // Thông tin reference
    ngaySuaChua: String?   // Ngày sửa (nullable = chưa sửa)
    ghiChu: String        // Ghi chú sửa chữa
    updatedAt: Long        // Timestamp thay đổi
}
    │
    ▼ (Lượt sau - chưa implement)
DeviceLogRepositoryImpl.refreshRepairFromRemote()
    │
    ▼
Local Room DB - device_logs table
```

### 1.2 Đặc điểm quan trọng

- `pullRepairLogs()` trả về **chỉ các trường cần cập nhật**, không phải full record
- `ngaySuaChua` là nullable: `null`/`blank` = chưa sửa, có giá trị = đã sửa
- Merge cần **partial update**: chỉ cập nhật `ngaySuaChua` và `ghiChu`, giữ nguyên các trường khác

---

## 2. Lookup bằng `recordId`

### 2.1 Cơ chế lookup

**`recordId` là Primary Key** trong `DeviceLogEntity`:

```kotlin
// DeviceLogEntity.kt
@Entity(tableName = "device_logs")
data class DeviceLogEntity(
    @PrimaryKey val recordId: String,  // ← Primary Key
    ...
)
```

**Lookup flow trong `DeviceLogRepositoryImpl`:**

```kotlin
// refreshFromRemote() dòng 168
val local = deviceLogDao.getById(remoteLog.recordId)  // ← Primary Key lookup

// Result:
// - local != null → Record tồn tại → apply merge logic
// - local == null → Record không có trong local → skip (không tạo mới từ repair sheet)
```

### 2.2 Tại sao không ghi nhầm

| Yếu tố | Bảo vệ |
|--------|--------|
| Primary Key | `recordId` là unique identifier, không có 2 record cùng recordId |
| Namespace | DMBT recordId đã có namespace theo sheet (ví dụ: `dmbt-2026-{sheetId}-{baseId}`) |
| Repair sheet recordId | Lấy trực tiếp từ `record_id` column của sheet, khớp với DMBT recordId |

### 2.3 Edge cases được xử lý

| Edge case | Xử lý |
|-----------|--------|
| Record không tồn tại trong local | Skip - không tạo record mới từ repair sheet |
| `record_id` blank trong sheet | Skip row (đã có check `if (recordId.isBlank()) return@mapNotNull null`) |
| `ngay_sua_chua` blank trong sheet | Đặt `ngaySuaChua = null` (chưa sửa) |

---

## 3. Rule Conflict: Local PENDING/FAILED thắng Remote

### 3.1 Rule hiện tại cho DMBT pull

```kotlin
// DeviceLogRepositoryImpl.kt dòng 298-302
internal fun shouldApplyRemoteLog(currentLocal: DeviceLogEntity?, remoteLog: DeviceLog): Boolean {
    if (currentLocal == null) return true      // Không có local → apply remote
    if (currentLocal.syncStatus != "SYNCED") return false  // ← LOCAL THẮNG
    return remoteLog.updatedAt >= currentLocal.updatedAt
}
```

**Logic:**
- Nếu `syncStatus != "SYNCED"` → Local đang chờ push → Không apply remote
- Nếu `syncStatus == "SYNCED"` → Local đã sync → So sánh `updatedAt`

### 3.2 Áp dụng cho Repair Pull

Vấn đề: `pullRepairLogs()` trả về `DmbtRepairUpdate`, không phải `DeviceLog`.

**Cần tạo function mới:**

```kotlin
// DeviceLogRepositoryImpl.kt
internal fun shouldApplyRepairUpdate(
    currentLocal: DeviceLogEntity?,
    repairUpdate: DmbtRepairUpdate
): Boolean {
    // Không có local → không apply (repair sheet không tạo record mới)
    if (currentLocal == null) return false
    
    // Local đang chờ push → LOCAL THẮNG
    if (currentLocal.syncStatus != "SYNCED") return false
    
    // Local đã sync → apply nếu remote mới hơn
    return repairUpdate.updatedAt >= currentLocal.updatedAt
}
```

### 3.3 Partial Merge Logic

Khi apply repair update, cần **merge chỉ các trường thay đổi**:

```kotlin
// Pseudocode cho merge
val local = deviceLogDao.getById(repairUpdate.recordId)
if (shouldApplyRepairUpdate(local, repairUpdate)) {
    val mergedEntity = local!!.copy(
        ngaySuaChua = repairUpdate.ngaySuaChua,  // Cập nhật từ remote
        ghiChu = repairUpdate.ghiChu,            // Cập nhật từ remote
        updatedAt = repairUpdate.updatedAt,       // Cập nhật timestamp
        syncStatus = "SYNCED"                    // Đã đồng bộ với remote
    )
    deviceLogDao.upsert(mergedEntity)
}
```

---

## 4. Test Plan chi tiết

### Test 1: repairPull_updatesCorrectRecordByRecordId

**Mục tiêu:** Verify repair update cập nhật đúng record dựa trên `recordId`.

```kotlin
@Test
fun repairPull_updatesCorrectRecordByRecordId() = runTest {
    // Arrange
    val recordId = "dmbt-2026-1383308512-TB001-001"
    
    // Local record với ngaySuaChua = null
    val localEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB001",
        hangMuc = "Hư màn hình",
        nguoiBaoCao = "KTV A",
        tinhTrangThietBi = "Bình thường",
        ktvPhuTrach = "KTV B",
        ngayPhatHien = "2026-01-01",
        ngaySuaChua = null,  // ← Chưa sửa
        ghiChu = "",
        updatedAt = 1000L,
        syncStatus = "SYNCED"
    )
    deviceLogDao.upsert(localEntity)

    // Remote repair update với ngaySuaChua = "2026-04-15"
    val repairUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "TB001",
        ngaySuaChua = "2026-04-15",
        ghiChu = "Đã thay màn hình mới",
        updatedAt = 2000L
    )

    // Act
    val result = repository.applyRepairUpdate(repairUpdate)

    // Assert
    assertThat(result.isSuccess).isTrue()
    
    val updatedEntity = deviceLogDao.getById(recordId)
    assertThat(updatedEntity?.ngaySuaChua).isEqualTo("2026-04-15")
    assertThat(updatedEntity?.ghiChu).isEqualTo("Đã thay màn hình mới")
    assertThat(updatedEntity?.updatedAt).isEqualTo(2000L)
    assertThat(updatedEntity?.syncStatus).isEqualTo("SYNCED")
    // Các trường khác giữ nguyên
    assertThat(updatedEntity?.hangMuc).isEqualTo("Hư màn hình")
    assertThat(updatedEntity?.nguoiBaoCao).isEqualTo("KTV A")
}
```

---

### Test 2: repairPull_doesNotOverwriteLocalPending

**Mục tiêu:** Verify local PENDING không bị remote overwrite.

```kotlin
@Test
fun repairPull_doesNotOverwriteLocalPending() = runTest {
    // Arrange
    val recordId = "dmbt-2026-1383308512-TB002-001"
    
    // Local record với syncStatus = PENDING (đang chờ push)
    val localEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB002",
        hangMuc = "Hư pin",
        ngayPhatHien = "2026-01-01",
        ngaySuaChua = null,
        ghiChu = "",
        updatedAt = 1000L,
        syncStatus = "PENDING"  // ← Local đang chờ push
    )
    deviceLogDao.upsert(localEntity)

    // Remote repair update với ngaySuaChua mới
    val repairUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "TB002",
        ngaySuaChua = "2026-04-20",
        ghiChu = "Remote ghi chú",
        updatedAt = 2000L  // ← Remote mới hơn
    )

    // Act
    val result = repository.applyRepairUpdate(repairUpdate)

    // Assert
    assertThat(result.isSuccess).isTrue()
    
    // Local vẫn giữ nguyên, không bị overwrite
    val updatedEntity = deviceLogDao.getById(recordId)
    assertThat(updatedEntity?.ngaySuaChua).isNull()  // ← Giữ nguyên null
    assertThat(updatedEntity?.ghiChu).isEqualTo("")   // ← Giữ nguyên rỗng
    assertThat(updatedEntity?.syncStatus).isEqualTo("PENDING")  // ← Vẫn PENDING
}
```

---

### Test 3: repairPull_blankNgaySuaChuaMeansPending

**Mục tiêu:** Verify blank `ngay_sua_chua` vẫn được hiểu là "chưa sửa".

```kotlin
@Test
fun repairPull_blankNgaySuaChuaMeansPending() = runTest {
    // Arrange
    val recordId = "dmbt-2026-1383308512-TB003-001"
    
    // Local record đã sửa trước đó
    val localEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB003",
        ngayPhatHien = "2026-01-01",
        ngaySuaChua = "2026-03-01",  // ← Đã sửa trước đó
        ghiChu = "Sửa lần 1",
        updatedAt = 1000L,
        syncStatus = "SYNCED"
    )
    deviceLogDao.upsert(localEntity)

    // Remote repair update với ngaySuaChua blank (ô trống)
    val repairUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "TB003",
        ngaySuaChua = null,  // ← Blank/null = chưa sửa
        ghiChu = "",
        updatedAt = 2000L
    )

    // Act
    val result = repository.applyRepairUpdate(repairUpdate)

    // Assert
    assertThat(result.isSuccess).isTrue()
    
    // Local được cập nhật: ngaySuaChua = null, ghiChu = ""
    val updatedEntity = deviceLogDao.getById(recordId)
    assertThat(updatedEntity?.ngaySuaChua).isNull()  // ← Chưa sửa
    assertThat(updatedEntity?.ghiChu).isEqualTo("")   // ← Ghi chú trống
    assertThat(updatedEntity?.syncStatus).isEqualTo("SYNCED")
}
```

---

### Test 4: repairPull_skipsRecordNotInLocal

**Mục tiêu:** Verify record không tồn tại trong local được skip.

```kotlin
@Test
fun repairPull_skipsRecordNotInLocal() = runTest {
    // Arrange
    val recordId = "dmbt-2026-1383308512-UNKNOWN-999"
    
    // Không insert record nào vào local DB
    
    // Remote repair update cho record không tồn tại
    val repairUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "UNKNOWN",
        ngaySuaChua = "2026-04-25",
        ghiChu = "Test",
        updatedAt = 2000L
    )

    // Act
    val result = repository.applyRepairUpdate(repairUpdate)

    // Assert
    assertThat(result.isSuccess).isTrue()
    
    // Record vẫn không tồn tại trong local
    val localEntity = deviceLogDao.getById(recordId)
    assertThat(localEntity).isNull()
}
```

---

### Test 5: repairPull_multipleUpdatesSameRecord_takesLatest

**Mục tiêu:** Verify nếu có nhiều repair updates cho cùng record, chỉ apply bản mới nhất.

```kotlin
@Test
fun repairPull_multipleUpdatesSameRecord_takesLatest() = runTest {
    // Arrange
    val recordId = "dmbt-2026-1383308512-TB004-001"
    
    val localEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB004",
        ngayPhatHien = "2026-01-01",
        ngaySuaChua = null,
        ghiChu = "",
        updatedAt = 1000L,
        syncStatus = "SYNCED"
    )
    deviceLogDao.upsert(localEntity)

    // Remote repair updates với timestamps khác nhau
    val olderUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "TB004",
        ngaySuaChua = "2026-04-10",
        ghiChu = "Older update",
        updatedAt = 2000L
    )
    val newerUpdate = DmbtRepairUpdate(
        recordId = recordId,
        maThietBi = "TB004",
        ngaySuaChua = "2026-04-25",
        ghiChu = "Newer update",
        updatedAt = 3000L
    )

    // Act: Apply cả 2 (theo thứ tự trong list)
    repository.applyRepairUpdate(olderUpdate)
    repository.applyRepairUpdate(newerUpdate)

    // Assert: Chỉ bản mới nhất được apply
    val updatedEntity = deviceLogDao.getById(recordId)
    assertThat(updatedEntity?.ngaySuaChua).isEqualTo("2026-04-25")
    assertThat(updatedEntity?.ghiChu).isEqualTo("Newer update")
    assertThat(updatedEntity?.updatedAt).isEqualTo(3000L)
}
```

---

## 5. File cần sửa (Lượt sau - Implementation)

### 5.1 DeviceLogRepositoryImpl.kt

**Vị trí:** `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`

**Thêm mới:**

1. **Method mới:** `applyRepairUpdate()`

```kotlin
/**
 * Apply một repair update từ Google Sheet vào local DB.
 * Chỉ áp dụng nếu:
 * - Local record tồn tại
 * - Local syncStatus == "SYNCED"
 * - Remote updatedAt >= local updatedAt
 */
suspend fun applyRepairUpdate(repairUpdate: DmbtRepairUpdate): Result<Unit> {
    val local = deviceLogDao.getById(repairUpdate.recordId)
    
    if (!shouldApplyRepairUpdate(local, repairUpdate)) {
        Log.d(TAG, "applyRepairUpdate skipped recordId=${repairUpdate.recordId}")
        return Result.success(Unit)
    }
    
    val mergedEntity = local!!.copy(
        ngaySuaChua = repairUpdate.ngaySuaChua,
        ghiChu = repairUpdate.ghiChu,
        updatedAt = repairUpdate.updatedAt,
        syncStatus = "SYNCED"
    )
    deviceLogDao.upsert(mergedEntity)
    Log.i(TAG, "applyRepairUpdate applied recordId=${repairUpdate.recordId}")
    return Result.success(Unit)
}
```

2. **Function mới:** `shouldApplyRepairUpdate()`

```kotlin
internal fun shouldApplyRepairUpdate(
    currentLocal: DeviceLogEntity?,
    repairUpdate: DmbtRepairUpdate
): Boolean {
    if (currentLocal == null) return false
    if (currentLocal.syncStatus != "SYNCED") return false
    return repairUpdate.updatedAt >= currentLocal.updatedAt
}
```

3. **Cập nhật `refreshFromRemote()`:** Gọi `pullRepairLogs()` và apply từng update

---

### 5.2 SheetsRemoteDataSource (đã có, không sửa)

- `pullRepairLogs()` đã implement ở dòng 1199-1249
- Trả về `List<DmbtRepairUpdate>`

---

### 5.3 Unit Test File (New)

**Vị trí mới:** `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositoryRepairPullTest.kt`

**Test cases:**
1. `repairPull_updatesCorrectRecordByRecordId`
2. `repairPull_doesNotOverwriteLocalPending`
3. `repairPull_blankNgaySuaChuaMeansPending`
4. `repairPull_skipsRecordNotInLocal`
5. `repairPull_multipleUpdatesSameRecord_takesLatest`

---

## 6. Rủi ro và giải thích

### Rủi ro tiềm năng

| Rủi ro | Mức độ | Giải thích |
|--------|--------|------------|
| Record không tồn tại trong local | THẤP | Repair sheet chỉ update records đã có trong DMBT, skip nếu không có |
| Overwrite local pending | KHÔNG CÓ | Rule `syncStatus != "SYNCED"` ngăn chặn hoàn toàn |
| Timestamp race | THẤP | So sánh `updatedAt` đảm bảo bản mới hơn thắng |
| Null ngaySuaChua overwrite | CẦN CHÚ Ý | Null trong repair = blank ô = chưa sửa → hợp lý |

### Không đề xuất

- Không tạo record mới từ repair sheet (vì repair chỉ update, không phải create)
- Không merge full record từ repair (vì repair chỉ có 4 trường)
- Không thay đổi schema database

---

## 7. Checklist cho lượt implement

- [ ] Thêm `applyRepairUpdate(repairUpdate)` method vào `DeviceLogRepositoryImpl`
- [ ] Thêm `shouldApplyRepairUpdate()` function
- [ ] Cập nhật `refreshFromRemote()` để gọi `pullRepairLogs()` và apply
- [ ] Tạo unit test `DeviceLogRepositoryRepairPullTest.kt`
- [ ] Chạy tất cả unit test, verify pass
- [ ] Verify log output đúng khi apply và skip

---

**Reviewed by:** Agent 2
**Status:** Ready for implementation by next agent

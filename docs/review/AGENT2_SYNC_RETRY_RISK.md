# AGENT2 REPORT: Sync Retry/Duplicate/Conflict Risk Analysis

**Agent:** Agent 2 - Risk Review
**Date:** 2026-05-04
**Scope:** android-mvp sync layer (retry, duplicate, conflict resolution)
**Constraint:** Write-only to `docs/review/AGENT2_SYNC_RETRY_RISK.md`

---

## 1. Tóm tắt điều tra

Đã rà soát các file sau:
- `SheetsRemoteDataSource.kt` (1260 dòng)
- `SheetsSyncWorker.kt` (85 dòng)
- `DeviceLogRepositoryImpl.kt` (302 dòng)
- `DeviceLogDao.kt`
- `SyncQueueDao.kt`
- `SyncQueueEntity.kt`
- `SheetSyncRegistry.kt`
- Các unit test trong `src/test/`

---

## 2. Cơ chế idempotent đã có

### 2.1 Push Deduplication (SheetsRemoteDataSource)

```kotlin
// Dòng 1229-1242: dedupeDmbtLogsForPush()
internal fun dedupeDmbtLogsForPush(targetSheetId: Int, logs: List<DeviceLog>): List<DeviceLog> {
    val latestBySheetRecordId = linkedMapOf<String, DeviceLog>()
    logs.forEach { log ->
        val sheetRecordId = resolveDmbtSheetRecordId(targetSheetId = targetSheetId, recordId = log.recordId)
        val current = latestBySheetRecordId[sheetRecordId]
        if (current == null || log.updatedAt >= current.updatedAt) {
            latestBySheetRecordId[sheetRecordId] = log
        }
    }
    return latestBySheetRecordId.values.sortedBy { it.updatedAt }
}
```

**Đánh giá:** ✅ Cơ chế deduplicate tốt. Nếu cùng `recordId` xuất hiện nhiều lần trong một batch, chỉ giữ lại bản có `updatedAt` mới nhất.

### 2.2 Upsert by recordId (SheetsRemoteDataSource)

```kotlin
// Dòng 111-121: Tìm row theo recordId hoặc fallback key
if (existingRow != null) {
    updates += existingRow to rowValues  // UPDATE
} else {
    appends += rowValues                 // APPEND
}
```

**Đánh giá:** ✅ Cơ chế upsert đúng: tìm theo `record_id` trước, nếu có thì update row hiện có, không thì append row mới.

### 2.3 Queue Snapshot Pattern (DeviceLogRepositoryImpl)

```kotlin
// Dòng 98-102: Lấy snapshot trước khi sync
val queueSnapshot = syncQueueDao.getAll().filter { it.operation == OP_UPSERT_LOG }
if (queueSnapshot.isEmpty()) return Result.success(Unit)
```

```kotlin
// Dòng 137: Chỉ xóa queue items thuộc snapshot này
queueSnapshot.forEach { item -> syncQueueDao.deleteById(item.id) }
```

**Đánh giá:** ✅ Tốt. Mỗi sync run chỉ consume snapshot của nó, queue items mới tạo trong khi sync đang chạy sẽ không bị xóa nhầm.

### 2.4 Conflict Resolution Local-First (DeviceLogRepositoryImpl)

```kotlin
// Dòng 298-302: shouldApplyRemoteLog()
internal fun shouldApplyRemoteLog(currentLocal: DeviceLogEntity?, remoteLog: DeviceLog): Boolean {
    if (currentLocal == null) return true
    if (currentLocal.syncStatus != "SYNCED") return false  // ← Bảo vệ local PENDING/FAILED
    return remoteLog.updatedAt >= currentLocal.updatedAt
}
```

**Đánh giá:** ✅ Đúng policy. Local PENDING/FAILED không bị remote overwrite.

---

## 3. Rủi ro phát hiện

### 3.1 RỦI RO CAO

#### R01: Race condition khi push trong khi user đang sửa cùng bản ghi

**Vị trí:** `DeviceLogRepositoryImpl.syncPending()` dòng 121-134

**Mô tả:**
```kotlin
// Lấy logs từ DB tại thời điểm T1
val syncCandidateLogs = queueSnapshot.map { ... }.mapNotNull { recordId ->
    deviceLogDao.getById(recordId)?.toDomain()  // ← Thời điểm T1
}

// ... gọi API push thành công ...

// Kiểm tra lại tại thời điểm T2 > T1
val current = deviceLogDao.getById(pushedLog.recordId)
if (shouldMarkAsSynced(current, pushedLog)) {  // ← So sánh updatedAt
    deviceLogDao.upsert(currentLog.copy(syncStatus = "SYNCED"))
}
```

**Vấn đề:**
- Tại T1: User A sửa bản ghi X → `updatedAt = 1000`, queue PENDING
- T2: Sync chạy, lấy snapshot, push thành công
- T3: User A tiếp tục sửa bản ghi X → `updatedAt = 2000`
- T4: Sync kiểm tra `shouldMarkAsSynced(current, pushedLog)`
  - `current.updatedAt = 2000` (mới hơn)
  - `pushedLog.updatedAt = 1000` (cũ)
  - → Không mark SYNCED (đúng!)
  - → Nhưng queue đã bị xóa ở dòng 137!

**Hậu quả:** Bản ghi mới sửa tại T3 không có trong queue, sẽ không được sync lại cho đến khi user tạo thao tác mới (saveLog/updateRepairDate).

**Mức độ:** CAO - Dữ liệu không bị mất (vẫn trong DB), nhưng thay đổi tại T3 sẽ không được push lên Google Sheet.

**Test cần thêm:**
```
1. Mock: User sửa bản ghi X (updatedAt=1000), push thành công
2. Mock: Trước khi mark SYNCED, user sửa lại X (updatedAt=2000)
3. Assert: X có syncStatus = PENDING, queue có entry cho X
4. Assert: Không gọi push 2 lần cho cùng recordId
```

---

### 3.2 RỦI RO TRUNG BÌNH

#### R02: Append rows không có retry protection

**Vị trí:** `SheetsRemoteDataSource.appendRows()` dòng 642-667

**Mô tả:**
```kotlin
private fun appendRows(sheetTitle: String, ..., rows: List<List<String>>, accessToken: String) {
    // ... gọi POST /values:append ...
}
```

**Vấn đề:**
- API `append` không có idempotency key
- Nếu request bị timeout ở phía client nhưng server đã xử lý thành công, retry sẽ tạo row trùng
- Không có cơ chế "check-before-append" (chỉ có check trong `dedupeDmbtLogsForPush` nhưng chỉ trong cùng 1 batch)

**Giảm thiểu hiện có:**
- `dedupeDmbtLogsForPush()` đã deduplicate trong batch
- Nhưng không bảo vệ against retry sau khi timeout

**Mức độ:** TRUNG BÌNH - Có thể tạo duplicate row nếu network timeout xảy ra.

**Test cần thêm:**
```
1. Mock HTTP client: Request 1 gọi thành công nhưng timeout ở client
2. Retry request
3. Assert: Sheet chỉ có 1 row cho recordId đó (hoặc app có cơ chế detect duplicate)
```

---

#### R03: Pull merge không check recordId namespace collision

**Vị trí:** `SheetsRemoteDataSource.pullDmbtLogsFromSheet()` dòng 226-230

**Mô tả:**
```kotlin
val recordIdentity = dmbtPulledRecordIdentity(
    sheetId = sheetId,
    baseRecordId = baseRecordId,
    namespaceRecordIds = namespaceRecordIds
)
```

**Vấn đề:**
- Nếu 2 sheet khác nhau có cùng `baseRecordId` (ví dụ: cả 2 sheet đều không có `record_id` và fallback key trùng nhau)
- Mặc dù có namespace, nhưng `namespaceRecordIds = false` cho default sheet
- Cùng 1 baseRecordId từ default sheet sẽ ghi đè baseRecordId từ non-default sheet

**Giảm thiểu:**
- Các sheet DMBT 2022-2026 được config là `namespaceRecordIds = true`
- Chỉ default create sheet (DMBT T4.2026) có `namespaceRecordIds = false`

**Mức độ:** TRUNG BÌNH - Xảy ra nếu user có 2 sheet với dữ liệu trùng lặp và cùng fallback key.

---

#### R04: Refresh token race condition

**Vị trí:** `SheetsRemoteDataSource.refreshAccessTokenIfNeeded()` dòng 496-515

**Mô tả:**
```kotlin
@Synchronized
private fun refreshAccessTokenIfNeeded(): String {
    // ...
    cachedAccessToken
        ?.takeIf { it.isNotBlank() && now < cachedAccessTokenExpiresAt }
        ?.let { return it }
    // ... refresh token ...
}
```

**Vấn đề:**
- `@Synchronized` chỉ bảo vệ trong 1 JVM instance
- Trên Android, nếu có nhiều WorkManager instances chạy song song (ví dụ: expedited work), có thể gọi refresh đồng thời
- Tuy nhiên WorkManager đảm bảo constraints, nên rủi ro thấp

**Mức độ:** THẤP trong context Android WorkManager

---

### 3.3 RỦI RO THẤP

#### R05: Batch update không có partial failure handling

**Vị trí:** `SheetsRemoteDataSource.batchUpdateRows()` dòng 612-640

**Mô tả:**
```kotlin
rowUpdates.forEach { (rowNumber, rowValues) ->
    // Tạo batch request
}
// Gửi 1 request cho tất cả rows
executeJsonRequest(method = "POST", ..., requestBody = body)
```

**Vấn đề:**
- Nếu 1 row trong batch fail, toàn bộ batch có thể fail
- Hoặc 1 row fail nhưng các row khác thành công (tùy API behavior)

**Giảm thiểu:**
- API batchUpdate của Google Sheets có thể trả về partial success
- Response có thể chứa error per-item

**Mức độ:** THẤP - Có thể xảy ra nhưng hiếm và có retry mechanism.

---

#### R06: Retry count không được track trong queue

**Vị trí:** `SyncQueueEntity` và `SyncQueueDao.markFailed()`

**Mô tả:**
```kotlin
// SyncQueueDao
@Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
suspend fun markFailed(id: Long, error: String)
```

**Vấn đề:**
- `retryCount` được tăng khi markFailed, nhưng không có max retry limit check
- WorkManager có `runAttemptCount` riêng, nhưng queue không có

**Giảm thiểu:**
- WorkManager sẽ không retry vô hạn (có maxAttempts mặc định)
- NonRetryableException sẽ không retry

**Mức độ:** THẤP - Đã có WorkManager retry limit.

---

## 4. Ma trận rủi ro

| ID | Rủi ro | Khả năng | Tác động | Mức độ |
|----|--------|----------|----------|---------|
| R01 | Race condition: thay đổi trong khi sync không được re-push | Trung bình | Thay đổi local không lên sheet | **CAO** |
| R02 | Append retry tạo duplicate row | Thấp | Duplicate data trong sheet | TRUNG BÌNH |
| R03 | Pull merge namespace collision | Thấp | Data overwrite nhầm | TRUNG BÌNH |
| R04 | Refresh token race | Rất thấp | Token refresh thừa | THẤP |
| R05 | Batch update partial failure | Thấp | Sync không hoàn toàn | THẤP |
| R06 | Retry count không giới hạn trong queue | Rất thấp | Queue stale | THẤP |

---

## 5. Test cần thêm trước khi ghi Google Sheet thật

### 5.1 Priority 1 (CAO - phải có trước production)

```
Test Suite: SyncRaceConditionTest

Test 1: syncPending_withConcurrentLocalEdit
- Setup: Insert record X (updatedAt=1000), enqueue, call syncPending()
- Mock remote push success
- Simulate: Before mark SYNCED, another edit updates X (updatedAt=2000)
- Assert: X.syncStatus == PENDING
- Assert: Queue still has entry for X
- Assert: No duplicate push for X in this sync run

Test 2: syncPending_retryAfterTimeout_doesNotDuplicateRow
- Setup: Record Y đang chờ sync
- Mock: First push() call throws IOException (simulate timeout)
- Verify: Exception propagates, queue marked FAILED
- Mock: Second push() call succeeds
- Verify: Sheet has exactly 1 row for Y

Test 3: fullSync_pullDoesNotOverwritePendingLocal
- Setup: Local record Z có syncStatus=PENDING với content "local"
- Mock remote: record Z có content "remote" với updatedAt mới hơn
- Call refreshFromRemote()
- Assert: Local record Z giữ nguyên content "local"
- Assert: Z.syncStatus vẫn là PENDING
```

### 5.2 Priority 2 (TRUNG BÌNH - nên có)

```
Test Suite: AppendIdempotencyTest

Test 4: appendRows_withHttpTimeoutThenRetry_noDuplicateRow
- Setup: Record W đang chờ sync, sheet chưa có W
- Mock: First appendRows() HTTP timeout
- Trigger: Retry (call pushLogs again)
- Mock: Second appendRows() success
- Verify: Sheet chỉ có 1 row cho W
- Hint: Cần mock server để track số lần thực sự append

Test 5: dedupeInBatch_worksAcrossMultipleSheets
- Setup: 2 logs cho cùng recordId nhưng khác sheetId trong 1 batch
- Call: pushLogs() với cả 2 logs
- Assert: Mỗi sheet chỉ có 1 row cho recordId đó
```

### 5.3 Priority 3 (THẤP - nếu có thời gian)

```
Test Suite: NamespaceCollisionTest

Test 6: pullFromMultipleSheets_sameFallbackKey_noCollision
- Setup: Sheet A và Sheet B đều có row với same maThietBi, ngayPhatHien, hangMuc, tinhTrang
- Sheet A: namespaceRecordIds = false (default create target)
- Sheet B: namespaceRecordIds = true
- Call: pullLatestLogs()
- Assert: 2 DeviceLog objects in result
- Assert: recordIds khác nhau (namespaced)
```

---

## 6. Khuyến nghị

### 6.1 Fix cần thiết cho R01

**Vấn đề:** Race condition có thể khiến thay đổi trong lúc sync không được re-push.

**Giải pháp đề xuất:**

```kotlin
// Trong syncPending(), sau khi push thành công:
syncCandidateLogs.forEach { pushedLog ->
    val current = deviceLogDao.getById(pushedLog.recordId)
    
    // Chỉ mark SYNCED nếu:
    // 1. Có local record tồn tại
    // 2. updatedAt khớp với bản đã push
    // 3. KHÔNG có queue entry mới cho record này (thay đổi trong lúc sync)
    val hasNewerQueueEntry = syncQueueDao.getByRecordId(pushedLog.recordId) != null
    
    if (current != null && 
        current.updatedAt == pushedLog.updatedAt && 
        !hasNewerQueueEntry) {
        deviceLogDao.upsert(current.copy(syncStatus = "SYNCED"))
    }
}
```

**Lưu ý:** Cần thêm method `getByRecordId` vào `SyncQueueDao`.

### 6.2 Monitoring cần thêm

1. **Metric:** Số lượng "stale" records (sync thành công nhưng không mark SYNCED vì có thay đổi mới)
2. **Alert:** Nếu stale count > 0 trong 5 phút, báo để investigate
3. **Log enhancement:**
   ```
   Log.w(TAG, "syncPending skipped markSynced for recordId=X: updatedAt mismatch or newer queue entry")
   ```

### 6.3 Workaround tạm thời

Trong khi chưa fix R01, user có thể:
1. Sau khi sync, kiểm tra lại các bản ghi đã sửa
2. Nếu thấy bản ghi vẫn ở trạng thái "Chờ đẩy", bấm sync lại

---

## 7. Kết luận

**Điểm mạnh:**
- Cơ chế idempotent push đã tốt (dedupe + upsert)
- Queue snapshot pattern đúng đắn
- Conflict resolution local-first đúng policy
- Có phân biệt retryable vs non-retryable exceptions

**Điểm yếu cần fix:**
- **R01 (CAO):** Race condition có thể khiến thay đổi trong lúc sync không được push lên sheet
- **R02 (TRUNG BÌNH):** Append không có idempotency protection against timeout retry

**Ước tính effort fix:**
- R01: 4-8 giờ (cần thêm test và sửa logic)
- R02: 8-16 giờ (cần thay đổi API approach hoặc thêm dedup table)

---

**Reviewed by:** Agent 2
**Next action:** Agent 3 sẽ đề xuất fix方案 cụ thể

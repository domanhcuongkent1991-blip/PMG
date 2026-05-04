# AGENT2 REPORT: R01 Sync Race Condition Fix Plan

**Agent:** Agent 2 - Fix Planning
**Date:** 2026-05-04
**Scope:** R01 - Race condition khi push trong khi user đang sửa cùng bản ghi
**Constraint:** Write-only to `docs/review/AGENT2_R01_SYNC_RACE_FIX_PLAN.md`

---

## 1. Mô tả Bug R01

**Bug:** Race condition trong `syncPending()` khiến queue bị xóa sớm, dẫn đến thay đổi local trong lúc sync không được re-push lên Google Sheet.

**Hậu quả:** User sửa bản ghi, bấm sync, bản ghi được push thành công. Nếu user tiếp tục sửa bản ghi đó trước khi queue bị xóa, thay đổi thứ 2 sẽ không bao giờ được push lên sheet (trừ khi user tạo thao tác mới như saveLog/updateRepairDate).

---

## 2. Nguyên nhân chính xác

### Timeline lỗi:

```
T1: User A sửa bản ghi X → updatedAt=1000, enqueue(X)
T2: syncPending() chạy
    - Lấy queueSnapshot = [X]
    - Lấy pushedLog = DeviceLog(recordId=X, updatedAt=1000)
    - pushLogs() thành công
T3: User A sửa lại bản ghi X → updatedAt=2000 (KHÔNG tạo queue mới!)
T4: syncPending() kiểm tra mark sync:
    - current = deviceLogDao.getById(X) → updatedAt=2000
    - shouldMarkAsSynced(current, pushedLog) → false (vì 2000 != 1000)
    - → Không mark SYNCED (ĐÚNG)
    - queueSnapshot.forEach { deleteById(it.id) } → X bị XÓA KHỎI QUEUE!
T5: User A bấm sync lại
    - Queue rỗng → Không có gì để push
    - Thay đổi tại T3 không bao giờ được push lên sheet!
```

### Tại sao không tạo queue mới tại T3?

Trong `enqueueUpsert()`:
```kotlin
private suspend fun enqueueUpsert(recordId: String) {
    syncQueueDao.deleteByRecordId(recordId)  // ← Xóa queue cũ
    syncQueueDao.insert(SyncQueueEntity(...)) // ← Tạo queue mới
}
```

Tại T3, user sửa bản ghi nhưng **trong cùng sync run**, nên flow đi qua:
- `updateRepairDate()` → `enqueueUpsert()` → `deleteByRecordId()` + `insert()`

Điều này tạo queue mới với `createdAt` mới. Tuy nhiên, sync đang chạy đã lấy snapshot tại T2, nên queue mới tại T3 **không nằm trong snapshot** và sẽ không bị xóa.

**Nhưng vấn đề thực sự khác:** User có thể sửa bản ghi X tại T3 trong cùng một lần sửa (ví dụ: double-tap, hoặc rapid save). Hoặc user sửa sau khi sync bắt đầu nhưng trước khi queue bị xóa hoàn toàn.

### Vấn đề cốt lõi:

```kotlin
// DeviceLogRepositoryImpl.kt dòng 137
queueSnapshot.forEach { item -> syncQueueDao.deleteById(item.id) }
```

Code này xóa queue **cho tất cả** records trong snapshot, bất kể record đó có thay đổi sau khi push hay không.

Logic `shouldMarkAsSynced()` đúng ở chỗ không mark SYNCED khi có thay đổi, nhưng **không bảo vệ queue** khỏi bị xóa.

---

## 3. Phương án Fix (Tối thiểu, Không đổi kiến trúc)

### Phương án A: Chỉ xóa queue cho records thực sự mark SYNCED

**Logic:** Chỉ xóa queue item khi và chỉ khi record đã được mark SYNCED thành công.

```kotlin
// Sửa trong syncPending(), sau khi push thành công
val recordsMarkedSynced = mutableSetOf<String>()

syncCandidateLogs.forEach { pushedLog ->
    val current = deviceLogDao.getById(pushedLog.recordId)
    if (shouldMarkAsSynced(current, pushedLog)) {
        val currentLog = current ?: return@forEach
        deviceLogDao.upsert(currentLog.copy(syncStatus = "SYNCED"))
        markedSyncedCount += 1
        recordsMarkedSynced.add(pushedLog.recordId)  // ← Track records đã mark
    } else {
        staleCount += 1
    }
}

// Chỉ xóa queue cho records đã thực sự mark SYNCED
queueSnapshot.forEach { item ->
    if (item.recordId in recordsMarkedSynced) {
        syncQueueDao.deleteById(item.id)
    }
    // Records không mark SYNCED → giữ lại queue cho sync tiếp theo
}
```

**Ưu điểm:**
- Fix chính xác vấn đề: queue chỉ bị xóa khi record đã thực sự sync
- Không cần thay đổi schema database
- Không cần thêm method mới vào DAO
- Logic đơn giản, dễ hiểu

**Nhược điểm:**
- Queue sẽ tồn tại lâu hơn cho records đang thay đổi liên tục
- Có thể tạo nhiều queue entries cho cùng recordId (nhưng `deleteByRecordId` trong `enqueueUpsert` sẽ clean)

---

### Phương án B: Kiểm tra queue mới trước khi xóa

**Logic:** Trước khi xóa queue, kiểm tra xem có queue entry mới nào được tạo sau khi sync bắt đầu không.

```kotlin
// Sửa trong syncPending()
val syncStartTime = System.currentTimeMillis()
val newQueueEntries = mutableSetOf<String>()

// ... sau khi push thành công và mark synced ...

// Kiểm tra queue mới tạo sau khi sync bắt đầu
val allQueueAfterSync = syncQueueDao.getAll()
allQueueAfterSync.forEach { item ->
    if (item.createdAt > syncStartTime && item.operation == OP_UPSERT_LOG) {
        newQueueEntries.add(item.recordId)
    }
}

// Không xóa queue cho records có queue entry mới
queueSnapshot.forEach { item ->
    if (item.recordId !in newQueueEntries) {
        syncQueueDao.deleteById(item.id)
    }
}
```

**Ưu điểm:**
- Bắt chính xác trường hợp user sửa record sau khi sync bắt đầu

**Nhược điểm:**
- Cần thêm 1 query `getAll()` trong sync flow
- Logic phức tạp hơn
- Có thể miss nếu user sửa record gần nhưng queue entry cũ hơn syncStartTime (edge case hiếm)

---

### Đề xuất: Phương án A

Phương án A đơn giản hơn và giải quyết đúng vấn đề. Phương án B thêm độ phức tạp không cần thiết.

---

## 4. Test Plan chi tiết (Arrange / Act / Assert)

### Test 1: syncPending_withStaleLocalChanges_keepsQueueForNextSync

**Mục tiêu:** Verify queue không bị xóa khi record đã thay đổi sau khi push.

```kotlin
@Test
fun syncPending_withStaleLocalChanges_keepsQueueForNextSync() = runTest {
    // Arrange
    val recordId = "test-record-1"
    val oldTimestamp = 1000L
    val newTimestamp = 2000L

    // Insert record với updatedAt=1000
    val originalEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB001",
        ngayPhatHien = "2026-01-01",
        hangMuc = "Hư màn hình",
        tinhTrang = "Chưa sửa",
        ngaySuaChua = null,
        ghiChu = "",
        updatedAt = oldTimestamp,
        syncStatus = "PENDING"
    )
    deviceLogDao.upsert(originalEntity)

    // Enqueue record
    repository.updateRepairDate(recordId, null, "")

    // Act: Gọi syncPending() - push thành công
    // Mock remote push success
    val syncResult = repository.syncPending()

    // Simulate: User sửa lại record sau khi push nhưng trước khi queue bị xóa
    // (Trong thực tế, điều này xảy ra nếu user sửa trong khoảng thời gian ngắn)
    val updatedEntity = originalEntity.copy(
        updatedAt = newTimestamp,
        ghiChu = "Sửa lại sau sync"
    )
    deviceLogDao.upsert(updatedEntity)

    // Gọi syncPending() lần 2 - push thành công
    val syncResult2 = repository.syncPending()

    // Assert
    assertThat(syncResult.isSuccess).isTrue()
    assertThat(syncResult2.isSuccess).isTrue()

    // Record phải được mark SYNCED sau sync thứ 2
    val finalEntity = deviceLogDao.getById(recordId)
    assertThat(finalEntity?.syncStatus).isEqualTo("SYNCED")
}
```

---

### Test 2: syncPending_withConcurrentEditDuringSync_doesNotLoseChanges

**Mục tiêu:** Verify thay đổi trong lúc sync không bị mất.

```kotlin
@Test
fun syncPending_withConcurrentEditDuringSync_doesNotLoseChanges() = runTest {
    // Arrange
    val recordId = "concurrent-test"
    val t1 = 1000L
    val t2 = 2000L

    // Tạo record tại T1
    val entityT1 = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB002",
        ngayPhatHien = "2026-01-01",
        hangMuc = "Hư pin",
        tinhTrang = "Chưa sửa",
        updatedAt = t1,
        syncStatus = "PENDING"
    )
    deviceLogDao.upsert(entityT1)
    repository.saveLog(entityT1.toDomain())

    // Act: Simulate sync bắt đầu, lấy snapshot
    val queueSnapshotBefore = syncQueueDao.getAll().filter { it.recordId == recordId }
    assertThat(queueSnapshotBefore).hasSize(1)

    // User sửa record tại T2 (sau khi sync bắt đầu nhưng trước khi hoàn thành)
    val entityT2 = entityT1.copy(
        updatedAt = t2,
        ghiChu = "Sửa lúc đồng bộ đang chạy"
    )
    deviceLogDao.upsert(entityT2)

    // Hoàn thành sync
    val result = repository.syncPending()

    // Assert
    assertThat(result.isSuccess).isTrue()

    // Sync tiếp theo phải push bản T2 lên
    val nextResult = repository.syncPending()
    assertThat(nextResult.isSuccess).isTrue()

    // Record phải ở trạng thái SYNCED với nội dung T2
    val finalEntity = deviceLogDao.getById(recordId)
    assertThat(finalEntity?.syncStatus).isEqualTo("SYNCED")
    assertThat(finalEntity?.ghiChu).isEqualTo("Sửa lúc đồng bộ đang chạy")
}
```

---

### Test 3: syncPending_multipleRecordsPartialStale_keepsQueueOnlyForStale

**Mục tiêu:** Verify chỉ queue của records đã thay đổi được giữ lại.

```kotlin
@Test
fun syncPending_multipleRecordsPartialStale_keepsQueueOnlyForStale() = runTest {
    // Arrange
    val stableRecord = "stable-record"
    val staleRecord = "stale-record"

    // Record 1: Không thay đổi sau sync
    val stableEntity = DeviceLogEntity(
        recordId = stableRecord,
        maThietBi = "TB003",
        updatedAt = 1000L,
        syncStatus = "PENDING"
    )
    deviceLogDao.upsert(stableEntity)

    // Record 2: Thay đổi sau sync
    val staleEntity = DeviceLogEntity(
        recordId = staleRecord,
        maThietBi = "TB004",
        updatedAt = 1000L,
        syncStatus = "PENDING"
    )
    deviceLogDao.upsert(staleEntity)

    // Enqueue cả 2
    repository.saveLog(stableEntity.toDomain())
    repository.saveLog(staleEntity.toDomain())

    // Act: Sync lần 1
    repository.syncPending()

    // Thay đổi record 2
    val updatedStale = staleEntity.copy(updatedAt = 2000L, ghiChu = "Updated after sync")
    deviceLogDao.upsert(updatedStale)

    // Sync lần 2
    repository.syncPending()

    // Assert: Record 1 phải SYNCED, record 2 phải được sync lại
    val finalStable = deviceLogDao.getById(stableRecord)
    val finalStale = deviceLogDao.getById(staleRecord)

    assertThat(finalStable?.syncStatus).isEqualTo("SYNCED")
    assertThat(finalStale?.syncStatus).isEqualTo("SYNCED")
    assertThat(finalStale?.ghiChu).isEqualTo("Updated after sync")
}
```

---

## 5. File cần sửa (Lượt sau - Implementation)

### 5.1 DeviceLogRepositoryImpl.kt

**Vị trí:** `android-mvp/app/src/main/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImpl.kt`

**Thay đổi:** Sửa method `syncPending()` - dòng 121-149

**Logic mới:**
```kotlin
val result = remoteDataSource.pushLogs(syncCandidateLogs)
if (result.isSuccess) {
    var markedSyncedCount = 0
    var staleCount = 0
    val recordsMarkedSynced = mutableSetOf<String>()  // ← Thêm biến track

    syncCandidateLogs.forEach { pushedLog ->
        val current = deviceLogDao.getById(pushedLog.recordId)
        if (shouldMarkAsSynced(current, pushedLog)) {
            val currentLog = current ?: return@forEach
            deviceLogDao.upsert(currentLog.copy(syncStatus = "SYNCED"))
            markedSyncedCount += 1
            recordsMarkedSynced.add(pushedLog.recordId)  // ← Track
        } else {
            staleCount += 1
        }
    }

    // Chỉ xóa queue cho records đã mark SYNCED
    queueSnapshot.forEach { item ->
        if (item.recordId in recordsMarkedSynced) {
            syncQueueDao.deleteById(item.id)
        }
        // else: giữ lại queue để sync tiếp theo push lại bản mới
    }

    Log.i(
        TAG,
        "syncPending success: queueDeleted=${recordsMarkedSynced.size}, keptQueue=${staleCount}, markedSynced=$markedSyncedCount, staleLocal=$staleCount"
    )
    return Result.success(Unit)
}
```

---

### 5.2 Unit Test File (New)

**Vị trí mới:** `android-mvp/app/src/test/java/com/example/devicetracker/data/repository/DeviceLogRepositoryImplSyncRaceTest.kt`

**Test cases cần thêm:**
1. `syncPending_withStaleLocalChanges_keepsQueueForNextSync`
2. `syncPending_withConcurrentEditDuringSync_doesNotLoseChanges`
3. `syncPending_multipleRecordsPartialStale_keepsQueueOnlyForStale`

---

## 6. Rủi ro của từng phương án fix

### Phương án A (Khuyến nghị)

| Rủi ro | Mức độ | Giải thích |
|--------|--------|------------|
| Queue tích tụ nếu user sửa liên tục | THẤP | `deleteByRecordId` trong `enqueueUpsert` sẽ clean queue cũ trước khi tạo mới |
| Sync chạy nhiều lần cho 1 record | THẤP | Remote `upsert` đã có deduplication, không tạo duplicate row |
| Queue size tăng | THẤP | Queue chỉ tích tụ cho records đang thay đổi liên tục, không phải tất cả |

### Phương án B

| Rủi ro | Mức độ | Giải thích |
|--------|--------|------------|
| Thêm 1 query `getAll()` mỗi sync | TRUNG BÌNH | Tăng latency cho sync flow |
| Edge case: timestamp gần nhau | THẤP | Hiếm khi xảy ra trong thực tế |
| Logic phức tạp hơn, khó maintain | TRUNG BÌNH | Cần giải thích rõ trong code review |

---

## 7. Không đề xuất refactor rộng

**Giữ nguyên:**
- Schema database (sync_queue table)
- `SyncQueueDao` interface
- `SheetsRemoteDataSource` 
- Flow sync chung (snapshot → push → mark → delete)

**Chỉ thay đổi:**
- Logic xóa queue trong `syncPending()`
- Thêm unit test

---

## 8. Checklist cho lượt implement

- [ ] Sửa `DeviceLogRepositoryImpl.syncPending()` theo phương án A
- [ ] Thêm unit test `DeviceLogRepositoryImplSyncRaceTest.kt`
- [ ] Chạy tất cả unit test hiện có, verify không break
- [ ] Verify log output: `keptQueue=N` đúng khi có stale records
- [ ] Optional: Thêm metric/strict mode cho stale count

---

**Reviewed by:** Agent 2
**Status:** Ready for implementation by next agent

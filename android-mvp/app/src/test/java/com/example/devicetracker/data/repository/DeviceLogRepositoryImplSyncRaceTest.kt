package com.example.devicetracker.data.repository

import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for R01 sync race condition fix.
 * Verifies that the logic for determining when to mark records as SYNCED
 * and when to keep queue items is correct.
 *
 * These tests verify the core fix: queue items should only be deleted
 * when records are actually marked SYNCED.
 */
class DeviceLogRepositoryImplSyncRaceTest {

    // ==================== Core Logic Tests ====================

    /**
     * R01 Test 1: verify shouldMarkAsSynced logic
     *
     * Core fix: shouldMarkAsSynced returns true only when local.updatedAt matches pushed.updatedAt.
     * This is the gate that determines whether queue item gets deleted.
     */
    @Test
    fun shouldMarkAsSynced_returnsTrueOnlyWhenUpdatedAtMatches() {
        val pushedVersion = DeviceLog(
            recordId = "test-1",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )

        // Case 1: Local matches pushed - should mark SYNCED
        val localMatching = DeviceLogEntity(
            recordId = "test-1",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L,  // Same as pushed
            syncStatus = "PENDING"
        )
        assertTrue(shouldMarkAsSynced(localMatching, pushedVersion))

        // Case 2: Local is newer (stale) - should NOT mark SYNCED
        val localStale = DeviceLogEntity(
            recordId = "test-1",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Updated after sync",
            updatedAt = 2000L,  // Newer than pushed
            syncStatus = "PENDING"
        )
        assertFalse(shouldMarkAsSynced(localStale, pushedVersion))

        // Case 3: Local is older - should NOT mark SYNCED
        // Logic only checks equality: updatedAt must match exactly
        val localOlder = DeviceLogEntity(
            recordId = "test-1",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 500L,  // Different from pushed (1000L)
            syncStatus = "PENDING"
        )
        assertFalse(shouldMarkAsSynced(localOlder, pushedVersion))

        // Case 4: Local is null - should NOT mark SYNCED
        assertFalse(shouldMarkAsSynced(null, pushedVersion))
    }

    /**
     * R01 Test 2: Race condition simulation
     *
     * Scenario: User edits record during sync window
     * - First sync pushes record with updatedAt=1000
     * - Local record has updatedAt=2000 (changed after push started)
     * - Queue must be kept for next sync
     */
    @Test
    fun raceCondition_localChangedDuringSync_keepsQueue() {
        // Timeline:
        // T1: User edits record → local updatedAt=1000, queue created
        // T2: Sync starts, gets queueSnapshot
        // T3: User edits again → local updatedAt=2000 (queue still exists)
        // T4: Push happens with version updatedAt=1000
        // T5: shouldMarkAsSynced check: 2000 != 1000 → false
        // T6: Queue item should NOT be deleted (fix for R01)

        val recordId = "race-test-record"

        // Pushed version (from syncCandidateLogs)
        val pushedVersion = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư pin",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )

        // Local version AFTER push (user edited during sync)
        val localAfterEdit = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư pin",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Sửa lại lúc sync đang chạy",
            updatedAt = 2000L,  // Changed after push
            syncStatus = "PENDING"
        )

        // shouldMarkAsSynced returns false → queue should be kept
        val shouldMark = shouldMarkAsSynced(localAfterEdit, pushedVersion)
        assertFalse(shouldMark)

        // This proves the fix: when shouldMarkAsSynced is false,
        // queue item should NOT be deleted
    }

    /**
     * R01 Test 3: Normal sync scenario
     *
     * When no changes happen during sync, all records match and all queue items are deleted.
     */
    @Test
    fun normalSync_allRecordsMatch_queuesDeleted() {
        val recordId = "normal-sync-record"

        val pushedVersion = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )

        // Local version matches pushed
        val localMatching = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L,  // Same as pushed
            syncStatus = "PENDING"
        )

        // shouldMarkAsSynced returns true → queue item CAN be deleted
        assertTrue(shouldMarkAsSynced(localMatching, pushedVersion))
    }

    /**
     * R01 Test 4: Partial stale scenario
     *
     * With multiple records:
     * - Record A: no change → shouldMarkAsSynced=true → queue deleted
     * - Record B: changed → shouldMarkAsSynced=false → queue kept
     */
    @Test
    fun partialStale_onlyStaleRecordsKeepQueues() {
        // Record A: stable (no change during sync)
        val pushedA = DeviceLog(
            recordId = "stable-record",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )
        val localA = DeviceLogEntity(
            recordId = "stable-record",
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L,
            syncStatus = "PENDING"
        )

        // Record B: stale (changed during sync)
        val pushedB = DeviceLog(
            recordId = "stale-record",
            maThietBi = "TB002",
            hangMuc = "Hư pin",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )
        val localB = DeviceLogEntity(
            recordId = "stale-record",
            maThietBi = "TB002",
            hangMuc = "Hư pin",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Updated during sync",
            updatedAt = 2000L,  // Changed
            syncStatus = "PENDING"
        )

        // Verify logic
        assertTrue(shouldMarkAsSynced(localA, pushedA))  // Stable: can delete queue
        assertFalse(shouldMarkAsSynced(localB, pushedB))  // Stale: must keep queue
    }

    /**
     * R01 Test 5: Multiple edits chain
     *
     * Verify that after first sync keeps queue for stale record,
     * second sync will mark it as SYNCED once local matches.
     */
    @Test
    fun multipleEditsChain_syncEventuallySucceeds() {
        val recordId = "chain-test"

        // First sync: pushed version
        val pushedV1 = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Version 1",
            updatedAt = 1000L
        )

        // First sync: local is newer (stale)
        val localV2 = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Version 2",
            updatedAt = 2000L,  // Newer
            syncStatus = "PENDING"
        )

        // First sync: should NOT mark SYNCED
        assertFalse(shouldMarkAsSynced(localV2, pushedV1))

        // Second sync: pushed version (from queue)
        val pushedV2 = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Version 2",
            updatedAt = 2000L  // Now matches local
        )

        // Second sync: local matches pushed version
        assertTrue(shouldMarkAsSynced(localV2, pushedV2))

        // Queue would be deleted after second sync, and record marked SYNCED
    }

    /**
     * R01 Test 6: Queue deletion logic simulation
     *
     * Simulates the fix logic:
     * val recordsMarkedSynced = mutableSetOf<String>()
     * syncCandidateLogs.forEach { pushedLog ->
     *     val current = deviceLogDao.getById(pushedLog.recordId)
     *     if (shouldMarkAsSynced(current, pushedLog)) {
     *         recordsMarkedSynced.add(pushedLog.recordId)
     *     }
     * }
     * queueSnapshot.forEach { item ->
     *     if (item.recordId in recordsMarkedSynced) {
     *         syncQueueDao.deleteById(item.id)
     *     }
     * }
     */
    @Test
    fun queueDeletionLogic_simulatedCorrectly() {
        data class QueueItem(val id: Long, val recordId: String)
        data class SimResult(val deletedIds: MutableList<Long>, val keptRecordIds: MutableList<String>)

        val recordId = "queue-test"

        // Simulate syncCandidateLogs with pushed version
        val pushedVersion = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = 1000L
        )

        // Simulate local state AFTER push (user edited during sync)
        val localStale = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Changed during sync",
            updatedAt = 2000L,  // Stale
            syncStatus = "PENDING"
        )

        // Simulate queueSnapshot
        val queueSnapshot = listOf(QueueItem(id = 1L, recordId = recordId))

        // Simulate the fixed logic
        val recordsMarkedSynced = mutableSetOf<String>()

        // Step 1: Check shouldMarkAsSynced
        if (shouldMarkAsSynced(localStale, pushedVersion)) {
            recordsMarkedSynced.add(pushedVersion.recordId)
        }

        // Step 2: Delete only records in recordsMarkedSynced
        val deletedIds = mutableListOf<Long>()
        queueSnapshot.forEach { item ->
            if (item.recordId in recordsMarkedSynced) {
                deletedIds.add(item.id)
            }
        }

        // Assert: Queue item should NOT be deleted (stale record)
        assertEquals(0, deletedIds.size)
        assertEquals(0, recordsMarkedSynced.size)
    }

    // ==================== Near-Real Flow Tests ====================

    /**
     * R01 Test 7: Real flow simulation - Stale queue kept
     *
     * Gần flow thật nhất:
     * - Tạo queue snapshot với 1 SyncQueueEntity
     * - Pushed log có updatedAt=1000
     * - Local record sau push có updatedAt=2000 (stale)
     * - Verify queue phải được giữ lại (không xóa)
     */
    @Test
    fun nearRealFlow_staleQueue_mustBeKept() {
        val recordId = "stale-queue-record"

        // Queue snapshot như syncPending() lấy được
        val queueSnapshot = listOf(
            QueueSnapshotItem(id = 1L, recordId = recordId)
        )

        // Pushed log - version đã push lên server
        val pushedLog = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Original content",
            updatedAt = 1000L  // Bản đã push
        )

        // Local record - bản mới nhất trong database
        val localRecord = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Changed during sync",
            updatedAt = 2000L,  // Bản mới - stale
            syncStatus = "PENDING"
        )

        // Mô phỏng logic syncPending() sau khi push thành công
        val syncCandidateLogs = listOf(pushedLog)
        val recordsMarkedSynced = simulateSyncPendingPostPush(
            syncCandidateLogs = syncCandidateLogs,
            localRecord = localRecord
        )

        // Mô phỏng xóa queue
        val deletedIds = simulateQueueDeletion(
            queueSnapshot = queueSnapshot,
            recordsMarkedSynced = recordsMarkedSynced
        )

        // ASSERT: Queue phải được giữ lại vì record stale
        assertTrue("Queue phải được giữ lại vì local đã thay đổi sau khi push",
            deletedIds.isEmpty())

        // ASSERT: Record không được mark SYNCED
        assertFalse("Record không được mark SYNCED vì stale",
            recordsMarkedSynced.contains(recordId))
    }

    /**
     * R01 Test 8: Real flow simulation - Stable queue deleted
     *
     * Trường hợp bình thường:
     * - Queue snapshot với 1 item
     * - Pushed log và local record cùng updatedAt
     * - Verify queue phải được xóa
     */
    @Test
    fun nearRealFlow_stableQueue_mustBeDeleted() {
        val recordId = "stable-queue-record"

        // Queue snapshot
        val queueSnapshot = listOf(
            QueueSnapshotItem(id = 2L, recordId = recordId)
        )

        // Pushed log - version đã push
        val pushedLog = DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Stable content",
            updatedAt = 1000L
        )

        // Local record - khớp với pushed (không thay đổi)
        val localRecord = DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Hư màn hình",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Stable content",
            updatedAt = 1000L,  // Cùng updatedAt - stable
            syncStatus = "PENDING"
        )

        // Mô phỏng logic syncPending()
        val syncCandidateLogs = listOf(pushedLog)
        val recordsMarkedSynced = simulateSyncPendingPostPush(
            syncCandidateLogs = syncCandidateLogs,
            localRecord = localRecord
        )

        // Mô phỏng xóa queue
        val deletedIds = simulateQueueDeletion(
            queueSnapshot = queueSnapshot,
            recordsMarkedSynced = recordsMarkedSynced
        )

        // ASSERT: Queue phải được xóa vì record stable
        assertTrue("Queue phải được xóa vì local khớp với pushed",
            deletedIds.contains(2L))

        // ASSERT: Record phải được mark SYNCED
        assertTrue("Record phải được mark SYNCED",
            recordsMarkedSynced.contains(recordId))
    }

    /**
     * R01 Test 9: Real flow - Partial stale (mixed scenario)
     *
     * 2 records trong queue:
     * - Record A: stable → queue xóa
     * - Record B: stale → queue giữ
     */
    @Test
    fun nearRealFlow_partialStale_mixedBehavior() {
        val stableRecordId = "stable-record"
        val staleRecordId = "stale-record"

        // Queue snapshot với 2 items
        val queueSnapshot = listOf(
            QueueSnapshotItem(id = 1L, recordId = stableRecordId),
            QueueSnapshotItem(id = 2L, recordId = staleRecordId)
        )

        // Stable record: local và pushed cùng updatedAt
        val stablePushed = DeviceLog(
            recordId = stableRecordId,
            maThietBi = "TB001",
            hangMuc = "Hư A",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Stable",
            updatedAt = 1000L
        )
        val stableLocal = DeviceLogEntity(
            recordId = stableRecordId,
            maThietBi = "TB001",
            hangMuc = "Hư A",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Stable",
            updatedAt = 1000L,
            syncStatus = "PENDING"
        )

        // Stale record: local mới hơn pushed
        val stalePushed = DeviceLog(
            recordId = staleRecordId,
            maThietBi = "TB002",
            hangMuc = "Hư B",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Old",
            updatedAt = 1000L
        )
        val staleLocal = DeviceLogEntity(
            recordId = staleRecordId,
            maThietBi = "TB002",
            hangMuc = "Hư B",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tình trạng",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "2026-01-01",
            ngaySuaChua = null,
            ghiChu = "Changed after push",
            updatedAt = 2000L,  // Stale
            syncStatus = "PENDING"
        )

        // Mô phỏng sync với 2 records
        val syncCandidateLogs = listOf(stablePushed, stalePushed)

        // Xử lý stable record
        val stableMarked = simulateSyncPendingPostPush(
            syncCandidateLogs = listOf(stablePushed),
            localRecord = stableLocal
        )

        // Xử lý stale record
        val staleMarked = simulateSyncPendingPostPush(
            syncCandidateLogs = listOf(stalePushed),
            localRecord = staleLocal
        )

        val recordsMarkedSynced = stableMarked + staleMarked

        // Mô phỏng xóa queue
        val deletedIds = simulateQueueDeletion(
            queueSnapshot = queueSnapshot,
            recordsMarkedSynced = recordsMarkedSynced.toSet()
        )

        // ASSERT: Chỉ queue của stable record được xóa
        assertEquals(1, deletedIds.size)
        assertTrue("Chỉ queue stable được xóa", deletedIds.contains(1L))
        assertFalse("Queue stale record giữ lại", deletedIds.contains(2L))

        // ASSERT: Chỉ stable record được mark SYNCED
        assertTrue("Stable được mark SYNCED", recordsMarkedSynced.contains(stableRecordId))
        assertFalse("Stale không được mark SYNCED", recordsMarkedSynced.contains(staleRecordId))
    }

    // ==================== Helper Data Classes ====================

    private data class QueueSnapshotItem(val id: Long, val recordId: String)

    /**
     * Mô phỏng phần post-push của syncPending()
     * Trả về danh sách recordId đã được mark SYNCED
     */
    private fun simulateSyncPendingPostPush(
        syncCandidateLogs: List<DeviceLog>,
        localRecord: DeviceLogEntity
    ): Set<String> {
        val recordsMarkedSynced = mutableSetOf<String>()

        syncCandidateLogs.forEach { pushedLog ->
            if (shouldMarkAsSynced(localRecord, pushedLog)) {
                recordsMarkedSynced.add(pushedLog.recordId)
            }
        }

        return recordsMarkedSynced
    }

    /**
     * Mô phỏng logic xóa queue sau khi push thành công
     * Trả về danh sách queue ID đã bị xóa
     */
    private fun simulateQueueDeletion(
        queueSnapshot: List<QueueSnapshotItem>,
        recordsMarkedSynced: Set<String>
    ): List<Long> {
        val deletedIds = mutableListOf<Long>()

        queueSnapshot.forEach { item ->
            if (item.recordId in recordsMarkedSynced) {
                deletedIds.add(item.id)
            }
        }

        return deletedIds
    }
}

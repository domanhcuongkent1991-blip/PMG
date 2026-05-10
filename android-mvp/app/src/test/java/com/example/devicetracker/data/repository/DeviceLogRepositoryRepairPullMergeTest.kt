package com.example.devicetracker.data.repository

import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.model.DmbtRepairUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for A1-REPAIR-MERGE-IDENTITY-FIX.
 * Verifies repair log merge with RepairRecordIdentityResolver.
 */
class DeviceLogRepositoryRepairPullMergeTest {

    // ==================== Identity Resolution Tests ====================

    /**
     * Test 1: Exact match - repair recordId khớp chính xác với local recordId
     */
    @Test
    fun resolveIdentity_exactMatch_returnsMatchedId() {
        val repairRecordId = "readonly-dmbt-1607125070-TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1383308512-TB002-001"
        )

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertEquals(repairRecordId, result)
    }

    /**
     * Test 2: Base id match - repair nhập base id, local có đúng 1 record
     */
    @Test
    fun resolveIdentity_baseIdMatchUnique_resolvesCorrectly() {
        val repairRecordId = "TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1383308512-TB002-001"
        )

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertEquals("readonly-dmbt-1607125070-TB001-001", result)
    }

    /**
     * Test 3: Ambiguous - nhiều local records cùng base id
     */
    @Test
    fun resolveIdentity_ambiguous_returnsNull() {
        val repairRecordId = "TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1383308512-TB001-001"
        )

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertNull(result)
    }

    /**
     * Test 4: Not found - record không tồn tại trong local
     */
    @Test
    fun resolveIdentity_notFound_returnsNull() {
        val repairRecordId = "UNKNOWN-999"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001"
        )

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertNull(result)
    }

    @Test
    fun resolveIdentity_monthlyRepair_onlyMonthlyCandidates_canMergeMonthly() {
        val repairRecordId = "TB001-001"
        val monthlyCandidates = listOf("readonly-dmbt-1383308512-TB001-001")

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, monthlyCandidates)

        assertEquals("readonly-dmbt-1383308512-TB001-001", result)
    }

    @Test
    fun resolveIdentity_monthlyRepair_whenMonthlyCandidatesMissing_doesNotMergeYearly() {
        val repairRecordId = "TB001-001"
        val monthlyCandidates = emptyList<String>()

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, monthlyCandidates)

        assertNull(result)
    }

    /**
     * Test 5: Namespaced repair recordId resolve sang namespaced local
     */
    @Test
    fun resolveIdentity_namespacedToNamespaced_exactMatch() {
        val repairRecordId = "readonly-dmbt-1607125070-TB002-002"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1607125070-TB002-002"
        )

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertEquals("readonly-dmbt-1607125070-TB002-002", result)
    }

    // ==================== shouldMergeRepairIntoLocal Tests ====================

    /**
     * Test 6: Repair log merge - local is SYNCED and remote is newer
     * → Should merge
     */
    @Test
    fun shouldMergeRepairIntoLocal_syncedLocalNewerRemote_merges() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 1000L,
            syncStatus = "SYNCED",
            ngaySuaChua = null,
            ghiChu = ""
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Đã sửa xong",
            updatedAt = 2000L
        )

        assertTrue(shouldMergeRepairIntoLocal(local, repairLog))
    }

    /**
     * Test 7: Repair log merge - local is SYNCED and remote is equal
     * → Should merge
     */
    @Test
    fun shouldMergeRepairIntoLocal_syncedLocalEqualRemote_merges() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 1000L,
            syncStatus = "SYNCED"
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Đã sửa",
            updatedAt = 1000L
        )

        assertTrue(shouldMergeRepairIntoLocal(local, repairLog))
    }

    /**
     * Test 8: Repair log merge - local is PENDING
     * → Should NOT merge (preserve user offline data)
     */
    @Test
    fun shouldMergeRepairIntoLocal_pendingLocal_doesNotMerge() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 1000L,
            syncStatus = "PENDING"
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Đã sửa",
            updatedAt = 2000L
        )

        assertFalse(shouldMergeRepairIntoLocal(local, repairLog))
    }

    /**
     * Test 9: Repair log merge - local is FAILED
     * → Should NOT merge (preserve user offline data)
     */
    @Test
    fun shouldMergeRepairIntoLocal_failedLocal_doesNotMerge() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 1000L,
            syncStatus = "FAILED"
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Đã sửa",
            updatedAt = 2000L
        )

        assertFalse(shouldMergeRepairIntoLocal(local, repairLog))
    }

    /**
     * Test 10: Repair log merge - remote is older than local
     * → Should NOT merge (local was edited after repair was created)
     */
    @Test
    fun shouldMergeRepairIntoLocal_localIsNewer_doesNotMerge() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 2000L,
            syncStatus = "SYNCED"
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Đã sửa",
            updatedAt = 1000L
        )

        assertFalse(shouldMergeRepairIntoLocal(local, repairLog))
    }

    @Test
    fun shouldMergeRepairIntoLocal_mergesManualSheetRepair_whenUpdatedAtMissingButContentChanged() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 2000L,
            syncStatus = "SYNCED",
            ngaySuaChua = null,
            ghiChu = ""
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "05/05/2026",
            ghiChu = "test",
            updatedAt = 0L
        )

        assertTrue(shouldMergeRepairIntoLocal(local, repairLog))
    }

    @Test
    fun shouldMergeRepairIntoLocal_skipsManualSheetRepair_whenUpdatedAtMissingAndContentSame() {
        val local = createEntity(
            recordId = "r1",
            updatedAt = 2000L,
            syncStatus = "SYNCED",
            ngaySuaChua = "05/05/2026",
            ghiChu = "test"
        )
        val repairLog = DmbtRepairUpdate(
            recordId = "r1",
            maThietBi = "TB001",
            ngaySuaChua = "05/05/2026",
            ghiChu = "test",
            updatedAt = 0L
        )

        assertFalse(shouldMergeRepairIntoLocal(local, repairLog))
    }

    // ==================== Integration Tests ====================

    /**
     * Test 11: Full flow - resolve + merge với namespaced identity
     */
    @Test
    fun integration_resolveAndMerge_namespacedIdentity() {
        val repairRecordId = "TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1383308512-TB002-001"
        )

        // Step 1: Resolve
        val resolvedId = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)
        assertEquals("readonly-dmbt-1607125070-TB001-001", resolvedId)

        // Step 2: Create local entity (simulating getById)
        val local = createEntity(
            recordId = resolvedId!!,
            updatedAt = 1000L,
            syncStatus = "SYNCED"
        )

        // Step 3: Create repair log
        val repairLog = DmbtRepairUpdate(
            recordId = repairRecordId,
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Màn hình đã thay",
            updatedAt = 2000L
        )

        // Step 4: Check merge eligibility
        assertTrue(shouldMergeRepairIntoLocal(local, repairLog))

        // Step 5: Simulate merge
        val merged = local.copy(
            ngaySuaChua = repairLog.ngaySuaChua,
            ghiChu = repairLog.ghiChu,
            updatedAt = repairLog.updatedAt,
            syncStatus = "SYNCED"
        )

        assertEquals("2026-01-15", merged.ngaySuaChua)
        assertEquals("Màn hình đã thay", merged.ghiChu)
        assertEquals(2000L, merged.updatedAt)
    }

    /**
     * Test 12: Full flow - ambiguous skip
     */
    @Test
    fun integration_resolveAmbiguous_skips() {
        val repairRecordId = "TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001",
            "readonly-dmbt-1383308512-TB001-001"
        )

        val resolvedId = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertNull(resolvedId)
        // In actual code: skippedAmbiguous += 1
    }

    /**
     * Test 13: Full flow - PENDING local not overwritten
     */
    @Test
    fun integration_pendingLocal_notOverwritten() {
        val repairRecordId = "TB001-001"
        val localRecordIds = listOf(
            "readonly-dmbt-1607125070-TB001-001"
        )

        val resolvedId = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)
        assertEquals("readonly-dmbt-1607125070-TB001-001", resolvedId)

        // Local is PENDING
        val local = createEntity(
            recordId = resolvedId!!,
            updatedAt = 500L,
            syncStatus = "PENDING",
            ghiChu = "User offline notes..."
        )

        val repairLog = DmbtRepairUpdate(
            recordId = repairRecordId,
            maThietBi = "TB001",
            ngaySuaChua = "2026-01-15",
            ghiChu = "Sheet notes",
            updatedAt = 2000L
        )

        // Should NOT merge
        assertFalse(shouldMergeRepairIntoLocal(local, repairLog))

        // Local data preserved
        assertEquals("PENDING", local.syncStatus)
        assertEquals(500L, local.updatedAt)
        assertEquals("User offline notes...", local.ghiChu)
    }

    /**
     * Test 14: Empty local recordIds returns null
     */
    @Test
    fun resolveIdentity_emptyLocal_returnsNull() {
        val repairRecordId = "TB001-001"
        val localRecordIds = emptyList<String>()

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertNull(result)
    }

    /**
     * Test 15: Empty repair recordId returns null
     */
    @Test
    fun resolveIdentity_emptyRepair_returnsNull() {
        val repairRecordId = ""
        val localRecordIds = listOf("readonly-dmbt-1607125070-TB001-001")

        val result = RepairRecordIdentityResolver.resolveRepairRecordId(repairRecordId, localRecordIds)

        assertNull(result)
    }

    // ==================== Helper ====================

    private fun createEntity(
        recordId: String,
        updatedAt: Long,
        syncStatus: String,
        ngaySuaChua: String? = null,
        ghiChu: String = ""
    ): DeviceLogEntity = DeviceLogEntity(
        recordId = recordId,
        maThietBi = "TB001",
        hangMuc = "Hư màn hình",
        nguoiBaoCao = "A",
        tinhTrangThietBi = "Tình trạng",
        ktvPhuTrach = "KTV",
        ngayPhatHien = "2026-01-01",
        ngaySuaChua = ngaySuaChua,
        ghiChu = ghiChu,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )
}

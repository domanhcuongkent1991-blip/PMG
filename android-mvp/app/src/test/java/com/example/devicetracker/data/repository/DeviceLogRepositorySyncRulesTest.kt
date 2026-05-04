package com.example.devicetracker.data.repository

import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLogRepositorySyncRulesTest {

    @Test
    fun shouldMarkAsSynced_returns_true_only_when_updatedAt_matches() {
        val pushed = sampleDomainLog(recordId = "r1", updatedAt = 200L)
        val localSame = sampleEntity(recordId = "r1", updatedAt = 200L, syncStatus = "PENDING")
        val localNewer = sampleEntity(recordId = "r1", updatedAt = 300L, syncStatus = "PENDING")

        assertTrue(shouldMarkAsSynced(localSame, pushed))
        assertFalse(shouldMarkAsSynced(localNewer, pushed))
        assertFalse(shouldMarkAsSynced(null, pushed))
    }

    @Test
    fun shouldApplyRemoteLog_keeps_pending_local_changes() {
        val localPending = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "PENDING")
        val remote = sampleDomainLog(recordId = "r1", updatedAt = 700L)

        assertFalse(shouldApplyRemoteLog(localPending, remote))
    }

    @Test
    fun shouldApplyRemoteLog_keeps_failed_local_changes() {
        val localFailed = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "FAILED")
        val remote = sampleDomainLog(recordId = "r1", updatedAt = 700L)

        assertFalse(shouldApplyRemoteLog(localFailed, remote))
    }

    @Test
    fun shouldApplyRemoteLog_applies_when_local_is_synced_and_remote_is_newer_or_equal() {
        val localSynced = sampleEntity(recordId = "r1", updatedAt = 500L, syncStatus = "SYNCED")
        val remoteNewer = sampleDomainLog(recordId = "r1", updatedAt = 700L)
        val remoteEqual = sampleDomainLog(recordId = "r1", updatedAt = 500L)
        val remoteOlder = sampleDomainLog(recordId = "r1", updatedAt = 400L)

        assertTrue(shouldApplyRemoteLog(localSynced, remoteNewer))
        assertTrue(shouldApplyRemoteLog(localSynced, remoteEqual))
        assertFalse(shouldApplyRemoteLog(localSynced, remoteOlder))
    }

    @Test
    fun shouldApplyRemoteLog_applies_for_new_local_record() {
        val remote = sampleDomainLog(recordId = "new", updatedAt = 100L)
        assertTrue(shouldApplyRemoteLog(null, remote))
    }

    private fun sampleEntity(recordId: String, updatedAt: Long, syncStatus: String): DeviceLogEntity =
        DeviceLogEntity(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Xuong",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tinh trang",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "22/03/2026",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = updatedAt,
            syncStatus = syncStatus
        )

    private fun sampleDomainLog(recordId: String, updatedAt: Long): DeviceLog =
        DeviceLog(
            recordId = recordId,
            maThietBi = "TB001",
            hangMuc = "Xuong",
            nguoiBaoCao = "A",
            tinhTrangThietBi = "Tinh trang",
            ktvPhuTrach = "KTV",
            ngayPhatHien = "22/03/2026",
            ngaySuaChua = null,
            ghiChu = "",
            updatedAt = updatedAt
        )
}

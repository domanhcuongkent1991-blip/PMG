package com.example.devicetracker.data.repository

import com.example.devicetracker.data.local.entity.HgtCheckEntity
import com.example.devicetracker.domain.model.HgtCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HgtCheckRepositorySyncRulesTest {

    @Test
    fun shouldMarkHgtAsSynced_returns_true_only_when_updatedAt_matches() {
        val pushed = sampleDomain(updatedAt = 200L)
        val localSame = sampleEntity(updatedAt = 200L, syncStatus = "PENDING")
        val localNewer = sampleEntity(updatedAt = 300L, syncStatus = "PENDING")

        assertTrue(shouldMarkHgtAsSynced(localSame, pushed))
        assertFalse(shouldMarkHgtAsSynced(localNewer, pushed))
        assertFalse(shouldMarkHgtAsSynced(null, pushed))
    }

    @Test
    fun shouldApplyRemoteHgt_keeps_pending_local_changes() {
        val localPending = sampleEntity(updatedAt = 500L, syncStatus = "PENDING")
        val remote = sampleDomain(updatedAt = 700L)

        assertFalse(shouldApplyRemoteHgt(localPending, remote))
    }

    @Test
    fun shouldApplyRemoteHgt_applies_when_local_is_synced_and_remote_is_newer_or_equal() {
        val localSynced = sampleEntity(updatedAt = 500L, syncStatus = "SYNCED")
        val remoteNewer = sampleDomain(updatedAt = 700L)
        val remoteEqual = sampleDomain(updatedAt = 500L)
        val remoteOlder = sampleDomain(updatedAt = 400L)

        assertTrue(shouldApplyRemoteHgt(localSynced, remoteNewer))
        assertTrue(shouldApplyRemoteHgt(localSynced, remoteEqual))
        assertFalse(shouldApplyRemoteHgt(localSynced, remoteOlder))
    }

    @Test
    fun shouldApplyRemoteHgt_rejects_unknown_updatedAt() {
        val localSynced = sampleEntity(updatedAt = 500L, syncStatus = "SYNCED")
        val remoteUnknown = sampleDomain(updatedAt = 0L)
        assertFalse(shouldApplyRemoteHgt(localSynced, remoteUnknown))
    }

    @Test
    fun shouldApplyRemoteHgt_applies_unknown_updatedAt_when_content_changed() {
        val localSynced = sampleEntity(updatedAt = 500L, syncStatus = "SYNCED")
        val remoteChanged = sampleDomain(updatedAt = 0L).copy(lanGanNhat = "29/10/2025")

        assertTrue(shouldApplyRemoteHgt(localSynced, remoteChanged))
    }

    @Test
    fun shouldApplyRemoteHgt_applies_unknown_updatedAt_when_note_changed() {
        val localSynced = sampleEntity(updatedAt = 500L, syncStatus = "SYNCED")
        val remoteChanged = sampleDomain(updatedAt = 0L).copy(ghiChu = "Can kiem tra lai gioi han")

        assertTrue(shouldApplyRemoteHgt(localSynced, remoteChanged))
    }

    private fun sampleEntity(updatedAt: Long, syncStatus: String): HgtCheckEntity =
        HgtCheckEntity(
            id = "hgt-1",
            maThietBi = "523BC01-05",
            chuKyNgay = 120,
            lanGanNhat = "28/10/2025",
            lanTiepTheo = "25/02/2026",
            ghiChu = "",
            updatedAt = updatedAt,
            syncStatus = syncStatus
        )

    private fun sampleDomain(updatedAt: Long): HgtCheck =
        HgtCheck(
            id = "hgt-1",
            maThietBi = "523BC01-05",
            chuKyNgay = 120,
            lanGanNhat = "28/10/2025",
            lanTiepTheo = "25/02/2026",
            ghiChu = "",
            updatedAt = updatedAt
        )
}

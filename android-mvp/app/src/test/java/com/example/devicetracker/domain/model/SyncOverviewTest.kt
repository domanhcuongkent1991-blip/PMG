package com.example.devicetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOverviewTest {

    @Test
    fun total_fields_can_be_split_by_dmbt_and_hgt() {
        val overview = SyncOverview(
            totalLogs = 12,
            syncedLogs = 10,
            pendingLogs = 2,
            totalDmbtLogs = 8,
            syncedDmbtLogs = 7,
            pendingDmbtLogs = 1,
            totalHgtChecks = 4,
            syncedHgtChecks = 3,
            pendingHgtChecks = 1,
            queueSize = 0,
            queueErrorCount = 0,
            latestQueueError = null
        )

        assertEquals(8, overview.totalDmbtLogs)
        assertEquals(4, overview.totalHgtChecks)
        assertEquals(12, overview.totalLogs)
    }
}

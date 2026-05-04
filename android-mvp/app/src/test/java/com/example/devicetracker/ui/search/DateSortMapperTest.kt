package com.example.devicetracker.ui.search

import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertEquals
import org.junit.Test

class DateSortMapperTest {

    @Test
    fun sort_by_discovery_date_newest_first() {
        val sorted = sortDeviceLogsByDate(
            items = listOf(
                sampleLog(recordId = "r1", discoveryDate = "01/01/2026", repairDate = null, updatedAt = 1L),
                sampleLog(recordId = "r2", discoveryDate = "22/03/2026", repairDate = null, updatedAt = 2L),
                sampleLog(recordId = "r3", discoveryDate = "05/02/2026", repairDate = null, updatedAt = 3L)
            ),
            field = DateSortField.DISCOVERY_DATE,
            order = DateSortOrder.NEWEST_FIRST
        )

        assertEquals(listOf("r2", "r3", "r1"), sorted.map { it.recordId })
    }

    @Test
    fun sort_by_repair_date_oldest_first_keeps_missing_date_at_end() {
        val sorted = sortDeviceLogsByDate(
            items = listOf(
                sampleLog(recordId = "r1", discoveryDate = "01/01/2026", repairDate = "22/03/2026", updatedAt = 1L),
                sampleLog(recordId = "r2", discoveryDate = "02/01/2026", repairDate = "", updatedAt = 4L),
                sampleLog(recordId = "r3", discoveryDate = "03/01/2026", repairDate = "25/03/2026", updatedAt = 3L)
            ),
            field = DateSortField.REPAIR_DATE,
            order = DateSortOrder.OLDEST_FIRST
        )

        assertEquals(listOf("r1", "r3", "r2"), sorted.map { it.recordId })
    }

    private fun sampleLog(
        recordId: String,
        discoveryDate: String,
        repairDate: String?,
        updatedAt: Long
    ): DeviceLog = DeviceLog(
        recordId = recordId,
        maThietBi = "TB001",
        hangMuc = "DMBT 2026",
        nguoiBaoCao = "A",
        tinhTrangThietBi = "B",
        ktvPhuTrach = "C",
        ngayPhatHien = discoveryDate,
        ngaySuaChua = repairDate,
        ghiChu = "",
        updatedAt = updatedAt
    )
}


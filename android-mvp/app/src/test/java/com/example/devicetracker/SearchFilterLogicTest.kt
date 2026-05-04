package com.example.devicetracker

import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.RepairFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterLogicTest {

    @Test
    fun repair_status_is_pending_when_ngay_sua_chua_is_blank() {
        val log = sampleLog(ngaySuaChua = null)
        assertEquals(RepairFilter.PENDING, log.repairStatus)
    }

    @Test
    fun repair_status_is_repaired_when_ngay_sua_chua_has_value() {
        val log = sampleLog(ngaySuaChua = "2026-04-22")
        assertEquals(RepairFilter.REPAIRED, log.repairStatus)
    }

    private fun sampleLog(ngaySuaChua: String?) = DeviceLog(
        recordId = "1",
        maThietBi = "TB-001",
        hangMuc = "Lò 3",
        nguoiBaoCao = "Cường",
        tinhTrangThietBi = "Rò dầu",
        ktvPhuTrach = "Anh A",
        ngayPhatHien = "2026-04-22",
        ngaySuaChua = ngaySuaChua,
        ghiChu = "",
        updatedAt = 1L
    )
}

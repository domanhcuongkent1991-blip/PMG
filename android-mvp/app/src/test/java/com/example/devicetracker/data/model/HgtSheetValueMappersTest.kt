package com.example.devicetracker.data.model

import com.example.devicetracker.data.sheet.HgtCheckColumns
import com.example.devicetracker.domain.model.HgtCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HgtSheetValueMappersTest {

    @Test
    fun toHgtRow_includes_note_when_syncing_to_sheet() {
        val row = HgtCheck(
            id = "hgt-1",
            maThietBi = "523BC01-05",
            chuKyNgay = 120,
            lanGanNhat = "28/10/2025",
            lanTiepTheo = "25/02/2026",
            ghiChu = "Kiem tra them gioi han",
            updatedAt = 1000L
        ).toHgtRow()

        assertEquals("Kiem tra them gioi han", row[HgtCheckColumns.GHI_CHU])
    }

    @Test
    fun toHgtCheckFromRow_reads_optional_note_from_sheet() {
        val result = mapOf(
            HgtCheckColumns.RECORD_ID to "hgt-1",
            HgtCheckColumns.MA_THIET_BI to "523BC01-05",
            HgtCheckColumns.CHU_KY_NGAY to "120",
            HgtCheckColumns.LAN_GAN_NHAT to "28/10/2025",
            HgtCheckColumns.LAN_TIEP_THEO to "25/02/2026",
            HgtCheckColumns.GHI_CHU to "Da bo sung cot ghi chu",
            HgtCheckColumns.UPDATED_AT to "1000"
        ).toHgtCheckFromRow()

        assertTrue(result.isSuccess)
        assertEquals("Da bo sung cot ghi chu", result.getOrThrow().ghiChu)
    }
}

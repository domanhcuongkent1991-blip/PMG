package com.example.devicetracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetsRemoteDataSourceHgtTest {

    @Test
    fun parsePulledHgtRowsForTest_reads_vietnamese_sheet_shape_with_note() {
        val rows = listOf(
            listOf("STT", "Thiết bị", "Chu kì(ngày)", "Lần gần nhất", "Lần tiếp theo", "Ghi chú"),
            listOf("1", "523BC01-05", "120", "28/10/2025", "", "Can bo sung dau moi")
        )

        val parsed = SheetsRemoteDataSource.parsePulledHgtRowsForTest(rows)

        assertEquals(1, parsed.size)
        assertEquals("523BC01-05", parsed.single().maThietBi)
        assertEquals("Can bo sung dau moi", parsed.single().ghiChu)
        assertEquals("25/02/2026", parsed.single().lanTiepTheo)
    }
}

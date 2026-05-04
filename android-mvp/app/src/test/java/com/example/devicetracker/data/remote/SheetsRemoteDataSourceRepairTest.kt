package com.example.devicetracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetsRemoteDataSourceRepairTest {

    // ==================== parseRepairSchema tests ====================

    @Test
    fun parseRepairSchema_parsesValidHeaders() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong", "1713980000000"),
            listOf("repair-002", "TB-002", "", "Dang cho", "1713990000000")
        )

        val schema = SheetsRemoteDataSource.parseRepairSchema(gridRows)

        assertEquals(5, schema.rawHeaders.size)
        assertEquals(2, schema.rowByRecordId.size)
    }

    @Test
    fun parseRepairSchema_throwsOnMissingRequiredColumn_record_id() {
        val gridRows = listOf(
            listOf("ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("TB-001", "2026-04-25", "Da sua xong", "1713980000000")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG missing required columns"))
        assertTrue(exception.message!!.contains("record_id"))
    }

    @Test
    fun parseRepairSchema_throwsOnMissingRequiredColumn_ma_thiet_bi() {
        val gridRows = listOf(
            listOf("record_id", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "2026-04-25", "Da sua xong", "1713980000000")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG missing required columns"))
        assertTrue(exception.message!!.contains("ma_thiet_bi"))
    }

    @Test
    fun parseRepairSchema_throwsOnMissingRequiredColumn_ngay_sua_chua() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "Da sua xong", "1713980000000")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG missing required columns"))
        assertTrue(exception.message!!.contains("ngay_sua_chua"))
    }

    @Test
    fun parseRepairSchema_throwsOnMissingRequiredColumn_ghi_chu() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "updated_at"),
            listOf("repair-001", "TB-001", "2026-04-25", "1713980000000")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG missing required columns"))
        assertTrue(exception.message!!.contains("ghi_chu"))
    }

    @Test
    fun parseRepairSchema_throwsOnMissingRequiredColumn_updated_at() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu"),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG missing required columns"))
        assertTrue(exception.message!!.contains("updated_at"))
    }

    @Test
    fun parseRepairSchema_throwsOnEmptyHeaderRow() {
        val gridRows = listOf(
            emptyList<String>(),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong", "1713980000000")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG header row is empty"))
    }

    @Test
    fun parseRepairSchema_throwsOnEmptySheet() {
        val gridRows = emptyList<List<String>>()

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("DMBT_REPAIR_LOG sheet is empty"))
    }

    @Test
    fun parseRepairSchema_throwsOnDuplicateHeaders() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at", "record_id"),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong", "1713980000000", "repair-002")
        )

        val exception = catchNonRetryable { SheetsRemoteDataSource.parseRepairSchema(gridRows) }

        assertTrue(exception.message!!.contains("Duplicate headers found in DMBT_REPAIR_LOG"))
    }

    @Test
    fun parseRepairSchema_parsesHeadersWithIdVariant() {
        val gridRows = listOf(
            listOf("id", "ma_thiet_bi_", "ngay_sua_chua_", "ghi_chu_", "updated_at"),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong", "1713980000000")
        )

        val schema = SheetsRemoteDataSource.parseRepairSchema(gridRows)

        assertEquals(5, schema.rawHeaders.size)
        assertEquals(1, schema.rowByRecordId.size)
    }

    @Test
    fun parseRepairSchema_buildsRowByRecordIdCorrectly() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "", "Dang cho", "1713990000000"),
            listOf("repair-002", "TB-002", "   ", "Chua duoc sua", "1714000000000")
        )

        val schema = SheetsRemoteDataSource.parseRepairSchema(gridRows)

        assertEquals(2, schema.rowValuesByRowNumber.size)
        assertEquals(2, schema.rowByRecordId.size)
        assertTrue(schema.rowByRecordId.containsKey("repair-001"))
        assertTrue(schema.rowByRecordId.containsKey("repair-002"))
    }

    // ==================== parseRepairRows tests ====================

    @Test
    fun parseRepairRows_mapsRowToDmbtRepairUpdate() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "2026-04-25", "Da sua xong", "1713980000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(1, updates.size)
        assertEquals("repair-001", updates[0].recordId)
        assertEquals("TB-001", updates[0].maThietBi)
        assertEquals("2026-04-25", updates[0].ngaySuaChua)
        assertEquals("Da sua xong", updates[0].ghiChu)
        assertEquals(1713980000000L, updates[0].updatedAt)
    }

    @Test
    fun parseRepairRows_mapsRowWithEmptyNgaySuaChuaAsNull() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "", "Chua sua", "1713990000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(1, updates.size)
        assertEquals(null, updates[0].ngaySuaChua)
        assertEquals("Chua sua", updates[0].ghiChu)
    }

    @Test
    fun parseRepairRows_mapsRowWithWhitespaceNgaySuaChuaAsNull() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "   ", "Chua sua", "1713990000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(1, updates.size)
        assertEquals(null, updates[0].ngaySuaChua)
    }

    @Test
    fun parseRepairRows_skipsRowWithBlankRecordId() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("", "TB-001", "2026-04-25", "Da sua", "1713980000000"),
            listOf("repair-001", "TB-002", "2026-04-26", "Da sua 2", "1713990000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(1, updates.size)
        assertEquals("repair-001", updates[0].recordId)
    }

    @Test
    fun parseRepairRows_skipsRowWithBlankMaThietBi() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "", "2026-04-25", "Da sua", "1713980000000"),
            listOf("repair-002", "TB-002", "2026-04-26", "Da sua 2", "1713990000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(1, updates.size)
        assertEquals("repair-002", updates[0].recordId)
    }

    @Test
    fun parseRepairRows_parsesMultipleRows() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at"),
            listOf("repair-001", "TB-001", "2026-04-25", "Note 1", "1713980000000"),
            listOf("repair-002", "TB-002", "2026-04-26", "Note 2", "1713990000000"),
            listOf("repair-003", "TB-003", "", "Note 3", "1714000000000")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertEquals(3, updates.size)

        assertEquals("repair-001", updates[0].recordId)
        assertEquals("TB-001", updates[0].maThietBi)
        assertEquals("2026-04-25", updates[0].ngaySuaChua)
        assertEquals("Note 1", updates[0].ghiChu)

        assertEquals("repair-002", updates[1].recordId)
        assertEquals("TB-002", updates[1].maThietBi)
        assertEquals("2026-04-26", updates[1].ngaySuaChua)
        assertEquals("Note 2", updates[1].ghiChu)

        assertEquals("repair-003", updates[2].recordId)
        assertEquals("TB-003", updates[2].maThietBi)
        assertEquals(null, updates[2].ngaySuaChua)
        assertEquals("Note 3", updates[2].ghiChu)
    }

    @Test
    fun parseRepairRows_returnsEmptyForHeaderOnly() {
        val gridRows = listOf(
            listOf("record_id", "ma_thiet_bi", "ngay_sua_chua", "ghi_chu", "updated_at")
        )

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertTrue(updates.isEmpty())
    }

    @Test
    fun parseRepairRows_returnsEmptyForEmptySheet() {
        val gridRows = emptyList<List<String>>()

        val updates = SheetsRemoteDataSource.parseRepairRows(gridRows)

        assertTrue(updates.isEmpty())
    }

    // ==================== Helper functions ====================

    private fun catchNonRetryable(block: () -> Unit): NonRetryableSyncException {
        return try {
            block()
            throw AssertionError("Expected NonRetryableSyncException to be thrown")
        } catch (e: NonRetryableSyncException) {
            e
        } catch (e: Exception) {
            throw AssertionError("Expected NonRetryableSyncException but got ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

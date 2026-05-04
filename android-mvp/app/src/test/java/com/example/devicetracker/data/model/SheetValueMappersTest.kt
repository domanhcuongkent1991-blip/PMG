package com.example.devicetracker.data.model

import com.example.devicetracker.data.sheet.DmbtLogColumns
import com.example.devicetracker.data.sheet.DmbtRepairLogColumns
import com.example.devicetracker.domain.model.DeviceLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetValueMappersTest {

    @Test
    fun to_dmbt_log_row_maps_repair_date_null_to_blank_cell() {
        val row = sampleLog(ngaySuaChua = null).toDmbtLogRow()

        assertEquals("", row[DmbtLogColumns.NGAY_SUA_CHUA])
        assertEquals("r-1", row[DmbtLogColumns.RECORD_ID])
        assertEquals("TB-001", row[DmbtLogColumns.MA_THIET_BI])
    }

    @Test
    fun to_device_log_maps_blank_repair_date_back_to_null() {
        val result = sampleRow(
            DmbtLogColumns.NGAY_SUA_CHUA to ""
        ).toDeviceLogFromDmbtRow()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.ngaySuaChua)
    }

    @Test
    fun device_log_domain_entity_round_trip_keeps_source_sheet_id() {
        val entity = sampleLog(ngaySuaChua = "2026-04-23")
            .copy(sourceSheetId = 849979183)
            .toEntity(syncStatus = "SYNCED")

        val domain = entity.toDomain()

        assertEquals(849979183, domain.sourceSheetId)
        assertEquals("SYNCED", entity.syncStatus)
    }

    @Test
    fun to_device_log_fails_when_required_column_missing() {
        val result = sampleRow().toMutableMap().apply {
            remove(DmbtLogColumns.RECORD_ID)
        }.toDeviceLogFromDmbtRow()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required DMBT_LOG columns") == true)
    }

    @Test
    fun to_device_log_fails_when_updated_at_is_not_long() {
        val result = sampleRow(
            DmbtLogColumns.UPDATED_AT to "invalid"
        ).toDeviceLogFromDmbtRow()

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.isNullOrBlank())
    }

    @Test
    fun repair_update_maps_to_repair_sheet_row() {
        val row = DmbtRepairUpdate(
            recordId = "r-1",
            maThietBi = "TB-001",
            ngaySuaChua = "2026-04-23",
            ghiChu = "Changed bearing",
            updatedAt = 1713780000000
        ).toDmbtRepairLogRow()

        assertEquals("r-1", row[DmbtRepairLogColumns.RECORD_ID])
        assertEquals("TB-001", row[DmbtRepairLogColumns.MA_THIET_BI])
        assertEquals("2026-04-23", row[DmbtRepairLogColumns.NGAY_SUA_CHUA])
        assertEquals("Changed bearing", row[DmbtRepairLogColumns.GHI_CHU])
        assertEquals("1713780000000", row[DmbtRepairLogColumns.UPDATED_AT])
    }

    @Test
    fun repair_update_maps_blank_repair_date_to_null() {
        val result = sampleRepairRow(
            DmbtRepairLogColumns.NGAY_SUA_CHUA to ""
        ).toDmbtRepairUpdateFromRow()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.ngaySuaChua)
    }

    @Test
    fun repair_update_fails_when_note_column_missing() {
        val result = sampleRepairRow().toMutableMap().apply {
            remove(DmbtRepairLogColumns.GHI_CHU)
        }.toDmbtRepairUpdateFromRow()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required DMBT_REPAIR_LOG columns") == true)
    }

    @Test
    fun repair_update_fails_when_updated_at_is_not_long() {
        val result = sampleRepairRow(
            DmbtRepairLogColumns.UPDATED_AT to "invalid"
        ).toDmbtRepairUpdateFromRow()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("updated_at") == true)
    }

    private fun sampleLog(ngaySuaChua: String?): DeviceLog = DeviceLog(
        recordId = "r-1",
        maThietBi = "TB-001",
        hangMuc = "Khu A",
        nguoiBaoCao = "Tester",
        tinhTrangThietBi = "Can check",
        ktvPhuTrach = "KTV A",
        ngayPhatHien = "2026-04-22",
        ngaySuaChua = ngaySuaChua,
        ghiChu = "note",
        updatedAt = 1713780000000
    )

    private fun sampleRow(vararg overrides: Pair<String, String>): Map<String, String> {
        val base = mutableMapOf(
            DmbtLogColumns.RECORD_ID to "r-1",
            DmbtLogColumns.MA_THIET_BI to "TB-001",
            DmbtLogColumns.HANG_MUC to "Khu A",
            DmbtLogColumns.NGUOI_BAO_CAO to "Tester",
            DmbtLogColumns.TINH_TRANG_THIET_BI to "Can check",
            DmbtLogColumns.KTV_PHU_TRACH to "KTV A",
            DmbtLogColumns.NGAY_PHAT_HIEN to "2026-04-22",
            DmbtLogColumns.NGAY_SUA_CHUA to "2026-04-23",
            DmbtLogColumns.GHI_CHU to "note",
            DmbtLogColumns.UPDATED_AT to "1713780000000"
        )
        overrides.forEach { (key, value) -> base[key] = value }
        return base
    }

    private fun sampleRepairRow(vararg overrides: Pair<String, String>): Map<String, String> {
        val base = mutableMapOf(
            DmbtRepairLogColumns.RECORD_ID to "r-1",
            DmbtRepairLogColumns.MA_THIET_BI to "TB-001",
            DmbtRepairLogColumns.NGAY_SUA_CHUA to "2026-04-23",
            DmbtRepairLogColumns.GHI_CHU to "Changed bearing",
            DmbtRepairLogColumns.UPDATED_AT to "1713780000000"
        )
        overrides.forEach { (key, value) -> base[key] = value }
        return base
    }
}

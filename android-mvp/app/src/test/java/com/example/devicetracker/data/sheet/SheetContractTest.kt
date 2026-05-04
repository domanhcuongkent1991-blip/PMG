package com.example.devicetracker.data.sheet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetContractTest {

    @Test
    fun required_columns_for_dmbt_log_include_core_keys() {
        val required = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_LOG)

        assertTrue(required.contains(DmbtLogColumns.RECORD_ID))
        assertTrue(required.contains(DmbtLogColumns.MA_THIET_BI))
        assertTrue(required.contains(DmbtLogColumns.NGAY_PHAT_HIEN))
        assertTrue(required.contains(DmbtLogColumns.NGAY_SUA_CHUA))
        assertTrue(required.contains(DmbtLogColumns.UPDATED_AT))
        assertFalse(required.contains("repair_status"))
    }

    @Test
    fun required_columns_for_hgt_checks_match_current_sheet_shape() {
        val required = SheetContract.requiredColumnsByRole.getValue(SheetRole.HGT_CHECKS)

        assertTrue(required.contains(HgtCheckColumns.MA_THIET_BI))
        assertTrue(required.contains(HgtCheckColumns.CHU_KY_NGAY))
        assertTrue(required.contains(HgtCheckColumns.LAN_GAN_NHAT))
        assertTrue(required.contains(HgtCheckColumns.LAN_TIEP_THEO))
        assertFalse(required.contains(HgtCheckColumns.RECORD_ID))
        assertFalse(required.contains(HgtCheckColumns.UPDATED_AT))
    }

    @Test
    fun default_config_requires_dmbt_log_sheet_id_before_sync() {
        val missingRoles = SheetConfig.findMissingSheetIdRoles(
            requiredRoles = SheetContract.requiredRolesForSync,
            roleSheetIds = mapOf(
                SheetRole.DMBT_LOG to null
            )
        )

        assertTrue(missingRoles.contains(SheetRole.DMBT_LOG))
    }
}

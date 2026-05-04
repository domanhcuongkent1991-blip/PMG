package com.example.devicetracker.data.sheet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairSheetContractTest {

    @Test
    fun repairLogRole_hasRequiredColumnsForSafeTwoWaySync() {
        val requiredColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_REPAIR_LOG)

        assertTrue(requiredColumns.contains(DmbtRepairLogColumns.RECORD_ID))
        assertTrue(requiredColumns.contains(DmbtRepairLogColumns.MA_THIET_BI))
        assertTrue(requiredColumns.contains(DmbtRepairLogColumns.NGAY_SUA_CHUA))
        assertTrue(requiredColumns.contains(DmbtRepairLogColumns.GHI_CHU))
        assertTrue(requiredColumns.contains(DmbtRepairLogColumns.UPDATED_AT))
    }

    @Test
    fun repairLogRole_isOptInUntilProductionIntegrationIsVerified() {
        assertFalse(SheetContract.requiredRolesForSync.contains(SheetRole.DMBT_REPAIR_LOG))
    }
}

package com.example.devicetracker.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetSyncRegistryTest {

    @Test
    fun default_registry_keeps_new_sheet_roles_inventory_only() {
        val registry = SheetSyncRegistry.default()

        assertEquals(SheetSyncMode.TWO_WAY, registry.get(SheetRole.DMBT_LOG).mode)
        assertEquals(SheetSyncMode.TWO_WAY, registry.get(SheetRole.DMBT_REPAIR_LOG).mode)
        assertEquals(SheetSyncMode.TWO_WAY, registry.get(SheetRole.HGT_CHECKS).mode)
        assertEquals(SheetSyncMode.INVENTORY_ONLY, registry.get(SheetRole.DEVICE_MASTER).mode)
        assertEquals(SheetSyncMode.INVENTORY_ONLY, registry.get(SheetRole.LOOKUP_OPTIONS).mode)
        assertEquals(SheetSyncMode.INVENTORY_ONLY, registry.get(SheetRole.APP_CONFIG).mode)
    }

    @Test
    fun only_two_way_roles_are_writable_by_default() {
        val registry = SheetSyncRegistry.default()

        assertTrue(registry.canWrite(SheetRole.DMBT_LOG))
        assertTrue(registry.canWrite(SheetRole.DMBT_REPAIR_LOG))
        assertTrue(registry.canWrite(SheetRole.HGT_CHECKS))
        assertFalse(registry.canWrite(SheetRole.DEVICE_MASTER))
        assertFalse(registry.canWrite(SheetRole.LOOKUP_OPTIONS))
        assertFalse(registry.canWrite(SheetRole.APP_CONFIG))
    }

    @Test
    fun new_sheet_roles_have_required_contract_columns_before_enabling_pull() {
        val deviceMasterColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DEVICE_MASTER)
        val repairLogColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.DMBT_REPAIR_LOG)
        val lookupColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.LOOKUP_OPTIONS)
        val appConfigColumns = SheetContract.requiredColumnsByRole.getValue(SheetRole.APP_CONFIG)

        assertTrue(deviceMasterColumns.contains(DeviceMasterColumns.DEVICE_CODE))
        assertTrue(repairLogColumns.contains(DmbtRepairLogColumns.RECORD_ID))
        assertTrue(repairLogColumns.contains(DmbtRepairLogColumns.MA_THIET_BI))
        assertTrue(lookupColumns.contains(LookupOptionColumns.OPTION_GROUP))
        assertTrue(lookupColumns.contains(LookupOptionColumns.OPTION_KEY))
        assertTrue(appConfigColumns.contains(AppConfigColumns.CONFIG_KEY))
        assertTrue(appConfigColumns.contains(AppConfigColumns.CONFIG_VALUE))
    }
}

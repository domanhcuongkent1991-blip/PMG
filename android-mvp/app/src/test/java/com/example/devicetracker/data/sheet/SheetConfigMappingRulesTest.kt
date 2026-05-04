package com.example.devicetracker.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetConfigMappingRulesTest {

    @Test
    fun findMissingSheetIdRoles_returns_sorted_roles_with_null_sheet_id() {
        val missing = SheetConfig.findMissingSheetIdRoles(
            requiredRoles = setOf(SheetRole.DMBT_LOG, SheetRole.DEVICE_MASTER, SheetRole.APP_CONFIG),
            roleSheetIds = mapOf(
                SheetRole.DMBT_LOG to 157327514,
                SheetRole.DEVICE_MASTER to null,
                SheetRole.APP_CONFIG to null
            )
        )

        assertEquals(listOf(SheetRole.APP_CONFIG, SheetRole.DEVICE_MASTER), missing)
    }

    @Test
    fun findDuplicateSheetIdRoles_detects_conflicts_across_roles() {
        val duplicates = SheetConfig.findDuplicateSheetIdRoles(
            mapOf(
                SheetRole.DMBT_LOG to 157327514,
                SheetRole.DEVICE_MASTER to 157327514,
                SheetRole.LOOKUP_OPTIONS to 1224276666,
                SheetRole.APP_CONFIG to null
            )
        )

        assertTrue(duplicates.containsKey(157327514))
        assertEquals(
            listOf(SheetRole.DEVICE_MASTER, SheetRole.DMBT_LOG),
            duplicates.getValue(157327514)
        )
    }

    @Test
    fun findDuplicateSheetIdRoles_ignores_null_and_unique_ids() {
        val duplicates = SheetConfig.findDuplicateSheetIdRoles(
            mapOf(
                SheetRole.DMBT_LOG to 157327514,
                SheetRole.DEVICE_MASTER to null,
                SheetRole.LOOKUP_OPTIONS to 1224276666,
                SheetRole.APP_CONFIG to 849979183
            )
        )

        assertTrue(duplicates.isEmpty())
    }

    @Test
    fun parseReadOnlySheetIds_keeps_valid_unique_ids_and_excludes_primary_sheet() {
        val sheetIds = SheetConfig.parseReadOnlySheetIds(
            rawValue = "849979183, 1607125070, abc, -1, 1783863163, 849979183",
            primarySheetId = 1607125070
        )

        assertEquals(listOf(849979183, 1783863163), sheetIds)
    }

    @Test
    fun parseReadOnlySheetIds_accepts_multiple_separators_without_auto_scanning_unknown_tabs() {
        val sheetIds = SheetConfig.parseReadOnlySheetIds(
            rawValue = "849979183;1783863163\n1224276666  989601207\tbad-id",
            primarySheetId = null
        )

        assertEquals(listOf(849979183, 1783863163, 1224276666, 989601207), sheetIds)
    }

    @Test
    fun parseDmbtSheetBindings_keeps_unique_valid_sheet_ids_and_marks_default_target() {
        val bindings = SheetConfig.parseDmbtSheetBindings(
            rawSheetIds = "849979183,1783863163,849979183,bad,-1,1383308512",
            defaultCreateSheetId = 1383308512,
            legacyPrimarySheetId = null,
            legacyReadOnlySheetIds = ""
        )

        assertEquals(
            listOf(849979183, 1783863163, 1383308512),
            bindings.map { it.sheetId }
        )
        assertTrue(bindings.all { it.mode == SheetSyncMode.TWO_WAY })
        assertEquals(1, bindings.count { it.isDefaultCreateTarget })
        assertEquals(1383308512, bindings.single { it.isDefaultCreateTarget }.sheetId)
    }

    @Test
    fun parseDmbtSheetBindings_fallsBackToLegacyPrimaryAndReadOnlyIdsAsTwoWayBindings() {
        val bindings = SheetConfig.parseDmbtSheetBindings(
            rawSheetIds = "",
            defaultCreateSheetId = 1383308512,
            legacyPrimarySheetId = 1383308512,
            legacyReadOnlySheetIds = "849979183,1783863163"
        )

        assertEquals(
            listOf(1383308512, 849979183, 1783863163),
            bindings.map { it.sheetId }
        )
        assertTrue(bindings.all { it.mode == SheetSyncMode.TWO_WAY })
        assertTrue(bindings.first().isDefaultCreateTarget)
        assertFalse(bindings.drop(1).any { it.isDefaultCreateTarget })
    }

    @Test
    fun findDuplicateSheetIdRoles_detects_repair_sheet_conflict_with_dmbt_write_target() {
        val duplicates = SheetConfig.findDuplicateSheetIdRoles(
            mapOf(
                SheetRole.DMBT_LOG to 1383308512,
                SheetRole.DMBT_REPAIR_LOG to 1383308512,
                SheetRole.HGT_CHECKS to 57428884
            )
        )

        assertEquals(
            listOf(SheetRole.DMBT_LOG, SheetRole.DMBT_REPAIR_LOG),
            duplicates.getValue(1383308512)
        )
    }
}

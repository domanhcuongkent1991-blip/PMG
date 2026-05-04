package com.example.devicetracker.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetDryRunValidatorTest {

    @Test
    fun validateHeader_allows_extra_columns_when_required_columns_exist() {
        val result = SheetDryRunValidator.validateHeader(
            role = SheetRole.DEVICE_MASTER,
            headers = listOf(
                DeviceMasterColumns.DEVICE_CODE,
                DeviceMasterColumns.DEVICE_NAME,
                "extra_operator_note"
            )
        )

        assertTrue(result.canEnablePullOnly)
        assertEquals(SheetDryRunStatus.OK, result.status)
        assertTrue(result.missingColumns.isEmpty())
    }

    @Test
    fun validateHeader_blocks_pull_when_required_column_is_missing() {
        val result = SheetDryRunValidator.validateHeader(
            role = SheetRole.LOOKUP_OPTIONS,
            headers = listOf(
                LookupOptionColumns.OPTION_GROUP,
                LookupOptionColumns.OPTION_LABEL
            )
        )

        assertFalse(result.canEnablePullOnly)
        assertEquals(SheetDryRunStatus.SCHEMA_ERROR, result.status)
        assertEquals(listOf(LookupOptionColumns.OPTION_KEY), result.missingColumns)
    }

    @Test
    fun validateHeader_reports_empty_header_as_schema_error() {
        val result = SheetDryRunValidator.validateHeader(
            role = SheetRole.APP_CONFIG,
            headers = emptyList()
        )

        assertFalse(result.canEnablePullOnly)
        assertEquals(SheetDryRunStatus.SCHEMA_ERROR, result.status)
        assertTrue(result.missingColumns.contains(AppConfigColumns.CONFIG_KEY))
    }
}

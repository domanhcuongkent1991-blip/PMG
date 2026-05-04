package com.example.devicetracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DateTextFormatterTest {

    @Test
    fun normalize_input_accepts_dd_mm_yyyy() {
        assertEquals("22/03/2026", DateTextFormatter.normalizeInputOrNull("22/03/2026"))
    }

    @Test
    fun normalize_input_rejects_iso_format() {
        assertNull(DateTextFormatter.normalizeInputOrNull("2026-03-22"))
    }

    @Test
    fun display_supports_legacy_and_new_formats() {
        assertEquals("22/03/2026", DateTextFormatter.formatForDisplay("2026-03-22"))
        assertEquals("22/03/2026", DateTextFormatter.formatForDisplay("22/03/2026"))
    }

    @Test
    fun parse_to_epoch_supports_display_and_legacy_formats() {
        assertNotNull(DateTextFormatter.parseToEpochMillisOrNull("22/03/2026"))
        assertNotNull(DateTextFormatter.parseToEpochMillisOrNull("2026-03-22"))
    }

    @Test
    fun invalid_dates_are_rejected_for_epoch_parsing() {
        assertNull(DateTextFormatter.parseToEpochMillisOrNull("31/02/2026"))
        assertNull(DateTextFormatter.parseToEpochMillisOrNull("2026-13-01"))
        assertNull(DateTextFormatter.parseToEpochMillisOrNull("not-a-date"))
    }
}

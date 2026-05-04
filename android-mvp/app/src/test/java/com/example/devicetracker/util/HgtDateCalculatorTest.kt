package com.example.devicetracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HgtDateCalculatorTest {

    @Test
    fun calculateNextDate_adds_cycle_days_across_month_boundary() {
        assertEquals("31/01/2026", HgtDateCalculator.calculateNextDate("01/01/2026", 30))
    }

    @Test
    fun calculateNextDate_rejects_invalid_input_safely() {
        assertEquals("", HgtDateCalculator.calculateNextDate("31/02/2026", 30))
        assertEquals("", HgtDateCalculator.calculateNextDate("01/01/2026", 0))
        assertEquals("", HgtDateCalculator.calculateNextDate("01/01/2026", -1))
    }
}

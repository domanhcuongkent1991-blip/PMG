package com.example.devicetracker.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidebarMonthlyLabelStoreTest {

    @Test
    fun normalizeOverride_returnsNull_forBlankInput() {
        assertNull(SidebarMonthlyLabelStore.normalizeOverride(null))
        assertNull(SidebarMonthlyLabelStore.normalizeOverride(""))
        assertNull(SidebarMonthlyLabelStore.normalizeOverride("   "))
    }

    @Test
    fun normalizeOverride_collapsesWhitespaceAndTrims() {
        val result = SidebarMonthlyLabelStore.normalizeOverride("  DMBT   tháng   tùy  chỉnh  ")
        assertEquals("DMBT tháng tùy chỉnh", result)
    }

    @Test
    fun normalizeOverride_limitsLengthTo64() {
        val longText = "1234567890".repeat(8) // 80 chars
        val result = SidebarMonthlyLabelStore.normalizeOverride(longText)
        assertEquals(64, result?.length)
    }
}

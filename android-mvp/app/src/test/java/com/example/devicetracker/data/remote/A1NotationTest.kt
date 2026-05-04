package com.example.devicetracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class A1NotationTest {

    @Test
    fun converts_column_index_to_a1_label() {
        assertEquals("A", toA1Column(1))
        assertEquals("Z", toA1Column(26))
        assertEquals("AA", toA1Column(27))
        assertEquals("AZ", toA1Column(52))
        assertEquals("BA", toA1Column(53))
        assertEquals("ZZ", toA1Column(702))
    }
}

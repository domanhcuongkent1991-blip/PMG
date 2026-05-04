package com.example.devicetracker.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HgtDateCalculator {
    private const val DATE_PATTERN = "dd/MM/yyyy"

    fun calculateNextDate(latestDate: String, cycleDays: Int): String {
        if (cycleDays <= 0) return ""
        val parsed = parseDate(latestDate.trim()) ?: return ""
        val calendar = Calendar.getInstance(Locale.US).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, cycleDays)
        }
        return formatter().format(calendar.time)
    }

    private fun parseDate(value: String): java.util.Date? {
        val formatter = formatter().apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        return if (position.index == value.length) parsed else null
    }

    private fun formatter(): SimpleDateFormat = SimpleDateFormat(DATE_PATTERN, Locale.US)
}

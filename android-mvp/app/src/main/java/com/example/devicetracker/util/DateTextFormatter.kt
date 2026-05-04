package com.example.devicetracker.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object DateTextFormatter {
    private const val DISPLAY_PATTERN = "dd/MM/yyyy"
    private const val LEGACY_PATTERN = "yyyy-MM-dd"

    fun normalizeInputOrNull(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        val parsed = parseStrict(trimmed, DISPLAY_PATTERN) ?: return null
        return format(parsed, DISPLAY_PATTERN)
    }

    fun formatForDisplay(rawValue: String?): String {
        val trimmed = rawValue?.trim().orEmpty()
        if (trimmed.isEmpty()) return "--"

        val parsed = parseStrict(trimmed, DISPLAY_PATTERN)
            ?: parseStrict(trimmed, LEGACY_PATTERN)
            ?: return trimmed

        return format(parsed, DISPLAY_PATTERN)
    }

    fun parseToEpochMillisOrNull(rawValue: String?): Long? {
        val trimmed = rawValue?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val parsed = parseStrict(trimmed, DISPLAY_PATTERN)
            ?: parseStrict(trimmed, LEGACY_PATTERN)
            ?: return null
        return parsed.time
    }

    private fun parseStrict(value: String, pattern: String): java.util.Date? {
        val formatter = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        return if (position.index == value.length) parsed else null
    }

    private fun format(date: java.util.Date, pattern: String): String {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        return formatter.format(date)
    }
}

package com.example.devicetracker.ui.search

import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.util.DateTextFormatter

fun sortDeviceLogsByDate(
    items: List<DeviceLog>,
    field: DateSortField,
    order: DateSortOrder
): List<DeviceLog> {
    return items.sortedWith { left, right ->
        val leftMillis = extractSortMillis(left, field)
        val rightMillis = extractSortMillis(right, field)

        // Keep missing date values at the end to avoid misleading sort order.
        if (leftMillis == null && rightMillis == null) {
            return@sortedWith right.updatedAt.compareTo(left.updatedAt)
        }
        if (leftMillis == null) return@sortedWith 1
        if (rightMillis == null) return@sortedWith -1

        val dateCompare = when (order) {
            DateSortOrder.NEWEST_FIRST -> rightMillis.compareTo(leftMillis)
            DateSortOrder.OLDEST_FIRST -> leftMillis.compareTo(rightMillis)
        }
        if (dateCompare != 0) {
            return@sortedWith dateCompare
        }
        right.updatedAt.compareTo(left.updatedAt)
    }
}

private fun extractSortMillis(log: DeviceLog, field: DateSortField): Long? {
    val rawDate = when (field) {
        DateSortField.DISCOVERY_DATE -> log.ngayPhatHien
        DateSortField.REPAIR_DATE -> log.ngaySuaChua
    }
    return DateTextFormatter.parseToEpochMillisOrNull(rawDate)
}


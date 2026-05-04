package com.example.devicetracker.ui.search

import com.example.devicetracker.domain.model.DeviceLog
import java.util.Calendar

data class TimelineFilterPresentation(
    val years: List<Int>,
    val selectedYear: Int?,
    val visibleItems: List<DeviceLog>
)

data class MaintenanceCategoryOption(
    val id: String,
    val type: MaintenanceCategoryType,
    val year: Int? = null
)

enum class MaintenanceCategoryType {
    YEARLY_ALL,
    YEARLY,
    MONTHLY_DMBT,
    MONTHLY_REPAIR
}

data class MaintenanceCategoryPresentation(
    val categoryOptions: List<MaintenanceCategoryOption>,
    val selectedCategoryId: String,
    val visibleItems: List<DeviceLog>
)

const val CATEGORY_YEARLY_ALL = "yearly-all"
const val CATEGORY_MONTHLY_DMBT_T4_2026 = "monthly-dmbt-t4-2026"
const val CATEGORY_MONTHLY_REPAIR_T4_2026 = "monthly-repair-t4-2026"

fun buildTimelineFilterPresentation(
    items: List<DeviceLog>,
    selectedYear: Int?,
    additionalYears: List<Int> = emptyList()
): TimelineFilterPresentation {
    val yearByRecordId = items.associate { log ->
        log.recordId to extractTimelineYear(log)
    }
    val timelineYears = yearByRecordId.values.filterNotNull().distinct()
    val years = (defaultTimelineYears() + timelineYears)
        .plus(additionalYears)
        .distinct()
        .sorted()

    val effectiveSelectedYear = selectedYear?.takeIf { years.contains(it) }

    val visibleItems = if (effectiveSelectedYear == null) {
        items
    } else {
        items.filter { log -> yearByRecordId[log.recordId] == effectiveSelectedYear }
    }

    return TimelineFilterPresentation(
        years = years,
        selectedYear = effectiveSelectedYear,
        visibleItems = visibleItems
    )
}

fun extractTimelineYears(items: List<DeviceLog>): List<Int> =
    items.mapNotNull { extractTimelineYear(it) }.distinct().sorted()

fun buildMaintenanceCategoryPresentation(
    items: List<DeviceLog>,
    selectedCategoryId: String,
    additionalYears: List<Int> = emptyList()
): MaintenanceCategoryPresentation {
    val years = (defaultTimelineYears() + extractTimelineYears(items) + additionalYears)
        .distinct()
        .sorted()

    val options = buildList {
        add(
            MaintenanceCategoryOption(
                id = CATEGORY_YEARLY_ALL,
                type = MaintenanceCategoryType.YEARLY_ALL
            )
        )
        years.forEach { year ->
            add(
                MaintenanceCategoryOption(
                    id = yearlyCategoryId(year),
                    type = MaintenanceCategoryType.YEARLY,
                    year = year
                )
            )
        }
        add(
            MaintenanceCategoryOption(
                id = CATEGORY_MONTHLY_DMBT_T4_2026,
                type = MaintenanceCategoryType.MONTHLY_DMBT
            )
        )
        add(
            MaintenanceCategoryOption(
                id = CATEGORY_MONTHLY_REPAIR_T4_2026,
                type = MaintenanceCategoryType.MONTHLY_REPAIR
            )
        )
    }

    val effectiveSelectedId = selectedCategoryId
        .takeIf { requestedId -> options.any { it.id == requestedId } }
        ?: CATEGORY_YEARLY_ALL

    return MaintenanceCategoryPresentation(
        categoryOptions = options,
        selectedCategoryId = effectiveSelectedId,
        visibleItems = items.filter { log -> matchesCategory(log, effectiveSelectedId) }
    )
}

fun yearlyCategoryId(year: Int): String = "yearly-dmbt-$year"

private fun extractTimelineYear(dateText: String?): Int? {
    val trimmed = dateText?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val slashMatch = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(trimmed)
    if (slashMatch != null) {
        return slashMatch.groupValues[3].toIntOrNull()
    }

    val isoMatch = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").matchEntire(trimmed)
    if (isoMatch != null) {
        return isoMatch.groupValues[1].toIntOrNull()
    }

    val dateLiteralMatch = Regex("""^Date\((\d{4}),\d{1,2},\d{1,2}\)$""").matchEntire(trimmed)
    if (dateLiteralMatch != null) {
        return dateLiteralMatch.groupValues[1].toIntOrNull()
    }

    val looseYear = Regex("""(20\d{2})""").find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (looseYear != null) {
        return looseYear
    }

    return null
}

private fun extractTimelineYear(log: DeviceLog): Int? {
    val fromDiscovery = extractTimelineYear(log.ngayPhatHien)
    if (fromDiscovery != null) return fromDiscovery

    val fromSeedRecordId = extractTimelineYearFromSeedRecordId(log.recordId)
    if (fromSeedRecordId != null) return fromSeedRecordId

    val fromRepair = extractTimelineYear(log.ngaySuaChua)
    if (fromRepair != null) return fromRepair

    return null
}

private enum class SourceBucket {
    YEARLY_DMBT,
    MONTHLY_DMBT,
    MONTHLY_REPAIR
}

private data class SourceCategory(
    val bucket: SourceBucket,
    val year: Int? = null
)

private fun matchesCategory(log: DeviceLog, categoryId: String): Boolean {
    val sourceCategory = classifySource(log)

    if (categoryId == CATEGORY_YEARLY_ALL) {
        return sourceCategory.bucket == SourceBucket.YEARLY_DMBT
    }
    if (categoryId == CATEGORY_MONTHLY_DMBT_T4_2026) {
        return sourceCategory.bucket == SourceBucket.MONTHLY_DMBT
    }
    if (categoryId == CATEGORY_MONTHLY_REPAIR_T4_2026) {
        return sourceCategory.bucket == SourceBucket.MONTHLY_REPAIR
    }

    val selectedYear = categoryId.removePrefix("yearly-dmbt-").toIntOrNull()
    return selectedYear != null &&
        sourceCategory.bucket == SourceBucket.YEARLY_DMBT &&
        sourceCategory.year == selectedYear
}

private fun classifySource(log: DeviceLog): SourceCategory {
    val recordId = log.recordId.trim().lowercase()
    val categoryText = log.hangMuc.trim().lowercase()

    if (recordId.startsWith("seed-beta-sua-chua-t") || categoryText.contains("sửa chữa t")) {
        return SourceCategory(SourceBucket.MONTHLY_REPAIR)
    }

    if (recordId.startsWith("seed-beta-dmbt-t") || categoryText.contains("dmbt t")) {
        return SourceCategory(SourceBucket.MONTHLY_DMBT)
    }

    val yearlySeedYear = Regex("""^seed-beta-dmbt-(20\d{2})-r\d+""")
        .find(recordId)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (yearlySeedYear != null) {
        return SourceCategory(SourceBucket.YEARLY_DMBT, yearlySeedYear)
    }

    val categoryYear = Regex("""dmbt\s*(20\d{2})""")
        .find(categoryText)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (categoryYear != null) {
        return SourceCategory(SourceBucket.YEARLY_DMBT, categoryYear)
    }

    val fallbackYear = extractTimelineYear(log)
    if (fallbackYear != null) {
        return SourceCategory(SourceBucket.YEARLY_DMBT, fallbackYear)
    }

    // App-created abnormal records default to the current monthly DMBT bucket.
    return SourceCategory(SourceBucket.MONTHLY_DMBT)
}

private fun extractTimelineYearFromSeedRecordId(recordId: String): Int? {
    val normalized = recordId.trim().lowercase()
    if (!normalized.startsWith("seed-")) return null

    return Regex("""(?<!\d)(20\d{2})(?!\d)""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

private fun defaultTimelineYears(): List<Int> {
    val baseline = 2022
    val requiredUpperBound = 2026
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val upperBound = maxOf(currentYear, requiredUpperBound)
    if (upperBound < baseline) return listOf(baseline)
    return (baseline..upperBound).toList()
}

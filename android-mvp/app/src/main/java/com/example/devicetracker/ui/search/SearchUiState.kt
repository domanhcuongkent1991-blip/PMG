package com.example.devicetracker.ui.search

import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.RepairFilter

enum class DateSortField {
    DISCOVERY_DATE,
    REPAIR_DATE
}

enum class DateSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

data class SearchUiState(
    val query: String = "",
    val selectedFilter: RepairFilter = RepairFilter.ALL,
    val selectedSortField: DateSortField = DateSortField.DISCOVERY_DATE,
    val selectedSortOrder: DateSortOrder = DateSortOrder.NEWEST_FIRST,
    val selectedCategoryId: String = CATEGORY_YEARLY_ALL,
    val categoryOptions: List<MaintenanceCategoryOption> = emptyList(),
    val timelineYearOptions: List<Int> = emptyList(),
    val items: List<DeviceLog> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

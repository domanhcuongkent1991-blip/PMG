package com.example.devicetracker.ui.hgt

import com.example.devicetracker.domain.model.HgtCheck

data class HgtCheckUiState(
    val query: String = "",
    val items: List<HgtCheck> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isEditorVisible: Boolean = false,
    val isCreateMode: Boolean = false,
    val editingItemId: String? = null,
    val editingDeviceCode: String = "",
    val editingCycleDays: String = "",
    val editingLatestDate: String = "",
    val editingError: String? = null,
    val isReminderDialogVisible: Boolean = false,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: String = "3",
    val reminderTime: String = "07:30",
    val reminderError: String? = null
)

package com.example.devicetracker.domain.model

data class HgtReminderSettings(
    val enabled: Boolean = true,
    val daysBefore: Int = 3,
    val hourOfDay: Int = 7,
    val minute: Int = 30
)

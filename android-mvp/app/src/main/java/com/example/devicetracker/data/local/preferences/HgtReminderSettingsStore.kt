package com.example.devicetracker.data.local.preferences

import android.content.Context
import com.example.devicetracker.domain.model.HgtReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HgtReminderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun load(): HgtReminderSettings {
        return HgtReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            daysBefore = prefs.getInt(KEY_DAYS_BEFORE, 3).coerceIn(0, 3650),
            hourOfDay = prefs.getInt(KEY_HOUR, 7).coerceIn(0, 23),
            minute = prefs.getInt(KEY_MINUTE, 30).coerceIn(0, 59)
        )
    }

    fun save(settings: HgtReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_DAYS_BEFORE, settings.daysBefore.coerceIn(0, 3650))
            .putInt(KEY_HOUR, settings.hourOfDay.coerceIn(0, 23))
            .putInt(KEY_MINUTE, settings.minute.coerceIn(0, 59))
            .apply()
    }

    companion object {
        private const val PREF_NAME = "hgt_reminder_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DAYS_BEFORE = "days_before"
        private const val KEY_HOUR = "hour_of_day"
        private const val KEY_MINUTE = "minute"
    }
}

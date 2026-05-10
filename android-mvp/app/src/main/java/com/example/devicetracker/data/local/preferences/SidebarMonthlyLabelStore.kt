package com.example.devicetracker.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SidebarMonthlyLabelOverride(
    val monthlyDmbtLabel: String?,
    val monthlyRepairLabel: String?
)

@Singleton
class SidebarMonthlyLabelStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun load(): SidebarMonthlyLabelOverride {
        return SidebarMonthlyLabelOverride(
            monthlyDmbtLabel = normalizeOverride(
                prefs.getString(KEY_MONTHLY_DMBT_LABEL, null)
            ),
            monthlyRepairLabel = normalizeOverride(
                prefs.getString(KEY_MONTHLY_REPAIR_LABEL, null)
            )
        )
    }

    fun save(monthlyDmbtLabelInput: String?, monthlyRepairLabelInput: String?) {
        val monthlyDmbtLabel = normalizeOverride(monthlyDmbtLabelInput)
        val monthlyRepairLabel = normalizeOverride(monthlyRepairLabelInput)
        prefs.edit().apply {
            if (monthlyDmbtLabel == null) remove(KEY_MONTHLY_DMBT_LABEL) else putString(KEY_MONTHLY_DMBT_LABEL, monthlyDmbtLabel)
            if (monthlyRepairLabel == null) remove(KEY_MONTHLY_REPAIR_LABEL) else putString(KEY_MONTHLY_REPAIR_LABEL, monthlyRepairLabel)
        }.apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .remove(KEY_MONTHLY_DMBT_LABEL)
            .remove(KEY_MONTHLY_REPAIR_LABEL)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "sidebar_monthly_label_overrides"
        private const val KEY_MONTHLY_DMBT_LABEL = "monthly_dmbt_label"
        private const val KEY_MONTHLY_REPAIR_LABEL = "monthly_repair_label"
        private const val MAX_LABEL_LENGTH = 64

        internal fun normalizeOverride(raw: String?): String? {
            val normalized = raw
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(MAX_LABEL_LENGTH)
                .orEmpty()
            return normalized.ifBlank { null }
        }
    }
}

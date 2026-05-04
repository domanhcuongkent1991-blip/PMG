package com.example.devicetracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.preferences.HgtReminderSettingsStore
import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.HgtDateCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class HgtReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hgtCheckDao: HgtCheckDao,
    private val settingsStore: HgtReminderSettingsStore
) {
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun rescheduleAllAsync() {
        schedulerScope.launch {
            rescheduleAll()
        }
    }

    suspend fun rescheduleAll() {
        val settings = settingsStore.load()
        val checks = hgtCheckDao.getAll().map {
            HgtCheck(
                id = it.id,
                maThietBi = it.maThietBi,
                chuKyNgay = it.chuKyNgay,
                lanGanNhat = it.lanGanNhat,
                lanTiepTheo = it.lanTiepTheo,
                updatedAt = it.updatedAt
            )
        }
        checks.forEach { check ->
            scheduleForCheck(check, settings)
        }
    }

    suspend fun scheduleForCheck(check: HgtCheck) {
        val settings = settingsStore.load()
        scheduleForCheck(check, settings)
    }

    fun cancelReminder(checkId: String) {
        alarmManager.cancel(buildPendingIntent(checkId))
    }

    private fun scheduleForCheck(check: HgtCheck, settings: com.example.devicetracker.domain.model.HgtReminderSettings) {
        val pendingIntent = buildPendingIntent(
            checkId = check.id,
            maThietBi = check.maThietBi,
            nextDate = resolvedNextDate(check)
        )
        alarmManager.cancel(pendingIntent)
        if (!settings.enabled) return

        val triggerAtMillis = computeTriggerMillis(
            nextDate = resolvedNextDate(check),
            daysBefore = settings.daysBefore,
            hourOfDay = settings.hourOfDay,
            minute = settings.minute
        ) ?: return

        scheduleAlarmSafely(triggerAtMillis = triggerAtMillis, pendingIntent = pendingIntent)
    }

    private fun computeTriggerMillis(
        nextDate: String,
        daysBefore: Int,
        hourOfDay: Int,
        minute: Int
    ): Long? {
        val nextDateMillis = DateTextFormatter.parseToEpochMillisOrNull(nextDate) ?: return null
        val now = System.currentTimeMillis()
        if (nextDateMillis + ONE_DAY_MILLIS < now) {
            return null
        }

        val triggerCalendar = Calendar.getInstance(Locale.getDefault()).apply {
            timeInMillis = nextDateMillis
            add(Calendar.DAY_OF_YEAR, -daysBefore)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return if (triggerCalendar.timeInMillis <= now) {
            now + ONE_MINUTE_MILLIS
        } else {
            triggerCalendar.timeInMillis
        }
    }

    private fun resolvedNextDate(check: HgtCheck): String {
        val normalizedNext = DateTextFormatter.normalizeInputOrNull(check.lanTiepTheo).orEmpty()
        if (normalizedNext.isNotBlank()) return normalizedNext
        return HgtDateCalculator.calculateNextDate(check.lanGanNhat, check.chuKyNgay)
    }

    private fun buildPendingIntent(
        checkId: String,
        maThietBi: String? = null,
        nextDate: String? = null
    ): PendingIntent {
        val intent = Intent(context, HgtReminderReceiver::class.java).apply {
            action = HgtReminderReceiver.ACTION_HGT_REMINDER
            putExtra(HgtReminderReceiver.EXTRA_CHECK_ID, checkId)
            putExtra(HgtReminderReceiver.EXTRA_DEVICE_CODE, maThietBi.orEmpty())
            putExtra(HgtReminderReceiver.EXTRA_NEXT_DATE, nextDate.orEmpty())
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, checkId.hashCode(), intent, flags)
    }

    private fun scheduleAlarmSafely(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback on Android 12+ when exact alarm permission is not granted.
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }.onFailure { throwable ->
            // Last-resort fallback: keep reminder flow alive even if exact APIs fail.
            Log.w(TAG, "Exact alarm failed, fallback to inexact alarm: ${throwable.message}")
            runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }.onFailure { fallbackError ->
                Log.e(TAG, "Cannot schedule HGT reminder alarm: ${fallbackError.message}")
            }
        }
    }

    companion object {
        private const val TAG = "HgtReminderScheduler"
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val ONE_MINUTE_MILLIS = 60 * 1000L
    }
}

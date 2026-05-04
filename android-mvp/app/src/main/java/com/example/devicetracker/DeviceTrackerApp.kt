package com.example.devicetracker

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.devicetracker.data.bootstrap.SeedLocalDataLoader
import com.example.devicetracker.reminder.HgtReminderScheduler
import com.example.devicetracker.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class DeviceTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var seedLocalDataLoader: SeedLocalDataLoader

    @Inject
    lateinit var hgtReminderScheduler: HgtReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { seedLocalDataLoader.seedIfDatabaseEmpty() }
                .onSuccess { seededCount ->
                    if (seededCount > 0) {
                        Log.i(TAG, "Seeded $seededCount local records from bundled sheet snapshot.")
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "Local seed skipped: ${throwable.message}")
                }
        }
        syncScheduler.schedulePeriodicSync()
        hgtReminderScheduler.rescheduleAllAsync()
    }

    companion object {
        private const val TAG = "DeviceTrackerApp"
    }
}

package com.example.devicetracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HgtReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var hgtReminderScheduler: HgtReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        hgtReminderScheduler.rescheduleAllAsync()
    }
}

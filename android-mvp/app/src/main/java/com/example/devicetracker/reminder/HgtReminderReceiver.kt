package com.example.devicetracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.devicetracker.MainActivity
import com.example.devicetracker.R
import com.example.devicetracker.util.DateTextFormatter

class HgtReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HGT_REMINDER) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel(context)
        val checkId = intent.getStringExtra(EXTRA_CHECK_ID).orEmpty()
        if (checkId.isBlank()) return

        val maThietBi = intent.getStringExtra(EXTRA_DEVICE_CODE).orEmpty()
        val nextDate = DateTextFormatter.formatForDisplay(intent.getStringExtra(EXTRA_NEXT_DATE))
        val title = context.getString(R.string.hgt_reminder_notification_title)
        val body = context.getString(R.string.hgt_reminder_notification_body, maThietBi, nextDate)

        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            checkId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(checkId.hashCode(), notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.hgt_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.hgt_reminder_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_HGT_REMINDER = "com.example.devicetracker.ACTION_HGT_REMINDER"
        const val EXTRA_CHECK_ID = "extra_check_id"
        const val EXTRA_DEVICE_CODE = "extra_device_code"
        const val EXTRA_NEXT_DATE = "extra_next_date"
        const val CHANNEL_ID = "hgt_reminder_channel"
    }
}

package com.irtek.live.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.irtek.live.MainActivity
import com.irtek.live.R

object AlarmNotifier {
    private const val CHANNEL_ID = "alarm_messages"
    private const val CHANNEL_NAME = "Alarm notifications"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyAlarm(
        context: Context,
        alarmId: Long,
        title: String,
        content: String,
        deviceName: String
    ) {
        if (!AppPreferences.isNotificationsEnabled(context)) return
        ensureChannel(context)

        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_messages", true)
        }
        val pending = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { context.getString(R.string.notification_default_title) })
            .setContentText(
                buildString {
                    if (deviceName.isNotBlank()) append(deviceName)
                    if (content.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(content)
                    }
                }.ifBlank { context.getString(R.string.notification_default_body) }
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOf(deviceName, content).filter { it.isNotBlank() }.joinToString("\n")
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            nm.notify((alarmId % Int.MAX_VALUE).toInt().coerceAtLeast(1), notification)
        }
    }
}

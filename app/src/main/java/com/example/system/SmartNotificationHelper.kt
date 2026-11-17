package com.example.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class SmartNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "smart_dnd_status_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TURN_OFF = "com.example.ACTION_TURN_OFF_DND"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart DND Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status when Smart DND is running"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showActiveNotification(freedMemoryMb: Long, appsKilledCount: Int) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingAppIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val turnOffIntent = Intent(context, com.example.receiver.SmartDndWidgetProvider::class.java).apply {
            action = com.example.receiver.SmartDndWidgetProvider.ACTION_TOGGLE_DND
        }
        val pendingTurnOffIntent = PendingIntent.getBroadcast(
            context,
            1,
            turnOffIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (appsKilledCount > 0) {
            "DND Active • Cleaned $appsKilledCount apps (~${freedMemoryMb}MB RAM)"
        } else {
            "DND Active • Silencing alerts & managing background apps"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_smart_dnd_1793105166534)
            .setContentTitle("🌙 Smart DND is Active")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Turn Off",
                pendingTurnOffIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelActiveNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

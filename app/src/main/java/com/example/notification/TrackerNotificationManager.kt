package com.example.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

class TrackerNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE = "tracker_service_channel"
        const val CHANNEL_ALARM = "find_phone_alarm_channel"
        const val CHANNEL_EVENTS = "tracker_events_channel"

        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_ALARM = 1002
        const val NOTIFICATION_ID_EVENT = 1003

        const val ACTION_STOP_ALARM = "com.example.bletracker.ACTION_STOP_ALARM"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Background monitoring service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Tracker Active Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of connected Bluetooth trackers"
                setShowBadge(false)
            }

            // Loud Find Phone alarm channel
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                "Find Phone Emergency Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts triggered by pressing your physical Bluetooth tracker"
                enableVibration(true)
                enableLights(true)
            }

            // Out-of-range & safety channel
            val eventsChannel = NotificationChannel(
                CHANNEL_EVENTS,
                "Tracker Out of Range & Safety Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a tracker disconnects or unknown beacons appear"
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, alarmChannel, eventsChannel)
            )
        }
    }

    fun buildForegroundServiceNotification(connectedCount: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (connectedCount > 0) {
            "Monitoring $connectedCount connected Bluetooth tracker(s)"
        } else {
            "Scanning for paired Bluetooth trackers in range"
        }

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("Bluetooth Tracker Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showFindPhoneNotification(trackerName: String) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_ALARM", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action intent to stop alarm directly from notification shade
        val stopIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_STOP_ALARM
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setContentTitle("Your phone is here!")
            .setContentText("Triggered by physical button on \"$trackerName\"")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", stopPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALARM, notification)
        } catch (e: Exception) {
            // Permission or notification disabled
        }
    }

    fun dismissFindPhoneNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ALARM)
    }

    @SuppressLint("MissingPermission")
    fun showOutOfRangeNotification(trackerName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle("Tracker Out of Range")
            .setContentText("\"$trackerName\" lost Bluetooth connection. Last known location recorded.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_EVENT, notification)
        } catch (e: Exception) {
            // ignore
        }
    }
}

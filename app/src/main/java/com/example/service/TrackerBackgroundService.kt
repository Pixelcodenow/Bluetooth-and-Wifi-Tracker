package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.BleTrackerApp
import com.example.ble.BleEvent
import com.example.ble.ConnectionState
import com.example.notification.TrackerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TrackerBackgroundService : Service() {

    private val tag = "TrackerBgService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, TrackerBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackerBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "TrackerBackgroundService created")

        val app = application as BleTrackerApp
        val notification = app.notificationManager.buildForegroundServiceNotification(0)

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            TrackerNotificationManager.NOTIFICATION_ID_SERVICE,
            notification,
            foregroundType
        )

        observeBleEvents(app)
        observeConnectedCount(app)
    }

    private fun observeBleEvents(app: BleTrackerApp) {
        serviceScope.launch {
            app.bleManager.events.collectLatest { event ->
                when (event) {
                    is BleEvent.ButtonPressed -> {
                        val tracker = app.repository.getTrackerByMac(event.mac)
                        val trackerName = tracker?.displayName ?: "Bluetooth Tracker"
                        // Trigger the loud phone alarm
                        app.alarmManager.startAlarm(trackerName)
                        app.notificationManager.showFindPhoneNotification(trackerName)
                        app.repository.logEvent(
                            mac = event.mac,
                            type = "FIND_PHONE_TRIGGERED",
                            details = "Physical button pressed on tracker (Type: 0x${event.clickType.toString(16)})"
                        )
                    }
                    is BleEvent.DeviceDisconnected -> {
                        val tracker = app.repository.getTrackerByMac(event.mac)
                        val trackerName = tracker?.displayName ?: "Bluetooth Tracker"
                        // Save last known phone location at moment of disconnection
                        val loc = app.locationHelper.getLastKnownPhoneLocation()
                        app.repository.updateLastLocation(
                            mac = event.mac,
                            timestamp = System.currentTimeMillis(),
                            lat = loc?.latitude,
                            lng = loc?.longitude,
                            label = loc?.addressLabel
                        )
                        app.repository.logEvent(
                            mac = event.mac,
                            type = "DISCONNECTED",
                            details = "Connection lost. Recorded phone's last known position.",
                            lat = loc?.latitude,
                            lng = loc?.longitude
                        )
                        app.notificationManager.showOutOfRangeNotification(trackerName)
                    }
                    is BleEvent.DeviceConnected -> {
                        val loc = app.locationHelper.getLastKnownPhoneLocation()
                        app.repository.updateLastLocation(
                            mac = event.mac,
                            timestamp = System.currentTimeMillis(),
                            lat = loc?.latitude,
                            lng = loc?.longitude,
                            label = loc?.addressLabel
                        )
                        app.repository.logEvent(
                            mac = event.mac,
                            type = "CONNECTED",
                            details = "Connected to tracker.",
                            lat = loc?.latitude,
                            lng = loc?.longitude
                        )
                    }
                    is BleEvent.BatteryUpdated -> {
                        app.repository.updateBatteryLevel(event.mac, event.level)
                    }
                    is BleEvent.RssiUpdated -> {
                        app.repository.updateRssi(event.mac, event.rssi)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeConnectedCount(app: BleTrackerApp) {
        serviceScope.launch {
            app.bleManager.trackerStatuses.collectLatest { statuses ->
                val connectedCount = statuses.values.count { it.connectionState == ConnectionState.CONNECTED }
                val notification = app.notificationManager.buildForegroundServiceNotification(connectedCount)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(TrackerNotificationManager.NOTIFICATION_ID_SERVICE, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "TrackerBackgroundService destroyed")
    }
}

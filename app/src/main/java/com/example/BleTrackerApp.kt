package com.example

import android.app.Application
import com.example.alarm.FindPhoneAlarmManager
import com.example.ble.BleManager
import com.example.data.db.AppDatabase
import com.example.data.repository.TrackerRepository
import com.example.location.LocationHelper
import com.example.notification.TrackerNotificationManager

class BleTrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: TrackerRepository
        private set

    lateinit var notificationManager: TrackerNotificationManager
        private set

    lateinit var alarmManager: FindPhoneAlarmManager
        private set

    lateinit var locationHelper: LocationHelper
        private set

    lateinit var bleManager: BleManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        repository = TrackerRepository(
            trackerDao = database.trackerDao(),
            trackerEventDao = database.trackerEventDao(),
            unknownTrackerDao = database.unknownTrackerDao()
        )
        notificationManager = TrackerNotificationManager(this)
        notificationManager.createNotificationChannels()

        alarmManager = FindPhoneAlarmManager(this)
        locationHelper = LocationHelper(this)
        bleManager = BleManager(this)
    }

    companion object {
        lateinit var instance: BleTrackerApp
            private set
    }
}

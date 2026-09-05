package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracker_events")
data class TrackerEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackerMac: String,
    val eventType: String, // "CONNECTED", "DISCONNECTED", "FIND_PHONE_TRIGGERED", "BEEP_COMMAND", "BATTERY_ALERT"
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val details: String = ""
)

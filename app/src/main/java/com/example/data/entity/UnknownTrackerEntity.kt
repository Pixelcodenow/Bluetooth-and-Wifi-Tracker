package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unknown_trackers",
    indices = [Index(value = ["macAddress"], unique = true)]
)
data class UnknownTrackerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val macAddress: String,
    val deviceName: String = "Unknown Beacon",
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val detectionCount: Int = 1,
    val highestRssi: Int = -100,
    val isDismissed: Boolean = false,
    val riskLevel: String = "LOW" // LOW, MEDIUM, HIGH
)

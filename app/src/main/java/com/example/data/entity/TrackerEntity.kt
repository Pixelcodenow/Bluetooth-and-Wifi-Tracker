package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trackers",
    indices = [Index(value = ["macAddress"], unique = true)]
)
data class TrackerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val macAddress: String,
    val name: String,
    val customName: String = "",
    val colorArgb: Long = 0xFF00C9FF, // Cyan default
    val iconType: String = "KEYS", // KEYS, WALLET, BACKPACK, LUGGAGE, BIKE, PET, OTHER
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationLabel: String? = null,
    val batteryLevel: Int? = null, // 0 - 100%
    val isAutoReconnect: Boolean = true,
    val isFavorite: Boolean = false,
    val signalRssi: Int = 0
) {
    val displayName: String
        get() = customName.ifBlank { name.ifBlank { "BLE Tracker (${macAddress.takeLast(5)})" } }
}

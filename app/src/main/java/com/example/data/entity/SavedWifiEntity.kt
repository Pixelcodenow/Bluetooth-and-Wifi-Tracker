package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_wifi_networks",
    indices = [Index(value = ["bssid"], unique = true)]
)
data class SavedWifiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bssid: String,
    val ssid: String,
    val customName: String = "",
    val security: String = "Unknown",
    val frequency: Int = 0,
    val band: String = "2.4 GHz",
    val channel: Int = 0,
    val isFavorite: Boolean = false,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val lastRssi: Int = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationLabel: String? = null
) {
    val displayName: String
        get() = customName.ifBlank { ssid.ifBlank { "Hidden Network (${bssid.takeLast(5)})" } }
}

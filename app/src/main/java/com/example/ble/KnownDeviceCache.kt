package com.example.ble

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent cache for remembered device names and last known phone GPS positions.
 * This ensures that if a device was once identified by name or manufacturer,
 * subsequent scan packets that omit the name still display the accurate, known name.
 */
class KnownDeviceCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ble_known_devices_cache", Context.MODE_PRIVATE)

    fun saveDeviceName(address: String, name: String) {
        if (name.isBlank() || name.equals("Unknown BLE Device", ignoreCase = true) || name.startsWith("BLE Device")) {
            return
        }
        prefs.edit().putString("name_$address", name.trim()).apply()
    }

    fun getDeviceName(address: String): String? {
        val name = prefs.getString("name_$address", null)
        return if (!name.isNullOrBlank()) name else null
    }

    fun saveLastLocation(address: String, lat: Double, lng: Double, label: String, timestamp: Long) {
        prefs.edit()
            .putString("lat_$address", lat.toString())
            .putString("lng_$address", lng.toString())
            .putString("label_$address", label)
            .putLong("time_$address", timestamp)
            .apply()
    }

    fun getLastLocation(address: String): CachedLocation? {
        val latStr = prefs.getString("lat_$address", null) ?: return null
        val lngStr = prefs.getString("lng_$address", null) ?: return null
        val label = prefs.getString("label_$address", "") ?: ""
        val timestamp = prefs.getLong("time_$address", 0L)
        return try {
            CachedLocation(
                latitude = latStr.toDouble(),
                longitude = lngStr.toDouble(),
                addressLabel = label,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class CachedLocation(
    val latitude: Double,
    val longitude: Double,
    val addressLabel: String,
    val timestamp: Long
)

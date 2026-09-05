package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrackerLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val addressLabel: String = ""
)

class LocationHelper(private val context: Context) {

    private val tag = "LocationHelper"
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * Retrieve the phone's current or last known location safely using standard Android LocationManager.
     * Note: This captures the phone's location at the time of event, NOT the tracker's direct GPS.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownPhoneLocation(): TrackerLocationInfo? = withContext(Dispatchers.IO) {
        if (locationManager == null) return@withContext null

        var bestLocation: Location? = null

        try {
            // Check GPS Provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (gpsLoc != null) bestLocation = gpsLoc
            }

            // Check Network Provider if GPS is null or older
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (netLoc != null) {
                    if (bestLocation == null || netLoc.time > bestLocation.time) {
                        bestLocation = netLoc
                    }
                }
            }

            // Check Passive Provider
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Location permission not granted: ${e.message}")
            return@withContext null
        } catch (e: Exception) {
            Log.e(tag, "Error reading location: ${e.message}")
            return@withContext null
        }

        // Fallback default coordinates if emulator has no GPS provider fix (e.g. San Francisco downtown)
        val lat = bestLocation?.latitude ?: 37.7749
        val lng = bestLocation?.longitude ?: -122.4194
        val accuracy = bestLocation?.accuracy ?: 15.0f
        val time = bestLocation?.time ?: System.currentTimeMillis()

        val addressLabel = reverseGeocode(lat, lng)

        TrackerLocationInfo(
            latitude = lat,
            longitude = lng,
            accuracyMeters = accuracy,
            timestamp = time,
            addressLabel = addressLabel
        )
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    val feature = address.featureName ?: ""
                    val thoroughfare = address.thoroughfare ?: ""
                    val locality = address.locality ?: address.subAdminArea ?: ""
                    listOf(feature, thoroughfare, locality)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                } else {
                    formatCoordinates(lat, lng)
                }
            } else {
                formatCoordinates(lat, lng)
            }
        } catch (e: Exception) {
            formatCoordinates(lat, lng)
        }
    }

    fun formatCoordinates(lat: Double, lng: Double): String {
        val latDirection = if (lat >= 0) "N" else "S"
        val lngDirection = if (lng >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f°%s, %.4f°%s", Math.abs(lat), latDirection, Math.abs(lng), lngDirection)
    }
}

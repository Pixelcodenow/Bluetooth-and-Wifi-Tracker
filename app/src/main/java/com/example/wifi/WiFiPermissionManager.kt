package com.example.wifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

sealed class WiFiPermissionStatus {
    object Granted : WiFiPermissionStatus()
    data class MissingPermissions(
        val permissions: List<String>,
        val rationale: String
    ) : WiFiPermissionStatus()
    object LocationServiceDisabled : WiFiPermissionStatus()
}

class WiFiPermissionManager(private val context: Context) {

    /**
     * Inspects all runtime requirements for native Wi-Fi scanning on this specific Android version.
     */
    fun checkScanPrerequisites(): WiFiPermissionStatus {
        val missingPermissions = mutableListOf<String>()

        // 1. Precise location is mandatory on Android 6.0+ to obtain Wi-Fi scan results from WifiManager
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // 2. Android 13+ (API 33) NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearbyWifi = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNearbyWifi) {
                missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val rationale = if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                "Location permission is required by this version of Android to discover nearby Wi-Fi networks."
            } else {
                "Nearby Wi-Fi devices permission is required to scan nearby wireless networks."
            }
            return WiFiPermissionStatus.MissingPermissions(missingPermissions, rationale)
        }

        // 3. Android requires device Location Services to be enabled for WifiManager to deliver scan results
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationEnabled = if (locationManager != null) {
            LocationManagerCompat.isLocationEnabled(locationManager)
        } else {
            false
        }

        if (!isLocationEnabled) {
            return WiFiPermissionStatus.LocationServiceDisabled
        }

        return WiFiPermissionStatus.Granted
    }

    fun isAllGranted(): Boolean {
        return checkScanPrerequisites() is WiFiPermissionStatus.Granted
    }

    /**
     * Direct user to system Location toggle screen.
     */
    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Direct user to this application's App Info settings screen.
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

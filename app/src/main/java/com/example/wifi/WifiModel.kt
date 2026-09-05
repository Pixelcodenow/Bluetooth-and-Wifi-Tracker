package com.example.wifi

import androidx.compose.ui.graphics.Color

/**
 * High-level Wi-Fi UI states as required by the specification.
 */
enum class WifiUiState {
    INITIALIZING,
    WIFI_OFF,
    PERMISSION_REQUIRED,
    LOCATION_REQUIRED,
    READY,
    SCANNING,
    RESULTS,
    NO_RESULTS,
    SCAN_FAILED,
    SCAN_TIMEOUT,
    CONNECTING,
    CONNECTED,
    CONNECTION_FAILED,
    OS_RESTRICTED
}

/**
 * Real physical state of the phone's Wi-Fi hardware adapter.
 */
enum class WifiAdapterState(val label: String) {
    WIFI_ON("Wi-Fi ON"),
    WIFI_OFF("Wi-Fi OFF"),
    WIFI_TURNING_ON("Wi-Fi Turning ON"),
    WIFI_TURNING_OFF("Wi-Fi Turning OFF"),
    WIFI_UNAVAILABLE("Wi-Fi Hardware Unavailable")
}

/**
 * Scan state machine states.
 */
enum class WifiScanStep(val label: String) {
    IDLE("Idle"),
    CHECKING_WIFI("Checking Wi-Fi Adapter..."),
    CHECKING_PERMISSIONS("Checking Permissions..."),
    REQUESTING_SCAN("Requesting Wi-Fi Scan..."),
    WAITING_FOR_RESULTS("Waiting for Scan Results..."),
    PROCESSING_RESULTS("Processing Fresh Results..."),
    SCAN_COMPLETE("Scan Completed"),
    SCAN_FAILED("Scan Failed"),
    SCAN_TIMEOUT("Scan Timed Out")
}

/**
 * Security protocol classification strictly parsed from real scan capabilities.
 */
enum class WifiSecurityType(
    val label: String,
    val color: Color,
    val isSecure: Boolean,
    val description: String
) {
    OPEN("Open / Unsecured", Color(0xFFEF4444), false, "Unencrypted open network; traffic can be monitored"),
    WEP("WEP (Insecure)", Color(0xFFF97316), true, "Obsolete encryption standard with critical vulnerabilities"),
    WPA("WPA (Legacy)", Color(0xFFF97316), true, "Older security standard superseded by WPA2"),
    WPA2("WPA2", Color(0xFFEAB308), true, "Standard modern security protocol (AES-CCMP)"),
    WPA3("WPA3", Color(0xFF10B981), true, "Latest wireless security standard with SAE handshake"),
    WPA_WPA2("WPA/WPA2 Mixed", Color(0xFFEAB308), true, "Mixed WPA and WPA2 backwards-compatible security"),
    WPA2_WPA3("WPA2/WPA3 Transition", Color(0xFF10B981), true, "Transition mode supporting WPA3-SAE and WPA2-PSK"),
    ENTERPRISE("Enterprise (802.1X)", Color(0xFFA855F7), true, "Corporate RADIUS authentication with per-user credentials"),
    UNKNOWN("Unknown Security", Color(0xFF94A3B8), false, "Security protocol not identified from beacon capabilities")
}

/**
 * 6-Tier Signal strength categories with 10-block visual meters.
 * Explicitly states that signal does NOT equate to physical distance.
 */
enum class WifiSignalLevel(
    val label: String,
    val blocksString: String,
    val filledBlocks: Int,
    val color: Color,
    val proximityCategory: String
) {
    EXCELLENT("EXCELLENT", "██████████", 10, Color(0xFF10B981), "Very Strong"),
    STRONG("STRONG", "████████░░", 8, Color(0xFF22C55E), "Strong"),
    GOOD("GOOD", "██████░░░░", 6, Color(0xFFEAB308), "Good"),
    FAIR("FAIR", "████░░░░░░", 4, Color(0xFFF59E0B), "Fair"),
    WEAK("WEAK", "██░░░░░░░░", 2, Color(0xFFF97316), "Weak"),
    VERY_WEAK("VERY WEAK", "█░░░░░░░░░", 1, Color(0xFFEF4444), "Very Weak"),
    SIGNAL_LOST("SIGNAL LOST", "░░░░░░░░░░", 0, Color(0xFF94A3B8), "No Signal");

    companion object {
        fun fromRssi(rssi: Int, isLost: Boolean = false): WifiSignalLevel {
            if (isLost || rssi == 0 || rssi <= -100) return SIGNAL_LOST
            return when {
                rssi >= -50 -> EXCELLENT
                rssi >= -60 -> STRONG
                rssi >= -70 -> GOOD
                rssi >= -80 -> FAIR
                rssi >= -90 -> WEAK
                else -> VERY_WEAK
            }
        }
    }
}

/**
 * Lifecycle state of a detected network to avoid UI jumping and flickering.
 */
enum class WifiLifecycleState(val label: String, val alpha: Float) {
    ACTIVE("Active", 1.0f),               // Detected in the current fresh scan
    RECENTLY_SEEN("Recently Seen", 0.85f), // Detected in previous scan but not current
    OUT_OF_RANGE("Out of Range", 0.55f),  // Not detected in last 2 scans
    LOST("Lost", 0.35f);                  // Disappeared from multiple scan cycles
}

/**
 * Proximity signal trend for Find Network mode.
 */
enum class WifiSignalTrend(val label: String, val symbol: String, val color: Color) {
    GETTING_STRONGER("Signal getting stronger", "↑", Color(0xFF10B981)),
    GETTING_WEAKER("Signal getting weaker", "↓", Color(0xFFEF4444)),
    STABLE("Signal stable", "→", Color(0xFF38BDF8)),
    SIGNAL_LOST("Signal lost", "✕", Color(0xFF94A3B8))
}

/**
 * Data model for a real detected Wi-Fi Access Point / Network.
 * Uses BSSID (or unique composite) as identity so distinct APs sharing an SSID are never merged.
 */
data class WifiNetworkModel(
    val bssid: String,
    val ssid: String,
    val rawRssi: Int,
    val smoothedRssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: String,
    val channelWidth: String,
    val securityType: WifiSecurityType,
    val capabilitiesString: String,
    val wifiStandard: String,
    val isConnected: Boolean = false,
    val isHidden: Boolean = false,
    val isFreshResult: Boolean = true,
    val lifecycleState: WifiLifecycleState = WifiLifecycleState.ACTIVE,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val scanTimestampMicros: Long = 0L,
    val rssiHistory: List<Int> = listOf(rawRssi),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val customName: String = "",
    val isFavorite: Boolean = false
) {
    val id: String get() = if (bssid.isNotBlank()) bssid else "${ssid}_${frequency}"

    val displayName: String
        get() = when {
            customName.isNotBlank() -> customName
            ssid.isNotBlank() -> ssid
            else -> "Hidden Network (${bssid.takeLast(5)})"
        }

    val signalLevel: WifiSignalLevel
        get() = WifiSignalLevel.fromRssi(smoothedRssi, lifecycleState == WifiLifecycleState.LOST)

    val lastSeenAgoText: String
        get() {
            val diffSec = ((System.currentTimeMillis() - lastSeenTimestamp) / 1000).coerceAtLeast(0)
            return when {
                diffSec < 3 -> "Just now"
                diffSec < 60 -> "$diffSec sec ago"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                else -> "${diffSec / 3600}h ago"
            }
        }

    val formattedFirstSeenTime: String
        get() = java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(firstSeenTimestamp))

    val formattedLastSeenTime: String
        get() = java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(lastSeenTimestamp))

    val hysteresisBucket: Int
        get() = (smoothedRssi / 5) * 5

    val signalPercentage: Int
        get() = (2 * (smoothedRssi + 100)).coerceIn(0, 100)

    val signalTrend: WifiSignalTrend
        get() = WiFiSignalProcessor.calculateSignalTrend(rssiHistory, lifecycleState == WifiLifecycleState.LOST)
}

/**
 * Data model for the currently connected Wi-Fi link.
 */
data class ConnectedWifiInfo(
    val isConnected: Boolean,
    val ssid: String = "",
    val bssid: String = "",
    val rssi: Int = 0,
    val smoothedRssi: Int = 0,
    val linkSpeedMbps: Int = 0,
    val frequency: Int = 0,
    val band: String = "",
    val channel: Int = 0,
    val ipAddress: String = "",
    val gateway: String = "",
    val wifiStandard: String = ""
) {
    val signalLevel: WifiSignalLevel
        get() = if (isConnected) WifiSignalLevel.fromRssi(if (smoothedRssi != 0) smoothedRssi else rssi) else WifiSignalLevel.SIGNAL_LOST
}

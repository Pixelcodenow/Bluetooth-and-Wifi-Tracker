package com.example.wifi

import android.net.wifi.ScanResult
import android.os.Build
import android.os.SystemClock
import java.util.Locale

object WiFiResultParser {

    /**
     * Parses a native Android ScanResult into a clean, platform-aware WifiNetworkModel.
     */
    fun parseScanResult(
        result: ScanResult,
        connectedBssid: String?,
        smoothedRssi: Int,
        firstSeen: Long,
        isFresh: Boolean,
        lifecycle: WifiLifecycleState,
        history: List<Int>,
        lat: Double? = null,
        lng: Double? = null,
        customName: String = "",
        isFavorite: Boolean = false
    ): WifiNetworkModel {
        val bssid = result.BSSID ?: ""

        @Suppress("DEPRECATION")
        val rawSsid = result.SSID ?: ""
        val cleanSsid = cleanSsid(rawSsid)
        val isHidden = cleanSsid.isBlank() || cleanSsid == "<unknown ssid>" || cleanSsid.startsWith("0x")

        val rawRssi = result.level
        val frequency = result.frequency
        val band = determineBand(frequency)
        val channel = calculateChannel(frequency)
        val channelWidth = determineChannelWidth(result.channelWidth)
        val capabilities = result.capabilities ?: ""
        val security = parseSecurity(capabilities)
        val wifiStandard = determineWifiStandard(result)

        // Convert ScanResult.timestamp (microseconds since boot) to epoch millis
        val epochMillis = convertBootMicrosToEpoch(result.timestamp)

        val isConnected = !connectedBssid.isNullOrBlank() && connectedBssid.equals(bssid, ignoreCase = true)

        return WifiNetworkModel(
            bssid = bssid,
            ssid = cleanSsid,
            rawRssi = rawRssi,
            smoothedRssi = smoothedRssi,
            frequency = frequency,
            channel = channel,
            band = band,
            channelWidth = channelWidth,
            securityType = security,
            capabilitiesString = capabilities,
            wifiStandard = wifiStandard,
            isConnected = isConnected,
            isHidden = isHidden,
            isFreshResult = isFresh,
            lifecycleState = lifecycle,
            firstSeenTimestamp = firstSeen,
            lastSeenTimestamp = System.currentTimeMillis(),
            scanTimestampMicros = result.timestamp,
            rssiHistory = history,
            latitude = lat,
            longitude = lng,
            customName = customName,
            isFavorite = isFavorite
        )
    }

    fun cleanSsid(ssid: String): String {
        return if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length >= 2) {
            ssid.substring(1, ssid.length - 1)
        } else {
            ssid
        }
    }

    fun determineBand(frequency: Int): String {
        return when {
            frequency in 2412..2484 -> "2.4 GHz"
            frequency in 4915..5825 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz (Wi-Fi 6E)"
            frequency in 57000..71000 -> "60 GHz (WiGig)"
            else -> if (frequency > 5000) "5 GHz" else "2.4 GHz"
        }
    }

    fun calculateChannel(frequency: Int): Int {
        return when {
            frequency == 2484 -> 14
            frequency in 2412..2472 -> (frequency - 2407) / 5
            frequency in 5170..5825 -> (frequency - 5000) / 5
            frequency in 5925..7125 -> (frequency - 5940) / 5
            else -> 0
        }
    }

    fun determineChannelWidth(widthCode: Int): String {
        return when (widthCode) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
            else -> "20 MHz"
        }
    }

    fun parseSecurity(capabilities: String): WifiSecurityType {
        val upper = capabilities.uppercase(Locale.US)
        return when {
            upper.contains("SAE") && upper.contains("PSK") -> WifiSecurityType.WPA2_WPA3
            upper.contains("SAE") || upper.contains("WPA3") -> WifiSecurityType.WPA3
            upper.contains("EAP") || upper.contains("802.1X") -> WifiSecurityType.ENTERPRISE
            upper.contains("WPA2") || upper.contains("RSN-PSK") -> {
                if (upper.contains("WPA-PSK")) WifiSecurityType.WPA_WPA2 else WifiSecurityType.WPA2
            }
            upper.contains("WPA") -> WifiSecurityType.WPA
            upper.contains("WEP") -> WifiSecurityType.WEP
            upper.contains("OWE") -> WifiSecurityType.OPEN
            upper.contains("ESS") && !upper.contains("WPA") && !upper.contains("WEP") && !upper.contains("RSN") -> WifiSecurityType.OPEN
            else -> WifiSecurityType.UNKNOWN
        }
    }

    fun determineWifiStandard(result: ScanResult): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (result.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Legacy (802.11a/b/g)"
                else -> {
                    if (result.frequency > 5900) "Wi-Fi 6E"
                    else if (result.frequency > 4900) "Wi-Fi 5"
                    else "Wi-Fi 4"
                }
            }
        }
        return if (result.frequency > 4900) "Wi-Fi 5 (802.11ac)" else "Wi-Fi 4 (802.11n)"
    }

    private fun convertBootMicrosToEpoch(micros: Long): Long {
        if (micros <= 0) return System.currentTimeMillis()
        val bootMillis = micros / 1000L
        val currentBootMillis = SystemClock.elapsedRealtime()
        val delta = currentBootMillis - bootMillis
        return System.currentTimeMillis() - delta
    }
}

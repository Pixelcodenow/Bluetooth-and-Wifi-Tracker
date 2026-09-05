package com.example.wifi

import kotlin.math.roundToInt

object WiFiSignalProcessor {

    private const val DEFAULT_ALPHA = 0.35f

    /**
     * Exponential Moving Average (EMA) filter to prevent RSSI jumping.
     */
    fun smoothRssi(currentRawRssi: Int, previousSmoothedRssi: Int, alpha: Float = DEFAULT_ALPHA): Int {
        if (previousSmoothedRssi == 0 || previousSmoothedRssi <= -120) {
            return currentRawRssi
        }
        val smoothed = (alpha * currentRawRssi) + ((1.0f - alpha) * previousSmoothedRssi)
        return smoothed.roundToInt()
    }

    /**
     * Evaluates signal direction trend based on recent RSSI history.
     */
    fun calculateSignalTrend(history: List<Int>, isLost: Boolean): WifiSignalTrend {
        if (isLost || history.size < 2) {
            return if (isLost) WifiSignalTrend.SIGNAL_LOST else WifiSignalTrend.STABLE
        }
        val recent = history.takeLast(4)
        val first = recent.first()
        val last = recent.last()
        val delta = last - first

        return when {
            delta >= 3 -> WifiSignalTrend.GETTING_STRONGER
            delta <= -3 -> WifiSignalTrend.GETTING_WEAKER
            else -> WifiSignalTrend.STABLE
        }
    }

    /**
     * Stable sorting function to prevent tiny RSSI fluctuations from constantly re-ordering cards.
     */
    fun sortNetworks(
        networks: List<WifiNetworkModel>,
        sortType: WifiSortType
    ): List<WifiNetworkModel> {
        return when (sortType) {
            WifiSortType.STRONGEST_SIGNAL -> {
                networks.sortedWith(
                    compareByDescending<WifiNetworkModel> { it.isConnected }
                        .thenByDescending { it.smoothedRssi }
                        .thenBy { it.displayName }
                        .thenBy { it.bssid }
                )
            }
            WifiSortType.WEAKEST_SIGNAL -> {
                networks.sortedWith(
                    compareBy<WifiNetworkModel> { it.smoothedRssi }
                        .thenBy { it.displayName }
                )
            }
            WifiSortType.NAME -> {
                networks.sortedWith(
                    compareBy<WifiNetworkModel> { it.displayName.lowercase() }
                        .thenByDescending { it.smoothedRssi }
                )
            }
            WifiSortType.FREQUENCY -> {
                networks.sortedWith(
                    compareByDescending<WifiNetworkModel> { it.frequency }
                        .thenByDescending { it.smoothedRssi }
                )
            }
            WifiSortType.SECURITY -> {
                networks.sortedWith(
                    compareByDescending<WifiNetworkModel> { it.securityType.ordinal }
                        .thenByDescending { it.smoothedRssi }
                )
            }
            WifiSortType.CONNECTED_FIRST -> {
                networks.sortedWith(
                    compareByDescending<WifiNetworkModel> { it.isConnected }
                        .thenByDescending { it.smoothedRssi }
                )
            }
        }
    }
}

enum class WifiSortType(val label: String) {
    STRONGEST_SIGNAL("Strongest Signal"),
    WEAKEST_SIGNAL("Weakest Signal"),
    NAME("Network Name (A-Z)"),
    FREQUENCY("Band / Frequency"),
    SECURITY("Security Type"),
    CONNECTED_FIRST("Connected First")
}

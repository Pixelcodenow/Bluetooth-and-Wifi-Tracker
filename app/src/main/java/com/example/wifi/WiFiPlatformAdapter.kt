package com.example.wifi

object WiFiPlatformAdapter {

    data class PlatformCapabilities(
        val platformName: String,
        val supportsNearbyScanning: Boolean,
        val supportsConnectedNetworkInfo: Boolean,
        val supportsRssiSmoothing: Boolean,
        val supportsFrequencyAndBand: Boolean,
        val supportsChannelDetection: Boolean,
        val scanThrottlingBehavior: String,
        val platformLimitationsExplanation: String
    )

    fun getAndroidCapabilities(): PlatformCapabilities {
        return PlatformCapabilities(
            platformName = "Android",
            supportsNearbyScanning = true,
            supportsConnectedNetworkInfo = true,
            supportsRssiSmoothing = true,
            supportsFrequencyAndBand = true,
            supportsChannelDetection = true,
            scanThrottlingBehavior = "Android 9+ throttles foreground apps to 4 scans every 2 minutes. Throttled scans return the most recent system scan cache.",
            platformLimitationsExplanation = "Android provides native Wi-Fi scan results through WifiManager. Requires ACCESS_FINE_LOCATION and active device Location Services to deliver access point beacons."
        )
    }

    fun getIosCapabilities(): PlatformCapabilities {
        return PlatformCapabilities(
            platformName = "iOS (Apple)",
            supportsNearbyScanning = false,
            supportsConnectedNetworkInfo = true,
            supportsRssiSmoothing = false,
            supportsFrequencyAndBand = false,
            supportsChannelDetection = false,
            scanThrottlingBehavior = "No nearby scanning API exists for third-party apps in iOS SDK.",
            platformLimitationsExplanation = "Apple does not permit third-party iOS apps to scan for nearby Wi-Fi networks. Only NEHotspotNetwork.fetchCurrent(withCompletionHandler:) is available to query the currently connected Wi-Fi SSID, requiring the 'com.apple.developer.networking.wifi-info' entitlement and Location permission. This application legitimately documents this restriction rather than fabricating simulated scan results."
        )
    }
}

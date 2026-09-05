package com.example.wifi

data class WiFiDiagnosticsData(
    val platform: String = "Android",
    val osVersion: String = android.os.Build.VERSION.RELEASE,
    val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
    val wifiAdapterState: WifiAdapterState = WifiAdapterState.WIFI_OFF,
    val isWifiEnabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val locationServicesOn: Boolean = false,
    val scannerInitialized: Boolean = false,
    val scanRequested: Boolean = false,
    val scanCallbackReceived: Boolean = false,
    val resultCount: Int = 0,
    val freshResultCount: Int = 0,
    val lastScanTimeFormatted: String = "Never",
    val scanDurationSeconds: Double = 0.0,
    val lastScanSuccess: Boolean = false,
    val lastFailureReason: String? = null,
    val isThrottled: Boolean = false
)

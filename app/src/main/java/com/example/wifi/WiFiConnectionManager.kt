package com.example.wifi

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WifiConnectionStep(val label: String) {
    IDLE("Idle"),
    CONNECTING("Connecting to network..."),
    CONNECTED("Successfully Connected"),
    CONNECTION_FAILED("Connection Failed"),
    WRONG_PASSWORD("Authentication Failed (Check Password)"),
    NETWORK_UNAVAILABLE("Network is no longer available in range"),
    OS_RESTRICTED("Programmatic connection restricted by OS; open system settings")
}

class WiFiConnectionManager(
    private val context: Context,
    private val connectivityManager: ConnectivityManager?
) {
    private val tag = "WiFiConnectionManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionStep = MutableStateFlow(WifiConnectionStep.IDLE)
    val connectionStep: StateFlow<WifiConnectionStep> = _connectionStep.asStateFlow()

    private val _connectingSsid = MutableStateFlow<String?>(null)
    val connectingSsid: StateFlow<String?> = _connectingSsid.asStateFlow()

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectionTimeoutJob: Job? = null

    fun connectToNetwork(
        network: WifiNetworkModel,
        passphrase: String? = null
    ) {
        // Reset state
        cancelActiveConnection()

        _connectingSsid.value = network.displayName
        _connectionStep.value = WifiConnectionStep.CONNECTING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifierBuilder = WifiNetworkSpecifier.Builder()

                if (network.ssid.isNotBlank()) {
                    specifierBuilder.setSsid(network.ssid)
                }
                if (network.bssid.isNotBlank()) {
                    try {
                        specifierBuilder.setBssid(android.net.MacAddress.fromString(network.bssid))
                    } catch (_: Exception) {}
                }

                if (network.securityType == WifiSecurityType.OPEN || passphrase.isNullOrBlank()) {
                    // Open network
                } else if (network.securityType == WifiSecurityType.WPA3) {
                    specifierBuilder.setWpa3Passphrase(passphrase)
                } else {
                    specifierBuilder.setWpa2Passphrase(passphrase)
                }

                val specifier = specifierBuilder.build()
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(net: Network) {
                        Log.i(tag, "Wi-Fi connection successful to ${network.ssid}")
                        connectionTimeoutJob?.cancel()
                        _connectionStep.value = WifiConnectionStep.CONNECTED
                    }

                    override fun onUnavailable() {
                        Log.w(tag, "Wi-Fi network connection unavailable for ${network.ssid}")
                        _connectionStep.value = WifiConnectionStep.CONNECTION_FAILED
                    }

                    override fun onLost(net: Network) {
                        Log.i(tag, "Wi-Fi connection lost for ${network.ssid}")
                        if (_connectionStep.value == WifiConnectionStep.CONNECTED) {
                            _connectionStep.value = WifiConnectionStep.IDLE
                        }
                    }
                }

                activeNetworkCallback = callback
                connectivityManager?.requestNetwork(request, callback)

                // 25 second timeout for user confirmation & DHCP handshake
                connectionTimeoutJob = scope.launch {
                    delay(25_000L)
                    if (_connectionStep.value == WifiConnectionStep.CONNECTING) {
                        Log.w(tag, "Connection attempt timed out")
                        _connectionStep.value = WifiConnectionStep.CONNECTION_FAILED
                        cancelActiveConnection()
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Failed to initiate WifiNetworkSpecifier connection: ${e.message}")
                _connectionStep.value = WifiConnectionStep.OS_RESTRICTED
            }
        } else {
            // Android 9 and below delegate to system settings
            openSystemWifiSettings()
            _connectionStep.value = WifiConnectionStep.OS_RESTRICTED
        }
    }

    fun cancelActiveConnection() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
        activeNetworkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        activeNetworkCallback = null
        _connectingSsid.value = null
        _connectionStep.value = WifiConnectionStep.IDLE
    }

    fun openSystemWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening Wi-Fi settings: ${e.message}")
        }
    }
}

package com.example.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WiFiStateManager(private val context: Context) {

    private val tag = "WiFiStateManager"
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _adapterState = MutableStateFlow(determineInitialState())
    val adapterState: StateFlow<WifiAdapterState> = _adapterState.asStateFlow()

    private var isReceiverRegistered = false

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                val stateInt = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                val newState = mapWifiState(stateInt)
                Log.d(tag, "Wi-Fi adapter state changed: $newState (int=$stateInt)")
                _adapterState.value = newState
            }
        }
    }

    init {
        registerReceiver()
        // Refresh state
        refreshState()
    }

    fun refreshState() {
        _adapterState.value = determineInitialState()
    }

    fun isWifiOn(): Boolean {
        return _adapterState.value == WifiAdapterState.WIFI_ON
    }

    private fun determineInitialState(): WifiAdapterState {
        if (wifiManager == null) {
            return WifiAdapterState.WIFI_UNAVAILABLE
        }
        return try {
            mapWifiState(wifiManager.wifiState)
        } catch (e: Exception) {
            Log.w(tag, "Failed to read wifiState: ${e.message}")
            if (wifiManager.isWifiEnabled) WifiAdapterState.WIFI_ON else WifiAdapterState.WIFI_OFF
        }
    }

    private fun mapWifiState(stateInt: Int): WifiAdapterState {
        return when (stateInt) {
            WifiManager.WIFI_STATE_ENABLED -> WifiAdapterState.WIFI_ON
            WifiManager.WIFI_STATE_DISABLED -> WifiAdapterState.WIFI_OFF
            WifiManager.WIFI_STATE_ENABLING -> WifiAdapterState.WIFI_TURNING_ON
            WifiManager.WIFI_STATE_DISABLING -> WifiAdapterState.WIFI_TURNING_OFF
            else -> {
                if (wifiManager?.isWifiEnabled == true) {
                    WifiAdapterState.WIFI_ON
                } else {
                    WifiAdapterState.WIFI_OFF
                }
            }
        }
    }

    fun registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
                context.registerReceiver(wifiStateReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(tag, "Failed to register wifiStateReceiver: ${e.message}")
            }
        }
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiStateReceiver)
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Opens native system Wi-Fi settings or Wi-Fi panel so user can turn Wi-Fi ON.
     * Android 10+ restricts third-party apps from toggling Wi-Fi directly, so we open the supported OS UI.
     */
    fun openWifiSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                return
            }
        } catch (_: Exception) {
            // Fallback to standard settings
        }

        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Could not open Wi-Fi settings: ${e.message}")
        }
    }
}

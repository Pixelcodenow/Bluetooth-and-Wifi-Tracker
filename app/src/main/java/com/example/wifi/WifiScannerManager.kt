package com.example.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.location.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class WifiScannerManager(
    private val context: Context,
    private val locationHelper: LocationHelper? = null
) {
    private val tag = "WifiScannerManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // Core managers
    val stateManager = WiFiStateManager(context)
    val permissionManager = WiFiPermissionManager(context)
    val connectionManager = WiFiConnectionManager(context, connectivityManager)

    // Observable states
    private val _networks = MutableStateFlow<List<WifiNetworkModel>>(emptyList())
    val networks: StateFlow<List<WifiNetworkModel>> = _networks.asStateFlow()

    private val _connectedNetwork = MutableStateFlow<ConnectedWifiInfo?>(null)
    val connectedNetwork: StateFlow<ConnectedWifiInfo?> = _connectedNetwork.asStateFlow()

    private val _scanStep = MutableStateFlow(WifiScanStep.IDLE)
    val scanStep: StateFlow<WifiScanStep> = _scanStep.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanErrorReason = MutableStateFlow<String?>(null)
    val scanErrorReason: StateFlow<String?> = _scanErrorReason.asStateFlow()

    private val _diagnostics = MutableStateFlow(WiFiDiagnosticsData())
    val diagnostics: StateFlow<WiFiDiagnosticsData> = _diagnostics.asStateFlow()

    // Cache of networks keyed by BSSID or (SSID + frequency)
    private val networkCache = ConcurrentHashMap<String, WifiNetworkModel>()

    // Scan timeout and scheduling jobs
    private var scanTimeoutJob: Job? = null
    private var scanStartTime = 0L
    private var isScanReceiverRegistered = false
    private var isNetworkCallbackRegistered = false

    // BroadcastReceiver for scan completion
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val resultsUpdated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                Log.d(tag, "SCAN_RESULTS_AVAILABLE_ACTION received (updated=$resultsUpdated)")
                handleScanResultsAvailable(resultsUpdated)
            }
        }
    }

    // Network callback for live connected link monitoring
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateConnectedNetworkInfo()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateConnectedNetworkInfo()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            updateConnectedNetworkInfo()
        }

        override fun onLost(network: Network) {
            _connectedNetwork.value = ConnectedWifiInfo(isConnected = false)
            // Mark all networks as not connected
            val current = _networks.value.map { if (it.isConnected) it.copy(isConnected = false) else it }
            _networks.value = current
        }
    }

    init {
        // Observe adapter state changes
        scope.launch {
            stateManager.adapterState.collect { state ->
                handleAdapterStateChange(state)
            }
        }

        registerReceivers()
        updateConnectedNetworkInfo()
    }

    private fun handleAdapterStateChange(state: WifiAdapterState) {
        Log.i(tag, "Adapter state observed: $state")
        _diagnostics.value = _diagnostics.value.copy(
            wifiAdapterState = state,
            isWifiEnabled = state == WifiAdapterState.WIFI_ON
        )

        when (state) {
            WifiAdapterState.WIFI_OFF, WifiAdapterState.WIFI_TURNING_OFF -> {
                // Point 2: WIFI OFF MUST STOP SCANNING IMMEDIATELY
                abortScan("Wi-Fi was disabled")
                // Clear active connected network
                _connectedNetwork.value = ConnectedWifiInfo(isConnected = false)
                // Clear network list so we do not show stale networks as if currently detected
                networkCache.clear()
                _networks.value = emptyList()
            }
            WifiAdapterState.WIFI_ON -> {
                _scanStep.value = WifiScanStep.IDLE
                _scanErrorReason.value = null
                updateConnectedNetworkInfo()
            }
            else -> {
                // Pending transitions
            }
        }
    }

    /**
     * Executes the full native Wi-Fi scan state machine.
     * Flow:
     * IDLE -> CHECKING_WIFI -> CHECKING_PERMISSIONS -> REQUESTING_SCAN -> WAITING_FOR_RESULTS -> PROCESSING_RESULTS -> SCAN_COMPLETE
     */
    fun startScan() {
        if (_isScanning.value) {
            Log.w(tag, "Scan already in progress; ignoring duplicate request")
            return
        }

        scope.launch {
            // 1. CHECKING_WIFI
            _scanStep.value = WifiScanStep.CHECKING_WIFI
            _scanErrorReason.value = null

            if (!stateManager.isWifiOn()) {
                _scanStep.value = WifiScanStep.SCAN_FAILED
                _scanErrorReason.value = "Wi-Fi is currently OFF"
                return@launch
            }

            // 2. CHECKING_PERMISSIONS
            _scanStep.value = WifiScanStep.CHECKING_PERMISSIONS
            val permStatus = permissionManager.checkScanPrerequisites()
            when (permStatus) {
                is WiFiPermissionStatus.MissingPermissions -> {
                    _scanStep.value = WifiScanStep.SCAN_FAILED
                    _scanErrorReason.value = permStatus.rationale
                    return@launch
                }
                is WiFiPermissionStatus.LocationServiceDisabled -> {
                    _scanStep.value = WifiScanStep.SCAN_FAILED
                    _scanErrorReason.value = "Location services are disabled on your phone. Android requires Location to scan nearby Wi-Fi."
                    return@launch
                }
                is WiFiPermissionStatus.Granted -> {
                    // Ready to proceed
                }
            }

            // 3. REQUESTING_SCAN
            _scanStep.value = WifiScanStep.REQUESTING_SCAN
            _isScanning.value = true
            scanStartTime = System.currentTimeMillis()

            val wm = wifiManager
            if (wm == null) {
                _isScanning.value = false
                _scanStep.value = WifiScanStep.SCAN_FAILED
                _scanErrorReason.value = "Wi-Fi hardware manager is not available"
                return@launch
            }

            // Start 10-second timeout watchdog (Point 10)
            startScanTimeoutWatchdog()

            _diagnostics.value = _diagnostics.value.copy(
                scanRequested = true,
                scanCallbackReceived = false,
                scannerInitialized = true
            )

            try {
                @Suppress("DEPRECATION")
                val scanSuccess = wm.startScan()
                if (scanSuccess) {
                    _scanStep.value = WifiScanStep.WAITING_FOR_RESULTS
                    Log.i(tag, "Native startScan() returned true; awaiting broadcast...")
                } else {
                    // startScan() may return false if throttled by Android (4 scans per 2 minutes)
                    Log.w(tag, "Native startScan() returned false (likely throttled). Reading recent scan cache...")
                    _diagnostics.value = _diagnostics.value.copy(isThrottled = true)
                    // Process available cached results
                    delay(600)
                    handleScanResultsAvailable(resultsUpdated = false)
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during startScan: ${e.message}")
                abortScan("Scan request error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun startScanTimeoutWatchdog() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(10_000L) // 10 seconds maximum scan wait
            if (_isScanning.value) {
                Log.w(tag, "Wi-Fi scan timed out after 10 seconds")
                scanTimeoutJob = null
                _isScanning.value = false
                _scanStep.value = WifiScanStep.SCAN_TIMEOUT
                _scanErrorReason.value = "Scan timed out after 10 seconds. The operating system did not return fresh results."
            }
        }
    }

    private fun handleScanResultsAvailable(resultsUpdated: Boolean) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        if (!_isScanning.value && _networks.value.isNotEmpty()) {
            // System background scan broadcast received while not actively scanning; refresh list passively
            processScanResults(resultsUpdated)
            return
        }

        _scanStep.value = WifiScanStep.PROCESSING_RESULTS
        processScanResults(resultsUpdated)
    }

    private fun processScanResults(resultsUpdated: Boolean) {
        val wm = wifiManager ?: return
        val durationSec = ((System.currentTimeMillis() - scanStartTime) / 100.0) / 10.0

        try {
            @SuppressLint("MissingPermission")
            val rawResults: List<ScanResult> = wm.scanResults ?: emptyList()
            val connectedBssid = getConnectedBssid()
            val phoneLocation = locationHelper?.getLastLocation()

            Log.i(tag, "Raw scan results count: ${rawResults.size}, updated=$resultsUpdated, connectedBssid=$connectedBssid")

            // Track IDs detected in this specific scan
            val currentScanKeys = mutableSetOf<String>()

            for (res in rawResults) {
                val bssid = res.BSSID ?: ""
                @Suppress("DEPRECATION")
                val ssidRaw = res.SSID ?: ""
                val cleanSsid = WiFiResultParser.cleanSsid(ssidRaw)

                // Key by BSSID to keep distinct access points with same SSID separate
                val key = if (bssid.isNotBlank()) bssid else "${cleanSsid}_${res.frequency}"
                currentScanKeys.add(key)

                val existing = networkCache[key]
                val firstSeen = existing?.firstSeenTimestamp ?: System.currentTimeMillis()
                val customName = existing?.customName ?: ""
                val isFav = existing?.isFavorite ?: false

                // RSSI smoothing
                val rawRssi = res.level
                val previousSmoothed = existing?.smoothedRssi ?: rawRssi
                val smoothed = WiFiResultParser.let {
                    WiFiSignalProcessor.smoothRssi(rawRssi, previousSmoothed)
                }

                // Append to history
                val updatedHist = (existing?.rssiHistory ?: emptyList()) + rawRssi
                val trimmedHist = if (updatedHist.size > 20) updatedHist.takeLast(20) else updatedHist

                val parsedModel = WiFiResultParser.parseScanResult(
                    result = res,
                    connectedBssid = connectedBssid,
                    smoothedRssi = smoothed,
                    firstSeen = firstSeen,
                    isFresh = resultsUpdated,
                    lifecycle = WifiLifecycleState.ACTIVE,
                    history = trimmedHist,
                    lat = phoneLocation?.latitude ?: existing?.latitude,
                    lng = phoneLocation?.longitude ?: existing?.longitude,
                    customName = customName,
                    isFavorite = isFav
                )

                networkCache[key] = parsedModel
            }

            // Lifecycle aging for networks NOT in the current scan:
            // ACTIVE -> RECENTLY_SEEN -> OUT_OF_RANGE -> LOST
            val now = System.currentTimeMillis()
            networkCache.forEach { (key, net) ->
                if (!currentScanKeys.contains(key)) {
                    val ageMs = now - net.lastSeenTimestamp
                    val updatedLifecycle = when {
                        ageMs < 12_000L -> WifiLifecycleState.RECENTLY_SEEN
                        ageMs < 30_000L -> WifiLifecycleState.OUT_OF_RANGE
                        else -> WifiLifecycleState.LOST
                    }
                    networkCache[key] = net.copy(
                        lifecycleState = updatedLifecycle,
                        isFreshResult = false
                    )
                }
            }

            // Prune networks lost for more than 2 minutes
            val iterator = networkCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeenTimestamp > 120_000L) {
                    iterator.remove()
                }
            }

            val list = networkCache.values.toList()
            _networks.value = list
            _isScanning.value = false
            _scanStep.value = WifiScanStep.SCAN_COMPLETE

            // Update diagnostics
            val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
            _diagnostics.value = _diagnostics.value.copy(
                scanCallbackReceived = true,
                resultCount = list.size,
                freshResultCount = currentScanKeys.size,
                lastScanTimeFormatted = timeFmt,
                scanDurationSeconds = durationSec,
                lastScanSuccess = true,
                lastFailureReason = null
            )

        } catch (e: Exception) {
            Log.e(tag, "Error processing scan results: ${e.message}")
            _isScanning.value = false
            _scanStep.value = WifiScanStep.SCAN_FAILED
            _scanErrorReason.value = "Failed to parse scan results: ${e.localizedMessage}"
            _diagnostics.value = _diagnostics.value.copy(
                lastScanSuccess = false,
                lastFailureReason = e.message
            )
        }
    }

    fun abortScan(reason: String) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        _isScanning.value = false
        _scanStep.value = WifiScanStep.SCAN_FAILED
        _scanErrorReason.value = reason
        _diagnostics.value = _diagnostics.value.copy(
            scanRequested = false,
            lastScanSuccess = false,
            lastFailureReason = reason
        )
    }

    /**
     * Reads current connected Wi-Fi link parameters from the OS.
     */
    fun updateConnectedNetworkInfo() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!hasWifi || !stateManager.isWifiOn()) {
            _connectedNetwork.value = ConnectedWifiInfo(isConnected = false)
            return
        }

        try {
            val wm = wifiManager
            @Suppress("DEPRECATION")
            val connectionInfo: WifiInfo? = wm?.connectionInfo

            if (connectionInfo == null || connectionInfo.networkId == -1) {
                _connectedNetwork.value = ConnectedWifiInfo(isConnected = false)
                return
            }

            val rawSsid = connectionInfo.ssid ?: ""
            val cleanSsid = WiFiResultParser.cleanSsid(rawSsid)
            val bssid = connectionInfo.bssid ?: ""
            val rssi = connectionInfo.rssi
            val linkSpeed = connectionInfo.linkSpeed
            val frequency = connectionInfo.frequency
            val band = WiFiResultParser.determineBand(frequency)
            val channel = WiFiResultParser.calculateChannel(frequency)

            // Extract IPv4 address
            var ipStr = ""
            var gatewayStr = ""
            val linkProps = cm.getLinkProperties(activeNetwork)
            linkProps?.linkAddresses?.forEach { addr ->
                if (addr.address is Inet4Address) {
                    ipStr = addr.address.hostAddress ?: ""
                }
            }
            linkProps?.routes?.forEach { route ->
                if (route.isDefaultRoute && route.gateway is Inet4Address) {
                    gatewayStr = route.gateway?.hostAddress ?: ""
                }
            }

            var wifiStandard = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifiStandard = when (connectionInfo.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
                    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                    else -> if (frequency > 4900) "Wi-Fi 5" else "Wi-Fi 4"
                }
            }

            val prevSmoothed = _connectedNetwork.value?.smoothedRssi ?: rssi
            val smoothed = WiFiSignalProcessor.smoothRssi(rssi, prevSmoothed)

            _connectedNetwork.value = ConnectedWifiInfo(
                isConnected = cleanSsid.isNotBlank() && cleanSsid != "<unknown ssid>",
                ssid = cleanSsid,
                bssid = bssid,
                rssi = rssi,
                smoothedRssi = smoothed,
                linkSpeedMbps = linkSpeed,
                frequency = frequency,
                band = band,
                channel = channel,
                ipAddress = ipStr,
                gateway = gatewayStr,
                wifiStandard = wifiStandard
            )

            // Mark matched network in networkCache as connected
            if (bssid.isNotBlank()) {
                networkCache[bssid]?.let {
                    networkCache[bssid] = it.copy(isConnected = true)
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Error reading connection info: ${e.message}")
        }
    }

    private fun getConnectedBssid(): String? {
        val wm = wifiManager ?: return null
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo ?: return null
        val bssid = info.bssid
        return if (!bssid.isNullOrBlank() && bssid != "02:00:00:00:00:00") bssid else null
    }

    fun renameNetwork(bssid: String, customName: String) {
        networkCache[bssid]?.let {
            networkCache[bssid] = it.copy(customName = customName)
            _networks.value = networkCache.values.toList()
        }
    }

    fun toggleFavorite(bssid: String) {
        networkCache[bssid]?.let {
            val updated = it.copy(isFavorite = !it.isFavorite)
            networkCache[bssid] = updated
            _networks.value = networkCache.values.toList()
        }
    }

    private fun registerReceivers() {
        if (!isScanReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                }
                context.registerReceiver(wifiScanReceiver, filter)
                isScanReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(tag, "Error registering scan receiver: ${e.message}")
            }
        }

        if (!isNetworkCallbackRegistered) {
            try {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
                isNetworkCallbackRegistered = true
            } catch (e: Exception) {
                Log.e(tag, "Error registering network callback: ${e.message}")
            }
        }
    }

    fun cleanup() {
        scanTimeoutJob?.cancel()
        stateManager.unregisterReceiver()
        connectionManager.cancelActiveConnection()

        if (isScanReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
            } catch (_: Exception) {}
            isScanReceiverRegistered = false
        }

        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
            isNetworkCallbackRegistered = false
        }
    }
}

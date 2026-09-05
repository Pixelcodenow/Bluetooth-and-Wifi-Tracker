package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BleTrackerApp
import com.example.data.entity.SavedWifiEntity
import com.example.data.entity.WifiScanEventEntity
import com.example.data.repository.WifiRepository
import com.example.wifi.ConnectedWifiInfo
import com.example.wifi.WiFiDiagnosticsData
import com.example.wifi.WiFiPermissionStatus
import com.example.wifi.WiFiSignalProcessor
import com.example.wifi.WifiAdapterState
import com.example.wifi.WifiConnectionStep
import com.example.wifi.WifiNetworkModel
import com.example.wifi.WifiScannerManager
import com.example.wifi.WifiScanStep
import com.example.wifi.WifiSecurityType
import com.example.wifi.WifiSignalLevel
import com.example.wifi.WifiSortType
import com.example.wifi.WifiUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WifiFilter(val label: String) {
    ALL("All"),
    CONNECTED("Connected"),
    NOT_CONNECTED("Not Connected"),
    BAND_2_4("2.4 GHz"),
    BAND_5("5 GHz"),
    BAND_6("6 GHz"),
    OPEN("Open"),
    WPA2("WPA2"),
    WPA3("WPA3"),
    STRONG("Strong"),
    MEDIUM("Medium"),
    WEAK("Weak")
}

data class WifiScanStats(
    val totalCount: Int = 0,
    val connectedCount: Int = 0,
    val band24Count: Int = 0,
    val band5Count: Int = 0,
    val band6Count: Int = 0,
    val secureCount: Int = 0,
    val openCount: Int = 0
)

class WifiViewModel(
    private val app: BleTrackerApp,
    val scannerManager: WifiScannerManager,
    private val repository: WifiRepository
) : ViewModel() {

    val adapterState: StateFlow<WifiAdapterState> = scannerManager.stateManager.adapterState
    val isScanning: StateFlow<Boolean> = scannerManager.isScanning
    val scanStep: StateFlow<WifiScanStep> = scannerManager.scanStep
    val scanErrorReason: StateFlow<String?> = scannerManager.scanErrorReason
    val connectedNetwork: StateFlow<ConnectedWifiInfo?> = scannerManager.connectedNetwork
    val diagnostics: StateFlow<WiFiDiagnosticsData> = scannerManager.diagnostics
    val connectionStep: StateFlow<WifiConnectionStep> = scannerManager.connectionManager.connectionStep
    val connectingSsid: StateFlow<String?> = scannerManager.connectionManager.connectingSsid

    private val _targetNetworkId = MutableStateFlow<String?>(null)
    val targetNetworkId: StateFlow<String?> = _targetNetworkId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(WifiFilter.ALL)
    val selectedFilter: StateFlow<WifiFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(WifiSortType.STRONGEST_SIGNAL)
    val selectedSort: StateFlow<WifiSortType> = _selectedSort.asStateFlow()

    private val _comparisonIds = MutableStateFlow<Set<String>>(emptySet())
    val comparisonIds: StateFlow<Set<String>> = _comparisonIds.asStateFlow()

    val savedNetworks: StateFlow<List<SavedWifiEntity>> = repository.allSavedNetworks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanHistory: StateFlow<List<WifiScanEventEntity>> = repository.recentScanEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // High level UI State Machine
    val uiState: StateFlow<WifiUiState> = combine(
        adapterState,
        isScanning,
        scanStep,
        scannerManager.networks,
        connectionStep
    ) { adapter, scanning, step, networks, connStep ->
        when {
            adapter == WifiAdapterState.WIFI_OFF || adapter == WifiAdapterState.WIFI_TURNING_OFF -> {
                WifiUiState.WIFI_OFF
            }
            connStep == WifiConnectionStep.CONNECTING -> {
                WifiUiState.CONNECTING
            }
            connStep == WifiConnectionStep.CONNECTED -> {
                WifiUiState.CONNECTED
            }
            connStep == WifiConnectionStep.CONNECTION_FAILED || connStep == WifiConnectionStep.WRONG_PASSWORD -> {
                WifiUiState.CONNECTION_FAILED
            }
            connStep == WifiConnectionStep.OS_RESTRICTED -> {
                WifiUiState.OS_RESTRICTED
            }
            step == WifiScanStep.SCAN_TIMEOUT -> {
                WifiUiState.SCAN_TIMEOUT
            }
            step == WifiScanStep.SCAN_FAILED -> {
                val permStatus = scannerManager.permissionManager.checkScanPrerequisites()
                when (permStatus) {
                    is WiFiPermissionStatus.MissingPermissions -> WifiUiState.PERMISSION_REQUIRED
                    is WiFiPermissionStatus.LocationServiceDisabled -> WifiUiState.LOCATION_REQUIRED
                    else -> WifiUiState.SCAN_FAILED
                }
            }
            scanning -> {
                WifiUiState.SCANNING
            }
            networks.isNotEmpty() -> {
                WifiUiState.RESULTS
            }
            step == WifiScanStep.SCAN_COMPLETE && networks.isEmpty() -> {
                WifiUiState.NO_RESULTS
            }
            else -> {
                val permStatus = scannerManager.permissionManager.checkScanPrerequisites()
                when (permStatus) {
                    is WiFiPermissionStatus.MissingPermissions -> WifiUiState.PERMISSION_REQUIRED
                    is WiFiPermissionStatus.LocationServiceDisabled -> WifiUiState.LOCATION_REQUIRED
                    else -> WifiUiState.READY
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WifiUiState.INITIALIZING)

    val filteredNetworks: StateFlow<List<WifiNetworkModel>> = combine(
        scannerManager.networks,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { networks, query, filter, sort ->
        var list = networks

        // Search
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.displayName.lowercase().contains(q) ||
                it.ssid.lowercase().contains(q) ||
                it.bssid.lowercase().contains(q) ||
                it.securityType.label.lowercase().contains(q) ||
                it.band.lowercase().contains(q)
            }
        }

        // Filter
        list = when (filter) {
            WifiFilter.ALL -> list
            WifiFilter.CONNECTED -> list.filter { it.isConnected }
            WifiFilter.NOT_CONNECTED -> list.filter { !it.isConnected }
            WifiFilter.BAND_2_4 -> list.filter { it.band.contains("2.4") }
            WifiFilter.BAND_5 -> list.filter { it.band.contains("5 GHz") }
            WifiFilter.BAND_6 -> list.filter { it.band.contains("6 GHz") }
            WifiFilter.OPEN -> list.filter { it.securityType == WifiSecurityType.OPEN }
            WifiFilter.WPA2 -> list.filter {
                it.securityType == WifiSecurityType.WPA2 ||
                it.securityType == WifiSecurityType.WPA_WPA2 ||
                it.securityType == WifiSecurityType.WPA2_WPA3
            }
            WifiFilter.WPA3 -> list.filter {
                it.securityType == WifiSecurityType.WPA3 ||
                it.securityType == WifiSecurityType.WPA2_WPA3
            }
            WifiFilter.STRONG -> list.filter {
                it.signalLevel == WifiSignalLevel.EXCELLENT || it.signalLevel == WifiSignalLevel.STRONG
            }
            WifiFilter.MEDIUM -> list.filter { it.signalLevel == WifiSignalLevel.GOOD || it.signalLevel == WifiSignalLevel.FAIR }
            WifiFilter.WEAK -> list.filter { it.signalLevel == WifiSignalLevel.WEAK || it.signalLevel == WifiSignalLevel.VERY_WEAK }
        }

        // Stable Sort
        WiFiSignalProcessor.sortNetworks(list, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val targetNetwork: StateFlow<WifiNetworkModel?> = combine(
        scannerManager.networks,
        _targetNetworkId
    ) { networks, targetId ->
        if (targetId == null) null
        else networks.firstOrNull { it.id == targetId || it.bssid == targetId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<WifiScanStats> = scannerManager.networks.combine(_searchQuery) { networks, _ ->
        WifiScanStats(
            totalCount = networks.size,
            connectedCount = networks.count { it.isConnected },
            band24Count = networks.count { it.band.contains("2.4") },
            band5Count = networks.count { it.band.contains("5 GHz") },
            band6Count = networks.count { it.band.contains("6 GHz") },
            secureCount = networks.count { it.securityType.isSecure },
            openCount = networks.count { it.securityType == WifiSecurityType.OPEN }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WifiScanStats())

    fun startScan() {
        scannerManager.startScan()
    }

    fun openWifiSettings() {
        scannerManager.stateManager.openWifiSettings()
    }

    fun openLocationSettings() {
        scannerManager.permissionManager.openLocationSettings()
    }

    fun openAppSettings() {
        scannerManager.permissionManager.openAppSettings()
    }

    fun connectToNetwork(network: WifiNetworkModel, passphrase: String? = null) {
        scannerManager.connectionManager.connectToNetwork(network, passphrase)
    }

    fun cancelConnection() {
        scannerManager.connectionManager.cancelActiveConnection()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: WifiFilter) {
        _selectedFilter.value = filter
    }

    fun setSort(sort: WifiSortType) {
        _selectedSort.value = sort
    }

    fun setTargetNetwork(id: String?) {
        _targetNetworkId.value = id
    }

    fun toggleFavorite(network: WifiNetworkModel) {
        viewModelScope.launch {
            scannerManager.toggleFavorite(network.bssid)
            val isFav = !network.isFavorite
            repository.saveNetwork(network.copy(isFavorite = isFav), isFavorite = isFav)
        }
    }

    fun renameNetwork(bssid: String, customName: String) {
        viewModelScope.launch {
            scannerManager.renameNetwork(bssid, customName)
            repository.updateCustomName(bssid, customName, true)
        }
    }

    fun deleteSaved(bssid: String) {
        viewModelScope.launch {
            repository.deleteSavedNetwork(bssid)
        }
    }

    fun recordHistory(network: WifiNetworkModel) {
        viewModelScope.launch {
            repository.recordScanEvent(network)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun toggleComparison(networkId: String) {
        val current = _comparisonIds.value.toMutableSet()
        if (current.contains(networkId)) {
            current.remove(networkId)
        } else {
            if (current.size < 2) {
                current.add(networkId)
            }
        }
        _comparisonIds.value = current
    }

    fun clearComparison() {
        _comparisonIds.value = emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        scannerManager.cleanup()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = BleTrackerApp.instance
                return WifiViewModel(
                    app = app,
                    scannerManager = app.wifiScannerManager,
                    repository = app.wifiRepository
                ) as T
            }
        }
    }
}

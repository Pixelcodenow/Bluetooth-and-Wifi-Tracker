package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BleTrackerApp
import com.example.alarm.FindPhoneAlarmManager
import com.example.ble.BleEvent
import com.example.ble.BleManager
import com.example.ble.BluetoothDeviceType
import com.example.ble.ConnectionState
import com.example.ble.DeviceConnectionStatus
import com.example.ble.DeviceLifecycleState
import com.example.ble.DiscoveredBleDevice
import com.example.ble.DiscoveredBluetoothDevice
import com.example.ble.SignalTrend
import com.example.ble.TrackerLiveStatus
import com.example.data.entity.TrackerEntity
import com.example.data.entity.TrackerEventEntity
import com.example.data.entity.UnknownTrackerEntity
import com.example.data.repository.TrackerRepository
import com.example.location.LocationHelper
import com.example.service.TrackerBackgroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeviceFilter(val label: String) {
    ALL("All"),
    CONNECTED("Connected"),
    PAIRED("Paired"),
    NEARBY("Nearby"),
    LOST("Lost"),
    BLE("BLE"),
    UNKNOWN("Unknown")
}

enum class DeviceSort(val label: String) {
    SIGNAL_STRENGTH("Strongest Signal"),
    NAME("Name (A-Z)"),
    LAST_DETECTED("Recently Detected"),
    STATUS("Status")
}

data class DeviceScanStats(
    val totalCount: Int = 0,
    val connectedCount: Int = 0,
    val pairedCount: Int = 0,
    val nearbyCount: Int = 0,
    val lostCount: Int = 0,
    val unknownCount: Int = 0
)

class TrackerViewModel(
    private val app: BleTrackerApp,
    private val repository: TrackerRepository,
    private val bleManager: BleManager,
    private val alarmManager: FindPhoneAlarmManager,
    private val locationHelper: LocationHelper
) : ViewModel() {

    // Trackers saved in Room Database
    val trackers: StateFlow<List<TrackerEntity>> = repository.allTrackers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All real-time discovered Bluetooth devices from scanner
    val allDiscoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = bleManager.allDiscoveredDevices
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val trackerStatuses: StateFlow<Map<String, TrackerLiveStatus>> = bleManager.trackerStatuses

    // Backwards-compatible flow for AddTrackerSheet
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = bleManager.discoveredDevices

    // Alarm state
    val isAlarmActive: StateFlow<Boolean> = alarmManager.isAlarmActive
    val alarmSource: StateFlow<String?> = alarmManager.triggerSource

    // Historical events & unknown trackers
    val trackerEvents: StateFlow<List<TrackerEventEntity>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unknownTrackers: StateFlow<List<UnknownTrackerEntity>> = repository.unknownTrackers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Target Finding States
    val lockedTargetMac: StateFlow<String?> = bleManager.lockedTargetMac
    val lockedTargetDevice: StateFlow<DiscoveredBluetoothDevice?> = bleManager.lockedTargetDevice
    val lockedTargetTrend: StateFlow<SignalTrend> = bleManager.lockedTargetTrend

    // Search and Filter States for Nearby Devices Scanner
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(DeviceFilter.ALL)
    val activeFilter: StateFlow<DeviceFilter> = _activeFilter.asStateFlow()

    private val _activeSort = MutableStateFlow(DeviceSort.SIGNAL_STRENGTH)
    val activeSort: StateFlow<DeviceSort> = _activeSort.asStateFlow()

    // Aggregate statistics for the scanner header summary
    val deviceStats: StateFlow<DeviceScanStats> = allDiscoveredDevices.combine(trackers) { devices, _ ->
        val total = devices.size
        val connected = devices.count { it.connectionStatus == DeviceConnectionStatus.CONNECTED }
        val paired = devices.count { it.connectionStatus == DeviceConnectionStatus.PAIRED }
        val nearby = devices.count { it.connectionStatus == DeviceConnectionStatus.NEARBY && it.lifecycleState != DeviceLifecycleState.LOST }
        val lost = devices.count { it.lifecycleState == DeviceLifecycleState.LOST }
        val unknown = devices.count { !it.isNamed }
        DeviceScanStats(total, connected, paired, nearby, lost, unknown)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceScanStats())

    // Filtered & Sorted Devices List with Hysteresis Stability
    val filteredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = combine(
        allDiscoveredDevices,
        _searchQuery,
        _activeFilter,
        _activeSort
    ) { devices, query, filter, sort ->
        // 1. Search Query Filter
        var list = if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            devices.filter {
                it.name.lowercase().contains(q) ||
                        it.address.lowercase().contains(q) ||
                        it.displayName.lowercase().contains(q) ||
                        it.manufacturerName?.lowercase()?.contains(q) == true
            }
        } else {
            devices
        }

        // 2. Category Filter
        list = when (filter) {
            DeviceFilter.ALL -> list
            DeviceFilter.CONNECTED -> list.filter { it.connectionStatus == DeviceConnectionStatus.CONNECTED }
            DeviceFilter.PAIRED -> list.filter { it.connectionStatus == DeviceConnectionStatus.PAIRED }
            DeviceFilter.NEARBY -> list.filter { it.connectionStatus == DeviceConnectionStatus.NEARBY && it.lifecycleState != DeviceLifecycleState.LOST }
            DeviceFilter.LOST -> list.filter { it.lifecycleState == DeviceLifecycleState.LOST }
            DeviceFilter.BLE -> list.filter { it.deviceType == BluetoothDeviceType.BLE }
            DeviceFilter.UNKNOWN -> list.filter { !it.isNamed }
        }

        // 3. Stable Sorting with Hysteresis
        when (sort) {
            DeviceSort.SIGNAL_STRENGTH -> list.sortedWith(
                compareByDescending<DiscoveredBluetoothDevice> { dev ->
                    // Connected/Paired first, then signal strength with 4 dBm hysteresis buckets
                    // to prevent continuous jumping
                    val effectiveRssi = if (dev.smoothedRssi != 0) dev.smoothedRssi else dev.rssi
                    if (dev.lifecycleState == DeviceLifecycleState.LOST) {
                        -999
                    } else {
                        // Rounding to nearest 4 dBm prevents 1-2 dBm jitter from reordering the list
                        (effectiveRssi / 4) * 4
                    }
                }.thenBy { it.displayName }
            )
            DeviceSort.NAME -> list.sortedBy { it.displayName.lowercase() }
            DeviceSort.LAST_DETECTED -> list.sortedByDescending { it.lastSeenTimestamp }
            DeviceSort.STATUS -> list.sortedWith(
                compareBy<DiscoveredBluetoothDevice> {
                    when (it.connectionStatus) {
                        DeviceConnectionStatus.CONNECTED -> 0
                        DeviceConnectionStatus.PAIRED -> 1
                        DeviceConnectionStatus.NEARBY -> 2
                        DeviceConnectionStatus.UNKNOWN -> 3
                    }
                }.thenByDescending { if (it.smoothedRssi != 0) it.smoothedRssi else it.rssi }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected device for Details Screen
    private val _selectedDeviceMac = MutableStateFlow<String?>(null)
    val selectedDeviceForDetails: StateFlow<DiscoveredBluetoothDevice?> = combine(
        allDiscoveredDevices,
        _selectedDeviceMac
    ) { devices, mac ->
        if (mac != null) devices.find { it.address == mac } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selected Tracker for Tracker / Map views
    private val _selectedTrackerId = MutableStateFlow<Long?>(null)
    val selectedTrackerId: StateFlow<Long?> = _selectedTrackerId.asStateFlow()

    val selectedTracker: StateFlow<TrackerEntity?> = combine(trackers, _selectedTrackerId) { list, id ->
        if (id != null) list.find { it.id == id } else list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Start background service to maintain persistent BLE connection
        try {
            TrackerBackgroundService.start(app)
        } catch (e: Exception) {
            // Service start exception handled
        }

        // Sync user tracker MAC addresses to BleManager
        viewModelScope.launch {
            trackers.collect { list ->
                val macs = list.map { it.macAddress }.toSet()
                bleManager.setUserTrackerMacs(macs)

                list.forEach { tracker ->
                    if (tracker.isAutoReconnect) {
                        val status = trackerStatuses.value[tracker.macAddress]
                        if (status == null || status.connectionState == ConnectionState.DISCONNECTED) {
                            bleManager.connect(tracker.macAddress)
                        }
                    }
                }
            }
        }

        // Unknown beacon observation for anti-stalking
        viewModelScope.launch {
            allDiscoveredDevices.collect { devices ->
                devices.forEach { device ->
                    repository.recordUnknownBeacon(device.address, device.displayName, device.rssi)
                }
            }
        }
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: DeviceFilter) {
        _activeFilter.value = filter
    }

    fun setSort(sort: DeviceSort) {
        _activeSort.value = sort
    }

    fun openDeviceDetails(macAddress: String) {
        _selectedDeviceMac.value = macAddress
    }

    fun closeDeviceDetails() {
        _selectedDeviceMac.value = null
    }

    // Target Finding Methods
    fun startFindingDevice(macAddress: String) {
        bleManager.lockTarget(macAddress)
    }

    fun stopFindingDevice() {
        bleManager.unlockTarget()
    }

    fun viewDeviceLocationOnMap(device: DiscoveredBluetoothDevice) {
        viewModelScope.launch {
            val existing = repository.getTrackerByMac(device.address)
            if (existing != null) {
                _selectedTrackerId.value = existing.id
            } else {
                val loc = locationHelper.getLastKnownPhoneLocation()
                val entity = TrackerEntity(
                    macAddress = device.address,
                    name = device.name.ifBlank { device.displayName },
                    customName = device.displayName,
                    colorArgb = 0xFF00C9FF,
                    iconType = "OTHER",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    lastLatitude = device.lastKnownLatitude ?: loc?.latitude,
                    lastLongitude = device.lastKnownLongitude ?: loc?.longitude,
                    lastLocationLabel = device.lastKnownLocationLabel ?: loc?.addressLabel,
                    signalRssi = if (device.smoothedRssi != 0) device.smoothedRssi else device.rssi,
                    isAutoReconnect = false
                )
                val newId = repository.insertTracker(entity)
                _selectedTrackerId.value = newId
            }
        }
    }

    fun connectDevice(macAddress: String) {
        bleManager.connect(macAddress)
    }

    fun disconnectDevice(macAddress: String) {
        bleManager.disconnect(macAddress)
    }

    fun toggleTrackerStatus(device: DiscoveredBluetoothDevice) {
        viewModelScope.launch {
            val existing = repository.getTrackerByMac(device.address)
            if (existing != null) {
                repository.deleteTracker(existing.id, existing.macAddress)
            } else {
                val loc = locationHelper.getLastKnownPhoneLocation()
                val entity = TrackerEntity(
                    macAddress = device.address,
                    name = device.name.ifBlank { device.displayName },
                    customName = device.displayName,
                    colorArgb = 0xFF00C9FF,
                    iconType = "OTHER",
                    lastSeenTimestamp = System.currentTimeMillis(),
                    lastLatitude = loc?.latitude,
                    lastLongitude = loc?.longitude,
                    lastLocationLabel = loc?.addressLabel,
                    signalRssi = if (device.smoothedRssi != 0) device.smoothedRssi else device.rssi,
                    isAutoReconnect = true
                )
                repository.insertTracker(entity)
            }
        }
    }

    fun selectTracker(id: Long) {
        _selectedTrackerId.value = id
    }

    fun pairDevice(device: DiscoveredBleDevice, customName: String, icon: String, color: Long) {
        viewModelScope.launch {
            val loc = locationHelper.getLastKnownPhoneLocation()
            val entity = TrackerEntity(
                macAddress = device.address,
                name = device.name.ifBlank { "BLE Tracker" },
                customName = customName,
                colorArgb = color,
                iconType = icon,
                lastSeenTimestamp = System.currentTimeMillis(),
                lastLatitude = loc?.latitude,
                lastLongitude = loc?.longitude,
                lastLocationLabel = loc?.addressLabel,
                signalRssi = device.rssi,
                isAutoReconnect = true
            )
            val newId = repository.insertTracker(entity)
            _selectedTrackerId.value = newId

            repository.logEvent(
                mac = device.address,
                type = "CONNECTED",
                details = "Paired new Bluetooth device \"${entity.displayName}\"",
                lat = loc?.latitude,
                lng = loc?.longitude
            )

            bleManager.connect(device.address)
        }
    }

    fun connectTracker(mac: String) {
        bleManager.connect(mac)
    }

    fun disconnectTracker(mac: String) {
        bleManager.disconnect(mac)
    }

    fun toggleBuzzer(mac: String) {
        val currentStatus = trackerStatuses.value[mac]
        val willBeep = !(currentStatus?.isBeeping ?: false)
        bleManager.setBuzzerState(mac, willBeep)

        viewModelScope.launch {
            repository.logEvent(
                mac = mac,
                type = "BEEP_COMMAND",
                details = if (willBeep) "Started tracker sound buzzer" else "Silenced tracker sound buzzer"
            )
        }
    }

    fun stopPhoneAlarm() {
        alarmManager.stopAlarm()
        app.notificationManager.dismissFindPhoneNotification()
    }

    fun triggerFindPhoneTest(mac: String = "TRACKER:01") {
        bleManager.triggerSimulatedButtonPress(mac)
    }

    fun updateCustomization(id: Long, name: String, color: Long, icon: String) {
        viewModelScope.launch {
            repository.updateCustomization(id, name, color, icon)
        }
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, current)
        }
    }

    fun deleteTracker(tracker: TrackerEntity) {
        viewModelScope.launch {
            bleManager.disconnect(tracker.macAddress)
            repository.deleteTracker(tracker.id, tracker.macAddress)
            if (_selectedTrackerId.value == tracker.id) {
                _selectedTrackerId.value = null
            }
        }
    }

    fun clearLocationHistory() {
        viewModelScope.launch {
            repository.clearAllLocationData()
        }
    }

    fun clearEventLogs() {
        viewModelScope.launch {
            repository.clearHistoryLogs()
        }
    }

    fun dismissUnknownBeacon(mac: String) {
        viewModelScope.launch {
            repository.dismissUnknownAlert(mac)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = BleTrackerApp.instance
                return TrackerViewModel(
                    app = app,
                    repository = app.repository,
                    bleManager = app.bleManager,
                    alarmManager = app.alarmManager,
                    locationHelper = app.locationHelper
                ) as T
            }
        }
    }
}

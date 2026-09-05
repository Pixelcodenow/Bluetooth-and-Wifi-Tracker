package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BleTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed class BleEvent {
    data class DeviceConnected(val mac: String) : BleEvent()
    data class DeviceDisconnected(val mac: String) : BleEvent()
    data class ButtonPressed(val mac: String, val clickType: Byte) : BleEvent()
    data class BatteryUpdated(val mac: String, val level: Int) : BleEvent()
    data class RssiUpdated(val mac: String, val rssi: Int) : BleEvent()
    data class BeepStatusChanged(val mac: String, val isBeeping: Boolean) : BleEvent()
    data class ConnectionFailed(val mac: String, val error: String) : BleEvent()
    data class GattServicesDiscovered(val mac: String, val services: List<GattServiceInfo>) : BleEvent()
}

class BleManager(private val context: Context) {

    private val tag = "BleManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val knownDeviceCache = KnownDeviceCache(context)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleScanner: BluetoothLeScanner? = null

    // Real-time discovered Bluetooth devices map (MAC -> DiscoveredBluetoothDevice)
    private val _devicesMap = MutableStateFlow<Map<String, DiscoveredBluetoothDevice>>(emptyMap())
    val allDiscoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> =
        MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList()).also { stateFlow ->
            coroutineScope.launch {
                _devicesMap.collect { map ->
                    stateFlow.value = map.values.toList()
                }
            }
        }

    // Backwards-compatible legacy discovered list for pairing sheets
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Live tracker runtime statuses (isBeeping, battery, connection state, live RSSI)
    private val _trackerStatuses = MutableStateFlow<Map<String, TrackerLiveStatus>>(emptyMap())
    val trackerStatuses: StateFlow<Map<String, TrackerLiveStatus>> = _trackerStatuses.asStateFlow()

    // Active GATT connections: MAC -> BluetoothGatt
    private val activeGatts = mutableMapOf<String, BluetoothGatt>()

    // Global BLE Event Bus
    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleEvent> = _events.asSharedFlow()

    // Set of MAC addresses marked as user trackers in DB
    private val userTrackerMacs = mutableSetOf<String>()

    // Target Device Lock state (for finding non-connected devices)
    private val _lockedTargetMac = MutableStateFlow<String?>(null)
    val lockedTargetMac: StateFlow<String?> = _lockedTargetMac.asStateFlow()

    private val _lockedTargetTrend = MutableStateFlow(SignalTrend.STABLE)
    val lockedTargetTrend: StateFlow<SignalTrend> = _lockedTargetTrend.asStateFlow()

    val lockedTargetDevice: StateFlow<DiscoveredBluetoothDevice?> = combine(
        allDiscoveredDevices,
        _lockedTargetMac
    ) { devices, targetMac ->
        if (targetMac != null) devices.find { it.address == targetMac } else null
    }.let { flow ->
        val state = MutableStateFlow<DiscoveredBluetoothDevice?>(null)
        coroutineScope.launch {
            flow.collect { state.value = it }
        }
        state.asStateFlow()
    }

    private var rssiPollingJob: Job? = null
    private var lifecycleMonitorJob: Job? = null

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val stopScanRunnable = Runnable {
        if (_lockedTargetMac.value == null) {
            stopScan()
        }
    }

    init {
        startRssiPollingLoop()
        startLifecycleMonitorLoop()
        loadBondedDevices()
    }

    fun setUserTrackerMacs(macs: Set<String>) {
        userTrackerMacs.clear()
        userTrackerMacs.addAll(macs)
        val current = _devicesMap.value.toMutableMap()
        current.forEach { (mac, dev) ->
            if (userTrackerMacs.contains(mac) != dev.isUserTracker) {
                current[mac] = dev.copy(isUserTracker = userTrackerMacs.contains(mac))
            }
        }
        _devicesMap.value = current
    }

    /**
     * Pre-populate bonded/paired Classic and BLE devices so user sees them immediately
     */
    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        if (!isBluetoothSupported || !isBluetoothEnabled) return
        try {
            val bonded = bluetoothAdapter?.bondedDevices ?: return
            val current = _devicesMap.value.toMutableMap()
            val now = System.currentTimeMillis()

            for (dev in bonded) {
                val mac = dev.address ?: continue
                val name = dev.name ?: ""

                // Cache known name
                if (name.isNotBlank()) {
                    knownDeviceCache.saveDeviceName(mac, name)
                }

                val type = when (dev.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.BLE
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
                    BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
                    else -> BluetoothDeviceType.UNKNOWN
                }

                val isConnected = activeGatts.containsKey(mac)
                val status = if (isConnected) DeviceConnectionStatus.CONNECTED else DeviceConnectionStatus.PAIRED
                val cachedLoc = knownDeviceCache.getLastLocation(mac)

                val existing = current[mac]
                if (existing == null) {
                    current[mac] = DiscoveredBluetoothDevice(
                        address = mac,
                        name = name,
                        rssi = 0,
                        smoothedRssi = 0,
                        lifecycleState = if (isConnected) DeviceLifecycleState.ACTIVE else DeviceLifecycleState.RECENTLY_SEEN,
                        deviceType = type,
                        connectionStatus = status,
                        firstSeenTimestamp = now,
                        lastSeenTimestamp = now,
                        lastKnownLatitude = cachedLoc?.latitude,
                        lastKnownLongitude = cachedLoc?.longitude,
                        lastKnownLocationLabel = cachedLoc?.addressLabel,
                        lastKnownLocationTimestamp = cachedLoc?.timestamp,
                        isUserTracker = userTrackerMacs.contains(mac)
                    )
                } else {
                    current[mac] = existing.copy(
                        name = if (existing.name.isBlank()) name else existing.name,
                        deviceType = type,
                        connectionStatus = if (activeGatts.containsKey(mac)) DeviceConnectionStatus.CONNECTED else DeviceConnectionStatus.PAIRED
                    )
                }
            }
            _devicesMap.value = current
        } catch (e: SecurityException) {
            Log.w(tag, "Permission not granted to read bonded devices", e)
        } catch (e: Exception) {
            Log.w(tag, "Error loading bonded devices", e)
        }
    }

    /**
     * Start real BLE scan
     */
    @SuppressLint("MissingPermission")
    fun startScan(lowLatency: Boolean = false) {
        if (!isBluetoothSupported || !isBluetoothEnabled) {
            _isScanning.value = false
            return
        }

        try {
            loadBondedDevices()
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bleScanner == null) {
                _isScanning.value = false
                return
            }

            _isScanning.value = true

            val mode = if (lowLatency || _lockedTargetMac.value != null) {
                ScanSettings.SCAN_MODE_LOW_LATENCY
            } else {
                ScanSettings.SCAN_MODE_BALANCED
            }

            val settings = ScanSettings.Builder()
                .setScanMode(mode)
                .setReportDelay(0)
                .build()

            bleScanner?.stopScan(scanCallback)
            bleScanner?.startScan(null, settings, scanCallback)

            mainHandler.removeCallbacks(stopScanRunnable)
            // If target is locked, keep scanning indefinitely; otherwise auto-stop after 30s
            if (_lockedTargetMac.value == null) {
                mainHandler.postDelayed(stopScanRunnable, 30000)
            }

        } catch (e: SecurityException) {
            Log.e(tag, "Missing permission for BLE scan", e)
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(tag, "Error starting BLE scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(stopScanRunnable)
        _isScanning.value = false
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(tag, "Error stopping scan: ${e.message}")
        }
    }

    /**
     * Lock onto a specific target device for real-time finding without GATT connection
     */
    fun lockTarget(mac: String) {
        _lockedTargetMac.value = mac
        _lockedTargetTrend.value = SignalTrend.STABLE
        // Restart scan with low latency for maximum responsiveness
        startScan(lowLatency = true)
        // Record phone location for this target immediately
        recordLocationForDevice(mac)
    }

    fun unlockTarget() {
        _lockedTargetMac.value = null
        _lockedTargetTrend.value = SignalTrend.STABLE
        // Revert to normal scan timeout
        mainHandler.removeCallbacks(stopScanRunnable)
        mainHandler.postDelayed(stopScanRunnable, 15000)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "BLE Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val scanRecord = result.scanRecord
        val currentMap = _devicesMap.value.toMutableMap()
        val existing = currentMap[address]

        // 1. DEVICE NAME RESOLUTION HIERARCHY:
        // Priority 1: Advertised local BLE device name
        val advertisedName = scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        // Priority 2: Scan result device name
        val rawDeviceName = device.name?.takeIf { it.isNotBlank() }
        // Priority 3: Previously known name stored in persistent cache
        val cachedName = knownDeviceCache.getDeviceName(address)

        // Decode manufacturer info
        var manufacturerId: Int? = null
        var manufacturerName: String? = null
        var manufacturerHex: String? = null

        val manufacturerSparseArray = scanRecord?.manufacturerSpecificData
        if (manufacturerSparseArray != null && manufacturerSparseArray.size() > 0) {
            manufacturerId = manufacturerSparseArray.keyAt(0)
            manufacturerName = BluetoothCompanyLookup.getCompanyName(manufacturerId)
            val bytes = manufacturerSparseArray.valueAt(0)
            if (bytes != null && bytes.isNotEmpty()) {
                manufacturerHex = bytes.joinToString(separator = " ") { "%02X".format(it) }
            }
        }

        // Priority 4: If no real name yet, but manufacturer is known -> "$manufacturerName Device"
        val manufacturerFallbackName = if (!manufacturerName.isNullOrBlank()) {
            "$manufacturerName Device"
        } else {
            null
        }

        // Resolve best name according to hierarchy
        val resolvedName = advertisedName
            ?: rawDeviceName
            ?: existing?.name?.takeIf { it.isNotBlank() && !it.equals("Unknown BLE Device", ignoreCase = true) }
            ?: cachedName
            ?: manufacturerFallbackName
            ?: ""

        // Persist real name if found
        if (advertisedName != null || rawDeviceName != null) {
            knownDeviceCache.saveDeviceName(address, resolvedName)
        }

        val rawRssi = result.rssi
        val now = System.currentTimeMillis()

        // 2. RSSI SMOOTHING USING EXPONENTIAL MOVING AVERAGE (EMA)
        // alpha = 0.35 gives responsive yet stable smoothing
        val alpha = 0.35f
        val smoothedRssi = if (existing != null && existing.smoothedRssi != 0) {
            (alpha * rawRssi + (1f - alpha) * existing.smoothedRssi).toInt()
        } else {
            rawRssi
        }

        val updatedRssiHistory = if (existing != null) {
            (existing.rssiHistory + rawRssi).takeLast(20)
        } else {
            listOf(rawRssi)
        }

        val updatedSmoothedHistory = if (existing != null) {
            (existing.smoothedRssiHistory + smoothedRssi).takeLast(20)
        } else {
            listOf(smoothedRssi)
        }

        // Device type
        val deviceType = when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.BLE
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
            BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
            else -> BluetoothDeviceType.BLE
        }

        // Connection status
        val isConnected = activeGatts.containsKey(address)
        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
        val connectionStatus = when {
            isConnected -> DeviceConnectionStatus.CONNECTED
            isBonded -> DeviceConnectionStatus.PAIRED
            resolvedName.isBlank() -> DeviceConnectionStatus.UNKNOWN
            else -> DeviceConnectionStatus.NEARBY
        }

        // Service UUIDs
        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (result.txPower != ScanResult.TX_POWER_NOT_PRESENT) result.txPower else null
        } else {
            scanRecord?.txPowerLevel
        }

        // Tracker compatibility check
        val uuids = serviceUuids.mapNotNull {
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }
        val isCompatible = uuids.contains(BleGattSpec.UUID_CUSTOM_TRACKER_SERVICE) ||
                uuids.contains(BleGattSpec.UUID_SERVICE_IMMEDIATE_ALERT) ||
                resolvedName.contains("Track", ignoreCase = true) ||
                resolvedName.contains("Beacon", ignoreCase = true) ||
                resolvedName.contains("Tag", ignoreCase = true)

        val updatedDevice = DiscoveredBluetoothDevice(
            address = address,
            name = resolvedName,
            rssi = rawRssi,
            smoothedRssi = smoothedRssi,
            lifecycleState = DeviceLifecycleState.ACTIVE,
            deviceType = deviceType,
            connectionStatus = connectionStatus,
            manufacturerId = manufacturerId ?: existing?.manufacturerId,
            manufacturerName = manufacturerName ?: existing?.manufacturerName,
            manufacturerDataHex = manufacturerHex ?: existing?.manufacturerDataHex,
            serviceUuids = if (serviceUuids.isNotEmpty()) serviceUuids else (existing?.serviceUuids ?: emptyList()),
            txPowerLevel = txPower ?: existing?.txPowerLevel,
            firstSeenTimestamp = existing?.firstSeenTimestamp ?: now,
            lastSeenTimestamp = now,
            lastKnownLatitude = existing?.lastKnownLatitude,
            lastKnownLongitude = existing?.lastKnownLongitude,
            lastKnownLocationLabel = existing?.lastKnownLocationLabel,
            lastKnownLocationTimestamp = existing?.lastKnownLocationTimestamp,
            rssiHistory = updatedRssiHistory,
            smoothedRssiHistory = updatedSmoothedHistory,
            isUserTracker = userTrackerMacs.contains(address),
            isCustomTrackerCompatible = isCompatible,
            gattServices = existing?.gattServices ?: emptyList(),
            isGattConnecting = existing?.isGattConnecting ?: false,
            isGattConnected = isConnected
        )

        currentMap[address] = updatedDevice
        _devicesMap.value = currentMap

        // If this device is the locked target, update phone GPS location
        if (address == _lockedTargetMac.value) {
            recordLocationForDevice(address)
        }

        // Update legacy flow for AddTrackerSheet
        val legacyList = currentMap.values.map {
            DiscoveredBleDevice(
                address = it.address,
                name = it.name,
                rssi = it.rssi,
                lastSeenTimestamp = it.lastSeenTimestamp,
                isCustomTrackerCompatible = it.isCustomTrackerCompatible,
                isSimulated = false
            )
        }.sortedByDescending { it.rssi }
        _discoveredDevices.value = legacyList
    }

    /**
     * Record the phone's last known location for a specific device when detected
     */
    private fun recordLocationForDevice(mac: String) {
        coroutineScope.launch {
            try {
                val loc = BleTrackerApp.instance.locationHelper.getLastKnownPhoneLocation() ?: return@launch
                val now = System.currentTimeMillis()
                val current = _devicesMap.value.toMutableMap()
                current[mac]?.let { dev ->
                    current[mac] = dev.copy(
                        lastKnownLatitude = loc.latitude,
                        lastKnownLongitude = loc.longitude,
                        lastKnownLocationLabel = loc.addressLabel,
                        lastKnownLocationTimestamp = now
                    )
                    _devicesMap.value = current
                    knownDeviceCache.saveLastLocation(mac, loc.latitude, loc.longitude, loc.addressLabel, now)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to record location for $mac: ${e.message}")
            }
        }
    }

    /**
     * Periodic monitor to update device lifecycle states (Active, Recent, Out of range, Lost)
     * without abruptly dropping them from the list, and compute target signal trend.
     */
    private fun startLifecycleMonitorLoop() {
        lifecycleMonitorJob?.cancel()
        lifecycleMonitorJob = coroutineScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val current = _devicesMap.value
                if (current.isNotEmpty()) {
                    var modified = false
                    val updated = current.mapValues { (_, dev) ->
                        val diff = now - dev.lastSeenTimestamp
                        val newState = when {
                            diff <= 2000L -> DeviceLifecycleState.ACTIVE
                            diff <= 5000L -> DeviceLifecycleState.RECENTLY_SEEN
                            diff <= 15000L -> DeviceLifecycleState.OUT_OF_RANGE
                            else -> DeviceLifecycleState.LOST
                        }
                        if (newState != dev.lifecycleState) {
                            modified = true
                            dev.copy(lifecycleState = newState)
                        } else {
                            dev
                        }
                    }
                    if (modified) {
                        _devicesMap.value = updated
                    }
                }
                updateTargetTrend()
            }
        }
    }

    /**
     * Calculate signal trend for the locked target (Getting closer / Moving farther / Stable / Lost)
     */
    private fun updateTargetTrend() {
        val targetMac = _lockedTargetMac.value ?: return
        val target = _devicesMap.value[targetMac]
        if (target == null) {
            _lockedTargetTrend.value = SignalTrend.SIGNAL_LOST
            return
        }

        val elapsedSinceLastSeen = System.currentTimeMillis() - target.lastSeenTimestamp
        if (elapsedSinceLastSeen > 10000L || target.lifecycleState == DeviceLifecycleState.LOST) {
            _lockedTargetTrend.value = SignalTrend.SIGNAL_LOST
            return
        }

        val history = target.smoothedRssiHistory
        if (history.size >= 4) {
            val recent = history.takeLast(2).average()
            val prior = history.dropLast(2).takeLast(2).average()
            val diff = recent - prior
            _lockedTargetTrend.value = when {
                diff >= 2.0 -> SignalTrend.GETTING_CLOSER
                diff <= -2.0 -> SignalTrend.MOVING_FARTHER
                else -> SignalTrend.STABLE
            }
        } else {
            _lockedTargetTrend.value = SignalTrend.STABLE
        }
    }

    /**
     * Connect to a tracker or any BLE device via GATT
     */
    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        updateTrackerStatus(macAddress) {
            it.copy(connectionState = ConnectionState.CONNECTING)
        }
        updateDeviceGattState(macAddress, isConnecting = true, isConnected = false)

        if (!isBluetoothSupported || bluetoothAdapter == null) {
            _events.tryEmit(BleEvent.ConnectionFailed(macAddress, "Bluetooth hardware unavailable"))
            updateTrackerStatus(macAddress) { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            updateDeviceGattState(macAddress, isConnecting = false, isConnected = false)
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, createGattCallback(macAddress), BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, createGattCallback(macAddress))
            }
            activeGatts[macAddress] = gatt
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException connecting to $macAddress", e)
            _events.tryEmit(BleEvent.ConnectionFailed(macAddress, "Missing Bluetooth permissions"))
            updateTrackerStatus(macAddress) { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            updateDeviceGattState(macAddress, isConnecting = false, isConnected = false)
        } catch (e: Exception) {
            Log.e(tag, "Error connecting to $macAddress", e)
            _events.tryEmit(BleEvent.ConnectionFailed(macAddress, e.message ?: "Connection error"))
            updateTrackerStatus(macAddress) { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            updateDeviceGattState(macAddress, isConnecting = false, isConnected = false)
        }
    }

    /**
     * Disconnect GATT device
     */
    @SuppressLint("MissingPermission")
    fun disconnect(macAddress: String) {
        try {
            val gatt = activeGatts[macAddress]
            gatt?.disconnect()
            gatt?.close()
            activeGatts.remove(macAddress)
        } catch (e: Exception) {
            Log.w(tag, "Error disconnecting $macAddress: ${e.message}")
        } finally {
            updateTrackerStatus(macAddress) {
                it.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    isBeeping = false,
                    rssi = 0
                )
            }
            updateDeviceGattState(macAddress, isConnecting = false, isConnected = false)
            _events.tryEmit(BleEvent.DeviceDisconnected(macAddress))
        }
    }

    /**
     * Trigger tracker physical buzzer to START or STOP beeping
     */
    @SuppressLint("MissingPermission")
    fun setBuzzerState(macAddress: String, shouldBeep: Boolean) {
        val gatt = activeGatts[macAddress]
        if (gatt == null) {
            Log.w(tag, "Cannot set buzzer: device $macAddress is not connected")
            return
        }

        val customService = gatt.getService(BleGattSpec.UUID_CUSTOM_TRACKER_SERVICE)
        val immediateAlertService = gatt.getService(BleGattSpec.UUID_SERVICE_IMMEDIATE_ALERT)

        val buzzerChar = customService?.getCharacteristic(BleGattSpec.UUID_CHAR_BUZZER_CONTROL)
        val alertChar = immediateAlertService?.getCharacteristic(BleGattSpec.UUID_CHAR_ALERT_LEVEL)

        try {
            if (buzzerChar != null) {
                val command = if (shouldBeep) BleGattSpec.CMD_BUZZER_START_BEEP else BleGattSpec.CMD_BUZZER_STOP
                writeCharacteristicCompat(gatt, buzzerChar, byteArrayOf(command))
                updateTrackerStatus(macAddress) { it.copy(isBeeping = shouldBeep) }
                _events.tryEmit(BleEvent.BeepStatusChanged(macAddress, shouldBeep))
            } else if (alertChar != null) {
                val command = if (shouldBeep) BleGattSpec.ALERT_LEVEL_HIGH else BleGattSpec.ALERT_LEVEL_NO_ALERT
                writeCharacteristicCompat(gatt, alertChar, byteArrayOf(command))
                updateTrackerStatus(macAddress) { it.copy(isBeeping = shouldBeep) }
                _events.tryEmit(BleEvent.BeepStatusChanged(macAddress, shouldBeep))
            } else {
                Log.w(tag, "No buzzer or alert characteristic found on $macAddress")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error writing buzzer command to $macAddress", e)
        }
    }

    /**
     * Trigger simulated tracker physical button press (Find Phone test)
     */
    fun triggerSimulatedButtonPress(macAddress: String = "TRACKER:01") {
        _events.tryEmit(BleEvent.ButtonPressed(macAddress, BleGattSpec.EVENT_BUTTON_SINGLE_CLICK))
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            char.value = value
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(char)
        }
    }

    private fun createGattCallback(macAddress: String) = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "onConnectionStateChange $macAddress: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateTrackerStatus(macAddress) {
                    it.copy(connectionState = ConnectionState.CONNECTED)
                }
                updateDeviceGattState(macAddress, isConnecting = false, isConnected = true)
                _events.tryEmit(BleEvent.DeviceConnected(macAddress))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateTrackerStatus(macAddress) {
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        isBeeping = false,
                        rssi = 0
                    )
                }
                updateDeviceGattState(macAddress, isConnecting = false, isConnected = false)
                _events.tryEmit(BleEvent.DeviceDisconnected(macAddress))
                gatt.close()
                activeGatts.remove(macAddress)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            // Parse all discovered GATT services and characteristics for the Device Details view
            val serviceInfoList = gatt.services.map { s: BluetoothGattService ->
                val charInfos = s.characteristics.map { c: BluetoothGattCharacteristic ->
                    val props = mutableListOf<String>()
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ")
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE")
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WRITE NO RESP")
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY")
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("INDICATE")

                    GattCharacteristicInfo(
                        uuid = c.uuid.toString(),
                        name = BluetoothServiceLookup.getCharacteristicName(c.uuid.toString()),
                        properties = props
                    )
                }
                GattServiceInfo(
                    uuid = s.uuid.toString(),
                    name = BluetoothServiceLookup.getServiceName(s.uuid.toString()),
                    characteristics = charInfos
                )
            }

            // Update in device details
            val currentMap = _devicesMap.value.toMutableMap()
            currentMap[macAddress]?.let { dev ->
                currentMap[macAddress] = dev.copy(gattServices = serviceInfoList)
                _devicesMap.value = currentMap
            }
            _events.tryEmit(BleEvent.GattServicesDiscovered(macAddress, serviceInfoList))

            // Check Battery Service
            val batteryService = gatt.getService(BleGattSpec.UUID_SERVICE_BATTERY)
            val batteryChar = batteryService?.getCharacteristic(BleGattSpec.UUID_CHAR_BATTERY_LEVEL)
            if (batteryChar != null) {
                gatt.readCharacteristic(batteryChar)
            }

            // Check Custom Tracker Service button notifications
            val trackerService = gatt.getService(BleGattSpec.UUID_CUSTOM_TRACKER_SERVICE)
            val buttonChar = trackerService?.getCharacteristic(BleGattSpec.UUID_CHAR_BUTTON_EVENT)
            if (buttonChar != null) {
                enableNotifications(gatt, buttonChar)
            }

            gatt.readRemoteRssi()
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicData(macAddress, characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicData(macAddress, characteristic)
        }

        @SuppressLint("MissingPermission")
        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateTrackerStatus(macAddress) {
                    it.copy(rssi = rssi, lastRssiTimestamp = System.currentTimeMillis())
                }
                val currentMap = _devicesMap.value.toMutableMap()
                currentMap[macAddress]?.let { dev ->
                    val alpha = 0.35f
                    val smoothed = if (dev.smoothedRssi != 0) {
                        (alpha * rssi + (1f - alpha) * dev.smoothedRssi).toInt()
                    } else {
                        rssi
                    }
                    val updatedHist = (dev.rssiHistory + rssi).takeLast(20)
                    val updatedSmoothHist = (dev.smoothedRssiHistory + smoothed).takeLast(20)
                    currentMap[macAddress] = dev.copy(
                        rssi = rssi,
                        smoothedRssi = smoothed,
                        rssiHistory = updatedHist,
                        smoothedRssiHistory = updatedSmoothHist,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        lifecycleState = DeviceLifecycleState.ACTIVE
                    )
                    _devicesMap.value = currentMap
                }
                _events.tryEmit(BleEvent.RssiUpdated(macAddress, rssi))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(BleGattSpec.UUID_DESCRIPTOR_CCCD)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleCharacteristicData(macAddress: String, char: BluetoothGattCharacteristic) {
        val value = char.value ?: return

        when (char.uuid) {
            BleGattSpec.UUID_CHAR_BATTERY_LEVEL -> {
                val batteryLevel = value.firstOrNull()?.toInt()?.coerceIn(0, 100) ?: return
                updateTrackerStatus(macAddress) { it.copy(batteryLevel = batteryLevel) }
                val currentMap = _devicesMap.value.toMutableMap()
                currentMap[macAddress]?.let { dev ->
                    currentMap[macAddress] = dev.copy(batteryLevel = batteryLevel)
                    _devicesMap.value = currentMap
                }
                _events.tryEmit(BleEvent.BatteryUpdated(macAddress, batteryLevel))
            }
            BleGattSpec.UUID_CHAR_BUTTON_EVENT -> {
                val clickType = value.firstOrNull() ?: BleGattSpec.EVENT_BUTTON_SINGLE_CLICK
                Log.i(tag, "Tracker button triggered! Mac=$macAddress, ClickType=$clickType")
                _events.tryEmit(BleEvent.ButtonPressed(macAddress, clickType))
            }
        }
    }

    /**
     * Poll RSSI for connected trackers and active GATT connections
     */
    private fun startRssiPollingLoop() {
        rssiPollingJob?.cancel()
        rssiPollingJob = coroutineScope.launch {
            while (isActive) {
                delay(1200)
                activeGatts.forEach { (_, gatt) ->
                    try {
                        @SuppressLint("MissingPermission")
                        gatt.readRemoteRssi()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun updateTrackerStatus(
        macAddress: String,
        transform: (TrackerLiveStatus) -> TrackerLiveStatus
    ) {
        val current = _trackerStatuses.value.toMutableMap()
        val existing = current[macAddress] ?: TrackerLiveStatus(macAddress = macAddress)
        current[macAddress] = transform(existing)
        _trackerStatuses.value = current
    }

    private fun updateDeviceGattState(macAddress: String, isConnecting: Boolean, isConnected: Boolean) {
        val currentMap = _devicesMap.value.toMutableMap()
        currentMap[macAddress]?.let { dev ->
            val status = if (isConnected) {
                DeviceConnectionStatus.CONNECTED
            } else if (dev.connectionStatus == DeviceConnectionStatus.PAIRED) {
                DeviceConnectionStatus.PAIRED
            } else {
                DeviceConnectionStatus.NEARBY
            }
            currentMap[macAddress] = dev.copy(
                isGattConnecting = isConnecting,
                isGattConnected = isConnected,
                connectionStatus = status
            )
            _devicesMap.value = currentMap
        }
    }
}

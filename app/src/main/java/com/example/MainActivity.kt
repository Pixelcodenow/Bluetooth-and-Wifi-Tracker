package com.example

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ble.DiscoveredBluetoothDevice
import com.example.notification.TrackerNotificationManager
import com.example.ui.components.AddTrackerSheet
import com.example.ui.components.FindPhoneAlertOverlay
import com.example.ui.components.UnifiedWirelessView
import com.example.ui.screens.DeviceDetailsScreen
import com.example.ui.screens.FindNearbyDeviceScreen
import com.example.ui.screens.HardwareDocsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.NearbyDevicesScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.SavedDevicesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WifiDetailsScreen
import com.example.ui.screens.WifiFindScreen
import com.example.ui.screens.WifiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TrackerViewModel
import com.example.viewmodel.WifiViewModel
import com.example.wifi.WifiNetworkModel

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    BLUETOOTH("Bluetooth", Icons.Filled.BluetoothSearching, Icons.Outlined.BluetoothSearching, "nav_bluetooth"),
    WIFI("Wi-Fi", Icons.Filled.Wifi, Icons.Outlined.Wifi, "nav_wifi"),
    SAVED("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_saved"),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History, "nav_history"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private var trackerViewModel: TrackerViewModel? = null
    private var wifiViewModel: WifiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                val trackerVm: TrackerViewModel = viewModel(factory = TrackerViewModel.Factory)
                val wifiVm: WifiViewModel = viewModel(factory = WifiViewModel.Factory)
                trackerViewModel = trackerVm
                wifiViewModel = wifiVm

                // Check intent actions
                LaunchedEffect(intent) {
                    handleIntent(intent, trackerVm)
                }

                MainAppScreen(
                    trackerVm = trackerVm,
                    wifiVm = wifiVm,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trackerViewModel?.let { handleIntent(intent, it) }
    }

    private fun handleIntent(intent: Intent?, vm: TrackerViewModel) {
        if (intent == null) return
        if (intent.action == TrackerNotificationManager.ACTION_STOP_ALARM) {
            vm.stopPhoneAlarm()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    trackerVm: TrackerViewModel,
    wifiVm: WifiViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(AppDestination.BLUETOOTH) }
    var showAddTrackerSheet by remember { mutableStateOf(false) }
    var isUnifiedViewActive by remember { mutableStateOf(false) }

    // Secondary navigation states
    var selectedWifiForDetails by remember { mutableStateOf<WifiNetworkModel?>(null) }
    var wifiTargetIdForFind by remember { mutableStateOf<String?>(null) }
    var isMapOpen by remember { mutableStateOf(false) }
    var isRadarOpen by remember { mutableStateOf(false) }
    var isHardwareDocsOpen by remember { mutableStateOf(false) }

    // Bluetooth ViewModel states
    val trackers by trackerVm.trackers.collectAsState()
    val discoveredDevices by trackerVm.discoveredDevices.collectAsState()
    val isBtScanning by trackerVm.isScanning.collectAsState()
    val trackerStatuses by trackerVm.trackerStatuses.collectAsState()
    val isAlarmActive by trackerVm.isAlarmActive.collectAsState()
    val alarmSource by trackerVm.alarmSource.collectAsState()
    val selectedTracker by trackerVm.selectedTracker.collectAsState()
    val trackerEvents by trackerVm.trackerEvents.collectAsState()
    val unknownTrackers by trackerVm.unknownTrackers.collectAsState()

    val filteredBtDevices by trackerVm.filteredDevices.collectAsState()
    val btStats by trackerVm.deviceStats.collectAsState()
    val btSearchQuery by trackerVm.searchQuery.collectAsState()
    val btActiveFilter by trackerVm.activeFilter.collectAsState()
    val btActiveSort by trackerVm.activeSort.collectAsState()
    val selectedBtDeviceForDetails by trackerVm.selectedDeviceForDetails.collectAsState()
    val lockedBtTargetDevice by trackerVm.lockedTargetDevice.collectAsState()
    val lockedBtTargetTrend by trackerVm.lockedTargetTrend.collectAsState()

    // Wi-Fi ViewModel states
    val wifiNetworks by wifiVm.filteredNetworks.collectAsState()
    val savedWifiNetworks by wifiVm.savedNetworks.collectAsState()
    val wifiScanEvents by wifiVm.scanHistory.collectAsState()
    val targetWifiNetwork by wifiVm.targetNetwork.collectAsState()
    val isWifiEnabled by wifiVm.isWifiEnabled.collectAsState()

    // Hardware status
    var isBluetoothEnabled by remember { mutableStateOf(true) }

    // Runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions updated
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        isBluetoothEnabled = adapter?.isEnabled == true
    }

    // Modal Overlays for Bluetooth Device Details & Find Mode
    if (selectedBtDeviceForDetails != null) {
        DeviceDetailsScreen(
            device = selectedBtDeviceForDetails!!,
            onBack = { trackerVm.closeDeviceDetails() },
            onConnect = { trackerVm.connectDevice(it) },
            onDisconnect = { trackerVm.disconnectDevice(it) },
            onFindDevice = {
                trackerVm.closeDeviceDetails()
                trackerVm.startFindingDevice(it.address)
            },
            onToggleTracker = { trackerVm.toggleTrackerStatus(it) }
        )
        return
    }

    if (lockedBtTargetDevice != null) {
        FindNearbyDeviceScreen(
            device = lockedBtTargetDevice!!,
            signalTrend = lockedBtTargetTrend,
            onStopFinding = { trackerVm.stopFindingDevice() },
            onViewOnMap = { dev ->
                trackerVm.viewDeviceLocationOnMap(dev)
                isMapOpen = true
                trackerVm.stopFindingDevice()
            },
            onToggleBuzzer = { trackerVm.toggleBuzzer(it) }
        )
        return
    }

    // Modal Overlays for Wi-Fi Details & Find Mode
    if (selectedWifiForDetails != null) {
        WifiDetailsScreen(
            network = selectedWifiForDetails!!,
            onBack = { selectedWifiForDetails = null },
            onFindNetwork = { netId ->
                wifiVm.setTargetNetwork(netId)
                wifiTargetIdForFind = netId
                selectedWifiForDetails = null
            },
            onToggleFavorite = { wifiVm.toggleFavorite(it) },
            onRenameNetwork = { bssid, name -> wifiVm.renameNetwork(bssid, name) }
        )
        return
    }

    if (wifiTargetIdForFind != null) {
        WifiFindScreen(
            targetNetwork = targetWifiNetwork,
            onBack = {
                wifiTargetIdForFind = null
                wifiVm.setTargetNetwork(null)
            }
        )
        return
    }

    // Modal Overlays for Map, Radar, and Hardware Docs
    if (isMapOpen) {
        MapScreen(
            trackers = trackers,
            selectedTracker = selectedTracker,
            onSelectTracker = { trackerVm.selectTracker(it) },
            onNavigateHome = { isMapOpen = false }
        )
        return
    }

    if (isRadarOpen) {
        RadarScreen(
            trackers = trackers,
            selectedTracker = selectedTracker,
            trackerStatuses = trackerStatuses,
            onSelectTracker = { trackerVm.selectTracker(it) },
            onToggleBuzzer = { mac -> trackerVm.toggleBuzzer(mac) },
            onNavigateHome = { isRadarOpen = false }
        )
        return
    }

    if (isHardwareDocsOpen) {
        HardwareDocsScreen()
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isUnifiedViewActive) "All Wireless Devices" else currentDestination.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                },
                actions = {
                    // Dark / Light Mode Toggle
                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppDestination.entries.forEach { destination ->
                    val isSelected = currentDestination == destination && !isUnifiedViewActive
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            isUnifiedViewActive = false
                            currentDestination = destination
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Bluetooth Disabled Banner
            if (!isBluetoothEnabled) {
                Surface(
                    color = Color(0xFFEF4444),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bluetooth is disabled on your device.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                try {
                                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    context.startActivity(enableBtIntent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Enable", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isUnifiedViewActive) {
                    UnifiedWirelessView(
                        bluetoothDevices = filteredBtDevices,
                        wifiNetworks = wifiNetworks,
                        onBluetoothClick = { dev -> trackerVm.openDeviceDetails(dev.address) },
                        onWifiClick = { net -> selectedWifiForDetails = net }
                    )
                } else {
                    Crossfade(targetState = currentDestination, label = "MainDestinationTransition") { destination ->
                        when (destination) {
                            AppDestination.BLUETOOTH -> {
                                NearbyDevicesScreen(
                                    devices = filteredBtDevices,
                                    stats = btStats,
                                    isScanning = isBtScanning,
                                    searchQuery = btSearchQuery,
                                    activeFilter = btActiveFilter,
                                    activeSort = btActiveSort,
                                    onStartScan = { trackerVm.startScan() },
                                    onStopScan = { trackerVm.stopScan() },
                                    onSearchChange = { trackerVm.setSearchQuery(it) },
                                    onFilterChange = { trackerVm.setFilter(it) },
                                    onSortChange = { trackerVm.setSort(it) },
                                    onDeviceClick = { dev -> trackerVm.openDeviceDetails(dev.address) },
                                    onFindDeviceClick = { dev -> trackerVm.startFindingDevice(dev.address) }
                                )
                            }
                            AppDestination.WIFI -> {
                                WifiScreen(
                                    viewModel = wifiVm,
                                    onNavigateToDetails = { net ->
                                        selectedWifiForDetails = net
                                        wifiVm.recordHistory(net)
                                    },
                                    onNavigateToFind = { netId ->
                                        wifiVm.setTargetNetwork(netId)
                                        wifiTargetIdForFind = netId
                                    },
                                    onToggleUnifiedView = { isUnifiedViewActive = !isUnifiedViewActive },
                                    isUnifiedViewActive = isUnifiedViewActive
                                )
                            }
                            AppDestination.SAVED -> {
                                SavedDevicesScreen(
                                    trackers = trackers,
                                    liveStatuses = trackerStatuses,
                                    savedWifiNetworks = savedWifiNetworks,
                                    onRingTracker = { tracker -> trackerVm.toggleBuzzer(tracker.macAddress) },
                                    onStopRinging = { tracker -> trackerVm.toggleBuzzer(tracker.macAddress) },
                                    onFindDevice = { tracker ->
                                        trackerVm.selectTracker(tracker.id)
                                        isRadarOpen = true
                                    },
                                    onEditTracker = { tracker, name, color, icon ->
                                        trackerVm.updateCustomization(tracker.id, name, color, icon)
                                    },
                                    onDeleteTracker = { tracker -> trackerVm.deleteTracker(tracker) },
                                    onAddTrackerClick = {
                                        trackerVm.startScan()
                                        showAddTrackerSheet = true
                                    },
                                    onFindWifi = { bssid ->
                                        wifiVm.setTargetNetwork(bssid)
                                        wifiTargetIdForFind = bssid
                                    },
                                    onRenameWifi = { bssid, name -> wifiVm.renameNetwork(bssid, name) },
                                    onDeleteWifi = { bssid -> wifiVm.deleteSaved(bssid) }
                                )
                            }
                            AppDestination.HISTORY -> {
                                HistoryScreen(
                                    trackerEvents = trackerEvents,
                                    unknownTrackers = unknownTrackers,
                                    wifiScanEvents = wifiScanEvents,
                                    onClearWifiHistory = { wifiVm.clearHistory() },
                                    onViewOnMap = { lat, lng, name ->
                                        isMapOpen = true
                                    }
                                )
                            }
                            AppDestination.SETTINGS -> {
                                SettingsScreen(
                                    isBluetoothEnabled = isBluetoothEnabled,
                                    isWifiEnabled = isWifiEnabled,
                                    onNavigateToHardwareDocs = { isHardwareDocsOpen = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for pairing new BLE trackers
    if (showAddTrackerSheet) {
        AddTrackerSheet(
            isScanning = isBtScanning,
            discoveredDevices = discoveredDevices,
            onStartScan = { trackerVm.startScan() },
            onStopScan = { trackerVm.stopScan() },
            onPairDevice = { dev, name, icon, color ->
                trackerVm.pairDevice(dev, name, icon, color)
            },
            onDismiss = {
                trackerVm.stopScan()
                showAddTrackerSheet = false
            }
        )
    }

    // Full screen heads-up pulsing emergency alert when phone is triggered by BLE button
    FindPhoneAlertOverlay(
        isAlarmActive = isAlarmActive,
        sourceTrackerName = alarmSource,
        onStopAlarm = { trackerVm.stopPhoneAlarm() }
    )
}

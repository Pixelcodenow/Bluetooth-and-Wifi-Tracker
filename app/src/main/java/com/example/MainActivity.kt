package com.example

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Sensors
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
import com.example.notification.TrackerNotificationManager
import com.example.ui.components.AddTrackerSheet
import com.example.ui.components.FindPhoneAlertOverlay
import com.example.ui.screens.DeviceDetailsScreen
import com.example.ui.screens.FindNearbyDeviceScreen
import com.example.ui.screens.HardwareDocsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.NearbyDevicesScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.SafetyPrivacyScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TrackerViewModel

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    NEARBY("Nearby", Icons.Filled.BluetoothSearching, Icons.Outlined.BluetoothSearching, "nav_nearby"),
    TRACKERS("My Trackers", Icons.Filled.Sensors, Icons.Outlined.Sensors, "nav_trackers"),
    RADAR("Find Device", Icons.Filled.NearMe, Icons.Outlined.NearMe, "nav_radar"),
    MAP("Map", Icons.Filled.Map, Icons.Outlined.Map, "nav_map"),
    DOCS("Docs", Icons.Filled.DeveloperBoard, Icons.Outlined.DeveloperBoard, "nav_docs")
}

class MainActivity : ComponentActivity() {

    private var trackerViewModel: TrackerViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }

            MyApplicationTheme(darkTheme = isDarkMode) {
                val vm: TrackerViewModel = viewModel(factory = TrackerViewModel.Factory)
                trackerViewModel = vm

                // Check intent actions
                LaunchedEffect(intent) {
                    handleIntent(intent, vm)
                }

                MainAppScreen(
                    viewModel = vm,
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
    viewModel: TrackerViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(AppDestination.NEARBY) }
    var showAddSheet by remember { mutableStateOf(false) }

    // State flows from ViewModel
    val trackers by viewModel.trackers.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val trackerStatuses by viewModel.trackerStatuses.collectAsState()
    val isAlarmActive by viewModel.isAlarmActive.collectAsState()
    val alarmSource by viewModel.alarmSource.collectAsState()
    val selectedTracker by viewModel.selectedTracker.collectAsState()

    // Scanner state
    val filteredDevices by viewModel.filteredDevices.collectAsState()
    val deviceStats by viewModel.deviceStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val activeSort by viewModel.activeSort.collectAsState()
    val selectedDeviceForDetails by viewModel.selectedDeviceForDetails.collectAsState()
    val lockedTargetDevice by viewModel.lockedTargetDevice.collectAsState()
    val lockedTargetTrend by viewModel.lockedTargetTrend.collectAsState()

    var hasBluetoothScanPermission by remember { mutableStateOf(true) }
    var isBluetoothEnabled by remember { mutableStateOf(true) }

    // Permissions check
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBluetoothScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            results[Manifest.permission.BLUETOOTH_SCAN] == true && results[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
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
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // Check if Bluetooth adapter is enabled
        val adapter = BluetoothAdapter.getDefaultAdapter()
        isBluetoothEnabled = adapter?.isEnabled == true
    }

    // Modal Overlays for Device Details & Find Device
    if (selectedDeviceForDetails != null) {
        DeviceDetailsScreen(
            device = selectedDeviceForDetails!!,
            onBack = { viewModel.closeDeviceDetails() },
            onConnect = { viewModel.connectDevice(it) },
            onDisconnect = { viewModel.disconnectDevice(it) },
            onFindDevice = {
                viewModel.closeDeviceDetails()
                viewModel.startFindingDevice(it.address)
            },
            onToggleTracker = { viewModel.toggleTrackerStatus(it) }
        )
        return
    }

    if (lockedTargetDevice != null) {
        FindNearbyDeviceScreen(
            device = lockedTargetDevice!!,
            signalTrend = lockedTargetTrend,
            onStopFinding = { viewModel.stopFindingDevice() },
            onViewOnMap = { dev ->
                viewModel.viewDeviceLocationOnMap(dev)
                currentDestination = AppDestination.MAP
                viewModel.stopFindingDevice()
            },
            onToggleBuzzer = { viewModel.toggleBuzzer(it) }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = currentDestination.title,
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
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
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
            // Bluetooth Disabled Banner if Bluetooth is off
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
                                } catch (e: Exception) {
                                    // ignore
                                }
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
                Crossfade(targetState = currentDestination, label = "ScreenTransition") { destination ->
                    when (destination) {
                        AppDestination.NEARBY -> {
                            NearbyDevicesScreen(
                                devices = filteredDevices,
                                stats = deviceStats,
                                isScanning = isScanning,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                activeSort = activeSort,
                                onStartScan = { viewModel.startScan() },
                                onStopScan = { viewModel.stopScan() },
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onFilterChange = { viewModel.setFilter(it) },
                                onSortChange = { viewModel.setSort(it) },
                                onDeviceClick = { dev -> viewModel.openDeviceDetails(dev.address) },
                                onFindDeviceClick = { dev -> viewModel.startFindingDevice(dev.address) }
                            )
                        }
                        AppDestination.TRACKERS -> {
                            HomeScreen(
                                trackers = trackers,
                                trackerStatuses = trackerStatuses,
                                onAddTrackerClick = {
                                    viewModel.startScan()
                                    showAddSheet = true
                                },
                                onTrackerClick = { tracker ->
                                    viewModel.selectTracker(tracker.id)
                                    currentDestination = AppDestination.RADAR
                                },
                                onFindRadarClick = { tracker ->
                                    viewModel.selectTracker(tracker.id)
                                    currentDestination = AppDestination.RADAR
                                },
                                onToggleBuzzer = { mac -> viewModel.toggleBuzzer(mac) },
                                onToggleFavorite = { id, current -> viewModel.toggleFavorite(id, current) },
                                onEditTracker = { id, name, color, icon ->
                                    viewModel.updateCustomization(id, name, color, icon)
                                },
                                onDeleteTracker = { tracker -> viewModel.deleteTracker(tracker) },
                                onTriggerFindPhoneTest = { viewModel.triggerFindPhoneTest() }
                            )
                        }
                        AppDestination.RADAR -> {
                            RadarScreen(
                                trackers = trackers,
                                selectedTracker = selectedTracker,
                                trackerStatuses = trackerStatuses,
                                onSelectTracker = { viewModel.selectTracker(it) },
                                onToggleBuzzer = { mac -> viewModel.toggleBuzzer(mac) },
                                onNavigateHome = { currentDestination = AppDestination.TRACKERS }
                            )
                        }
                        AppDestination.MAP -> {
                            MapScreen(
                                trackers = trackers,
                                selectedTracker = selectedTracker,
                                onSelectTracker = { viewModel.selectTracker(it) },
                                onNavigateHome = { currentDestination = AppDestination.TRACKERS }
                            )
                        }
                        AppDestination.DOCS -> {
                            HardwareDocsScreen()
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for pairing new BLE trackers
    if (showAddSheet) {
        AddTrackerSheet(
            isScanning = isScanning,
            discoveredDevices = discoveredDevices,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onPairDevice = { dev, name, icon, color ->
                viewModel.pairDevice(dev, name, icon, color)
            },
            onDismiss = {
                viewModel.stopScan()
                showAddSheet = false
            }
        )
    }

    // Full screen heads-up pulsing emergency alert when phone is triggered by BLE button
    FindPhoneAlertOverlay(
        isAlarmActive = isAlarmActive,
        sourceTrackerName = alarmSource,
        onStopAlarm = { viewModel.stopPhoneAlarm() }
    )
}

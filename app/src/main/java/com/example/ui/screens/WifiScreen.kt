package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.WifiCompareDialog
import com.example.ui.components.WifiConnectDialog
import com.example.ui.components.WifiDebugDialog
import com.example.viewmodel.WifiFilter
import com.example.viewmodel.WifiViewModel
import com.example.wifi.ConnectedWifiInfo
import com.example.wifi.WifiAdapterState
import com.example.wifi.WifiLifecycleState
import com.example.wifi.WifiNetworkModel
import com.example.wifi.WifiScanStep
import com.example.wifi.WifiSecurityType
import com.example.wifi.WifiSortType
import com.example.wifi.WifiUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(
    viewModel: WifiViewModel,
    onNavigateToDetails: (WifiNetworkModel) -> Unit,
    onNavigateToFind: (String) -> Unit,
    onToggleUnifiedView: (() -> Unit)? = null,
    isUnifiedViewActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val adapterState by viewModel.adapterState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanStep by viewModel.scanStep.collectAsState()
    val scanErrorReason by viewModel.scanErrorReason.collectAsState()
    val connectedInfo by viewModel.connectedNetwork.collectAsState()
    val networks by viewModel.filteredNetworks.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val comparisonIds by viewModel.comparisonIds.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val connectionStep by viewModel.connectionStep.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCompareDialog by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var networkToConnect by remember { mutableStateOf<WifiNetworkModel?>(null) }

    if (showDebugDialog) {
        WifiDebugDialog(
            diagnostics = diagnostics,
            onDismiss = { showDebugDialog = false },
            onTriggerScan = { viewModel.startScan() }
        )
    }

    if (showCompareDialog) {
        val compareList = networks.filter { comparisonIds.contains(it.id) }
        WifiCompareDialog(
            networks = compareList,
            onDismiss = { showCompareDialog = false }
        )
    }

    networkToConnect?.let { net ->
        WifiConnectDialog(
            network = net,
            connectionStep = connectionStep,
            onConnect = { target, pass -> viewModel.connectToNetwork(target, pass) },
            onOpenSystemSettings = { viewModel.openWifiSettings() },
            onDismiss = {
                viewModel.cancelConnection()
                networkToConnect = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Current Connected Wi-Fi Card (Top of Screen)
            item {
                CurrentConnectedWifiCard(
                    connectedInfo = connectedInfo,
                    adapterState = adapterState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 2. Hardware / Permissions / System Alert Banner if applicable
            when {
                adapterState == WifiAdapterState.WIFI_OFF || adapterState == WifiAdapterState.WIFI_TURNING_OFF -> {
                    item {
                        WifiOffStateCard(
                            onOpenSettings = { viewModel.openWifiSettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                uiState == WifiUiState.PERMISSION_REQUIRED -> {
                    item {
                        PermissionRequiredCard(
                            onGrantPermission = { viewModel.openAppSettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                uiState == WifiUiState.LOCATION_REQUIRED -> {
                    item {
                        LocationRequiredCard(
                            onOpenLocationSettings = { viewModel.openLocationSettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                uiState == WifiUiState.SCAN_FAILED -> {
                    item {
                        ScanFailedCard(
                            reason = scanErrorReason ?: "Operating system scan call failed or was restricted.",
                            onRetry = { viewModel.startScan() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                uiState == WifiUiState.SCAN_TIMEOUT -> {
                    item {
                        ScanTimeoutCard(
                            onRetry = { viewModel.startScan() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 3. Scan Control Header with Real State Machine
            if (adapterState == WifiAdapterState.WIFI_ON) {
                item {
                    ScanControlHeader(
                        isScanning = isScanning,
                        scanStep = scanStep,
                        adapterState = adapterState,
                        totalFound = stats.totalCount,
                        onScanClick = { viewModel.startScan() },
                        onOpenDebug = { showDebugDialog = true },
                        onToggleUnifiedView = onToggleUnifiedView,
                        isUnifiedViewActive = isUnifiedViewActive,
                        comparisonCount = comparisonIds.size,
                        onOpenComparison = { showCompareDialog = true },
                        onClearComparison = { viewModel.clearComparison() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // 4. Search & Filters Bar
                item {
                    SearchAndFilterSection(
                        searchQuery = searchQuery,
                        onSearchChanged = { viewModel.setSearchQuery(it) },
                        selectedFilter = selectedFilter,
                        onFilterSelected = { viewModel.setFilter(it) },
                        selectedSort = selectedSort,
                        onSortSelected = { viewModel.setSort(it) },
                        showSortMenu = showSortMenu,
                        onToggleSortMenu = { showSortMenu = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 5. Section Title & Live Stats
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WI-FI NETWORKS (${networks.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${stats.band5Count + stats.band6Count} High-Band • ${stats.openCount} Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 6. Network List or No Results Screen
                if (networks.isEmpty()) {
                    item {
                        NoResultsScreen(
                            isScanning = isScanning,
                            onScanAgain = { viewModel.startScan() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                } else {
                    items(networks, key = { it.id }) { network ->
                        WifiNetworkCard(
                            network = network,
                            isComparing = comparisonIds.contains(network.id),
                            onToggleCompare = { viewModel.toggleComparison(network.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(network) },
                            onFindClick = { onNavigateToFind(network.id) },
                            onConnectClick = { networkToConnect = network },
                            onClick = { onNavigateToDetails(network) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Card displaying actual current Wi-Fi status or explicit Disconnected alert.
 */
@Composable
fun CurrentConnectedWifiCard(
    connectedInfo: ConnectedWifiInfo?,
    adapterState: WifiAdapterState,
    modifier: Modifier = Modifier
) {
    val isConnected = connectedInfo?.isConnected == true && adapterState == WifiAdapterState.WIFI_ON

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.testTag("current_wifi_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "CURRENT WI-FI" else "DISCONNECTED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                        letterSpacing = 1.sp
                    )
                }

                if (isConnected && connectedInfo != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = connectedInfo.band,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isConnected && connectedInfo != null) {
                Text(
                    text = connectedInfo.ssid,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Signal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${connectedInfo.signalLevel.label} (${connectedInfo.rssi} dBm)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = connectedInfo.signalLevel.color
                        )
                    }

                    Column {
                        Text(
                            text = "Frequency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${connectedInfo.frequency} MHz (Ch ${connectedInfo.channel})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (connectedInfo.linkSpeedMbps > 0) {
                        Column {
                            Text(
                                text = "Link Speed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${connectedInfo.linkSpeedMbps} Mbps",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (connectedInfo.ipAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IP: ${connectedInfo.ipAddress}" + if (connectedInfo.gateway.isNotBlank()) " • Gateway: ${connectedInfo.gateway}" else "",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else {
                Text(
                    text = "No active Wi-Fi connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (adapterState == WifiAdapterState.WIFI_OFF) "Wi-Fi radio is currently disabled on this device." else "Not connected to any wireless access point.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Explicit Wi-Fi OFF Card when hardware is disabled (Point 2).
 */
@Composable
fun WifiOffStateCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
        modifier = modifier.testTag("wifi_off_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Wi-Fi is OFF",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEF4444)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Wi-Fi scanning is unavailable while the adapter is disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.testTag("open_wifi_settings_button")
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPEN WI-FI SETTINGS")
            }
        }
    }
}

@Composable
fun PermissionRequiredCard(
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
        modifier = modifier.testTag("permission_required_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Location permission is required by this version of Android to discover nearby Wi-Fi networks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Text("GRANT PERMISSION")
            }
        }
    }
}

@Composable
fun LocationRequiredCard(
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
        modifier = modifier.testTag("location_required_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.LocationOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Location Services Disabled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Location services are disabled. Android requires Location Services to deliver nearby Wi-Fi scan results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenLocationSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Text("OPEN LOCATION SETTINGS")
            }
        }
    }
}

@Composable
fun ScanFailedCard(
    reason: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN FAILED", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Reason: $reason", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("TRY AGAIN")
            }
        }
    }
}

@Composable
fun ScanTimeoutCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF97316).copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan timed out.", fontWeight = FontWeight.Bold, color = Color(0xFFF97316), style = MaterialTheme.typography.titleSmall)
            Text("Maximum scan wait (10s) reached without fresh OS scan results.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                Text("TRY AGAIN")
            }
        }
    }
}

/**
 * Scan button state machine header.
 */
@Composable
fun ScanControlHeader(
    isScanning: Boolean,
    scanStep: WifiScanStep,
    adapterState: WifiAdapterState,
    totalFound: Int,
    onScanClick: () -> Unit,
    onOpenDebug: () -> Unit,
    onToggleUnifiedView: (() -> Unit)?,
    isUnifiedViewActive: Boolean,
    comparisonCount: Int,
    onOpenComparison: () -> Unit,
    onClearComparison: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🟢 Wi-Fi ON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Text(
                        text = if (isScanning) scanStep.label else "Ready to scan • $totalFound access points",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenDebug,
                        modifier = Modifier.testTag("wifi_debug_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = "Diagnostics",
                            tint = Color(0xFF8B5CF6)
                        )
                    }

                    if (onToggleUnifiedView != null) {
                        OutlinedButton(
                            onClick = onToggleUnifiedView,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(if (isUnifiedViewActive) "Dual Mode" else "Unified")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Button(
                        onClick = onScanClick,
                        enabled = !isScanning && adapterState == WifiAdapterState.WIFI_ON,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("scan_wifi_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SCANNING...")
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SCAN NOW")
                        }
                    }
                }
            }

            // Comparison badge
            if (comparisonCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("$comparisonCount network(s) selected for comparison", style = MaterialTheme.typography.labelSmall)
                        }
                        Row {
                            TextButton(onClick = onOpenComparison) { Text("Compare", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = onClearComparison) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter and Search row.
 */
@Composable
fun SearchAndFilterSection(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedFilter: WifiFilter,
    onFilterSelected: (WifiFilter) -> Unit,
    selectedSort: WifiSortType,
    onSortSelected: (WifiSortType) -> Unit,
    showSortMenu: Boolean,
    onToggleSortMenu: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search SSID, BSSID, security, band...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("wifi_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                OutlinedButton(
                    onClick = { onToggleSortMenu(true) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("wifi_sort_button")
                ) {
                    Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onToggleSortMenu(false) }
                ) {
                    WifiSortType.values().forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = {
                                onSortSelected(sort)
                                onToggleSortMenu(false)
                            },
                            leadingIcon = {
                                if (selectedSort == sort) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Horizontal filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WifiFilter.values().forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

/**
 * Individual Wi-Fi Access Point card with 10-block visual meters and honest metadata.
 */
@Composable
fun WifiNetworkCard(
    network: WifiNetworkModel,
    isComparing: Boolean,
    onToggleCompare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFindClick: () -> Unit,
    onConnectClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLost = network.lifecycleState == WifiLifecycleState.LOST
    val alpha = network.lifecycleState.alpha

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (network.isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (network.isConnected) 3.dp else 1.dp),
        modifier = modifier
            .alpha(alpha)
            .clickable(onClick = onClick)
            .testTag("wifi_card_${network.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Status & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when {
                                    network.isConnected -> Color(0xFF10B981)
                                    isLost -> Color(0xFF94A3B8)
                                    network.isFreshResult -> Color(0xFF3B82F6)
                                    else -> Color(0xFFF59E0B)
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = network.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (network.isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (network.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Checkbox(
                        checked = isComparing,
                        onCheckedChange = { onToggleCompare() },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Fresh vs Cached distinction badge (Point 11)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (network.isFreshResult) "Current Scan" else "Previously detected (${network.lastSeenAgoText})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (network.isFreshResult) Color(0xFF3B82F6) else Color(0xFFF59E0B),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (network.isConnected) "Connected" else "Not Connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (network.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 10-Block RSSI Visual Meter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = network.signalLevel.blocksString,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = network.signalLevel.color
                )

                Text(
                    text = "${network.smoothedRssi} dBm",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = network.signalLevel.color
                )
            }

            // Signal category and proximity assistance note
            Text(
                text = "Signal: ${network.signalLevel.label} • Proximity: ${network.signalLevel.proximityCategory}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Technical Badges: Band, Channel, Security
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${network.band} • Ch ${network.channel}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = network.securityType.color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = network.securityType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = network.securityType.color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (network.wifiStandard.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = network.wifiStandard,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row: FIND NETWORK & CONNECT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onFindClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("find_wifi_${network.id}")
                ) {
                    Icon(Icons.Filled.WifiFind, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FIND NETWORK")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("connect_wifi_${network.id}")
                ) {
                    Text(if (network.isConnected) "CONNECTED" else "CONNECT")
                }
            }
        }
    }
}

/**
 * Honest No Results Screen (Point 38).
 */
@Composable
fun NoResultsScreen(
    isScanning: Boolean,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No Wi-Fi networks detected.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Possible reasons:\n• No wireless access points are currently visible in range.\n• Wi-Fi scan was restricted or throttled by operating system.\n• Device is in a radio-shielded environment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onScanAgain,
                enabled = !isScanning
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isScanning) "Scanning..." else "SCAN AGAIN")
            }
        }
    }
}

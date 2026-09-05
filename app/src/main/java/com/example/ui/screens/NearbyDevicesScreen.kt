package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BluetoothDeviceType
import com.example.ble.DeviceConnectionStatus
import com.example.ble.DeviceLifecycleState
import com.example.ble.DiscoveredBluetoothDevice
import com.example.ble.ProximityIndicator
import com.example.viewmodel.DeviceFilter
import com.example.viewmodel.DeviceScanStats
import com.example.viewmodel.DeviceSort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NearbyDevicesScreen(
    devices: List<DiscoveredBluetoothDevice>,
    stats: DeviceScanStats,
    isScanning: Boolean,
    searchQuery: String,
    activeFilter: DeviceFilter,
    activeSort: DeviceSort,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (DeviceFilter) -> Unit,
    onSortChange: (DeviceSort) -> Unit,
    onDeviceClick: (DiscoveredBluetoothDevice) -> Unit,
    onFindDeviceClick: (DiscoveredBluetoothDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Scanner Hero Control Card
        item {
            ScannerHeroCard(
                isScanning = isScanning,
                totalCount = stats.totalCount,
                onStartScan = onStartScan,
                onStopScan = onStopScan
            )
        }

        // 2. Summary Status Counter Badges
        item {
            ScanStatsRow(
                stats = stats,
                activeFilter = activeFilter,
                onSelectFilter = onFilterChange
            )
        }

        // 3. Search and Sort Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_device_input"),
                    placeholder = { Text("Search nearby devices...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Sort Dropdown Button
                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sort,
                            contentDescription = "Sort",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (activeSort) {
                                DeviceSort.SIGNAL_STRENGTH -> "Signal"
                                DeviceSort.NAME -> "Name"
                                DeviceSort.LAST_DETECTED -> "Recent"
                                DeviceSort.STATUS -> "Status"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DeviceSort.entries.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sortOption.label,
                                        fontWeight = if (activeSort == sortOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSortChange(sortOption)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Quick Category Filter Chips
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DeviceFilter.entries.forEach { filter ->
                    val isSelected = activeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = when (filter) {
                                    DeviceFilter.ALL -> "All (${stats.totalCount})"
                                    DeviceFilter.CONNECTED -> "Connected (${stats.connectedCount})"
                                    DeviceFilter.PAIRED -> "Paired (${stats.pairedCount})"
                                    DeviceFilter.NEARBY -> "Nearby (${stats.nearbyCount})"
                                    DeviceFilter.LOST -> "Lost (${stats.lostCount})"
                                    DeviceFilter.BLE -> "BLE"
                                    DeviceFilter.UNKNOWN -> "Unknown (${stats.unknownCount})"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // 5. Section Header with stability note
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DISCOVERED DEVICES (${devices.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = "Smoothed RSSI · No Jitter",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        // 6. Device List or Empty State
        if (devices.isEmpty()) {
            item {
                ScannerEmptyCard(isScanning = isScanning, onStartScan = onStartScan)
            }
        } else {
            items(devices, key = { it.address }) { device ->
                ModernDeviceCard(
                    device = device,
                    onCardClick = { onDeviceClick(device) },
                    onFindClick = { onFindDeviceClick(device) }
                )
            }
        }

        // 7. Educational Bluetooth Limitations Card
        item {
            BluetoothLimitationsCard()
        }
    }
}

@Composable
private fun ScannerHeroCard(
    isScanning: Boolean,
    totalCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isScanning) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(24.dp)
                                .then(if (isScanning) Modifier.scale(pulseScale) else Modifier)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (isScanning) "Scanning Nearby Radio" else "BLE Scanner Idle",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isScanning) "Actively listening for BLE advertisements..." else "$totalCount devices in local memory",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Button(
                    onClick = if (isScanning) onStopScan else onStartScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("scan_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isScanning) "STOP" else "SCAN")
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ScanStatsRow(
    stats: DeviceScanStats,
    activeFilter: DeviceFilter,
    onSelectFilter: (DeviceFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBadge(
            label = "Connected",
            count = stats.connectedCount,
            color = Color(0xFF10B981),
            isSelected = activeFilter == DeviceFilter.CONNECTED,
            onClick = { onSelectFilter(DeviceFilter.CONNECTED) },
            modifier = Modifier.weight(1f)
        )
        StatBadge(
            label = "Paired",
            count = stats.pairedCount,
            color = Color(0xFF38BDF8),
            isSelected = activeFilter == DeviceFilter.PAIRED,
            onClick = { onSelectFilter(DeviceFilter.PAIRED) },
            modifier = Modifier.weight(1f)
        )
        StatBadge(
            label = "Nearby",
            count = stats.nearbyCount,
            color = Color(0xFFEAB308),
            isSelected = activeFilter == DeviceFilter.NEARBY,
            onClick = { onSelectFilter(DeviceFilter.NEARBY) },
            modifier = Modifier.weight(1f)
        )
        StatBadge(
            label = "Lost",
            count = stats.lostCount,
            color = Color(0xFF64748B),
            isSelected = activeFilter == DeviceFilter.LOST,
            onClick = { onSelectFilter(DeviceFilter.LOST) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBadge(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) color else Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ModernDeviceCard(
    device: DiscoveredBluetoothDevice,
    onCardClick: () -> Unit,
    onFindClick: () -> Unit
) {
    val proximity = device.proximityIndicator
    val proximityColor = Color(proximity.colorArgb)
    val lifecycle = device.lifecycleState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("device_card_${device.address}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                device.connectionStatus == DeviceConnectionStatus.CONNECTED -> Color(0xFF10B981).copy(alpha = 0.5f)
                lifecycle == DeviceLifecycleState.ACTIVE -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row: Category Icon, Display Name, Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Category Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when (device.connectionStatus) {
                                DeviceConnectionStatus.CONNECTED -> Color(0xFF10B981).copy(alpha = 0.15f)
                                DeviceConnectionStatus.PAIRED -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = resolveCategoryIcon(device),
                        contentDescription = device.categoryLabel,
                        tint = when (device.connectionStatus) {
                            DeviceConnectionStatus.CONNECTED -> Color(0xFF10B981)
                            DeviceConnectionStatus.PAIRED -> Color(0xFF38BDF8)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (device.isNamed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                // Connection status badge
                Surface(
                    color = when (device.connectionStatus) {
                        DeviceConnectionStatus.CONNECTED -> Color(0xFF10B981).copy(alpha = 0.15f)
                        DeviceConnectionStatus.PAIRED -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                        DeviceConnectionStatus.NEARBY -> MaterialTheme.colorScheme.surfaceVariant
                        DeviceConnectionStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = device.connectionStatus.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (device.connectionStatus) {
                                DeviceConnectionStatus.CONNECTED -> Color(0xFF10B981)
                                DeviceConnectionStatus.PAIRED -> Color(0xFF38BDF8)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Chips (Technology, Category, Manufacturer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataBadge(text = device.deviceType.label)
                if (!device.manufacturerName.isNullOrBlank()) {
                    MetadataBadge(text = device.manufacturerName)
                } else {
                    MetadataBadge(text = device.categoryLabel)
                }
                Spacer(modifier = Modifier.weight(1f))

                // Lifecycle state indicator (Active / Recent / Out of Range / Lost)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(lifecycle.badgeColorArgb))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = device.lastSeenAgoText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 10-Block Proximity Bar Preview & RSSI readout
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = proximity.blocksString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = proximityColor
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = proximity.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = proximityColor
                                )
                            )
                        }
                    }

                    // RSSI Readout (Smoothed & Raw)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (device.smoothedRssi != 0) "${device.smoothedRssi} dBm" else "-- dBm",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (device.rssi != 0 && device.rssi != device.smoothedRssi) {
                            Text(
                                text = "Raw: ${device.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: [ FIND ] and [ DETAILS ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFindClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("find_button_${device.address}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FIND", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCardClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("details_button_${device.address}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DETAILS")
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ScannerEmptyCard(
    isScanning: Boolean,
    onStartScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isScanning) "Listening for Advertisements..." else "No Devices in Current View",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isScanning) "Discoverable Bluetooth devices advertise every few hundred milliseconds. Keep the scanner open."
                else "Tap 'Scan for Devices' to begin scanning nearby BLE peripherals, PCs, watches, and smart tags.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!isScanning) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartScan) {
                    Text("Start Scan")
                }
            }
        }
    }
}

@Composable
private fun BluetoothLimitationsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Understanding Bluetooth Detection",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Only active & discoverable devices broadcast advertisements.\n" +
                        "• Classic Bluetooth devices only appear when paired or put in pairing mode.\n" +
                        "• RSSI measures signal power, which is affected by walls, obstacles, human bodies, and device orientation.\n" +
                        "• Bluetooth devices do not transmit GPS coordinates; location is recorded by this phone upon detection.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

private fun resolveCategoryIcon(device: DiscoveredBluetoothDevice): ImageVector {
    val text = (device.name + " " + (device.manufacturerName ?: "") + " " + device.categoryLabel).lowercase()
    return when {
        text.contains("pc") || text.contains("macbook") || text.contains("laptop") || text.contains("desktop") || text.contains("computer") -> Icons.Filled.Computer
        text.contains("watch") || text.contains("band") || text.contains("fitbit") || text.contains("garmin") -> Icons.Filled.Watch
        text.contains("headphone") || text.contains("airpod") || text.contains("earbud") || text.contains("buds") || text.contains("bose") -> Icons.Filled.Headphones
        text.contains("speaker") || text.contains("soundbar") || text.contains("audio") -> Icons.Filled.Speaker
        text.contains("phone") || text.contains("pixel") || text.contains("galaxy") || text.contains("iphone") -> Icons.Filled.Smartphone
        text.contains("tracker") || text.contains("tag") || text.contains("tile") || device.isCustomTrackerCompatible -> Icons.Filled.Sensors
        device.connectionStatus == DeviceConnectionStatus.CONNECTED -> Icons.Filled.BluetoothConnected
        else -> Icons.Filled.Bluetooth
    }
}

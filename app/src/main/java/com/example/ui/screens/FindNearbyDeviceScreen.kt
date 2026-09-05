package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.DeviceConnectionStatus
import com.example.ble.DeviceLifecycleState
import com.example.ble.DiscoveredBluetoothDevice
import com.example.ble.ProximityIndicator
import com.example.ble.SignalTrend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindNearbyDeviceScreen(
    device: DiscoveredBluetoothDevice,
    signalTrend: SignalTrend,
    onStopFinding: () -> Unit,
    onViewOnMap: (DiscoveredBluetoothDevice) -> Unit,
    onToggleBuzzer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLost = device.lifecycleState == DeviceLifecycleState.LOST || signalTrend == SignalTrend.SIGNAL_LOST
    val proximity = if (isLost) ProximityIndicator.SIGNAL_LOST else device.proximityIndicator
    val proximityColor = Color(proximity.colorArgb)
    val trendColor = Color(signalTrend.colorArgb)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isLost) Color(0xFFEF4444) else Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FINDING TARGET",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (isLost) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            )
                        }
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onStopFinding, modifier = Modifier.testTag("find_device_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Stop Finding"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onStopFinding,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("stop_finding_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Target Identity Header Card
            item {
                TargetIdentityCard(device = device)
            }

            // 2. Large Radar & Proximity Visual Card
            item {
                TargetRadarDisplayCard(
                    device = device,
                    proximity = proximity,
                    proximityColor = proximityColor,
                    isLost = isLost
                )
            }

            // 3. Exact 10-Block Proximity Indicator Banner
            item {
                ProximityBlocksBanner(
                    proximity = proximity,
                    proximityColor = proximityColor
                )
            }

            // 4. Real-time Signal Trend Card
            item {
                SignalTrendCard(
                    trend = signalTrend,
                    trendColor = trendColor,
                    device = device
                )
            }

            // 5. Sparkline Signal History Graph
            if (device.rssiHistory.isNotEmpty() && !isLost) {
                item {
                    SignalSparklineCard(history = device.rssiHistory, smoothedHistory = device.smoothedRssiHistory)
                }
            }

            // 6. Signal Lost Alert or Guidance Instruction
            item {
                if (isLost) {
                    SignalLostAlertCard(
                        device = device,
                        onViewOnMap = { onViewOnMap(device) }
                    )
                } else {
                    MovementGuidanceCard(proximity = proximity)
                }
            }

            // 7. Last Known Phone Location Card
            item {
                LastKnownLocationCard(
                    device = device,
                    onViewOnMap = { onViewOnMap(device) }
                )
            }

            // 8. Hardware Audio / Buzzer Capability Card
            item {
                TrackerBuzzerCard(
                    device = device,
                    onToggleBuzzer = { onToggleBuzzer(device.address) }
                )
            }

            // 9. Stop Finding Action Button
            item {
                OutlinedButton(
                    onClick = onStopFinding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UNLOCK TARGET & RETURN TO SCANNER", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TargetIdentityCard(device: DiscoveredBluetoothDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolveFinderCategoryIcon(device),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = "ID: ${device.address}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp)
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = device.deviceType.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetRadarDisplayCard(
    device: DiscoveredBluetoothDevice,
    proximity: ProximityIndicator,
    proximityColor: Color,
    isLost: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadar"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, proximityColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sonar Canvas
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Grid rings
                    for (i in 1..4) {
                        val r = maxRadius * (i / 4f)
                        drawCircle(
                            color = Color(0xFF334155).copy(alpha = 0.6f),
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Pulsing animated wave if active
                    if (!isLost) {
                        val pulseRadius = (maxRadius * (pulseScale - 0.85f) / 0.5f).coerceIn(0f, maxRadius)
                        val alpha = (1.35f - pulseScale).coerceIn(0.1f, 0.8f)
                        drawCircle(
                            color = proximityColor.copy(alpha = alpha),
                            radius = pulseRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // Center Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(proximityColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLost) Icons.Filled.Warning else Icons.Filled.NearMe,
                        contentDescription = null,
                        tint = proximityColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Proximity Label & Approximate Description
            Text(
                text = proximity.label,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = proximityColor
                )
            )
            Text(
                text = proximity.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Smoothed RSSI vs Raw RSSI metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricColumn(
                    label = "Smoothed RSSI",
                    value = if (device.smoothedRssi != 0) "${device.smoothedRssi} dBm" else "--",
                    subtext = "Filtered signal power"
                )
                MetricColumn(
                    label = "Raw RSSI",
                    value = if (device.rssi != 0) "${device.rssi} dBm" else "--",
                    subtext = "Latest packet"
                )
                MetricColumn(
                    label = "Last Detected",
                    value = device.lastSeenAgoText,
                    subtext = if (isLost) "Signal lost" else "Live scan"
                )
            }
        }
    }
}

@Composable
private fun ProximityBlocksBanner(
    proximity: ProximityIndicator,
    proximityColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, proximityColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "APPROXIMATE PROXIMITY",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = proximity.blocksString,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = proximityColor
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${proximity.filledBlocks} / 10 BLOCKS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = proximityColor
                )
            )
        }
    }
}

@Composable
private fun SignalTrendCard(
    trend: SignalTrend,
    trendColor: Color,
    device: DiscoveredBluetoothDevice
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, trendColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(trendColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trend.iconText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trend.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                )
                Text(
                    text = trend.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun SignalSparklineCard(
    history: List<Int>,
    smoothedHistory: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Signal History Trend",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Past 20 packets",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val points = smoothedHistory.ifEmpty { history }
                    if (points.size < 2) return@Canvas

                    val minVal = -95f
                    val maxVal = -40f
                    val widthStep = size.width / (points.size - 1)

                    val path = Path()
                    points.forEachIndexed { index, rssi ->
                        val normalized = ((rssi.toFloat().coerceIn(minVal, maxVal) - minVal) / (maxVal - minVal))
                        val x = index * widthStep
                        val y = size.height - (normalized * size.height)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF10B981),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementGuidanceCard(proximity: ProximityIndicator) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    text = "Movement Guidance",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Move slowly around the area. Bluetooth signal fluctuates based on obstacles and distance. As you walk closer, the 10-block indicator will fill and RSSI will climb toward -50 dBm.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Notice: Distance is estimated from signal power and can be affected by walls, metal, human bodies, and device orientation.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun SignalLostAlertCard(
    device: DiscoveredBluetoothDevice,
    onViewOnMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
        border = BorderStroke(1.5.dp, Color(0xFFEF4444))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SIGNAL LOST",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No advertisements heard recently. Device may be powered off, out of range, or behind dense obstacles.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Last RSSI: ${device.smoothedRssi} dBm\n• Last detected: ${device.lastSeenAgoText}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onViewOnMap,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VIEW LAST KNOWN LOCATION")
            }
        }
    }
}

@Composable
private fun LastKnownLocationCard(
    device: DiscoveredBluetoothDevice,
    onViewOnMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Last Known Location",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                TextButton(onClick = onViewOnMap) {
                    Text("VIEW MAP", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!device.lastKnownLocationLabel.isNullOrBlank()) {
                    device.lastKnownLocationLabel
                } else if (device.lastKnownLatitude != null && device.lastKnownLongitude != null) {
                    "%.4f, %.4f".format(device.lastKnownLatitude, device.lastKnownLongitude)
                } else {
                    "Phone GPS location captured on target detection."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Note: Standard Bluetooth devices do not transmit GPS coordinates. This position marks where this phone was located when it heard the device.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun TrackerBuzzerCard(
    device: DiscoveredBluetoothDevice,
    onToggleBuzzer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Acoustic Ringing / Buzzer",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (device.isCustomTrackerCompatible) {
                            "Hardware buzzer supported via Immediate Alert / Custom GATT"
                        } else {
                            "Generic BLE devices (PCs, phones, standard peripherals) cannot be commanded to beep without proprietary vendor software."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                if (device.isCustomTrackerCompatible && device.isGattConnected) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onToggleBuzzer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RING")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, subtext: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private fun resolveFinderCategoryIcon(device: DiscoveredBluetoothDevice): ImageVector {
    val text = (device.name + " " + (device.manufacturerName ?: "") + " " + device.categoryLabel).lowercase()
    return when {
        text.contains("pc") || text.contains("macbook") || text.contains("laptop") || text.contains("desktop") || text.contains("computer") -> Icons.Filled.Computer
        text.contains("watch") || text.contains("band") || text.contains("fitbit") || text.contains("garmin") -> Icons.Filled.Watch
        text.contains("headphone") || text.contains("airpod") || text.contains("earbud") || text.contains("buds") -> Icons.Filled.Headphones
        text.contains("speaker") || text.contains("soundbar") -> Icons.Filled.Speaker
        text.contains("phone") || text.contains("pixel") || text.contains("galaxy") || text.contains("iphone") -> Icons.Filled.Smartphone
        text.contains("tracker") || text.contains("tag") || text.contains("tile") || device.isCustomTrackerCompatible -> Icons.Filled.Sensors
        else -> Icons.Filled.NearMe
    }
}

@Composable
private fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        content()
    }
}

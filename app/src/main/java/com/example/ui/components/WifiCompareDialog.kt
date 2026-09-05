package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifi.WifiNetworkModel

@Composable
fun WifiCompareDialog(
    networks: List<WifiNetworkModel>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Compare Wi-Fi Networks", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (networks.isEmpty()) {
                Text("Select 2 or more Wi-Fi networks from the list to compare their signal, band, channel, and security.")
            } else {
                val horizontalScroll = rememberScrollState()
                val verticalScroll = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(verticalScroll)
                ) {
                    Text(
                        "Side-by-side comparison of ${networks.size} networks to determine optimal performance:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .horizontalScroll(horizontalScroll)
                            .padding(8.dp)
                    ) {
                        Column {
                            // Header Row: Network Names
                            Row {
                                CompareCell(text = "Metric", isHeader = true, width = 100)
                                networks.forEach { net ->
                                    CompareCell(
                                        text = net.displayName.take(14),
                                        isHeader = true,
                                        width = 130,
                                        textColor = if (net.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Signal Level
                            Row {
                                CompareCell(text = "Signal", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(
                                        text = "${net.signalLevel.label}\n${net.smoothedRssi} dBm",
                                        isHeader = false,
                                        width = 130,
                                        textColor = net.signalLevel.color
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // 10-Block Meter
                            Row {
                                CompareCell(text = "Meter", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(
                                        text = net.signalLevel.blocksString,
                                        isHeader = false,
                                        width = 130,
                                        textColor = net.signalLevel.color,
                                        isMonospace = true
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Band
                            Row {
                                CompareCell(text = "Band", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(text = net.band, isHeader = false, width = 130)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Frequency
                            Row {
                                CompareCell(text = "Frequency", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(text = "${net.frequency} MHz", isHeader = false, width = 130)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Channel
                            Row {
                                CompareCell(text = "Channel", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(text = "Ch ${net.channel}", isHeader = false, width = 130)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Channel Width
                            Row {
                                CompareCell(text = "Channel Width", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(text = net.channelWidth, isHeader = false, width = 130)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Security
                            Row {
                                CompareCell(text = "Security", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(
                                        text = net.securityType.label,
                                        isHeader = false,
                                        width = 130,
                                        textColor = net.securityType.color
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Wi-Fi Standard
                            Row {
                                CompareCell(text = "Standard", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(text = net.wifiStandard, isHeader = false, width = 130)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))

                            // Status
                            Row {
                                CompareCell(text = "Status", isHeader = false, width = 100)
                                networks.forEach { net ->
                                    CompareCell(
                                        text = if (net.isConnected) "Connected" else "Nearby",
                                        isHeader = false,
                                        width = 130,
                                        textColor = if (net.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun CompareCell(
    text: String,
    isHeader: Boolean,
    width: Int,
    textColor: Color = Color.Unspecified,
    isMonospace: Boolean = false
) {
    Surface(
        modifier = Modifier
            .width(width.dp)
            .padding(2.dp),
        color = if (isHeader) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                fontSize = if (isMonospace) 10.sp else 11.sp,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

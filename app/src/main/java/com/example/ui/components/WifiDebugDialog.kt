package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wifi.WiFiDiagnosticsData

@Composable
fun WifiDebugDialog(
    diagnostics: WiFiDiagnosticsData,
    onDismiss: () -> Unit,
    onTriggerScan: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("wifi_debug_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Wi-Fi Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${diagnostics.platform} • SDK ${diagnostics.sdkInt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Telemetry Items
                DebugMetricRow(
                    label = "Wi-Fi enabled",
                    value = if (diagnostics.isWifiEnabled) "YES" else "NO",
                    isPositive = diagnostics.isWifiEnabled
                )

                DebugMetricRow(
                    label = "Adapter state",
                    value = diagnostics.wifiAdapterState.label,
                    isPositive = diagnostics.wifiAdapterState.name.contains("ON")
                )

                DebugMetricRow(
                    label = "Permission (Location / Nearby)",
                    value = if (diagnostics.permissionGranted) "GRANTED" else "CHECK REQUIRED",
                    isPositive = diagnostics.permissionGranted
                )

                DebugMetricRow(
                    label = "Location services",
                    value = if (diagnostics.locationServicesOn) "ON" else "OFF",
                    isPositive = diagnostics.locationServicesOn
                )

                DebugMetricRow(
                    label = "Scanner initialized",
                    value = if (diagnostics.scannerInitialized) "YES" else "NO",
                    isPositive = diagnostics.scannerInitialized
                )

                DebugMetricRow(
                    label = "Scan requested",
                    value = if (diagnostics.scanRequested) "YES" else "NO",
                    isPositive = diagnostics.scanRequested
                )

                DebugMetricRow(
                    label = "Scan callback received",
                    value = if (diagnostics.scanCallbackReceived) "YES" else "NO",
                    isPositive = diagnostics.scanCallbackReceived
                )

                DebugMetricRow(
                    label = "Results count",
                    value = "${diagnostics.resultCount} (${diagnostics.freshResultCount} fresh)",
                    isPositive = diagnostics.resultCount > 0
                )

                DebugMetricRow(
                    label = "Last scan timestamp",
                    value = diagnostics.lastScanTimeFormatted,
                    isPositive = diagnostics.lastScanTimeFormatted != "Never"
                )

                DebugMetricRow(
                    label = "Scan duration",
                    value = "${diagnostics.scanDurationSeconds} seconds",
                    isPositive = true
                )

                if (diagnostics.isThrottled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Notice: Scan throttled by Android OS (max 4 scans / 2 mins). Cached results active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (!diagnostics.lastFailureReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Failure Reason: ${diagnostics.lastFailureReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onTriggerScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trigger Test Scan")
                }
            }
        }
    }
}

@Composable
private fun DebugMetricRow(
    label: String,
    value: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    }
}

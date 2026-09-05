package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.ConnectionState
import com.example.ble.TrackerLiveStatus
import com.example.data.entity.TrackerEntity
import com.example.ui.components.EditTrackerDialog
import com.example.ui.components.TrackerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    trackers: List<TrackerEntity>,
    trackerStatuses: Map<String, TrackerLiveStatus>,
    onAddTrackerClick: () -> Unit,
    onTrackerClick: (TrackerEntity) -> Unit,
    onFindRadarClick: (TrackerEntity) -> Unit,
    onToggleBuzzer: (String) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onEditTracker: (Long, String, Long, String) -> Unit,
    onDeleteTracker: (TrackerEntity) -> Unit,
    onTriggerFindPhoneTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var trackerToEdit by remember { mutableStateOf<TrackerEntity?>(null) }
    var trackerToDelete by remember { mutableStateOf<TrackerEntity?>(null) }

    val connectedCount = trackerStatuses.values.count { it.connectionState == ConnectionState.CONNECTED }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header stats banner
            item {
                HeaderSummaryCard(
                    totalTrackers = trackers.size,
                    connectedCount = connectedCount,
                    onTriggerFindPhoneTest = onTriggerFindPhoneTest
                )
            }

            // Trackers Title & Add Quick Action
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Trackers (${trackers.size})",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )

                    OutlinedButton(
                        onClick = onAddTrackerClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_tracker_quick_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Tracker", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Empty State
            if (trackers.isEmpty()) {
                item {
                    EmptyTrackersPlaceholder(onAddTrackerClick = onAddTrackerClick)
                }
            } else {
                // Tracker list
                items(trackers, key = { it.id }) { tracker ->
                    val status = trackerStatuses[tracker.macAddress]
                    TrackerCard(
                        tracker = tracker,
                        status = status,
                        onCardClick = { onTrackerClick(tracker) },
                        onFindRadarClick = { onFindRadarClick(tracker) },
                        onToggleBuzzer = { onToggleBuzzer(tracker.macAddress) },
                        onEditClick = { trackerToEdit = tracker },
                        onToggleFavorite = { onToggleFavorite(tracker.id, tracker.isFavorite) },
                        onDeleteClick = { trackerToDelete = tracker }
                    )
                }
            }
        }

        // Floating Action Button to Add Tracker
        FloatingActionButton(
            onClick = onAddTrackerClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_tracker_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Tracker")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pair Tracker", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    // Edit Dialog
    trackerToEdit?.let { tr ->
        EditTrackerDialog(
            tracker = tr,
            onDismiss = { trackerToEdit = null },
            onSave = { name, color, icon ->
                onEditTracker(tr.id, name, color, icon)
            }
        )
    }

    // Delete Confirmation Dialog
    trackerToDelete?.let { tr ->
        AlertDialog(
            onDismissRequest = { trackerToDelete = null },
            title = { Text("Remove Tracker?") },
            text = { Text("Are you sure you want to unpair and remove \"${tr.displayName}\"? Connection history will be cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTracker(tr)
                        trackerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HeaderSummaryCard(
    totalTrackers: Int,
    connectedCount: Int,
    onTriggerFindPhoneTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bluetooth Tracker Hub",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (connectedCount > 0) "$connectedCount of $totalTrackers devices connected" else "No devices currently in range",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (connectedCount > 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothConnected,
                            contentDescription = null,
                            tint = if (connectedCount > 0) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (connectedCount > 0) "ACTIVE" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (connectedCount > 0) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Find Phone Test Banner Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTriggerFindPhoneTest() }
                    .testTag("test_find_phone_button"),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Test \"Find Phone\" Alert",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Simulates physical button press on tracker to sound phone alarm",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Trigger",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTrackersPlaceholder(onAddTrackerClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Trackers Paired Yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Connect a Bluetooth Low Energy (BLE) tag to monitor keys, wallet, backpack, or find your phone.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddTrackerClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan & Pair Tracker")
            }
        }
    }
}

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TrackerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapCanvas(
    tracker: TrackerEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val lat = tracker?.lastLatitude ?: 37.7749
    val lng = tracker?.lastLongitude ?: -122.4194
    val trackerColor = if (tracker != null) Color(tracker.colorArgb) else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F172A))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomLevel = (zoomLevel * zoom).coerceIn(0.6f, 2.5f)
                    panOffset += pan
                }
            }
    ) {
        // Map Vector Graphics
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f) + panOffset
            val scale = zoomLevel

            // 1. Waterway / River background accent
            val riverPath = Path().apply {
                moveTo(-size.width * 0.5f, center.y + 120.dp.toPx() * scale)
                cubicTo(
                    center.x - 80.dp.toPx() * scale, center.y + 80.dp.toPx() * scale,
                    center.x + 80.dp.toPx() * scale, center.y + 200.dp.toPx() * scale,
                    size.width * 1.5f, center.y + 140.dp.toPx() * scale
                )
            }
            drawPath(
                path = riverPath,
                color = Color(0xFF1E3A8A).copy(alpha = 0.5f),
                style = Stroke(width = 44.dp.toPx() * scale, cap = StrokeCap.Round)
            )

            // 2. Park / Green area blocks
            drawRoundRect(
                color = Color(0xFF065F46).copy(alpha = 0.35f),
                topLeft = Offset(center.x - 180.dp.toPx() * scale, center.y - 190.dp.toPx() * scale),
                size = androidx.compose.ui.geometry.Size(120.dp.toPx() * scale, 90.dp.toPx() * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF065F46).copy(alpha = 0.35f),
                topLeft = Offset(center.x + 80.dp.toPx() * scale, center.y - 140.dp.toPx() * scale),
                size = androidx.compose.ui.geometry.Size(110.dp.toPx() * scale, 80.dp.toPx() * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            )

            // 3. Road grid lines (Primary Avenues)
            val primaryRoadColor = Color(0xFF334155)
            val secondaryRoadColor = Color(0xFF1E293B)

            // Horizontal secondary streets
            for (i in -4..4) {
                val y = center.y + (i * 70.dp.toPx() * scale)
                drawLine(
                    color = if (i % 2 == 0) primaryRoadColor else secondaryRoadColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (i % 2 == 0) 4.dp.toPx() else 2.dp.toPx()
                )
            }

            // Vertical secondary streets
            for (i in -4..4) {
                val x = center.x + (i * 70.dp.toPx() * scale)
                drawLine(
                    color = if (i % 2 == 0) primaryRoadColor else secondaryRoadColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (i % 2 == 0) 4.dp.toPx() else 2.dp.toPx()
                )
            }

            // Diagonal Highway
            drawLine(
                color = Color(0xFFF59E0B).copy(alpha = 0.6f),
                start = Offset(0f, center.y - 120.dp.toPx() * scale),
                end = Offset(size.width, center.y + 160.dp.toPx() * scale),
                strokeWidth = 6.dp.toPx() * scale,
                cap = StrokeCap.Round
            )

            // 4. GPS Accuracy Circle around pin (representing phone GPS accuracy ~15m)
            val accuracyRadius = 55.dp.toPx() * scale
            drawCircle(
                color = trackerColor.copy(alpha = 0.15f),
                radius = accuracyRadius,
                center = center
            )
            drawCircle(
                color = trackerColor.copy(alpha = 0.5f),
                radius = accuracyRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            )

            // 5. Center Pin Target Glow
            drawCircle(
                color = trackerColor.copy(alpha = 0.4f),
                radius = 18.dp.toPx(),
                center = center
            )
            drawCircle(
                color = trackerColor,
                radius = 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center
            )
        }

        // Top Banner: Mandatory GPS Disclaimer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD0B132B)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "GPS Disclaimer",
                    tint = Color(0xFF00C9FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "LAST KNOWN PHONE LOCATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF00C9FF)
                    )
                    Text(
                        text = "Recorded by phone's GPS at disconnection. BLE hardware does not transmit satellite GPS.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Floating Map Controls (Zoom In, Zoom Out, Recenter)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(2.5f) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1E293B)),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            IconButton(
                onClick = { zoomLevel = (zoomLevel / 1.25f).coerceAtLeast(0.6f) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1E293B)),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
            IconButton(
                onClick = {
                    zoomLevel = 1.0f
                    panOffset = Offset.Zero
                },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xCC1E293B)),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Filled.Explore, contentDescription = "Recenter", tint = Color(0xFF00C9FF))
            }
        }

        // Bottom Card: Location details & Open in Maps button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE131D31)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(trackerColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = trackerColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tracker?.displayName ?: "No Tracker Selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = tracker?.lastLocationLabel ?: "Coordinates: ${String.format(Locale.US, "%.4f, %.4f", lat, lng)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last known location detected by this phone. Note: Standard Bluetooth devices do not transmit GPS coordinates.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recorded: ${formatTimestamp(tracker?.lastSeenTimestamp ?: System.currentTimeMillis())}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF64748B)
                    )

                    Button(
                        onClick = {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(tracker?.displayName ?: "Last Known Position")})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                // Web fallback
                                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("open_external_map_button")
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Open in Maps", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

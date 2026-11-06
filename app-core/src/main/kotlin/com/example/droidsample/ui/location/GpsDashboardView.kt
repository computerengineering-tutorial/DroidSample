package com.example.droidsample.ui.location

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.location.DistanceCalculator
import com.example.droidsample.location.LocationData
import com.example.droidsample.location.LocationTrackerService
import com.example.droidsample.location.MockLocationDetector
import com.example.droidsample.location.NativeLocationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GpsDashboardView(
    locationManager: NativeLocationManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLocation by locationManager.currentLocation.collectAsState()
    val isTracking by locationManager.isTracking.collectAsState()
    val activeProvider by locationManager.activeProvider.collectAsState()
    val providerStatuses by locationManager.providerStatuses.collectAsState()
    val isServiceRunning by LocationTrackerService.isServiceRunning.collectAsState()

    var showMph by remember { mutableStateOf(false) }

    val mockEvaluation = remember(currentLocation) {
        MockLocationDetector.evaluateLocationData(currentLocation, context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mock Location Warning Banner
        AnimatedVisibility(
            visible = mockEvaluation.isMock,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Mock Alert",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mock Location Detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mockEvaluation.summary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Provider Selector Chips
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Location Provider Selection",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    locationManager.getAvailableProviders().forEach { provider ->
                        val isEnabled = providerStatuses[provider]?.isEnabled ?: false
                        val isSelected = activeProvider == provider

                        FilterChip(
                            selected = isSelected,
                            onClick = { locationManager.setActiveProvider(provider) },
                            label = {
                                Text(
                                    text = "$provider ${if (isEnabled) "✓" else "✕"}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }
                }
            }
        }

        // Main Coordinates Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Geodetic Position",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Accuracy Pill
                    val accuracy = currentLocation?.horizontalAccuracyMeters
                    val (accuracyColor, accuracyLabel) = when {
                        accuracy == null -> Pair(Color.Gray, "No Fix")
                        accuracy <= 5.0f -> Pair(Color(0xFF2E7D32), "High (±${String.format("%.1f", accuracy)}m)")
                        accuracy <= 20.0f -> Pair(Color(0xFFF57F17), "Med (±${String.format("%.1f", accuracy)}m)")
                        else -> Pair(Color(0xFFC62828), "Low (±${String.format("%.1f", accuracy)}m)")
                    }

                    Surface(
                        color = accuracyColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = accuracyLabel,
                            color = accuracyColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CoordinateValueDisplay(
                        label = "LATITUDE",
                        value = currentLocation?.latitude?.let { String.format(Locale.US, "%.6f°", it) } ?: "--"
                    )
                    CoordinateValueDisplay(
                        label = "LONGITUDE",
                        value = currentLocation?.longitude?.let { String.format(Locale.US, "%.6f°", it) } ?: "--"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            currentLocation?.let {
                                val text = "${it.latitude}, ${it.longitude}"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Coordinates", text))
                                Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = currentLocation != null
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Coordinates")
                    }
                }
            }
        }

        // Speed & Heading Gauges Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speedometer Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (showMph) "MPH" else "KM/H",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val speedValue = if (showMph) {
                        currentLocation?.speedMph?.let { String.format("%.1f", it) } ?: "0.0"
                    } else {
                        currentLocation?.speedKmh?.let { String.format("%.1f", it) } ?: "0.0"
                    }

                    Text(
                        text = speedValue,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val speedAcc = currentLocation?.speedAccuracyMps
                    Text(
                        text = if (speedAcc != null) "±${String.format("%.1f", speedAcc * 3.6f)} km/h" else "Accuracy N/A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showMph = !showMph },
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (showMph) "Switch to km/h" else "Switch to mph", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Compass / Bearing Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bearing = currentLocation?.bearingDegrees ?: 0f
                    val cardinal = DistanceCalculator.bearingToCardinal(bearing.toDouble())

                    Text(
                        text = "HEADING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp)
                    ) {
                        CompassDial()
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Heading",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(bearing)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${String.format("%.0f", bearing)}° $cardinal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    val bearingAcc = currentLocation?.bearingAccuracyDegrees
                    Text(
                        text = if (bearingAcc != null) "±${String.format("%.0f", bearingAcc)}°" else "Bearing N/A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Altitude & Timing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Altitude & Satellite Timing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Altitude (WGS84)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = currentLocation?.altitudeMeters?.let { String.format("%.2f m", it) } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        currentLocation?.verticalAccuracyMeters?.let {
                            Text("Vert Acc: ±${String.format("%.1f", it)}m", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Elapsed Realtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = currentLocation?.elapsedRealtimeNanos?.let { "${it / 1_000_000_000L}s" } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val timestamp = currentLocation?.timeMillis ?: 0L
                        if (timestamp > 0) {
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                            Text("Time: $timeStr", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Foreground Service Background Tracking Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continuous Background Tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isServiceRunning) "Running in Foreground Service with notification" else "Idle (Foreground Service stopped)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { start ->
                        if (start) {
                            LocationTrackerService.start(context)
                        } else {
                            LocationTrackerService.stop(context)
                        }
                    }
                )
            }
        }

        // Action Buttons: Single Fix & Continuous Tracking Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    locationManager.requestCurrentLocation { loc ->
                        if (loc != null) {
                            Toast.makeText(context, "Current location acquired!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to get single fix.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Single Fix")
            }

            Button(
                onClick = {
                    if (isTracking) {
                        locationManager.stopLocationUpdates()
                    } else {
                        locationManager.startLocationUpdates()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isTracking) "Stop Live" else "Start Live")
            }
        }
    }
}

@Composable
private fun CoordinateValueDisplay(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CompassDial() {
    Canvas(modifier = Modifier.size(56.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 2.dp.toPx()

        drawCircle(
            color = Color.LightGray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // North tick
        drawLine(
            color = Color.Red,
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y - radius + 6.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

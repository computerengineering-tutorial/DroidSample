package com.example.droidsample.ui.location

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.location.DistanceCalculator
import com.example.droidsample.location.GeofenceEvent
import com.example.droidsample.location.GeofenceItem
import com.example.droidsample.location.GeofenceManager
import com.example.droidsample.location.GeofenceTransition
import com.example.droidsample.location.NativeLocationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeofencingView(
    geofenceManager: GeofenceManager,
    locationManager: NativeLocationManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val geofences by geofenceManager.geofences.collectAsState()
    val events by geofenceManager.events.collectAsState()
    val currentLocation by locationManager.currentLocation.collectAsState()

    var fenceName by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf("100") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Geofencing Header
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Native Proximity Alert Geofencing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Powered by LocationManager.addProximityAlert() with real-time distance countdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Add Geofence Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Create New Proximity Alert",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = fenceName,
                        onValueChange = { fenceName = it },
                        label = { Text("Geofence Name") },
                        placeholder = { Text("e.g. Office HQ or Home") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { latInput = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lonInput,
                            onValueChange = { lonInput = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = radiusInput,
                        onValueChange = { radiusInput = it },
                        label = { Text("Radius (meters)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentLocation?.let {
                                    latInput = String.format(Locale.US, "%.6f", it.latitude)
                                    lonInput = String.format(Locale.US, "%.6f", it.longitude)
                                } ?: Toast.makeText(context, "No location fix available", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use Current Fix")
                        }

                        Button(
                            onClick = {
                                val lat = latInput.toDoubleOrNull()
                                val lon = lonInput.toDoubleOrNull()
                                val radius = radiusInput.toFloatOrNull()
                                if (fenceName.isNotBlank() && lat != null && lon != null && radius != null && radius > 0f) {
                                    val created = geofenceManager.addGeofence(
                                        name = fenceName,
                                        latitude = lat,
                                        longitude = lon,
                                        radiusMeters = radius
                                    )
                                    if (created != null) {
                                        Toast.makeText(context, "Geofence registered!", Toast.LENGTH_SHORT).show()
                                        fenceName = ""
                                    } else {
                                        Toast.makeText(context, "Failed to register geofence", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Fill in all valid parameters", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Fence")
                        }
                    }
                }
            }
        }

        // Active Geofences Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Fences (${geofences.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (geofences.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { geofenceManager.clearAllGeofences() },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Clear All", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (geofences.isEmpty()) {
            item {
                Text(
                    text = "No geofences active. Set up a proximity alert above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(geofences, key = { it.id }) { fence ->
                ActiveGeofenceCard(
                    fence = fence,
                    currentLat = currentLocation?.latitude,
                    currentLon = currentLocation?.longitude,
                    onRemove = { geofenceManager.removeGeofence(fence.id) },
                    onSimulateEnter = { geofenceManager.simulateTrigger(fence.id, true) },
                    onSimulateExit = { geofenceManager.simulateTrigger(fence.id, false) }
                )
            }
        }

        // Transition Events History Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transition Events History (${events.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "No enter/exit transitions recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(events, key = { it.id }) { event ->
                GeofenceEventCard(event = event)
            }
        }
    }
}

@Composable
private fun ActiveGeofenceCard(
    fence: GeofenceItem,
    currentLat: Double?,
    currentLon: Double?,
    onRemove: () -> Unit,
    onSimulateEnter: () -> Unit,
    onSimulateExit: () -> Unit
) {
    val distanceMeters = remember(currentLat, currentLon, fence) {
        if (currentLat != null && currentLon != null) {
            DistanceCalculator.haversineDistance(currentLat, currentLon, fence.latitude, fence.longitude)
        } else null
    }

    val isInside = distanceMeters?.let { it <= fence.radiusMeters } ?: false

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInside) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
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
                            .clip(CircleShape)
                            .background(if (isInside) Color(0xFF2E7D32) else Color(0xFF1565C0))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fence.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Center: ${String.format("%.5f", fence.latitude)}, ${String.format("%.5f", fence.longitude)} | Radius: ${fence.radiusMeters.toInt()}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Distance countdown & status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distanceText = when {
                    distanceMeters == null -> "Distance: Waiting for GPS..."
                    distanceMeters >= 1000.0 -> String.format("Distance: %.2f km", distanceMeters / 1000.0)
                    else -> String.format("Distance: %.0f m", distanceMeters)
                }

                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )

                Surface(
                    color = if (isInside) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isInside) "INSIDE RADIUS" else "OUTSIDE",
                        color = if (isInside) Color(0xFF2E7D32) else Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulation trigger buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSimulateEnter,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simulate ENTER", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onSimulateExit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Simulate EXIT", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun GeofenceEventCard(event: GeofenceEvent) {
    val isEnter = event.transition == GeofenceTransition.ENTER
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestampMillis))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isEnter) Color(0xFF2E7D32) else Color(0xFFC62828),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isEnter) "ENTER" else "EXIT",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.geofenceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

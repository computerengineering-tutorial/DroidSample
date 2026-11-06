package com.example.droidsample.ui.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.droidsample.system.AppForegroundService

/**
 * ForegroundServiceView:
 * Control interface for Android 14+ typed foreground services.
 * Allows configuring combinations of Camera, Microphone, Location,
 * Data Sync, and Media Playback foreground types.
 */
@Composable
fun ForegroundServiceView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val serviceStatus by AppForegroundService.serviceState.collectAsStateWithLifecycle()

    var selectedTypesMask by remember { mutableIntStateOf(AppForegroundService.TYPE_DATA_SYNC) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (serviceStatus.isRunning) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Service Lifecycle Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (serviceStatus.isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    ) {
                        Text(
                            text = if (serviceStatus.isRunning) "RUNNING" else "STOPPED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Active Types: ${serviceStatus.typeDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (serviceStatus.isRunning) {
                    val minutes = serviceStatus.elapsedSeconds / 60
                    val seconds = serviceStatus.elapsedSeconds % 60
                    Text(
                        text = "Uptime: %02d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Service Type Selection Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Foreground Service Types (Android 14+)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select one or more declared types to start with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                TypeCheckboxRow(
                    label = "Data Sync (Background downloads/uploads)",
                    icon = Icons.Default.Sync,
                    checked = (selectedTypesMask and AppForegroundService.TYPE_DATA_SYNC) != 0,
                    enabled = !serviceStatus.isRunning,
                    onCheckedChange = { checked ->
                        selectedTypesMask = if (checked) {
                            selectedTypesMask or AppForegroundService.TYPE_DATA_SYNC
                        } else {
                            selectedTypesMask and AppForegroundService.TYPE_DATA_SYNC.inv()
                        }
                    }
                )

                TypeCheckboxRow(
                    label = "Location (GPS tracking & navigation)",
                    icon = Icons.Default.LocationOn,
                    checked = (selectedTypesMask and AppForegroundService.TYPE_LOCATION) != 0,
                    enabled = !serviceStatus.isRunning,
                    onCheckedChange = { checked ->
                        selectedTypesMask = if (checked) {
                            selectedTypesMask or AppForegroundService.TYPE_LOCATION
                        } else {
                            selectedTypesMask and AppForegroundService.TYPE_LOCATION.inv()
                        }
                    }
                )

                TypeCheckboxRow(
                    label = "Camera (Streaming & photo capture)",
                    icon = Icons.Default.CameraAlt,
                    checked = (selectedTypesMask and AppForegroundService.TYPE_CAMERA) != 0,
                    enabled = !serviceStatus.isRunning,
                    onCheckedChange = { checked ->
                        selectedTypesMask = if (checked) {
                            selectedTypesMask or AppForegroundService.TYPE_CAMERA
                        } else {
                            selectedTypesMask and AppForegroundService.TYPE_CAMERA.inv()
                        }
                    }
                )

                TypeCheckboxRow(
                    label = "Microphone (Voice recording & calls)",
                    icon = Icons.Default.Mic,
                    checked = (selectedTypesMask and AppForegroundService.TYPE_MICROPHONE) != 0,
                    enabled = !serviceStatus.isRunning,
                    onCheckedChange = { checked ->
                        selectedTypesMask = if (checked) {
                            selectedTypesMask or AppForegroundService.TYPE_MICROPHONE
                        } else {
                            selectedTypesMask and AppForegroundService.TYPE_MICROPHONE.inv()
                        }
                    }
                )

                TypeCheckboxRow(
                    label = "Media Playback (Audio & video players)",
                    icon = Icons.Default.PlayArrow,
                    checked = (selectedTypesMask and AppForegroundService.TYPE_MEDIA_PLAYBACK) != 0,
                    enabled = !serviceStatus.isRunning,
                    onCheckedChange = { checked ->
                        selectedTypesMask = if (checked) {
                            selectedTypesMask or AppForegroundService.TYPE_MEDIA_PLAYBACK
                        } else {
                            selectedTypesMask and AppForegroundService.TYPE_MEDIA_PLAYBACK.inv()
                        }
                    }
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val types = if (selectedTypesMask != 0) selectedTypesMask else AppForegroundService.TYPE_DATA_SYNC
                    AppForegroundService.start(context, types)
                },
                enabled = !serviceStatus.isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Service")
            }

            OutlinedButton(
                onClick = {
                    AppForegroundService.stop(context)
                },
                enabled = serviceStatus.isRunning,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop Service")
            }
        }

        // Compliance Architecture Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Architecture Notice",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Android 14 (API 34) Foreground Rules:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = """
                            1. Each foreground service type must be explicitly listed in AndroidManifest.xml.
                            2. Service must pass valid bitwise flags to startForeground(id, notification, type).
                            3. Corresponding FOREGROUND_SERVICE_* permission must be held.
                            4. If camera/mic/location types are used, runtime permissions must also be granted.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeCheckboxRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

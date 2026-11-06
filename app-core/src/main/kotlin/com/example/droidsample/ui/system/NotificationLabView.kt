package com.example.droidsample.ui.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.droidsample.system.NotificationHelper

/**
 * Interactive Notification Lab:
 * Test channels, notification styles, expandable layouts, action buttons, and progress notifications.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationLabView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationHelper.areNotificationsEnabled(context)
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        NotificationHelper.initChannels(context)
    }

    var selectedChannel by remember { mutableStateOf(NotificationHelper.CHANNEL_ID_DEFAULT) }
    var progressValue by remember { mutableFloatStateOf(45f) }
    var isIndeterminateProgress by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Status Card
        if (!hasNotificationPermission) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permission Alert",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "Notification Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "Android 13+ requires explicit runtime permission to show notifications on the lockscreen and status bar.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Notification Channel Selector
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Target Notification Channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Channels define notification importance, vibration, sound, and heads-up behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedChannel == NotificationHelper.CHANNEL_ID_HIGH,
                        onClick = { selectedChannel = NotificationHelper.CHANNEL_ID_HIGH },
                        label = { Text("High (Heads-Up)") }
                    )
                    FilterChip(
                        selected = selectedChannel == NotificationHelper.CHANNEL_ID_DEFAULT,
                        onClick = { selectedChannel = NotificationHelper.CHANNEL_ID_DEFAULT },
                        label = { Text("Default") }
                    )
                    FilterChip(
                        selected = selectedChannel == NotificationHelper.CHANNEL_ID_LOW,
                        onClick = { selectedChannel = NotificationHelper.CHANNEL_ID_LOW },
                        label = { Text("Low (Silent)") }
                    )
                    FilterChip(
                        selected = selectedChannel == NotificationHelper.CHANNEL_ID_MIN,
                        onClick = { selectedChannel = NotificationHelper.CHANNEL_ID_MIN },
                        label = { Text("Min (Collapsed)") }
                    )
                }
            }
        }

        // Notification Style Generators
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Notification Styles Lab",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            NotificationHelper.postStandardNotification(
                                context = context,
                                title = "System Notification",
                                message = "Standard priority notification with action buttons",
                                channelId = selectedChannel
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Post Standard Notification")
                    }

                    Button(
                        onClick = {
                            NotificationHelper.postBigTextNotification(context = context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Post BigTextStyle Notification")
                    }

                    Button(
                        onClick = {
                            NotificationHelper.postBigPictureNotification(context = context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Post BigPictureStyle Notification")
                    }

                    Button(
                        onClick = {
                            NotificationHelper.postInboxNotification(context = context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Post InboxStyle Notification")
                    }

                    Button(
                        onClick = {
                            NotificationHelper.postMessagingNotification(context = context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Post MessagingStyle Simulator")
                    }

                    Button(
                        onClick = {
                            NotificationHelper.postGroupedNotifications(context = context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Post Grouped Notifications (3 Children + Summary)")
                    }
                }
            }
        }

        // Dynamic Progress Notifications
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. Dynamic Progress Notification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Indeterminate Progress")
                    Switch(
                        checked = isIndeterminateProgress,
                        onCheckedChange = { isIndeterminateProgress = it }
                    )
                }

                if (!isIndeterminateProgress) {
                    Text(
                        text = "Progress: ${progressValue.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = progressValue,
                        onValueChange = { progressValue = it },
                        valueRange = 0f..100f
                    )
                    LinearProgressIndicator(
                        progress = { progressValue / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        NotificationHelper.updateProgressNotification(
                            context = context,
                            progress = progressValue.toInt(),
                            max = 100,
                            indeterminate = isIndeterminateProgress
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Update Progress Bar")
                }
            }
        }

        // Dismiss & Clear Controls
        OutlinedButton(
            onClick = { NotificationHelper.cancelAll(context) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text("Cancel All Notifications")
        }
    }
}

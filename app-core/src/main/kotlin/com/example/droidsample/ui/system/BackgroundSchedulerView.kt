package com.example.droidsample.ui.system

import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.NetworkType
import com.example.droidsample.system.AlarmManagerHelper
import com.example.droidsample.system.WorkSchedulerConstraints
import com.example.droidsample.system.WorkSchedulerHelper

/**
 * BackgroundSchedulerView:
 * Control dashboard to configure and enqueue WorkManager jobs (One-time and Periodic)
 * with network, battery, and device idle constraints. Also tests AlarmManager
 * exact and inexact alarms under Android 12+ API 31 restrictions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundSchedulerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // WorkManager Constraint states
    var selectedNetworkType by remember { mutableStateOf(NetworkType.CONNECTED) }
    var requiresCharging by remember { mutableStateOf(false) }
    var requiresBatteryNotLow by remember { mutableStateOf(true) }
    var requiresDeviceIdle by remember { mutableStateOf(false) }
    var requiresStorageNotLow by remember { mutableStateOf(false) }
    var initialDelaySeconds by remember { mutableFloatStateOf(0f) }

    // AlarmManager states
    var alarmDelaySeconds by remember { mutableFloatStateOf(5f) }
    var canScheduleExact by remember {
        mutableStateOf(AlarmManagerHelper.canScheduleExactAlarms(context))
    }

    // WorkManager Live status
    val oneTimeWorkInfos by WorkSchedulerHelper.getWorkInfosByTagFlow(
        context,
        WorkSchedulerHelper.TAG_ONE_TIME_SYNC
    ).collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: WorkManager Scheduler
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1. WorkManager Task Constraints",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Required Network Type:", style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = selectedNetworkType == NetworkType.CONNECTED,
                        onClick = { selectedNetworkType = NetworkType.CONNECTED },
                        label = { Text("Any Connected") }
                    )
                    FilterChip(
                        selected = selectedNetworkType == NetworkType.UNMETERED,
                        onClick = { selectedNetworkType = NetworkType.UNMETERED },
                        label = { Text("Unmetered (Wi-Fi)") }
                    )
                    FilterChip(
                        selected = selectedNetworkType == NetworkType.NOT_ROAMING,
                        onClick = { selectedNetworkType = NetworkType.NOT_ROAMING },
                        label = { Text("Not Roaming") }
                    )
                    FilterChip(
                        selected = selectedNetworkType == NetworkType.NOT_REQUIRED,
                        onClick = { selectedNetworkType = NetworkType.NOT_REQUIRED },
                        label = { Text("None") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                ConstraintSwitchRow(
                    label = "Requires Charging",
                    checked = requiresCharging,
                    onCheckedChange = { requiresCharging = it }
                )
                ConstraintSwitchRow(
                    label = "Requires Battery Not Low",
                    checked = requiresBatteryNotLow,
                    onCheckedChange = { requiresBatteryNotLow = it }
                )
                ConstraintSwitchRow(
                    label = "Requires Device Idle (Doze friendly)",
                    checked = requiresDeviceIdle,
                    onCheckedChange = { requiresDeviceIdle = it }
                )
                ConstraintSwitchRow(
                    label = "Requires Storage Not Low",
                    checked = requiresStorageNotLow,
                    onCheckedChange = { requiresStorageNotLow = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Initial Delay: ${initialDelaySeconds.toInt()}s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = initialDelaySeconds,
                    onValueChange = { initialDelaySeconds = it },
                    valueRange = 0f..60f,
                    steps = 5
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val constraints = WorkSchedulerConstraints(
                                networkType = selectedNetworkType,
                                requiresCharging = requiresCharging,
                                requiresBatteryNotLow = requiresBatteryNotLow,
                                requiresDeviceIdle = requiresDeviceIdle,
                                requiresStorageNotLow = requiresStorageNotLow
                            )
                            WorkSchedulerHelper.enqueueOneTimeWork(
                                context = context,
                                taskName = "Manual Sync (${selectedNetworkType.name})",
                                constraints = constraints,
                                initialDelaySeconds = initialDelaySeconds.toLong()
                            )
                            Toast.makeText(context, "One-Time Work enqueued", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enqueue One-Time")
                    }

                    Button(
                        onClick = {
                            val constraints = WorkSchedulerConstraints(
                                networkType = selectedNetworkType,
                                requiresCharging = requiresCharging,
                                requiresBatteryNotLow = requiresBatteryNotLow,
                                requiresDeviceIdle = requiresDeviceIdle,
                                requiresStorageNotLow = requiresStorageNotLow
                            )
                            WorkSchedulerHelper.enqueuePeriodicWork(
                                context = context,
                                taskName = "Recurring Health Sync",
                                intervalMinutes = 15,
                                constraints = constraints
                            )
                            Toast.makeText(context, "Periodic Work enqueued (15m interval)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enqueue Periodic")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        WorkSchedulerHelper.cancelAll(context)
                        Toast.makeText(context, "All Work canceled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel All Scheduled Work")
                }
            }
        }

        // Live WorkStatus card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Active One-Time Workers (${oneTimeWorkInfos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (oneTimeWorkInfos.isEmpty()) {
                    Text(
                        text = "No one-time workers currently tracked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    oneTimeWorkInfos.take(5).forEach { info ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ID: ${info.id.toString().take(8)}...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Run attempt: ${info.runAttemptCount}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = info.state.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (info.state.name) {
                                        "SUCCEEDED" -> MaterialTheme.colorScheme.primary
                                        "RUNNING" -> MaterialTheme.colorScheme.tertiary
                                        "FAILED" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: AlarmManager
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2. AlarmManager (Exact & Inexact)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Android 12+ Permission Check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCHEDULE_EXACT_ALARM Permission: " + if (canScheduleExact) "Allowed" else "Denied",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (!canScheduleExact) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        AlarmManagerHelper.createRequestExactAlarmPermissionIntent(context)
                                    )
                                }
                            ) {
                                Text("Open Settings")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Trigger Delay: ${alarmDelaySeconds.toInt()} seconds",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = alarmDelaySeconds,
                    onValueChange = { alarmDelaySeconds = it },
                    valueRange = 3f..60f,
                    steps = 18
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val success = AlarmManagerHelper.scheduleExactAlarm(
                                context = context,
                                triggerAfterSeconds = alarmDelaySeconds.toLong(),
                                alarmId = 101,
                                label = "Exact Alarm (${alarmDelaySeconds.toInt()}s)"
                            )
                            val msg = if (success) "Exact Alarm scheduled for +${alarmDelaySeconds.toInt()}s" else "Failed to schedule exact alarm (Permission required)"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set Exact Alarm")
                    }

                    Button(
                        onClick = {
                            AlarmManagerHelper.scheduleInexactAlarm(
                                context = context,
                                triggerAfterSeconds = alarmDelaySeconds.toLong(),
                                alarmId = 102,
                                label = "Inexact Alarm (${alarmDelaySeconds.toInt()}s)"
                            )
                            Toast.makeText(context, "Inexact Alarm scheduled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set Inexact Alarm")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        AlarmManagerHelper.cancelAlarm(context, 101)
                        AlarmManagerHelper.cancelAlarm(context, 102)
                        Toast.makeText(context, "Alarms canceled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AlarmOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel Alarms")
                }
            }
        }
    }
}

@Composable
private fun ConstraintSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

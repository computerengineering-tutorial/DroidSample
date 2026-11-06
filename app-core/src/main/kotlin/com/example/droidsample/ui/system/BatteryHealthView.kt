package com.example.droidsample.ui.system

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.droidsample.system.BatterySnapshot
import com.example.droidsample.system.PowerBatteryManager

/**
 * BatteryHealthView:
 * Real-time hardware telemetry dashboard displaying animated battery level gauge,
 * thermal state, voltage readings, charging status, and PowerManager Doze states.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryHealthView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val initialSnapshot = remember { PowerBatteryManager.getBatterySnapshot(context) }
    val batterySnapshot by PowerBatteryManager.observeBattery(context)
        .collectAsStateWithLifecycle(initialValue = initialSnapshot)

    val animatedProgress by animateFloatAsState(
        targetValue = batterySnapshot.percentage / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "BatteryProgress"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            batterySnapshot.isCharging -> Color(0xFF2E7D32)
            batterySnapshot.percentage <= 15 -> MaterialTheme.colorScheme.error
            batterySnapshot.percentage <= 30 -> Color(0xFFF57C00)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "GaugeColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Gauge Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(180.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 14.dp,
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = gaugeColor,
                        strokeWidth = 14.dp,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${batterySnapshot.percentage}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (batterySnapshot.isCharging) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Charging",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = batterySnapshot.pluggedSource,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusPill(
                        label = "Health",
                        value = batterySnapshot.health,
                        isHighlight = batterySnapshot.health == "Good"
                    )
                    StatusPill(
                        label = "Tech",
                        value = batterySnapshot.technology
                    )
                    StatusPill(
                        label = "Present",
                        value = if (batterySnapshot.present) "Yes" else "No"
                    )
                }
            }
        }

        // Detailed Telemetry Cards Grid
        Text(
            text = "Hardware Diagnostics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryMetricCard(
                title = "Temperature",
                value = "${batterySnapshot.temperatureCelsius} °C",
                subtitle = if (batterySnapshot.temperatureCelsius > 40.0) "High Thermal Load" else "Nominal Operating Temp",
                icon = Icons.Default.Thermostat,
                modifier = Modifier.weight(1f, fill = true)
            )

            TelemetryMetricCard(
                title = "Voltage",
                value = "${batterySnapshot.voltageMv} mV",
                subtitle = "Cell Potential",
                icon = Icons.Default.Bolt,
                modifier = Modifier.weight(1f, fill = true)
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryMetricCard(
                title = "Instant Current",
                value = "${batterySnapshot.currentNowUa / 1000} mA",
                subtitle = "Battery Manager API",
                icon = Icons.Default.Power,
                modifier = Modifier.weight(1f, fill = true)
            )

            TelemetryMetricCard(
                title = "Average Current",
                value = "${batterySnapshot.currentAverageUa / 1000} mA",
                subtitle = "Rolling Average Load",
                icon = Icons.Default.BatteryChargingFull,
                modifier = Modifier.weight(1f, fill = true)
            )
        }

        // Power Management Modes Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Power Modes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Power Save Mode (Battery Saver)")
                    ModeStatusBadge(active = batterySnapshot.isPowerSaveMode)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device Idle Mode (Doze Active)")
                    ModeStatusBadge(active = batterySnapshot.isDeviceIdleMode)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, value: String, isHighlight: Boolean = false) {
    Surface(
        color = if (isHighlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeStatusBadge(active: Boolean) {
    Surface(
        color = if (active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
    ) {
        Text(
            text = if (active) "ACTIVE" else "OFF",
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

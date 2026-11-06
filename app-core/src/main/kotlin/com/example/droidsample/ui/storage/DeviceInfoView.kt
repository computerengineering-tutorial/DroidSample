package com.example.droidsample.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.storage.DeviceInfoProvider
import com.example.droidsample.storage.DeviceSpecSheet

@Composable
fun DeviceInfoView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val provider = remember { DeviceInfoProvider(context) }
    var specs by remember { mutableStateOf(provider.getDeviceSpecSheet()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Refresh Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = specs.buildInfo.model,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${specs.buildInfo.manufacturer} • Android ${specs.buildInfo.androidRelease} (API ${specs.buildInfo.sdkInt})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { specs = provider.getDeviceSpecSheet() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // Memory (RAM) Card
        item {
            HardwareMetricCard(
                icon = Icons.Default.Memory,
                title = "RAM / System Memory",
                status = if (specs.ramInfo.isLowMemory) "LOW MEMORY" else "HEALTHY",
                isWarning = specs.ramInfo.isLowMemory
            ) {
                val totalStr = DeviceInfoProvider.formatBytesToGb(specs.ramInfo.totalMemBytes)
                val usedStr = DeviceInfoProvider.formatBytesToGb(specs.ramInfo.usedMemBytes)
                val availStr = DeviceInfoProvider.formatBytesToGb(specs.ramInfo.availableMemBytes)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Used: $usedStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Free: $availStr", style = MaterialTheme.typography.bodySmall)
                    Text("Total: $totalStr", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (specs.ramInfo.usedPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.1f", specs.ramInfo.usedPercentage)}% used",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Storage Partition Card
        item {
            HardwareMetricCard(
                icon = Icons.Default.Storage,
                title = "Primary Storage (/data)",
                status = "${String.format("%.1f", specs.storageInfo.usedPercentage)}% Used",
                isWarning = specs.storageInfo.freePercentage < 10.0
            ) {
                val totalStr = DeviceInfoProvider.formatBytesToGb(specs.storageInfo.totalBytes)
                val usedStr = DeviceInfoProvider.formatBytesToGb(specs.storageInfo.usedBytes)
                val freeStr = DeviceInfoProvider.formatBytesToGb(specs.storageInfo.availableBytes)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Used: $usedStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Free: $freeStr", style = MaterialTheme.typography.bodySmall)
                    Text("Total: $totalStr", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (specs.storageInfo.usedPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Display Specs Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Display & Refresh Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SpecRow(label = "Resolution", value = "${specs.displayInfo.widthPx} x ${specs.displayInfo.heightPx} px")
                    SpecRow(label = "Screen Density", value = "${specs.displayInfo.densityDpi} DPI (${specs.displayInfo.densityScale}x)")
                    SpecRow(label = "Refresh Rate", value = "${String.format("%.1f", specs.displayInfo.refreshRateHz)} Hz")
                    val hdrText = if (specs.displayInfo.isHdrSupported) {
                        "Supported (${specs.displayInfo.hdrCapabilities.joinToString()})"
                    } else {
                        "SDR Only"
                    }
                    SpecRow(label = "HDR Modes", value = hdrText)
                }
            }
        }

        // CPU & SoC Specs Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processor & Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SpecRow(label = "Cores Available", value = "${specs.cpuInfo.coreCount} Cores")
                    SpecRow(label = "CPU Architecture", value = specs.cpuInfo.architecture)
                    SpecRow(label = "Supported ABIs", value = specs.cpuInfo.supportedAbis.joinToString())
                    SpecRow(label = "Board / Hardware", value = "${specs.buildInfo.board} / ${specs.buildInfo.hardware}")
                }
            }
        }

        // Battery Status Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SpecRow(label = "Charge Level", value = "${specs.batteryInfo.percentage}%")
                    SpecRow(label = "State", value = if (specs.batteryInfo.isCharging) "Charging (${specs.batteryInfo.chargingSource})" else "Discharging")
                    SpecRow(label = "Health", value = specs.batteryInfo.health)
                    SpecRow(label = "Temperature", value = "${specs.batteryInfo.temperatureCelsius} °C")
                    SpecRow(label = "Voltage", value = "${specs.batteryInfo.voltageMv} mV")
                }
            }
        }

        // Build & OS Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OS & Build Fingerprint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SpecRow(label = "Manufacturer", value = specs.buildInfo.manufacturer)
                    SpecRow(label = "Brand / Product", value = "${specs.buildInfo.brand} / ${specs.buildInfo.product}")
                    SpecRow(label = "Android Release", value = specs.buildInfo.androidRelease)
                    SpecRow(label = "API Level", value = specs.buildInfo.sdkInt.toString())
                    SpecRow(label = "Security Patch", value = specs.buildInfo.securityPatch)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Fingerprint:\n${specs.buildInfo.fingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareMetricCard(
    icon: ImageVector,
    title: String,
    status: String,
    isWarning: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(status) },
                    colors = if (isWarning) {
                        AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

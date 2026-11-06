package com.example.droidsample.ui.connectivity

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.connectivity.BluetoothAdapterStatus
import com.example.droidsample.connectivity.BluetoothClassicManager
import com.example.droidsample.connectivity.ClassicDeviceInfo
import com.example.droidsample.connectivity.DeviceBondStatus

@Composable
fun BluetoothClassicView(
    classicManager: BluetoothClassicManager,
    modifier: Modifier = Modifier
) {
    val adapterStatus by classicManager.adapterStatus.collectAsState()
    val isDiscovering by classicManager.isDiscovering.collectAsState()
    val pairedDevices by classicManager.pairedDevices.collectAsState()
    val discoveredDevices by classicManager.discoveredDevices.collectAsState()
    val lastError by classicManager.lastError.collectAsState()

    var selectedDeviceForDetails by remember { mutableStateOf<ClassicDeviceInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status & Controls Header
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (adapterStatus == BluetoothAdapterStatus.ON)
                                Icons.Default.BluetoothConnected
                            else
                                Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (adapterStatus == BluetoothAdapterStatus.ON)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Classic Bluetooth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Status: ${adapterStatus.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { classicManager.refreshPairedDevices() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isDiscovering) {
                                classicManager.cancelDiscovery()
                            } else {
                                classicManager.startDiscovery()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = adapterStatus == BluetoothAdapterStatus.ON,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDiscovering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isDiscovering) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDiscovering) "Cancel Discovery" else "Start Discovery")
                    }
                }
            }
        }

        if (isDiscovering) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        lastError?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Lists (Paired and Discovered)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Paired Devices Section
            item {
                Text(
                    text = "Paired Devices (${pairedDevices.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (pairedDevices.isEmpty()) {
                item {
                    Text(
                        text = "No bonded Bluetooth devices found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(pairedDevices, key = { "paired_" + it.address }) { device ->
                    ClassicDeviceCard(
                        device = device,
                        onDetailsClick = { selectedDeviceForDetails = device },
                        onPairClick = null
                    )
                }
            }

            // Discovered Devices Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Discovered Devices (${discoveredDevices.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (discoveredDevices.isEmpty()) {
                item {
                    Text(
                        text = if (isDiscovering) "Scanning for nearby Bluetooth devices..." else "Start discovery to find nearby devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(discoveredDevices, key = { "discovered_" + it.address }) { device ->
                    ClassicDeviceCard(
                        device = device,
                        onDetailsClick = { selectedDeviceForDetails = device },
                        onPairClick = {
                            classicManager.pairDeviceByAddress(device.address)
                        }
                    )
                }
            }
        }
    }

    // Details Dialog
    selectedDeviceForDetails?.let { device ->
        ClassicDeviceDetailsDialog(
            device = device,
            onDismiss = { selectedDeviceForDetails = null }
        )
    }
}

@Composable
fun ClassicDeviceCard(
    device: ClassicDeviceInfo,
    onDetailsClick: () -> Unit,
    onPairClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = device.majorClass,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = when (device.bondState) {
                            DeviceBondStatus.BONDED -> Color(0xFFC8E6C9)
                            DeviceBondStatus.BONDING -> Color(0xFFFFF9C4)
                            DeviceBondStatus.NONE -> Color(0xFFEEEEEE)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = device.bondState.name,
                            fontSize = 10.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    device.rssi?.let { rssi ->
                        Text(
                            text = "$rssi dBm",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPairClick != null && device.bondState == DeviceBondStatus.NONE) {
                    FilledTonalButton(
                        onClick = onPairClick,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pair")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(onClick = onDetailsClick) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Device info")
                }
            }
        }
    }
}

@Composable
fun ClassicDeviceDetailsDialog(
    device: ClassicDeviceInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Technical Specs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Name", device.name)
                DetailRow("MAC Address", device.address)
                DetailRow("Bond State", device.bondState.name)
                DetailRow("Device Kind", device.deviceKind.name)
                DetailRow("Major Class", device.majorClass)
                device.rssi?.let { DetailRow("Signal RSSI", "$it dBm") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

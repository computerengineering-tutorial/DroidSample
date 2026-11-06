package com.example.droidsample.ui.connectivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.droidsample.connectivity.BleGattClientManager
import com.example.droidsample.connectivity.CharacteristicProperty
import com.example.droidsample.connectivity.GattCharacteristicInfo
import com.example.droidsample.connectivity.GattConnectionState
import com.example.droidsample.connectivity.GattServiceInfo
import java.util.UUID

@Composable
fun GattExplorerView(
    gattManager: BleGattClientManager,
    modifier: Modifier = Modifier
) {
    val connectionState by gattManager.connectionState.collectAsState()
    val deviceName by gattManager.connectedDeviceName.collectAsState()
    val deviceAddress by gattManager.connectedDeviceAddress.collectAsState()
    val services by gattManager.services.collectAsState()
    val logs by gattManager.logMessages.collectAsState()

    var writeTargetChar by remember { mutableStateOf<Pair<UUID, GattCharacteristicInfo>?>(null) }
    var showLogs by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Device header card
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName ?: "No Device Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = deviceAddress ?: "00:00:00:00:00:00",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    GattStateBadge(connectionState = connectionState)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = { showLogs = !showLogs }) {
                        Text(if (showLogs) "Hide Logs" else "View Logs")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (connectionState != GattConnectionState.DISCONNECTED) {
                        Button(
                            onClick = { gattManager.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }

        if (connectionState == GattConnectionState.CONNECTING ||
            connectionState == GattConnectionState.DISCOVERING_SERVICES
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AnimatedVisibility(visible = showLogs) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "GATT Transaction Logs",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (services.isEmpty() && connectionState == GattConnectionState.READY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No GATT Services found on peripheral.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else if (services.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Connect to a peripheral to explore its GATT services & characteristics.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(services, key = { it.uuid }) { service ->
                    GattServiceCard(
                        service = service,
                        onRead = { char ->
                            gattManager.readCharacteristic(service.uuid, char.uuid)
                        },
                        onWrite = { char ->
                            writeTargetChar = service.uuid to char
                        },
                        onToggleNotify = { char ->
                            gattManager.setNotification(service.uuid, char.uuid, !char.isNotifying)
                        }
                    )
                }
            }
        }
    }

    // Write Dialog
    writeTargetChar?.let { (serviceUuid, charInfo) ->
        GattWriteDialog(
            characteristic = charInfo,
            onDismiss = { writeTargetChar = null },
            onConfirmWrite = { text, isHex ->
                val bytes = if (isHex) {
                    text.replace(" ", "")
                        .chunked(2)
                        .mapNotNull { it.toIntOrNull(16)?.toByte() }
                        .toByteArray()
                } else {
                    text.toByteArray(Charsets.UTF_8)
                }
                gattManager.writeCharacteristic(serviceUuid, charInfo.uuid, bytes)
                writeTargetChar = null
            }
        )
    }
}

@Composable
fun GattStateBadge(connectionState: GattConnectionState) {
    val (color, label) = when (connectionState) {
        GattConnectionState.READY -> Color(0xFF2E7D32) to "Ready"
        GattConnectionState.CONNECTED -> Color(0xFF1976D2) to "Connected"
        GattConnectionState.CONNECTING -> Color(0xFFF57F17) to "Connecting"
        GattConnectionState.DISCOVERING_SERVICES -> Color(0xFF7B1FA2) to "Discovering..."
        GattConnectionState.DISCONNECTED -> Color(0xFF757575) to "Disconnected"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun GattServiceCard(
    service: GattServiceInfo,
    onRead: (GattCharacteristicInfo) -> Unit,
    onWrite: (GattCharacteristicInfo) -> Unit,
    onToggleNotify: (GattCharacteristicInfo) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Service: ${formatUuidToCommonName(service.uuid)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = service.uuid.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (service.isPrimary) "Primary" else "Secondary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    service.characteristics.forEach { char ->
                        GattCharacteristicItem(
                            characteristic = char,
                            onRead = { onRead(char) },
                            onWrite = { onWrite(char) },
                            onToggleNotify = { onToggleNotify(char) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GattCharacteristicItem(
    characteristic: GattCharacteristicInfo,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Char: ${formatUuidToCommonName(characteristic.uuid)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = characteristic.uuid.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Property chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                characteristic.properties.forEach { prop ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = prop.label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Value Display
            if (characteristic.lastValue != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = "HEX: ${characteristic.hexValue}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        if (characteristic.asciiValue.isNotBlank()) {
                            Text(
                                text = "ASCII: \"${characteristic.asciiValue}\"",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasRead = characteristic.properties.any { it == CharacteristicProperty.READ }
                val hasWrite = characteristic.properties.any {
                    it == CharacteristicProperty.WRITE || it == CharacteristicProperty.WRITE_NO_RESPONSE
                }
                val hasNotify = characteristic.properties.any {
                    it == CharacteristicProperty.NOTIFY || it == CharacteristicProperty.INDICATE
                }

                if (hasRead) {
                    FilledTonalButton(
                        onClick = onRead,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (hasWrite) {
                    FilledTonalButton(
                        onClick = onWrite,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Write")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (hasNotify) {
                    Button(
                        onClick = onToggleNotify,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (characteristic.isNotifying)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(
                            imageVector = if (characteristic.isNotifying) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (characteristic.isNotifying) "Mute" else "Notify")
                    }
                }
            }
        }
    }
}

@Composable
fun GattWriteDialog(
    characteristic: GattCharacteristicInfo,
    onDismiss: () -> Unit,
    onConfirmWrite: (String, Boolean) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var isHexMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write Characteristic") },
        text = {
            Column {
                Text(
                    text = characteristic.uuid.toString(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { isHexMode = false }) {
                        Text("Text/ASCII", fontWeight = if (!isHexMode) FontWeight.Bold else FontWeight.Normal)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { isHexMode = true }) {
                        Text("HEX Bytes", fontWeight = if (isHexMode) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text(if (isHexMode) "e.g. 01 02 A0 FF" else "e.g. Hello") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmWrite(textInput, isHexMode) },
                enabled = textInput.isNotBlank()
            ) {
                Text("Send Write")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatUuidToCommonName(uuid: UUID): String {
    val hex = uuid.toString().lowercase()
    return when {
        hex.startsWith("00001800") -> "Generic Access"
        hex.startsWith("00001801") -> "Generic Attribute"
        hex.startsWith("0000180a") -> "Device Information"
        hex.startsWith("0000180f") -> "Battery Service"
        hex.startsWith("0000180d") -> "Heart Rate Service"
        hex.startsWith("00002a00") -> "Device Name"
        hex.startsWith("00002a19") -> "Battery Level"
        hex.startsWith("00002a37") -> "Heart Rate Measurement"
        hex.startsWith("0000ffe0") -> "Custom Echo Service"
        hex.startsWith("0000ffe1") -> "Echo Read/Write Char"
        hex.startsWith("0000ffe2") -> "Echo Telemetry Char"
        else -> hex.take(8)
    }
}

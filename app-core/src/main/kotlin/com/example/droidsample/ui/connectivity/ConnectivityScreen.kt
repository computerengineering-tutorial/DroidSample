package com.example.droidsample.ui.connectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.droidsample.connectivity.BleGattClientManager
import com.example.droidsample.connectivity.BleGattServerManager
import com.example.droidsample.connectivity.BleScannerManager
import com.example.droidsample.connectivity.BluetoothClassicManager
import com.example.droidsample.connectivity.CompanionDeviceHelper
import com.example.droidsample.connectivity.UsbHostManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectivityScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val classicManager = remember { BluetoothClassicManager(context.applicationContext) }
    val scannerManager = remember { BleScannerManager(context.applicationContext) }
    val gattManager = remember { BleGattClientManager(context.applicationContext) }
    val serverManager = remember { BleGattServerManager(context.applicationContext) }
    val usbManager = remember { UsbHostManager(context.applicationContext) }
    val companionHelper = remember { CompanionDeviceHelper(context.applicationContext) }

    DisposableEffect(Unit) {
        onDispose {
            classicManager.unregisterReceiver()
            scannerManager.stopScan()
            gattManager.close()
            serverManager.stopServer()
            usbManager.unregisterReceiver()
        }
    }

    // Permission state
    var hasPermissions by remember { mutableStateOf(checkAllPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = checkAllPermissions(context)
        if (hasPermissions) {
            classicManager.refreshPairedDevices()
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "BLE Scanner" to Icons.AutoMirrored.Filled.BluetoothSearching,
        "GATT Explorer" to Icons.Default.Devices,
        "Classic BT" to Icons.Default.Bluetooth,
        "USB Host" to Icons.Default.Usb,
        "GATT Server / CDM" to Icons.Default.Watch
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Connectivity Hub", fontWeight = FontWeight.Bold) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Banner if missing
            if (!hasPermissions) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bluetooth & Location permissions required for scanning and GATT.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(getRequiredPermissions()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(imageVector = icon, contentDescription = title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> BleScannerView(
                        scannerManager = scannerManager,
                        onConnectDevice = { bleDevice ->
                            gattManager.connect(bleDevice.device)
                            selectedTabIndex = 1
                        }
                    )
                    1 -> GattExplorerView(gattManager = gattManager)
                    2 -> BluetoothClassicView(classicManager = classicManager)
                    3 -> UsbDevicesView(usbManager = usbManager)
                    4 -> GattServerAndCompanionTab(
                        serverManager = serverManager,
                        companionHelper = companionHelper
                    )
                }
            }
        }
    }
}

@Composable
fun GattServerAndCompanionTab(
    serverManager: BleGattServerManager,
    companionHelper: CompanionDeviceHelper,
    modifier: Modifier = Modifier
) {
    val isAdvertising by serverManager.isAdvertising.collectAsState()
    val isServerRunning by serverManager.isServerRunning.collectAsState()
    val connectedClients by serverManager.connectedDeviceCount.collectAsState()
    val logs by serverManager.serverLogs.collectAsState()

    val associations by companionHelper.associations.collectAsState()
    val companionStatus by companionHelper.statusMessage.collectAsState()

    var notifyMessage by remember { mutableStateOf("Sensor Reading #42") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: GATT Server Hosting & Advertising
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "BLE Peripheral (GATT Server & Advertiser)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hosts custom Service (0xFFE0) with Echo Read/Write (0xFFE1) & Telemetry (0xFFE2).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isServerRunning) {
                                    serverManager.stopServer()
                                } else {
                                    serverManager.startServer()
                                    serverManager.startAdvertising()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isServerRunning) "Stop Hosting" else "Start GATT Server")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Server: ${if (isServerRunning) "RUNNING" else "STOPPED"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Advertising: ${if (isAdvertising) "ACTIVE" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clients: $connectedClients",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isServerRunning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notifyMessage,
                            onValueChange = { notifyMessage = it },
                            label = { Text("Notify / Telemetry Payload") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FilledTonalButton(
                            onClick = { serverManager.sendNotification(notifyMessage) },
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send BLE Notification")
                        }
                    }
                }
            }
        }

        // Section 2: Companion Device Manager (CDM)
        item {
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
                        Column {
                            Text(
                                text = "Companion Device Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Associated wearables and fitness trackers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { companionHelper.refreshAssociations() }) {
                            Icon(imageVector = Icons.Default.Devices, contentDescription = null)
                        }
                    }

                    companionStatus?.let { status ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (associations.isEmpty()) {
                        Text(
                            text = "No associated companion devices found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        associations.forEach { assoc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = assoc.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = assoc.deviceAddress,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(onClick = { companionHelper.disassociateDevice(assoc) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Server Logs
        if (logs.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Server Activity Logs",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        logs.take(15).forEach { log ->
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
    }
}

private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

private fun checkAllPermissions(context: Context): Boolean {
    val perms = getRequiredPermissions()
    return perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

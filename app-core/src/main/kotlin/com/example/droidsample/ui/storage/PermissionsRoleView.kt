package com.example.droidsample.ui.storage

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.droidsample.storage.PermissionItem
import com.example.droidsample.storage.PermissionRoleManager
import com.example.droidsample.storage.RoleItem
import com.example.droidsample.storage.SpecialAccessItem

@Composable
fun PermissionsRoleView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { PermissionRoleManager(context) }

    var permissionsList by remember { mutableStateOf(manager.getPermissionsMatrix()) }
    var specialAccessList by remember { mutableStateOf(manager.getSpecialAppAccess()) }
    var rolesList by remember { mutableStateOf(manager.getRoles()) }
    var biometricStatus by remember { mutableStateOf(manager.checkBiometricStatus()) }
    var biometricResultMsg by remember { mutableStateOf<String?>(null) }

    val reloadAll = {
        permissionsList = manager.getPermissionsMatrix()
        specialAccessList = manager.getSpecialAppAccess()
        rolesList = manager.getRoles()
        biometricStatus = manager.checkBiometricStatus()
    }

    // Permission Request Launcher
    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        reloadAll()
    }

    // Role Request Launcher
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        reloadAll()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Biometric Authentication Section
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Biometric Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (biometricStatus.isAvailable) "Ready" else "Unavailable") },
                            colors = if (biometricStatus.isAvailable) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = biometricStatus.message, style = MaterialTheme.typography.bodySmall)

                    biometricResultMsg?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Result: $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                manager.authenticateWithBiometrics(
                                    activity = activity,
                                    onSuccess = { biometricResultMsg = "Biometric Authentication Succeeded!" },
                                    onError = { code, err -> biometricResultMsg = "Error ($code): $err" },
                                    onFailed = { biometricResultMsg = "Biometric validation failed." }
                                )
                            } else {
                                Toast.makeText(context, "Activity must inherit from FragmentActivity", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = biometricStatus.isAvailable
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Biometric Prompt")
                    }
                }
            }
        }

        // Special App Access
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Special App Access Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    specialAccessList.forEach { item ->
                        SpecialAccessRow(
                            item = item,
                            onRequest = {
                                try {
                                    val intent = Intent(item.settingsAction).apply {
                                        if (item.settingsUri != null) data = item.settingsUri
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(fallback)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }

        // System Roles (RoleManager API 29+)
        if (rolesList.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("System Roles (RoleManager)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        rolesList.forEach { role ->
                            RoleRow(
                                role = role,
                                onRequest = {
                                    val intent = manager.createRequestRoleIntent(role.roleName)
                                    if (intent != null) {
                                        roleLauncher.launch(intent)
                                    } else {
                                        Toast.makeText(context, "Role request not available", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }

        // Dangerous Permissions Matrix
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Runtime Dangerous Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { reloadAll() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        items(permissionsList) { perm ->
            PermissionItemRow(
                item = perm,
                onRequest = { singlePermLauncher.launch(perm.permission) }
            )
        }
    }
}

@Composable
private fun PermissionItemRow(item: PermissionItem, onRequest: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (item.isGranted) {
                AssistChip(
                    onClick = {},
                    label = { Text("Granted") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                FilledTonalButton(onClick = onRequest) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun SpecialAccessRow(item: SpecialAccessItem, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (item.isGranted) {
            AssistChip(
                onClick = {},
                label = { Text("Allowed") },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            OutlinedButton(onClick = onRequest) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun RoleRow(role: RoleItem, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = role.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = "Role: ${role.roleName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (role.isHeld) {
            AssistChip(
                onClick = {},
                label = { Text("Held") },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        } else if (role.isAvailable) {
            Button(onClick = onRequest) {
                Text("Request")
            }
        } else {
            Text("Unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

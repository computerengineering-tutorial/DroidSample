package com.example.droidsample.ui.calling

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.droidsample.calling.CallLogEntry
import com.example.droidsample.calling.CallLogRepository
import com.example.droidsample.calling.CallTriggerHelper
import com.example.droidsample.calling.CallType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallLogView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<CallType?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var rawLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(CallLogRepository.hasReadCallLogPermission(context)) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    fun refreshLogs() {
        coroutineScope.launch {
            isLoading = true
            val perm = CallLogRepository.hasReadCallLogPermission(context)
            hasPermission = perm
            if (perm) {
                CallLogRepository.queryCallLogs(context).onSuccess { list ->
                    rawLogs = list
                }.onFailure { e ->
                    rawLogs = emptyList()
                    Toast.makeText(context, "Error reading call logs: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                rawLogs = emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            Toast.makeText(context, "Permission granted. Reading real call logs...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "READ_CALL_LOG permission is required to view call logs.", Toast.LENGTH_SHORT).show()
        }
        refreshLogs()
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val numberToCall = pendingCallNumber
        pendingCallNumber = null
        if (granted && !numberToCall.isNullOrBlank()) {
            CallTriggerHelper.makeDirectCall(context, numberToCall).onFailure { error ->
                Toast.makeText(context, "Call failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (!granted) {
            Toast.makeText(context, "CALL_PHONE permission is required to place direct calls", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerCall: (String) -> Unit = { number ->
        if (number.isNotBlank()) {
            if (CallTriggerHelper.hasCallPhonePermission(context)) {
                CallTriggerHelper.makeDirectCall(context, number).onFailure { error ->
                    Toast.makeText(context, "Call failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingCallNumber = number
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        } else {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredLogs = remember(rawLogs, searchQuery, selectedTypeFilter) {
        CallLogRepository.filterEntries(rawLogs, searchQuery, selectedTypeFilter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Permission Banner if missing
        if (!hasPermission) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permission Notice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Grant READ_CALL_LOG to view real phone call history.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant")
                    }
                }
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name or number...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Type Filter Chips & Refresh Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("All") }
                    )
                }
                items(CallType.entries.toTypedArray()) { type ->
                    FilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = {
                            selectedTypeFilter = if (selectedTypeFilter == type) null else type
                        },
                        label = { Text(type.displayName) }
                    )
                }
            }

            IconButton(onClick = { refreshLogs() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Call Logs (${filteredLogs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (!hasPermission) "Grant READ_CALL_LOG permission to view device calls" else "No call logs found on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    if (!hasPermission) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    CallLogItemCard(
                        log = log,
                        onCallClick = {
                            triggerCall(log.number)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogItemCard(
    log: CallLogEntry,
    onCallClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = getCallTypeIconAndColor(log.type)

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = log.type.displayName,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (log.cachedName != null && log.cachedName.isNotBlank()) {
                    Text(
                        text = log.number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormatter.format(Date(log.dateEpochMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = log.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun getCallTypeIconAndColor(type: CallType): Pair<ImageVector, Color> {
    return when (type) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF2E7D32)
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF1565C0)
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFD32F2F)
        CallType.VOICEMAIL -> Icons.Default.Voicemail to Color(0xFFE65100)
        CallType.REJECTED -> Icons.Default.PhonePaused to Color(0xFFC2185B)
        CallType.BLOCKED -> Icons.Default.Block to Color(0xFF616161)
        CallType.OTHER -> Icons.AutoMirrored.Filled.PhoneCallback to Color(0xFF455A64)
    }
}

package com.example.droidsample.ui.calling

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.calling.CallScreeningRepository
import com.example.droidsample.calling.ScreenedCallLogEntry
import com.example.droidsample.calling.ScreeningDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallScreeningConfigView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rules by CallScreeningRepository.rules.collectAsState()
    val history by CallScreeningRepository.history.collectAsState()

    var newBlacklistNumber by remember { mutableStateOf("") }
    var testNumber by remember { mutableStateOf("") }
    var testName by remember { mutableStateOf("") }
    var simulatedDecision by remember { mutableStateOf<ScreeningDecision?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Shield Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Telecom Call Screening Shield",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Android CallScreeningService interceptor filters incoming spam before ringing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Screening Rules Toggles
        item {
            Text(
                text = "Screening Policies",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    RuleToggleRow(
                        title = "Block Spam Patterns",
                        subtitle = "Auto-reject numbers matching known toll-free spam patterns",
                        checked = rules.blockSpamNumbers,
                        onCheckedChange = { checked ->
                            CallScreeningRepository.updateRules { it.copy(blockSpamNumbers = checked) }
                        }
                    )
                    HorizontalDivider()
                    RuleToggleRow(
                        title = "Silence Unknown Callers",
                        subtitle = "Silence ringer if caller is not in your device contacts",
                        checked = rules.silenceUnknownNumbers,
                        onCheckedChange = { checked ->
                            CallScreeningRepository.updateRules { it.copy(silenceUnknownNumbers = checked) }
                        }
                    )
                    HorizontalDivider()
                    RuleToggleRow(
                        title = "Block Hidden / Private Caller IDs",
                        subtitle = "Reject calls with restricted or empty phone numbers",
                        checked = rules.blockUnknownNumbersWithoutCallerId,
                        onCheckedChange = { checked ->
                            CallScreeningRepository.updateRules { it.copy(blockUnknownNumbersWithoutCallerId = checked) }
                        }
                    )
                    HorizontalDivider()
                    RuleToggleRow(
                        title = "Skip Spam from Call Log",
                        subtitle = "Keep call history clean by not logging blocked spam",
                        checked = rules.skipCallLogInSpam,
                        onCheckedChange = { checked ->
                            CallScreeningRepository.updateRules { it.copy(skipCallLogInSpam = checked) }
                        }
                    )
                    HorizontalDivider()
                    RuleToggleRow(
                        title = "Skip Missed Call Notifications",
                        subtitle = "Do not show system notifications for filtered spam calls",
                        checked = rules.skipNotificationInSpam,
                        onCheckedChange = { checked ->
                            CallScreeningRepository.updateRules { it.copy(skipNotificationInSpam = checked) }
                        }
                    )
                }
            }
        }

        // Custom Blacklist
        item {
            Text(
                text = "Custom Blacklist (${rules.customBlacklist.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newBlacklistNumber,
                    onValueChange = { newBlacklistNumber = it },
                    placeholder = { Text("e.g. +14155550999") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newBlacklistNumber.isNotBlank()) {
                            CallScreeningRepository.addBlockedNumber(newBlacklistNumber)
                            newBlacklistNumber = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Block")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            rules.customBlacklist.forEach { num ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = num, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = { CallScreeningRepository.removeBlockedNumber(num) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Rule Evaluation Sandbox
        item {
            Text(
                text = "Test Rule Engine Sandbox",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = testNumber,
                        onValueChange = { testNumber = it },
                        label = { Text("Test Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testName,
                        onValueChange = { testName = it },
                        label = { Text("Caller Name (Leave empty to test unknown)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                simulatedDecision = CallScreeningRepository.evaluateCall(
                                    handleSchemeSpecificPart = testNumber,
                                    displayName = testName.ifBlank { null },
                                    rules = rules
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Evaluate Decision")
                        }

                        OutlinedButton(
                            onClick = {
                                val simulated = ScreenedCallLogEntry(
                                    handle = testNumber.ifBlank { "Private" },
                                    callerName = testName.ifBlank { null },
                                    isBlocked = simulatedDecision?.disallow ?: false,
                                    isSilenced = simulatedDecision?.silence ?: false,
                                    reason = simulatedDecision?.reason ?: "Manual simulation test"
                                )
                                CallScreeningRepository.recordScreenedCall(simulated)
                                Toast.makeText(context, "Logged to history!", Toast.LENGTH_SHORT).show()
                            },
                            enabled = simulatedDecision != null
                        ) {
                            Text("Log Event")
                        }
                    }

                    simulatedDecision?.let { decision ->
                        Spacer(modifier = Modifier.height(12.dp))
                        DecisionResultBanner(decision)
                    }
                }
            }
        }

        // Screened Calls History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screening Event History (${history.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (history.isNotEmpty()) {
                    IconButton(onClick = { CallScreeningRepository.clearHistory() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear History")
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "No screened calls recorded yet. Use the sandbox or receive incoming calls to test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(history, key = { it.id }) { entry ->
                ScreenedHistoryItemCard(entry)
            }
        }
    }
}

@Composable
private fun RuleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DecisionResultBanner(decision: ScreeningDecision) {
    val bgColor = when {
        decision.disallow -> MaterialTheme.colorScheme.errorContainer
        decision.silence -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        decision.disallow -> MaterialTheme.colorScheme.onErrorContainer
        decision.silence -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = when {
        decision.disallow -> Icons.Default.PhoneDisabled
        decision.silence -> Icons.AutoMirrored.Filled.VolumeOff
        else -> Icons.Default.CheckCircle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = decision.reason,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Disallow: ${decision.disallow} | Silence: ${decision.silence} | SkipLog: ${decision.skipCallLog} | SkipNotif: ${decision.skipNotification}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ScreenedHistoryItemCard(entry: ScreenedCallLogEntry) {
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
            val icon = when {
                entry.isBlocked -> Icons.Default.PhoneDisabled to MaterialTheme.colorScheme.error
                entry.isSilenced -> Icons.AutoMirrored.Filled.VolumeOff to Color(0xFFE65100)
                else -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
            }

            Icon(
                imageVector = icon.first,
                contentDescription = null,
                tint = icon.second,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!entry.callerName.isNullOrBlank()) "${entry.callerName} (${entry.handle})" else entry.handle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${entry.reason} • ${dateFormatter.format(Date(entry.timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

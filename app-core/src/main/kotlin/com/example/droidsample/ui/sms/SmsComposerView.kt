package com.example.droidsample.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.droidsample.sms.SimSubscription
import com.example.droidsample.sms.SmsDispatchRecord
import com.example.droidsample.sms.SmsSenderHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsComposerView(
    smsSenderHelper: SmsSenderHelper,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var recipient by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf<SimSubscription?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val simCards = remember { smsSenderHelper.getAvailableSimCards() }
    val dispatchHistory by smsSenderHelper.dispatchHistory.collectAsState()

    // Default to first SIM or default SIM if available
    LaunchedEffect(simCards) {
        if (selectedSim == null && simCards.isNotEmpty()) {
            selectedSim = simCards.find { it.isDefaultSms } ?: simCards.first()
        }
    }

    // Dynamic encoding calculation
    val encodingInfo = remember(messageText) {
        SmsSenderHelper.calculateSmsEncoding(messageText)
    }

    // Permission launcher for SEND_SMS & READ_PHONE_STATE
    var hasSendPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSendPermission = permissions[Manifest.permission.SEND_SMS] == true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SMS Dispatcher & Segment Calculator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Test standard GSM 7-bit, UCS-2 Unicode (Hindi, Emoji), multipart splitting, SIM slot routing, and delivery tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // SIM card selector row
        item {
            Text(
                text = "Select SIM Subscription",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (simCards.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Default System SIM (No multi-SIM detected or READ_PHONE_STATE ungranted)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(simCards) { sim ->
                        val isSelected = selectedSim?.subscriptionId == sim.subscriptionId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSim = sim },
                            label = {
                                Text("${sim.displayName} (${sim.carrierName})")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // Recipient field
        item {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("Recipient Phone Number") },
                placeholder = { Text("+15551234567") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Preset templates
        item {
            Text(
                text = "Preset Message Templates",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = {
                            messageText = "Hello from Android DroidSample! Testing standard 7-bit GSM SMS dispatch."
                        },
                        label = { Text("GSM 7-bit (1 segment)") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            messageText = "नमस्ते! एंड्रॉइड से यूनिकोड एसएमएस टेस्ट संदेश। (Hindi Unicode UCS-2)"
                        },
                        label = { Text("Unicode Hindi") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            messageText = "Rocket launch test! 🚀🛰️🌌 Traveling beyond Mars with full telemetry enabled! 🔥"
                        },
                        label = { Text("Emoji UCS-2") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            messageText = "This is a long multipart SMS message designed to exceed the standard 160 character boundary. In GSM 7-bit encoding, when a message exceeds 160 characters, it is automatically fragmented into individual segments of 153 characters each. The cellular modem injects a 6-byte User Data Header (UDH) in each segment to allow the receiving terminal to assemble the message transparently!"
                        },
                        label = { Text("Long Multipart (>160)") }
                    )
                }
            }
        }

        // Message text input
        item {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message Content") },
                placeholder = { Text("Type an SMS message...") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Realtime Encoding HUD & Segment Counter
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (encodingInfo.isUnicode)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (encodingInfo.isUnicode) Icons.Default.Language else Icons.Default.TextFields,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = encodingInfo.encodingName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "${encodingInfo.segmentCount} ${if (encodingInfo.segmentCount == 1) "Segment" else "Segments (Multipart)"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Characters: ${encodingInfo.characterCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Left in segment: ${encodingInfo.remainingInCurrentSegment} / ${encodingInfo.segmentLimit}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (encodingInfo.isUnicode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: UCS-2 (Unicode) reduces limit from 160 to 70 chars (or 67 chars/segment when multipart).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Action & Permission buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasSendPermission) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant SEND_SMS Permission")
                    }
                }

                Button(
                    onClick = {
                        if (recipient.isBlank()) {
                            statusMessage = "Please enter a valid recipient phone number"
                            isError = true
                            return@Button
                        }
                        if (messageText.isBlank()) {
                            statusMessage = "Please enter a message"
                            isError = true
                            return@Button
                        }

                        val result = smsSenderHelper.sendSms(
                            destinationAddress = recipient.trim(),
                            messageText = messageText,
                            subscriptionId = selectedSim?.subscriptionId
                        )

                        if (result.isSuccess) {
                            statusMessage = "Message dispatched to cellular network!"
                            isError = false
                        } else {
                            statusMessage = "Failed: ${result.exceptionOrNull()?.message}"
                            isError = true
                        }
                    },
                    enabled = hasSendPermission && recipient.isNotBlank() && messageText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send SMS (${encodingInfo.segmentCount} ${if (encodingInfo.segmentCount == 1) "part" else "parts"})")
                }

                statusMessage?.let { msg ->
                    Surface(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Dispatched history
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Outbox & Delivery Receipts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (dispatchHistory.isNotEmpty()) {
                    TextButton(onClick = { smsSenderHelper.clearHistory() }) {
                        Text("Clear")
                    }
                }
            }
        }

        if (dispatchHistory.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No dispatched SMS messages yet in this session.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(dispatchHistory) { record ->
                DispatchRecordItem(record)
            }
        }
    }
}

@Composable
fun DispatchRecordItem(record: SmsDispatchRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "To: ${record.address}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${record.segments} part(s)",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sent: ${record.sentStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Delivery: ${record.deliveryStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.deliveryStatus.contains("Delivered"))
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.example.droidsample.ui.calling

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.calling.ActiveVoipCall
import com.example.droidsample.calling.CallRecordingAuditReport
import com.example.droidsample.calling.CallRecordingAuditor
import com.example.droidsample.calling.TelecomManagerHelper
import com.example.droidsample.calling.VoipCallManager
import com.example.droidsample.calling.VoipState
import kotlinx.coroutines.delay

@Composable
fun VoipCallSimulatorView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeCall by VoipCallManager.currentCall.collectAsState()

    var isAccountRegistered by remember {
        mutableStateOf(TelecomManagerHelper.isPhoneAccountRegistered(context))
    }

    var auditReport by remember {
        mutableStateOf(CallRecordingAuditor.performAudit(context))
    }

    // Timer loop for active call duration
    LaunchedEffect(activeCall?.state) {
        while (activeCall?.state == VoipState.ACTIVE) {
            delay(1000)
            VoipCallManager.incrementDuration()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Call Banner or In-Call Interface
        if (activeCall != null) {
            item {
                ActiveCallInterface(
                    call = activeCall!!,
                    onAnswer = { VoipCallManager.answer() },
                    onReject = { VoipCallManager.reject() },
                    onDisconnect = { VoipCallManager.disconnect() },
                    onHoldToggle = {
                        val isHold = activeCall!!.state == VoipState.ON_HOLD
                        VoipCallManager.setHold(!isHold)
                    },
                    onSpeakerToggle = {
                        VoipCallManager.setSpeakerphone(!activeCall!!.isSpeakerOn)
                    },
                    onSendDtmf = { VoipCallManager.sendDtmf(it) }
                )
            }
        }

        // Telecom PhoneAccount Registration Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Telecom PhoneAccount Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Self-Managed PhoneAccount registers our AppConnectionService with Android Telecom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAccountRegistered) "Status: REGISTERED & ENABLED" else "Status: NOT REGISTERED",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAccountRegistered) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isAccountRegistered) {
                                Button(
                                    onClick = {
                                        TelecomManagerHelper.registerVoipPhoneAccount(context).onSuccess {
                                            isAccountRegistered = true
                                            Toast.makeText(context, "VoIP PhoneAccount Registered!", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Registration failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Register")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        TelecomManagerHelper.unregisterVoipPhoneAccount(context)
                                        isAccountRegistered = false
                                        Toast.makeText(context, "VoIP PhoneAccount Unregistered", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Unregister")
                                }
                            }
                        }
                    }
                }
            }
        }

        // VoIP Simulation Launcher
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Simulate VoIP Connections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Incoming VoIP Simulation
                        Button(
                            onClick = {
                                val testNum = "+1 (800) 424-9355"
                                val testName = "Apollo Central Dispatch"
                                TelecomManagerHelper.simulateIncomingVoipCall(context, testNum, testName).onFailure {
                                    // Fallback to local simulator if telecom permissions or simulator is restricted
                                    VoipCallManager.simulateLocalCall(testNum, testName, incoming = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Incoming Call")
                        }

                        // Outgoing VoIP Simulation
                        FilledTonalButton(
                            onClick = {
                                val targetNum = "+1 (555) 019-2834"
                                val targetName = "Client Operations"
                                TelecomManagerHelper.startOutgoingVoipCall(context, targetNum, targetName).onFailure {
                                    VoipCallManager.simulateLocalCall(targetNum, targetName, incoming = false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Outgoing Call")
                        }
                    }
                }
            }
        }

        // Audio & Call Recording Auditor Section
        item {
            CallRecordingAuditorCard(auditReport)
        }
    }
}

@Composable
private fun ActiveCallInterface(
    call: ActiveVoipCall,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onDisconnect: () -> Unit,
    onHoldToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSendDtmf: (Char) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = call.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = call.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(4.dp))

            val stateText = when (call.state) {
                VoipState.INITIALIZING -> "Initializing..."
                VoipState.RINGING -> "Incoming Call..."
                VoipState.DIALING -> "Dialing..."
                VoipState.ACTIVE -> {
                    val minutes = call.durationSeconds / 60
                    val seconds = call.durationSeconds % 60
                    String.format("%02d:%02d", minutes, seconds)
                }
                VoipState.ON_HOLD -> "Call On Hold"
                VoipState.DISCONNECTED -> call.disconnectReason ?: "Call Ended"
            }

            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (call.state == VoipState.ACTIVE) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
            )

            if (call.dtmfTones.isNotEmpty()) {
                Text(
                    text = "DTMF Dialed: ${call.dtmfTones}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            if (call.state == VoipState.RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Decline")
                    }

                    Button(
                        onClick = onAnswer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Answer")
                    }
                }
            } else if (call.state == VoipState.ACTIVE || call.state == VoipState.ON_HOLD || call.state == VoipState.DIALING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Speakerphone Toggle
                    IconButton(onClick = onSpeakerToggle) {
                        Icon(
                            imageVector = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Speaker",
                            tint = if (call.isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Hold / Resume
                    IconButton(onClick = onHoldToggle) {
                        Icon(
                            imageVector = if (call.state == VoipState.ON_HOLD) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Hold",
                            tint = if (call.state == VoipState.ON_HOLD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // End Call
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Hang Up")
                    }
                }

                // In-Call DTMF Pad
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf('1', '2', '3', '4', '5', '#').forEach { digit ->
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onSendDtmf(digit) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRecordingAuditorCard(audit: CallRecordingAuditReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cellular Call Recording Security Audit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = audit.summaryMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (audit.canRecordCellularCalls) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            audit.technicalDetails.forEach { detail ->
                Text(
                    text = "• $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Architectural Recommendation:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = audit.recommendedArchitecture,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

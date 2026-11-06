package com.example.droidsample.ui.calling

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.droidsample.calling.CallState
import com.example.droidsample.calling.CallStateInfo
import com.example.droidsample.calling.CallStateMonitor
import com.example.droidsample.calling.CallTriggerHelper
import com.example.droidsample.calling.InCallManager
import com.example.droidsample.calling.TelecomManagerHelper
import kotlinx.coroutines.flow.flowOf

@Composable
fun PhoneDialerView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputNumber by remember { mutableStateOf("") }

    val callStateMonitor = remember { CallStateMonitor(context) }
    val callStateInfo by remember {
        if (callStateMonitor.hasPhoneStatePermission()) {
            callStateMonitor.observeCallState()
        } else {
            flowOf(
                CallStateInfo(
                    state = CallState.Idle,
                    rawStateInt = 0,
                    description = "Phone state monitoring requires permission"
                )
            )
        }
    }.collectAsState(initial = CallStateInfo(CallState.Idle, 0, "Idle"))

    var toneGenerator by remember { mutableStateOf<ToneGenerator?>(null) }

    DisposableEffect(Unit) {
        runCatching {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
        }
        onDispose {
            toneGenerator?.release()
            toneGenerator = null
        }
    }

    val playDtmfTone: (Char) -> Unit = { digit ->
        val tone = when (digit) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        try {
            toneGenerator?.startTone(tone, 120)
        } catch (_: Exception) {}
    }

    var isDefaultDialer by remember { mutableStateOf(TelecomManagerHelper.isDefaultDialer(context)) }

    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = TelecomManagerHelper.isDefaultDialer(context)
        if (isDefaultDialer) {
            Toast.makeText(context, "App set as Default Phone App! Custom in-call screen active.", Toast.LENGTH_SHORT).show()
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (inputNumber.isNotBlank()) {
                CallTriggerHelper.makeDirectCall(context, inputNumber).onFailure {
                    Toast.makeText(context, "Error placing direct call: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "CALL_PHONE permission is required for direct calling", Toast.LENGTH_LONG).show()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Default Phone App Role Status & In-Call Screen Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDefaultDialer) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isDefaultDialer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDefaultDialer) "Default Phone App (Active)" else "Default Phone App Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isDefaultDialer) {
                        Button(
                            onClick = {
                                val intent = TelecomManagerHelper.createDefaultDialerIntent(context)
                                if (intent != null) {
                                    defaultDialerLauncher.launch(intent)
                                } else {
                                    Toast.makeText(context, "RoleManager unavailable on this device", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Set Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text(
                    text = if (isDefaultDialer) {
                        "All incoming & outgoing cellular calls will display this app's custom in-call phone screen."
                    } else {
                        "Without setting this app as Default Phone App, Android delegates the in-call screen to the system dialer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Demo preview button to test custom In-Call UI
                OutlinedButton(
                    onClick = {
                        val num = inputNumber.ifBlank { "+1 (555) 019-2834" }
                        InCallManager.startSimulatedCall(context, num, "Test In-Call Display", false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Custom In-Call Screen", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Telephony Status Header
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = when (callStateInfo.state) {
                        is CallState.Idle -> MaterialTheme.colorScheme.primary
                        is CallState.Ringing -> MaterialTheme.colorScheme.error
                        is CallState.Offhook -> MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Device Line State",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = callStateInfo.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phone Number Display Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (inputNumber.isEmpty()) "Enter phone number" else inputNumber,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (inputNumber.length > 12) 22.sp else 28.sp
                ),
                color = if (inputNumber.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (inputNumber.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (inputNumber.isNotEmpty()) {
                            inputNumber = inputNumber.dropLast(1)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Keypad Grid
        val dialpadRows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        dialpadRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DialpadButton(
                        digit = digit,
                        letters = letters,
                        onClick = {
                            playDtmfTone(digit.first())
                            inputNumber += digit
                        },
                        onLongClick = {
                            if (digit == "0") {
                                playDtmfTone('+')
                                inputNumber += "+"
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Calling Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ACTION_DIAL (Safe)
            FilledTonalButton(
                onClick = {
                    if (inputNumber.isNotBlank()) {
                        CallTriggerHelper.openDialer(context, inputNumber).onFailure {
                            Toast.makeText(context, "Could not open dialer: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Please enter a phone number first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Dialer")
            }

            // ACTION_CALL (Direct Call)
            Button(
                onClick = {
                    if (inputNumber.isNotBlank()) {
                        if (CallTriggerHelper.hasCallPhonePermission(context)) {
                            CallTriggerHelper.makeDirectCall(context, inputNumber).onFailure {
                                Toast.makeText(context, "Call failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    } else {
                        Toast.makeText(context, "Please enter a phone number first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Direct Call")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DialpadButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}

package com.example.droidsample.calling

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.theme.DroidSampleTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show on lock screen when receiving an active call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContent {
            DroidSampleTheme {
                InCallScreen(
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun InCallScreen(
    onFinish: () -> Unit
) {
    val callInfo by InCallManager.currentCall.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }

    // Duration timer for active calls
    LaunchedEffect(callInfo?.state) {
        if (callInfo?.state == InCallSessionState.ACTIVE) {
            while (true) {
                delay(1000)
                InCallManager.incrementDuration()
            }
        } else if (callInfo?.state == InCallSessionState.DISCONNECTED) {
            delay(1500)
            onFinish()
        }
    }

    // Auto-finish if no call exists
    LaunchedEffect(callInfo) {
        if (callInfo == null) {
            onFinish()
        }
    }

    val call = callInfo ?: return

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1C23),
            Color(0xFF0F1015),
            Color(0xFF08080C)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // Top status and caller info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Caller Avatar Circle
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = call.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = call.number.ifBlank { "Private Number" },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // State Badge / Call Duration
            val stateText = when (call.state) {
                InCallSessionState.RINGING -> if (call.isIncoming) "Incoming Call..." else "Ringing..."
                InCallSessionState.DIALING -> "Dialing..."
                InCallSessionState.CONNECTING -> "Connecting..."
                InCallSessionState.ACTIVE -> {
                    val m = call.durationSeconds / 60
                    val s = call.durationSeconds % 60
                    String.format("%02d:%02d", m, s)
                }
                InCallSessionState.ON_HOLD -> "Call on Hold"
                InCallSessionState.DISCONNECTED -> call.disconnectCause ?: "Call Ended"
                InCallSessionState.IDLE -> ""
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when (call.state) {
                    InCallSessionState.ACTIVE -> Color(0xFF1E3A2F)
                    InCallSessionState.RINGING -> Color(0xFF3A2B1E)
                    InCallSessionState.ON_HOLD -> Color(0xFF332A1E)
                    InCallSessionState.DISCONNECTED -> Color(0xFF3A1E1E)
                    else -> Color(0xFF1E283A)
                }
            ) {
                Text(
                    text = stateText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (call.state) {
                        InCallSessionState.ACTIVE -> Color(0xFF4CAF50)
                        InCallSessionState.RINGING -> Color(0xFFFFB74D)
                        InCallSessionState.ON_HOLD -> Color(0xFFFFCC80)
                        InCallSessionState.DISCONNECTED -> Color(0xFFEF5350)
                        else -> Color(0xFF64B5F6)
                    }
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // When in active or dialing call: show in-call action buttons (mute, hold, speaker, keypad)
            if (call.state != InCallSessionState.RINGING && call.state != InCallSessionState.DISCONNECTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallActionButton(
                        icon = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (call.isMuted) "Unmute" else "Mute",
                        isActive = call.isMuted,
                        onClick = { InCallManager.setMute(!call.isMuted) }
                    )

                    InCallActionButton(
                        icon = Icons.Default.Dialpad,
                        label = "Keypad",
                        isActive = showKeypad,
                        onClick = { showKeypad = !showKeypad }
                    )

                    InCallActionButton(
                        icon = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                        label = "Speaker",
                        isActive = call.isSpeakerOn,
                        onClick = { InCallManager.setSpeakerphone(!call.isSpeakerOn) }
                    )

                    InCallActionButton(
                        icon = if (call.state == InCallSessionState.ON_HOLD) Icons.Default.PlayArrow else Icons.Default.Pause,
                        label = if (call.state == InCallSessionState.ON_HOLD) "Resume" else "Hold",
                        isActive = call.state == InCallSessionState.ON_HOLD,
                        onClick = { InCallManager.toggleHold() }
                    )
                }
            }

            // Call Action Buttons (Answer / End / Reject)
            if (call.state == InCallSessionState.RINGING && call.isIncoming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { InCallManager.rejectCall() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline Call", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    // Answer Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { InCallManager.answerCall() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer Call", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Answer", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (call.state != InCallSessionState.DISCONNECTED) {
                // End Call Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { InCallManager.disconnectCall() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("End Call", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Keypad overlay if toggled
        if (showKeypad) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E2028).copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("In-Call DTMF Keypad", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showKeypad = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Keypad", tint = Color.White)
                        }
                    }

                    val dtmfPad = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    )

                    dtmfPad.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                OutlinedButton(
                                    onClick = { InCallManager.playDtmf(digit) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Text(digit.toString(), fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InCallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

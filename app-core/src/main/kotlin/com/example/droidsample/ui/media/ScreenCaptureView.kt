package com.example.droidsample.ui.media

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.media.ScreenRecorderManager
import com.example.droidsample.media.ScreenRecorderManager.ScreenConfig
import com.example.droidsample.media.ScreenRecorderManager.ScreenRecordState
import java.util.Locale

@Composable
fun ScreenCaptureView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { ScreenRecorderManager(context) }
    val recordState by manager.recordingState.collectAsState()

    var selectedResolution by remember { mutableStateOf("1080p") }
    var selectedFps by remember { mutableIntStateOf(30) }
    var selectedBitrateMbps by remember { mutableFloatStateOf(8f) }
    var enableAudio by remember { mutableStateOf(false) }

    val isRecording = recordState is ScreenRecordState.Recording

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val recConfig = manager.getRecommendedConfig()
            val (w, h) = if (selectedResolution == "720p") {
                Pair(720, 1280)
            } else {
                Pair(1080, 1920)
            }
            val config = ScreenConfig(
                width = w,
                height = h,
                densityDpi = recConfig.densityDpi,
                frameRate = selectedFps,
                bitrate = (selectedBitrateMbps * 1_000_000).toInt(),
                enableAudio = enableAudio
            )
            manager.startRecording(result.resultCode, result.data!!, config)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRecording) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    val recording = recordState as ScreenRecordState.Recording
                    val mins = recording.durationSeconds / 60
                    val secs = recording.durationSeconds % 60
                    val timeString = String.format(Locale.US, "%02d:%02d", mins, secs)

                    val infiniteTransition = rememberInfiniteTransition(label = "screen_pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Text(
                            text = "RECORDING: $timeString",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Output: ${recording.outputFile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { manager.stopRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.StopScreenShare, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Screen Recording")
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Screen Recording Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hardware-accelerated VirtualDisplay capture with MediaProjection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { captureLauncher.launch(manager.createScreenCaptureIntent()) },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ScreenShare, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Screen Capture")
                    }
                }
            }
        }

        // Configuration Panel
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Recording Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Resolution options
                Column {
                    Text("Target Resolution", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720p", "1080p").forEach { res ->
                            FilterChip(
                                selected = selectedResolution == res,
                                onClick = { selectedResolution = res },
                                label = { Text(res) },
                                enabled = !isRecording
                            )
                        }
                    }
                }

                // Frame rate
                Column {
                    Text("Target Frame Rate", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = selectedFps == fps,
                                onClick = { selectedFps = fps },
                                label = { Text("$fps FPS") },
                                enabled = !isRecording
                            )
                        }
                    }
                }

                // Bitrate slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Video Bitrate", style = MaterialTheme.typography.labelLarge)
                        Text("${selectedBitrateMbps.toInt()} Mbps", fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = selectedBitrateMbps,
                        onValueChange = { selectedBitrateMbps = it },
                        valueRange = 2f..20f,
                        steps = 8,
                        enabled = !isRecording
                    )
                }

                // Audio toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (enableAudio) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null
                        )
                        Column {
                            Text("Microphone Audio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Mix ambient / voice commentary", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = enableAudio,
                        onCheckedChange = { enableAudio = it },
                        enabled = !isRecording
                    )
                }
            }
        }

        // Completion Card
        AnimatedVisibility(visible = recordState is ScreenRecordState.Completed) {
            val completed = recordState as? ScreenRecordState.Completed
            if (completed != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Screen Recording Saved",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Path: ${completed.outputFile.absolutePath}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            Text(
                                text = "Duration: ${completed.durationSeconds}s | Size: ${completed.outputFile.length() / 1024} KB",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // Error Card
        AnimatedVisibility(visible = recordState is ScreenRecordState.Error) {
            val err = recordState as? ScreenRecordState.Error
            if (err != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = err.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

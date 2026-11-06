package com.example.droidsample.ui.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droidsample.media.CameraVideoRecorder
import com.example.droidsample.media.CameraVideoRecorder.RecordingStatus
import com.example.droidsample.media.CameraVideoRecorder.TargetQuality
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCaptureView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { CameraVideoRecorder(context) }

    val recordingState by recorder.recordingState.collectAsState()
    val isAudioEnabled by recorder.isAudioEnabled.collectAsState()
    val selectedQuality by recorder.selectedQuality.collectAsState()
    val lensFacing by recorder.lensFacing.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Grant camera and audio permissions to record videos with CameraX.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Video Preview View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        recorder.bindToLifecycle(lifecycleOwner, this)
                    }
                },
                update = { previewView ->
                    recorder.bindToLifecycle(lifecycleOwner, previewView)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar Overlay: Quality Selector & Audio Toggle & Switch Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quality Dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.width(150.dp)
                ) {
                    OutlinedTextField(
                        value = selectedQuality.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        textStyle = MaterialTheme.typography.labelMedium
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        TargetQuality.values().forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality.label) },
                                onClick = {
                                    recorder.setSelectedQuality(quality)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Audio toggle
                    FilledTonalIconButton(
                        onClick = { recorder.setAudioEnabled(!isAudioEnabled) }
                    ) {
                        Icon(
                            imageVector = if (isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isAudioEnabled) "Audio Enabled" else "Audio Muted",
                            tint = if (isAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    // Flip camera
                    FilledTonalIconButton(
                        onClick = { recorder.toggleCameraLens() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera"
                        )
                    }
                }
            }

            // Duration and State Badge Overlay
            val isRecordingActive = recordingState is RecordingStatus.Recording
            val isPaused = recordingState is RecordingStatus.Paused

            if (isRecordingActive || isPaused) {
                val durationSec = when (val s = recordingState) {
                    is RecordingStatus.Recording -> s.durationSeconds
                    is RecordingStatus.Paused -> s.durationSeconds
                    else -> 0L
                }

                val mins = durationSec / 60
                val secs = durationSec % 60
                val timeString = String.format(Locale.US, "%02d:%02d", mins, secs)

                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(if (isRecordingActive) scale else 1f)
                                .clip(CircleShape)
                                .background(if (isPaused) Color.Yellow else Color.Red)
                        )

                        Text(
                            text = if (isPaused) "PAUSED $timeString" else "REC $timeString",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Controls Deck
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (recordingState) {
                        RecordingStatus.Idle, is RecordingStatus.Completed, is RecordingStatus.Error -> {
                            // Start Record Button
                            IconButton(
                                onClick = { recorder.startRecording(saveToMediaStore = true) },
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                            }
                        }

                        is RecordingStatus.Starting -> {
                            Text("Initializing recorder...")
                        }

                        is RecordingStatus.Recording -> {
                            // Pause button
                            FilledTonalIconButton(
                                onClick = { recorder.pauseRecording() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }

                            // Stop button
                            IconButton(
                                onClick = { recorder.stopRecording() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(4.dp, MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                )
                            }
                        }

                        is RecordingStatus.Paused -> {
                            // Resume button
                            FilledTonalIconButton(
                                onClick = { recorder.resumeRecording() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }

                            // Stop button
                            IconButton(
                                onClick = { recorder.stopRecording() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(4.dp, MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                )
                            }
                        }
                    }
                }

                // Completion status banner
                AnimatedVisibility(visible = recordingState is RecordingStatus.Completed) {
                    val completed = recordingState as? RecordingStatus.Completed
                    if (completed != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
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
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Recording Saved Successfully",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Duration: ${completed.durationSeconds}s | Size: ${completed.bytesRecorded / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    completed.outputUri?.let { uri ->
                                        Text(
                                            text = "URI: $uri",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Error status banner
                AnimatedVisibility(visible = recordingState is RecordingStatus.Error) {
                    val err = recordingState as? RecordingStatus.Error
                    if (err != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = err.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

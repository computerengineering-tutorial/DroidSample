package com.example.droidsample.ui.audio

import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.audio.MediaAudioRecorder
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRecorderView(
    recorder: MediaAudioRecorder,
    modifier: Modifier = Modifier
) {
    val stats by recorder.stats.collectAsState()
    val state by recorder.state.collectAsState()
    val context = LocalContext.current

    // Recording configuration states
    var selectedSource by remember { mutableIntStateOf(MediaRecorder.AudioSource.MIC) }
    var selectedFormat by remember { mutableIntStateOf(MediaRecorder.OutputFormat.MPEG_4) }
    var selectedEncoder by remember { mutableIntStateOf(MediaRecorder.AudioEncoder.AAC) }
    var sampleRate by remember { mutableIntStateOf(44100) }
    var bitRate by remember { mutableIntStateOf(128000) }
    var channels by remember { mutableIntStateOf(1) }

    // Playback state
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: ${state.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (state) {
                            MediaAudioRecorder.State.RECORDING -> MaterialTheme.colorScheme.error
                            MediaAudioRecorder.State.PAUSED -> MaterialTheme.colorScheme.tertiary
                            MediaAudioRecorder.State.STOPPED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    val seconds = stats.durationMs / 1000
                    val minutes = seconds / 60
                    Text(
                        text = String.format(Locale.US, "%02d:%02d.%01d", minutes, seconds % 60, (stats.durationMs % 1000) / 100),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Peak dB Meter
                Text(
                    text = "Peak Level: ${String.format(Locale.US, "%.1f", stats.currentPeakDb)} dBFS",
                    style = MaterialTheme.typography.bodySmall
                )
                val progress = ((stats.currentPeakDb + 60f) / 60f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (stats.currentPeakDb > -3f) Color.Red else MaterialTheme.colorScheme.primary
                )

                if (stats.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stats.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                MediaAudioRecorder.State.IDLE, MediaAudioRecorder.State.STOPPED, MediaAudioRecorder.State.ERROR -> {
                    Button(
                        onClick = {
                            val config = MediaAudioRecorder.Config(
                                audioSource = selectedSource,
                                outputFormat = selectedFormat,
                                audioEncoder = selectedEncoder,
                                sampleRate = sampleRate,
                                bitRate = bitRate,
                                channels = channels
                            )
                            recorder.prepare(config)
                            recorder.start()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record")
                    }
                }
                MediaAudioRecorder.State.RECORDING -> {
                    Button(
                        onClick = { recorder.pause() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                    Button(
                        onClick = { recorder.stop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
                MediaAudioRecorder.State.PAUSED -> {
                    Button(
                        onClick = { recorder.resume() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                    Button(
                        onClick = { recorder.stop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
                MediaAudioRecorder.State.INITIALIZED -> {
                    Button(
                        onClick = { recorder.start() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                }
            }
        }

        // Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                // Audio Source Dropdown
                SourceDropdown(selected = selectedSource, onSelected = { selectedSource = it })

                // Output Format Dropdown
                FormatDropdown(selected = selectedFormat, onSelected = { selectedFormat = it })

                // Audio Encoder Dropdown
                EncoderDropdown(selected = selectedEncoder, onSelected = { selectedEncoder = it })

                // Sample Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sample Rate: $sampleRate Hz")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { sampleRate = 16000 }) { Text("16k") }
                        OutlinedButton(onClick = { sampleRate = 44100 }) { Text("44.1k") }
                        OutlinedButton(onClick = { sampleRate = 48000 }) { Text("48k") }
                    }
                }

                // Bitrate Slider
                Text("Bitrate: ${bitRate / 1000} kbps")
                Slider(
                    value = bitRate.toFloat(),
                    onValueChange = { bitRate = it.toInt() },
                    valueRange = 12000f..320000f,
                    steps = 10
                )

                // Channels Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Channels: ${if (channels == 1) "Mono" else "Stereo"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { channels = 1 }) { Text("Mono (1)") }
                        OutlinedButton(onClick = { channels = 2 }) { Text("Stereo (2)") }
                    }
                }
            }
        }

        // Output File Preview & Player Card
        val lastFile = stats.outputFile
        if (lastFile != null && lastFile.exists()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recorded File Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lastFile.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Size: ${lastFile.length() / 1024} KB",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                } else {
                                    try {
                                        mediaPlayer = MediaPlayer().apply {
                                            setDataSource(lastFile.absolutePath)
                                            prepare()
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                            start()
                                        }
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        isPlaying = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Stop Player" else "Play Recording")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val sources = listOf(
        MediaRecorder.AudioSource.MIC to "MIC (Built-in)",
        MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
        MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
        MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
        MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED (Raw)",
        MediaRecorder.AudioSource.VOICE_PERFORMANCE to "VOICE_PERFORMANCE"
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = sources.firstOrNull { it.first == selected }?.second ?: "Source $selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Audio Source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sources.forEach { (sourceId, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(sourceId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val formats = listOf(
        MediaRecorder.OutputFormat.MPEG_4 to "MPEG-4 (.m4a)",
        MediaRecorder.OutputFormat.THREE_GPP to "3GPP (.3gp)",
        MediaRecorder.OutputFormat.AAC_ADTS to "AAC ADTS (.aac)",
        MediaRecorder.OutputFormat.AMR_NB to "AMR-NB (.amr)",
        MediaRecorder.OutputFormat.AMR_WB to "AMR-WB (.awb)",
        MediaRecorder.OutputFormat.OGG to "OGG (.ogg)"
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = formats.firstOrNull { it.first == selected }?.second ?: "Format $selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Container Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            formats.forEach { (formatId, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(formatId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncoderDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val encoders = listOf(
        MediaRecorder.AudioEncoder.AAC to "AAC (Advanced Audio Coding)",
        MediaRecorder.AudioEncoder.AAC_ELD to "AAC-ELD (Enhanced Low Delay)",
        MediaRecorder.AudioEncoder.HE_AAC to "HE-AAC (High Efficiency)",
        MediaRecorder.AudioEncoder.AMR_NB to "AMR-NB (Narrowband)",
        MediaRecorder.AudioEncoder.AMR_WB to "AMR-WB (Wideband)",
        MediaRecorder.AudioEncoder.OPUS to "Opus"
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = encoders.firstOrNull { it.first == selected }?.second ?: "Encoder $selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Audio Encoder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            encoders.forEach { (encoderId, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(encoderId)
                        expanded = false
                    }
                )
            }
        }
    }
}

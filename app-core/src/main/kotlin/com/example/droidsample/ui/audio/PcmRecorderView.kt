package com.example.droidsample.ui.audio

import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.audio.AudioAnalyzer
import com.example.droidsample.audio.PcmAudioRecorder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcmRecorderView(
    pcmRecorder: PcmAudioRecorder,
    modifier: Modifier = Modifier
) {
    val stats by pcmRecorder.stats.collectAsState()
    val state by pcmRecorder.state.collectAsState()
    val metrics by pcmRecorder.audioMetricsFlow.collectAsState(
        initial = AudioAnalyzer.AudioMetrics(0f, 0f, -100f, FloatArray(100), FloatArray(8))
    )

    // Configuration selections
    var selectedSampleRate by remember { mutableIntStateOf(44100) }
    var selectedEncoding by remember { mutableIntStateOf(AudioFormat.ENCODING_PCM_16BIT) }
    var selectedChannels by remember { mutableIntStateOf(AudioFormat.CHANNEL_IN_MONO) }
    var selectedSource by remember { mutableIntStateOf(MediaRecorder.AudioSource.MIC) }

    // Playback
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
        // Live Oscilloscope Screen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101418))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE OSCILLOSCOPE (PCM)",
                        color = Color(0xFF00FFCC),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedSampleRate} Hz | ${if (selectedChannels == AudioFormat.CHANNEL_IN_STEREO) "Stereo" else "Mono"}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OscilloscopeCanvas(
                    waveform = metrics.downsampledWaveform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // Live VU Meter & Peak Level
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RMS Level: ${String.format(Locale.US, "%.3f", metrics.rms)}", style = MaterialTheme.typography.bodySmall)
                    Text("Peak: ${String.format(Locale.US, "%.1f", metrics.dbFs)} dBFS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                VuMeterCanvas(
                    rms = metrics.rms,
                    peakDb = metrics.dbFs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }

        // Live 8-Band Frequency Spectrum Visualizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141923))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "FFT SPECTRUM BANDS",
                    color = Color(0xFFFF9900),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                SpectrumBarsCanvas(
                    frequencyBands = metrics.frequencyBands,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // Stream Status & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state == PcmAudioRecorder.State.RECORDING) {
                Button(
                    onClick = { pcmRecorder.stopRecording() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop PCM Stream")
                }
            } else {
                Button(
                    onClick = {
                        val config = PcmAudioRecorder.Config(
                            audioSource = selectedSource,
                            sampleRate = selectedSampleRate,
                            channelConfig = selectedChannels,
                            audioEncoding = selectedEncoding
                        )
                        pcmRecorder.startRecording(config)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start PCM Stream")
                }
            }
        }

        // Configuration Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("AudioRecord Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Sample Rate Dropdown
                SampleRateDropdown(
                    selected = selectedSampleRate,
                    onSelected = { selectedSampleRate = it }
                )

                // Encoding Dropdown
                EncodingDropdown(
                    selected = selectedEncoding,
                    onSelected = { selectedEncoding = it }
                )

                // Channel Dropdown
                ChannelDropdown(
                    selected = selectedChannels,
                    onSelected = { selectedChannels = it }
                )
            }
        }

        // Recorded WAV Playback Preview
        val lastFile = stats.outputFile
        if (lastFile != null && lastFile.exists()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Captured PCM / WAV File",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = lastFile.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Size: ${lastFile.length() / 1024} KB (${stats.totalBytesRecorded} bytes)", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (lastFile.extension == "wav") {
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
                                            setOnCompletionListener { isPlaying = false }
                                            start()
                                        }
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        isPlaying = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Stop WAV Playback" else "Play Generated WAV")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OscilloscopeCanvas(waveform: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw center reference line
        drawLine(
            color = Color(0xFF253342),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (waveform.isEmpty()) return@Canvas

        val path = Path()
        val stepX = width / (waveform.size - 1).coerceAtLeast(1)

        for (i in waveform.indices) {
            val x = i * stepX
            val y = centerY - (waveform[i] * (height / 2f) * 0.9f)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF00FFCC),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun VuMeterCanvas(rms: Float, peakDb: Float, modifier: Modifier = Modifier) {
    val animatedRms by animateFloatAsState(targetValue = rms.coerceIn(0f, 1f), label = "vuRms")

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Background track
        drawRect(
            color = Color(0xFF222222),
            size = Size(width, height)
        )

        // RMS gradient bar (green -> yellow -> red)
        val fillWidth = width * animatedRms
        val gradient = Brush.horizontalGradient(
            0.0f to Color(0xFF00E676),
            0.7f to Color(0xFFFFD600),
            1.0f to Color(0xFFFF1744)
        )
        drawRect(
            brush = gradient,
            size = Size(fillWidth, height)
        )

        // Peak dB indicator tick
        val peakNormalized = ((peakDb + 60f) / 60f).coerceIn(0f, 1f)
        val peakX = width * peakNormalized
        drawLine(
            color = Color.White,
            start = Offset(peakX, 0f),
            end = Offset(peakX, height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun SpectrumBarsCanvas(frequencyBands: FloatArray, modifier: Modifier = Modifier) {
    val labels = listOf("Sub", "Bass", "LoMid", "Mid", "HiMid", "Pres", "Brill", "Ultra")

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val numBars = frequencyBands.size.coerceAtLeast(1)
        val barSpacing = 8.dp.toPx()
        val totalSpacing = barSpacing * (numBars + 1)
        val barWidth = (width - totalSpacing) / numBars

        for (i in frequencyBands.indices) {
            val mag = frequencyBands[i].coerceIn(0f, 1f)
            val barHeight = height * mag
            val left = barSpacing + i * (barWidth + barSpacing)
            val top = height - barHeight

            val gradient = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF1744), Color(0xFFFF9900), Color(0xFF00E676)),
                startY = top,
                endY = height
            )

            drawRect(
                brush = gradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleRateDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val rates = listOf(8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000, 192000)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "$selected Hz",
            onValueChange = {},
            readOnly = true,
            label = { Text("Sample Rate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            rates.forEach { rate ->
                DropdownMenuItem(
                    text = { Text("$rate Hz") },
                    onClick = {
                        onSelected(rate)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncodingDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val encodings = listOf(
        AudioFormat.ENCODING_PCM_16BIT to "16-Bit Signed Integer (Default)",
        AudioFormat.ENCODING_PCM_FLOAT to "32-Bit Float PCM (Direct)",
        AudioFormat.ENCODING_PCM_8BIT to "8-Bit Unsigned Integer"
    )
    var expanded by remember { mutableStateOf(false) }
    val label = encodings.firstOrNull { it.first == selected }?.second ?: "Format $selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("PCM Audio Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            encodings.forEach { (enc, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(enc)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val channels = listOf(
        AudioFormat.CHANNEL_IN_MONO to "Mono (1 Channel)",
        AudioFormat.CHANNEL_IN_STEREO to "Stereo (2 Channels)"
    )
    var expanded by remember { mutableStateOf(false) }
    val label = channels.firstOrNull { it.first == selected }?.second ?: "Channels $selected"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Channel Configuration") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            channels.forEach { (ch, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(ch)
                        expanded = false
                    }
                )
            }
        }
    }
}

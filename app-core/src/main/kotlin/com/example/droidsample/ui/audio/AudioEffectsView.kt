package com.example.droidsample.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.audio.AudioEffectsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsView(
    effectsManager: AudioEffectsManager,
    activeAudioSessionId: Int = 0,
    modifier: Modifier = Modifier
) {
    val state by effectsManager.effectsState.collectAsState()
    val scrollState = rememberScrollState()

    var targetSessionId by remember { mutableIntStateOf(activeAudioSessionId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Session Attachment Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Audio Session Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Active Session: ${if (activeAudioSessionId > 0) "#$activeAudioSessionId (PCM Recorder)" else "0 (Global Mix)"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { effectsManager.attachToSession(activeAudioSessionId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Attach Effects to Active Session ($activeAudioSessionId)")
                }
            }
        }

        // Capture Preprocessing Effects (AEC, NS, AGC)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hardware Capture Preprocessing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Acoustic Echo Canceler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acoustic Echo Canceler (AEC)", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (state.isAecAvailable) "Supported by hardware DSP" else "Not supported on device",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.isAecEnabled,
                        onCheckedChange = { effectsManager.setAecEnabled(it) },
                        enabled = state.isAecAvailable
                    )
                }

                // Noise Suppressor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Noise Suppressor (NS)", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (state.isNsAvailable) "Supported by hardware DSP" else "Not supported on device",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.isNsEnabled,
                        onCheckedChange = { effectsManager.setNsEnabled(it) },
                        enabled = state.isNsAvailable
                    )
                }

                // Automatic Gain Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Gain Control (AGC)", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (state.isAgcAvailable) "Supported by hardware DSP" else "Not supported on device",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.isAgcEnabled,
                        onCheckedChange = { effectsManager.setAgcEnabled(it) },
                        enabled = state.isAgcAvailable
                    )
                }
            }
        }

        // Equalizer Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Equalizer Console",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (state.equalizerPresets.isNotEmpty()) {
                    var presetExpanded by remember { mutableStateOf(false) }
                    val currentPresetName = if (state.currentPresetIndex >= 0 && state.currentPresetIndex < state.equalizerPresets.size) {
                        state.equalizerPresets[state.currentPresetIndex.toInt()]
                    } else "Custom"

                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentPresetName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("EQ Preset") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = presetExpanded,
                            onDismissRequest = { presetExpanded = false }
                        ) {
                            state.equalizerPresets.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        effectsManager.setEqualizerPreset(index.toShort())
                                        presetExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // EQ Band Sliders
                state.equalizerBands.forEach { band ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${band.centerFreqHz} Hz", fontWeight = FontWeight.Medium)
                            val db = band.currentLevelMb / 100.0f
                            Text("${if (db > 0) "+" else ""}${String.format("%.1f", db)} dB")
                        }
                        Slider(
                            value = band.currentLevelMb.toFloat(),
                            onValueChange = { effectsManager.setEqualizerBandLevel(band.bandIndex, it.toInt().toShort()) },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat()
                        )
                    }
                }
            }
        }

        // Enhancements: Bass Boost, Virtualizer, Loudness Enhancer
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Acoustic Enhancements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Bass Boost
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost", fontWeight = FontWeight.SemiBold)
                        Text("${state.bassBoostStrength / 10}%")
                    }
                    Slider(
                        value = state.bassBoostStrength.toFloat(),
                        onValueChange = { effectsManager.setBassBoostStrength(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        enabled = state.isBassBoostAvailable
                    )
                }

                // Virtualizer (Spatializer)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Virtualizer (Spatializer)", fontWeight = FontWeight.SemiBold)
                        Text("${state.virtualizerStrength / 10}%")
                    }
                    Slider(
                        value = state.virtualizerStrength.toFloat(),
                        onValueChange = { effectsManager.setVirtualizerStrength(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        enabled = state.isVirtualizerAvailable
                    )
                }

                // Loudness Enhancer
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Loudness Enhancer", fontWeight = FontWeight.SemiBold)
                        Text("+${state.loudnessGainMb / 100} dB")
                    }
                    Slider(
                        value = state.loudnessGainMb.toFloat(),
                        onValueChange = { effectsManager.setLoudnessGainMb(it.toInt()) },
                        valueRange = 0f..2000f,
                        enabled = state.isLoudnessEnhancerAvailable
                    )
                }
            }
        }

        // System DSP Effects Catalog
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Discovered System Effects (${state.availableSystemEffects.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                state.availableSystemEffects.forEach { effect ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(effect.name, fontWeight = FontWeight.SemiBold)
                            Text("Implementor: ${effect.implementor}", style = MaterialTheme.typography.bodySmall)
                            Text("UUID: ${effect.uuid}", style = MaterialTheme.typography.bodySmall)
                            Text("Connect Mode: ${effect.connectMode}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

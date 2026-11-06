package com.example.droidsample.ui.audio

import android.media.AudioManager
import android.os.Build
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.audio.AudioRoutingManager
import com.example.droidsample.audio.MicrophoneCharacteristicsManager
import java.util.Locale

@Composable
fun AudioRoutingView(
    routingManager: AudioRoutingManager,
    modifier: Modifier = Modifier
) {
    val routingState by routingManager.routingState.collectAsState()
    val context = LocalContext.current
    val micManager = remember { MicrophoneCharacteristicsManager(context) }
    val microphones = remember { micManager.queryAvailableMicrophones() }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Communication Device & Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Communication Routing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Active Comm Device: ${routingState.activeCommunicationDevice?.productName ?: "System Default"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && routingState.activeCommunicationDevice != null) {
                    OutlinedButton(
                        onClick = { routingManager.clearCommunicationDevice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Communication Device")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speakerphone On", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = routingState.isSpeakerphoneOn,
                        onCheckedChange = { routingManager.setSpeakerphoneOn(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bluetooth SCO On", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = routingState.isBluetoothScoOn,
                        onCheckedChange = { routingManager.setBluetoothScoOn(it) }
                    )
                }
            }
        }

        // Audio Focus Test Panel
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Audio Focus Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Focus Status: ${getFocusStateName(routingState.audioFocusState)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { routingManager.requestAudioFocus(AudioManager.AUDIOFOCUS_GAIN) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Gain Focus")
                    }
                    Button(
                        onClick = { routingManager.requestAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Duck Focus")
                    }
                }
                OutlinedButton(
                    onClick = { routingManager.abandonAudioFocus() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abandon Focus")
                }
            }
        }

        // Connected Audio Input Devices
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Audio Input Devices (${routingState.inputDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (routingState.inputDevices.isEmpty()) {
                    Text("No input devices detected", style = MaterialTheme.typography.bodySmall)
                }

                routingState.inputDevices.forEach { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(device.productName, fontWeight = FontWeight.Bold)
                                Text("ID: ${device.id}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("Type: ${device.typeName}", style = MaterialTheme.typography.bodySmall)
                            if (device.sampleRates.isNotEmpty()) {
                                Text("Sample Rates: ${device.sampleRates.joinToString()} Hz", style = MaterialTheme.typography.bodySmall)
                            }
                            if (device.channelCounts.isNotEmpty()) {
                                Text("Channels: ${device.channelCounts.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { routingManager.setCommunicationDevice(device.rawDevice) },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Route Voice Here")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Connected Audio Output Devices
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Audio Output Devices (${routingState.outputDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                routingState.outputDevices.forEach { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(device.productName, fontWeight = FontWeight.Bold)
                                Text("ID: ${device.id}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("Type: ${device.typeName}", style = MaterialTheme.typography.bodySmall)
                            if (device.sampleRates.isNotEmpty()) {
                                Text("Sample Rates: ${device.sampleRates.joinToString()} Hz", style = MaterialTheme.typography.bodySmall)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { routingManager.setCommunicationDevice(device.rawDevice) },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Route Voice Here")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Physical Microphone Characteristics
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Microphone Characteristics (${microphones.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (microphones.isEmpty()) {
                    Text("No MicrophoneInfo available on this platform (API 28+ required)", style = MaterialTheme.typography.bodySmall)
                }

                microphones.forEach { mic ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Mic #${mic.id} (${mic.locationName})",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Type: ${mic.typeName}", style = MaterialTheme.typography.bodySmall)
                            Text("Directionality: ${mic.directionalityName}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.US, "Position (x,y,z): (%.3f, %.3f, %.3f) m", mic.positionX, mic.positionY, mic.positionZ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format(Locale.US, "Orientation: Az=%.1f°, El=%.1f°, Roll=%.1f°", mic.orientationAzimuth, mic.orientationElevation, mic.orientationRoll),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format(Locale.US, "Sensitivity: %.1f dBFS | Max SPL: %.1f dB | Min SPL: %.1f dB", mic.sensitivityDbFs, mic.maxSplDb, mic.minSplDb),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (mic.frequencyResponse.isNotEmpty()) {
                                Text(
                                    text = "Freq Curve Points: ${mic.frequencyResponse.size} calibration points",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getFocusStateName(state: Int): String {
    return when (state) {
        AudioManager.AUDIOFOCUS_NONE -> "None"
        AudioManager.AUDIOFOCUS_GAIN -> "Active (AUDIOFOCUS_GAIN)"
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "Active Transient"
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "Active Transient (May Duck)"
        AudioManager.AUDIOFOCUS_LOSS -> "Focus Lost Permanently"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "Focus Lost Temporarily"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "Focus Lost (Ducking)"
        else -> "State $state"
    }
}

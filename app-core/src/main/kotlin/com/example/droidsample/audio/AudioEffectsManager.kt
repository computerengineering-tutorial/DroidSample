@file:Suppress("DEPRECATION")

package com.example.droidsample.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.Virtualizer
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio hardware and software DSP effects:
 * - Capture preprocessing: AcousticEchoCanceler (AEC), NoiseSuppressor (NS), AutomaticGainControl (AGC).
 * - Output processing: Equalizer (EQ), BassBoost, Virtualizer, LoudnessEnhancer, DynamicsProcessing.
 */
@Suppress("DEPRECATION")
class AudioEffectsManager {

    data class EffectDescriptor(
        val type: String,
        val uuid: String,
        val connectMode: String,
        val name: String,
        val implementor: String
    )

    data class EqualizerBandInfo(
        val bandIndex: Short,
        val centerFreqHz: Int,
        val minLevelMb: Short,
        val maxLevelMb: Short,
        val currentLevelMb: Short
    )

    data class EffectsState(
        val isAecAvailable: Boolean = false,
        val isAecEnabled: Boolean = false,
        val isNsAvailable: Boolean = false,
        val isNsEnabled: Boolean = false,
        val isAgcAvailable: Boolean = false,
        val isAgcEnabled: Boolean = false,
        val isEqualizerAvailable: Boolean = false,
        val isEqualizerEnabled: Boolean = false,
        val equalizerBands: List<EqualizerBandInfo> = emptyList(),
        val equalizerPresets: List<String> = emptyList(),
        val currentPresetIndex: Short = -1,
        val isBassBoostAvailable: Boolean = false,
        val isBassBoostEnabled: Boolean = false,
        val bassBoostStrength: Short = 0,
        val isVirtualizerAvailable: Boolean = false,
        val isVirtualizerEnabled: Boolean = false,
        val virtualizerStrength: Short = 0,
        val isLoudnessEnhancerAvailable: Boolean = false,
        val isLoudnessEnhancerEnabled: Boolean = false,
        val loudnessGainMb: Int = 0,
        val isDynamicsProcessingAvailable: Boolean = false,
        val isDynamicsProcessingEnabled: Boolean = false,
        val availableSystemEffects: List<EffectDescriptor> = emptyList(),
        val lastErrorMessage: String? = null
    )

    private val _effectsState = MutableStateFlow(EffectsState())
    val effectsState: StateFlow<EffectsState> = _effectsState.asStateFlow()

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    init {
        scanSystemEffects()
    }

    /**
     * Scans all system-registered audio effect descriptors.
     */
    fun scanSystemEffects() {
        val descriptors = try {
            AudioEffect.queryEffects()?.map { desc ->
                EffectDescriptor(
                    type = desc.type?.toString() ?: "",
                    uuid = desc.uuid?.toString() ?: "",
                    connectMode = desc.connectMode ?: "",
                    name = desc.name ?: "Unknown Effect",
                    implementor = desc.implementor ?: "OEM"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val aecAvailable = try { AcousticEchoCanceler.isAvailable() } catch (e: Exception) { false }
        val nsAvailable = try { NoiseSuppressor.isAvailable() } catch (e: Exception) { false }
        val agcAvailable = try { AutomaticGainControl.isAvailable() } catch (e: Exception) { false }
        val dpAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        _effectsState.value = _effectsState.value.copy(
            isAecAvailable = aecAvailable,
            isNsAvailable = nsAvailable,
            isAgcAvailable = agcAvailable,
            isDynamicsProcessingAvailable = dpAvailable,
            availableSystemEffects = descriptors
        )
    }

    /**
     * Attaches effects to a specific audio session ID (e.g. from AudioRecord or MediaPlayer).
     * Pass 0 for system-wide/output mix if supported.
     */
    fun attachToSession(sessionId: Int) {
        release()

        // 1. Acoustic Echo Canceler
        if (_effectsState.value.isAecAvailable && sessionId > 0) {
            try {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
                _effectsState.value = _effectsState.value.copy(isAecEnabled = echoCanceler?.enabled == true)
            } catch (e: Exception) {
                // Not supported on this OEM session
            }
        }

        // 2. Noise Suppressor
        if (_effectsState.value.isNsAvailable && sessionId > 0) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
                _effectsState.value = _effectsState.value.copy(isNsEnabled = noiseSuppressor?.enabled == true)
            } catch (e: Exception) {
                // Not supported on this OEM session
            }
        }

        // 3. Automatic Gain Control
        if (_effectsState.value.isAgcAvailable && sessionId > 0) {
            try {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                }
                _effectsState.value = _effectsState.value.copy(isAgcEnabled = gainControl?.enabled == true)
            } catch (e: Exception) {
                // Not supported on this OEM session
            }
        }

        // 4. Equalizer
        try {
            val eq = Equalizer(0, sessionId).apply {
                enabled = true
            }
            equalizer = eq

            val bands = mutableListOf<EqualizerBandInfo>()
            val numBands = eq.numberOfBands
            val levelRange = eq.bandLevelRange // [minMb, maxMb]

            for (b in 0 until numBands) {
                val bandIndex = b.toShort()
                val centerFreq = eq.getCenterFreq(bandIndex) / 1000 // Convert mHz to Hz
                val currentLevel = eq.getBandLevel(bandIndex)
                bands.add(
                    EqualizerBandInfo(
                        bandIndex = bandIndex,
                        centerFreqHz = centerFreq,
                        minLevelMb = levelRange[0],
                        maxLevelMb = levelRange[1],
                        currentLevelMb = currentLevel
                    )
                )
            }

            val presets = mutableListOf<String>()
            val numPresets = eq.numberOfPresets
            for (p in 0 until numPresets) {
                presets.add(eq.getPresetName(p.toShort()))
            }

            _effectsState.value = _effectsState.value.copy(
                isEqualizerAvailable = true,
                isEqualizerEnabled = eq.enabled,
                equalizerBands = bands,
                equalizerPresets = presets,
                currentPresetIndex = eq.currentPreset
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(isEqualizerAvailable = false)
        }

        // 5. Bass Boost
        try {
            val bb = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(500.toShort()) // 50%
                }
                enabled = true
            }
            bassBoost = bb
            _effectsState.value = _effectsState.value.copy(
                isBassBoostAvailable = bb.strengthSupported,
                isBassBoostEnabled = bb.enabled,
                bassBoostStrength = bb.roundedStrength
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(isBassBoostAvailable = false)
        }

        // 6. Virtualizer
        try {
            val virt = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(500.toShort())
                }
                enabled = true
            }
            virtualizer = virt
            _effectsState.value = _effectsState.value.copy(
                isVirtualizerAvailable = virt.strengthSupported,
                isVirtualizerEnabled = virt.enabled,
                virtualizerStrength = virt.roundedStrength
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(isVirtualizerAvailable = false)
        }

        // 7. Loudness Enhancer
        try {
            val le = LoudnessEnhancer(sessionId).apply {
                setTargetGain(100) // 1 dB
                enabled = true
            }
            loudnessEnhancer = le
            _effectsState.value = _effectsState.value.copy(
                isLoudnessEnhancerAvailable = true,
                isLoudnessEnhancerEnabled = le.enabled,
                loudnessGainMb = 100
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(isLoudnessEnhancerAvailable = false)
        }

        // 8. Dynamics Processing (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val dp = DynamicsProcessing(sessionId).apply {
                    enabled = true
                }
                dynamicsProcessing = dp
                _effectsState.value = _effectsState.value.copy(
                    isDynamicsProcessingEnabled = dp.enabled
                )
            } catch (e: Exception) {
                // Not supported
            }
        }
    }

    fun setAecEnabled(enabled: Boolean) {
        try {
            echoCanceler?.enabled = enabled
            _effectsState.value = _effectsState.value.copy(isAecEnabled = echoCanceler?.enabled ?: false)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setNsEnabled(enabled: Boolean) {
        try {
            noiseSuppressor?.enabled = enabled
            _effectsState.value = _effectsState.value.copy(isNsEnabled = noiseSuppressor?.enabled ?: false)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setAgcEnabled(enabled: Boolean) {
        try {
            gainControl?.enabled = enabled
            _effectsState.value = _effectsState.value.copy(isAgcEnabled = gainControl?.enabled ?: false)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, levelMb)
            val updatedBands = _effectsState.value.equalizerBands.map {
                if (it.bandIndex == bandIndex) it.copy(currentLevelMb = levelMb) else it
            }
            _effectsState.value = _effectsState.value.copy(
                equalizerBands = updatedBands,
                currentPresetIndex = -1 // custom
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setEqualizerPreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            val bands = _effectsState.value.equalizerBands.map {
                val lvl = equalizer?.getBandLevel(it.bandIndex) ?: it.currentLevelMb
                it.copy(currentLevelMb = lvl)
            }
            _effectsState.value = _effectsState.value.copy(
                equalizerBands = bands,
                currentPresetIndex = presetIndex
            )
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
            _effectsState.value = _effectsState.value.copy(bassBoostStrength = bassBoost?.roundedStrength ?: strength)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        try {
            virtualizer?.setStrength(strength)
            _effectsState.value = _effectsState.value.copy(virtualizerStrength = virtualizer?.roundedStrength ?: strength)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun setLoudnessGainMb(gainMb: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
            _effectsState.value = _effectsState.value.copy(loudnessGainMb = gainMb)
        } catch (e: Exception) {
            _effectsState.value = _effectsState.value.copy(lastErrorMessage = e.message)
        }
    }

    fun release() {
        try { echoCanceler?.release() } catch (e: Exception) {}
        try { noiseSuppressor?.release() } catch (e: Exception) {}
        try { gainControl?.release() } catch (e: Exception) {}
        try { equalizer?.release() } catch (e: Exception) {}
        try { bassBoost?.release() } catch (e: Exception) {}
        try { virtualizer?.release() } catch (e: Exception) {}
        try { loudnessEnhancer?.release() } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { dynamicsProcessing?.release() } catch (e: Exception) {}
        }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        dynamicsProcessing = null
    }
}

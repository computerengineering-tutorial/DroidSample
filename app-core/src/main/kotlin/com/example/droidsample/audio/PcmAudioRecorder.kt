package com.example.droidsample.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages low-level PCM audio recording using [AudioRecord].
 * Supports 8-bit, 16-bit, and 32-bit Float PCM formats, multiple sample rates (8k - 192k Hz),
 * routing, audio effects attachment (via [audioSessionId]), and real-time buffer streaming.
 */
class PcmAudioRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    enum class State {
        IDLE,
        INITIALIZING,
        RECORDING,
        PAUSED,
        STOPPED,
        ERROR
    }

    data class Config(
        val audioSource: Int = MediaRecorder.AudioSource.MIC,
        val sampleRate: Int = 44100,
        val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        val bufferMultiplier: Int = 2
    ) {
        val channelCount: Int
            get() = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1

        val bytesPerSample: Int
            get() = when (audioEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                AudioFormat.ENCODING_PCM_16BIT -> 2
                else -> 2
            }
    }

    data class RecordingStats(
        val state: State = State.IDLE,
        val totalBytesRecorded: Long = 0L,
        val durationMs: Long = 0L,
        val currentRms: Float = 0f,
        val currentPeakDb: Float = AudioAnalyzer.MIN_DB,
        val outputFile: File? = null,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stats = MutableStateFlow(RecordingStats())
    val stats: StateFlow<RecordingStats> = _stats.asStateFlow()

    private val _audioMetricsFlow = MutableSharedFlow<AudioAnalyzer.AudioMetrics>(replay = 1, extraBufferCapacity = 16)
    val audioMetricsFlow: SharedFlow<AudioAnalyzer.AudioMetrics> = _audioMetricsFlow.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var activeConfig: Config = Config()
    private var outputStream: FileOutputStream? = null
    private var outputFile: File? = null

    val audioSessionId: Int
        get() = audioRecord?.audioSessionId ?: 0

    val routedDevice: AudioDeviceInfo?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord?.routedDevice
        } else null

    /**
     * Calculates the minimum buffer size in bytes for a given configuration.
     */
    fun calculateMinBufferSize(config: Config): Int {
        return AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioEncoding
        )
    }

    /**
     * Initializes and starts recording PCM audio to an optional file,
     * simultaneously emitting analyzed metrics for UI visualization.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(config: Config = Config(), destinationFile: File? = null) {
        if (_state.value == State.RECORDING) return

        activeConfig = config
        _state.value = State.INITIALIZING

        val minBufSize = calculateMinBufferSize(config)
        if (minBufSize <= 0) {
            _state.value = State.ERROR
            _stats.value = _stats.value.copy(
                state = State.ERROR,
                errorMessage = "Invalid AudioRecord configuration or unsupported sample rate: ${config.sampleRate} Hz"
            )
            return
        }

        val bufferSize = minBufSize * config.bufferMultiplier

        try {
            audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(config.audioSource)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(config.audioEncoding)
                            .setSampleRate(config.sampleRate)
                            .setChannelMask(config.channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    config.audioSource,
                    config.sampleRate,
                    config.channelConfig,
                    config.audioEncoding,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = State.ERROR
                _stats.value = _stats.value.copy(
                    state = State.ERROR,
                    errorMessage = "AudioRecord failed to initialize (state=${audioRecord?.state})"
                )
                releaseRecorder()
                return
            }

            outputFile = destinationFile ?: File(context.cacheDir, "recorded_pcm_${System.currentTimeMillis()}.pcm")
            outputStream = FileOutputStream(outputFile)

            audioRecord?.startRecording()
            _state.value = State.RECORDING
            _stats.value = RecordingStats(
                state = State.RECORDING,
                outputFile = outputFile
            )

            startStreamingLoop(bufferSize, config)
        } catch (e: Exception) {
            _state.value = State.ERROR
            _stats.value = _stats.value.copy(
                state = State.ERROR,
                errorMessage = "Failed to start AudioRecord: ${e.message}"
            )
            releaseRecorder()
        }
    }

    private fun startStreamingLoop(bufferSize: Int, config: Config) {
        recordJob?.cancel()
        recordJob = coroutineScope.launch {
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            val isFloat = config.audioEncoding == AudioFormat.ENCODING_PCM_FLOAT
            val is8Bit = config.audioEncoding == AudioFormat.ENCODING_PCM_8BIT

            val byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
            val floatArray = if (isFloat) FloatArray(bufferSize / 4) else null
            val shortArray = if (!isFloat && !is8Bit) ShortArray(bufferSize / 2) else null
            val byteArray = ByteArray(bufferSize)

            while (isActive && _state.value == State.RECORDING) {
                val record = audioRecord ?: break

                when {
                    isFloat && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && floatArray != null -> {
                        val readFloats = record.read(floatArray, 0, floatArray.size, AudioRecord.READ_BLOCKING)
                        if (readFloats > 0) {
                            val bytesRead = readFloats * 4
                            totalBytes += bytesRead

                            // Write raw float bytes to output stream
                            byteBuffer.clear()
                            byteBuffer.asFloatBuffer().put(floatArray, 0, readFloats)
                            byteBuffer.position(0)
                            byteBuffer.get(byteArray, 0, bytesRead)
                            outputStream?.write(byteArray, 0, bytesRead)

                            val metrics = AudioAnalyzer.analyze(
                                samples = floatArray,
                                length = readFloats,
                                sampleRate = config.sampleRate
                            )
                            _audioMetricsFlow.tryEmit(metrics)

                            updateStats(totalBytes, startTime, metrics)
                        }
                    }
                    is8Bit -> {
                        val readBytes = record.read(byteArray, 0, byteArray.size)
                        if (readBytes > 0) {
                            totalBytes += readBytes
                            outputStream?.write(byteArray, 0, readBytes)

                            val normalizedFloats = AudioAnalyzer.pcm8BitToNormalizedFloats(byteArray, readBytes)
                            val metrics = AudioAnalyzer.analyze(
                                samples = normalizedFloats,
                                length = normalizedFloats.size,
                                sampleRate = config.sampleRate
                            )
                            _audioMetricsFlow.tryEmit(metrics)

                            updateStats(totalBytes, startTime, metrics)
                        }
                    }
                    shortArray != null -> {
                        val readShorts = record.read(shortArray, 0, shortArray.size)
                        if (readShorts > 0) {
                            val bytesRead = readShorts * 2
                            totalBytes += bytesRead

                            byteBuffer.clear()
                            byteBuffer.asShortBuffer().put(shortArray, 0, readShorts)
                            byteBuffer.position(0)
                            byteBuffer.get(byteArray, 0, bytesRead)
                            outputStream?.write(byteArray, 0, bytesRead)

                            val normalizedFloats = AudioAnalyzer.shortArrayToNormalizedFloats(shortArray, readShorts)
                            val metrics = AudioAnalyzer.analyze(
                                samples = normalizedFloats,
                                length = normalizedFloats.size,
                                sampleRate = config.sampleRate
                            )
                            _audioMetricsFlow.tryEmit(metrics)

                            updateStats(totalBytes, startTime, metrics)
                        }
                    }
                }
            }
        }
    }

    private fun updateStats(totalBytes: Long, startTime: Long, metrics: AudioAnalyzer.AudioMetrics) {
        val durationMs = System.currentTimeMillis() - startTime
        _stats.value = _stats.value.copy(
            totalBytesRecorded = totalBytes,
            durationMs = durationMs,
            currentRms = metrics.rms,
            currentPeakDb = metrics.dbFs
        )
    }

    fun pause() {
        if (_state.value == State.RECORDING) {
            _state.value = State.PAUSED
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun resume() {
        if (_state.value == State.PAUSED) {
            _state.value = State.RECORDING
            try {
                audioRecord?.startRecording()
                val minBufSize = calculateMinBufferSize(activeConfig)
                startStreamingLoop(minBufSize * activeConfig.bufferMultiplier, activeConfig)
            } catch (e: Exception) {
                _state.value = State.ERROR
            }
        }
    }

    fun stopRecording(): File? {
        if (_state.value != State.RECORDING && _state.value != State.PAUSED) return outputFile

        _state.value = State.STOPPED
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore
        }

        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            // Ignore
        }
        outputStream = null

        val finalFile = outputFile
        val totalBytes = _stats.value.totalBytesRecorded

        // If recording 16-bit PCM, create a corresponding WAV file for instant playback
        if (finalFile != null && finalFile.exists() && activeConfig.audioEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            try {
                val wavFile = File(finalFile.parentFile, finalFile.nameWithoutExtension + ".wav")
                convertPcmToWav(finalFile, wavFile, activeConfig.sampleRate, activeConfig.channelCount, 16)
                _stats.value = _stats.value.copy(
                    state = State.STOPPED,
                    outputFile = wavFile
                )
                releaseRecorder()
                return wavFile
            } catch (e: Exception) {
                // Return original pcm file if wav conversion fails
            }
        }

        _stats.value = _stats.value.copy(state = State.STOPPED)
        releaseRecorder()
        return finalFile
    }

    /**
     * Preferred audio input device routing (API 28+).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setPreferredDevice(device: AudioDeviceInfo?): Boolean {
        return audioRecord?.setPreferredDevice(device) ?: false
    }

    /**
     * Preferred microphone directionality (API 29+).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setPreferredMicrophoneDirection(direction: Int): Boolean {
        return audioRecord?.setPreferredMicrophoneDirection(direction) ?: false
    }

    /**
     * Preferred microphone field dimension (API 29+).
     * @param zoom zoom factor in [-1.0f, 1.0f]
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setPreferredMicrophoneFieldDimension(zoom: Float): Boolean {
        return audioRecord?.setPreferredMicrophoneFieldDimension(zoom) ?: false
    }

    private fun releaseRecorder() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
    }

    companion object {
        /**
         * Builds a 44-byte standard RIFF/WAVE header for PCM audio.
         */
        fun createWavHeader(
            pcmDataSize: Long,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ): ByteArray {
            val totalDataLen = pcmDataSize + 36
            val byteRate = (sampleRate * channels * bitsPerSample) / 8
            val blockAlign = (channels * bitsPerSample) / 8

            val header = ByteArray(44)
            // "RIFF"
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            // Overall file size - 8
            header[4] = (totalDataLen and 0xffL).toByte()
            header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
            header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
            header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
            // "WAVE"
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            // "fmt "
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            // Subchunk1 size (16 for PCM)
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            // AudioFormat: 1 for PCM
            header[20] = 1
            header[21] = 0
            // Channels
            header[22] = channels.toByte()
            header[23] = 0
            // Sample rate
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            // Byte rate
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            // Block align
            header[32] = blockAlign.toByte()
            header[33] = 0
            // Bits per sample
            header[34] = bitsPerSample.toByte()
            header[35] = 0
            // "data"
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            // Data chunk length
            header[40] = (pcmDataSize and 0xffL).toByte()
            header[41] = ((pcmDataSize shr 8) and 0xffL).toByte()
            header[42] = ((pcmDataSize shr 16) and 0xffL).toByte()
            header[43] = ((pcmDataSize shr 24) and 0xffL).toByte()

            return header
        }

        /**
         * Wraps raw PCM file with a standard WAV RIFF header.
         */
        fun convertPcmToWav(
            pcmFile: File,
            wavFile: File,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ) {
            val pcmSize = pcmFile.length()
            val header = createWavHeader(pcmSize, sampleRate, channels, bitsPerSample)

            pcmFile.inputStream().use { input ->
                wavFile.outputStream().use { output ->
                    output.write(header)
                    input.copyTo(output)
                }
            }
        }
    }
}

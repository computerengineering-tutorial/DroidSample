package com.example.droidsample.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * High-level audio recording manager built upon Android [MediaRecorder].
 * Supports all standard audio sources, container output formats, encoders,
 * dynamic audio routing, microphone directionality, and amplitude tracking.
 */
class MediaAudioRecorder(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    enum class State {
        IDLE,
        INITIALIZED,
        RECORDING,
        PAUSED,
        STOPPED,
        ERROR
    }

    data class Config(
        val audioSource: Int = MediaRecorder.AudioSource.MIC,
        val outputFormat: Int = MediaRecorder.OutputFormat.MPEG_4,
        val audioEncoder: Int = MediaRecorder.AudioEncoder.AAC,
        val sampleRate: Int = 44100,
        val bitRate: Int = 128000,
        val channels: Int = 1,
        val maxDurationMs: Int = 0, // 0 = unlimited
        val maxFileSizeBytes: Long = 0L // 0 = unlimited
    ) {
        val extension: String
            get() = when (outputFormat) {
                MediaRecorder.OutputFormat.MPEG_4 -> "m4a"
                MediaRecorder.OutputFormat.THREE_GPP -> "3gp"
                MediaRecorder.OutputFormat.AMR_NB -> "amr"
                MediaRecorder.OutputFormat.AMR_WB -> "awb"
                MediaRecorder.OutputFormat.AAC_ADTS -> "aac"
                MediaRecorder.OutputFormat.OGG -> "ogg"
                MediaRecorder.OutputFormat.WEBM -> "webm"
                else -> "m4a"
            }
    }

    data class RecordingStats(
        val state: State = State.IDLE,
        val durationMs: Long = 0L,
        val currentMaxAmplitude: Int = 0,
        val currentPeakDb: Float = AudioAnalyzer.MIN_DB,
        val outputFile: File? = null,
        val infoEventCode: Int? = null,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stats = MutableStateFlow(RecordingStats())
    val stats: StateFlow<RecordingStats> = _stats.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var activeConfig: Config = Config()
    private var outputFile: File? = null
    private var pollingJob: Job? = null
    private var recordingStartTime = 0L
    private var totalPausedDuration = 0L
    private var pauseStartTime = 0L

    val routedDevice: AudioDeviceInfo?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mediaRecorder?.routedDevice
        } else null

    /**
     * Initializes the [MediaRecorder] with the specified configuration and output file destination.
     */
    fun prepare(config: Config = Config(), destinationFile: File? = null): Boolean {
        release()
        activeConfig = config

        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(config.audioSource)
            recorder.setOutputFormat(config.outputFormat)
            recorder.setAudioEncoder(config.audioEncoder)
            recorder.setAudioSamplingRate(config.sampleRate)
            recorder.setAudioEncodingBitRate(config.bitRate)
            recorder.setAudioChannels(config.channels)

            if (config.maxDurationMs > 0) {
                recorder.setMaxDuration(config.maxDurationMs)
            }
            if (config.maxFileSizeBytes > 0) {
                recorder.setMaxFileSize(config.maxFileSizeBytes)
            }

            val targetFile = destinationFile ?: File(
                context.cacheDir,
                "rec_${System.currentTimeMillis()}.${config.extension}"
            )
            outputFile = targetFile
            recorder.setOutputFile(targetFile.absolutePath)

            recorder.setOnInfoListener { _, what, extra ->
                _stats.value = _stats.value.copy(infoEventCode = what)
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                ) {
                    stop()
                }
            }

            recorder.setOnErrorListener { _, what, extra ->
                _state.value = State.ERROR
                _stats.value = _stats.value.copy(
                    state = State.ERROR,
                    errorMessage = "MediaRecorder error: code=$what, extra=$extra"
                )
                stopPolling()
            }

            recorder.prepare()
            mediaRecorder = recorder
            _state.value = State.INITIALIZED
            _stats.value = RecordingStats(state = State.INITIALIZED, outputFile = outputFile)
            true
        } catch (e: Exception) {
            _state.value = State.ERROR
            _stats.value = _stats.value.copy(
                state = State.ERROR,
                errorMessage = "Failed to prepare MediaRecorder: ${e.message}"
            )
            release()
            false
        }
    }

    /**
     * Starts audio recording and launches coroutine to track elapsed time & peak amplitude.
     */
    fun start(): Boolean {
        if (_state.value != State.INITIALIZED) {
            if (!prepare(activeConfig, outputFile)) return false
        }

        return try {
            mediaRecorder?.start()
            recordingStartTime = System.currentTimeMillis()
            totalPausedDuration = 0L
            _state.value = State.RECORDING
            _stats.value = _stats.value.copy(state = State.RECORDING)
            startAmplitudePolling()
            true
        } catch (e: Exception) {
            _state.value = State.ERROR
            _stats.value = _stats.value.copy(
                state = State.ERROR,
                errorMessage = "Failed to start MediaRecorder: ${e.message}"
            )
            false
        }
    }

    /**
     * Pauses ongoing recording (API 24+).
     */
    fun pause(): Boolean {
        if (_state.value != State.RECORDING) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                pauseStartTime = System.currentTimeMillis()
                _state.value = State.PAUSED
                _stats.value = _stats.value.copy(state = State.PAUSED)
                true
            } catch (e: Exception) {
                false
            }
        } else false
    }

    /**
     * Resumes a paused recording (API 24+).
     */
    fun resume(): Boolean {
        if (_state.value != State.PAUSED) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                if (pauseStartTime > 0L) {
                    totalPausedDuration += System.currentTimeMillis() - pauseStartTime
                    pauseStartTime = 0L
                }
                _state.value = State.RECORDING
                _stats.value = _stats.value.copy(state = State.RECORDING)
                true
            } catch (e: Exception) {
                false
            }
        } else false
    }

    /**
     * Stops recording and returns the recorded [File].
     */
    fun stop(): File? {
        if (_state.value != State.RECORDING && _state.value != State.PAUSED) return outputFile
        stopPolling()

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Can throw if called immediately after start
        }

        val completedFile = outputFile
        _state.value = State.STOPPED
        _stats.value = _stats.value.copy(
            state = State.STOPPED,
            outputFile = completedFile
        )
        release()
        return completedFile
    }

    /**
     * Sets preferred audio input device (API 28+).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun setPreferredDevice(device: AudioDeviceInfo?): Boolean {
        return mediaRecorder?.setPreferredDevice(device) ?: false
    }

    /**
     * Sets preferred microphone directionality (API 29+).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setPreferredMicrophoneDirection(direction: Int): Boolean {
        return mediaRecorder?.setPreferredMicrophoneDirection(direction) ?: false
    }

    /**
     * Sets preferred microphone field dimension / zoom (API 29+).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setPreferredMicrophoneFieldDimension(zoom: Float): Boolean {
        return mediaRecorder?.setPreferredMicrophoneFieldDimension(zoom) ?: false
    }

    private fun startAmplitudePolling() {
        stopPolling()
        pollingJob = coroutineScope.launch {
            while (isActive && _state.value == State.RECORDING) {
                val maxAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }

                val normalized = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
                val dbFs = AudioAnalyzer.amplitudeToDbFs(normalized)
                val duration = System.currentTimeMillis() - recordingStartTime - totalPausedDuration

                _stats.value = _stats.value.copy(
                    durationMs = duration,
                    currentMaxAmplitude = maxAmp,
                    currentPeakDb = dbFs
                )
                delay(100L)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun release() {
        stopPolling()
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
    }
}

package com.example.droidsample.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Manages full-screen capture workflows via MediaProjection, VirtualDisplay, and MediaRecorder.
 */
class ScreenRecorderManager(private val context: Context) {

    data class ScreenConfig(
        val width: Int = 1080,
        val height: Int = 1920,
        val densityDpi: Int = 400,
        val frameRate: Int = 30,
        val bitrate: Int = 8_000_000,
        val enableAudio: Boolean = false
    )

    sealed interface ScreenRecordState {
        data object Idle : ScreenRecordState
        data class Recording(
            val durationSeconds: Long,
            val outputFile: File,
            val config: ScreenConfig
        ) : ScreenRecordState
        data class Completed(val outputFile: File, val durationSeconds: Long) : ScreenRecordState
        data class Error(val message: String) : ScreenRecordState
    }

    private val projectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    private val _recordingState = MutableStateFlow<ScreenRecordState>(ScreenRecordState.Idle)
    val recordingState: StateFlow<ScreenRecordState> = _recordingState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    /**
     * Creates intent to prompt user for screen recording permission.
     */
    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    /**
     * Inspects the default display metrics to provide a recommended recording configuration.
     */
    fun getRecommendedConfig(): ScreenConfig {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // Ensure dimensions are even numbers (required by AVC/HEVC encoders)
        val w = (metrics.widthPixels / 2) * 2
        val h = (metrics.heightPixels / 2) * 2
        return ScreenConfig(
            width = if (w > 0) w else 1080,
            height = if (h > 0) h else 1920,
            densityDpi = if (metrics.densityDpi > 0) metrics.densityDpi else 400,
            frameRate = 30,
            bitrate = 8_000_000,
            enableAudio = false
        )
    }

    /**
     * Start screen recording with user-approved MediaProjection token.
     */
    fun startRecording(
        resultCode: Int,
        resultData: Intent,
        config: ScreenConfig = getRecommendedConfig()
    ): Boolean {
        if (resultCode != Activity.RESULT_OK) {
            _recordingState.value = ScreenRecordState.Error("Screen capture permission was denied")
            return false
        }

        try {
            val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: run {
                _recordingState.value = ScreenRecordState.Error("Failed to obtain MediaProjection instance")
                return false
            }
            mediaProjection = projection

            // On Android 14+ registering callback is required before creating virtual display
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                }
            }, Handler(Looper.getMainLooper()))

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val outputFile = File(context.cacheDir, "SCREEN_$timestamp.mp4")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                if (config.enableAudio) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoSize(config.width, config.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (config.enableAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }
                setVideoEncodingBitRate(config.bitrate)
                setVideoFrameRate(config.frameRate)
                prepare()
            }
            mediaRecorder = recorder

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenRecorderDisplay",
                config.width,
                config.height,
                config.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            startTimer(outputFile, config)
            return true
        } catch (e: Exception) {
            cleanup()
            _recordingState.value = ScreenRecordState.Error("Failed to start recording: ${e.message}")
            return false
        }
    }

    private fun startTimer(outputFile: File, config: ScreenConfig) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                _recordingState.value = ScreenRecordState.Recording(seconds, outputFile, config)
                delay(1000L)
                seconds++
            }
        }
    }

    /**
     * Stop active recording and release pipeline resources.
     */
    fun stopRecording() {
        timerJob?.cancel()
        val cur = _recordingState.value
        val duration = (cur as? ScreenRecordState.Recording)?.durationSeconds ?: 0L
        val file = currentOutputFile

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        if (file != null && file.exists() && file.length() > 0) {
            _recordingState.value = ScreenRecordState.Completed(file, duration)
        } else {
            _recordingState.value = ScreenRecordState.Idle
        }
    }

    private fun cleanup() {
        timerJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    fun release() {
        stopRecording()
        cleanup()
    }
}

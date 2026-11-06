package com.example.droidsample.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * High-level CameraX Video Recorder encapsulating quality selection,
 * audio capture toggle, lifecycle binding, and recording state tracking.
 */
class CameraVideoRecorder(private val context: Context) {

    enum class TargetQuality(val label: String) {
        SD("SD (480p)"),
        HD("HD (720p)"),
        FHD("FHD (1080p)"),
        UHD("UHD (4K)");

        val quality: Quality
            get() = when (this) {
                SD -> Quality.SD
                HD -> Quality.HD
                FHD -> Quality.FHD
                UHD -> Quality.UHD
            }
    }

    sealed interface RecordingStatus {
        data object Idle : RecordingStatus
        data class Starting(val targetQuality: TargetQuality) : RecordingStatus
        data class Recording(
            val durationSeconds: Long,
            val bytesRecorded: Long,
            val isAudioEnabled: Boolean
        ) : RecordingStatus
        data class Paused(val durationSeconds: Long, val bytesRecorded: Long) : RecordingStatus
        data class Completed(
            val outputUri: Uri?,
            val durationSeconds: Long,
            val bytesRecorded: Long,
            val filePath: String? = null
        ) : RecordingStatus
        data class Error(val message: String, val errorCode: Int? = null) : RecordingStatus
    }

    private val _recordingState = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val recordingState: StateFlow<RecordingStatus> = _recordingState.asStateFlow()

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()

    private val _selectedQuality = MutableStateFlow(TargetQuality.FHD)
    val selectedQuality: StateFlow<TargetQuality> = _selectedQuality.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun setAudioEnabled(enabled: Boolean) {
        _isAudioEnabled.value = enabled
    }

    fun setSelectedQuality(quality: TargetQuality) {
        _selectedQuality.value = quality
    }

    fun toggleCameraLens() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    /**
     * Bind CameraX preview and video capture use cases to the lifecycle owner.
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val qualitySelector = QualitySelector.from(
                    _selectedQuality.value.quality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

                val capture = VideoCapture.withOutput(recorder)
                videoCapture = capture

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(_lensFacing.value)
                    .build()

                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
                onBound?.invoke()
            } catch (t: Throwable) {
                _recordingState.value = RecordingStatus.Error("Failed to bind camera: ${t.message}")
                onError?.invoke(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Start video recording either to MediaStore or to internal cache.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(saveToMediaStore: Boolean = true) {
        val capture = videoCapture ?: run {
            _recordingState.value = RecordingStatus.Error("Camera not initialized")
            return
        }

        if (activeRecording != null) {
            return
        }

        val name = "VID_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}"
        val pendingRecording: PendingRecording = if (saveToMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DroidSample")
            }
            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            capture.output.prepareRecording(context, mediaStoreOutput)
        } else {
            val file = File(context.cacheDir, "$name.mp4")
            val fileOutput = FileOutputOptions.Builder(file).build()
            capture.output.prepareRecording(context, fileOutput)
        }

        if (_isAudioEnabled.value && hasAudioPermission()) {
            pendingRecording.withAudioEnabled()
        }

        _recordingState.value = RecordingStatus.Starting(_selectedQuality.value)

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            handleRecordEvent(event)
        }
    }

    fun pauseRecording() {
        activeRecording?.pause()
    }

    fun resumeRecording() {
        activeRecording?.resume()
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun handleRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _recordingState.value = RecordingStatus.Recording(
                    durationSeconds = 0,
                    bytesRecorded = 0,
                    isAudioEnabled = _isAudioEnabled.value
                )
            }
            is VideoRecordEvent.Status -> {
                val durationSec = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                val bytes = event.recordingStats.numBytesRecorded
                _recordingState.value = RecordingStatus.Recording(
                    durationSeconds = durationSec,
                    bytesRecorded = bytes,
                    isAudioEnabled = _isAudioEnabled.value
                )
            }
            is VideoRecordEvent.Pause -> {
                val cur = _recordingState.value
                if (cur is RecordingStatus.Recording) {
                    _recordingState.value = RecordingStatus.Paused(cur.durationSeconds, cur.bytesRecorded)
                }
            }
            is VideoRecordEvent.Resume -> {
                val cur = _recordingState.value
                if (cur is RecordingStatus.Paused) {
                    _recordingState.value = RecordingStatus.Recording(
                        durationSeconds = cur.durationSeconds,
                        bytesRecorded = cur.bytesRecorded,
                        isAudioEnabled = _isAudioEnabled.value
                    )
                }
            }
            is VideoRecordEvent.Finalize -> {
                val durationSec = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                val bytes = event.recordingStats.numBytesRecorded
                if (event.hasError()) {
                    _recordingState.value = RecordingStatus.Error(
                        message = "Recording error: ${event.error}",
                        errorCode = event.error
                    )
                } else {
                    _recordingState.value = RecordingStatus.Completed(
                        outputUri = event.outputResults.outputUri,
                        durationSeconds = durationSec,
                        bytesRecorded = bytes
                    )
                }
                activeRecording = null
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun release() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {}
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}

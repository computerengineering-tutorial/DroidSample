package com.example.droidsample.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Analysis statistics calculated per frame by ImageAnalysis.
 */
data class CameraAnalysisStats(
    val averageLuminance: Double = 0.0,
    val fps: Double = 0.0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val timestampMs: Long = 0L
)

/**
 * Video recording status states.
 */
sealed class VideoRecordingState {
    data object Idle : VideoRecordingState()
    data class Recording(val durationMs: Long, val bytesWritten: Long) : VideoRecordingState()
    data class Paused(val durationMs: Long) : VideoRecordingState()
    data class Finalized(val outputUri: Uri) : VideoRecordingState()
    data class Error(val message: String, val cause: Throwable? = null) : VideoRecordingState()
}

/**
 * High-level manager orchestrating Jetpack CameraX use cases:
 * Preview, ImageCapture, ImageAnalysis, and VideoCapture with manual CameraControl actions.
 */
class CameraXManager(private val context: Context) {

    private val tag = "CameraXManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    // Use cases
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var videoCaptureUseCase: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // State flows
    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_AUTO)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _linearZoom = MutableStateFlow(0.0f)
    val linearZoom: StateFlow<Float> = _linearZoom.asStateFlow()

    private val _analysisStats = MutableStateFlow(CameraAnalysisStats())
    val analysisStats: StateFlow<CameraAnalysisStats> = _analysisStats.asStateFlow()

    private val _recordingState = MutableStateFlow<VideoRecordingState>(VideoRecordingState.Idle)
    val recordingState: StateFlow<VideoRecordingState> = _recordingState.asStateFlow()

    // FPS calculation tracking
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    /**
     * Initializes ProcessCameraProvider instance asynchronously.
     */
    fun initialize(onInitialized: (ProcessCameraProvider) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                _isCameraReady.value = true
                onInitialized(provider)
            } catch (e: Exception) {
                Log.e(tag, "Failed to obtain ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds CameraX use cases to the provided LifecycleOwner and SurfaceProvider.
     */
    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        enableAnalysis: Boolean = true,
        enableVideo: Boolean = false
    ) {
        val provider = cameraProvider ?: return

        // Unbind previous bindings
        provider.unbindAll()

        // 1. CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_lensFacing.value)
            .build()

        // 2. Preview Use Case
        previewUseCase = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(surfaceProvider)
            }

        // 3. ImageCapture Use Case
        imageCaptureUseCase = ImageCapture.Builder()
            .setFlashMode(_flashMode.value)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val useCasesToBind = mutableListOf<UseCase>(previewUseCase!!, imageCaptureUseCase!!)

        // 4. ImageAnalysis Use Case
        if (enableAnalysis && !enableVideo) {
            imageAnalysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageAnalysis(imageProxy)
                    }
                }
            useCasesToBind.add(imageAnalysisUseCase!!)
        }

        // 5. VideoCapture Use Case
        if (enableVideo) {
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()
            videoCaptureUseCase = VideoCapture.withOutput(recorder)
            useCasesToBind.add(videoCaptureUseCase!!)
        }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCasesToBind.toTypedArray()
            )

            // Observe zoom and torch state
            camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoom ->
                _zoomRatio.value = zoom.zoomRatio
                _linearZoom.value = zoom.linearZoom
            }
            camera?.cameraInfo?.torchState?.observe(lifecycleOwner) { torch ->
                _torchEnabled.value = (torch == TorchState.ON)
            }
        } catch (e: Exception) {
            Log.e(tag, "Use case binding failed", e)
        }
    }

    /**
     * Analyzes image proxy to compute average luminance and FPS.
     */
    private fun processImageAnalysis(image: ImageProxy) {
        val plane = image.planes[0] // Y buffer
        val buffer: ByteBuffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        var sum = 0L
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        val avgLuma = if (data.isNotEmpty()) sum.toDouble() / data.size else 0.0

        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTimestamp
        var currentFps = _analysisStats.value.fps
        if (elapsed >= 1000) {
            currentFps = (frameCount * 1000.0) / elapsed
            frameCount = 0
            lastFpsTimestamp = now
        }

        _analysisStats.value = CameraAnalysisStats(
            averageLuminance = avgLuma,
            fps = currentFps,
            frameWidth = image.width,
            frameHeight = image.height,
            timestampMs = now
        )

        image.close()
    }

    /**
     * Switches lens facing between Back and Front.
     */
    fun toggleCameraFacing(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        enableAnalysis: Boolean = true,
        enableVideo: Boolean = false
    ) {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases(lifecycleOwner, surfaceProvider, enableAnalysis, enableVideo)
    }

    /**
     * Toggles flash mode between AUTO, ON, and OFF.
     */
    fun cycleFlashMode() {
        val nextMode = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        _flashMode.value = nextMode
        imageCaptureUseCase?.flashMode = nextMode
    }

    /**
     * Toggles device torch on or off.
     */
    fun toggleTorch() {
        val target = !_torchEnabled.value
        camera?.cameraControl?.enableTorch(target)
    }

    /**
     * Adjusts linear zoom (0.0f to 1.0f).
     */
    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0.0f, 1.0f))
    }

    /**
     * Adjusts zoom ratio directly.
     */
    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    /**
     * Initiates tap-to-focus and metering at screen coordinates.
     */
    fun tapToFocus(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /**
     * Sets EV exposure compensation index.
     */
    fun setExposureCompensation(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    /**
     * Takes picture and writes to specified target file.
     */
    fun capturePhotoToFile(
        outputFile: File,
        onSuccess: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCapture = imageCaptureUseCase ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "ImageCapture not bound", null))
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture to file failed", exception)
                    onError(exception)
                }
            }
        )
    }

    /**
     * Takes picture to in-memory ImageProxy buffer.
     */
    fun capturePhotoToMemory(
        onSuccess: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCapture = imageCaptureUseCase ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "ImageCapture not bound", null))
            return
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    onSuccess(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture to memory failed", exception)
                    onError(exception)
                }
            }
        )
    }

    /**
     * Starts video recording to target output file.
     */
    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        outputFile: File,
        recordAudio: Boolean = false,
        onFinalized: (Uri) -> Unit
    ) {
        val videoCapture = videoCaptureUseCase ?: run {
            _recordingState.value = VideoRecordingState.Error("VideoCapture use case not bound")
            return
        }

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        var pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)
        if (recordAudio) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _recordingState.value = VideoRecordingState.Recording(0L, 0L)
                }
                is VideoRecordEvent.Status -> {
                    val stats = event.recordingStats
                    _recordingState.value = VideoRecordingState.Recording(
                        durationMs = stats.recordedDurationNanos / 1_000_000L,
                        bytesWritten = stats.numBytesRecorded
                    )
                }
                is VideoRecordEvent.Pause -> {
                    val stats = event.recordingStats
                    _recordingState.value = VideoRecordingState.Paused(
                        durationMs = stats.recordedDurationNanos / 1_000_000L
                    )
                }
                is VideoRecordEvent.Resume -> {
                    val stats = event.recordingStats
                    _recordingState.value = VideoRecordingState.Recording(
                        durationMs = stats.recordedDurationNanos / 1_000_000L,
                        bytesWritten = stats.numBytesRecorded
                    )
                }
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        activeRecording = null
                        _recordingState.value = VideoRecordingState.Error(
                            "Video record error: ${event.error}",
                            event.cause
                        )
                    } else {
                        activeRecording = null
                        val uri = event.outputResults.outputUri
                        _recordingState.value = VideoRecordingState.Finalized(uri)
                        onFinalized(uri)
                    }
                }
            }
        }
    }

    /**
     * Pauses ongoing video recording.
     */
    fun pauseVideoRecording() {
        activeRecording?.pause()
    }

    /**
     * Resumes paused video recording.
     */
    fun resumeVideoRecording() {
        activeRecording?.resume()
    }

    /**
     * Stops video recording and finalizes output.
     */
    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    /**
     * Unbinds all active use cases without terminating executor.
     */
    fun unbindAll() {
        try {
            activeRecording?.stop()
            activeRecording = null
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(tag, "Error during unbindAll", e)
        }
    }

    /**
     * Releases executor and unbinds all use cases.
     */
    fun shutdown() {
        try {
            unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(tag, "Error during shutdown", e)
        }
    }
}

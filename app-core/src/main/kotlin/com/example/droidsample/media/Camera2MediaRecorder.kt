package com.example.droidsample.media

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
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
import java.io.IOException

/**
 * Direct Camera2 pipeline paired with MediaRecorder for low-level video encoding control.
 * Supports configurable resolution, frame rate, encoder formats (H.264, HEVC, VP8), and audio options.
 */
class Camera2MediaRecorder(private val context: Context) {

    data class VideoConfig(
        val videoSize: Size = Size(1920, 1080),
        val frameRate: Int = 30,
        val bitrate: Int = 10_000_000,
        val videoEncoder: Int = MediaRecorder.VideoEncoder.H264,
        val audioEncoder: Int = MediaRecorder.AudioEncoder.AAC,
        val enableAudio: Boolean = true
    )

    sealed interface PipelineState {
        data object Idle : PipelineState
        data object Initializing : PipelineState
        data object Previewing : PipelineState
        data class Recording(val durationSeconds: Long, val outputPath: String) : PipelineState
        data class Finished(val file: File, val durationSeconds: Long) : PipelineState
        data class Error(val message: String, val throwable: Throwable? = null) : PipelineState
    }

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentOutputFile: File? = null
    private var currentConfig: VideoConfig = VideoConfig()
    private var activePreviewSurface: Surface? = null

    private val _pipelineState = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            val thread = HandlerThread("Camera2MediaRecorderThread").also { it.start() }
            backgroundThread = thread
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (_: InterruptedException) {}
    }

    /**
     * Finds the preferred back or front camera ID.
     */
    fun getCameraId(facingFront: Boolean = false): String? {
        val targetFacing = if (facingFront) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: cameraManager.cameraIdList.firstOrNull()
    }

    /**
     * Initializes MediaRecorder with the given configuration.
     */
    private fun setUpMediaRecorder(outputFile: File, config: VideoConfig): MediaRecorder {
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
            setVideoEncodingBitRate(config.bitrate)
            setVideoFrameRate(config.frameRate)
            setVideoSize(config.videoSize.width, config.videoSize.height)
            setVideoEncoder(config.videoEncoder)
            if (config.enableAudio) {
                setAudioEncoder(config.audioEncoder)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)
            }
            prepare()
        }
        return recorder
    }

    /**
     * Opens the camera and begins preview.
     */
    @SuppressLint("MissingPermission")
    fun openCamera(
        cameraId: String,
        previewSurface: Surface,
        config: VideoConfig = VideoConfig()
    ) {
        startBackgroundThread()
        currentConfig = config
        activePreviewSurface = previewSurface
        _pipelineState.value = PipelineState.Initializing

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreviewSession(camera, previewSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    _pipelineState.value = PipelineState.Idle
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    _pipelineState.value = PipelineState.Error("Camera device error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            _pipelineState.value = PipelineState.Error("Failed to open camera: ${e.message}", e)
        }
    }

    private fun startPreviewSession(camera: CameraDevice, previewSurface: Surface) {
        try {
            val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(previewSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                            _pipelineState.value = PipelineState.Previewing
                        } catch (e: Exception) {
                            _pipelineState.value = PipelineState.Error("Failed to start preview: ${e.message}", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _pipelineState.value = PipelineState.Error("Preview session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            _pipelineState.value = PipelineState.Error("Error configuring preview: ${e.message}", e)
        }
    }

    /**
     * Starts recording video to the specified target file.
     */
    fun startRecording(targetFile: File, config: VideoConfig = currentConfig) {
        val camera = cameraDevice ?: run {
            _pipelineState.value = PipelineState.Error("Camera not opened")
            return
        }
        val previewSurface = activePreviewSurface ?: run {
            _pipelineState.value = PipelineState.Error("Preview surface missing")
            return
        }

        try {
            // Close existing preview session
            captureSession?.close()
            captureSession = null

            currentOutputFile = targetFile
            currentConfig = config
            val recorder = setUpMediaRecorder(targetFile, config)
            mediaRecorder = recorder

            val recorderSurface = recorder.surface

            val recordRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                recordRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                            recorder.start()
                            startTimer(targetFile.absolutePath)
                        } catch (e: Exception) {
                            _pipelineState.value = PipelineState.Error("Failed to start recording: ${e.message}", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _pipelineState.value = PipelineState.Error("Recording session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            _pipelineState.value = PipelineState.Error("Failed to initialize recording: ${e.message}", e)
        }
    }

    private fun startTimer(outputPath: String) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                _pipelineState.value = PipelineState.Recording(seconds, outputPath)
                delay(1000L)
                seconds++
            }
        }
    }

    /**
     * Stops video recording and restores the preview session.
     */
    fun stopRecording() {
        timerJob?.cancel()
        val curState = _pipelineState.value
        val recordedDuration = (curState as? PipelineState.Recording)?.durationSeconds ?: 0L
        val savedFile = currentOutputFile

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // Can happen if stopped immediately after starting
        } finally {
            mediaRecorder = null
        }

        if (savedFile != null && savedFile.exists() && savedFile.length() > 0) {
            _pipelineState.value = PipelineState.Finished(savedFile, recordedDuration)
        } else {
            _pipelineState.value = PipelineState.Idle
        }

        // Restart preview if camera is still open
        val camera = cameraDevice
        val surface = activePreviewSurface
        if (camera != null && surface != null) {
            startPreviewSession(camera, surface)
        }
    }

    fun release() {
        timerJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        stopBackgroundThread()
        _pipelineState.value = PipelineState.Idle
    }
}

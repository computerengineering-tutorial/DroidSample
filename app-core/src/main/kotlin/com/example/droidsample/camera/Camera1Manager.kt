@file:Suppress("DEPRECATION")

package com.example.droidsample.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Data class representing dimension dimensions for Camera1 previews and captures.
 */
data class CameraSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x${height}"
}

/**
 * Metadata snapshot for an enumerated Camera1 hardware camera.
 */
data class Camera1DeviceInfo(
    val id: Int,
    val facing: Int, // Camera.CameraInfo.CAMERA_FACING_BACK or CAMERA_FACING_FRONT
    val orientation: Int,
    val canDisableShutterSound: Boolean
)

/**
 * Comprehensive inspection parameters supported by a Camera1 device.
 */
data class Camera1ParametersInfo(
    val supportedPreviewSizes: List<CameraSize> = emptyList(),
    val supportedPictureSizes: List<CameraSize> = emptyList(),
    val supportedFocusModes: List<String> = emptyList(),
    val supportedFlashModes: List<String> = emptyList(),
    val supportedSceneModes: List<String> = emptyList(),
    val supportedWhiteBalance: List<String> = emptyList(),
    val isZoomSupported: Boolean = false,
    val maxZoom: Int = 0,
    val zoomRatios: List<Int> = emptyList(),
    val minExposureCompensation: Int = 0,
    val maxExposureCompensation: Int = 0,
    val exposureCompensationStep: Float = 0f,
    val supportedFpsRanges: List<Pair<Int, Int>> = emptyList()
)

/**
 * Reactive states for Camera1 lifecycle tracking.
 */
sealed class Camera1State {
    data object Uninitialized : Camera1State()
    data class Opened(val cameraId: Int, val parameters: Camera1ParametersInfo) : Camera1State()
    data class Previewing(val cameraId: Int, val selectedPreviewSize: CameraSize) : Camera1State()
    data class Capturing(val cameraId: Int) : Camera1State()
    data class Error(val message: String, val throwable: Throwable? = null) : Camera1State()
}

/**
 * Manages legacy android.hardware.Camera operations safely on a dedicated background thread.
 */
class Camera1Manager(private val context: Context? = null) {

    private val tag = "Camera1Manager"

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var camera: Camera? = null
    private var currentCameraId: Int = 0
    private var isPreviewing: Boolean = false

    private var pendingHolder: SurfaceHolder? = null
    private var pendingTexture: SurfaceTexture? = null
    private var pendingPreviewSize: CameraSize? = null

    private val _state = MutableStateFlow<Camera1State>(Camera1State.Uninitialized)
    val state: StateFlow<Camera1State> = _state.asStateFlow()

    private val _parametersInfo = MutableStateFlow(Camera1ParametersInfo())
    val parametersInfo: StateFlow<Camera1ParametersInfo> = _parametersInfo.asStateFlow()

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        cameraThread = HandlerThread("Camera1BackgroundThread").apply {
            start()
            cameraHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            Log.e(tag, "Error terminating camera thread", e)
        }
    }

    /**
     * Obtains the display rotation from the WindowManager.
     */
    fun getDisplayRotation(): Int {
        val ctx = context ?: return Surface.ROTATION_0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ctx.display.rotation
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    /**
     * Calculates the compensation degrees to correctly orient the preview stream.
     */
    fun calculateCameraDisplayOrientation(cameraId: Int): Int {
        val info = Camera.CameraInfo()
        try {
            Camera.getCameraInfo(cameraId, info)
        } catch (e: Exception) {
            return 90
        }
        val degrees = when (getDisplayRotation()) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            val result = (info.orientation + degrees) % 360
            (360 - result) % 360 // Compensate mirror
        } else {
            (info.orientation - degrees + 360) % 360
        }
    }

    /**
     * Calculates the rotation angle to set on Camera.Parameters for captured JPEG images.
     */
    fun calculatePictureOrientation(cameraId: Int): Int {
        val info = Camera.CameraInfo()
        try {
            Camera.getCameraInfo(cameraId, info)
        } catch (e: Exception) {
            return 0
        }
        val degrees = when (getDisplayRotation()) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (info.orientation - degrees + 360) % 360
        } else {
            (info.orientation + degrees) % 360
        }
    }

    /**
     * Enumerates all physical cameras detected by the legacy Camera subsystem.
     */
    fun getAvailableCameras(): List<Camera1DeviceInfo> {
        val count = Camera.getNumberOfCameras()
        val cameras = mutableListOf<Camera1DeviceInfo>()
        val info = Camera.CameraInfo()
        for (i in 0 until count) {
            Camera.getCameraInfo(i, info)
            val canDisableSound = try {
                info.canDisableShutterSound
            } catch (e: NoSuchFieldError) {
                false
            }
            cameras.add(Camera1DeviceInfo(i, info.facing, info.orientation, canDisableSound))
        }
        return cameras
    }

    /**
     * Opens the camera by ID on the background handler.
     */
    fun openCamera(cameraId: Int = 0, onOpened: ((Boolean) -> Unit)? = null) {
        cameraHandler?.post {
            try {
                releaseInternal()
                currentCameraId = cameraId
                val cam = Camera.open(cameraId)
                camera = cam

                // Inspect parameters
                val params = cam.parameters
                val parsedParams = extractParameters(params)
                _parametersInfo.value = parsedParams
                _state.value = Camera1State.Opened(cameraId, parsedParams)
                onOpened?.invoke(true)

                // If a preview surface was already attached, start preview immediately
                pendingHolder?.let { holder ->
                    try {
                        cam.setPreviewDisplay(holder)
                        configureAndStartPreview(cam, pendingPreviewSize)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed auto-starting pending SurfaceHolder preview", e)
                    }
                } ?: pendingTexture?.let { texture ->
                    try {
                        cam.setPreviewTexture(texture)
                        configureAndStartPreview(cam, pendingPreviewSize)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed auto-starting pending SurfaceTexture preview", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to open Camera1 ID: $cameraId", e)
                _state.value = Camera1State.Error("Failed to open camera: ${e.message}", e)
                onOpened?.invoke(false)
            }
        }
    }

    /**
     * Attaches a SurfaceHolder (e.g. from SurfaceView) and starts preview.
     */
    fun startPreviewWithHolder(holder: SurfaceHolder, previewSize: CameraSize? = null) {
        pendingHolder = holder
        pendingTexture = null
        pendingPreviewSize = previewSize

        cameraHandler?.post {
            val cam = camera ?: return@post // Will auto-start once openCamera finishes
            try {
                cam.setPreviewDisplay(holder)
                configureAndStartPreview(cam, previewSize)
            } catch (e: IOException) {
                Log.e(tag, "Error setting preview display", e)
                _state.value = Camera1State.Error("SurfaceHolder error: ${e.message}", e)
            }
        }
    }

    /**
     * Attaches a SurfaceTexture (e.g. from TextureView) and starts preview.
     */
    fun startPreviewWithTexture(texture: SurfaceTexture, previewSize: CameraSize? = null) {
        pendingTexture = texture
        pendingHolder = null
        pendingPreviewSize = previewSize

        cameraHandler?.post {
            val cam = camera ?: return@post // Will auto-start once openCamera finishes
            try {
                cam.setPreviewTexture(texture)
                configureAndStartPreview(cam, previewSize)
            } catch (e: IOException) {
                Log.e(tag, "Error setting preview texture", e)
                _state.value = Camera1State.Error("SurfaceTexture error: ${e.message}", e)
            }
        }
    }

    private fun configureAndStartPreview(cam: Camera, requestedSize: CameraSize?) {
        try {
            // Apply correct display orientation to fix 90 degree sideways tilt
            val orientation = calculateCameraDisplayOrientation(currentCameraId)
            cam.setDisplayOrientation(orientation)

            val params = cam.parameters

            // Pick optimal preview size (preferring 16:9 widescreen or closest to requested size)
            val supportedSizes = params.supportedPreviewSizes ?: emptyList()
            val chosenSize = requestedSize?.let { req ->
                supportedSizes.find { it.width == req.width && it.height == req.height }
            } ?: selectOptimalPreviewSize(supportedSizes)

            chosenSize?.let {
                params.setPreviewSize(it.width, it.height)
            }

            // Enable continuous focus if supported
            val focusModes = params.supportedFocusModes ?: emptyList()
            when {
                focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) -> {
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
                focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> {
                    params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }
            }

            cam.parameters = params
            cam.startPreview()
            isPreviewing = true

            val activeSize = chosenSize?.let { CameraSize(it.width, it.height) }
                ?: CameraSize(0, 0)
            _state.value = Camera1State.Previewing(currentCameraId, activeSize)
        } catch (e: Exception) {
            Log.e(tag, "Error configuring preview", e)
            _state.value = Camera1State.Error("Preview config failed: ${e.message}", e)
        }
    }

    private fun selectOptimalPreviewSize(sizes: List<Camera.Size>): Camera.Size? {
        if (sizes.isEmpty()) return null
        // Target 16:9 ratio with reasonable width (around 1280 or 1920)
        val targetRatio = 16.0 / 9.0
        val sorted = sizes.sortedByDescending { it.width * it.height }
        return sorted.firstOrNull {
            val ratio = it.width.toDouble() / it.height.toDouble()
            kotlin.math.abs(ratio - targetRatio) < 0.05 && it.width in 1280..1920
        } ?: sorted.firstOrNull { it.width in 1280..1920 } ?: sizes.first()
    }

    /**
     * Configures preview callback with pre-allocated buffer for zero-copy streaming.
     */
    fun setupPreviewCallbackWithBuffer(
        previewWidth: Int,
        previewHeight: Int,
        onFrameReceived: (ByteArray, Int, Int) -> Unit
    ) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            val bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21)
            val bufferSize = ((previewWidth * previewHeight * bitsPerPixel) / 8.0).toInt() + 1024

            cam.addCallbackBuffer(ByteArray(bufferSize))
            cam.addCallbackBuffer(ByteArray(bufferSize))

            cam.setPreviewCallbackWithBuffer { data, activeCam ->
                if (data != null) {
                    onFrameReceived(data, previewWidth, previewHeight)
                    activeCam.addCallbackBuffer(data)
                }
            }
        }
    }

    /**
     * Stops camera preview.
     */
    fun stopPreview() {
        cameraHandler?.post {
            try {
                camera?.stopPreview()
                isPreviewing = false
                val currentInfo = _parametersInfo.value
                _state.value = Camera1State.Opened(currentCameraId, currentInfo)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping preview", e)
            }
        }
    }

    /**
     * Triggers autofocus with completion callback.
     */
    fun triggerAutoFocus(onFocused: (Boolean) -> Unit) {
        cameraHandler?.post {
            val cam = camera ?: return@post onFocused(false)
            try {
                cam.autoFocus { success, _ ->
                    onFocused(success)
                }
            } catch (e: Exception) {
                Log.e(tag, "Autofocus failed", e)
                onFocused(false)
            }
        }
    }

    /**
     * Cancels active autofocus.
     */
    fun cancelAutoFocus() {
        cameraHandler?.post {
            try {
                camera?.cancelAutoFocus()
            } catch (e: Exception) {
                Log.e(tag, "Cancel autofocus failed", e)
            }
        }
    }

    /**
     * Updates flash mode.
     */
    fun setFlashMode(flashMode: String) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (params.supportedFlashModes?.contains(flashMode) == true) {
                    params.flashMode = flashMode
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set flash mode: $flashMode", e)
            }
        }
    }

    /**
     * Updates focus mode.
     */
    fun setFocusMode(focusMode: String) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (params.supportedFocusModes?.contains(focusMode) == true) {
                    params.focusMode = focusMode
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set focus mode: $focusMode", e)
            }
        }
    }

    /**
     * Updates digital zoom index.
     */
    fun setZoom(zoomIndex: Int) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (params.isZoomSupported && zoomIndex in 0..params.maxZoom) {
                    params.zoom = zoomIndex
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set zoom: $zoomIndex", e)
            }
        }
    }

    /**
     * Updates exposure compensation index.
     */
    fun setExposureCompensation(compensationIndex: Int) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (compensationIndex in params.minExposureCompensation..params.maxExposureCompensation) {
                    params.exposureCompensation = compensationIndex
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set exposure compensation: $compensationIndex", e)
            }
        }
    }

    /**
     * Sets white balance mode.
     */
    fun setWhiteBalance(whiteBalance: String) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (params.supportedWhiteBalance?.contains(whiteBalance) == true) {
                    params.whiteBalance = whiteBalance
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set white balance: $whiteBalance", e)
            }
        }
    }

    /**
     * Sets scene mode.
     */
    fun setSceneMode(sceneMode: String) {
        cameraHandler?.post {
            val cam = camera ?: return@post
            try {
                val params = cam.parameters
                if (params.supportedSceneModes?.contains(sceneMode) == true) {
                    params.sceneMode = sceneMode
                    cam.parameters = params
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to set scene mode: $sceneMode", e)
            }
        }
    }

    /**
     * Captures a still picture using legacy shutter and JPEG callbacks.
     */
    fun takePicture(
        onShutter: (() -> Unit)? = null,
        onPictureTaken: (ByteArray) -> Unit,
        onError: (Exception) -> Unit
    ) {
        cameraHandler?.post {
            val cam = camera ?: run {
                onError(IllegalStateException("Camera is not opened"))
                return@post
            }
            try {
                _state.value = Camera1State.Capturing(currentCameraId)

                // Apply picture orientation so captured JPEG is correctly rotated
                try {
                    val pictureRotation = calculatePictureOrientation(currentCameraId)
                    val p = cam.parameters
                    p.setRotation(pictureRotation)
                    cam.parameters = p
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set picture rotation", e)
                }

                cam.takePicture(
                    Camera.ShutterCallback { onShutter?.invoke() },
                    null, // Raw callback
                    null, // Postview callback
                    Camera.PictureCallback { data, activeCam ->
                        if (data != null) {
                            onPictureTaken(data)
                        } else {
                            onError(IllegalStateException("Captured null JPEG data"))
                        }
                        // Note: Camera1 stops preview after takePicture
                        isPreviewing = false
                        _state.value = Camera1State.Opened(currentCameraId, _parametersInfo.value)
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "takePicture failed", e)
                _state.value = Camera1State.Error("takePicture failed: ${e.message}", e)
                onError(e)
            }
        }
    }

    /**
     * Closes and frees camera hardware.
     */
    fun release() {
        cameraHandler?.post {
            releaseInternal()
            _state.value = Camera1State.Uninitialized
        }
    }

    private fun releaseInternal() {
        try {
            if (isPreviewing) {
                camera?.stopPreview()
                isPreviewing = false
            }
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.release()
            camera = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing Camera1 instance", e)
        }
    }

    /**
     * Complete lifecycle shutdown.
     */
    fun destroy() {
        release()
        stopBackgroundThread()
    }

    private fun extractParameters(params: Camera.Parameters): Camera1ParametersInfo {
        return Camera1ParametersInfo(
            supportedPreviewSizes = params.supportedPreviewSizes?.map { CameraSize(it.width, it.height) }
                ?: emptyList(),
            supportedPictureSizes = params.supportedPictureSizes?.map { CameraSize(it.width, it.height) }
                ?: emptyList(),
            supportedFocusModes = params.supportedFocusModes ?: emptyList(),
            supportedFlashModes = params.supportedFlashModes ?: emptyList(),
            supportedSceneModes = params.supportedSceneModes ?: emptyList(),
            supportedWhiteBalance = params.supportedWhiteBalance ?: emptyList(),
            isZoomSupported = params.isZoomSupported,
            maxZoom = if (params.isZoomSupported) params.maxZoom else 0,
            zoomRatios = if (params.isZoomSupported) params.zoomRatios?.toList() ?: emptyList() else emptyList(),
            minExposureCompensation = params.minExposureCompensation,
            maxExposureCompensation = params.maxExposureCompensation,
            exposureCompensationStep = params.exposureCompensationStep,
            supportedFpsRanges = params.supportedPreviewFpsRange?.map { Pair(it[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], it[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]) }
                ?: emptyList()
        )
    }
}

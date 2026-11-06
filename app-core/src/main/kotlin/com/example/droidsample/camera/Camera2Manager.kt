package com.example.droidsample.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Inspection data for Camera2 hardware capabilities.
 */
data class Camera2CharacteristicsInfo(
    val cameraId: String,
    val hardwareLevel: String,
    val lensFacing: Int, // CameraCharacteristics.LENS_FACING_BACK / FRONT / EXTERNAL
    val sensorOrientation: Int,
    val activeArraySize: Rect?,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val focusDistanceCalibration: String?,
    val isFlashAvailable: Boolean,
    val supportedAfModes: List<Int>,
    val supportedAeModes: List<Int>,
    val supportedAwbModes: List<Int>,
    val maxZoomRatio: Float,
    val minZoomRatio: Float,
    val supportedJpegSizes: List<Size>,
    val supportedYuvSizes: List<Size>,
    val supportedPreviewSizes: List<Size>
)

/**
 * Current configuration for Camera2 manual controls.
 */
data class Camera2ControlSettings(
    val aeMode: Int = CaptureRequest.CONTROL_AE_MODE_ON,
    val afMode: Int = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    val awbMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO,
    val flashMode: Int = CaptureRequest.FLASH_MODE_OFF,
    val iso: Int? = null, // Manual ISO (requires AE_MODE_OFF)
    val exposureTimeNs: Long? = null, // Manual exposure time (requires AE_MODE_OFF)
    val focusDistanceDiopters: Float? = null, // Manual focus (requires AF_MODE_OFF)
    val exposureCompensation: Int = 0, // EV compensation index
    val zoomRatio: Float = 1.0f
)

/**
 * Reactive states for Camera2 session.
 */
sealed class Camera2State {
    data object Idle : Camera2State()
    data class Opening(val cameraId: String) : Camera2State()
    data class Configured(val cameraId: String, val info: Camera2CharacteristicsInfo) : Camera2State()
    data class Previewing(val cameraId: String, val settings: Camera2ControlSettings) : Camera2State()
    data class PhotoCaptured(val jpegBytes: ByteArray) : Camera2State()
    data class Error(val message: String, val errorType: Int = 0) : Camera2State()
}

/**
 * Low-level camera controller utilizing android.hardware.camera2 API with full manual AE/AF/AWB/Zoom.
 */
class Camera2Manager(private val context: Context) {

    private val tag = "Camera2Manager"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReaderJpeg: ImageReader? = null
    private var previewSurface: Surface? = null
    private var pendingSurface: Surface? = null
    private var pendingCaptureWidth: Int = 1920
    private var pendingCaptureHeight: Int = 1080

    private var activeCameraId: String = ""
    private var activeCharacteristics: CameraCharacteristics? = null

    private val _state = MutableStateFlow<Camera2State>(Camera2State.Idle)
    val state: StateFlow<Camera2State> = _state.asStateFlow()

    private val _settings = MutableStateFlow(Camera2ControlSettings())
    val settings: StateFlow<Camera2ControlSettings> = _settings.asStateFlow()

    private val _cameraList = MutableStateFlow<List<String>>(emptyList())
    val cameraList: StateFlow<List<String>> = _cameraList.asStateFlow()

    private val availabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            refreshCameraList()
        }

        override fun onCameraUnavailable(cameraId: String) {
            refreshCameraList()
        }
    }

    init {
        startBackgroundThread()
        cameraManager.registerAvailabilityCallback(availabilityCallback, backgroundHandler)
        refreshCameraList()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera2BackgroundThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(tag, "Failed to stop background thread", e)
        }
    }

    fun refreshCameraList() {
        try {
            _cameraList.value = cameraManager.cameraIdList.toList()
        } catch (e: CameraAccessException) {
            Log.e(tag, "Failed to query camera list", e)
        }
    }

    /**
     * Inspects camera characteristics for a specific camera ID.
     */
    fun getCameraCharacteristicsInfo(cameraId: String): Camera2CharacteristicsInfo? {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val levelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val hardwareLevelStr = when (levelInt) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN ($levelInt)"
            }

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList() ?: emptyList()

            val minZoom: Float
            val maxZoom: Float
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                minZoom = zoomRange?.lower ?: 1.0f
                maxZoom = zoomRange?.upper ?: 1.0f
            } else {
                val maxCrop = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                minZoom = 1.0f
                maxZoom = maxCrop
            }

            val calibrationInt = chars.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
            val calibStr = when (calibrationInt) {
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> "UNCALIBRATED"
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> "APPROXIMATE"
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> "CALIBRATED"
                else -> "NONE"
            }

            Camera2CharacteristicsInfo(
                cameraId = cameraId,
                hardwareLevel = hardwareLevelStr,
                lensFacing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK,
                sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                activeArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureTimeRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                focusDistanceCalibration = calibStr,
                isFlashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false,
                supportedAfModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList(),
                supportedAeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toList() ?: emptyList(),
                supportedAwbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList(),
                maxZoomRatio = maxZoom,
                minZoomRatio = minZoom,
                supportedJpegSizes = jpegSizes,
                supportedYuvSizes = yuvSizes,
                supportedPreviewSizes = previewSizes
            )
        } catch (e: Exception) {
            Log.e(tag, "Error reading characteristics for camera $cameraId", e)
            null
        }
    }

    /**
     * Obtains the display rotation from the WindowManager.
     */
    fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display.rotation
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    /**
     * Queries the optimal preview size for a given camera ID matching target aspect ratio.
     */
    fun getOptimalPreviewSize(cameraId: String, targetRatio: Float = 16f / 9f): Size? {
        val chars = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            return null
        }
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return null
        return sizes.minByOrNull {
            val ratio = it.width.toFloat() / it.height.toFloat()
            kotlin.math.abs(ratio - targetRatio)
        } ?: sizes.firstOrNull()
    }

    /**
     * Opens camera device asynchronously with CameraDevice.StateCallback.
     */
    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String) {
        val handler = backgroundHandler ?: return
        handler.post {
            val savedSurface = previewSurface ?: pendingSurface
            closeCameraInternal()
            pendingSurface = savedSurface
            activeCameraId = cameraId
            _state.value = Camera2State.Opening(cameraId)

            try {
                activeCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
                val info = getCameraCharacteristicsInfo(cameraId)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        if (info != null) {
                            _state.value = Camera2State.Configured(cameraId, info)
                        }
                        // Automatically start preview if a surface was already provided
                        val surface = pendingSurface
                        if (surface != null && surface.isValid) {
                            startPreviewInternal(camera, surface, pendingCaptureWidth, pendingCaptureHeight)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        _state.value = Camera2State.Idle
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        _state.value = Camera2State.Error("Camera open failed with code $error", error)
                    }
                }, handler)
            } catch (e: Exception) {
                Log.e(tag, "Failed to open camera2 device: $cameraId", e)
                _state.value = Camera2State.Error("Exception opening camera: ${e.message}")
            }
        }
    }

    /**
     * Starts preview session with the given Surface (e.g. from TextureView / SurfaceView).
     */
    fun startPreview(surface: Surface, captureWidth: Int = 1920, captureHeight: Int = 1080) {
        pendingSurface = surface
        pendingCaptureWidth = captureWidth
        pendingCaptureHeight = captureHeight
        previewSurface = surface

        val handler = backgroundHandler ?: return
        handler.post {
            val device = cameraDevice ?: return@post // Will auto-start in onOpened once camera opens
            startPreviewInternal(device, surface, captureWidth, captureHeight)
        }
    }

    private fun startPreviewInternal(device: CameraDevice, surface: Surface, captureWidth: Int, captureHeight: Int) {
        val handler = backgroundHandler ?: return
        previewSurface = surface

        // Setup ImageReader for still JPEG capture using supported size
        imageReaderJpeg?.close()
        val map = activeCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        val chosenJpeg = jpegSizes.find { it.width == captureWidth && it.height == captureHeight }
            ?: jpegSizes.maxByOrNull { it.width * it.height }
            ?: Size(captureWidth, captureHeight)

        val reader = ImageReader.newInstance(chosenJpeg.width, chosenJpeg.height, ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ ir ->
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            _state.value = Camera2State.PhotoCaptured(bytes)
        }, handler)
        imageReaderJpeg = reader

        try {
            val targets = listOf(surface, reader.surface)
            @Suppress("DEPRECATION")
            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                        }
                        previewRequestBuilder = builder
                        applyControlSettings(builder, _settings.value)

                        session.setRepeatingRequest(builder.build(), null, handler)
                        _state.value = Camera2State.Previewing(activeCameraId, _settings.value)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to start repeating preview request", e)
                        _state.value = Camera2State.Error("Preview request failed: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    _state.value = Camera2State.Error("CaptureSession configuration failed")
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(tag, "createCaptureSession failed", e)
            _state.value = Camera2State.Error("Session creation error: ${e.message}")
        }
    }

    /**
     * Updates one or more manual/auto controls and updates repeating request.
     */
    fun updateControls(update: (Camera2ControlSettings) -> Camera2ControlSettings) {
        val newSettings = update(_settings.value)
        _settings.value = newSettings

        val handler = backgroundHandler ?: return
        handler.post {
            val session = captureSession ?: return@post
            val builder = previewRequestBuilder ?: return@post

            applyControlSettings(builder, newSettings)
            try {
                session.setRepeatingRequest(builder.build(), null, handler)
                _state.value = Camera2State.Previewing(activeCameraId, newSettings)
            } catch (e: Exception) {
                Log.e(tag, "Failed to apply updated controls", e)
            }
        }
    }

    private fun applyControlSettings(builder: CaptureRequest.Builder, settings: Camera2ControlSettings) {
        val chars = activeCharacteristics

        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // 1. AE Mode & Manual Exposure
        builder.set(CaptureRequest.CONTROL_AE_MODE, settings.aeMode)
        if (settings.aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
            settings.iso?.let { iso ->
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            }
            settings.exposureTimeNs?.let { exposureNs ->
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
            }
        } else {
            // Exposure compensation when AE is active
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, settings.exposureCompensation)
        }

        // 2. AF Mode & Manual Focus
        builder.set(CaptureRequest.CONTROL_AF_MODE, settings.afMode)
        if (settings.afMode == CaptureRequest.CONTROL_AF_MODE_OFF) {
            settings.focusDistanceDiopters?.let { diopters ->
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
            }
        }

        // 3. AWB Mode
        builder.set(CaptureRequest.CONTROL_AWB_MODE, settings.awbMode)

        // 4. Flash / Torch Mode
        builder.set(CaptureRequest.FLASH_MODE, settings.flashMode)

        // 5. Zoom Ratio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, settings.zoomRatio)
        } else {
            // Crop region fallback
            chars?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { activeArray ->
                val zoom = settings.zoomRatio.coerceAtLeast(1.0f)
                val cropWidth = (activeArray.width() / zoom).toInt()
                val cropHeight = (activeArray.height() / zoom).toInt()
                val left = (activeArray.width() - cropWidth) / 2
                val top = (activeArray.height() - cropHeight) / 2
                val cropRect = Rect(left, top, left + cropWidth, top + cropHeight)
                builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            }
        }
    }

    /**
     * Captures a single still photo with current settings.
     */
    fun takePicture() {
        val handler = backgroundHandler ?: return
        handler.post {
            val device = cameraDevice ?: return@post
            val session = captureSession ?: return@post
            val reader = imageReaderJpeg ?: return@post

            try {
                val stillBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                }
                applyControlSettings(stillBuilder, _settings.value)
                stillBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())

                session.capture(stillBuilder.build(), null, handler)
            } catch (e: Exception) {
                Log.e(tag, "Failed to capture still image", e)
            }
        }
    }

    private fun getJpegOrientation(): Int {
        val sensorOrientation = activeCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val facing = activeCharacteristics?.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val displayRotation = getDisplayRotation()
        val degrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation + degrees) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    /**
     * Closes the active session and device.
     */
    fun closeCamera() {
        val handler = backgroundHandler ?: return
        handler.post {
            pendingSurface = null
            closeCameraInternal()
            _state.value = Camera2State.Idle
        }
    }

    private fun closeCameraInternal() {
        try {
            captureSession?.abortCaptures()
            captureSession?.close()
            captureSession = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing capture session", e)
        }

        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing camera device", e)
        }

        imageReaderJpeg?.close()
        imageReaderJpeg = null
        previewSurface = null
        previewRequestBuilder = null
    }

    fun destroy() {
        try {
            cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering availability callback", e)
        }
        closeCamera()
        stopBackgroundThread()
    }
}

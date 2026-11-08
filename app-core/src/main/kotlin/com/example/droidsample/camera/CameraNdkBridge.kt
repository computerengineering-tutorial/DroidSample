package com.example.droidsample.camera

import android.util.Log
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

/**
 * Android NDK Camera2 API Constants as specified in:
 * <camera/NdkCameraMetadataTags.h>, <camera/NdkCameraError.h>, and <camera/NdkCaptureRequest.h>.
 */
object NdkCameraConstants {
    // Lens Facing
    const val ACAMERA_LENS_FACING_FRONT: Int = 0
    const val ACAMERA_LENS_FACING_BACK: Int = 1
    const val ACAMERA_LENS_FACING_EXTERNAL: Int = 2

    // Hardware Levels
    const val ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: Int = 0
    const val ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_FULL: Int = 1
    const val ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: Int = 2
    const val ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_3: Int = 3
    const val ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: Int = 4

    // Control AE Modes
    const val ACAMERA_CONTROL_AE_MODE_OFF: Int = 0
    const val ACAMERA_CONTROL_AE_MODE_ON: Int = 1
    const val ACAMERA_CONTROL_AE_MODE_ON_AUTO_FLASH: Int = 2
    const val ACAMERA_CONTROL_AE_MODE_ON_ALWAYS_FLASH: Int = 3
    const val ACAMERA_CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE: Int = 4
    const val ACAMERA_CONTROL_AE_MODE_ON_EXTERNAL_FLASH: Int = 5

    // Control AF Modes
    const val ACAMERA_CONTROL_AF_MODE_OFF: Int = 0
    const val ACAMERA_CONTROL_AF_MODE_AUTO: Int = 1
    const val ACAMERA_CONTROL_AF_MODE_MACRO: Int = 2
    const val ACAMERA_CONTROL_AF_MODE_CONTINUOUS_VIDEO: Int = 3
    const val ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE: Int = 4
    const val ACAMERA_CONTROL_AF_MODE_EDOF: Int = 5

    // Control AWB Modes
    const val ACAMERA_CONTROL_AWB_MODE_OFF: Int = 0
    const val ACAMERA_CONTROL_AWB_MODE_AUTO: Int = 1
    const val ACAMERA_CONTROL_AWB_MODE_INCANDESCENT: Int = 2
    const val ACAMERA_CONTROL_AWB_MODE_FLUORESCENT: Int = 3
    const val ACAMERA_CONTROL_AWB_MODE_WARM_FLUORESCENT: Int = 4
    const val ACAMERA_CONTROL_AWB_MODE_DAYLIGHT: Int = 5
    const val ACAMERA_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT: Int = 6
    const val ACAMERA_CONTROL_AWB_MODE_TWILIGHT: Int = 7
    const val ACAMERA_CONTROL_AWB_MODE_SHADE: Int = 8

    // Flash Modes
    const val ACAMERA_FLASH_MODE_OFF: Int = 0
    const val ACAMERA_FLASH_MODE_SINGLE: Int = 1
    const val ACAMERA_FLASH_MODE_TORCH: Int = 2

    // Optical Image Stabilization (OIS)
    const val ACAMERA_LENS_OPTICAL_STABILIZATION_MODE_OFF: Int = 0
    const val ACAMERA_LENS_OPTICAL_STABILIZATION_MODE_ON: Int = 1

    // Error Codes (<camera/NdkCameraError.h>)
    const val ACAMERA_OK: Int = 0
    const val ACAMERA_ERROR_BASE: Int = -10000
    const val ACAMERA_ERROR_UNKNOWN: Int = ACAMERA_ERROR_BASE
    const val ACAMERA_ERROR_INVALID_PARAMETER: Int = ACAMERA_ERROR_BASE - 1
    const val ACAMERA_ERROR_CAMERA_DISCONNECTED: Int = ACAMERA_ERROR_BASE - 2
    const val ACAMERA_ERROR_NOT_ENOUGH_MEMORY: Int = ACAMERA_ERROR_BASE - 3
    const val ACAMERA_ERROR_METADATA_NOT_FOUND: Int = ACAMERA_ERROR_BASE - 4
    const val ACAMERA_ERROR_CAMERA_DEVICE: Int = ACAMERA_ERROR_BASE - 5
    const val ACAMERA_ERROR_CAMERA_SERVICE: Int = ACAMERA_ERROR_BASE - 6
    const val ACAMERA_ERROR_SESSION_CLOSED: Int = ACAMERA_ERROR_BASE - 7
    const val ACAMERA_ERROR_INVALID_OPERATION: Int = ACAMERA_ERROR_BASE - 8
    const val ACAMERA_ERROR_STREAM_CONFIGURE_FAIL: Int = ACAMERA_ERROR_BASE - 9
    const val ACAMERA_ERROR_CAMERA_IN_USE: Int = ACAMERA_ERROR_BASE - 10
    const val ACAMERA_ERROR_MAX_CAMERA_IN_USE: Int = ACAMERA_ERROR_BASE - 11
    const val ACAMERA_ERROR_CAMERA_DISABLED: Int = ACAMERA_ERROR_BASE - 12
    const val ACAMERA_ERROR_PERMISSION_DENIED: Int = ACAMERA_ERROR_BASE - 13
    const val ACAMERA_ERROR_UNSUPPORTED_OPERATION: Int = ACAMERA_ERROR_BASE - 14

    fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        ACAMERA_OK -> "Success"
        ACAMERA_ERROR_UNKNOWN -> "Unknown native camera error"
        ACAMERA_ERROR_INVALID_PARAMETER -> "Invalid parameter passed to NDK camera function"
        ACAMERA_ERROR_CAMERA_DISCONNECTED -> "Camera device disconnected"
        ACAMERA_ERROR_NOT_ENOUGH_MEMORY -> "Insufficient native memory"
        ACAMERA_ERROR_METADATA_NOT_FOUND -> "Requested metadata tag not found"
        ACAMERA_ERROR_CAMERA_DEVICE -> "Fatal camera device error"
        ACAMERA_ERROR_CAMERA_SERVICE -> "Fatal camera service error"
        ACAMERA_ERROR_SESSION_CLOSED -> "Capture session already closed"
        ACAMERA_ERROR_INVALID_OPERATION -> "Invalid internal camera operation"
        ACAMERA_ERROR_STREAM_CONFIGURE_FAIL -> "Native stream configuration failed"
        ACAMERA_ERROR_CAMERA_IN_USE -> "Camera device already in use by higher priority client"
        ACAMERA_ERROR_MAX_CAMERA_IN_USE -> "Maximum open camera resource limit reached"
        ACAMERA_ERROR_CAMERA_DISABLED -> "Camera disabled by device policy"
        ACAMERA_ERROR_PERMISSION_DENIED -> "Camera permission denied"
        ACAMERA_ERROR_UNSUPPORTED_OPERATION -> "Operation unsupported on this device"
        else -> "Native error ($errorCode)"
    }
}

/**
 * Data model for NDK Camera device information retrieved from native ACameraMetadata.
 */
data class NdkCameraInfo(
    val cameraId: String,
    val facing: Int = NdkCameraConstants.ACAMERA_LENS_FACING_BACK,
    val hardwareLevel: Int = NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
    val sensorOrientation: Int = 90,
    val hasFlash: Boolean = true,
    val isoRange: Pair<Int, Int>? = Pair(100, 3200),
    val exposureTimeRangeNs: Pair<Long, Long>? = Pair(100_000L, 1_000_000_000L),
    val minFocusDistance: Float = 10.0f,
    val maxDigitalZoom: Float = 8.0f,
    val supportsOis: Boolean = true,
    val supportedPreviewSizes: List<CameraSize> = listOf(
        CameraSize(1920, 1080),
        CameraSize(1280, 720),
        CameraSize(640, 480)
    ),
    val supportedJpegSizes: List<CameraSize> = listOf(
        CameraSize(4000, 3000),
        CameraSize(1920, 1080),
        CameraSize(1280, 720)
    ),
    val supportedAfModes: List<Int> = listOf(
        NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE,
        NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_AUTO,
        NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_OFF
    ),
    val supportedAeModes: List<Int> = listOf(
        NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON,
        NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_OFF,
        NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON_AUTO_FLASH
    ),
    val supportedAwbModes: List<Int> = listOf(
        NdkCameraConstants.ACAMERA_CONTROL_AWB_MODE_AUTO,
        NdkCameraConstants.ACAMERA_CONTROL_AWB_MODE_INCANDESCENT,
        NdkCameraConstants.ACAMERA_CONTROL_AWB_MODE_DAYLIGHT,
        NdkCameraConstants.ACAMERA_CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
    )
) {
    val facingName: String
        get() = when (facing) {
            NdkCameraConstants.ACAMERA_LENS_FACING_FRONT -> "Front"
            NdkCameraConstants.ACAMERA_LENS_FACING_BACK -> "Back"
            NdkCameraConstants.ACAMERA_LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

    val hardwareLevelName: String
        get() = when (hardwareLevel) {
            NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
}

/**
 * Live configuration for NDK Camera2 manual controls.
 */
data class NdkCameraControlSettings(
    val aeMode: Int = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON,
    val afMode: Int = NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    val awbMode: Int = NdkCameraConstants.ACAMERA_CONTROL_AWB_MODE_AUTO,
    val flashMode: Int = NdkCameraConstants.ACAMERA_FLASH_MODE_OFF,
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val exposureCompensation: Int = 0,
    val zoomRatio: Float = 1.0f,
    val oisEnabled: Boolean = false
)

/**
 * Real-time capture and streaming telemetry reported from NDK capture callbacks.
 */
data class NdkCaptureStats(
    val frameCount: Long = 0L,
    val fps: Float = 0.0f,
    val lastTimestampNs: Long = 0L,
    val droppedFrames: Long = 0L,
    val lastExposureDurationNs: Long = 0L,
    val lastIso: Int = 0
)

/**
 * Reactive states representing the NDK Camera lifecycle.
 */
sealed class NdkCameraState {
    data object Idle : NdkCameraState()
    data class Opening(val cameraId: String) : NdkCameraState()
    data class Opened(val cameraId: String, val info: NdkCameraInfo) : NdkCameraState()
    data class Streaming(val cameraId: String, val settings: NdkCameraControlSettings, val stats: NdkCaptureStats) : NdkCameraState()
    data class PhotoCaptured(val cameraId: String, val jpegBytes: ByteArray) : NdkCameraState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PhotoCaptured
            return cameraId == other.cameraId && jpegBytes.contentEquals(other.jpegBytes)
        }
        override fun hashCode(): Int {
            var result = cameraId.hashCode()
            result = 31 * result + jpegBytes.contentHashCode()
            return result
        }
    }
    data class Error(val code: Int, val message: String) : NdkCameraState()
}

/**
 * JNI DTO filled by native C++ code in nativePopulateCharacteristics.
 */
internal class NativeMetadataHolder {
    var facing: Int = 1
    var hardwareLevel: Int = 1
    var sensorOrientation: Int = 90
    var hasFlash: Boolean = true
    var minIso: Int = 100
    var maxIso: Int = 3200
    var minExposureNs: Long = 100_000L
    var maxExposureNs: Long = 1_000_000_000L
    var minFocusDistance: Float = 10.0f
    var maxDigitalZoom: Float = 8.0f
    var supportsOis: Boolean = true
    var previewResolutions: IntArray = intArrayOf()
    var jpegResolutions: IntArray = intArrayOf()
    var supportedAfModes: IntArray = intArrayOf()
    var supportedAeModes: IntArray = intArrayOf()
    var supportedAwbModes: IntArray = intArrayOf()
}

/**
 * Production-ready JNI Bridge to Android NDK Camera2 (libcamera2ndk.so).
 *
 * Implements full NDK Camera features:
 * - ACameraManager (listing, callbacks, characteristics)
 * - ACameraDevice (lifecycle, session configurations)
 * - ACameraCaptureSession (repeating requests, still capture, abort, close)
 * - ACaptureRequest (manual AE, AF, AWB, Zoom, EV, OIS, Flash)
 * - AImageReader (real-time JPEG capture plane buffer acquisition)
 * - ANativeWindow (direct hardware Surface rendering)
 *
 * Includes automatic high-fidelity mock fallback for testing without native hardware.
 */
class CameraNdkBridge {

    private val tag = "CameraNdkBridge"
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var statsJob: Job? = null

    var isNativeLoaded: Boolean = false
        private set

    private var nativeEngineHandle: Long = 0L
    private var activeCameraId: String = ""
    private var activeInfo: NdkCameraInfo? = null

    private val _state = MutableStateFlow<NdkCameraState>(NdkCameraState.Idle)
    val state: StateFlow<NdkCameraState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(NdkCameraControlSettings())
    val settings: StateFlow<NdkCameraControlSettings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(NdkCaptureStats())
    val stats: StateFlow<NdkCaptureStats> = _stats.asStateFlow()

    // Mock mode telemetry state
    private var mockFrameCounter = 0L
    private var mockIsStreaming = false

    private fun logI(msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }
    private fun logW(msg: String, tr: Throwable? = null) {
        try { Log.w(tag, msg, tr) } catch (_: Throwable) { println("[$tag] $msg") }
    }
    private fun logD(msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }
    private fun logE(msg: String) {
        try { Log.e(tag, msg) } catch (_: Throwable) { System.err.println("[$tag] $msg") }
    }

    init {
        try {
            System.loadLibrary("camera2ndk_sample")
            isNativeLoaded = true
            logI("Native library 'camera2ndk_sample' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            logW("Native library 'camera2ndk_sample' not found. Operating in high-fidelity mock mode.", e)
            isNativeLoaded = false
        }
    }

    /**
     * Initializes the native CameraEngine.
     */
    fun initialize(): Boolean {
        if (!isNativeLoaded) {
            logD("Mock NDK Engine initialized.")
            _state.value = NdkCameraState.Idle
            return true
        }

        if (nativeEngineHandle == 0L) {
            nativeInit()
            nativeEngineHandle = nativeCreateEngine()
            if (nativeEngineHandle == 0L) {
                logE("Failed to create native CameraEngine instance")
                _state.value = NdkCameraState.Error(
                    NdkCameraConstants.ACAMERA_ERROR_NOT_ENOUGH_MEMORY,
                    "Failed to create native CameraEngine"
                )
                return false
            }
        }
        _state.value = NdkCameraState.Idle
        return true
    }

    /**
     * Retrieves the list of available camera IDs.
     */
    fun getCameraIdList(): List<String> {
        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            return listOf("0", "1") // Mock Back (0) and Front (1)
        }
        val array = nativeGetCameraIdList(nativeEngineHandle)
        return array.toList()
    }

    /**
     * Queries detailed camera characteristics from native ACameraMetadata.
     */
    fun getCameraCharacteristicsInfo(cameraId: String): NdkCameraInfo {
        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            val isBack = cameraId == "0"
            return NdkCameraInfo(
                cameraId = cameraId,
                facing = if (isBack) NdkCameraConstants.ACAMERA_LENS_FACING_BACK else NdkCameraConstants.ACAMERA_LENS_FACING_FRONT,
                hardwareLevel = NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                sensorOrientation = if (isBack) 90 else 270,
                hasFlash = isBack,
                isoRange = Pair(50, 6400),
                exposureTimeRangeNs = Pair(50_000L, 2_000_000_000L),
                minFocusDistance = if (isBack) 10.0f else 0.0f,
                maxDigitalZoom = 10.0f,
                supportsOis = isBack,
                supportedPreviewSizes = listOf(
                    CameraSize(1920, 1080),
                    CameraSize(1280, 720),
                    CameraSize(640, 480)
                ),
                supportedJpegSizes = listOf(
                    CameraSize(4000, 3000),
                    CameraSize(1920, 1080),
                    CameraSize(1280, 720)
                )
            )
        }

        val holder = NativeMetadataHolder()
        val success = nativePopulateCharacteristics(nativeEngineHandle, cameraId, holder)
        if (!success) {
            return NdkCameraInfo(cameraId = cameraId)
        }

        val previewSizes = mutableListOf<CameraSize>()
        for (i in 0 until holder.previewResolutions.size - 1 step 2) {
            previewSizes.add(CameraSize(holder.previewResolutions[i], holder.previewResolutions[i + 1]))
        }

        val jpegSizes = mutableListOf<CameraSize>()
        for (i in 0 until holder.jpegResolutions.size - 1 step 2) {
            jpegSizes.add(CameraSize(holder.jpegResolutions[i], holder.jpegResolutions[i + 1]))
        }

        return NdkCameraInfo(
            cameraId = cameraId,
            facing = holder.facing,
            hardwareLevel = holder.hardwareLevel,
            sensorOrientation = holder.sensorOrientation,
            hasFlash = holder.hasFlash,
            isoRange = Pair(holder.minIso, holder.maxIso),
            exposureTimeRangeNs = Pair(holder.minExposureNs, holder.maxExposureNs),
            minFocusDistance = holder.minFocusDistance,
            maxDigitalZoom = holder.maxDigitalZoom,
            supportsOis = holder.supportsOis,
            supportedPreviewSizes = if (previewSizes.isNotEmpty()) previewSizes else listOf(CameraSize(1920, 1080)),
            supportedJpegSizes = if (jpegSizes.isNotEmpty()) jpegSizes else listOf(CameraSize(1920, 1080)),
            supportedAfModes = holder.supportedAfModes.toList(),
            supportedAeModes = holder.supportedAeModes.toList(),
            supportedAwbModes = holder.supportedAwbModes.toList()
        )
    }

    /**
     * Opens the camera device asynchronously and transitions to Opened state.
     */
    fun openCamera(cameraId: String): Boolean {
        _state.value = NdkCameraState.Opening(cameraId)
        activeCameraId = cameraId
        val info = getCameraCharacteristicsInfo(cameraId)
        activeInfo = info

        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            logD("Mock NDK: Camera $cameraId opened")
            _state.value = NdkCameraState.Opened(cameraId, info)
            return true
        }

        val opened = nativeOpenCamera(nativeEngineHandle, cameraId)
        return if (opened) {
            _state.value = NdkCameraState.Opened(cameraId, info)
            true
        } else {
            _state.value = NdkCameraState.Error(
                NdkCameraConstants.ACAMERA_ERROR_CAMERA_DEVICE,
                "Failed to open native camera $cameraId"
            )
            false
        }
    }

    /**
     * Binds a preview Surface (ANativeWindow) and creates the native capture session.
     */
    fun setPreviewSurface(
        surface: Surface,
        previewWidth: Int = 1920,
        previewHeight: Int = 1080,
        captureWidth: Int = 1920,
        captureHeight: Int = 1080
    ): Boolean {
        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            logD("Mock NDK: Preview surface set (${previewWidth}x$previewHeight)")
            return true
        }

        val surfaceSet = nativeSetPreviewSurface(nativeEngineHandle, surface, previewWidth, previewHeight)
        if (!surfaceSet) return false

        return nativeCreateSession(nativeEngineHandle, previewWidth, previewHeight, captureWidth, captureHeight)
    }

    /**
     * Starts repeating capture request stream.
     */
    fun startStreaming(): Boolean {
        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            mockIsStreaming = true
            _state.value = NdkCameraState.Streaming(activeCameraId, _settings.value, _stats.value)
            startStatsPolling()
            return true
        }

        val success = nativeStartStreaming(nativeEngineHandle)
        if (success) {
            _state.value = NdkCameraState.Streaming(activeCameraId, _settings.value, _stats.value)
            startStatsPolling()
        }
        return success
    }

    /**
     * Stops repeating stream.
     */
    fun stopStreaming(): Boolean {
        statsJob?.cancel()
        statsJob = null

        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            mockIsStreaming = false
            activeInfo?.let { _state.value = NdkCameraState.Opened(activeCameraId, it) }
            return true
        }

        val success = nativeStopStreaming(nativeEngineHandle)
        activeInfo?.let { _state.value = NdkCameraState.Opened(activeCameraId, it) }
        return success
    }

    /**
     * Dynamically updates manual Pro controls on the repeating stream.
     */
    fun updateSettings(newSettings: NdkCameraControlSettings): Boolean {
        _settings.value = newSettings

        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            if (_state.value is NdkCameraState.Streaming) {
                _state.value = NdkCameraState.Streaming(activeCameraId, newSettings, _stats.value)
            }
            return true
        }

        val success = nativeUpdateControls(
            nativeEngineHandle,
            aeMode = newSettings.aeMode,
            afMode = newSettings.afMode,
            awbMode = newSettings.awbMode,
            flashMode = newSettings.flashMode,
            iso = newSettings.iso ?: -1,
            exposureTimeNs = newSettings.exposureTimeNs ?: -1L,
            focusDistance = newSettings.focusDistanceDiopters ?: -1.0f,
            exposureComp = newSettings.exposureCompensation,
            zoomRatio = newSettings.zoomRatio,
            ois = newSettings.oisEnabled
        )

        if (_state.value is NdkCameraState.Streaming) {
            _state.value = NdkCameraState.Streaming(activeCameraId, newSettings, _stats.value)
        }
        return success
    }

    /**
     * Triggers Auto-Focus (AF) metering convergence.
     */
    fun triggerAf(): Boolean {
        if (!isNativeLoaded || nativeEngineHandle == 0L) return true
        return nativeTriggerAf(nativeEngineHandle, cancel = false)
    }

    /**
     * Cancels Auto-Focus (AF) trigger.
     */
    fun cancelAf(): Boolean {
        if (!isNativeLoaded || nativeEngineHandle == 0L) return true
        return nativeTriggerAf(nativeEngineHandle, cancel = true)
    }

    /**
     * Captures a still frame via native AImageReader.
     */
    fun captureStill(onCaptured: ((ByteArray?) -> Unit)? = null): ByteArray? {
        if (!isNativeLoaded || nativeEngineHandle == 0L) {
            val mockJpeg = generateMockJpegBytes()
            _state.value = NdkCameraState.PhotoCaptured(activeCameraId, mockJpeg)
            onCaptured?.invoke(mockJpeg)
            return mockJpeg
        }

        val triggered = nativeCaptureStill(nativeEngineHandle)
        if (!triggered) {
            onCaptured?.invoke(null)
            return null
        }

        // Retrieve latest captured JPEG bytes
        val bytes = nativeGetLatestCapturedImage(nativeEngineHandle)
        if (bytes != null && bytes.isNotEmpty()) {
            _state.value = NdkCameraState.PhotoCaptured(activeCameraId, bytes)
            onCaptured?.invoke(bytes)
            return bytes
        }

        onCaptured?.invoke(null)
        return null
    }

    /**
     * Closes native camera device and frees all allocated session resources.
     */
    fun close() {
        statsJob?.cancel()
        statsJob = null
        mockIsStreaming = false

        if (isNativeLoaded && nativeEngineHandle != 0L) {
            nativeCloseCamera(nativeEngineHandle)
            nativeDestroyEngine(nativeEngineHandle)
            nativeEngineHandle = 0L
        }

        activeCameraId = ""
        activeInfo = null
        _state.value = NdkCameraState.Idle
        _stats.value = NdkCaptureStats()
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(500)
                if (!isNativeLoaded || nativeEngineHandle == 0L) {
                    if (mockIsStreaming) {
                        mockFrameCounter += 15
                        val currentSettings = _settings.value
                        val newStats = NdkCaptureStats(
                            frameCount = mockFrameCounter,
                            fps = 30.0f,
                            lastTimestampNs = System.nanoTime(),
                            droppedFrames = 0L,
                            lastExposureDurationNs = currentSettings.exposureTimeNs ?: 33_333_333L,
                            lastIso = currentSettings.iso ?: 400
                        )
                        _stats.value = newStats
                        if (_state.value is NdkCameraState.Streaming) {
                            _state.value = NdkCameraState.Streaming(activeCameraId, currentSettings, newStats)
                        }
                    }
                } else {
                    val rawStats = nativeGetCaptureStats(nativeEngineHandle)
                    if (rawStats != null && rawStats.size >= 6) {
                        val newStats = NdkCaptureStats(
                            frameCount = rawStats[0].toLong(),
                            fps = rawStats[1].toFloat(),
                            lastTimestampNs = rawStats[2].toLong(),
                            droppedFrames = rawStats[3].toLong(),
                            lastExposureDurationNs = rawStats[4].toLong(),
                            lastIso = rawStats[5].toInt()
                        )
                        _stats.value = newStats
                        if (_state.value is NdkCameraState.Streaming) {
                            _state.value = NdkCameraState.Streaming(activeCameraId, _settings.value, newStats)
                        }
                    }
                }
            }
        }
    }

    private fun generateMockJpegBytes(): ByteArray {
        // Simple valid JPEG SOI/EOI mock marker array
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(),
            0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x48.toByte(),
            0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    // Native JNI External Declarations
    private external fun nativeInit(): Boolean
    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeGetCameraIdList(handle: Long): Array<String>
    private external fun nativePopulateCharacteristics(handle: Long, cameraId: String, holder: Any): Boolean
    private external fun nativeOpenCamera(handle: Long, cameraId: String): Boolean
    private external fun nativeSetPreviewSurface(handle: Long, surface: Surface?, width: Int, height: Int): Boolean
    private external fun nativeCreateSession(handle: Long, previewW: Int, previewH: Int, capW: Int, capH: Int): Boolean
    private external fun nativeStartStreaming(handle: Long): Boolean
    private external fun nativeStopStreaming(handle: Long): Boolean
    private external fun nativeUpdateControls(
        handle: Long,
        aeMode: Int,
        afMode: Int,
        awbMode: Int,
        flashMode: Int,
        iso: Int,
        exposureTimeNs: Long,
        focusDistance: Float,
        exposureComp: Int,
        zoomRatio: Float,
        ois: Boolean
    ): Boolean
    private external fun nativeTriggerAf(handle: Long, cancel: Boolean): Boolean
    private external fun nativeCaptureStill(handle: Long): Boolean
    private external fun nativeGetLatestCapturedImage(handle: Long): ByteArray?
    private external fun nativeGetCaptureStats(handle: Long): DoubleArray?
    private external fun nativeCloseCamera(handle: Long)
}

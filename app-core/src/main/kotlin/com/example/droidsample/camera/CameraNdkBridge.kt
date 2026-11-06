package com.example.droidsample.camera

import android.util.Log
import android.view.Surface

/**
 * Data model for NDK Camera device information retrieved from native ACameraMetadata.
 */
data class NdkCameraInfo(
    val cameraId: String,
    val facing: Int,
    val hardwareLevel: Int,
    val hasFlash: Boolean
)

/**
 * JNI Bridge interface to Android NDK Camera2 (libcamera2ndk.so).
 *
 * Native Architecture Overview:
 * In C/C++ (NDK API Level 24+), camera access is provided through:
 * 1. `<camera/NdkCameraManager.h>`:
 *    - `ACameraManager_create()`
 *    - `ACameraManager_getCameraIdList()`
 *    - `ACameraManager_getCameraCharacteristics()`
 *    - `ACameraManager_openCamera()`
 * 2. `<camera/NdkCameraDevice.h>`:
 *    - `ACameraDevice_createCaptureSession()`
 *    - `ACameraDevice_close()`
 * 3. `<camera/NdkCameraCaptureSession.h>`:
 *    - `ACameraCaptureSession_setRepeatingRequest()`
 *    - `ACameraCaptureSession_capture()`
 *    - `ACameraCaptureSession_close()`
 * 4. `<camera/NdkCaptureRequest.h>`:
 *    - `ACaptureRequest_addTarget()`
 *    - `ACaptureRequest_setEntry_i32()`, `setEntry_i64()`, etc.
 * 5. `<media/NdkImageReader.h>`:
 *    - `AImageReader_new()`, `AImageReader_acquireLatestImage()`
 */
class CameraNdkBridge {

    private val tag = "CameraNdkBridge"
    var isNativeLoaded: Boolean = false
        private set

    private var nativeContextHandle: Long = 0L

    init {
        try {
            System.loadLibrary("camera2ndk_sample")
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(tag, "Native library 'camera2ndk_sample' not found. Operating in mock mode.", e)
            isNativeLoaded = false
        }
    }

    /**
     * Initializes the native ACameraManager.
     */
    fun initialize(): Boolean {
        return if (isNativeLoaded) {
            nativeInit()
        } else {
            Log.d(tag, "Mock NDK Camera initialized.")
            true
        }
    }

    /**
     * Opens native camera by cameraId.
     */
    fun openCamera(cameraId: String): Boolean {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Opening camera $cameraId")
            nativeContextHandle = 1001L
            return true
        }
        val handle = nativeOpen(cameraId)
        return if (handle != 0L) {
            nativeContextHandle = handle
            true
        } else {
            false
        }
    }

    /**
     * Binds native preview surface (ANativeWindow).
     */
    fun setPreviewSurface(surface: Surface): Boolean {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Setting preview surface")
            return true
        }
        return nativeSetPreviewSurface(nativeContextHandle, surface)
    }

    /**
     * Starts repeating request stream via ACameraCaptureSession.
     */
    fun startStreaming(): Boolean {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Starting streaming")
            return true
        }
        return nativeStartStreaming(nativeContextHandle)
    }

    /**
     * Stops native streaming.
     */
    fun stopStreaming(): Boolean {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Stopping streaming")
            return true
        }
        return nativeStopStreaming(nativeContextHandle)
    }

    /**
     * Captures a still frame via AImageReader.
     */
    fun captureStill(): ByteArray? {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Capturing mock frame")
            return ByteArray(0)
        }
        return nativeCaptureStill(nativeContextHandle)
    }

    /**
     * Closes native camera and frees session.
     */
    fun close() {
        if (!isNativeLoaded) {
            Log.d(tag, "Mock NDK: Closed")
            nativeContextHandle = 0L
            return
        }
        if (nativeContextHandle != 0L) {
            nativeClose(nativeContextHandle)
            nativeContextHandle = 0L
        }
    }

    // Native JNI External Declarations
    private external fun nativeInit(): Boolean
    private external fun nativeOpen(cameraId: String): Long
    private external fun nativeSetPreviewSurface(handle: Long, surface: Surface): Boolean
    private external fun nativeStartStreaming(handle: Long): Boolean
    private external fun nativeStopStreaming(handle: Long): Boolean
    private external fun nativeCaptureStill(handle: Long): ByteArray?
    private external fun nativeClose(handle: Long)
}

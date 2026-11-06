package com.example.droidsample.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State tracking for simultaneous dual (Front + Back) camera streaming.
 */
sealed class ConcurrentCameraState {
    data object Idle : ConcurrentCameraState()
    data object CheckingSupport : ConcurrentCameraState()
    data class Supported(val concurrentCombinationsCount: Int) : ConcurrentCameraState()
    data object Streaming : ConcurrentCameraState()
    data class Unsupported(val reason: String) : ConcurrentCameraState()
    data class Error(val message: String, val throwable: Throwable? = null) : ConcurrentCameraState()
}

/**
 * Orchestrates dual front and back simultaneous camera streaming using CameraX ConcurrentCamera API.
 */
class CameraXConcurrentManager(private val context: Context) {

    private val tag = "ConcurrentCamera"

    private var cameraProvider: ProcessCameraProvider? = null
    private var concurrentCamera: ConcurrentCamera? = null

    private val _state = MutableStateFlow<ConcurrentCameraState>(ConcurrentCameraState.Idle)
    val state: StateFlow<ConcurrentCameraState> = _state.asStateFlow()

    private var backPreview: Preview? = null
    private var frontPreview: Preview? = null

    /**
     * Initializes ProcessCameraProvider and checks for concurrent camera combinations support.
     */
    fun checkConcurrentSupport(onResult: (Boolean) -> Unit) {
        _state.value = ConcurrentCameraState.CheckingSupport
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val concurrentCameraInfos = provider.availableConcurrentCameraInfos

                if (concurrentCameraInfos.isNotEmpty()) {
                    _state.value = ConcurrentCameraState.Supported(concurrentCameraInfos.size)
                    onResult(true)
                } else {
                    _state.value = ConcurrentCameraState.Unsupported(
                        "Hardware does not support simultaneous front and back camera operation"
                    )
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to query concurrent camera support", e)
                _state.value = ConcurrentCameraState.Error("Failed to check concurrent cameras: ${e.message}", e)
                onResult(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds both Back and Front cameras simultaneously to provided SurfaceProviders.
     */
    fun startConcurrentStream(
        lifecycleOwner: LifecycleOwner,
        backSurfaceProvider: Preview.SurfaceProvider,
        frontSurfaceProvider: Preview.SurfaceProvider,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val concurrentCameraInfos = provider.availableConcurrentCameraInfos

                if (concurrentCameraInfos.isEmpty()) {
                    val msg = "Hardware does not support simultaneous front and back camera operation"
                    _state.value = ConcurrentCameraState.Unsupported(msg)
                    onError(UnsupportedOperationException(msg))
                    return@addListener
                }

                provider.unbindAll()

                // Back camera preview configuration
                val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
                backPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(backSurfaceProvider)
                }
                val backUseCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(backPreview!!)
                    .build()
                val backConfig = ConcurrentCamera.SingleCameraConfig(
                    backSelector,
                    backUseCaseGroup,
                    lifecycleOwner
                )

                // Front camera preview configuration
                val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                frontPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(frontSurfaceProvider)
                }
                val frontUseCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(frontPreview!!)
                    .build()
                val frontConfig = ConcurrentCamera.SingleCameraConfig(
                    frontSelector,
                    frontUseCaseGroup,
                    lifecycleOwner
                )

                concurrentCamera = provider.bindToLifecycle(listOf(backConfig, frontConfig))
                _state.value = ConcurrentCameraState.Streaming
                onSuccess()
            } catch (e: Exception) {
                Log.e(tag, "Failed to start concurrent camera stream", e)
                _state.value = ConcurrentCameraState.Error("Concurrent streaming failed: ${e.message}", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Stops the concurrent stream and unbinds cameras.
     */
    fun stopConcurrentStream() {
        try {
            cameraProvider?.unbindAll()
            concurrentCamera = null
            backPreview = null
            frontPreview = null
            _state.value = ConcurrentCameraState.Idle
        } catch (e: Exception) {
            Log.e(tag, "Error stopping concurrent camera stream", e)
        }
    }
}

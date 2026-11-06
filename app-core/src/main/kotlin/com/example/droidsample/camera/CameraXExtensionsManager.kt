package com.example.droidsample.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Metadata indicating availability of OEM extensions on a given camera.
 */
data class AvailableExtensions(
    val isBokeAvailable: Boolean = false,
    val isHdrAvailable: Boolean = false,
    val isNightAvailable: Boolean = false,
    val isFaceRetouchAvailable: Boolean = false,
    val isAutoAvailable: Boolean = false
)

/**
 * State for CameraX extensions.
 */
sealed class ExtensionsState {
    data object Uninitialized : ExtensionsState()
    data class Ready(val available: AvailableExtensions, val currentMode: Int) : ExtensionsState()
    data class Error(val message: String, val throwable: Throwable? = null) : ExtensionsState()
}

/**
 * Manages vendor-specific OEM extensions (HDR, Night Mode, Bokeh/Portrait, Face Retouch)
 * via androidx.camera.extensions.
 */
class CameraXExtensionsManager(private val context: Context) {

    private val tag = "ExtensionsManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null

    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null

    private val _state = MutableStateFlow<ExtensionsState>(ExtensionsState.Uninitialized)
    val state: StateFlow<ExtensionsState> = _state.asStateFlow()

    private val _activeMode = MutableStateFlow(ExtensionMode.NONE)
    val activeMode: StateFlow<Int> = _activeMode.asStateFlow()

    private val _availableExtensions = MutableStateFlow(AvailableExtensions())
    val availableExtensions: StateFlow<AvailableExtensions> = _availableExtensions.asStateFlow()

    /**
     * Initializes ProcessCameraProvider and ExtensionsManager.
     */
    fun initialize(onReady: (ExtensionsManager) -> Unit = {}) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
                extFuture.addListener({
                    try {
                        val extManager = extFuture.get()
                        extensionsManager = extManager
                        querySupportedExtensions(CameraSelector.DEFAULT_BACK_CAMERA)
                        onReady(extManager)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to get ExtensionsManager", e)
                        _state.value = ExtensionsState.Error("Extensions initialization failed: ${e.message}", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e(tag, "Failed to get CameraProvider", e)
                _state.value = ExtensionsState.Error("CameraProvider initialization failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Queries supported OEM extensions for the target camera selector.
     */
    fun querySupportedExtensions(baseSelector: CameraSelector): AvailableExtensions {
        val manager = extensionsManager ?: return AvailableExtensions()

        val available = AvailableExtensions(
            isBokeAvailable = manager.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH),
            isHdrAvailable = manager.isExtensionAvailable(baseSelector, ExtensionMode.HDR),
            isNightAvailable = manager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT),
            isFaceRetouchAvailable = manager.isExtensionAvailable(baseSelector, ExtensionMode.FACE_RETOUCH),
            isAutoAvailable = manager.isExtensionAvailable(baseSelector, ExtensionMode.AUTO)
        )

        _availableExtensions.value = available
        _state.value = ExtensionsState.Ready(available, _activeMode.value)
        return available
    }

    /**
     * Binds preview and image capture using an OEM extension selector.
     * Falls back to standard base selector if requested extension is not supported.
     */
    fun bindExtensionUseCase(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        baseSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        extensionMode: Int = ExtensionMode.NONE
    ) {
        val provider = cameraProvider ?: return
        val extManager = extensionsManager ?: return

        provider.unbindAll()

        // Check availability, fallback to NONE if unsupported
        val targetMode = if (extensionMode != ExtensionMode.NONE && extManager.isExtensionAvailable(baseSelector, extensionMode)) {
            extensionMode
        } else {
            ExtensionMode.NONE
        }
        _activeMode.value = targetMode

        val finalSelector = if (targetMode != ExtensionMode.NONE) {
            extManager.getExtensionEnabledCameraSelector(baseSelector, targetMode)
        } else {
            baseSelector
        }

        previewUseCase = Preview.Builder().build().also {
            it.setSurfaceProvider(surfaceProvider)
        }

        imageCaptureUseCase = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                finalSelector,
                previewUseCase!!,
                imageCaptureUseCase!!
            )
            _state.value = ExtensionsState.Ready(_availableExtensions.value, targetMode)
        } catch (e: Exception) {
            Log.e(tag, "Binding extension $targetMode failed", e)
            _state.value = ExtensionsState.Error("Extension binding failed: ${e.message}", e)
        }
    }

    /**
     * Captures a still photo with active OEM extension post-processing.
     */
    fun takePhoto(
        outputFile: File,
        onSuccess: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCaptureUseCase ?: run {
            onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "ImageCapture not bound", null))
            return
        }

        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Extension photo capture failed", exception)
                    onError(exception)
                }
            }
        )
    }

    fun unbindAll() {
        cameraProvider?.unbindAll()
        previewUseCase = null
        imageCaptureUseCase = null
    }
}

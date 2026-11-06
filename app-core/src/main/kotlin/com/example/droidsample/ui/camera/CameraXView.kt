package com.example.droidsample.ui.camera

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.droidsample.camera.*
import java.io.File

enum class CameraXMode {
    PHOTO,
    VIDEO,
    ANALYSIS,
    EXTENSIONS,
    CONCURRENT
}

@SuppressLint("DefaultLocale")
@Composable
fun CameraXView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraXManager = remember { CameraXManager(context) }
    val extensionsManager = remember { CameraXExtensionsManager(context) }
    val concurrentManager = remember { CameraXConcurrentManager(context) }

    var selectedMode by remember { mutableStateOf(CameraXMode.PHOTO) }
    var selectedExtension by remember { mutableIntStateOf(ExtensionMode.NONE) }
    val availableExtensions by extensionsManager.availableExtensions.collectAsState()

    val lensFacing by cameraXManager.lensFacing.collectAsState()
    val flashMode by cameraXManager.flashMode.collectAsState()
    val torchEnabled by cameraXManager.torchEnabled.collectAsState()
    val zoomRatio by cameraXManager.zoomRatio.collectAsState()
    val analysisStats by cameraXManager.analysisStats.collectAsState()
    val recordingState by cameraXManager.recordingState.collectAsState()
    val concurrentState by concurrentManager.state.collectAsState()

    var primaryPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var secondaryPreviewView by remember { mutableStateOf<PreviewView?>(null) }

    // Initialize managers
    LaunchedEffect(Unit) {
        cameraXManager.initialize {
            primaryPreviewView?.surfaceProvider?.let { provider ->
                cameraXManager.bindUseCases(lifecycleOwner, provider)
            }
        }
        extensionsManager.initialize()
        concurrentManager.checkConcurrentSupport {}
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraXManager.shutdown()
            concurrentManager.stopConcurrentStream()
            extensionsManager.unbindAll()
        }
    }

    // React to mode changes
    LaunchedEffect(selectedMode, selectedExtension) {
        when (selectedMode) {
            CameraXMode.PHOTO -> {
                concurrentManager.stopConcurrentStream()
                extensionsManager.unbindAll()
                primaryPreviewView?.surfaceProvider?.let {
                    cameraXManager.bindUseCases(lifecycleOwner, it, enableAnalysis = false, enableVideo = false)
                }
            }
            CameraXMode.VIDEO -> {
                concurrentManager.stopConcurrentStream()
                extensionsManager.unbindAll()
                primaryPreviewView?.surfaceProvider?.let {
                    cameraXManager.bindUseCases(lifecycleOwner, it, enableAnalysis = false, enableVideo = true)
                }
            }
            CameraXMode.ANALYSIS -> {
                concurrentManager.stopConcurrentStream()
                extensionsManager.unbindAll()
                primaryPreviewView?.surfaceProvider?.let {
                    cameraXManager.bindUseCases(lifecycleOwner, it, enableAnalysis = true, enableVideo = false)
                }
            }
            CameraXMode.EXTENSIONS -> {
                concurrentManager.stopConcurrentStream()
                cameraXManager.unbindAll()
                primaryPreviewView?.surfaceProvider?.let { provider ->
                    extensionsManager.bindExtensionUseCase(
                        lifecycleOwner,
                        provider,
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                        selectedExtension
                    )
                }
            }
            CameraXMode.CONCURRENT -> {
                cameraXManager.unbindAll()
                extensionsManager.unbindAll()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (selectedMode == CameraXMode.CONCURRENT) {
            ConcurrentCameraView(
                concurrentManager = concurrentManager,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Single Primary Camera View
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            cameraXManager.setZoomRatio(zoomRatio * zoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            primaryPreviewView?.meteringPointFactory?.let { factory ->
                                val point = factory.createPoint(offset.x, offset.y)
                                cameraXManager.tapToFocus(point)
                            }
                        }
                    },
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also {
                        primaryPreviewView = it
                        when (selectedMode) {
                            CameraXMode.PHOTO -> cameraXManager.bindUseCases(lifecycleOwner, it.surfaceProvider, enableAnalysis = false, enableVideo = false)
                            CameraXMode.VIDEO -> cameraXManager.bindUseCases(lifecycleOwner, it.surfaceProvider, enableAnalysis = false, enableVideo = true)
                            CameraXMode.ANALYSIS -> cameraXManager.bindUseCases(lifecycleOwner, it.surfaceProvider, enableAnalysis = true, enableVideo = false)
                            CameraXMode.EXTENSIONS -> extensionsManager.bindExtensionUseCase(
                                lifecycleOwner,
                                it.surfaceProvider,
                                if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                                selectedExtension
                            )
                            else -> {}
                        }
                    }
                }
            )
        }

        // Live Image Analysis Overlay HUD
        if (selectedMode == CameraXMode.ANALYSIS) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Image Analysis Stats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("FPS: ${String.format("%.1f", analysisStats.fps)}", style = MaterialTheme.typography.bodySmall)
                    Text("Luminance: ${String.format("%.1f", analysisStats.averageLuminance)}", style = MaterialTheme.typography.bodySmall)
                    Text("Resolution: ${analysisStats.frameWidth}x${analysisStats.frameHeight}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // OEM Extensions Selector Overlay
        if (selectedMode == CameraXMode.EXTENSIONS) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedExtension == ExtensionMode.NONE,
                        onClick = { selectedExtension = ExtensionMode.NONE },
                        label = { Text("None") }
                    )
                    FilterChip(
                        selected = selectedExtension == ExtensionMode.HDR,
                        onClick = { selectedExtension = ExtensionMode.HDR },
                        enabled = availableExtensions.isHdrAvailable,
                        label = { Text("HDR") }
                    )
                    FilterChip(
                        selected = selectedExtension == ExtensionMode.NIGHT,
                        onClick = { selectedExtension = ExtensionMode.NIGHT },
                        enabled = availableExtensions.isNightAvailable,
                        label = { Text("Night") }
                    )
                    FilterChip(
                        selected = selectedExtension == ExtensionMode.BOKEH,
                        onClick = { selectedExtension = ExtensionMode.BOKEH },
                        enabled = availableExtensions.isBokeAvailable,
                        label = { Text("Bokeh") }
                    )
                    FilterChip(
                        selected = selectedExtension == ExtensionMode.FACE_RETOUCH,
                        onClick = { selectedExtension = ExtensionMode.FACE_RETOUCH },
                        enabled = availableExtensions.isFaceRetouchAvailable,
                        label = { Text("Retouch") }
                    )
                }
            }
        }

        // Top Control Bar (Facing, Flash, Torch, Zoom display)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash toggle
                IconButton(onClick = { cameraXManager.cycleFlashMode() }) {
                    Icon(
                        when (flashMode) {
                            androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            androidx.camera.core.ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        },
                        contentDescription = "Flash Mode"
                    )
                }

                // Torch toggle
                IconButton(onClick = { cameraXManager.toggleTorch() }) {
                    Icon(
                        if (torchEnabled) Icons.Default.Highlight else Icons.Default.FlashlightOff,
                        contentDescription = "Torch",
                        tint = if (torchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = String.format("%.1fx", zoomRatio),
                    style = MaterialTheme.typography.labelMedium
                )

                // Lens switch
                IconButton(onClick = {
                    primaryPreviewView?.surfaceProvider?.let { provider ->
                        cameraXManager.toggleCameraFacing(lifecycleOwner, provider)
                    }
                }) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                }
            }
        }

        // Bottom Dashboard (Mode Selector & Shutter Button)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mode Selector Tabs
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedMode.ordinal,
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent
                ) {
                    CameraXMode.values().forEach { mode ->
                        Tab(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            text = { Text(mode.name) }
                        )
                    }
                }

                // Recording Status Display
                if (selectedMode == CameraXMode.VIDEO && recordingState is VideoRecordingState.Recording) {
                    val durationSecs = (recordingState as VideoRecordingState.Recording).durationMs / 1000
                    Text(
                        text = "Recording: ${durationSecs / 60}:${String.format("%02d", durationSecs % 60)}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Shutter / Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (selectedMode) {
                        CameraXMode.VIDEO -> {
                            val isRecording = recordingState is VideoRecordingState.Recording
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        cameraXManager.stopVideoRecording()
                                    } else {
                                        val file = CameraIntentsHelper.createVideoFile(context)
                                        cameraXManager.startVideoRecording(file) { uri ->
                                            Toast.makeText(context, "Saved video: $uri", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                                    contentDescription = "Record Video",
                                    tint = Color.White
                                )
                            }
                        }
                        CameraXMode.EXTENSIONS -> {
                            Button(
                                onClick = {
                                    val file = CameraIntentsHelper.createImageFile(context)
                                    extensionsManager.takePhoto(
                                        file,
                                        onSuccess = { Toast.makeText(context, "Captured with Extension: ${file.name}", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                                    )
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capture Extension Photo")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    val file = CameraIntentsHelper.createImageFile(context)
                                    cameraXManager.capturePhotoToFile(
                                        file,
                                        onSuccess = { Toast.makeText(context, "Captured: ${file.name}", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                                    )
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConcurrentCameraView(
    concurrentManager: CameraXConcurrentManager,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val concurrentState by concurrentManager.state.collectAsState()
    var backProvider by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }
    var frontProvider by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }

    LaunchedEffect(backProvider, frontProvider) {
        val back = backProvider
        val front = frontProvider
        if (back != null && front != null) {
            concurrentManager.startConcurrentStream(
                lifecycleOwner = lifecycleOwner,
                backSurfaceProvider = back,
                frontSurfaceProvider = front
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            concurrentManager.stopConcurrentStream()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            post { backProvider = this.surfaceProvider }
                        }
                    }
                )
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Back Camera", color = Color.White, modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            post { frontProvider = this.surfaceProvider }
                        }
                    }
                )
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Front Camera", color = Color.White, modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        when (val s = concurrentState) {
            is ConcurrentCameraState.Unsupported -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Dual Camera Unsupported",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = s.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is ConcurrentCameraState.Error -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Dual Camera Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

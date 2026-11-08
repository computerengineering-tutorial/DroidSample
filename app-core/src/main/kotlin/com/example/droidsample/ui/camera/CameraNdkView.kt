package com.example.droidsample.ui.camera

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.droidsample.camera.CameraNdkBridge
import com.example.droidsample.camera.CameraSize
import com.example.droidsample.camera.NdkCameraConstants
import com.example.droidsample.camera.NdkCameraInfo
import com.example.droidsample.camera.NdkCameraState

enum class NdkPreviewSurfaceType {
    SURFACE_VIEW,
    TEXTURE_VIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraNdkView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bridge = remember { CameraNdkBridge() }

    val state by bridge.state.collectAsState()
    val settings by bridge.settings.collectAsState()
    val stats by bridge.stats.collectAsState()

    var cameraIds by remember { mutableStateOf(listOf("0")) }
    var activeCameraId by remember { mutableStateOf("0") }
    var charsInfo by remember { mutableStateOf<NdkCameraInfo?>(null) }
    var showProControls by remember { mutableStateOf(false) }
    var surfaceType by remember { mutableStateOf(NdkPreviewSurfaceType.SURFACE_VIEW) }
    var lastCapturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var currentPreviewSurface by remember { mutableStateOf<Surface?>(null) }

    LaunchedEffect(Unit) {
        bridge.initialize()
        val ids = bridge.getCameraIdList()
        if (ids.isNotEmpty()) {
            cameraIds = ids
            activeCameraId = ids.first()
        }
    }

    LaunchedEffect(activeCameraId) {
        if (activeCameraId.isNotEmpty()) {
            charsInfo = bridge.getCameraCharacteristicsInfo(activeCameraId)
            bridge.openCamera(activeCameraId)
            currentPreviewSurface?.let { surface ->
                val optimalPreview = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                    ?: charsInfo?.supportedPreviewSizes?.firstOrNull()
                    ?: CameraSize(1920, 1080)
                val optimalJpeg = charsInfo?.supportedJpegSizes?.firstOrNull { it.width in 1920..3840 }
                    ?: charsInfo?.supportedJpegSizes?.firstOrNull()
                    ?: CameraSize(1920, 1080)

                bridge.setPreviewSurface(
                    surface,
                    optimalPreview.width,
                    optimalPreview.height,
                    optimalJpeg.width,
                    optimalJpeg.height
                )
                bridge.startStreaming()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bridge.stopStreaming()
            bridge.close()
        }
    }

    LaunchedEffect(state) {
        if (state is NdkCameraState.PhotoCaptured) {
            val bytes = (state as NdkCameraState.PhotoCaptured).jpegBytes
            if (bytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                lastCapturedBitmap = bmp
                Toast.makeText(context, "NDK Frame Captured: ${bytes.size} bytes", Toast.LENGTH_SHORT).show()
            }
        } else if (state is NdkCameraState.Error) {
            val err = state as NdkCameraState.Error
            Toast.makeText(context, "NDK Error: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Native Preview Surface (Supports both SurfaceView and TextureView)
        when (surfaceType) {
            NdkPreviewSurfaceType.SURFACE_VIEW -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    val optimalPreview = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                                        ?: charsInfo?.supportedPreviewSizes?.firstOrNull()
                                        ?: CameraSize(1920, 1080)
                                    val optimalJpeg = charsInfo?.supportedJpegSizes?.firstOrNull { it.width in 1920..3840 }
                                        ?: charsInfo?.supportedJpegSizes?.firstOrNull()
                                        ?: CameraSize(1920, 1080)

                                    holder.setFixedSize(optimalPreview.width, optimalPreview.height)
                                    currentPreviewSurface = holder.surface
                                    bridge.setPreviewSurface(
                                        holder.surface,
                                        optimalPreview.width,
                                        optimalPreview.height,
                                        optimalJpeg.width,
                                        optimalJpeg.height
                                    )
                                    bridge.startStreaming()
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    currentPreviewSurface = holder.surface
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    bridge.stopStreaming()
                                    currentPreviewSurface = null
                                }
                            })
                        }
                    }
                )
            }
            NdkPreviewSurfaceType.TEXTURE_VIEW -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                    val optimalPreview = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                                        ?: charsInfo?.supportedPreviewSizes?.firstOrNull()
                                        ?: CameraSize(1920, 1080)
                                    val optimalJpeg = charsInfo?.supportedJpegSizes?.firstOrNull { it.width in 1920..3840 }
                                        ?: charsInfo?.supportedJpegSizes?.firstOrNull()
                                        ?: CameraSize(1920, 1080)

                                    st.setDefaultBufferSize(optimalPreview.width, optimalPreview.height)
                                    configureTextureTransform(this@apply, width, height, optimalPreview, charsInfo?.sensorOrientation ?: 90)

                                    val surface = Surface(st)
                                    currentPreviewSurface = surface
                                    bridge.setPreviewSurface(
                                        surface,
                                        optimalPreview.width,
                                        optimalPreview.height,
                                        optimalJpeg.width,
                                        optimalJpeg.height
                                    )
                                    bridge.startStreaming()
                                }

                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                    val optimalPreview = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                                        ?: charsInfo?.supportedPreviewSizes?.firstOrNull()
                                        ?: CameraSize(1920, 1080)
                                    configureTextureTransform(this@apply, width, height, optimalPreview, charsInfo?.sensorOrientation ?: 90)
                                }

                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    bridge.stopStreaming()
                                    currentPreviewSurface?.release()
                                    currentPreviewSurface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    }
                )
            }
        }

        // Top Header: NDK Diagnostics & Status
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = if (bridge.isNativeLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                        Column {
                            Text(
                                text = "NDK Camera2 Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (bridge.isNativeLoaded) "libcamera2ndk.so (Native MMAP)" else "Mock Fallback Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bridge.isNativeLoaded) Color(0xFF388E3C) else Color(0xFFF57C00)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Switch between SurfaceView and TextureView
                        IconButton(onClick = {
                            surfaceType = if (surfaceType == NdkPreviewSurfaceType.SURFACE_VIEW) {
                                NdkPreviewSurfaceType.TEXTURE_VIEW
                            } else {
                                NdkPreviewSurfaceType.SURFACE_VIEW
                            }
                        }) {
                            Icon(
                                if (surfaceType == NdkPreviewSurfaceType.SURFACE_VIEW) Icons.Default.Layers else Icons.Default.Texture,
                                contentDescription = "Toggle Surface Type (${surfaceType.name})"
                            )
                        }

                        // Camera Flip Switcher
                        if (cameraIds.size > 1) {
                            IconButton(onClick = {
                                val nextIndex = (cameraIds.indexOf(activeCameraId) + 1) % cameraIds.size
                                activeCameraId = cameraIds[nextIndex]
                            }) {
                                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                // Metadata and Real-time Telemetry Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Camera ${activeCameraId} (${charsInfo?.facingName ?: "Back"})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Surface: ${if (surfaceType == NdkPreviewSurfaceType.SURFACE_VIEW) "SurfaceView" else "TextureView"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "FPS: ${String.format("%.1f", stats.fps)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Frames: ${stats.frameCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Bottom Controls Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Control Panel Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showProControls) "NDK Pro Manual Controls" else "Camera Controls",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { bridge.triggerAf() }) {
                            Text("AF Trigger")
                        }
                        IconButton(onClick = { showProControls = !showProControls }) {
                            Icon(
                                if (showProControls) Icons.Default.Tune else Icons.Default.Settings,
                                contentDescription = "Toggle Manual Controls"
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showProControls) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Manual ISO Slider
                        val (minIso, maxIso) = charsInfo?.isoRange ?: Pair(100, 3200)
                        Text(
                            text = "Manual ISO: ${settings.iso ?: "Auto"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = (settings.iso ?: minIso).toFloat(),
                                onValueChange = { newIso ->
                                    bridge.updateSettings(
                                        settings.copy(
                                            aeMode = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_OFF,
                                            iso = newIso.toInt()
                                        )
                                    )
                                },
                                valueRange = minIso.toFloat()..maxIso.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                bridge.updateSettings(
                                    settings.copy(
                                        aeMode = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON,
                                        iso = null
                                    )
                                )
                            }) {
                                Text("Auto")
                            }
                        }

                        // Manual Exposure Shutter Slider
                        val currentExpMs = settings.exposureTimeNs?.let { it / 1_000_000 }
                        Text(
                            text = "Exposure Time: ${currentExpMs?.let { "${it}ms" } ?: "Auto"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = (currentExpMs ?: 33).toFloat(),
                                onValueChange = { ms ->
                                    bridge.updateSettings(
                                        settings.copy(
                                            aeMode = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_OFF,
                                            exposureTimeNs = ms.toLong() * 1_000_000L
                                        )
                                    )
                                },
                                valueRange = 1f..250f,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                bridge.updateSettings(
                                    settings.copy(
                                        aeMode = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON,
                                        exposureTimeNs = null
                                    )
                                )
                            }) {
                                Text("Auto")
                            }
                        }

                        // Manual Focus Distance Slider
                        val maxFd = charsInfo?.minFocusDistance ?: 10.0f
                        if (maxFd > 0.0f) {
                            Text(
                                text = "Focus Distance: ${settings.focusDistanceDiopters?.let { String.format("%.1f dpt", it) } ?: "Continuous AF"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = settings.focusDistanceDiopters ?: 0f,
                                    onValueChange = { fd ->
                                        bridge.updateSettings(
                                            settings.copy(
                                                afMode = NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_OFF,
                                                focusDistanceDiopters = fd
                                            )
                                        )
                                    },
                                    valueRange = 0f..maxFd,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    bridge.updateSettings(
                                        settings.copy(
                                            afMode = NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                            focusDistanceDiopters = null
                                        )
                                    )
                                }) {
                                    Text("Auto")
                                }
                            }
                        }

                        // Zoom Ratio Slider
                        val maxZoom = charsInfo?.maxDigitalZoom ?: 8.0f
                        Text(
                            text = "Zoom Ratio: ${String.format("%.1fx", settings.zoomRatio)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.zoomRatio,
                            onValueChange = { z ->
                                bridge.updateSettings(settings.copy(zoomRatio = z))
                            },
                            valueRange = 1.0f..maxZoom
                        )

                        // Feature Toggles Row (Flash, OIS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = settings.flashMode == NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH,
                                onClick = {
                                    val nextFlash = if (settings.flashMode == NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH) {
                                        NdkCameraConstants.ACAMERA_FLASH_MODE_OFF
                                    } else {
                                        NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH
                                    }
                                    bridge.updateSettings(settings.copy(flashMode = nextFlash))
                                },
                                label = { Text("Torch") },
                                leadingIcon = {
                                    Icon(
                                        if (settings.flashMode == NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = null
                                    )
                                }
                            )

                            FilterChip(
                                selected = settings.oisEnabled,
                                onClick = {
                                    bridge.updateSettings(settings.copy(oisEnabled = !settings.oisEnabled))
                                },
                                label = { Text("OIS") },
                                leadingIcon = {
                                    Icon(Icons.Default.MotionPhotosOn, contentDescription = null)
                                }
                            )
                        }
                    }
                }

                // Shutter Capture & Thumbnail Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail box
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (lastCapturedBitmap != null) {
                            Image(
                                bitmap = lastCapturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Main Shutter Button
                    IconButton(
                        onClick = { bridge.captureStill() },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Still Frame",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Reset / Clear Settings Button
                    IconButton(
                        onClick = {
                            bridge.updateSettings(com.example.droidsample.camera.NdkCameraControlSettings())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Controls"
                        )
                    }
                }
            }
        }
    }
}

private fun configureTextureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    previewSize: CameraSize,
    sensorOrientation: Int
) {
    if (viewWidth == 0 || viewHeight == 0) return
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
    val scale = maxOf(
        viewHeight.toFloat() / previewSize.height,
        viewWidth.toFloat() / previewSize.width
    )
    matrix.postScale(scale, scale, centerX, centerY)
    textureView.setTransform(matrix)
}

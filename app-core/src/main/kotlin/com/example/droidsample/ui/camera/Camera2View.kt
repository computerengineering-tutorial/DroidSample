package com.example.droidsample.ui.camera

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.droidsample.camera.Camera2CharacteristicsInfo
import com.example.droidsample.camera.Camera2ControlSettings
import com.example.droidsample.camera.Camera2Manager
import com.example.droidsample.camera.Camera2State

@Composable
fun Camera2View(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { Camera2Manager(context) }
    val cameraList by manager.cameraList.collectAsState()
    val state by manager.state.collectAsState()
    val settings by manager.settings.collectAsState()

    var activeCameraId by remember { mutableStateOf("0") }
    var charsInfo by remember { mutableStateOf<Camera2CharacteristicsInfo?>(null) }
    var previewSurface by remember { mutableStateOf<Surface?>(null) }
    var showProControls by remember { mutableStateOf(false) }

    LaunchedEffect(cameraList) {
        if (cameraList.isNotEmpty() && activeCameraId !in cameraList) {
            activeCameraId = cameraList.first()
        }
    }

    LaunchedEffect(activeCameraId) {
        if (activeCameraId.isNotEmpty()) {
            charsInfo = manager.getCameraCharacteristicsInfo(activeCameraId)
            manager.openCamera(activeCameraId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.destroy()
        }
    }

    LaunchedEffect(state) {
        if (state is Camera2State.PhotoCaptured) {
            val bytes = (state as Camera2State.PhotoCaptured).jpegBytes
            Toast.makeText(context, "Captured JPEG: ${bytes.size} bytes", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // TextureView for Camera2 preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            val optimalSize = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                                ?: manager.getOptimalPreviewSize(activeCameraId)
                                ?: Size(1920, 1080)
                            st.setDefaultBufferSize(optimalSize.width, optimalSize.height)
                            configureTransform(this@apply, width, height, optimalSize, charsInfo?.sensorOrientation ?: 90, manager.getDisplayRotation())
                            val surface = Surface(st)
                            previewSurface = surface
                            manager.startPreview(surface, optimalSize.width, optimalSize.height)
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                            val optimalSize = charsInfo?.supportedPreviewSizes?.firstOrNull { it.width in 1280..1920 }
                                ?: manager.getOptimalPreviewSize(activeCameraId)
                                ?: Size(1920, 1080)
                            configureTransform(this@apply, width, height, optimalSize, charsInfo?.sensorOrientation ?: 90, manager.getDisplayRotation())
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            previewSurface?.release()
                            previewSurface = null
                            manager.closeCamera()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            }
        )

        // Error banner if any
        if (state is Camera2State.Error) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Camera Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text((state as Camera2State.Error).message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Top info overlay
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Camera2 Pro (Camera ID: $activeCameraId)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Level: ${charsInfo?.hardwareLevel ?: "..."}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Text(
                    text = "ISO: ${settings.iso ?: "Auto"} | Exp: ${settings.exposureTimeNs?.let { "${it / 1_000_000}ms" } ?: "Auto"} | Zoom: ${String.format("%.1fx", settings.zoomRatio)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Bottom Pro Controls Drawer / Panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Toggle between basic and pro manual sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showProControls) "Manual Sliders (Pro)" else "Basic Controls",
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = { showProControls = !showProControls }) {
                        Icon(
                            if (showProControls) Icons.Default.Tune else Icons.Default.Settings,
                            contentDescription = "Toggle Manual Controls"
                        )
                    }
                }

                if (showProControls) {
                    // Manual ISO Slider
                    charsInfo?.isoRange?.let { isoRange ->
                        Text("Sensitivity (ISO: ${settings.iso ?: "Auto"})", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = (settings.iso ?: isoRange.lower).toFloat(),
                                onValueChange = { newIso ->
                                    manager.updateControls { it.copy(
                                        aeMode = CaptureRequest.CONTROL_AE_MODE_OFF,
                                        iso = newIso.toInt()
                                    ) }
                                },
                                valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                manager.updateControls { it.copy(aeMode = CaptureRequest.CONTROL_AE_MODE_ON, iso = null) }
                            }) {
                                Text("Auto")
                            }
                        }
                    }

                    // Manual Zoom Slider
                    val minZoom = charsInfo?.minZoomRatio ?: 1f
                    val maxZoom = charsInfo?.maxZoomRatio ?: 4f
                    if (maxZoom > minZoom) {
                        Text("Zoom Ratio: ${String.format("%.1fx", settings.zoomRatio)}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = settings.zoomRatio,
                            onValueChange = { newZoom ->
                                manager.updateControls { it.copy(zoomRatio = newZoom) }
                            },
                            valueRange = minZoom..maxZoom
                        )
                    }

                    // Manual Focus Distance Slider
                    Text("Focus Diopters: ${settings.focusDistanceDiopters?.let { String.format("%.1f", it) } ?: "Auto"}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = settings.focusDistanceDiopters ?: 0f,
                            onValueChange = { diopters ->
                                manager.updateControls { it.copy(
                                    afMode = CaptureRequest.CONTROL_AF_MODE_OFF,
                                    focusDistanceDiopters = diopters
                                ) }
                            },
                            valueRange = 0f..10f,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            manager.updateControls { it.copy(
                                afMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                focusDistanceDiopters = null
                            ) }
                        }) {
                            Text("Auto")
                        }
                    }
                }

                // Action Bar (Switch camera, flash, shutter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch Camera ID
                    IconButton(onClick = {
                        if (cameraList.isNotEmpty()) {
                            val currentIndex = cameraList.indexOf(activeCameraId)
                            val nextIndex = (currentIndex + 1) % cameraList.size
                            activeCameraId = cameraList[nextIndex]
                        }
                    }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                    }

                    // Flash Toggle
                    IconButton(onClick = {
                        val nextFlash = if (settings.flashMode == CaptureRequest.FLASH_MODE_TORCH) {
                            CaptureRequest.FLASH_MODE_OFF
                        } else {
                            CaptureRequest.FLASH_MODE_TORCH
                        }
                        manager.updateControls { it.copy(flashMode = nextFlash) }
                    }) {
                        Icon(
                            if (settings.flashMode == CaptureRequest.FLASH_MODE_TORCH) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch"
                        )
                    }

                    // Shutter button
                    Button(
                        onClick = { manager.takePicture() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo", tint = Color.White)
                    }

                    // EV Exposure Compensation
                    IconButton(onClick = {
                        val nextComp = if (settings.exposureCompensation >= 4) -4 else settings.exposureCompensation + 2
                        manager.updateControls { it.copy(exposureCompensation = nextComp) }
                    }) {
                        Icon(Icons.Default.Exposure, contentDescription = "EV Compensation")
                    }
                }
            }
        }
    }
}

private fun configureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    previewSize: Size,
    sensorOrientation: Int,
    displayRotation: Int
) {
    if (viewWidth == 0 || viewHeight == 0) return
    val matrix = Matrix()
    val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    if (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270) {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        val scale = maxOf(
            viewHeight.toFloat() / previewSize.height,
            viewWidth.toFloat() / previewSize.width
        )
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate((90 * (displayRotation - 2)).toFloat(), centerX, centerY)
    } else if (displayRotation == Surface.ROTATION_180) {
        matrix.postRotate(180f, centerX, centerY)
    }
    textureView.setTransform(matrix)
}

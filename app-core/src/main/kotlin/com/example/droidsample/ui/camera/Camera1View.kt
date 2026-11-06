@file:Suppress("DEPRECATION")

package com.example.droidsample.ui.camera

import android.hardware.Camera
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.droidsample.camera.Camera1Manager
import com.example.droidsample.camera.Camera1State

@Composable
fun Camera1View(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { Camera1Manager(context) }
    val state by manager.state.collectAsState()
    val paramsInfo by manager.parametersInfo.collectAsState()

    var activeCameraId by remember { mutableIntStateOf(0) }
    var selectedFlash by remember { mutableStateOf(Camera.Parameters.FLASH_MODE_AUTO) }
    var zoomValue by remember { mutableFloatStateOf(0f) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    DisposableEffect(activeCameraId) {
        manager.openCamera(activeCameraId)
        onDispose {
            manager.destroy()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Legacy SurfaceView embedded via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceHolder = holder
                            manager.startPreviewWithHolder(holder)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            surfaceHolder = holder
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceHolder = null
                            manager.stopPreview()
                        }
                    })
                }
            }
        )

        // Overlay Header with Camera1 info
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Legacy Camera1 View (android.hardware.Camera)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                when (val s = state) {
                    is Camera1State.Previewing -> {
                        Text(
                            text = "Camera ID: ${s.cameraId} | Preview Size: ${s.selectedPreviewSize}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is Camera1State.Error -> {
                        Text(
                            text = "Error: ${s.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "Status: ${s::class.simpleName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Overlay Bottom Controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zoom Slider if supported
                if (paramsInfo.isZoomSupported && paramsInfo.maxZoom > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        Slider(
                            value = zoomValue,
                            onValueChange = {
                                zoomValue = it
                                manager.setZoom(it.toInt())
                            },
                            valueRange = 0f..paramsInfo.maxZoom.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                    }
                }

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch Camera Button
                    IconButton(onClick = {
                        val cameras = manager.getAvailableCameras()
                        if (cameras.isNotEmpty()) {
                            activeCameraId = (activeCameraId + 1) % cameras.size
                            manager.openCamera(activeCameraId)
                        }
                    }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                    }

                    // Flash Mode Toggle
                    IconButton(onClick = {
                        val modes = paramsInfo.supportedFlashModes
                        if (modes.isNotEmpty()) {
                            val currentIndex = modes.indexOf(selectedFlash)
                            val nextMode = modes[(currentIndex + 1) % modes.size]
                            selectedFlash = nextMode
                            manager.setFlashMode(nextMode)
                        }
                    }) {
                        Icon(Icons.Default.FlashAuto, contentDescription = "Flash Mode")
                    }

                    // Shutter Button
                    Button(
                        onClick = {
                            manager.takePicture(
                                onShutter = {},
                                onPictureTaken = { bytes ->
                                    Toast.makeText(context, "Captured ${bytes.size} bytes", Toast.LENGTH_SHORT).show()
                                    surfaceHolder?.let { holder ->
                                        manager.startPreviewWithHolder(holder)
                                    }
                                },
                                onError = { e ->
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo", tint = Color.White)
                    }

                    // Autofocus trigger
                    IconButton(onClick = {
                        manager.triggerAutoFocus { success ->
                            Toast.makeText(context, if (success) "Focused" else "Focus failed", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Autofocus")
                    }
                }
            }
        }
    }
}

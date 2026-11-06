package com.example.droidsample.ui.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class CameraDetailedMetadata(
    val cameraId: String,
    val facing: String,
    val hardwareLevel: String,
    val sensorOrientation: Int,
    val activeArray: Rect?,
    val isoRange: Range<Int>?,
    val exposureRangeMs: Pair<Double, Double>?,
    val capabilities: List<String>,
    val physicalCameras: List<String>,
    val flashAvailable: Boolean,
    val jpegSizes: List<Size>,
    val yuvSizes: List<Size>,
    val focalLengths: List<Float>
)

/**
 * Parses detailed camera metadata from CameraCharacteristics.
 */
fun extractCameraDetailedMetadata(cameraId: String, chars: CameraCharacteristics): CameraDetailedMetadata {
    val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
    val facingStr = when (facingInt) {
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }

    val levelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
    val levelStr = when (levelInt) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN ($levelInt)"
    }

    val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

    val expRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val expRangeMs = expRangeNs?.let {
        Pair(it.lower / 1_000_000.0, it.upper / 1_000_000.0)
    }

    val capsList = mutableListOf<String>()
    chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.forEach { cap ->
        val name = when (cap) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
            else -> "CAPABILITY_$cap"
        }
        capsList.add(name)
    }

    val physicalIds = chars.physicalCameraIds.toList()
    val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()
    val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList() ?: emptyList()
    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()

    return CameraDetailedMetadata(
        cameraId = cameraId,
        facing = facingStr,
        hardwareLevel = levelStr,
        sensorOrientation = orientation,
        activeArray = activeArray,
        isoRange = isoRange,
        exposureRangeMs = expRangeMs,
        capabilities = capsList,
        physicalCameras = physicalIds,
        flashAvailable = flash,
        jpegSizes = jpegSizes,
        yuvSizes = yuvSizes,
        focalLengths = focalLengths
    )
}

@Composable
fun CameraMetadataInspector(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cameraList by remember { mutableStateOf<List<CameraDetailedMetadata>>(emptyList()) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val list = mutableListOf<CameraDetailedMetadata>()
        for (id in cm.cameraIdList) {
            try {
                val chars = cm.getCameraCharacteristics(id)
                list.add(extractCameraDetailedMetadata(id, chars))
            } catch (e: Exception) {
                // Ignore inaccessible cameras
            }
        }
        cameraList = list
        if (list.isNotEmpty()) {
            selectedCameraId = list.first().cameraId
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Camera Hardware Diagnostics",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Total cameras detected: ${cameraList.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Camera Selection Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cameraList.forEach { meta ->
                FilterChip(
                    selected = selectedCameraId == meta.cameraId,
                    onClick = { selectedCameraId = meta.cameraId },
                    label = { Text("Cam ${meta.cameraId} (${meta.facing})") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentMeta = cameraList.find { it.cameraId == selectedCameraId }
        if (currentMeta != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    MetadataCard(title = "Hardware Level & Sensor") {
                        MetadataRow("Hardware Level", currentMeta.hardwareLevel)
                        MetadataRow("Facing", currentMeta.facing)
                        MetadataRow("Orientation", "${currentMeta.sensorOrientation}°")
                        MetadataRow("Flash Available", if (currentMeta.flashAvailable) "Yes" else "No")
                        currentMeta.activeArray?.let {
                            MetadataRow("Active Array", "${it.width()} x ${it.height()} px")
                        }
                    }
                }

                item {
                    MetadataCard(title = "Exposure & Sensitivity") {
                        currentMeta.isoRange?.let {
                            MetadataRow("ISO Range", "${it.lower} .. ${it.upper}")
                        }
                        currentMeta.exposureRangeMs?.let {
                            MetadataRow("Exposure Range", "${String.format("%.3f", it.first)}ms .. ${String.format("%.1f", it.second)}ms")
                        }
                        MetadataRow("Focal Lengths", currentMeta.focalLengths.joinToString(", ") { "${it}mm" })
                    }
                }

                if (currentMeta.physicalCameras.isNotEmpty()) {
                    item {
                        MetadataCard(title = "Logical Multi-Camera Subsystems") {
                            MetadataRow("Physical Sensor IDs", currentMeta.physicalCameras.joinToString(", "))
                        }
                    }
                }

                item {
                    MetadataCard(title = "Capabilities") {
                        currentMeta.capabilities.forEach { cap ->
                            Text("• $cap", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    MetadataCard(title = "Supported Resolutions") {
                        Text("JPEG Resolutions (Top 5):", style = MaterialTheme.typography.labelMedium)
                        currentMeta.jpegSizes.take(5).forEach { size ->
                            Text("  - ${size.width} x ${size.height} (${String.format("%.1f", (size.width * size.height) / 1_000_000.0)} MP)", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("YUV_420_888 Resolutions (Top 3):", style = MaterialTheme.typography.labelMedium)
                        currentMeta.yuvSizes.take(3).forEach { size ->
                            Text("  - ${size.width} x ${size.height}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

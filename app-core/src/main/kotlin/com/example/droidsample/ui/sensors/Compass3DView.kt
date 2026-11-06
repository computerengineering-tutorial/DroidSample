package com.example.droidsample.ui.sensors

import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.sensors.MagneticFieldSample
import com.example.droidsample.sensors.OrientationSample
import com.example.droidsample.sensors.PositionSensorManager
import com.example.droidsample.sensors.ProximitySample
import com.example.droidsample.sensors.SamplingDelay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Compass3DView(
    samplingDelay: SamplingDelay,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val positionManager = remember(context) { PositionSensorManager(context) }

    var orientation by remember { mutableStateOf(OrientationSample()) }
    var magneticField by remember { mutableStateOf(MagneticFieldSample()) }
    var proximity by remember { mutableStateOf(ProximitySample()) }

    val hasFusion = remember(positionManager) { positionManager.hasOrientationFusion() }
    val hasProximity = remember(positionManager) { positionManager.hasProximitySensor() }

    LaunchedEffect(samplingDelay) {
        positionManager.observeOrientation(samplingDelay).collectLatest { sample ->
            orientation = sample
        }
    }

    LaunchedEffect(samplingDelay) {
        positionManager.observeMagneticField(samplingDelay).collectLatest { sample ->
            magneticField = sample
        }
    }

    LaunchedEffect(samplingDelay) {
        positionManager.observeProximity(samplingDelay).collectLatest { sample ->
            proximity = sample
        }
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = orientation.azimuthDegrees,
        animationSpec = spring(stiffness = 300f),
        label = "azimuth"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Calibration Accuracy & Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "3D Compass & Orientation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = getCardinalDirection(orientation.azimuthDegrees),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AccuracyBadge(accuracy = orientation.accuracy)
        }

        // Circular Rotating Compass Dial
        Box(
            modifier = Modifier
                .size(230.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Compass Rose
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer { rotationZ = -animatedAzimuth }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Draw 8 cardinal tick marks
                for (i in 0 until 360 step 30) {
                    val angleRad = Math.toRadians(i.toDouble() - 90.0)
                    val tickLen = if (i % 90 == 0) 18f else 10f
                    val startX = (center.x + (radius - tickLen) * cos(angleRad)).toFloat()
                    val startY = (center.y + (radius - tickLen) * sin(angleRad)).toFloat()
                    val endX = (center.x + radius * cos(angleRad)).toFloat()
                    val endY = (center.y + radius * sin(angleRad)).toFloat()

                    val color = if (i == 0) Color(0xFFE53935) else Color.Gray
                    val stroke = if (i % 90 == 0) 3f else 1.5f
                    drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = stroke)
                }

                // North Needle (Red Arrow)
                val needlePath = Path().apply {
                    moveTo(center.x, center.y - radius + 22f)
                    lineTo(center.x - 14f, center.y)
                    lineTo(center.x + 14f, center.y)
                    close()
                }
                drawPath(needlePath, color = Color(0xFFE53935))

                // South Needle (Silver/Blue Arrow)
                val southPath = Path().apply {
                    moveTo(center.x, center.y + radius - 22f)
                    lineTo(center.x - 14f, center.y)
                    lineTo(center.x + 14f, center.y)
                    close()
                }
                drawPath(southPath, color = Color(0xFF64B5F6))
            }

            // Fixed Center Heading Readout
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${orientation.azimuthDegrees.toInt()}°",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "HEADING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 3D Tilt Visualizer (Pitch & Roll Card)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3D Artificial Horizon & Tilt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF263238)),
                    contentAlignment = Alignment.Center
                ) {
                    // Artificial Horizon Pitch / Roll line
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = -orientation.rollDegrees
                                translationY = (orientation.pitchDegrees / 90f) * 60f
                            }
                    ) {
                        val midY = size.height / 2f
                        drawLine(
                            color = Color(0xFF4CAF50),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 3f
                        )
                        drawCircle(
                            color = Color(0xFF4CAF50),
                            radius = 6f,
                            center = Offset(size.width / 2f, midY)
                        )
                    }

                    // Pitch & Roll Readouts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pitch: ${String.format("%+.1f", orientation.pitchDegrees)}°",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Roll: ${String.format("%+.1f", orientation.rollDegrees)}°",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Magnetic Field Strength Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Geomagnetic Field Strength",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${String.format("%.1f", magneticField.totalFieldUt)} µT",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bx: ${String.format("%+.1f", magneticField.x)} µT",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE53935)
                    )
                    Text(
                        text = "By: ${String.format("%+.1f", magneticField.y)} µT",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF43A047)
                    )
                    Text(
                        text = "Bz: ${String.format("%+.1f", magneticField.z)} µT",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF1E88E5)
                    )
                }
            }
        }

        // Proximity Sensor Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Proximity Sensor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (hasProximity) "Distance: ${proximity.distanceCm} cm" else "Not supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (proximity.isNear) Color(0xFFE53935).copy(alpha = 0.18f) else Color(0xFF4CAF50).copy(alpha = 0.18f)
                ) {
                    Text(
                        text = if (proximity.isNear) "NEAR (Obstructed)" else "FAR (Clear)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (proximity.isNear) Color(0xFFE53935) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracyBadge(accuracy: Int) {
    val (text, color) = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High Precision" to Color(0xFF43A047)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium Accuracy" to Color(0xFFFB8C00)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low Accuracy" to Color(0xFFE53935)
        else -> "Unreliable / Calibrate" to Color.Gray
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

private fun getCardinalDirection(deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized in 337.5f..360f || normalized in 0f..22.5f -> "North (N)"
        normalized in 22.5f..67.5f -> "North-East (NE)"
        normalized in 67.5f..112.5f -> "East (E)"
        normalized in 112.5f..157.5f -> "South-East (SE)"
        normalized in 157.5f..202.5f -> "South (S)"
        normalized in 202.5f..247.5f -> "South-West (SW)"
        normalized in 247.5f..292.5f -> "West (W)"
        else -> "North-West (NW)"
    }
}

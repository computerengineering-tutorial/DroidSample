package com.example.droidsample.ui.sensors

import android.hardware.Sensor
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.sensors.FilterMode
import com.example.droidsample.sensors.MotionSample
import com.example.droidsample.sensors.MotionSensorManager
import com.example.droidsample.sensors.SamplingDelay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

private const val MAX_HISTORY_POINTS = 120

data class MotionSensorOption(
    val type: Int,
    val name: String,
    val defaultMaxY: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionOscilloscopeView(
    samplingDelay: SamplingDelay,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val motionManager = remember(context) { MotionSensorManager(context) }

    val sensorOptions = remember {
        listOf(
            MotionSensorOption(Sensor.TYPE_ACCELEROMETER, "Accelerometer (m/s²)", 20f),
            MotionSensorOption(Sensor.TYPE_GYROSCOPE, "Gyroscope (rad/s)", 10f),
            MotionSensorOption(Sensor.TYPE_GRAVITY, "Gravity Vector (m/s²)", 15f),
            MotionSensorOption(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration (m/s²)", 15f)
        )
    }

    var selectedSensor by remember { mutableStateOf(sensorOptions[0]) }
    var isSensorMenuExpanded by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(FilterMode.NONE) }
    var isPaused by remember { mutableStateOf(false) }

    val xHistory = remember { mutableStateListOf<Float>() }
    val yHistory = remember { mutableStateListOf<Float>() }
    val zHistory = remember { mutableStateListOf<Float>() }
    var currentSample by remember { mutableStateOf(MotionSample()) }

    LaunchedEffect(selectedSensor.type, samplingDelay, filterMode, isPaused) {
        if (isPaused) return@LaunchedEffect

        xHistory.clear()
        yHistory.clear()
        zHistory.clear()

        motionManager.observeMotionSensor(
            sensorType = selectedSensor.type,
            samplingDelay = samplingDelay,
            filterMode = filterMode,
            filterAlpha = if (filterMode == FilterMode.LOW_PASS) 0.15f else 0.8f
        ).collectLatest { sample ->
            currentSample = sample

            xHistory.add(sample.x)
            yHistory.add(sample.y)
            zHistory.add(sample.z)

            if (xHistory.size > MAX_HISTORY_POINTS) xHistory.removeAt(0)
            if (yHistory.size > MAX_HISTORY_POINTS) yHistory.removeAt(0)
            if (zHistory.size > MAX_HISTORY_POINTS) zHistory.removeAt(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Controls: Sensor Dropdown & Pause/Resume
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = isSensorMenuExpanded,
                onExpandedChange = { isSensorMenuExpanded = !isSensorMenuExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedSensor.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active Sensor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSensorMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = isSensorMenuExpanded,
                    onDismissRequest = { isSensorMenuExpanded = false }
                ) {
                    sensorOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                selectedSensor = option
                                isSensorMenuExpanded = false
                            }
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = { isPaused = !isPaused },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isPaused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPaused) "Resume" else "Pause")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Mode Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Filter:", style = MaterialTheme.typography.labelMedium)
            FilterChip(
                selected = filterMode == FilterMode.NONE,
                onClick = { filterMode = FilterMode.NONE },
                label = { Text("Raw") }
            )
            FilterChip(
                selected = filterMode == FilterMode.LOW_PASS,
                onClick = { filterMode = FilterMode.LOW_PASS },
                label = { Text("Low-Pass (Smooth)") }
            )
            FilterChip(
                selected = filterMode == FilterMode.HIGH_PASS,
                onClick = { filterMode = FilterMode.HIGH_PASS },
                label = { Text("High-Pass") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Real-Time Value Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AxisValueCard(label = "X", value = currentSample.x, color = Color(0xFFE53935), modifier = Modifier.weight(1f))
            AxisValueCard(label = "Y", value = currentSample.y, color = Color(0xFF43A047), modifier = Modifier.weight(1f))
            AxisValueCard(label = "Z", value = currentSample.z, color = Color(0xFF1E88E5), modifier = Modifier.weight(1f))
            AxisValueCard(
                label = "|V|",
                value = currentSample.magnitude,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Oscilloscope Graph View on Canvas
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color(0xFF121417)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    // Draw Grid Lines
                    val gridColor = Color(0x33888888)
                    val numHGrids = 4
                    for (i in 0..numHGrids) {
                        val y = height * (i / numHGrids.toFloat())
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Zero reference line
                    drawLine(
                        color = Color(0x66FFFFFF),
                        start = Offset(0f, centerY),
                        end = Offset(width, centerY),
                        strokeWidth = 1.5f
                    )

                    val maxVal = selectedSensor.defaultMaxY
                    fun scaleY(v: Float): Float {
                        val normalized = (v / maxVal).coerceIn(-1.5f, 1.5f)
                        return centerY - (normalized * (height / 3f))
                    }

                    fun drawTrace(history: List<Float>, color: Color) {
                        if (history.size < 2) return
                        val path = Path()
                        val stepX = width / (MAX_HISTORY_POINTS - 1).toFloat()

                        val startOffset = MAX_HISTORY_POINTS - history.size
                        val firstX = startOffset * stepX
                        val firstY = scaleY(history[0])
                        path.moveTo(firstX, firstY)

                        for (i in 1 until history.size) {
                            val x = (startOffset + i) * stepX
                            val y = scaleY(history[i])
                            path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Render 3 Axes
                    drawTrace(xHistory, Color(0xFFEF5350))
                    drawTrace(yHistory, Color(0xFF66BB6A))
                    drawTrace(zHistory, Color(0xFF42A5F5))
                }

                // Legend overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendItem("X Axis", Color(0xFFEF5350))
                    LegendItem("Y Axis", Color(0xFF66BB6A))
                    LegendItem("Z Axis", Color(0xFF42A5F5))
                }
            }
        }
    }
}

@Composable
private fun AxisValueCard(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format("%+.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

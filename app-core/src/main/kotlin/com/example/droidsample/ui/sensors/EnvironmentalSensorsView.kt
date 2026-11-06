package com.example.droidsample.ui.sensors

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.sensors.EnvironmentalData
import com.example.droidsample.sensors.EnvironmentalSensorManager
import com.example.droidsample.sensors.SamplingDelay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EnvironmentalSensorsView(
    samplingDelay: SamplingDelay,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val envManager = remember(context) { EnvironmentalSensorManager(context) }
    var envData by remember { mutableStateOf(EnvironmentalData()) }

    val hasLight = remember(envManager) { envManager.hasLightSensor() }
    val hasPressure = remember(envManager) { envManager.hasPressureSensor() }
    val hasTemperature = remember(envManager) { envManager.hasTemperatureSensor() }
    val hasHumidity = remember(envManager) { envManager.hasHumiditySensor() }

    LaunchedEffect(samplingDelay) {
        envManager.observeAllEnvironmental(samplingDelay).collectLatest { data ->
            envData = data
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Environmental Gauges",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Real-time atmospheric and ambient environment monitoring",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Light Sensor Card
        EnvironmentalGaugeCard(
            title = "Ambient Illuminance",
            valueText = envData.lightLux?.let { "${String.format("%.1f", it)} Lux" } ?: "No reading",
            statusLabel = envData.lightLux?.let { classifyLux(it) } ?: (if (hasLight) "Waiting..." else "Sensor Absent"),
            icon = Icons.Default.WbSunny,
            accentColor = Color(0xFFFFA000),
            isAvailable = hasLight,
            fraction = envData.lightLux?.let { (it / 10000f).coerceIn(0f, 1f) } ?: 0f,
            minMaxLabel = "0 lx  —  10,000+ lx (Direct Sun)"
        )

        // Barometric Pressure Card
        EnvironmentalGaugeCard(
            title = "Atmospheric Pressure",
            valueText = envData.pressureHpa?.let { "${String.format("%.2f", it)} hPa" } ?: "No reading",
            statusLabel = envData.pressureHpa?.let { classifyPressure(it) } ?: (if (hasPressure) "Waiting..." else "Sensor Absent"),
            icon = Icons.Default.Air,
            accentColor = Color(0xFF00ACC1),
            isAvailable = hasPressure,
            fraction = envData.pressureHpa?.let { ((it - 900f) / (1100f - 900f)).coerceIn(0f, 1f) } ?: 0.5f,
            minMaxLabel = "900 hPa (Low/Storm)  —  1100 hPa (High/Clear)"
        )

        // Estimated Barometric Altitude Card
        EnvironmentalGaugeCard(
            title = "Estimated Barometric Altitude",
            valueText = envData.estimatedAltitudeMeters?.let { "${String.format("%.1f", it)} m" } ?: "No reading",
            statusLabel = if (hasPressure) "Derived from standard sea level (1013.25 hPa)" else "Requires Barometer",
            icon = Icons.Default.Terrain,
            accentColor = Color(0xFF5E35B1),
            isAvailable = hasPressure,
            fraction = envData.estimatedAltitudeMeters?.let { (it / 5000f).coerceIn(0f, 1f) } ?: 0f,
            minMaxLabel = "0 m (Sea Level)  —  5,000 m"
        )

        // Temperature & Humidity Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactEnvCard(
                title = "Ambient Temp",
                valueText = envData.ambientTemperatureCelsius?.let { "${String.format("%.1f", it)} °C" } ?: "N/A",
                icon = Icons.Default.DeviceThermostat,
                color = Color(0xFFE64A19),
                isAvailable = hasTemperature,
                modifier = Modifier.weight(1f)
            )

            CompactEnvCard(
                title = "Humidity",
                valueText = envData.relativeHumidityPercent?.let { "${String.format("%.1f", it)} %" } ?: "N/A",
                icon = Icons.Default.WaterDrop,
                color = Color(0xFF0288D1),
                isAvailable = hasHumidity,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EnvironmentalGaugeCard(
    title: String,
    valueText: String,
    statusLabel: String,
    icon: ImageVector,
    accentColor: Color,
    isAvailable: Boolean,
    fraction: Float,
    minMaxLabel: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAvailable) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (isAvailable) "Active" else "Unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAvailable) accentColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = minMaxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CompactEnvCard(
    title: String,
    valueText: String,
    icon: ImageVector,
    color: Color,
    isAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                text = if (isAvailable) "Hardware Online" else "Hardware Missing",
                style = MaterialTheme.typography.labelSmall,
                color = if (isAvailable) color else MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun classifyLux(lux: Float): String {
    return when {
        lux < 1f -> "Pitch Darkness"
        lux < 15f -> "Dim Ambient / Twilight"
        lux < 100f -> "Dim Indoors"
        lux < 500f -> "Normal Indoor / Living Room"
        lux < 1000f -> "Bright Office / Workspace"
        lux < 10000f -> "Overcast Daylight"
        else -> "Direct Sunlight"
    }
}

private fun classifyPressure(hPa: Float): String {
    return when {
        hPa < 990f -> "Low Pressure (Stormy / Rain)"
        hPa in 990f..1010f -> "Changeable / Moderate Weather"
        hPa in 1010f..1025f -> "Standard Atmospheric / Fair"
        else -> "High Pressure (Very Dry / Settled)"
    }
}

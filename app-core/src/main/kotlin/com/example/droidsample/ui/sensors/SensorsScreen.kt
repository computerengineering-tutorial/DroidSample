package com.example.droidsample.ui.sensors

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.sensors.HingeAngleSample
import com.example.droidsample.sensors.NovelSensorManager
import com.example.droidsample.sensors.SamplingDelay
import com.example.droidsample.sensors.SensorInventoryManager
import kotlinx.coroutines.flow.collectLatest

enum class SensorTab(val title: String, val icon: ImageVector) {
    CATALOG("Catalog", Icons.Default.Category),
    MOTION("Motion", Icons.Default.Timeline),
    ENVIRONMENT("Environment", Icons.Default.WbSunny),
    COMPASS("3D Compass", Icons.Default.CompassCalibration),
    PEDOMETER("Steps", Icons.AutoMirrored.Filled.DirectionsWalk)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inventoryManager = remember(context) { SensorInventoryManager(context) }
    val novelManager = remember(context) { NovelSensorManager(context) }

    val allSensors = remember(inventoryManager) { inventoryManager.getAllSensors() }
    var selectedTab by remember { mutableStateOf(SensorTab.CATALOG) }
    var currentSamplingDelay by remember { mutableStateOf(SamplingDelay.UI) }
    var isRateMenuExpanded by remember { mutableStateOf(false) }

    var hingeSample by remember { mutableStateOf(HingeAngleSample()) }
    val hasHinge = remember(novelManager) { novelManager.hasHingeAngleSensor() }

    LaunchedEffect(hasHinge) {
        if (hasHinge) {
            novelManager.observeHingeAngle().collectLatest {
                hingeSample = it
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Sensors & Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasHinge) {
                            Text(
                                text = "Foldable: ${hingeSample.posture.displayName} (${hingeSample.angleDegrees.toInt()}°)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Sampling Rate Selector
                    Box {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { isRateMenuExpanded = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.AvTimer,
                                    contentDescription = "Sampling Rate",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentSamplingDelay.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isRateMenuExpanded,
                            onDismissRequest = { isRateMenuExpanded = false }
                        ) {
                            SamplingDelay.values().forEach { delayOption ->
                                DropdownMenuItem(
                                    text = { Text(delayOption.displayName) },
                                    onClick = {
                                        currentSamplingDelay = delayOption
                                        isRateMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scrollable Tab Row for 5 Sub-Screens
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SensorTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    SensorTab.CATALOG -> {
                        SensorCatalogView(sensors = allSensors)
                    }
                    SensorTab.MOTION -> {
                        MotionOscilloscopeView(samplingDelay = currentSamplingDelay)
                    }
                    SensorTab.ENVIRONMENT -> {
                        EnvironmentalSensorsView(samplingDelay = currentSamplingDelay)
                    }
                    SensorTab.COMPASS -> {
                        Compass3DView(samplingDelay = currentSamplingDelay)
                    }
                    SensorTab.PEDOMETER -> {
                        StepTrackerView(samplingDelay = currentSamplingDelay)
                    }
                }
            }
        }
    }
}

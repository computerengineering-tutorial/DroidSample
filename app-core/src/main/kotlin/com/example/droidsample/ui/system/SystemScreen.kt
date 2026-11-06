package com.example.droidsample.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.system.NotificationHelper
import com.example.droidsample.system.SystemBroadcastReceiver

/**
 * System Screen Tab Definition.
 */
enum class SystemScreenTab(
    val title: String,
    val icon: ImageVector
) {
    NOTIFICATIONS("Notifications", Icons.Default.Notifications),
    EVENT_LOG("Event Log", Icons.AutoMirrored.Filled.ListAlt),
    BATTERY("Battery & Power", Icons.Default.BatteryChargingFull),
    FOREGROUND("Foreground", Icons.Default.PlayCircle),
    SCHEDULER("Scheduler", Icons.Default.Schedule)
}

/**
 * Main parent dashboard for Subagent 11:
 * System Events, Notifications, Battery Diagnostics, Foreground Services & Work Scheduler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    // Lifecycle-aware registration of dynamic broadcasts while screen is active
    DisposableEffect(Unit) {
        NotificationHelper.initChannels(context)
        SystemBroadcastReceiver.registerDynamic(context)

        onDispose {
            SystemBroadcastReceiver.unregisterDynamic(context)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "System Services & Events",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Notifications, Broadcasts, Telemetry & Background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
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
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 12.dp
            ) {
                SystemScreenTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }

            when (SystemScreenTab.values()[selectedTabIndex]) {
                SystemScreenTab.NOTIFICATIONS -> NotificationLabView()
                SystemScreenTab.EVENT_LOG -> SystemEventLogView()
                SystemScreenTab.BATTERY -> BatteryHealthView()
                SystemScreenTab.FOREGROUND -> ForegroundServiceView()
                SystemScreenTab.SCHEDULER -> BackgroundSchedulerView()
            }
        }
    }
}

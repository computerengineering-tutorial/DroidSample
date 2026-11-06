package com.example.droidsample.ui.network

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.droidsample.network.NetworkAdapterInfo
import com.example.droidsample.network.NetworkInterfaceInspector
import com.example.droidsample.network.NetworkMonitor
import com.example.droidsample.network.RealTimeTrafficRate
import com.example.droidsample.network.TrafficSnapshot
import com.example.droidsample.network.TrafficStatsHelper
import com.example.droidsample.network.WifiDetails
import com.example.droidsample.network.WifiInfoManager
import kotlinx.coroutines.delay

/**
 * Tab items for the Network feature hub.
 */
enum class NetworkTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Public),
    WIFI("Wi-Fi", Icons.Default.Wifi),
    TRAFFIC("Traffic Stats", Icons.Default.Speed),
    INTERFACES("Interfaces", Icons.Default.Lan),
    DIAGNOSTICS("Ping Lab", Icons.Default.Dns)
}

/**
 * Primary root screen for Network, Wi-Fi, Traffic Statistics, and Connectivity Diagnostics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Services
    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val wifiInfoManager = remember(context) { WifiInfoManager(context) }

    // State collections
    val networkState by networkMonitor.networkState.collectAsStateWithLifecycle(
        initialValue = networkMonitor.getCurrentNetworkState()
    )

    var wifiDetails by remember { mutableStateOf(wifiInfoManager.getWifiDetails()) }
    var trafficSnapshot by remember { mutableStateOf(TrafficStatsHelper.getSnapshot()) }
    var realTimeRate by remember { mutableStateOf(RealTimeTrafficRate()) }
    var adapters by remember { mutableStateOf(NetworkInterfaceInspector.getNetworkInterfaces()) }

    var selectedTabIndex by remember { mutableIntStateOf(NetworkTab.OVERVIEW.ordinal) }

    // Live traffic and snapshot polling
    LaunchedEffect(Unit) {
        TrafficStatsHelper.observeTrafficRate(pollIntervalMs = 1000L).collect { rate ->
            realTimeRate = rate
            trafficSnapshot = TrafficStatsHelper.getSnapshot()
        }
    }

    // Wi-Fi details auto-refresh when Wi-Fi is active
    LaunchedEffect(networkState) {
        wifiDetails = wifiInfoManager.getWifiDetails()
        adapters = NetworkInterfaceInspector.getNetworkInterfaces()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Network & Wi-Fi Monitor") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            // Scrollable Tab Navigation
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                NetworkTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = null) }
                    )
                }
            }

            // Tab View Body
            Box(modifier = Modifier.fillMaxSize()) {
                when (NetworkTab.entries[selectedTabIndex]) {
                    NetworkTab.OVERVIEW -> {
                        NetworkDashboardView(state = networkState)
                    }
                    NetworkTab.WIFI -> {
                        WifiDetailsView(
                            wifiDetails = wifiDetails,
                            onRefresh = { wifiDetails = wifiInfoManager.getWifiDetails() }
                        )
                    }
                    NetworkTab.TRAFFIC -> {
                        TrafficStatsView(
                            rate = realTimeRate,
                            snapshot = trafficSnapshot
                        )
                    }
                    NetworkTab.INTERFACES -> {
                        NetworkInterfacesView(
                            adapters = adapters,
                            onRefresh = { adapters = NetworkInterfaceInspector.getNetworkInterfaces() }
                        )
                    }
                    NetworkTab.DIAGNOSTICS -> {
                        PingBenchmarkView()
                    }
                }
            }
        }
    }
}

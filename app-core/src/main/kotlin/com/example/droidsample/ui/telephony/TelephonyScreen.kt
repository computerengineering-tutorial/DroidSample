package com.example.droidsample.ui.telephony

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.droidsample.telephony.CellInfoScanner
import com.example.droidsample.telephony.CellScanResult
import com.example.droidsample.telephony.CellularNetworkManager
import com.example.droidsample.telephony.CellularSignalInfo
import com.example.droidsample.telephony.PhoneNumberHelper
import com.example.droidsample.telephony.RestrictedTelephonyAuditor
import com.example.droidsample.telephony.SubscriptionInfoManager
import com.example.droidsample.telephony.SubscriptionsState
import com.example.droidsample.telephony.TelephonySecurityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TelephonyTab(val title: String) {
    SIM_CARDS("SIM Cards"),
    CELLULAR_SIGNAL("Cellular & Towers"),
    PHONE_NUMBER("Phone Number"),
    SECURITY_AUDIT("Security Audit")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelephonyScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Instantiate Managers
    val subscriptionManager = remember { SubscriptionInfoManager(context) }
    val networkManager = remember { CellularNetworkManager(context) }
    val cellScanner = remember { CellInfoScanner(context) }
    val auditor = remember { RestrictedTelephonyAuditor(context) }

    // State bindings
    var subscriptionsState by remember { mutableStateOf(subscriptionManager.getSubscriptionsState()) }
    var signalInfo by remember { mutableStateOf(networkManager.getCurrentSignalInfo()) }
    var cellScanResult by remember { mutableStateOf(CellScanResult()) }
    var isScanningTowers by remember { mutableStateOf(false) }
    var securityReport by remember { mutableStateOf(auditor.auditRestrictedIdentifiers()) }

    var selectedTabIndex by remember { mutableIntStateOf(TelephonyTab.SIM_CARDS.ordinal) }

    // Detected device phone number & country ISO
    val detectedCountryIso = remember { PhoneNumberHelper.detectDeviceCountryIso(context) }
    val defaultSubId = subscriptionsState.activeSubscriptions.firstOrNull()?.subscriptionId
    val deviceLine1 = remember(subscriptionsState) {
        PhoneNumberHelper.getLine1Number(context, defaultSubId)
    }

    // Permission launcher for Telephony & Location permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        subscriptionsState = subscriptionManager.getSubscriptionsState()
        signalInfo = networkManager.getCurrentSignalInfo()
        securityReport = auditor.auditRestrictedIdentifiers()

        // Scan towers after permission granted
        coroutineScope.launch {
            isScanningTowers = true
            cellScanResult = withContext(Dispatchers.IO) { cellScanner.scanCellTowers() }
            isScanningTowers = false
        }
    }

    // Subscribe to reactive flows
    LaunchedEffect(Unit) {
        launch {
            subscriptionManager.subscriptionsFlow().collect { state ->
                subscriptionsState = state
            }
        }
        launch {
            networkManager.cellularSignalFlow().collect { info ->
                signalInfo = info
            }
        }
        // Initial scan of cell towers in background
        launch {
            isScanningTowers = true
            cellScanResult = withContext(Dispatchers.IO) { cellScanner.scanCellTowers() }
            isScanningTowers = false
        }
    }

    fun requestAllTelephonyPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Telephony & SIM Cards",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
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
            // Tab Header Row
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                TelephonyTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            when (tab) {
                                TelephonyTab.SIM_CARDS -> Icon(Icons.Default.SimCard, contentDescription = null)
                                TelephonyTab.CELLULAR_SIGNAL -> Icon(Icons.Default.SignalCellularAlt, contentDescription = null)
                                TelephonyTab.PHONE_NUMBER -> Icon(Icons.Default.Phone, contentDescription = null)
                                TelephonyTab.SECURITY_AUDIT -> Icon(Icons.Default.Security, contentDescription = null)
                            }
                        }
                    )
                }
            }

            // Tab Content Body
            Box(modifier = Modifier.fillMaxSize()) {
                when (TelephonyTab.entries[selectedTabIndex]) {
                    TelephonyTab.SIM_CARDS -> {
                        SimCardsView(
                            subscriptionsState = subscriptionsState,
                            onRefresh = {
                                subscriptionsState = subscriptionManager.getSubscriptionsState()
                            },
                            onRequestPermission = ::requestAllTelephonyPermissions
                        )
                    }
                    TelephonyTab.CELLULAR_SIGNAL -> {
                        CellularSignalView(
                            signalInfo = signalInfo,
                            scanResult = cellScanResult,
                            isScanning = isScanningTowers,
                            onScanTowers = {
                                coroutineScope.launch {
                                    isScanningTowers = true
                                    cellScanResult = withContext(Dispatchers.IO) { cellScanner.scanCellTowers() }
                                    isScanningTowers = false
                                }
                            },
                            onRequestLocationPermission = ::requestAllTelephonyPermissions
                        )
                    }
                    TelephonyTab.PHONE_NUMBER -> {
                        PhoneNumberView(
                            deviceLine1Number = deviceLine1,
                            detectedCountryIso = detectedCountryIso
                        )
                    }
                    TelephonyTab.SECURITY_AUDIT -> {
                        TelephonySecurityView(
                            securityReport = securityReport,
                            onRerunAudit = {
                                securityReport = auditor.auditRestrictedIdentifiers()
                            }
                        )
                    }
                }
            }
        }
    }
}

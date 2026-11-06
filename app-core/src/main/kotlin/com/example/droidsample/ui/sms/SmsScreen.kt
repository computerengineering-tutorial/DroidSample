package com.example.droidsample.ui.sms

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.sms.DefaultSmsRoleManager
import com.example.droidsample.sms.MmsMetadataHelper
import com.example.droidsample.sms.SmsDatabaseRepository
import com.example.droidsample.sms.SmsRetrieverHelper
import com.example.droidsample.sms.SmsSenderHelper

enum class SmsTab(val title: String) {
    COMPOSER("Composer"),
    INBOX("Inbox & Threads"),
    MONITOR("Live Monitor"),
    OTP("OTP Lab"),
    ROLE("Default Role")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Instantiate helpers
    val senderHelper = remember { SmsSenderHelper(context) }
    val dbRepository = remember { SmsDatabaseRepository(context) }
    val retrieverHelper = remember { SmsRetrieverHelper(context) }
    val mmsHelper = remember { MmsMetadataHelper(context) }
    val roleManager = remember { DefaultSmsRoleManager(context) }

    // Register / clean up lifecycle listeners
    DisposableEffect(Unit) {
        senderHelper.registerCallbacks()
        mmsHelper.registerWapPushReceiver()
        onDispose {
            senderHelper.unregisterCallbacks()
            mmsHelper.unregisterWapPushReceiver()
            retrieverHelper.stopRetriever()
        }
    }

    var selectedTab by remember { mutableStateOf(SmsTab.COMPOSER) }
    var isDefaultSms by remember { mutableStateOf(roleManager.isDefaultSmsApp()) }

    // Role request launcher
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isDefaultSms = roleManager.isDefaultSmsApp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SMS, MMS & Messaging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDefaultSms) "Role: Default SMS App (Full R/W)" else "Role: Standard Client (Read Only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDefaultSms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isDefaultSms && roleManager.isRoleAvailable()) {
                        FilledTonalButton(
                            onClick = {
                                val intent = roleManager.createRequestRoleIntent()
                                if (intent != null) {
                                    roleLauncher.launch(intent)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Set Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrollable Tab Row
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 12.dp
            ) {
                SmsTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                SmsTab.COMPOSER -> {
                    SmsComposerView(
                        smsSenderHelper = senderHelper,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SmsTab.INBOX -> {
                    SmsInboxView(
                        repository = dbRepository,
                        senderHelper = senderHelper,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SmsTab.MONITOR -> {
                    SmsMonitorView(
                        mmsHelper = mmsHelper,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SmsTab.OTP -> {
                    SmsVerificationView(
                        retrieverHelper = retrieverHelper,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                SmsTab.ROLE -> {
                    SmsRoleDetailsView(
                        roleManager = roleManager,
                        isDefault = isDefaultSms,
                        onRequestRole = {
                            val intent = roleManager.createRequestRoleIntent()
                            if (intent != null) {
                                roleLauncher.launch(intent)
                            }
                        },
                        onRefresh = {
                            isDefaultSms = roleManager.isDefaultSmsApp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SmsRoleDetailsView(
    roleManager: DefaultSmsRoleManager,
    isDefault: Boolean,
    onRequestRole: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDefault)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDefault) "Default SMS App Active" else "Secondary SMS Client",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (isDefault) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current system default: ${roleManager.getCurrentDefaultSmsPackage()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRequestRole,
                            enabled = !isDefault && roleManager.isRoleAvailable()
                        ) {
                            Text("Request Role.ROLE_SMS")
                        }

                        OutlinedButton(onClick = onRefresh) {
                            Text("Refresh Status")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Android Default SMS App Security Matrix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Since Android 4.4 (KitKat) and Android 10 (Q) RoleManager, core SMS database write access is restricted to the single user-selected Default SMS application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        items(DefaultSmsRoleManager.PRIVILEGES) { privilege ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (privilege.isDefaultOnly) Icons.Default.VpnKey else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (privilege.isDefaultOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = privilege.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (privilege.isDefaultOnly) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Default Only",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = privilege.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

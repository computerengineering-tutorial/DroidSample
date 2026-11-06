package com.example.droidsample.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed class StorageSecurityTab(val title: String, val icon: ImageVector) {
    data object Storage : StorageSecurityTab("Storage", Icons.Default.Folder)
    data object MediaStore : StorageSecurityTab("MediaStore", Icons.Default.PhotoLibrary)
    data object DeviceInfo : StorageSecurityTab("Device Info", Icons.Default.PhoneAndroid)
    data object Permissions : StorageSecurityTab("Permissions", Icons.Default.Security)
    data object TestRunner : StorageSecurityTab("Test Runner", Icons.Default.CheckCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSecurityScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        listOf(
            StorageSecurityTab.Storage,
            StorageSecurityTab.MediaStore,
            StorageSecurityTab.DeviceInfo,
            StorageSecurityTab.Permissions,
            StorageSecurityTab.TestRunner
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Storage, Security & Diagnostics") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
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
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> StorageExplorerView()
                    1 -> MediaStoreBrowserView()
                    2 -> DeviceInfoView()
                    3 -> PermissionsRoleView()
                    4 -> ApiTestRunnerView()
                }
            }
        }
    }
}

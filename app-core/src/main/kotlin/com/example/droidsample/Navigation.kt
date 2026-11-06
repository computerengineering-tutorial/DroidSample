package com.example.droidsample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.droidsample.ui.audio.AudioScreen
import com.example.droidsample.ui.calling.CallingScreen
import com.example.droidsample.ui.camera.CameraScreen
import com.example.droidsample.ui.connectivity.ConnectivityScreen
import com.example.droidsample.ui.home.HomeScreen
import com.example.droidsample.ui.location.LocationScreen
import com.example.droidsample.ui.media.MediaScreen
import com.example.droidsample.ui.network.NetworkScreen
import com.example.droidsample.ui.sensors.SensorsScreen
import com.example.droidsample.ui.sms.SmsScreen
import com.example.droidsample.ui.storage.StorageSecurityScreen
import com.example.droidsample.ui.system.SystemScreen
import com.example.droidsample.ui.telephony.TelephonyScreen

/**
 * Top-level application navigation router.
 * Connects all 12 core native Android capability screens to their respective [NavKey] entries
 * with backstack support via Jetpack Navigation 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(HomeNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            // Master Dashboard
            entry<HomeNavKey> {
                HomeScreen(
                    onNavigate = { destinationKey ->
                        backStack.add(destinationKey)
                    }
                )
            }

            // Module 01: Camera Lab
            entry<CameraNavKey> {
                CameraScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 02: Audio Studio
            entry<AudioNavKey> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Audio Studio") },
                            navigationIcon = {
                                IconButton(onClick = { backStack.removeLastOrNull() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Navigate Back"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                ) { innerPadding ->
                    AudioScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }

            // Module 03: Video & Media Pipeline
            entry<MediaNavKey> {
                MediaScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 04: Telephony & Multi-SIM
            entry<TelephonyNavKey> {
                TelephonyScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 05: Calling & Telecom
            entry<CallingNavKey> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Calling & Telecom") },
                            navigationIcon = {
                                IconButton(onClick = { backStack.removeLastOrNull() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Navigate Back"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                ) { innerPadding ->
                    CallingScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }

            // Module 06: SMS & Messaging
            entry<SmsNavKey> {
                SmsScreen(
                    onNavigateUp = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 07: Location & GNSS
            entry<LocationNavKey> {
                LocationScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 08: Sensor Suite
            entry<SensorsNavKey> {
                SensorsScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 09: Bluetooth & USB Connectivity
            entry<ConnectivityNavKey> {
                ConnectivityScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 10: Network & Wi-Fi
            entry<NetworkNavKey> {
                NetworkScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 11: System Events & Notifications
            entry<SystemNavKey> {
                SystemScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Module 12: Storage, Security & Test Runner
            entry<StorageSecurityNavKey> {
                StorageSecurityScreen(
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

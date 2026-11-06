package com.example.droidsample

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Root navigation destinations representing the 12 core native Android capability domains
 * plus the master Home catalog dashboard.
 */
@Serializable
sealed interface AppNavKey : NavKey

/**
 * Root home dashboard showcasing the feature catalog, metrics strip, and search.
 */
@Serializable
data object HomeNavKey : AppNavKey

/**
 * Legacy alias for initial project scaffold compatibility.
 */
typealias Main = HomeNavKey

/**
 * Module 01: Camera Lab (CameraX, Camera2, Camera1, NDK Bridge, Hardware Intents)
 */
@Serializable
data object CameraNavKey : AppNavKey

/**
 * Module 02: Audio Studio (PCM Recording, Routing, Effects DSP, Hardware Mics)
 */
@Serializable
data object AudioNavKey : AppNavKey

/**
 * Module 03: Video & Media Pipeline (MediaCodec, MediaMuxer, Screen Recording, Playback)
 */
@Serializable
data object MediaNavKey : AppNavKey

/**
 * Module 04: Telephony & Multi-SIM (Cellular Subscriptions, Cell Scanner, Carrier Config)
 */
@Serializable
data object TelephonyNavKey : AppNavKey

/**
 * Module 05: Calling & Telecom (TelecomManager, ConnectionService, Call Screening, Call Logs)
 */
@Serializable
data object CallingNavKey : AppNavKey

/**
 * Module 06: SMS & Messaging (SmsManager, Default SMS Role, Retriever, ContentProvider)
 */
@Serializable
data object SmsNavKey : AppNavKey

/**
 * Module 07: Location, GPS & GNSS (LocationManager, Raw GNSS, Geofencing, Geocoding)
 */
@Serializable
data object LocationNavKey : AppNavKey

/**
 * Module 08: Sensor Suite (Motion, Environmental, Position, Health, DSP Filters)
 */
@Serializable
data object SensorsNavKey : AppNavKey

/**
 * Module 09: Connectivity (BLE Scanner, GATT Server/Client, Bluetooth Classic, USB Host)
 */
@Serializable
data object ConnectivityNavKey : AppNavKey

/**
 * Module 10: Network & Wi-Fi (ConnectivityManager, Wi-Fi Info, TrafficStats, Diagnostics)
 */
@Serializable
data object NetworkNavKey : AppNavKey

/**
 * Module 11: System Events & Notifications (Battery, Doze, Foreground Services, Alarms, WorkManager)
 */
@Serializable
data object SystemNavKey : AppNavKey

/**
 * Module 12: Storage, Security & Test Runner (SAF, MediaStore, Biometrics, KeyStore, Diagnostic Suite)
 */
@Serializable
data object StorageSecurityNavKey : AppNavKey

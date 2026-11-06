# Subagent 13: App Shell, Navigation & Master Dashboard Specialist TODO & Implementation Specification

## Scope & Objective
Implement the top-level application navigation, modern Material 3 shell, unified search, capability overview dashboard, and integration glue that connects all 12 feature modules into a single, cohesive Android application using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic & Navigation: `app-core/src/main/kotlin/com/example/droidsample/`
  - `NavigationKeys.kt` (Define all NavKey destinations for all 12 modules)
  - `Navigation.kt` (Full `NavDisplay` and `entryProvider` routing for all screens)
  - `MainActivity.kt` (Activity setup, edge-to-edge, window insets, theme wrapper)
- Home Dashboard & Shell UI: `app-core/src/main/kotlin/com/example/droidsample/ui/home/`
  - `HomeScreen.kt` (Master hub with feature grid, category chips, and live search bar)
  - `FeatureCatalog.kt` (Master list of 100+ native Android APIs with icons, categories, descriptions, and route mappings)
  - `CapabilitySummaryCard.kt` (At-a-glance summary of detected hardware: Camera count, SIM count, Sensor count, Battery %)
  - `SearchFilterBar.kt` (Instant search component filtering features by keyword)
- Theme & Design System: `app-core/src/main/kotlin/com/example/droidsample/theme/`
  - `Color.kt`, `Theme.kt`, `Type.kt` (Polished Material 3 design tokens)
- Manifest & Resources:
  - `app-core/src/main/AndroidManifest.xml` (Comprehensive permissions declaration, activities, services, receivers)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/home/`
  - `FeatureCatalogTest.kt` (Validate that all 12 modules and all APIs are registered in catalog)
  - `NavigationKeysTest.kt` (Ensure route serializability and key uniqueness)

## Detailed Implementation Checklist

### 1. Unified Navigation Model (`NavigationKeys.kt`)
- [ ] Define `@Serializable` NavKeys for every domain:
  - `HomeNavKey` (Main catalog dashboard)
  - `CameraNavKey` (Module 01: Camera Lab)
  - `AudioNavKey` (Module 02: Audio Studio)
  - `MediaNavKey` (Module 03: Video & Media Pipeline)
  - `TelephonyNavKey` (Module 04: Telephony & Multi-SIM)
  - `CallingNavKey` (Module 05: Calling & Telecom)
  - `SmsNavKey` (Module 06: SMS & Messaging)
  - `LocationNavKey` (Module 07: Location, GPS & GNSS)
  - `SensorsNavKey` (Module 08: Sensor Suite)
  - `ConnectivityNavKey` (Module 09: Bluetooth & USB)
  - `NetworkNavKey` (Module 10: Network & Wi-Fi)
  - `SystemNavKey` (Module 11: System Events & Notifications)
  - `StorageSecurityNavKey` (Module 12: Storage, Security & Test Runner)

### 2. Master Navigation Router (`Navigation.kt`)
- [ ] Implement `MainNavigation()` with `androidx.navigation3` backstack.
- [ ] Connect every `NavKey` to its corresponding Compose screen.
- [ ] Provide top app bar with back button, current module title, and quick search.

### 3. Master Dashboard (`HomeScreen.kt`)
- [ ] Top banner: Device model, Android version, active build variant.
- [ ] Quick Metrics Strip:
  - Camera count.
  - Active SIM slots.
  - Connected network type (Wi-Fi / 5G).
  - Battery percentage & charging state.
  - Hardware sensors detected count.
- [ ] Interactive Category Filter:
  - Hardware (Camera, Audio, Sensors, Bluetooth/USB)
  - Telephony & Comms (SIM, Calling, SMS)
  - Connectivity (Location, Network)
  - System & OS (System Events, Storage/Security, API Test Runner)
- [ ] Instant search filtering through all 100+ native Android APIs.
- [ ] Card per module displaying title, icon, summary, and direct jump action.

### 4. Comprehensive Android Manifest (`AndroidManifest.xml`)
- [ ] Declare all required native permissions with appropriate flags:
  - Camera: `android.permission.CAMERA`
  - Audio: `android.permission.RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`
  - Telephony: `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`
  - Calling: `CALL_PHONE`, `READ_CALL_LOG`, `MANAGE_OWN_CALLS`
  - SMS: `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_WAP_PUSH`
  - Location: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
  - Sensors: `BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `HIGH_SAMPLING_RATE_SENSORS`
  - Connectivity: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
  - System: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`
  - Storage: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`
- [ ] Register all services:
  - `AppCallScreeningService`
  - `AppConnectionService`
  - `AppForegroundService`
- [ ] Register all broadcast receivers:
  - `SmsBroadcastReceiver`
  - `SystemBroadcastReceiver`
- [ ] Configure `FileProvider` with `res/xml/file_paths.xml`.

### 5. Tests
- Tests for navigation backstack integrity, feature catalog registry verification, and search filter matching.

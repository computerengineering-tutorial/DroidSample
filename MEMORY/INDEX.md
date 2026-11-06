# Master Coordination Index: Android Complete Device Capability Lab

This directory contains the exact implementation blueprints, checklists, directory structures, and API coverage targets for each parallel subagent.

## Subagent Architecture & Allocation

| Agent ID | File | Domain | Key Native APIs Covered |
|:---|:---|:---|:---|
| **Agent 01** | `MEMORY/agent_01_camera.md` | Camera Complete | Camera1, Camera2, CameraX (Core, Preview, ImageCapture, ImageAnalysis, VideoCapture), Concurrent, Extensions, Intents, NDK bridge |
| **Agent 02** | `MEMORY/agent_02_audio.md` | Audio & Microphone | MediaRecorder, AudioRecord (PCM 8/16/float), AudioRouting, MicrophoneInfo, AudioEffects, RMS/FFT analysis, AAudio/Oboe bridge |
| **Agent 03** | `MEMORY/agent_03_video_media.md` | Video & Media Pipeline | CameraX VideoCapture, Camera2+MediaRecorder, Screen Recording MediaProjection, MediaCodecList, MediaExtractor, MediaMuxer, MediaPlayer |
| **Agent 04** | `MEMORY/agent_04_telephony_sim.md` | Telephony & Multi-SIM | SubscriptionManager (dual-SIM), TelephonyCallback, SignalStrength, 5G/4G/3G/2G, CellInfo, PhoneNumberUtils, eSIM / restricted ID audit |
| **Agent 05** | `MEMORY/agent_05_calling_telecom.md` | Calling & Telecom | TelecomManager, ConnectionService (VoIP), CallScreeningService (spam filter), CallLog, InCallService, PhoneAccount |
| **Agent 06** | `MEMORY/agent_06_sms_messaging.md` | SMS & Messaging | SmsManager (single, multipart, Unicode, SIM picker), SMS BroadcastReceiver PDU decoder, Telephony.Sms ContentProvider, SmsRetriever OTP |
| **Agent 07** | `MEMORY/agent_07_location_gps.md` | Location & GNSS | LocationManager (GPS, Network, Passive), GnssStatus (constellations, C/N0), Geocoder, Proximity Alerts / Geofencing, Mock Location detection |
| **Agent 08** | `MEMORY/agent_08_sensors.md` | Sensor Suite | SensorManager (Accelerometer, Gyroscope, Magnetometer, Barometer, Light, Humidity, Step Counter, Hinge Angle, 3D Orientation matrix) |
| **Agent 09** | `MEMORY/agent_09_connectivity_devices.md` | Bluetooth & USB | Bluetooth Classic, BLE Scanner, BLE GATT Client/Server, CompanionDeviceManager, UsbManager (Host/Accessory/Endpoints) |
| **Agent 10** | `MEMORY/agent_10_network_wifi.md` | Network & Wi-Fi | ConnectivityManager (NetworkCallback, Capabilities), WifiManager (SSID, BSSID, RSSI, Wi-Fi 6/7), TrafficStats, NetworkInterface, Ping test |
| **Agent 11** | `MEMORY/agent_11_system_services.md` | System & Background | NotificationManager (Channels, Styles, Actions), System Broadcasts (Battery, Power, Screen, Boot, Time), Typed Foreground Services, WorkManager, AlarmManager |
| **Agent 12** | `MEMORY/agent_12_storage_permissions.md` | Storage, Security & Tests | Scoped Storage, MediaStore, SAF (ACTION_OPEN_DOCUMENT), Photo Picker, FileProvider, Build info, Permissions/RoleManager, BiometricPrompt, Master API Test Suite Runner |
| **Agent 13** | `MEMORY/agent_13_app_shell.md` | App Shell & Navigation | Master Navigation router, Home Dashboard, Unified Feature Search, System Capability Summary, AndroidManifest unification |

## Global Constraints & Principles for All Subagents
1. **Strictly Code Generation**: No deep reasoning, no verbose conversational preambles. Put maximum context into writing clean, idiomatic Kotlin code and Jetpack Compose UI.
2. **Native Only**: Use only official Android Framework APIs and standard AndroidX / Jetpack libraries (`camera-*`, `compose`, `lifecycle`, `core-ktx`). Do NOT add external third-party libraries for things Android provides natively.
3. **No Directory Confusion**: Every agent's target files and directories are explicitly given in their respective specification file.
4. **Handling Hardware Absence**: Gracefully handle missing hardware (e.g. barometer, dual SIM, flash, front camera) using dynamic availability checks and clear UI status chips.
5. **Standardized Testing**: Every agent writes unit tests for data formatting, mathematical algorithms, state models, and API result decoders.

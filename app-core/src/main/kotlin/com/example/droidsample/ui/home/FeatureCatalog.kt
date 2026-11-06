package com.example.droidsample.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.droidsample.AppNavKey
import com.example.droidsample.AudioNavKey
import com.example.droidsample.CallingNavKey
import com.example.droidsample.CameraNavKey
import com.example.droidsample.ConnectivityNavKey
import com.example.droidsample.LocationNavKey
import com.example.droidsample.MediaNavKey
import com.example.droidsample.NetworkNavKey
import com.example.droidsample.SensorsNavKey
import com.example.droidsample.SmsNavKey
import com.example.droidsample.StorageSecurityNavKey
import com.example.droidsample.SystemNavKey
import com.example.droidsample.TelephonyNavKey

/**
 * High-level category grouping for the 12 Android subsystem domains.
 */
enum class FeatureCategory(val displayName: String, val icon: ImageVector) {
    ALL("All Domains", Icons.Default.Apps),
    HARDWARE("Hardware", Icons.Default.Sensors),
    TELEPHONY_COMMS("Telephony & Comms", Icons.Default.Call),
    CONNECTIVITY("Connectivity", Icons.Default.Wifi),
    SYSTEM_OS("System & OS", Icons.Default.Security)
}

/**
 * Representation of a specific Android SDK or NDK capability.
 */
data class NativeApiItem(
    val name: String,
    val description: String,
    val minApi: Int = 24
)

/**
 * Descriptor defining a feature module within the DroidSample application.
 */
data class ModuleDescriptor(
    val id: String,
    val moduleNumber: Int,
    val name: String,
    val subtitle: String,
    val category: FeatureCategory,
    val description: String,
    val navKey: AppNavKey,
    val icon: ImageVector,
    val keyApis: List<NativeApiItem>
) {
    val apiCount: Int get() = keyApis.size
}

/**
 * Master catalog registry of all 12 modules and over 130 native Android APIs.
 */
object FeatureCatalog {

    val modules: List<ModuleDescriptor> = listOf(
        // 01: Camera Lab
        ModuleDescriptor(
            id = "camera",
            moduleNumber = 1,
            name = "Camera Lab",
            subtitle = "CameraX, Camera2 Pro & NDK Imaging",
            category = FeatureCategory.HARDWARE,
            description = "CameraX preview, high-res captures, concurrent cameras, Camera2 manual controls, sensor metadata, and native C++ NDK image processing.",
            navKey = CameraNavKey,
            icon = Icons.Default.CameraAlt,
            keyApis = listOf(
                NativeApiItem("CameraX Preview & ImageCapture", "Modern lifecycle-aware viewfinder and HDR/flash image capture", 24),
                NativeApiItem("CameraX VideoCapture (Recorder)", "Hardware-accelerated MP4 recording with audio sync", 24),
                NativeApiItem("CameraX ImageAnalysis", "Real-time luminance and computer-vision frame analyzer pipeline", 24),
                NativeApiItem("CameraX Vendor Extensions", "OEM-specific Night, Bokeh, Face Retouch, and HDR modes", 30),
                NativeApiItem("CameraX Concurrent Dual Camera", "Simultaneous front & back multi-camera streaming", 30),
                NativeApiItem("Camera2 CameraManager & Device", "Low-level pipeline session configuration and capture requests", 24),
                NativeApiItem("Camera2 Characteristics Inspector", "Sensor optical stabilization, lens apertures, and active arrays", 24),
                NativeApiItem("Camera2 Manual Exposure & Focus", "Hardware manual ISO, shutter speed, and focus distance", 24),
                NativeApiItem("Camera2 RAW DNG Capture", "Uncompressed Bayer sensor raw image capture to disk", 24),
                NativeApiItem("Camera1 Legacy API", "android.hardware.Camera backward compatibility verification", 24),
                NativeApiItem("Camera NDK Native Bridge", "libcamera2ndk C/C++ low-latency capture pipeline", 24),
                NativeApiItem("Camera Hardware Intents", "MediaStore.ACTION_IMAGE_CAPTURE & ACTION_VIDEO_CAPTURE", 24)
            )
        ),

        // 02: Audio Studio
        ModuleDescriptor(
            id = "audio",
            moduleNumber = 2,
            name = "Audio Studio",
            subtitle = "PCM Streaming, Effects DSP & Routing",
            category = FeatureCategory.HARDWARE,
            description = "Raw PCM 16-bit audio recording, AudioTrack low-latency playback, DSP effects console, acoustic echo cancellation, and hardware mic polar patterns.",
            navKey = AudioNavKey,
            icon = Icons.Default.Mic,
            keyApis = listOf(
                NativeApiItem("AudioRecord PCM Capture", "Direct uncompressed 16-bit / float audio stream recording", 24),
                NativeApiItem("AudioTrack Low-Latency Playback", "Direct buffer audio synthesis and wave playback", 24),
                NativeApiItem("MediaRecorder AAC / Opus", "Compressed multi-format file recording with metadata", 24),
                NativeApiItem("AudioRoutingManager", "AudioDeviceInfo enumeration and dynamic route assignment", 24),
                NativeApiItem("MicrophoneCharacteristics", "Polar direction patterns, frequency responses, and sensitivity", 28),
                NativeApiItem("AcousticEchoCanceler (AEC)", "Hardware-assisted echo cancellation DSP", 24),
                NativeApiItem("AutomaticGainControl (AGC)", "Hardware mic auto gain compensation", 24),
                NativeApiItem("NoiseSuppressor (NS)", "Hardware background noise reduction filter", 24),
                NativeApiItem("Equalizer 5/10-Band DSP", "Real-time parametric audio equalizer filtering", 24),
                NativeApiItem("BassBoost & Reverb Console", "Acoustic bass enhancement and environmental reverberation", 24),
                NativeApiItem("DynamicsProcessing", "Multiband compression and limiter stage (API 28+)", 28),
                NativeApiItem("AudioPlaybackCapture API", "Internal system audio loopback capture (API 29+)", 29),
                NativeApiItem("AAudio Native Bridge", "C/C++ ultra low-latency audio stream integration", 26)
            )
        ),

        // 03: Video & Media Pipeline
        ModuleDescriptor(
            id = "media",
            moduleNumber = 3,
            name = "Media Pipeline",
            subtitle = "MediaCodec, Muxer & Screen Recording",
            category = FeatureCategory.HARDWARE,
            description = "Asynchronous MediaCodec hardware video transcoding, MediaExtractor/Muxer demuxing, MediaProjection virtual display capture, and ExoPlayer playback.",
            navKey = MediaNavKey,
            icon = Icons.Default.VideoLibrary,
            keyApis = listOf(
                NativeApiItem("MediaCodec Asynchronous Video Encoder", "H.264/AVC & H.265/HEVC hardware encoder pipeline", 24),
                NativeApiItem("MediaCodec Hardware Video Decoder", "Hardware-accelerated decoding directly to Surface", 24),
                NativeApiItem("MediaCodec Surface Transcoding", "Input Surface to Output Surface frame pipeline", 24),
                NativeApiItem("MediaCodec Audio Processing", "Raw PCM to AAC/Opus audio codec encoding", 24),
                NativeApiItem("MediaExtractor Demuxer", "Track extraction and sample timestamp indexing", 24),
                NativeApiItem("MediaMuxer Multiplexer", "Interleaved audio/video MP4 container generation", 24),
                NativeApiItem("MediaProjection Screen Recording", "VirtualDisplay capture of device screen to video", 24),
                NativeApiItem("Camera2 + MediaRecorder Pipeline", "Combined camera streaming directly to recording session", 24),
                NativeApiItem("MediaPlayer Video Player", "Hardware SurfaceView playback with playback lifecycle controls", 24),
                NativeApiItem("MediaCodecList Capabilities", "Device codec profiles, color formats, and bitrate levels", 24),
                NativeApiItem("MediaFormat Configuration", "Dynamic mime-type, bitrate, keyframe interval specification", 24)
            )
        ),

        // 04: Telephony & Multi-SIM
        ModuleDescriptor(
            id = "telephony",
            moduleNumber = 4,
            name = "Telephony & SIM",
            subtitle = "Multi-SIM, Cell Towers & Carrier Config",
            category = FeatureCategory.TELEPHONY_COMMS,
            description = "SubscriptionManager multi-SIM detection, CellInfoScanner for 5G NR and LTE towers, TelephonyCallback signal monitors, and carrier configurations.",
            navKey = TelephonyNavKey,
            icon = Icons.Default.SimCard,
            keyApis = listOf(
                NativeApiItem("SubscriptionManager Multi-SIM", "Active SIM card slot enumeration and default voice/data detection", 24),
                NativeApiItem("TelephonyManager Hardware State", "Device SIM state, network operator, and data network type", 24),
                NativeApiItem("TelephonyCallback (API 31+)", "Modern listener for call state, data connection, and signal changes", 31),
                NativeApiItem("PhoneStateListener Legacy Fallback", "Backward compatible radio and service state tracking", 24),
                NativeApiItem("CellInfoScanner (5G NR / LTE / GSM)", "Real-time cell tower identity (PCI, TAC, CID, ARFCN)", 24),
                NativeApiItem("CellSignalStrength Metric Analysis", "RSRP, RSRQ, RSSNR, CQI, and SINR signal values", 24),
                NativeApiItem("ServiceState & Registration Info", "Roaming status, duplex mode, and operator PLMN", 24),
                NativeApiItem("CarrierConfigManager Overrides", "Carrier feature flags, VoLTE, and VoWiFi provisioning", 24),
                NativeApiItem("PhoneNumberUtils Formatter", "E.164 phone number formatting and country code validation", 24),
                NativeApiItem("Restricted Telephony Auditor", "Privacy barrier inspection for IMEI, IMSI, and device IDs", 29)
            )
        ),

        // 05: Calling & Telecom
        ModuleDescriptor(
            id = "calling",
            moduleNumber = 5,
            name = "Calling & Telecom",
            subtitle = "TelecomManager, VoIP & Call Screening",
            category = FeatureCategory.TELEPHONY_COMMS,
            description = "Telecom framework integration, custom ConnectionService for VoIP calling, CallScreeningService for spam identification, and CallLog auditing.",
            navKey = CallingNavKey,
            icon = Icons.Default.Call,
            keyApis = listOf(
                NativeApiItem("TelecomManager Dialer Integration", "System dialer registration, default dialer check and handoff", 24),
                NativeApiItem("TelecomManager.placeCall()", "Direct intent and Telecom URI dialing integration", 24),
                NativeApiItem("ConnectionService VoIP Engine", "Self-managed ConnectionService for standalone calling", 26),
                NativeApiItem("CallScreeningService Spam Filter", "Real-time incoming call blocking and caller identification", 29),
                NativeApiItem("CallAudioState Dynamic Routing", "Switching between earpiece, speaker, and Bluetooth SCO", 24),
                NativeApiItem("InCallService Custom Interface", "Native in-call display and active call management", 24),
                NativeApiItem("CallLog.Calls Provider Querying", "Historical call log querying with duration, timestamp, and type", 24),
                NativeApiItem("CallLog Insertion & Modification", "Programmatic call record registration", 24),
                NativeApiItem("Call Recording Legal Auditor", "Platform call recording policy and capability inspection", 24)
            )
        ),

        // 06: SMS & Messaging
        ModuleDescriptor(
            id = "sms",
            moduleNumber = 6,
            name = "SMS & Messaging",
            subtitle = "SmsManager, Retriever & MMS",
            category = FeatureCategory.TELEPHONY_COMMS,
            description = "SmsManager text and multipart dispatch, Sent/Delivered PendingIntents, SmsRetriever zero-permission OTP extraction, and Default SMS App role handling.",
            navKey = SmsNavKey,
            icon = Icons.Default.Sms,
            keyApis = listOf(
                NativeApiItem("SmsManager Text Dispatch", "Direct single-part SMS transmission via cellular radio", 24),
                NativeApiItem("SmsManager Multipart Dispatch", "Automatic 160-character message chunking and delivery", 24),
                NativeApiItem("Sent & Delivery PendingIntents", "Granular transmission receipts and radio confirmation status", 24),
                NativeApiItem("SmsReceiver Broadcast Listener", "SMS_RECEIVED intent filter parsing PDU messages", 24),
                NativeApiItem("Default SMS Deliver Receiver", "SMS_DELIVER system handler for default SMS role", 24),
                NativeApiItem("SmsRetriever One-Time Code", "Zero-permission automated OTP extraction via Play Services", 24),
                NativeApiItem("SmsUserConsent API", "One-click user consent dialog for incoming SMS verification", 24),
                NativeApiItem("RoleManager ROLE_SMS Provider", "Modern Android RoleManager integration for default SMS handler", 29),
                NativeApiItem("Telephony.Sms ContentProvider", "Inbox, Sent, and Draft database queries and search", 24),
                NativeApiItem("WAP Push MMS Receiver", "vnd.wap.mms-message raw payload decoder", 24)
            )
        ),

        // 07: Location, GPS & GNSS
        ModuleDescriptor(
            id = "location",
            moduleNumber = 7,
            name = "Location & GNSS",
            subtitle = "Satellites, Geofencing & Geocoding",
            category = FeatureCategory.CONNECTIVITY,
            description = "Fused and GPS location providers, raw GnssStatus constellation telemetry, circular geofencing triggers, reverse geocoding, and mock provider detection.",
            navKey = LocationNavKey,
            icon = Icons.Default.LocationOn,
            keyApis = listOf(
                NativeApiItem("LocationManager Fused Provider", "High-accuracy GPS, Wi-Fi, and Cell fused coordinates", 24),
                NativeApiItem("LocationRequest Dynamic Polling", "Configurable update intervals, displacement, and power priorities", 31),
                NativeApiItem("LocationManager.getCurrentLocation()", "Single fresh location retrieval with cancellation tokens", 30),
                NativeApiItem("GnssStatus Satellite Callback", "GPS, GLONASS, Galileo, Beidou satellite signal and azimuth", 24),
                NativeApiItem("GnssMeasurements Listener", "Raw pseudoranges, carrier phase, and Doppler shift", 24),
                NativeApiItem("GnssNavigationMessage Decoder", "Raw satellite broadcast ephemeris and clock frames", 24),
                NativeApiItem("Proximity Alerts & Geofences", "PendingIntent circular boundary entry and exit notifications", 24),
                NativeApiItem("Geocoder Reverse Geocoding", "Translating coordinates to human-readable street addresses", 24),
                NativeApiItem("Geocoder Forward Geocoding", "Searching street addresses to obtain latitude and longitude", 24),
                NativeApiItem("Mock Location Detector", "Security inspection for spoofed or test GPS providers", 24)
            )
        ),

        // 08: Sensor Suite
        ModuleDescriptor(
            id = "sensors",
            moduleNumber = 8,
            name = "Sensor Suite",
            subtitle = "Motion, Environment & DSP Filters",
            category = FeatureCategory.HARDWARE,
            description = "Complete SensorManager inventory, 3-axis accelerometer and gyroscope, ambient light, barometer, step counter, rotation vectors, and low-pass DSP filters.",
            navKey = SensorsNavKey,
            icon = Icons.Default.MotionPhotosOn,
            keyApis = listOf(
                NativeApiItem("SensorManager Complete Inventory", "Dynamic hardware sensor enumeration and manufacturer capabilities", 24),
                NativeApiItem("3-Axis Accelerometer", "Raw linear acceleration including gravitational pull", 24),
                NativeApiItem("3-Axis Gyroscope", "Angular rotation velocity in radians per second", 24),
                NativeApiItem("Geomagnetic Field Magnetometer", "Earth magnetic field measurement in microteslas", 24),
                NativeApiItem("Gravity & Linear Acceleration", "Software-fused gravity separation and pure movement vectors", 24),
                NativeApiItem("Rotation Vector & Orientation", "Quaternion 3D orientation and heading calculation", 24),
                NativeApiItem("Step Counter & Step Detector", "Hardware pedometer step detection and counter accumulation", 24),
                NativeApiItem("Significant Motion Trigger", "Low-power TriggerEventListener wake-up detector", 24),
                NativeApiItem("Ambient Light Lux Meter", "Photometer light sensor for ambient illumination", 24),
                NativeApiItem("Proximity Distance Sensor", "Hardware infrared proximity distance detection", 24),
                NativeApiItem("Barometer Pressure Sensor", "Atmospheric pressure in hPa and barometric altitude calculation", 24),
                NativeApiItem("Heart Rate & Body Sensors", "Optical PPG heart rate monitoring and body contact state", 24),
                NativeApiItem("Digital Signal Processing (DSP)", "Low-pass and high-pass filtering algorithms for sensor stabilization", 24)
            )
        ),

        // 09: Connectivity (Bluetooth & USB)
        ModuleDescriptor(
            id = "connectivity",
            moduleNumber = 9,
            name = "Bluetooth & USB",
            subtitle = "BLE Scanner, GATT Server & USB Host",
            category = FeatureCategory.CONNECTIVITY,
            description = "Bluetooth Low Energy peripheral scanner, GATT client characteristic read/writes, GATT server hosting, classic RFCOMM sockets, and USB Host mode.",
            navKey = ConnectivityNavKey,
            icon = Icons.Default.VpnLock,
            keyApis = listOf(
                NativeApiItem("BluetoothAdapter Hardware State", "Bluetooth radio state, LE 2M PHY, and coded PHY support", 24),
                NativeApiItem("BluetoothLeScanner BLE Scanner", "Filter-based scanning for BLE beacons and peripherals", 24),
                NativeApiItem("ScanFilter & ScanSettings", "Low-latency and low-power BLE scanning policies", 24),
                NativeApiItem("BluetoothGatt Client", "Connecting to peripherals and discovering GATT services", 24),
                NativeApiItem("GATT Characteristic Read/Write", "Subscribing to notifications and writing descriptors", 24),
                NativeApiItem("BluetoothGattServer BLE Hosting", "Hosting local GATT services and advertising peripheral packets", 24),
                NativeApiItem("Bluetooth Classic RFCOMM", "SPP serial port Bluetooth socket connections", 24),
                NativeApiItem("Bluetooth Profile Proxies", "Headset (HFP) and A2DP profile connection queries", 24),
                NativeApiItem("CompanionDeviceManager", "Zero-location-permission Bluetooth device pairing association", 26),
                NativeApiItem("UsbManager USB Host Mode", "Enumeration of connected USB peripherals and OTG devices", 24),
                NativeApiItem("UsbDeviceConnection Transfers", "Bulk, interrupt, and control endpoint data transfers", 24),
                NativeApiItem("UsbInterface Descriptor Parser", "Class, subclass, and protocol descriptor inspector", 24)
            )
        ),

        // 10: Network & Wi-Fi
        ModuleDescriptor(
            id = "network",
            moduleNumber = 10,
            name = "Network & Wi-Fi",
            subtitle = "NetworkCapabilities, Wi-Fi 7 & Traffic",
            category = FeatureCategory.CONNECTIVITY,
            description = "ConnectivityManager real-time NetworkCallback, Wi-Fi 6/7 standards and frequency detection, TrafficStats bandwidth counters, and ping diagnostics.",
            navKey = NetworkNavKey,
            icon = Icons.Default.Wifi,
            keyApis = listOf(
                NativeApiItem("ConnectivityManager NetworkCallback", "Real-time network availability, loss, and capability changes", 24),
                NativeApiItem("NetworkCapabilities Inspector", "NET_CAPABILITY_INTERNET, VALIDATED, NOT_METERED status", 24),
                NativeApiItem("Multi-Transport Detection", "Wi-Fi, Cellular, Ethernet, Bluetooth, and VPN transports", 24),
                NativeApiItem("WifiManager & WifiInfo", "SSID, BSSID, RSSI signal strength, and link speed", 24),
                NativeApiItem("Wi-Fi Standard Detection", "Wi-Fi 4 (802.11n), Wi-Fi 5 (ac), Wi-Fi 6 (ax), Wi-Fi 7 (be)", 30),
                NativeApiItem("Dynamic NetworkRequest Builder", "Custom network routing requests for specific transports", 24),
                NativeApiItem("TrafficStats RX/TX Counters", "Per-UID and total interface bytes and packets transmitted", 24),
                NativeApiItem("NetworkStatsManager Historical Data", "Querying cellular and Wi-Fi historical usage buckets", 24),
                NativeApiItem("NetworkInterface Inspector", "Listing IPv4, IPv6, MAC, MTU, and loopback flags", 24),
                NativeApiItem("Socket & Ping Latency Probe", "ICMP latency diagnostic probe and DNS resolution benchmark", 24),
                NativeApiItem("Captive Portal Verification", "Detection of restricted Wi-Fi login splash portals", 24)
            )
        ),

        // 11: System Events & Notifications
        ModuleDescriptor(
            id = "system",
            moduleNumber = 11,
            name = "System & Power",
            subtitle = "Battery, Notifications & Background",
            category = FeatureCategory.SYSTEM_OS,
            description = "BatteryManager temperature and charging state, PowerManager Doze mode, Android 13+ NotificationChannels, exact Alarms, and WorkManager jobs.",
            navKey = SystemNavKey,
            icon = Icons.Default.PermDeviceInformation,
            keyApis = listOf(
                NativeApiItem("BatteryManager State & Health", "Live battery percentage, voltage, temperature, and technology", 24),
                NativeApiItem("BatteryManager Charging Status", "AC, USB, Wireless, and Dock charging source identification", 24),
                NativeApiItem("PowerManager Doze & Power Save", "Device idle mode and battery saver status auditing", 24),
                NativeApiItem("PowerManager WakeLocks", "Partial wake lock CPU keep-alive management", 24),
                NativeApiItem("NotificationManager Channels", "NotificationChannel and ChannelGroup configuration", 26),
                NativeApiItem("NotificationCompat Rich Builder", "BigTextStyle, messaging styles, progress bars, and actions", 24),
                NativeApiItem("POST_NOTIFICATIONS Permission", "Runtime notification permission management for Android 13+", 33),
                NativeApiItem("ForegroundService Multi-Types", "Camera, microphone, location, and dataSync service types", 29),
                NativeApiItem("WorkManager Constraints Engine", "Periodic and one-time background jobs with battery/network gating", 24),
                NativeApiItem("AlarmManager Exact Scheduling", "setExactAndAllowWhileIdle() precise clock-driven execution", 24),
                NativeApiItem("BroadcastReceiver System Triggers", "BOOT_COMPLETED, POWER_CONNECTED, and AIRPLANE_MODE events", 24)
            )
        ),

        // 12: Storage, Security & Test Runner
        ModuleDescriptor(
            id = "storage",
            moduleNumber = 12,
            name = "Storage & Security",
            subtitle = "SAF, MediaStore, Biometrics & Tests",
            category = FeatureCategory.SYSTEM_OS,
            description = "Storage Access Framework (SAF) folder picker, MediaStore collection queries, BiometricPrompt authentication, hardware KeyStore AES-256, and test runner.",
            navKey = StorageSecurityNavKey,
            icon = Icons.Default.FolderShared,
            keyApis = listOf(
                NativeApiItem("StorageManager Volume Inspector", "Internal, external, and adopted storage partition byte metrics", 24),
                NativeApiItem("MediaStore ContentProvider", "Scoped storage media queries for Images, Video, Audio, Downloads", 29),
                NativeApiItem("SAF (Storage Access Framework)", "OPEN_DOCUMENT, CREATE_DOCUMENT, and file tree pickers", 24),
                NativeApiItem("Persistent SAF URI Permissions", "takePersistableUriPermission for permanent folder access", 24),
                NativeApiItem("FileProvider Secure Sharing", "Scoped content:// URI generation with grant-read permissions", 24),
                NativeApiItem("BiometricPrompt Authentication", "Strong/Weak fingerprint, face, and device credential unlock", 28),
                NativeApiItem("AndroidKeyStore Hardware Cryptography", "Hardware-backed AES-256-GCM cryptographic key generation", 24),
                NativeApiItem("EncryptedSharedPreferences", "AES-256-SIV key and file encryption", 24),
                NativeApiItem("Device Hardware Specs Inspector", "Manufacturer, device model, hardware board, and build tags", 24),
                NativeApiItem("Comprehensive Diagnostic Test Runner", "Automated native subsystem validation and health check suite", 24)
            )
        )
    )

    /**
     * Flat list of all registered native APIs paired with their parent module.
     */
    fun getAllApis(): List<Pair<ModuleDescriptor, NativeApiItem>> {
        return modules.flatMap { module ->
            module.keyApis.map { api -> module to api }
        }
    }

    /**
     * Total number of cataloged native Android APIs across all 12 modules.
     */
    fun totalApiCount(): Int {
        return modules.sumOf { it.apiCount }
    }

    /**
     * Filter modules by high-level category.
     */
    fun findByCategory(category: FeatureCategory): List<ModuleDescriptor> {
        if (category == FeatureCategory.ALL) return modules
        return modules.filter { it.category == category }
    }

    /**
     * Search modules by keyword across name, subtitle, description, and individual API items.
     */
    fun search(query: String, category: FeatureCategory = FeatureCategory.ALL): List<ModuleDescriptor> {
        val baseList = findByCategory(category)
        if (query.isBlank()) return baseList

        val trimmed = query.trim()
        return baseList.filter { module ->
            module.name.contains(trimmed, ignoreCase = true) ||
            module.subtitle.contains(trimmed, ignoreCase = true) ||
            module.description.contains(trimmed, ignoreCase = true) ||
            module.keyApis.any { api ->
                api.name.contains(trimmed, ignoreCase = true) ||
                api.description.contains(trimmed, ignoreCase = true)
            }
        }
    }

    /**
     * Search specific individual native APIs across all modules.
     */
    fun searchApis(query: String): List<Pair<ModuleDescriptor, NativeApiItem>> {
        if (query.isBlank()) return getAllApis()
        val trimmed = query.trim()
        return getAllApis().filter { (module, api) ->
            api.name.contains(trimmed, ignoreCase = true) ||
            api.description.contains(trimmed, ignoreCase = true) ||
            module.name.contains(trimmed, ignoreCase = true)
        }
    }
}

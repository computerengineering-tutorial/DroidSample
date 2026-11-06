package com.example.droidsample.storage

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import java.security.KeyStore

enum class TestStatus {
    PASS,
    FAIL,
    BLOCKED,
    UNSUPPORTED
}

data class FeatureTestResult(
    val featureId: String,
    val apiName: String,
    val category: String,
    val minSdk: Int = 1,
    val maxSdk: Int? = null,
    val permissionRequired: String? = null,
    val roleRequired: String? = null,
    val deviceSupported: Boolean = true,
    val status: TestStatus,
    val outputData: String,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class TestSuiteReport(
    val results: List<FeatureTestResult>,
    val totalTests: Int,
    val passCount: Int,
    val failCount: Int,
    val blockedCount: Int,
    val unsupportedCount: Int,
    val passRatePercent: Double,
    val generatedAt: Long = System.currentTimeMillis(),
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
)

/**
 * Master API Diagnostics & Testing Runner.
 * Executes non-destructive checks against all major Android subsystems
 * and exports formatted JSON / Markdown test matrices.
 */
class TestSuiteRunner(private val context: Context) {

    fun runAllDiagnostics(): TestSuiteReport {
        val results = mutableListOf<FeatureTestResult>()

        results.addAll(testCameraSubsystem())
        results.addAll(testAudioSubsystem())
        results.addAll(testVideoCodecSubsystem())
        results.addAll(testTelephonySubsystem())
        results.addAll(testCallingSubsystem())
        results.addAll(testSmsSubsystem())
        results.addAll(testLocationSubsystem())
        results.addAll(testSensorSubsystem())
        results.addAll(testConnectivitySubsystem())
        results.addAll(testNetworkSubsystem())
        results.addAll(testSystemSubsystem())
        results.addAll(testStorageSubsystem())
        results.addAll(testMediaStoreSubsystem())
        results.addAll(testSecuritySubsystem())
        results.addAll(testDisplaySubsystem())

        val total = results.size
        val pass = results.count { it.status == TestStatus.PASS }
        val fail = results.count { it.status == TestStatus.FAIL }
        val blocked = results.count { it.status == TestStatus.BLOCKED }
        val unsupported = results.count { it.status == TestStatus.UNSUPPORTED }
        val rate = if (total > 0) (pass.toDouble() / total.toDouble()) * 100.0 else 0.0

        return TestSuiteReport(
            results = results,
            totalTests = total,
            passCount = pass,
            failCount = fail,
            blockedCount = blocked,
            unsupportedCount = unsupported,
            passRatePercent = rate
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun testCameraSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val pm = context.packageManager
        val hasCameraFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        if (!hasCameraFeature) {
            list.add(
                FeatureTestResult(
                    featureId = "CAM-01",
                    apiName = "CameraManager.cameraIdList",
                    category = "Camera",
                    deviceSupported = false,
                    status = TestStatus.UNSUPPORTED,
                    outputData = "Device does not report FEATURE_CAMERA_ANY"
                )
            )
            return list
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager != null) {
            try {
                val ids = cameraManager.cameraIdList
                list.add(
                    FeatureTestResult(
                        featureId = "CAM-01",
                        apiName = "CameraManager.cameraIdList",
                        category = "Camera",
                        status = TestStatus.PASS,
                        outputData = "Detected ${ids.size} camera(s): [${ids.joinToString()}]"
                    )
                )
            } catch (e: Exception) {
                list.add(
                    FeatureTestResult(
                        featureId = "CAM-01",
                        apiName = "CameraManager.cameraIdList",
                        category = "Camera",
                        status = TestStatus.FAIL,
                        outputData = "Exception enumerating cameras",
                        errorMessage = e.message
                    )
                )
            }
        }

        // Camera permission check
        val camPerm = hasPermission(Manifest.permission.CAMERA)
        list.add(
            FeatureTestResult(
                featureId = "CAM-02",
                apiName = "Camera Runtime Permission",
                category = "Camera",
                permissionRequired = Manifest.permission.CAMERA,
                status = if (camPerm) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (camPerm) "Permission GRANTED" else "Permission DENIED"
            )
        )

        return list
    }

    private fun testAudioSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (audioManager == null) {
            list.add(
                FeatureTestResult(
                    featureId = "AUD-01",
                    apiName = "AudioManager.mode",
                    category = "Audio",
                    status = TestStatus.FAIL,
                    outputData = "AudioManager service not found"
                )
            )
        } else {
            val modeStr = when (audioManager.mode) {
                AudioManager.MODE_NORMAL -> "MODE_NORMAL"
                AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
                AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
                AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
                else -> "MODE_${audioManager.mode}"
            }
            val isMusicActive = audioManager.isMusicActive
            list.add(
                FeatureTestResult(
                    featureId = "AUD-01",
                    apiName = "AudioManager.mode & playback",
                    category = "Audio",
                    status = TestStatus.PASS,
                    outputData = "Mode: $modeStr, MusicActive: $isMusicActive"
                )
            )
        }

        val micPerm = hasPermission(Manifest.permission.RECORD_AUDIO)
        list.add(
            FeatureTestResult(
                featureId = "AUD-02",
                apiName = "Record Audio Permission",
                category = "Audio",
                permissionRequired = Manifest.permission.RECORD_AUDIO,
                status = if (micPerm) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (micPerm) "Permission GRANTED" else "Permission DENIED"
            )
        )

        return list
    }

    private fun testVideoCodecSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val infos = codecList.codecInfos
            val numCodecs = infos.size
            val decoders = infos.count { !it.isEncoder }
            val encoders = infos.count { it.isEncoder }

            list.add(
                FeatureTestResult(
                    featureId = "VID-01",
                    apiName = "MediaCodecList",
                    category = "Video",
                    status = TestStatus.PASS,
                    outputData = "Found $numCodecs codecs ($decoders decoders, $encoders encoders)"
                )
            )
        } catch (e: Exception) {
            list.add(
                FeatureTestResult(
                    featureId = "VID-01",
                    apiName = "MediaCodecList",
                    category = "Video",
                    status = TestStatus.FAIL,
                    outputData = "Failed to query MediaCodecList",
                    errorMessage = e.message
                )
            )
        }
        return list
    }

    private fun testTelephonySubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (tm == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            list.add(
                FeatureTestResult(
                    featureId = "TEL-01",
                    apiName = "TelephonyManager",
                    category = "Telephony",
                    deviceSupported = false,
                    status = TestStatus.UNSUPPORTED,
                    outputData = "Telephony hardware not available on this device"
                )
            )
        } else {
            val simState = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "SIM_READY"
                TelephonyManager.SIM_STATE_ABSENT -> "SIM_ABSENT"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                else -> "SIM_STATE_${tm.simState}"
            }
            list.add(
                FeatureTestResult(
                    featureId = "TEL-01",
                    apiName = "TelephonyManager.simState",
                    category = "Telephony",
                    status = TestStatus.PASS,
                    outputData = "SIM State: $simState, NetworkCountryIso: ${tm.networkCountryIso}"
                )
            )
        }

        val phoneStatePerm = hasPermission(Manifest.permission.READ_PHONE_STATE)
        list.add(
            FeatureTestResult(
                featureId = "TEL-02",
                apiName = "READ_PHONE_STATE Permission",
                category = "Telephony",
                permissionRequired = Manifest.permission.READ_PHONE_STATE,
                status = if (phoneStatePerm) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (phoneStatePerm) "Permission GRANTED" else "Permission DENIED"
            )
        )

        return list
    }

    private fun testCallingSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        if (telecomManager == null) {
            list.add(
                FeatureTestResult(
                    featureId = "CALL-01",
                    apiName = "TelecomManager",
                    category = "Calling",
                    status = TestStatus.FAIL,
                    outputData = "TelecomManager not available"
                )
            )
        } else {
            val defaultDialer = telecomManager.defaultDialerPackage ?: "None"
            val isDefault = defaultDialer == context.packageName
            list.add(
                FeatureTestResult(
                    featureId = "CALL-01",
                    apiName = "TelecomManager.defaultDialerPackage",
                    category = "Calling",
                    status = TestStatus.PASS,
                    outputData = "Default Dialer: $defaultDialer (Is DroidSample: $isDefault)"
                )
            )
        }

        val callLogPerm = hasPermission(Manifest.permission.READ_CALL_LOG)
        list.add(
            FeatureTestResult(
                featureId = "CALL-02",
                apiName = "READ_CALL_LOG Permission",
                category = "Calling",
                permissionRequired = Manifest.permission.READ_CALL_LOG,
                status = if (callLogPerm) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (callLogPerm) "Permission GRANTED" else "Permission DENIED"
            )
        )

        return list
    }

    private fun testSmsSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        if (!hasTelephony) {
            list.add(
                FeatureTestResult(
                    featureId = "SMS-01",
                    apiName = "SmsManager",
                    category = "SMS",
                    deviceSupported = false,
                    status = TestStatus.UNSUPPORTED,
                    outputData = "Device does not support SMS hardware"
                )
            )
        } else {
            val sendSmsPerm = hasPermission(Manifest.permission.SEND_SMS)
            val readSmsPerm = hasPermission(Manifest.permission.READ_SMS)
            val canSms = sendSmsPerm && readSmsPerm
            list.add(
                FeatureTestResult(
                    featureId = "SMS-01",
                    apiName = "SmsManager Permissions",
                    category = "SMS",
                    permissionRequired = "${Manifest.permission.SEND_SMS}, ${Manifest.permission.READ_SMS}",
                    status = if (canSms) TestStatus.PASS else TestStatus.BLOCKED,
                    outputData = "SendSMS: $sendSmsPerm, ReadSMS: $readSmsPerm"
                )
            )
        }

        return list
    }

    private fun testLocationSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        if (lm == null) {
            list.add(
                FeatureTestResult(
                    featureId = "LOC-01",
                    apiName = "LocationManager",
                    category = "Location",
                    status = TestStatus.FAIL,
                    outputData = "LocationManager not available"
                )
            )
        } else {
            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val providers = lm.allProviders.joinToString()
            list.add(
                FeatureTestResult(
                    featureId = "LOC-01",
                    apiName = "LocationManager.allProviders",
                    category = "Location",
                    status = TestStatus.PASS,
                    outputData = "GPS: $gpsEnabled, Net: $netEnabled, Providers: [$providers]"
                )
            )
        }

        val fineLoc = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(
            FeatureTestResult(
                featureId = "LOC-02",
                apiName = "ACCESS_FINE_LOCATION Permission",
                category = "Location",
                permissionRequired = Manifest.permission.ACCESS_FINE_LOCATION,
                status = if (fineLoc) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (fineLoc) "Permission GRANTED" else "Permission DENIED"
            )
        )

        return list
    }

    private fun testSensorSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        if (sm == null) {
            list.add(
                FeatureTestResult(
                    featureId = "SEN-01",
                    apiName = "SensorManager",
                    category = "Sensors",
                    status = TestStatus.FAIL,
                    outputData = "SensorManager not found"
                )
            )
        } else {
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
            val baro = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
            val allSensors = sm.getSensorList(Sensor.TYPE_ALL).size

            list.add(
                FeatureTestResult(
                    featureId = "SEN-01",
                    apiName = "SensorManager Hardware Suite",
                    category = "Sensors",
                    status = TestStatus.PASS,
                    outputData = "Total sensors: $allSensors (Accel: $accel, Gyro: $gyro, Mag: $mag, Baro: $baro)"
                )
            )
        }
        return list
    }

    private fun testConnectivitySubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()

        // Bluetooth
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = bluetoothManager?.adapter
        val hasBt = btAdapter != null
        list.add(
            FeatureTestResult(
                featureId = "CON-01",
                apiName = "BluetoothAdapter",
                category = "Connectivity",
                deviceSupported = hasBt,
                status = if (hasBt) TestStatus.PASS else TestStatus.UNSUPPORTED,
                outputData = if (hasBt) "Bluetooth Supported, Enabled: ${btAdapter.isEnabled}" else "No Bluetooth adapter"
            )
        )

        // NFC
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val hasNfc = nfcAdapter != null
        list.add(
            FeatureTestResult(
                featureId = "CON-02",
                apiName = "NfcAdapter",
                category = "Connectivity",
                deviceSupported = hasNfc,
                status = if (hasNfc) TestStatus.PASS else TestStatus.UNSUPPORTED,
                outputData = if (hasNfc) "NFC Supported, Enabled: ${nfcAdapter.isEnabled}" else "No NFC hardware"
            )
        )

        // Wi-Fi
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val hasWifi = wifi != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        list.add(
            FeatureTestResult(
                featureId = "CON-03",
                apiName = "WifiManager",
                category = "Connectivity",
                deviceSupported = hasWifi,
                status = if (hasWifi) TestStatus.PASS else TestStatus.UNSUPPORTED,
                outputData = if (hasWifi) "Wi-Fi Supported, Enabled: ${wifi.isWifiEnabled}" else "No Wi-Fi feature"
            )
        )

        return list
    }

    private fun testNetworkSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (cm == null) {
            list.add(
                FeatureTestResult(
                    featureId = "NET-01",
                    apiName = "ConnectivityManager",
                    category = "Network",
                    status = TestStatus.FAIL,
                    outputData = "ConnectivityManager unavailable"
                )
            )
        } else {
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false

            val typeDesc = when {
                isWifi -> "Wi-Fi"
                isCell -> "Cellular"
                else -> "Other / Disconnected"
            }

            list.add(
                FeatureTestResult(
                    featureId = "NET-01",
                    apiName = "ConnectivityManager.getNetworkCapabilities",
                    category = "Network",
                    status = if (hasInternet) TestStatus.PASS else TestStatus.BLOCKED,
                    outputData = "Type: $typeDesc, HasInternet: $hasInternet"
                )
            )
        }
        return list
    }

    private fun testSystemSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()

        // PowerManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null) {
            val isScreenOn = pm.isInteractive
            val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
            list.add(
                FeatureTestResult(
                    featureId = "SYS-01",
                    apiName = "PowerManager",
                    category = "System",
                    status = TestStatus.PASS,
                    outputData = "ScreenInteractive: $isScreenOn, IgnoreBatteryOpt: $isIgnoringBattery"
                )
            )
        }

        // Battery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        list.add(
            FeatureTestResult(
                featureId = "SYS-02",
                apiName = "BatteryManager.capacity",
                category = "System",
                status = TestStatus.PASS,
                outputData = "Battery Level: $capacity%"
            )
        )

        return list
    }

    private fun testStorageSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        try {
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir
            val extFiles = context.getExternalFilesDir(null)

            list.add(
                FeatureTestResult(
                    featureId = "STO-01",
                    apiName = "Internal & App-Specific Storage Directories",
                    category = "Storage",
                    status = TestStatus.PASS,
                    outputData = "filesDir: ${filesDir.exists()}, cacheDir: ${cacheDir.exists()}, extFiles: ${extFiles?.exists()}"
                )
            )

            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes / (1024L * 1024L * 1024L)
            list.add(
                FeatureTestResult(
                    featureId = "STO-02",
                    apiName = "StatFs Capacity Query",
                    category = "Storage",
                    status = TestStatus.PASS,
                    outputData = "Data partition available: $freeGb GB"
                )
            )
        } catch (e: Exception) {
            list.add(
                FeatureTestResult(
                    featureId = "STO-01",
                    apiName = "Storage Subsystem",
                    category = "Storage",
                    status = TestStatus.FAIL,
                    outputData = "Failed to inspect storage",
                    errorMessage = e.message
                )
            )
        }
        return list
    }

    private fun testMediaStoreSubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val hasMediaImagesPerm = if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        list.add(
            FeatureTestResult(
                featureId = "MED-01",
                apiName = "MediaStore Images Access",
                category = "MediaStore",
                permissionRequired = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
                status = if (hasMediaImagesPerm) TestStatus.PASS else TestStatus.BLOCKED,
                outputData = if (hasMediaImagesPerm) "Media image permission GRANTED" else "Media image permission DENIED"
            )
        )

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        list.add(
            FeatureTestResult(
                featureId = "MED-02",
                apiName = "MediaStore.Images Uri Resolution",
                category = "MediaStore",
                status = TestStatus.PASS,
                outputData = "Uri: $uri"
            )
        )

        return list
    }

    private fun testSecuritySubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()

        // Biometrics
        val biometricManager = BiometricManager.from(context)
        val code = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val bioStatus = if (code == BiometricManager.BIOMETRIC_SUCCESS) {
            TestStatus.PASS
        } else if (code == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            TestStatus.UNSUPPORTED
        } else {
            TestStatus.BLOCKED
        }

        list.add(
            FeatureTestResult(
                featureId = "SEC-01",
                apiName = "BiometricManager.canAuthenticate",
                category = "Security",
                status = bioStatus,
                outputData = "Result code: $code"
            )
        )

        // KeyStore
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            list.add(
                FeatureTestResult(
                    featureId = "SEC-02",
                    apiName = "KeyStore ('AndroidKeyStore')",
                    category = "Security",
                    status = TestStatus.PASS,
                    outputData = "AndroidKeyStore loaded successfully (${ks.size()} keys)"
                )
            )
        } catch (e: Exception) {
            list.add(
                FeatureTestResult(
                    featureId = "SEC-02",
                    apiName = "KeyStore ('AndroidKeyStore')",
                    category = "Security",
                    status = TestStatus.FAIL,
                    outputData = "KeyStore init failed",
                    errorMessage = e.message
                )
            )
        }

        return list
    }

    private fun testDisplaySubsystem(): List<FeatureTestResult> {
        val list = mutableListOf<FeatureTestResult>()
        val metrics = context.resources.displayMetrics
        list.add(
            FeatureTestResult(
                featureId = "DISP-01",
                apiName = "DisplayMetrics",
                category = "Display",
                status = TestStatus.PASS,
                outputData = "${metrics.widthPixels}x${metrics.heightPixels} px, Density: ${metrics.densityDpi} DPI"
            )
        )
        return list
    }

    companion object {
        /**
         * Serializes a TestSuiteReport to clean formatted JSON.
         */
        fun exportToJson(report: TestSuiteReport): String {
            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"deviceModel\": \"${escapeJson(report.deviceModel)}\",\n")
            sb.append("  \"androidVersion\": \"${escapeJson(report.androidVersion)}\",\n")
            sb.append("  \"generatedAt\": ${report.generatedAt},\n")
            sb.append("  \"totalTests\": ${report.totalTests},\n")
            sb.append("  \"passCount\": ${report.passCount},\n")
            sb.append("  \"failCount\": ${report.failCount},\n")
            sb.append("  \"blockedCount\": ${report.blockedCount},\n")
            sb.append("  \"unsupportedCount\": ${report.unsupportedCount},\n")
            sb.append("  \"passRatePercent\": ${String.format(java.util.Locale.US, "%.2f", report.passRatePercent)},\n")
            sb.append("  \"results\": [\n")

            report.results.forEachIndexed { index, r ->
                sb.append("    {\n")
                sb.append("      \"featureId\": \"${escapeJson(r.featureId)}\",\n")
                sb.append("      \"apiName\": \"${escapeJson(r.apiName)}\",\n")
                sb.append("      \"category\": \"${escapeJson(r.category)}\",\n")
                sb.append("      \"minSdk\": ${r.minSdk},\n")
                sb.append("      \"maxSdk\": ${r.maxSdk ?: "null"},\n")
                sb.append("      \"permissionRequired\": ${if (r.permissionRequired != null) "\"${escapeJson(r.permissionRequired)}\"" else "null"},\n")
                sb.append("      \"roleRequired\": ${if (r.roleRequired != null) "\"${escapeJson(r.roleRequired)}\"" else "null"},\n")
                sb.append("      \"deviceSupported\": ${r.deviceSupported},\n")
                sb.append("      \"status\": \"${r.status.name}\",\n")
                sb.append("      \"outputData\": \"${escapeJson(r.outputData)}\",\n")
                sb.append("      \"errorMessage\": ${if (r.errorMessage != null) "\"${escapeJson(r.errorMessage)}\"" else "null"},\n")
                sb.append("      \"timestamp\": ${r.timestamp}\n")
                sb.append("    }${if (index < report.results.size - 1) "," else ""}\n")
            }

            sb.append("  ]\n")
            sb.append("}")
            return sb.toString()
        }

        /**
         * Generates a GitHub-flavored Markdown diagnostic report table.
         */
        fun exportToMarkdown(report: TestSuiteReport): String {
            val sb = StringBuilder()
            sb.append("# DroidSample Master API Diagnostic Report\n\n")
            sb.append("- **Device**: ${report.deviceModel}\n")
            sb.append("- **OS Version**: ${report.androidVersion}\n")
            sb.append("- **Generated At**: ${java.util.Date(report.generatedAt)}\n")
            sb.append("- **Total Tests**: ${report.totalTests}\n")
            sb.append("- **Passed**: ${report.passCount} | **Failed**: ${report.failCount} | **Blocked**: ${report.blockedCount} | **Unsupported**: ${report.unsupportedCount}\n")
            sb.append("- **Pass Rate**: ${String.format(java.util.Locale.US, "%.1f", report.passRatePercent)}%\n\n")

            sb.append("## Diagnostic Matrix\n\n")
            sb.append("| ID | Category | API / Feature | Status | Details |\n")
            sb.append("|:---|:---|:---|:---|:---|\n")

            for (r in report.results) {
                val statusBadge = when (r.status) {
                    TestStatus.PASS -> "✅ PASS"
                    TestStatus.FAIL -> "❌ FAIL"
                    TestStatus.BLOCKED -> "⚠️ BLOCKED"
                    TestStatus.UNSUPPORTED -> "⚪ N/A"
                }
                val details = if (r.errorMessage != null) "${r.outputData} (Error: ${r.errorMessage})" else r.outputData
                sb.append("| `${r.featureId}` | ${r.category} | ${r.apiName} | $statusBadge | ${details.replace("|", "\\|")} |\n")
            }

            return sb.toString()
        }

        private fun escapeJson(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }
}

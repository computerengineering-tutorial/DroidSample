package com.example.droidsample.storage

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager

data class BuildInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val product: String,
    val board: String,
    val hardware: String,
    val fingerprint: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String
)

data class CpuInfo(
    val supportedAbis: List<String>,
    val coreCount: Int,
    val architecture: String
)

data class RamInfo(
    val totalMemBytes: Long,
    val availableMemBytes: Long,
    val usedMemBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val usedPercentage: Double
)

data class StorageCapacityInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val freePercentage: Double,
    val usedPercentage: Double
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val refreshRateHz: Float,
    val isHdrSupported: Boolean,
    val hdrCapabilities: List<String>
)

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val health: String,
    val temperatureCelsius: Float,
    val voltageMv: Int
)

data class DeviceSpecSheet(
    val buildInfo: BuildInfo,
    val cpuInfo: CpuInfo,
    val ramInfo: RamInfo,
    val storageInfo: StorageCapacityInfo,
    val displayInfo: DisplayInfo,
    val batteryInfo: BatteryInfo
)

/**
 * Provider class that gathers comprehensive hardware, system, and environment specifications.
 */
class DeviceInfoProvider(private val context: Context) {

    /**
     * Collects and returns the full device specification sheet.
     */
    fun getDeviceSpecSheet(): DeviceSpecSheet {
        return DeviceSpecSheet(
            buildInfo = getBuildInfo(),
            cpuInfo = getCpuInfo(),
            ramInfo = getRamInfo(),
            storageInfo = getStorageInfo(),
            displayInfo = getDisplayInfo(),
            batteryInfo = getBatteryInfo()
        )
    }

    fun getBuildInfo(): BuildInfo {
        return BuildInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            product = Build.PRODUCT,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            fingerprint = Build.FINGERPRINT,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else {
                "N/A"
            }
        )
    }

    fun getCpuInfo(): CpuInfo {
        val abis = Build.SUPPORTED_ABIS.toList()
        val cores = Runtime.getRuntime().availableProcessors()
        val arch = System.getProperty("os.arch") ?: (abis.firstOrNull() ?: "Unknown")
        return CpuInfo(
            supportedAbis = abis,
            coreCount = cores,
            architecture = arch
        )
    }

    fun getRamInfo(): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(memInfo)
        }
        val total = memInfo.totalMem
        val avail = memInfo.availMem
        val used = (total - avail).coerceAtLeast(0L)
        val usedPercent = if (total > 0) (used.toDouble() / total.toDouble()) * 100.0 else 0.0

        return RamInfo(
            totalMemBytes = total,
            availableMemBytes = avail,
            usedMemBytes = used,
            thresholdBytes = memInfo.threshold,
            isLowMemory = memInfo.lowMemory,
            usedPercentage = usedPercent
        )
    }

    fun getStorageInfo(): StorageCapacityInfo {
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val used = (total - avail).coerceAtLeast(0L)
        val freePct = if (total > 0) (avail.toDouble() / total.toDouble()) * 100.0 else 0.0
        val usedPct = if (total > 0) (used.toDouble() / total.toDouble()) * 100.0 else 0.0

        return StorageCapacityInfo(
            totalBytes = total,
            availableBytes = avail,
            usedBytes = used,
            freePercentage = freePct,
            usedPercentage = usedPct
        )
    }

    @Suppress("DEPRECATION")
    fun getDisplayInfo(): DisplayInfo {
        val metrics = Resources.getSystem().displayMetrics
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        var refreshRate = 60.0f
        var isHdr = false
        val hdrCaps = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display
            refreshRate = display.refreshRate
            @Suppress("DEPRECATION")
            val caps = display.hdrCapabilities
            if (caps != null) {
                @Suppress("DEPRECATION")
                val supportedTypes = caps.supportedHdrTypes
                if (supportedTypes.isNotEmpty()) {
                    isHdr = true
                    for (type in supportedTypes) {
                        hdrCaps.add(hdrTypeToString(type))
                    }
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            @Suppress("DEPRECATION")
            val defaultDisplay = wm?.defaultDisplay
            if (defaultDisplay != null) {
                refreshRate = defaultDisplay.refreshRate
                val caps = defaultDisplay.hdrCapabilities
                if (caps != null && caps.supportedHdrTypes.isNotEmpty()) {
                    isHdr = true
                    for (type in caps.supportedHdrTypes) {
                        hdrCaps.add(hdrTypeToString(type))
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            refreshRate = wm?.defaultDisplay?.refreshRate ?: 60.0f
        }

        return DisplayInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            densityScale = metrics.density,
            refreshRateHz = refreshRate,
            isHdrSupported = isHdr,
            hdrCapabilities = hdrCaps
        )
    }

    fun getBatteryInfo(): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val source = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "Unplugged"
        }

        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatteryInfo(
            percentage = pct,
            isCharging = isCharging,
            chargingSource = source,
            health = health,
            temperatureCelsius = temp,
            voltageMv = voltage
        )
    }

    private fun hdrTypeToString(type: Int): String {
        return when (type) {
            1 -> "HDR10"
            2 -> "HDR_HLG"
            3 -> "HDR10_PLUS"
            4 -> "DOLBY_VISION"
            else -> "HDR_TYPE_$type"
        }
    }

    companion object {
        fun formatBytesToGb(bytes: Long): String {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return String.format("%.2f GB", gb)
        }

        fun formatBytesToMb(bytes: Long): String {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
    }
}

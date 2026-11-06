package com.example.droidsample.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Detailed snapshot of current device battery and power states.
 */
data class BatterySnapshot(
    val level: Int = -1,
    val scale: Int = 100,
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val pluggedSource: String = "Unplugged",
    val health: String = "Unknown",
    val temperatureCelsius: Double = 0.0,
    val voltageMv: Int = 0,
    val technology: String = "Li-ion",
    val present: Boolean = true,
    val isPowerSaveMode: Boolean = false,
    val isDeviceIdleMode: Boolean = false,
    val capacityPercent: Int = 0,
    val currentNowUa: Long = 0L,
    val currentAverageUa: Long = 0L
)

/**
 * Helper manager interfacing with Android's BatteryManager and PowerManager.
 */
object PowerBatteryManager {

    /**
     * Pure function to calculate battery percentage.
     */
    fun calculatePercentage(level: Int, scale: Int): Int {
        if (scale <= 0 || level < 0) return 0
        return ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    /**
     * Decodes temperature reported in tenths of a degree Celsius.
     */
    fun convertTemperature(rawTenths: Int): Double {
        return rawTenths / 10.0
    }

    /**
     * Converts battery health integer code to human readable description.
     */
    fun parseHealth(healthCode: Int): String {
        return when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }

    /**
     * Converts plugged status integer code to power source name.
     */
    fun parsePlugged(pluggedCode: Int): String {
        return when (pluggedCode) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            0 -> "Unplugged (Battery)"
            else -> "Other ($pluggedCode)"
        }
    }

    /**
     * Retrieves instantaneous battery snapshot using sticky Intent.ACTION_BATTERY_CHANGED broadcast.
     */
    fun getBatterySnapshot(context: Context): BatterySnapshot {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        return parseBatteryIntent(context, batteryIntent)
    }

    /**
     * Returns a real-time reactive Flow of [BatterySnapshot].
     */
    fun observeBattery(context: Context): Flow<BatterySnapshot> = callbackFlow {
        // Emit initial snapshot
        trySend(getBatterySnapshot(context))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(parseBatteryIntent(context, intent))
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver might already be unregistered
            }
        }
    }

    /**
     * Decodes Intent.ACTION_BATTERY_CHANGED intent into [BatterySnapshot].
     */
    fun parseBatteryIntent(context: Context, intent: Intent?): BatterySnapshot {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = calculatePercentage(level, scale)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val pluggedCode = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedSource = parsePlugged(pluggedCode)

        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = parseHealth(healthCode)

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperature = convertTemperature(rawTemp)

        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true

        // PowerManager info
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
        val isDeviceIdleMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isDeviceIdleMode ?: false
        } else false

        // BatteryManager system service properties
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: percentage
        val currentNow = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L
        val currentAvg = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0L

        return BatterySnapshot(
            level = level,
            scale = scale,
            percentage = percentage,
            isCharging = isCharging,
            pluggedSource = pluggedSource,
            health = health,
            temperatureCelsius = temperature,
            voltageMv = voltage,
            technology = technology,
            present = present,
            isPowerSaveMode = isPowerSaveMode,
            isDeviceIdleMode = isDeviceIdleMode,
            capacityPercent = capacity,
            currentNowUa = currentNow,
            currentAverageUa = currentAvg
        )
    }
}

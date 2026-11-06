package com.example.droidsample.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Event categories for filtering and organizing system broadcasts.
 */
enum class EventCategory {
    BATTERY,
    POWER,
    SCREEN,
    TIME,
    CONNECTIVITY,
    PACKAGE,
    SYSTEM,
    ALARM
}

/**
 * Encapsulates a recorded system broadcast event.
 */
data class SystemBroadcastEvent(
    val id: Long,
    val timestamp: Long,
    val action: String,
    val summary: String,
    val category: EventCategory,
    val extras: Map<String, String> = emptyMap()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

/**
 * SystemBroadcastReceiver handles both manifest-registered broadcasts and
 * dynamic runtime broadcasts (such as ACTION_BATTERY_CHANGED and ACTION_SCREEN_ON).
 * It feeds all recorded events into an observable [eventFlow].
 */
class SystemBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action == null) return
        processIntent(intent)
    }

    companion object {
        private val idCounter = AtomicLong(1)
        private val _events = MutableStateFlow<List<SystemBroadcastEvent>>(emptyList())
        val eventFlow: StateFlow<List<SystemBroadcastEvent>> = _events.asStateFlow()

        private var dynamicReceiver: SystemBroadcastReceiver? = null
        private var isRegistered = false

        /**
         * Clears all recorded events from the log.
         */
        fun clearEvents() {
            _events.value = emptyList()
        }

        /**
         * Add an event to the state flow (retains last 200 events).
         */
        fun recordEvent(event: SystemBroadcastEvent) {
            val current = _events.value
            val updated = (listOf(event) + current).take(200)
            _events.value = updated
        }

        /**
         * Process and extract data from any broadcast Intent.
         */
        fun processIntent(intent: Intent) {
            val action = intent.action ?: return
            val category = categorizeAction(action)
            val extrasMap = mutableMapOf<String, String>()

            @Suppress("DEPRECATION")
            intent.extras?.keySet()?.forEach { key ->
                val value = intent.extras?.get(key)
                extrasMap[key] = value?.toString() ?: "null"
            }

            val summary = generateSummary(action, intent, extrasMap)

            val event = SystemBroadcastEvent(
                id = idCounter.getAndIncrement(),
                timestamp = System.currentTimeMillis(),
                action = action,
                summary = summary,
                category = category,
                extras = extrasMap
            )

            recordEvent(event)
        }

        private fun categorizeAction(action: String): EventCategory {
            return when {
                action == Intent.ACTION_BATTERY_CHANGED || action == Intent.ACTION_BATTERY_LOW || action == Intent.ACTION_BATTERY_OKAY -> EventCategory.BATTERY
                action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED -> EventCategory.POWER
                action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_USER_PRESENT -> EventCategory.SCREEN
                action == Intent.ACTION_TIME_TICK || action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED -> EventCategory.TIME
                action == Intent.ACTION_AIRPLANE_MODE_CHANGED -> EventCategory.CONNECTIVITY
                action.startsWith("android.intent.action.PACKAGE_") -> EventCategory.PACKAGE
                action.contains("ALARM") -> EventCategory.ALARM
                else -> EventCategory.SYSTEM
            }
        }

        private fun generateSummary(action: String, intent: Intent, extras: Map<String, String>): String {
            return when (action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
                    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                        BatteryManager.BATTERY_STATUS_FULL -> "Full"
                        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                        else -> "Unknown"
                    }
                    "Battery $percent% | $status | ${temp}°C | ${voltage}mV"
                }
                Intent.ACTION_POWER_CONNECTED -> "External power supply connected"
                Intent.ACTION_POWER_DISCONNECTED -> "External power supply disconnected"
                Intent.ACTION_SCREEN_ON -> "Screen powered on (interactive)"
                Intent.ACTION_SCREEN_OFF -> "Screen powered off (sleep/lock)"
                Intent.ACTION_USER_PRESENT -> "User unlocked lockscreen"
                Intent.ACTION_TIME_TICK -> "Minute clock tick update"
                Intent.ACTION_TIME_CHANGED -> "System clock time was adjusted"
                Intent.ACTION_TIMEZONE_CHANGED -> "Timezone adjusted: ${extras["time-zone"] ?: "Unknown"}"
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val state = extras["state"] ?: "unknown"
                    "Airplane mode toggled (state: $state)"
                }
                Intent.ACTION_BOOT_COMPLETED -> "Device system boot cycle completed"
                Intent.ACTION_LOCALE_CHANGED -> "Device display locale changed"
                AlarmManagerHelper.ACTION_ALARM_TRIGGERED -> "Alarm trigger event received"
                NotificationHelper.ACTION_ACKNOWLEDGE -> "Notification action acknowledged"
                else -> "Broadcast intent received: $action"
            }
        }

        /**
         * Register dynamic broadcast receiver for actions that require programmatic registration
         * (such as ACTION_BATTERY_CHANGED, ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_TIME_TICK).
         */
        @Synchronized
        fun registerDynamic(context: Context) {
            if (isRegistered) return

            val receiver = SystemBroadcastReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(Intent.ACTION_LOCALE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(AlarmManagerHelper.ACTION_ALARM_TRIGGERED)
                addAction(NotificationHelper.ACTION_ACKNOWLEDGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(receiver, filter)
            }

            dynamicReceiver = receiver
            isRegistered = true
        }

        /**
         * Unregister dynamic broadcast receiver.
         */
        @Synchronized
        fun unregisterDynamic(context: Context) {
            if (!isRegistered || dynamicReceiver == null) return
            try {
                context.applicationContext.unregisterReceiver(dynamicReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                dynamicReceiver = null
                isRegistered = false
            }
        }

        fun isDynamicallyRegistered(): Boolean = isRegistered
    }
}

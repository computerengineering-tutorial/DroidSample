package com.example.droidsample.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Handles scheduling of exact and inexact alarms via AlarmManager.
 * Complies with Android 12+ (API 31+) SCHEDULE_EXACT_ALARM restrictions.
 */
object AlarmManagerHelper {

    const val ACTION_ALARM_TRIGGERED = "com.example.droidsample.action.ALARM_TRIGGERED"
    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"

    /**
     * Checks if the app has permission to schedule exact alarms (Android 12+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * Intent to open system settings for exact alarm permission.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun createRequestExactAlarmPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Schedules an exact alarm that fires even in Doze / Idle mode.
     */
    fun scheduleExactAlarm(
        context: Context,
        triggerAfterSeconds: Long,
        alarmId: Int = 101,
        label: String = "Exact Alarm Test"
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val triggerAtMillis = System.currentTimeMillis() + (triggerAfterSeconds * 1000)
        val pendingIntent = createAlarmPendingIntent(context, alarmId, label)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            return true
        } catch (e: SecurityException) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Schedules an inexact battery-friendly alarm.
     */
    fun scheduleInexactAlarm(
        context: Context,
        triggerAfterSeconds: Long,
        alarmId: Int = 102,
        label: String = "Inexact Alarm Test"
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = SystemClock.elapsedRealtime() + (triggerAfterSeconds * 1000)
        val pendingIntent = createAlarmPendingIntent(context, alarmId, label)

        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    /**
     * Cancels an existing scheduled alarm.
     */
    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = createAlarmPendingIntent(context, alarmId, "cancel")
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createAlarmPendingIntent(context: Context, alarmId: Int, label: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGERED
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * BroadcastReceiver triggered by AlarmManager.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getIntExtra(AlarmManagerHelper.EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(AlarmManagerHelper.EXTRA_ALARM_LABEL) ?: "Scheduled Alarm"

        // Log to SystemBroadcastReceiver
        SystemBroadcastReceiver.recordEvent(
            SystemBroadcastEvent(
                id = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis(),
                action = AlarmManagerHelper.ACTION_ALARM_TRIGGERED,
                summary = "Alarm #$alarmId triggered: $label",
                category = EventCategory.ALARM,
                extras = mapOf("alarmId" to alarmId.toString(), "label" to label)
            )
        )

        // Show a high priority notification
        NotificationHelper.postStandardNotification(
            context = context,
            title = "Alarm Alert: $label",
            message = "Scheduled alarm #$alarmId fired at ${System.currentTimeMillis()}",
            channelId = NotificationHelper.CHANNEL_ID_HIGH
        )
    }
}

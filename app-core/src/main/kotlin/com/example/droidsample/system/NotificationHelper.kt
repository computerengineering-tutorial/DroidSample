package com.example.droidsample.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.droidsample.MainActivity

/**
 * Helper class providing exhaustive notification management:
 * - NotificationChannel & NotificationChannelGroup creation across importance levels
 * - Styles: Standard, BigTextStyle, BigPictureStyle, InboxStyle, MessagingStyle
 * - Progress tracking notifications (determinate & indeterminate)
 * - Notification grouping & group summary
 * - Interactive action buttons
 * - Dynamic cancellation and status verification
 */
object NotificationHelper {

    // Notification Channel IDs
    const val CHANNEL_ID_HIGH = "channel_system_high"
    const val CHANNEL_ID_DEFAULT = "channel_system_default"
    const val CHANNEL_ID_LOW = "channel_system_low"
    const val CHANNEL_ID_MIN = "channel_system_min"
    const val CHANNEL_ID_SERVICE = "channel_system_service"
    const val CHANNEL_ID_PROGRESS = "channel_system_progress"

    // Channel Group IDs
    const val GROUP_ID_GENERAL = "group_system_general"
    const val GROUP_ID_SYSTEM = "group_system_services"

    // Notification Group Keys
    const val NOTIFICATION_GROUP_KEY = "com.example.droidsample.NOTIFICATION_GROUP"

    // Action Intents
    const val ACTION_DISMISS = "com.example.droidsample.ACTION_DISMISS_NOTIFICATION"
    const val ACTION_ACKNOWLEDGE = "com.example.droidsample.ACTION_ACKNOWLEDGE_NOTIFICATION"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    // Static Notification IDs
    const val NOTIFICATION_ID_STANDARD = 1001
    const val NOTIFICATION_ID_BIG_TEXT = 1002
    const val NOTIFICATION_ID_BIG_PICTURE = 1003
    const val NOTIFICATION_ID_INBOX = 1004
    const val NOTIFICATION_ID_MESSAGING = 1005
    const val NOTIFICATION_ID_PROGRESS = 1006
    const val NOTIFICATION_ID_GROUP_SUMMARY = 1099

    /**
     * Creates and registers all required Notification Channels and Groups.
     * Safe to call multiple times as OS handles updates idempotently.
     */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Channel Groups
            val generalGroup = NotificationChannelGroup(GROUP_ID_GENERAL, "General Notifications")
            val systemGroup = NotificationChannelGroup(GROUP_ID_SYSTEM, "System & Background Services")
            notificationManager.createNotificationChannelGroups(listOf(generalGroup, systemGroup))

            // 2. High Importance Channel (Heads-up banner, sound, vibration)
            val highChannel = NotificationChannel(
                CHANNEL_ID_HIGH,
                "High Priority Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical and urgent notifications with heads-up display"
                group = GROUP_ID_GENERAL
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            // 3. Default Importance Channel
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "Default Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard system updates and status notifications"
                group = GROUP_ID_GENERAL
                enableLights(true)
                lightColor = Color.BLUE
            }

            // 4. Low Importance Channel (Quiet, no sound)
            val lowChannel = NotificationChannel(
                CHANNEL_ID_LOW,
                "Low Priority Info",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent notifications displayed in shade without sound"
                group = GROUP_ID_GENERAL
            }

            // 5. Min Importance Channel (Collapsed shade only)
            val minChannel = NotificationChannel(
                CHANNEL_ID_MIN,
                "Minimum Priority",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Unobtrusive notifications with minimal footprint"
                group = GROUP_ID_GENERAL
            }

            // 6. Foreground Service Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Active Foreground Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent indicators for running foreground tasks"
                group = GROUP_ID_SYSTEM
                setShowBadge(false)
            }

            // 7. Progress Channel
            val progressChannel = NotificationChannel(
                CHANNEL_ID_PROGRESS,
                "Task Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dynamic progress bar updates for asynchronous jobs"
                group = GROUP_ID_SYSTEM
            }

            notificationManager.createNotificationChannels(
                listOf(highChannel, defaultChannel, lowChannel, minChannel, serviceChannel, progressChannel)
            )
        }
    }

    /**
     * Check if notifications are enabled for the application.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Check if a specific channel is enabled and not blocked.
     */
    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val channel = manager.getNotificationChannel(channelId) ?: return false
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return areNotificationsEnabled(context)
    }

    /**
     * Standard notification with action buttons and content pending intent.
     */
    fun postStandardNotification(
        context: Context,
        title: String = "System Alert",
        message: String = "Standard priority notification triggered from lab.",
        channelId: String = CHANNEL_ID_DEFAULT
    ) {
        val clickIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_STANDARD,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_STANDARD,
            Intent(ACTION_ACKNOWLEDGE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_STANDARD)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(clickIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_send, "Acknowledge", actionIntent)

        notifySafe(context, NOTIFICATION_ID_STANDARD, builder.build())
    }

    /**
     * BigTextStyle notification: expandable for lengthy messages.
     */
    fun postBigTextNotification(
        context: Context,
        title: String = "Security Audit Report",
        summary: String = "Daily System Summary",
        bigText: String = """
            Android System Diagnostics:
            - Battery Health: Normal (31.2°C, 4120mV)
            - Background Services: 2 active workers
            - Network Constraints: Unmetered Wi-Fi verified
            - Storage Status: 48.2 GB free space remaining
            - Security Policy: All system broadcast receivers online.
        """.trimIndent()
    ) {
        val style = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .setSummaryText(summary)
            .bigText(bigText)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText("Tap to view full system audit details.")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notifySafe(context, NOTIFICATION_ID_BIG_TEXT, builder.build())
    }

    /**
     * BigPictureStyle notification: expandable picture preview.
     */
    fun postBigPictureNotification(
        context: Context,
        title: String = "Graphic Snapshot Captured",
        caption: String = "System Performance Matrix"
    ) {
        val testBitmap = createSampleBitmap(400, 200, Color.rgb(30, 136, 229), "SYSTEM METRICS")

        val style = NotificationCompat.BigPictureStyle()
            .bigPicture(testBitmap)
            .bigLargeIcon(null as Bitmap?)
            .setBigContentTitle(title)
            .setSummaryText(caption)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setLargeIcon(testBitmap)
            .setContentTitle(title)
            .setContentText(caption)
            .setStyle(style)
            .setAutoCancel(true)

        notifySafe(context, NOTIFICATION_ID_BIG_PICTURE, builder.build())
    }

    /**
     * InboxStyle notification: multi-item list representation.
     */
    fun postInboxNotification(
        context: Context,
        title: String = "System Event Digest",
        items: List<String> = listOf(
            "14:40 - Power Connected (AC Rapid)",
            "14:41 - Screen Turned On",
            "14:42 - Wi-Fi Link Established",
            "14:43 - JobScheduler Batch Completed"
        )
    ) {
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("${items.size} events captured")

        items.forEach { style.addLine(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText("${items.size} new system updates")
            .setStyle(style)
            .setAutoCancel(true)

        notifySafe(context, NOTIFICATION_ID_INBOX, builder.build())
    }

    /**
     * MessagingStyle notification simulator: conversation thread with avatars and timestamps.
     */
    fun postMessagingNotification(
        context: Context,
        conversationTitle: String = "System Diagnostics Room"
    ) {
        val user = Person.Builder()
            .setName("Current Operator")
            .setKey("user_operator")
            .build()

        val daemon = Person.Builder()
            .setName("Daemon Worker")
            .setKey("service_worker")
            .build()

        val now = System.currentTimeMillis()
        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .setConversationTitle(conversationTitle)
            .setGroupConversation(true)
            .addMessage("Background data sync started.", now - 60000, daemon)
            .addMessage("Sync completed without errors.", now - 30000, daemon)
            .addMessage("Status received. Acknowledged.", now - 5000, user)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(messagingStyle)
            .setAutoCancel(true)

        notifySafe(context, NOTIFICATION_ID_MESSAGING, builder.build())
    }

    /**
     * Dynamic Progress Notification: updates determinate or indeterminate progress.
     */
    fun updateProgressNotification(
        context: Context,
        progress: Int,
        max: Int = 100,
        indeterminate: Boolean = false,
        title: String = "Syncing System Data",
        content: String = if (indeterminate) "Calculating hashes..." else "$progress% completed"
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!indeterminate && progress < max)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, indeterminate)

        notifySafe(context, NOTIFICATION_ID_PROGRESS, builder.build())
    }

    /**
     * Grouped Notifications: sends multiple child notifications bundled under a summary card.
     */
    fun postGroupedNotifications(
        context: Context,
        childEvents: List<Pair<Int, String>> = listOf(
            2001 to "Battery level updated to 85%",
            2002 to "Storage benchmark finished in 240ms",
            2003 to "Alarm exact trigger scheduled"
        )
    ) {
        // Post child notifications
        for ((childId, text) in childEvents) {
            val childBuilder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Event #$childId")
                .setContentText(text)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setAutoCancel(true)

            notifySafe(context, childId, childBuilder.build())
        }

        // Summary notification
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Grouped System Events")
            .setContentText("${childEvents.size} system updates")
            .setGroup(NOTIFICATION_GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)

        notifySafe(context, NOTIFICATION_ID_GROUP_SUMMARY, summaryBuilder.build())
    }

    /**
     * Cancel a specific notification.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Cancel all active notifications.
     */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /**
     * Create persistent notification for Foreground Services.
     */
    fun createForegroundServiceNotification(
        context: Context,
        serviceTypeTitle: String,
        description: String = "Service running in background"
    ): Notification {
        initChannels(context)

        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, AppForegroundService::class.java).apply {
                action = AppForegroundService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(serviceTypeTitle)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopIntent)
            .build()
    }

    private fun notifySafe(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission might be revoked
            e.printStackTrace()
        }
    }

    private fun createSampleBitmap(width: Int, height: Int, color: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)

        val paint = Paint().apply {
            this.color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, width / 2f, height / 2f + 10f, paint)
        return bitmap
    }
}

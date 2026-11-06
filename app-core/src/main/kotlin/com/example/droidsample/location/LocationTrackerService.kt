package com.example.droidsample.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service that maintains continuous background location tracking
 * with an ongoing system notification.
 */
class LocationTrackerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nativeLocationManager: NativeLocationManager

    override fun onCreate() {
        super.onCreate()
        nativeLocationManager = NativeLocationManager(this)
        createNotificationChannel()

        serviceScope.launch {
            nativeLocationManager.currentLocation.collect { location ->
                _serviceLocation.value = location
                if (location != null && _isServiceRunning.value) {
                    updateNotification(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingService()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        _isServiceRunning.value = true
        val notification = buildNotification(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        nativeLocationManager.startLocationUpdates()
    }

    private fun updateNotification(location: LocationData) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(location))
    }

    private fun buildNotification(location: LocationData?): Notification {
        val stopIntent = Intent(this, LocationTrackerService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = PendingIntent.getService(this, 101, stopIntent, flags)

        val contentText = if (location != null) {
            val speedStr = location.speedKmh?.let { String.format("%.1f km/h", it) } ?: "0 km/h"
            String.format("Lat: %.5f, Lon: %.5f | Speed: %s", location.latitude, location.longitude, speedStr)
        } else {
            "Acquiring satellite fix..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Continuous GPS Tracker")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .build()
    }

    private fun stopTrackingService() {
        nativeLocationManager.stopLocationUpdates()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        nativeLocationManager.stopLocationUpdates()
        _isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for continuous background GPS and GNSS telemetry"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "location_tracker_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_START_TRACKING = "com.example.droidsample.location.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.droidsample.location.STOP_TRACKING"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _serviceLocation = MutableStateFlow<LocationData?>(null)
        val serviceLocation: StateFlow<LocationData?> = _serviceLocation.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackerService::class.java).apply {
                action = ACTION_START_TRACKING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackerService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }
}

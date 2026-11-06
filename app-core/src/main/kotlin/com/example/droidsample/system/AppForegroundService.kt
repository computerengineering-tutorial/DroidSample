package com.example.droidsample.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android 14+ Compliant Typed Foreground Service.
 * Demonstrates starting foreground services with explicit service types:
 * - Camera
 * - Microphone
 * - Location
 * - Data Sync
 * - Media Playback
 */
class AppForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var tickerJob: Job? = null
    private var startTimeMillis = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopServiceInternal()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE_TYPE -> {
                val serviceType = intent?.getIntExtra(EXTRA_SERVICE_TYPE, DEFAULT_TYPE_FLAGS)
                    ?: DEFAULT_TYPE_FLAGS
                startForegroundInternal(serviceType)
            }
        }

        return START_STICKY
    }

    private fun startForegroundInternal(serviceType: Int) {
        startTimeMillis = System.currentTimeMillis()
        val typeLabel = describeServiceTypes(serviceType)

        val notification = NotificationHelper.createForegroundServiceNotification(
            context = this,
            serviceTypeTitle = "Foreground Service Active",
            description = "Active types: $typeLabel"
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Ensure on Android 14 (API 34) valid foregroundServiceType is provided
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            _serviceState.value = ForegroundServiceStatus(
                isRunning = true,
                serviceTypeMask = serviceType,
                typeDescription = typeLabel,
                startedAt = startTimeMillis
            )

            // Start ticking duration
            startTicker()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
                _serviceState.value = _serviceState.value.copy(elapsedSeconds = elapsedSeconds)
                delay(1000)
            }
        }
    }

    private fun stopServiceInternal() {
        tickerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        _serviceState.value = ForegroundServiceStatus(
            isRunning = false,
            serviceTypeMask = 0,
            typeDescription = "Stopped",
            startedAt = 0L,
            elapsedSeconds = 0L
        )

        stopSelf()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        _serviceState.value = ForegroundServiceStatus(isRunning = false)
        super.onDestroy()
    }

    data class ForegroundServiceStatus(
        val isRunning: Boolean = false,
        val serviceTypeMask: Int = 0,
        val typeDescription: String = "Inactive",
        val startedAt: Long = 0L,
        val elapsedSeconds: Long = 0L
    )

    companion object {
        const val NOTIFICATION_ID = 5001

        const val ACTION_START = "com.example.droidsample.action.START_FOREGROUND_SERVICE"
        const val ACTION_STOP = "com.example.droidsample.action.STOP_FOREGROUND_SERVICE"
        const val ACTION_UPDATE_TYPE = "com.example.droidsample.action.UPDATE_FOREGROUND_SERVICE"
        const val EXTRA_SERVICE_TYPE = "extra_service_type"

        // Type definitions using Android Framework constants
        val TYPE_DATA_SYNC = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 1

        val TYPE_LOCATION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 8

        val TYPE_CAMERA = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else 64

        val TYPE_MICROPHONE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 128

        val TYPE_MEDIA_PLAYBACK = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 2

        val DEFAULT_TYPE_FLAGS = TYPE_DATA_SYNC

        private val _serviceState = MutableStateFlow(ForegroundServiceStatus())
        val serviceState: StateFlow<ForegroundServiceStatus> = _serviceState.asStateFlow()

        /**
         * Converts bitmask types to human-readable string.
         */
        fun describeServiceTypes(types: Int): String {
            val list = mutableListOf<String>()
            if ((types and TYPE_DATA_SYNC) != 0) list.add("Data Sync")
            if ((types and TYPE_LOCATION) != 0) list.add("Location")
            if ((types and TYPE_CAMERA) != 0) list.add("Camera")
            if ((types and TYPE_MICROPHONE) != 0) list.add("Microphone")
            if ((types and TYPE_MEDIA_PLAYBACK) != 0) list.add("Media Playback")
            return if (list.isEmpty()) "None" else list.joinToString(", ")
        }

        /**
         * Launch foreground service with the given typed configuration.
         */
        fun start(context: Context, serviceType: Int = TYPE_DATA_SYNC) {
            val intent = Intent(context, AppForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVICE_TYPE, serviceType)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Request service termination.
         */
        fun stop(context: Context) {
            val intent = Intent(context, AppForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

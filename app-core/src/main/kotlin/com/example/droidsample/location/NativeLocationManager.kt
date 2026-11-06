package com.example.droidsample.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Encapsulates detailed telemetry information from Android [Location].
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val verticalAccuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val speedAccuracyMps: Float? = null,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val horizontalAccuracyMeters: Float? = null,
    val timeMillis: Long = 0L,
    val elapsedRealtimeNanos: Long = 0L,
    val provider: String = "",
    val isMock: Boolean = false
) {
    val speedKmh: Float?
        get() = speedMetersPerSecond?.times(3.6f)

    val speedMph: Float?
        get() = speedMetersPerSecond?.times(2.23694f)
}

/**
 * Status information for all supported location providers.
 */
data class ProviderStatus(
    val provider: String,
    val isEnabled: Boolean,
    val lastKnownLocation: LocationData? = null
)

/**
 * Manages Android Native [LocationManager] interactions for GPS, Network, and Passive providers.
 */
class NativeLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val executor = Executors.newSingleThreadExecutor()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _providerStatuses = MutableStateFlow<Map<String, ProviderStatus>>(emptyMap())
    val providerStatuses: StateFlow<Map<String, ProviderStatus>> = _providerStatuses.asStateFlow()

    private val _activeProvider = MutableStateFlow(LocationManager.GPS_PROVIDER)
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val data = mapLocationToData(location)
            _currentLocation.value = data
            refreshProviderStatuses()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            refreshProviderStatuses()
        }

        override fun onProviderEnabled(provider: String) {
            refreshProviderStatuses()
        }

        override fun onProviderDisabled(provider: String) {
            refreshProviderStatuses()
        }
    }

    init {
        refreshProviderStatuses()
    }

    /**
     * Inspects available providers: GPS_PROVIDER, NETWORK_PROVIDER, PASSIVE_PROVIDER.
     */
    fun getAvailableProviders(): List<String> {
        val providers = locationManager?.allProviders ?: emptyList()
        val standard = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return (standard + providers).distinct()
    }

    /**
     * Checks if a specific provider is currently enabled in device settings.
     */
    fun isProviderEnabled(provider: String): Boolean {
        return try {
            locationManager?.isProviderEnabled(provider) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Switches the active provider (e.g. GPS_PROVIDER, NETWORK_PROVIDER, PASSIVE_PROVIDER).
     */
    fun setActiveProvider(provider: String) {
        _activeProvider.value = provider
        if (_isTracking.value) {
            stopLocationUpdates()
            startLocationUpdates(provider)
        }
    }

    /**
     * Refreshes the cached status and last known location for standard providers.
     */
    @SuppressLint("MissingPermission")
    fun refreshProviderStatuses() {
        if (locationManager == null) return
        val map = mutableMapOf<String, ProviderStatus>()
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val enabled = isProviderEnabled(provider)
            val lastLoc = try {
                locationManager.getLastKnownLocation(provider)?.let { mapLocationToData(it) }
            } catch (e: SecurityException) {
                null
            }
            map[provider] = ProviderStatus(provider, enabled, lastLoc)
        }
        _providerStatuses.value = map
    }

    /**
     * Retrieves the last known location for a specified provider or the active provider.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(provider: String = _activeProvider.value): LocationData? {
        if (locationManager == null) return null
        return try {
            val loc = locationManager.getLastKnownLocation(provider)
            loc?.let {
                val data = mapLocationToData(it)
                if (_currentLocation.value == null) {
                    _currentLocation.value = data
                }
                data
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Requests a single fresh current location fix.
     * Uses Android 11+ (API 30+) [LocationManager.getCurrentLocation] when available,
     * falling back to last known location or quick single update.
     */
    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(
        provider: String = _activeProvider.value,
        onResult: (LocationData?) -> Unit
    ) {
        val lm = locationManager ?: run {
            onResult(null)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                lm.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    val data = location?.let { mapLocationToData(it) }
                    if (data != null) {
                        _currentLocation.value = data
                    }
                    onResult(data)
                }
            } else {
                val lastKnown = getLastKnownLocation(provider)
                if (lastKnown != null) {
                    onResult(lastKnown)
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(
                        provider,
                        { location ->
                            val data = mapLocationToData(location)
                            _currentLocation.value = data
                            onResult(data)
                        },
                        null
                    )
                }
            }
        } catch (e: SecurityException) {
            onResult(null)
        } catch (e: Exception) {
            onResult(getLastKnownLocation(provider))
        }
    }

    /**
     * Begins continuous location updates using the specified provider.
     *
     * @param provider The provider name (default: activeProvider)
     * @param minTimeMs Minimum time interval between updates in milliseconds
     * @param minDistanceMeters Minimum distance between updates in meters
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        provider: String = _activeProvider.value,
        minTimeMs: Long = 1000L,
        minDistanceMeters: Float = 0f
    ) {
        val lm = locationManager ?: return
        try {
            lm.requestLocationUpdates(
                provider,
                minTimeMs,
                minDistanceMeters,
                locationListener
            )
            _isTracking.value = true
            _activeProvider.value = provider
            refreshProviderStatuses()
        } catch (e: SecurityException) {
            _isTracking.value = false
        } catch (e: Exception) {
            _isTracking.value = false
        }
    }

    /**
     * Stops receiving continuous location updates.
     */
    fun stopLocationUpdates() {
        val lm = locationManager ?: return
        try {
            lm.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Ignored
        } finally {
            _isTracking.value = false
        }
    }

    /**
     * Extracts and maps all telemetry fields from an Android [Location] instance.
     */
    fun mapLocationToData(location: Location): LocationData {
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        val vertAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters
        } else null

        val speedAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond
        } else null

        val bearingAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees
        } else null

        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            verticalAccuracyMeters = vertAcc,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            speedAccuracyMps = speedAcc,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            bearingAccuracyDegrees = bearingAcc,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            timeMillis = location.time,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            provider = location.provider ?: "",
            isMock = isMock
        )
    }
}

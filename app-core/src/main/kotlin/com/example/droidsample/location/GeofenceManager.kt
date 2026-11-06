package com.example.droidsample.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Type of geofence boundary transition.
 */
enum class GeofenceTransition {
    ENTER,
    EXIT
}

/**
 * Representation of an active geofence zone.
 */
data class GeofenceItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val expirationMillis: Long = -1L, // -1 means no expiration
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Event recorded when a user enters or exits a geofence.
 */
data class GeofenceEvent(
    val id: String = UUID.randomUUID().toString(),
    val geofenceId: String,
    val geofenceName: String,
    val transition: GeofenceTransition,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Manages native Android proximity alerts using [LocationManager.addProximityAlert]
 * and provides distance calculation and transition logging.
 */
class GeofenceManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _geofences = MutableStateFlow<List<GeofenceItem>>(emptyList())
    val geofences: StateFlow<List<GeofenceItem>> = _geofences.asStateFlow()

    private val _events = MutableStateFlow<List<GeofenceEvent>>(emptyList())
    val events: StateFlow<List<GeofenceEvent>> = _events.asStateFlow()

    private val pendingIntentsMap = mutableMapOf<String, PendingIntent>()

    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_GEOFENCE_PROXIMITY_ALERT) return
            val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
            val geofenceId = intent.getStringExtra(EXTRA_GEOFENCE_ID) ?: return
            val geofence = _geofences.value.find { it.id == geofenceId }
            val name = geofence?.name ?: "Geofence ($geofenceId)"

            val transition = if (entering) GeofenceTransition.ENTER else GeofenceTransition.EXIT
            val event = GeofenceEvent(
                geofenceId = geofenceId,
                geofenceName = name,
                transition = transition
            )
            val updated = listOf(event) + _events.value
            _events.value = updated.take(50) // Keep last 50 events
        }
    }

    init {
        val filter = IntentFilter(ACTION_GEOFENCE_PROXIMITY_ALERT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(proximityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(proximityReceiver, filter)
        }
    }

    /**
     * Registers a new proximity alert geofence with Android's [LocationManager].
     *
     * @param name Friendly name for the fence (e.g. "Office", "Home")
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusMeters Radius threshold in meters
     * @param expirationMillis Duration until expiration in ms (-1 for persistent)
     * @return Created [GeofenceItem] on success, null on failure
     */
    @SuppressLint("MissingPermission")
    fun addGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        expirationMillis: Long = -1L
    ): GeofenceItem? {
        val lm = locationManager ?: return null
        if (!DistanceCalculator.isValidCoordinate(latitude, longitude) || radiusMeters <= 0f) {
            return null
        }

        val item = GeofenceItem(
            name = name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            expirationMillis = expirationMillis
        )

        val intent = Intent(ACTION_GEOFENCE_PROXIMITY_ALERT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_GEOFENCE_ID, item.id)
            putExtra(EXTRA_GEOFENCE_NAME, item.name)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val requestCode = item.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags
        )

        try {
            lm.addProximityAlert(
                latitude,
                longitude,
                radiusMeters,
                expirationMillis,
                pendingIntent
            )
            pendingIntentsMap[item.id] = pendingIntent
            _geofences.value = _geofences.value + item
            return item
        } catch (e: SecurityException) {
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Removes an active geofence and cancels its associated PendingIntent.
     */
    @SuppressLint("MissingPermission")
    fun removeGeofence(geofenceId: String): Boolean {
        val lm = locationManager ?: return false
        val pi = pendingIntentsMap.remove(geofenceId)
        if (pi != null) {
            try {
                lm.removeProximityAlert(pi)
                pi.cancel()
            } catch (e: Exception) {
                // Ignore
            }
        }
        _geofences.value = _geofences.value.filterNot { it.id == geofenceId }
        return true
    }

    /**
     * Removes all active geofences.
     */
    @SuppressLint("MissingPermission")
    fun clearAllGeofences() {
        val lm = locationManager ?: return
        for ((_, pi) in pendingIntentsMap) {
            try {
                lm.removeProximityAlert(pi)
                pi.cancel()
            } catch (e: Exception) {
                // Ignore
            }
        }
        pendingIntentsMap.clear()
        _geofences.value = emptyList()
    }

    /**
     * Simulates a geofence trigger event (convenient for testing and QA).
     */
    fun simulateTrigger(geofenceId: String, entering: Boolean) {
        val fence = _geofences.value.find { it.id == geofenceId } ?: return
        val event = GeofenceEvent(
            geofenceId = geofenceId,
            geofenceName = fence.name,
            transition = if (entering) GeofenceTransition.ENTER else GeofenceTransition.EXIT
        )
        _events.value = listOf(event) + _events.value
    }

    /**
     * Calculates distance from current coordinates to a geofence center using both
     * Android's [Location.distanceBetween] and Haversine formula.
     */
    fun calculateDistance(
        currentLat: Double,
        currentLon: Double,
        geofence: GeofenceItem
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLat,
            currentLon,
            geofence.latitude,
            geofence.longitude,
            results
        )
        return results[0]
    }

    /**
     * Checks if current coordinate is inside the boundary radius of a geofence.
     */
    fun isInsideGeofence(
        currentLat: Double,
        currentLon: Double,
        geofence: GeofenceItem
    ): Boolean {
        val dist = calculateDistance(currentLat, currentLon, geofence)
        return dist <= geofence.radiusMeters
    }

    /**
     * Unregisters broadcast receiver when manager is destroyed.
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(proximityReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        const val ACTION_GEOFENCE_PROXIMITY_ALERT = "com.example.droidsample.location.ACTION_PROXIMITY_ALERT"
        const val EXTRA_GEOFENCE_ID = "extra_geofence_id"
        const val EXTRA_GEOFENCE_NAME = "extra_geofence_name"
    }
}

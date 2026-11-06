package com.example.droidsample.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Details of an individual GNSS satellite tracked by the device.
 */
data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int,
    val constellationName: String,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val usedInFix: Boolean,
    val hasCarrierFrequency: Boolean,
    val carrierFrequencyHz: Float? = null
) {
    val carrierFrequencyMhz: Float?
        get() = carrierFrequencyHz?.div(1_000_000f)
}

/**
 * Aggregated GNSS status and satellite inventory.
 */
data class GnssState(
    val isGnssActive: Boolean = false,
    val timeToFirstFixMillis: Int? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    val totalInView: Int = 0,
    val totalUsedInFix: Int = 0,
    val averageCn0: Float = 0f,
    val constellationCounts: Map<String, Int> = emptyMap(),
    val constellationUsedCounts: Map<String, Int> = emptyMap()
)

/**
 * Manages GNSS satellite status callbacks via Android [GnssStatus.Callback].
 */
class GnssStatusManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gnssState = MutableStateFlow(GnssState())
    val gnssState: StateFlow<GnssState> = _gnssState.asStateFlow()

    private var isRegistered = false

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            _gnssState.value = _gnssState.value.copy(isGnssActive = true)
        }

        override fun onStopped() {
            _gnssState.value = _gnssState.value.copy(isGnssActive = false)
        }

        override fun onFirstFix(ttffMillis: Int) {
            _gnssState.value = _gnssState.value.copy(
                isGnssActive = true,
                timeToFirstFixMillis = ttffMillis
            )
        }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val satelliteCount = status.satelliteCount
            val satelliteList = ArrayList<SatelliteInfo>(satelliteCount)
            var usedInFixCount = 0
            var cn0Sum = 0f
            val constCounts = mutableMapOf<String, Int>()
            val constUsedCounts = mutableMapOf<String, Int>()

            for (i in 0 until satelliteCount) {
                val svid = status.getSvid(i)
                val constellationType = status.getConstellationType(i)
                val constellationName = getConstellationName(constellationType)
                val cn0 = status.getCn0DbHz(i)
                val elevation = status.getElevationDegrees(i)
                val azimuth = status.getAzimuthDegrees(i)
                val hasEphemeris = status.hasEphemerisData(i)
                val hasAlmanac = status.hasAlmanacData(i)
                val used = status.usedInFix(i)

                val hasCarrierFreq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    status.hasCarrierFrequencyHz(i)
                } else false

                val carrierFreqHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierFreq) {
                    status.getCarrierFrequencyHz(i)
                } else null

                if (used) {
                    usedInFixCount++
                    constUsedCounts[constellationName] = (constUsedCounts[constellationName] ?: 0) + 1
                }
                constCounts[constellationName] = (constCounts[constellationName] ?: 0) + 1
                cn0Sum += cn0

                satelliteList.add(
                    SatelliteInfo(
                        svid = svid,
                        constellationType = constellationType,
                        constellationName = constellationName,
                        cn0DbHz = cn0,
                        elevationDegrees = elevation,
                        azimuthDegrees = azimuth,
                        hasEphemeris = hasEphemeris,
                        hasAlmanac = hasAlmanac,
                        usedInFix = used,
                        hasCarrierFrequency = hasCarrierFreq,
                        carrierFrequencyHz = carrierFreqHz
                    )
                )
            }

            // Sort by used in fix first, then descending C/N0
            satelliteList.sortWith(
                compareByDescending<SatelliteInfo> { it.usedInFix }
                    .thenByDescending { it.cn0DbHz }
            )

            val avgCn0 = if (satelliteCount > 0) cn0Sum / satelliteCount else 0f

            _gnssState.value = _gnssState.value.copy(
                isGnssActive = true,
                satellites = satelliteList,
                totalInView = satelliteCount,
                totalUsedInFix = usedInFixCount,
                averageCn0 = avgCn0,
                constellationCounts = constCounts,
                constellationUsedCounts = constUsedCounts
            )
        }
    }

    /**
     * Registers the GNSS status callback to begin receiving satellite constellation updates.
     */
    @SuppressLint("MissingPermission")
    fun registerCallback(): Boolean {
        if (isRegistered || locationManager == null) return false
        return try {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val success = locationManager.registerGnssStatusCallback(mainExecutor, gnssStatusCallback)
            isRegistered = success
            success
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unregisters the GNSS status callback.
     */
    fun unregisterCallback() {
        if (!isRegistered || locationManager == null) return
        try {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        } catch (e: Exception) {
            // Ignored
        } finally {
            isRegistered = false
            _gnssState.value = _gnssState.value.copy(isGnssActive = false)
        }
    }

    companion object {
        /**
         * Resolves integer constellation constants to readable string identifiers.
         */
        fun getConstellationName(constellationType: Int): String {
            return when (constellationType) {
                GnssStatus.CONSTELLATION_GPS -> "GPS"
                GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                GnssStatus.CONSTELLATION_IRNSS -> "NavIC / IRNSS"
                GnssStatus.CONSTELLATION_UNKNOWN -> "Unknown"
                else -> "Constellation #$constellationType"
            }
        }
    }
}

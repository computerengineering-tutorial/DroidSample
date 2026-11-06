@file:Suppress("DEPRECATION")

package com.example.droidsample.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Mobile cellular generation categories.
 */
enum class NetworkGeneration(val displayName: String) {
    UNKNOWN("Unknown"),
    GEN_2G("2G"),
    GEN_3G("3G"),
    GEN_4G("4G"),
    GEN_5G("5G")
}

/**
 * High-level cellular mobile data connection state.
 */
enum class CellularDataState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    SUSPENDED("Suspended"),
    UNKNOWN("Unknown")
}

/**
 * Cellular radio service state.
 */
enum class CellularServiceState(val label: String) {
    IN_SERVICE("In Service"),
    OUT_OF_SERVICE("Out of Service"),
    EMERGENCY_ONLY("Emergency Calls Only"),
    POWER_OFF("Radio Off (Airplane Mode)"),
    UNKNOWN("Unknown")
}

/**
 * Live cellular connectivity and radio signal metrics.
 */
data class CellularSignalInfo(
    val level: Int = 0,                    // 0 to 4
    val dbm: Int = -120,                   // Signal power in dBm
    val asuLevel: Int = 0,                 // Arbitrary Strength Unit
    val networkType: String = "Unknown",   // e.g. "NR (5G SA)", "LTE", "HSPA+"
    val networkGeneration: NetworkGeneration = NetworkGeneration.UNKNOWN,
    val operatorName: String = "No Service",
    val operatorNumeric: String = "",      // MCC+MNC
    val dataState: CellularDataState = CellularDataState.UNKNOWN,
    val serviceState: CellularServiceState = CellularServiceState.UNKNOWN,
    val isRoaming: Boolean = false,
    val isMetered: Boolean = true
) {
    val signalPercent: Int
        get() = (level.coerceIn(0, 4) * 25)

    val dbmFormatted: String
        get() = if (dbm != CellInfoScanner.UNAVAILABLE_INT && dbm != -120) "$dbm dBm" else "N/A"
}

/**
 * Monitors cellular radio state, 5G/4G/3G network type detection,
 * data connection status, and real-time dBm signal strength.
 */
@Suppress("DEPRECATION")
class CellularNetworkManager(private val context: Context) {

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    /**
     * Checks if READ_PHONE_STATE permission is held.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves an instant snapshot of the current cellular radio state.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentSignalInfo(): CellularSignalInfo {
        val tm = telephonyManager ?: return CellularSignalInfo()

        val operatorName = tm.networkOperatorName.takeIf { it.isNotBlank() } ?: "No Service"
        val operatorNumeric = tm.networkOperator ?: ""
        val isRoaming = tm.isNetworkRoaming

        val dataState = when (tm.dataState) {
            TelephonyManager.DATA_CONNECTED -> CellularDataState.CONNECTED
            TelephonyManager.DATA_CONNECTING -> CellularDataState.CONNECTING
            TelephonyManager.DATA_SUSPENDED -> CellularDataState.SUSPENDED
            TelephonyManager.DATA_DISCONNECTED -> CellularDataState.DISCONNECTED
            else -> CellularDataState.UNKNOWN
        }

        @Suppress("DEPRECATION")
        val rawNetworkType = if (hasPermission()) {
            try {
                tm.networkType
            } catch (_: SecurityException) {
                TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
        } else {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val (netTypeStr, gen) = classifyNetworkType(rawNetworkType)

        var level = 0
        var dbm = -120
        var asu = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tm.signalStrength?.let { ss ->
                level = ss.level
                dbm = extractDbmFromSignalStrength(ss)
                asu = extractAsuFromSignalStrength(ss)
            }
        }

        return CellularSignalInfo(
            level = level,
            dbm = dbm,
            asuLevel = asu,
            networkType = netTypeStr,
            networkGeneration = gen,
            operatorName = operatorName,
            operatorNumeric = operatorNumeric,
            dataState = dataState,
            serviceState = CellularServiceState.IN_SERVICE,
            isRoaming = isRoaming,
            isMetered = true
        )
    }

    /**
     * Returns a cold [Flow] that observes cellular signal strength, network display overrides
     * (e.g. 5G NSA vs 5G SA), service state, and data connectivity.
     */
    @SuppressLint("MissingPermission")
    fun cellularSignalFlow(): Flow<CellularSignalInfo> = callbackFlow {
        val tm = telephonyManager
        if (tm == null) {
            trySend(CellularSignalInfo())
            awaitClose { }
            return@callbackFlow
        }

        var currentInfo = getCurrentSignalInfo()
        trySend(currentInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener,
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.DisplayInfoListener {

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val dbm = extractDbmFromSignalStrength(signalStrength)
                    val asu = extractAsuFromSignalStrength(signalStrength)
                    currentInfo = currentInfo.copy(
                        level = signalStrength.level,
                        dbm = dbm,
                        asuLevel = asu
                    )
                    trySend(currentInfo)
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    val dataState = mapDataConnectionState(state)
                    val (netName, gen) = classifyNetworkType(networkType)
                    currentInfo = currentInfo.copy(
                        dataState = dataState,
                        networkType = netName,
                        networkGeneration = gen
                    )
                    trySend(currentInfo)
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    val sState = mapServiceState(serviceState.state)
                    currentInfo = currentInfo.copy(
                        serviceState = sState,
                        isRoaming = serviceState.roaming,
                        operatorName = serviceState.operatorAlphaLong ?: currentInfo.operatorName,
                        operatorNumeric = serviceState.operatorNumeric ?: currentInfo.operatorNumeric
                    )
                    trySend(currentInfo)
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    val (netName, gen) = classifyDisplayInfo(telephonyDisplayInfo)
                    currentInfo = currentInfo.copy(
                        networkType = netName,
                        networkGeneration = gen
                    )
                    trySend(currentInfo)
                }
            }

            try {
                tm.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (se: SecurityException) {
                trySend(currentInfo.copy(serviceState = CellularServiceState.UNKNOWN))
            }

            awaitClose {
                try {
                    tm.unregisterTelephonyCallback(callback)
                } catch (_: Exception) {
                }
            }
        } else {
            // Android 11 and lower fallback via PhoneStateListener
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength?.let { ss ->
                        val dbm = extractDbmFromSignalStrength(ss)
                        val asu = extractAsuFromSignalStrength(ss)
                        currentInfo = currentInfo.copy(
                            level = ss.level,
                            dbm = dbm,
                            asuLevel = asu
                        )
                        trySend(currentInfo)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    val dataState = mapDataConnectionState(state)
                    val (netName, gen) = classifyNetworkType(networkType)
                    currentInfo = currentInfo.copy(
                        dataState = dataState,
                        networkType = netName,
                        networkGeneration = gen
                    )
                    trySend(currentInfo)
                }

                @Deprecated("Deprecated in Java")
                override fun onServiceStateChanged(serviceState: ServiceState?) {
                    serviceState?.let { ss ->
                        val sState = mapServiceState(ss.state)
                        currentInfo = currentInfo.copy(
                            serviceState = sState,
                            isRoaming = ss.roaming,
                            operatorName = ss.operatorAlphaLong ?: currentInfo.operatorName,
                            operatorNumeric = ss.operatorNumeric ?: currentInfo.operatorNumeric
                        )
                        trySend(currentInfo)
                    }
                }
            }

            @Suppress("DEPRECATION")
            val events = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or
                    PhoneStateListener.LISTEN_SERVICE_STATE

            try {
                @Suppress("DEPRECATION")
                tm.listen(listener, events)
            } catch (_: Exception) {
            }

            awaitClose {
                try {
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun classifyDisplayInfo(displayInfo: TelephonyDisplayInfo): Pair<String, NetworkGeneration> {
        val override = displayInfo.overrideNetworkType
        return when (override) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G Ultra Wideband (NR Advanced)" to NetworkGeneration.GEN_5G
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G (NR NSA)" to NetworkGeneration.GEN_5G
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE-A Pro (4.5G)" to NetworkGeneration.GEN_4G
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE-A (Carrier Aggregation)" to NetworkGeneration.GEN_4G
            else -> classifyNetworkType(displayInfo.networkType)
        }
    }

    private fun extractDbmFromSignalStrength(signalStrength: SignalStrength): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignalStrengths = signalStrength.cellSignalStrengths
            for (css in cellSignalStrengths) {
                if (css.dbm != CellInfoScanner.UNAVAILABLE_INT && css.dbm < 0) {
                    return css.dbm
                }
            }
        }
        return -120
    }

    private fun extractAsuFromSignalStrength(signalStrength: SignalStrength): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignalStrengths = signalStrength.cellSignalStrengths
            for (css in cellSignalStrengths) {
                if (css.asuLevel in 0..99) {
                    return css.asuLevel
                }
            }
        }
        return 0
    }

    companion object {
        fun mapDataConnectionState(state: Int): CellularDataState = when (state) {
            TelephonyManager.DATA_CONNECTED -> CellularDataState.CONNECTED
            TelephonyManager.DATA_CONNECTING -> CellularDataState.CONNECTING
            TelephonyManager.DATA_SUSPENDED -> CellularDataState.SUSPENDED
            TelephonyManager.DATA_DISCONNECTED -> CellularDataState.DISCONNECTED
            else -> CellularDataState.UNKNOWN
        }

        fun mapServiceState(state: Int): CellularServiceState = when (state) {
            ServiceState.STATE_IN_SERVICE -> CellularServiceState.IN_SERVICE
            ServiceState.STATE_OUT_OF_SERVICE -> CellularServiceState.OUT_OF_SERVICE
            ServiceState.STATE_EMERGENCY_ONLY -> CellularServiceState.EMERGENCY_ONLY
            ServiceState.STATE_POWER_OFF -> CellularServiceState.POWER_OFF
            else -> CellularServiceState.UNKNOWN
        }

        fun classifyNetworkType(networkType: Int): Pair<String, NetworkGeneration> = when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR" to NetworkGeneration.GEN_5G

            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)" to NetworkGeneration.GEN_4G
            TelephonyManager.NETWORK_TYPE_IWLAN -> "VoWiFi (IWLAN)" to NetworkGeneration.GEN_4G

            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3.5G)" to NetworkGeneration.GEN_3G
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3.5G)" to NetworkGeneration.GEN_3G
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)" to NetworkGeneration.GEN_3G
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3.75G)" to NetworkGeneration.GEN_3G
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)" to NetworkGeneration.GEN_3G
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO (3G)" to NetworkGeneration.GEN_3G

            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2.5G)" to NetworkGeneration.GEN_2G
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2.75G)" to NetworkGeneration.GEN_2G
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA (2G)" to NetworkGeneration.GEN_2G
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT (2G)" to NetworkGeneration.GEN_2G
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM (2G)" to NetworkGeneration.GEN_2G

            else -> "Cellular Network" to NetworkGeneration.UNKNOWN
        }
    }
}

@file:Suppress("DEPRECATION")

package com.example.droidsample.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Mobile cellular network technology type for a cell tower.
 */
enum class CellType(val displayName: String) {
    LTE("4G LTE"),
    NR("5G NR"),
    GSM("2G GSM"),
    WCDMA("3G WCDMA"),
    TDSCDMA("3G TD-SCDMA"),
    CDMA("2G/3G CDMA"),
    UNKNOWN("Unknown")
}

/**
 * Detailed radio metrics and identifiers for a detected cellular tower.
 */
data class CellTowerData(
    val cellType: CellType,
    val isRegistered: Boolean,
    val pci: Int?,                // Physical Cell Identifier
    val tac: Int?,                // Tracking Area Code (LTE/NR) or LAC (GSM/WCDMA)
    val ci: Long?,                // Cell Identity (CI for LTE, NCI for 5G, CID for GSM)
    val earfcnOrArfcn: Int?,      // Radio Frequency Channel Number
    val rsrpDbm: Int?,            // Reference Signal Received Power in dBm
    val rsrqDb: Int?,             // Reference Signal Received Quality in dB
    val rssnrDb: Int?,            // Reference Signal Signal-to-Noise Ratio in dB
    val cqi: Int?,                // Channel Quality Indicator
    val asuLevel: Int,            // Arbitrary Strength Unit
    val signalLevel: Int,         // 0 to 4
    val bandwidthKhz: Int?,       // Channel Bandwidth in kHz
    val mccMncString: String?,    // Network operator code
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() = "${cellType.displayName} ${if (isRegistered) "(Serving Cell)" else "(Neighbor Cell)"}"

    val rsrpFormatted: String
        get() = rsrpDbm?.let { "$it dBm" } ?: "N/A"

    val rsrqFormatted: String
        get() = rsrqDb?.let { "$it dB" } ?: "N/A"

    val rssnrFormatted: String
        get() = rssnrDb?.let { "$it dB" } ?: "N/A"

    val pciFormatted: String
        get() = pci?.takeIf { it != CellInfoScanner.UNAVAILABLE_INT }?.toString() ?: "N/A"

    val tacFormatted: String
        get() = tac?.takeIf { it != CellInfoScanner.UNAVAILABLE_INT }?.toString() ?: "N/A"

    val ciFormatted: String
        get() = ci?.takeIf { it != CellInfoScanner.UNAVAILABLE_LONG }?.toString() ?: "N/A"
}

/**
 * Output of a cellular tower inspection scan.
 */
data class CellScanResult(
    val towers: List<CellTowerData> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Scans nearby and serving cellular base stations / cell towers.
 * Parses 5G NR, 4G LTE, 3G WCDMA, and 2G GSM cell identities and signal parameters.
 */
@Suppress("DEPRECATION")
class CellInfoScanner(private val context: Context) {

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    /**
     * Cellular tower scanning requires ACCESS_FINE_LOCATION on Android 10+ (API 29+).
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if location service is globally enabled on the device.
     */
    fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Queries all visible and connected cell towers via [TelephonyManager.getAllCellInfo].
     */
    @SuppressLint("MissingPermission")
    fun scanCellTowers(): CellScanResult {
        if (!hasLocationPermission()) {
            return CellScanResult(
                hasLocationPermission = false,
                isLocationEnabled = isLocationEnabled(),
                errorMessage = "ACCESS_FINE_LOCATION permission is required to scan cell towers."
            )
        }

        if (!isLocationEnabled()) {
            return CellScanResult(
                hasLocationPermission = true,
                isLocationEnabled = false,
                errorMessage = "Location services must be enabled on the device to scan cellular towers."
            )
        }

        val tm = telephonyManager ?: return CellScanResult(
            hasLocationPermission = true,
            isLocationEnabled = true,
            errorMessage = "Telephony service unavailable."
        )

        return try {
            val cellInfoList: List<CellInfo> = tm.allCellInfo ?: emptyList()
            val parsedTowers = cellInfoList.mapNotNull { parseCellInfo(it) }
                .sortedWith(compareByDescending<CellTowerData> { it.isRegistered }.thenByDescending { it.signalLevel })

            CellScanResult(
                towers = parsedTowers,
                hasLocationPermission = true,
                isLocationEnabled = true
            )
        } catch (se: SecurityException) {
            CellScanResult(
                hasLocationPermission = false,
                isLocationEnabled = isLocationEnabled(),
                errorMessage = "SecurityException accessing cell tower info: ${se.message}"
            )
        } catch (e: Exception) {
            CellScanResult(
                hasLocationPermission = true,
                isLocationEnabled = isLocationEnabled(),
                errorMessage = "Error scanning cell towers: ${e.message}"
            )
        }
    }

    private fun parseCellInfo(info: CellInfo): CellTowerData? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr -> parseCellInfoNr(info)
            info is CellInfoLte -> parseCellInfoLte(info)
            info is CellInfoWcdma -> parseCellInfoWcdma(info)
            info is CellInfoGsm -> parseCellInfoGsm(info)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoTdscdma -> parseCellInfoTdscdma(info)
            info is CellInfoCdma -> parseCellInfoCdma(info)
            else -> null
        }
    }

    private fun parseCellInfoLte(info: CellInfoLte): CellTowerData {
        val identity: CellIdentityLte = info.cellIdentity
        val strength: CellSignalStrengthLte = info.cellSignalStrength

        val pci = sanitizeInt(identity.pci)
        val tac = sanitizeInt(identity.tac)
        val ci = sanitizeLong(identity.ci.toLong())
        val earfcn = sanitizeInt(identity.earfcn)
        val bw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) sanitizeInt(identity.bandwidth) else null
        val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mcc = identity.mccString
            val mnc = identity.mncString
            if (mcc != null && mnc != null) "$mcc-$mnc" else null
        } else {
            @Suppress("DEPRECATION")
            if (identity.mcc > 0) "${identity.mcc}-${identity.mnc}" else null
        }

        val rsrp = sanitizeInt(strength.rsrp)
        val rsrq = sanitizeInt(strength.rsrq)
        val rssnr = sanitizeInt(strength.rssnr)
        val cqi = sanitizeInt(strength.cqi)

        return CellTowerData(
            cellType = CellType.LTE,
            isRegistered = info.isRegistered,
            pci = pci,
            tac = tac,
            ci = ci,
            earfcnOrArfcn = earfcn,
            rsrpDbm = rsrp ?: sanitizeInt(strength.dbm),
            rsrqDb = rsrq,
            rssnrDb = rssnr,
            cqi = cqi,
            asuLevel = strength.asuLevel,
            signalLevel = strength.level,
            bandwidthKhz = bw,
            mccMncString = mccMnc
        )
    }

    @SuppressLint("NewApi")
    private fun parseCellInfoNr(info: CellInfoNr): CellTowerData {
        val identity = info.cellIdentity as? CellIdentityNr
        val strength = info.cellSignalStrength as? CellSignalStrengthNr

        val pci = identity?.let { sanitizeInt(it.pci) }
        val tac = identity?.let { sanitizeInt(it.tac) }
        val nci = identity?.let { sanitizeLong(it.nci) }
        val nrarfcn = identity?.let { sanitizeInt(it.nrarfcn) }
        val mccMnc = identity?.let { id ->
            val mcc = id.mccString
            val mnc = id.mncString
            if (mcc != null && mnc != null) "$mcc-$mnc" else null
        }

        val ssRsrp = strength?.let { sanitizeInt(it.ssRsrp) }
        val ssRsrq = strength?.let { sanitizeInt(it.ssRsrq) }
        val ssSinr = strength?.let { sanitizeInt(it.ssSinr) }
        val level = strength?.level ?: 0
        val asu = strength?.asuLevel ?: 0
        val dbm = strength?.let { sanitizeInt(it.dbm) }

        return CellTowerData(
            cellType = CellType.NR,
            isRegistered = info.isRegistered,
            pci = pci,
            tac = tac,
            ci = nci,
            earfcnOrArfcn = nrarfcn,
            rsrpDbm = ssRsrp ?: dbm,
            rsrqDb = ssRsrq,
            rssnrDb = ssSinr,
            cqi = null,
            asuLevel = asu,
            signalLevel = level,
            bandwidthKhz = null,
            mccMncString = mccMnc
        )
    }

    private fun parseCellInfoWcdma(info: CellInfoWcdma): CellTowerData {
        val identity: CellIdentityWcdma = info.cellIdentity
        val strength: CellSignalStrengthWcdma = info.cellSignalStrength

        val psc = sanitizeInt(identity.psc)
        val lac = sanitizeInt(identity.lac)
        val cid = sanitizeLong(identity.cid.toLong())
        val uarfcn = sanitizeInt(identity.uarfcn)
        val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mcc = identity.mccString
            val mnc = identity.mncString
            if (mcc != null && mnc != null) "$mcc-$mnc" else null
        } else {
            @Suppress("DEPRECATION")
            if (identity.mcc > 0) "${identity.mcc}-${identity.mnc}" else null
        }

        return CellTowerData(
            cellType = CellType.WCDMA,
            isRegistered = info.isRegistered,
            pci = psc,
            tac = lac,
            ci = cid,
            earfcnOrArfcn = uarfcn,
            rsrpDbm = sanitizeInt(strength.dbm),
            rsrqDb = null,
            rssnrDb = null,
            cqi = null,
            asuLevel = strength.asuLevel,
            signalLevel = strength.level,
            bandwidthKhz = null,
            mccMncString = mccMnc
        )
    }

    private fun parseCellInfoGsm(info: CellInfoGsm): CellTowerData {
        val identity: CellIdentityGsm = info.cellIdentity
        val strength: CellSignalStrengthGsm = info.cellSignalStrength

        val bsic = sanitizeInt(identity.bsic)
        val lac = sanitizeInt(identity.lac)
        val cid = sanitizeLong(identity.cid.toLong())
        val arfcn = sanitizeInt(identity.arfcn)
        val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mcc = identity.mccString
            val mnc = identity.mncString
            if (mcc != null && mnc != null) "$mcc-$mnc" else null
        } else {
            @Suppress("DEPRECATION")
            if (identity.mcc > 0) "${identity.mcc}-${identity.mnc}" else null
        }

        return CellTowerData(
            cellType = CellType.GSM,
            isRegistered = info.isRegistered,
            pci = bsic,
            tac = lac,
            ci = cid,
            earfcnOrArfcn = arfcn,
            rsrpDbm = sanitizeInt(strength.dbm),
            rsrqDb = null,
            rssnrDb = null,
            cqi = null,
            asuLevel = strength.asuLevel,
            signalLevel = strength.level,
            bandwidthKhz = null,
            mccMncString = mccMnc
        )
    }

    @SuppressLint("NewApi")
    private fun parseCellInfoTdscdma(info: CellInfoTdscdma): CellTowerData {
        val identity = info.cellIdentity
        val strength = info.cellSignalStrength

        val cpid = sanitizeInt(identity.cpid)
        val lac = sanitizeInt(identity.lac)
        val cid = sanitizeLong(identity.cid.toLong())
        val uarfcn = sanitizeInt(identity.uarfcn)
        val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mcc = identity.mccString
            val mnc = identity.mncString
            if (mcc != null && mnc != null) "$mcc-$mnc" else null
        } else null

        return CellTowerData(
            cellType = CellType.TDSCDMA,
            isRegistered = info.isRegistered,
            pci = cpid,
            tac = lac,
            ci = cid,
            earfcnOrArfcn = uarfcn,
            rsrpDbm = sanitizeInt(strength.dbm),
            rsrqDb = null,
            rssnrDb = null,
            cqi = null,
            asuLevel = strength.asuLevel,
            signalLevel = strength.level,
            bandwidthKhz = null,
            mccMncString = mccMnc
        )
    }

    private fun parseCellInfoCdma(info: CellInfoCdma): CellTowerData {
        val identity = info.cellIdentity
        val strength = info.cellSignalStrength

        val basestationId = sanitizeInt(identity.basestationId)
        val networkId = sanitizeInt(identity.networkId)
        val systemId = sanitizeLong(identity.systemId.toLong())

        return CellTowerData(
            cellType = CellType.CDMA,
            isRegistered = info.isRegistered,
            pci = basestationId,
            tac = networkId,
            ci = systemId,
            earfcnOrArfcn = null,
            rsrpDbm = sanitizeInt(strength.dbm),
            rsrqDb = null,
            rssnrDb = null,
            cqi = null,
            asuLevel = strength.asuLevel,
            signalLevel = strength.level,
            bandwidthKhz = null,
            mccMncString = null
        )
    }

    companion object {
        const val UNAVAILABLE_INT: Int = Int.MAX_VALUE
        const val UNAVAILABLE_LONG: Long = Long.MAX_VALUE

        fun sanitizeInt(value: Int): Int? {
            return if (value == UNAVAILABLE_INT || value == -1 || value == CellInfo.UNAVAILABLE) null else value
        }

        fun sanitizeLong(value: Long): Long? {
            return if (value == UNAVAILABLE_LONG || value == -1L || value == CellInfo.UNAVAILABLE_LONG) null else value
        }
    }
}

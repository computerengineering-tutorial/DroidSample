package com.example.droidsample.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.InetAddress
import java.nio.ByteOrder

/**
 * Standard Wi-Fi frequency bands.
 */
enum class WifiBand(val displayName: String) {
    BAND_2_4_GHZ("2.4 GHz"),
    BAND_5_GHZ("5 GHz"),
    BAND_6_GHZ("6 GHz (Wi-Fi 6E/7)"),
    BAND_60_GHZ("60 GHz (WiGig)"),
    UNKNOWN("Unknown")
}

/**
 * Wi-Fi generational standards.
 */
enum class WifiStandardLabel(val standardName: String, val generation: String) {
    WIFI_7("802.11be", "Wi-Fi 7"),
    WIFI_6E("802.11ax (6 GHz)", "Wi-Fi 6E"),
    WIFI_6("802.11ax", "Wi-Fi 6"),
    WIFI_5("802.11ac", "Wi-Fi 5"),
    WIFI_4("802.11n", "Wi-Fi 4"),
    LEGACY("802.11a/b/g", "Legacy Wi-Fi"),
    UNKNOWN("Unknown", "Unknown")
}

/**
 * Comprehensive details regarding the currently connected Wi-Fi network.
 */
data class WifiDetails(
    val isWifiEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val ssid: String = "<Not Connected>",
    val bssid: String = "00:00:00:00:00:00",
    val rssiDbm: Int = -127,
    val signalLevel: Int = 0, // 0 to 4 (5 levels)
    val signalPercentage: Int = 0, // 0 to 100%
    val linkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val band: WifiBand = WifiBand.UNKNOWN,
    val channelWidth: String = "Unknown",
    val wifiStandard: WifiStandardLabel = WifiStandardLabel.UNKNOWN,
    val ipAddress: String = "0.0.0.0",
    val macAddress: String = "02:00:00:00:00:00",
    val isHiddenSsid: Boolean = false
)

/**
 * Inspects Wi-Fi network parameters using [WifiManager] and [NetworkCapabilities].
 */
class WifiInfoManager(context: Context) {

    private val applicationContext = context.applicationContext
    private val wifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Checks if Wi-Fi hardware adapter is currently enabled on the device.
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled == true
    }

    /**
     * Retrieves the current [WifiDetails] snapshot.
     */
    @Suppress("DEPRECATION")
    fun getWifiDetails(): WifiDetails {
        val wm = wifiManager ?: return WifiDetails(isWifiEnabled = false)
        val isEnabled = wm.isWifiEnabled

        if (!isEnabled) {
            return WifiDetails(isWifiEnabled = false, isConnected = false)
        }

        // Attempt retrieval via NetworkCapabilities transportInfo on API 29+
        val wifiInfo = getActiveWifiInfo() ?: wm.connectionInfo

        if (wifiInfo == null || wifiInfo.networkId == -1) {
            return WifiDetails(
                isWifiEnabled = true,
                isConnected = false,
                ssid = "<Not Connected>"
            )
        }

        val rawSsid = wifiInfo.ssid ?: "<Unknown SSID>"
        val cleanSsid = cleanSsid(rawSsid)
        val bssid = wifiInfo.bssid ?: "02:00:00:00:00:00"
        val rssi = wifiInfo.rssi
        val signalLevel = calculateSignalLevel(rssi, 5)
        val signalPercent = calculateSignalPercentage(rssi)
        val linkSpeed = wifiInfo.linkSpeed
        val frequency = wifiInfo.frequency
        val band = determineBand(frequency)
        val standard = determineStandard(wifiInfo, band)
        val channelWidth = determineChannelWidth(wifiInfo)

        var txSpeed = linkSpeed
        var rxSpeed = linkSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            txSpeed = wifiInfo.txLinkSpeedMbps
            rxSpeed = wifiInfo.rxLinkSpeedMbps
        }

        val formattedIp = formatIpAddress(wifiInfo.ipAddress)
        val mac = wifiInfo.macAddress ?: "02:00:00:00:00:00"
        val isHidden = wifiInfo.hiddenSSID

        return WifiDetails(
            isWifiEnabled = true,
            isConnected = true,
            ssid = cleanSsid,
            bssid = bssid,
            rssiDbm = rssi,
            signalLevel = signalLevel,
            signalPercentage = signalPercent,
            linkSpeedMbps = linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            frequencyMhz = frequency,
            band = band,
            channelWidth = channelWidth,
            wifiStandard = standard,
            ipAddress = formattedIp,
            macAddress = mac,
            isHiddenSsid = isHidden
        )
    }

    private fun getActiveWifiInfo(): WifiInfo? {
        val cm = connectivityManager ?: return null
        val activeNetwork = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                return transportInfo
            }
        }
        return null
    }

    private fun cleanSsid(ssid: String): String {
        return if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length >= 2) {
            ssid.substring(1, ssid.length - 1)
        } else if (ssid == "<unknown ssid>" || ssid == WifiManager.UNKNOWN_SSID) {
            "Hidden or Restricted (Location Permission Required)"
        } else {
            ssid
        }
    }

    private fun calculateSignalLevel(rssi: Int, numLevels: Int): Int {
        val wm = wifiManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            wm.calculateSignalLevel(rssi)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, numLevels)
        }
    }

    private fun calculateSignalPercentage(rssi: Int): Int {
        // Typical Wi-Fi RSSI ranges from -100 dBm (0%) to -50 dBm (100%)
        val minRssi = -100
        val maxRssi = -50
        return when {
            rssi <= minRssi -> 0
            rssi >= maxRssi -> 100
            else -> ((rssi - minRssi) * 100) / (maxRssi - minRssi)
        }
    }

    private fun determineBand(frequencyMhz: Int): WifiBand {
        return when (frequencyMhz) {
            in 2400..2495 -> WifiBand.BAND_2_4_GHZ
            in 5150..5895 -> WifiBand.BAND_5_GHZ
            in 5925..7125 -> WifiBand.BAND_6_GHZ
            in 58320..70200 -> WifiBand.BAND_60_GHZ
            else -> WifiBand.UNKNOWN
        }
    }

    private fun determineStandard(wifiInfo: WifiInfo, band: WifiBand): WifiStandardLabel {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo.wifiStandard) {
                8 -> return WifiStandardLabel.WIFI_7 // ScanResult.WIFI_STANDARD_11BE
                6 -> { // ScanResult.WIFI_STANDARD_11AX
                    return if (band == WifiBand.BAND_6_GHZ) {
                        WifiStandardLabel.WIFI_6E
                    } else {
                        WifiStandardLabel.WIFI_6
                    }
                }
                5 -> return WifiStandardLabel.WIFI_5 // ScanResult.WIFI_STANDARD_11AC
                4 -> return WifiStandardLabel.WIFI_4 // ScanResult.WIFI_STANDARD_11N
                1 -> return WifiStandardLabel.LEGACY // ScanResult.WIFI_STANDARD_LEGACY
            }
        }

        // Fallback standard inference if API < 30 or standard is 0
        return when (band) {
            WifiBand.BAND_6_GHZ -> WifiStandardLabel.WIFI_6E
            WifiBand.BAND_5_GHZ -> {
                if (wifiInfo.linkSpeed > 400) WifiStandardLabel.WIFI_5 else WifiStandardLabel.WIFI_4
            }
            WifiBand.BAND_2_4_GHZ -> WifiStandardLabel.WIFI_4
            else -> WifiStandardLabel.UNKNOWN
        }
    }

    private fun determineChannelWidth(wifiInfo: WifiInfo): String {
        // Channel width inspection
        val freq = wifiInfo.frequency
        return when {
            freq in 5925..7125 -> "160 / 320 MHz"
            freq in 5150..5895 -> "80 / 160 MHz"
            freq in 2400..2495 -> "20 / 40 MHz"
            else -> "Auto"
        }
    }

    private fun formatIpAddress(ipInt: Int): String {
        if (ipInt == 0) return "0.0.0.0"
        return try {
            val byteOrder = ByteOrder.nativeOrder()
            val bytes = if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                byteArrayOf(
                    (ipInt and 0xFF).toByte(),
                    ((ipInt shr 8) and 0xFF).toByte(),
                    ((ipInt shr 16) and 0xFF).toByte(),
                    ((ipInt shr 24) and 0xFF).toByte()
                )
            } else {
                byteArrayOf(
                    ((ipInt shr 24) and 0xFF).toByte(),
                    ((ipInt shr 16) and 0xFF).toByte(),
                    ((ipInt shr 8) and 0xFF).toByte(),
                    (ipInt and 0xFF).toByte()
                )
            }
            InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }
}

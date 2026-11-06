package com.example.droidsample.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

/**
 * Details of an individual IP address bound to a network adapter.
 */
data class IpAddressInfo(
    val address: String,
    val prefixLength: Short,
    val isIpv4: Boolean,
    val isLoopback: Boolean,
    val isLinkLocal: Boolean,
    val subnetMask: String = ""
)

/**
 * Inspection model of a physical, virtual, or cellular network interface.
 */
data class NetworkAdapterInfo(
    val name: String,
    val displayName: String,
    val mtu: Int,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isPointToPoint: Boolean,
    val supportsMulticast: Boolean,
    val isVirtual: Boolean,
    val macAddress: String,
    val ipAddresses: List<IpAddressInfo>
)

/**
 * Enumerates and inspects system network interfaces and IP configurations.
 */
object NetworkInterfaceInspector {

    /**
     * Enumerates all active and inactive network interfaces on the device.
     */
    fun getNetworkInterfaces(): List<NetworkAdapterInfo> {
        val result = mutableListOf<NetworkAdapterInfo>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in Collections.list(interfaces)) {
                val name = intf.name ?: "unknown"
                val displayName = intf.displayName ?: name
                val mtu = try { intf.mtu } catch (_: Exception) { 0 }
                val isUp = try { intf.isUp } catch (_: Exception) { false }
                val isLoopback = try { intf.isLoopback } catch (_: Exception) { false }
                val isPtp = try { intf.isPointToPoint } catch (_: Exception) { false }
                val supportsMulticast = try { intf.supportsMulticast() } catch (_: Exception) { false }
                val isVirtual = try { intf.isVirtual } catch (_: Exception) { false }
                val mac = try { formatMacAddress(intf.hardwareAddress) } catch (_: Exception) { "Unavailable" }

                val ipList = mutableListOf<IpAddressInfo>()
                try {
                    for (addr in intf.interfaceAddresses) {
                        val inet = addr.address
                        val isIpv4 = inet is Inet4Address
                        val prefix = addr.networkPrefixLength
                        val mask = if (isIpv4) prefixLengthToSubnetMask(prefix.toInt()) else ""
                        ipList.add(
                            IpAddressInfo(
                                address = inet.hostAddress ?: "",
                                prefixLength = prefix,
                                isIpv4 = isIpv4,
                                isLoopback = inet.isLoopbackAddress,
                                isLinkLocal = inet.isLinkLocalAddress,
                                subnetMask = mask
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Fallback to inetAddresses if interfaceAddresses throws
                    for (inet in Collections.list(intf.inetAddresses)) {
                        val isIpv4 = inet is Inet4Address
                        ipList.add(
                            IpAddressInfo(
                                address = inet.hostAddress ?: "",
                                prefixLength = (if (isIpv4) 24 else 64).toShort(),
                                isIpv4 = isIpv4,
                                isLoopback = inet.isLoopbackAddress,
                                isLinkLocal = inet.isLinkLocalAddress,
                                subnetMask = if (isIpv4) "255.255.255.0" else ""
                            )
                        )
                    }
                }

                result.add(
                    NetworkAdapterInfo(
                        name = name,
                        displayName = displayName,
                        mtu = mtu,
                        isUp = isUp,
                        isLoopback = isLoopback,
                        isPointToPoint = isPtp,
                        supportsMulticast = supportsMulticast,
                        isVirtual = isVirtual,
                        macAddress = mac,
                        ipAddresses = ipList
                    )
                )
            }
        } catch (_: Exception) {
            // Return any collected interfaces
        }

        // Sort so active, non-loopback interfaces (e.g. wlan0, rmnet) appear first
        return result.sortedWith(
            compareByDescending<NetworkAdapterInfo> { it.isUp && !it.isLoopback }
                .thenBy { it.name }
        )
    }

    /**
     * Formats MAC hardware bytes into standard hex notation (e.g., `aa:bb:cc:dd:ee:ff`).
     */
    fun formatMacAddress(mac: ByteArray?): String {
        if (mac == null || mac.isEmpty()) return "Unavailable"
        val sb = StringBuilder()
        for (i in mac.indices) {
            sb.append(String.format(Locale.US, "%02x", mac[i]))
            if (i < mac.size - 1) sb.append(":")
        }
        return sb.toString()
    }

    /**
     * Validates whether a string is a standard IPv4 address (e.g. `192.168.1.1`).
     */
    fun isIpv4(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 && (part == "0" || !part.startsWith("0")) } ?: false
        }
    }

    /**
     * Validates whether a string is a valid IPv6 address.
     */
    fun isIpv6(ip: String): Boolean {
        if (ip.isBlank()) return false
        val cleanIp = if (ip.contains("%")) ip.substringBefore("%") else ip
        return try {
            val addr = InetAddress.getByName(cleanIp)
            addr is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Converts a CIDR prefix length (0-32) to an IPv4 dotted-decimal subnet mask.
     */
    fun prefixLengthToSubnetMask(prefix: Int): String {
        if (prefix !in 0..32) return "255.255.255.0"
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val b1 = (mask ushr 24) and 0xFF
        val b2 = (mask ushr 16) and 0xFF
        val b3 = (mask ushr 8) and 0xFF
        val b4 = mask and 0xFF
        return "$b1.$b2.$b3.$b4"
    }

    /**
     * Computes the network address and broadcast address for a given IPv4 and prefix length.
     * Returns Pair(networkAddress, broadcastAddress).
     */
    fun calculateSubnetRange(ipv4: String, prefix: Int): Pair<String, String> {
        if (!isIpv4(ipv4) || prefix !in 0..32) return Pair(ipv4, ipv4)

        val parts = ipv4.split(".").map { it.toLong() }
        val ipVal = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]

        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val networkVal = ipVal and mask
        val wildcard = mask.inv() and 0xFFFFFFFFL
        val broadcastVal = networkVal or wildcard

        return Pair(longToIpv4(networkVal), longToIpv4(broadcastVal))
    }

    private fun longToIpv4(value: Long): String {
        val b1 = (value ushr 24) and 0xFF
        val b2 = (value ushr 16) and 0xFF
        val b3 = (value ushr 8) and 0xFF
        val b4 = value and 0xFF
        return "$b1.$b2.$b3.$b4"
    }
}

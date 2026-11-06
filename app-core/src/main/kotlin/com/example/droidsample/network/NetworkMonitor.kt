package com.example.droidsample.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.RouteInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Transport types for active networks.
 */
enum class TransportType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    BLUETOOTH,
    LOWPAN,
    USB,
    UNKNOWN
}

/**
 * Information extracted from [NetworkCapabilities].
 */
data class NetworkCapabilitiesInfo(
    val hasInternet: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = true,
    val isVpn: Boolean = false,
    val downstreamBandwidthKbps: Int = 0,
    val upstreamBandwidthKbps: Int = 0,
    val transports: List<TransportType> = emptyList()
)

/**
 * Information extracted from [LinkProperties].
 */
data class LinkPropertiesInfo(
    val interfaceName: String? = null,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val gateway: String? = null,
    val mtu: Int = 0,
    val domains: String? = null
)

/**
 * Snapshot of current network connectivity status.
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val activeTransport: TransportType = TransportType.UNKNOWN,
    val capabilities: NetworkCapabilitiesInfo = NetworkCapabilitiesInfo(),
    val linkProperties: LinkPropertiesInfo = LinkPropertiesInfo(),
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Monitors device connectivity status using [ConnectivityManager] and [NetworkCallback].
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Cold Flow that emits [NetworkState] changes in real-time.
     */
    val networkState: Flow<NetworkState> = callbackFlow {
        if (connectivityManager == null) {
            trySend(NetworkState(isConnected = false))
            close()
            return@callbackFlow
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var currentCapabilities: NetworkCapabilities? = null
        var currentLinkProperties: LinkProperties? = null
        var isNetworkActive = false

        fun emitCurrentState() {
            val caps = currentCapabilities
            val links = currentLinkProperties

            val capsInfo = caps?.let { parseCapabilities(it) } ?: NetworkCapabilitiesInfo()
            val linkInfo = links?.let { parseLinkProperties(it) } ?: LinkPropertiesInfo()

            val primaryTransport = capsInfo.transports.firstOrNull() ?: TransportType.UNKNOWN

            trySend(
                NetworkState(
                    isConnected = isNetworkActive,
                    activeTransport = primaryTransport,
                    capabilities = capsInfo,
                    linkProperties = linkInfo,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }

        // Initialize with current active network if available
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            isNetworkActive = true
            currentCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            currentLinkProperties = connectivityManager.getLinkProperties(activeNetwork)
            emitCurrentState()
        } else {
            emitCurrentState()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkActive = true
                currentCapabilities = connectivityManager.getNetworkCapabilities(network)
                currentLinkProperties = connectivityManager.getLinkProperties(network)
                emitCurrentState()
            }

            override fun onLost(network: Network) {
                // Check if there is still an active network remaining
                val currentActive = connectivityManager.activeNetwork
                if (currentActive == null || currentActive == network) {
                    isNetworkActive = false
                    currentCapabilities = null
                    currentLinkProperties = null
                } else {
                    currentCapabilities = connectivityManager.getNetworkCapabilities(currentActive)
                    currentLinkProperties = connectivityManager.getLinkProperties(currentActive)
                }
                emitCurrentState()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                currentCapabilities = networkCapabilities
                emitCurrentState()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                currentLinkProperties = linkProperties
                emitCurrentState()
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                // Ignore unregister exceptions on teardown
            }
        }
    }.conflate()

    /**
     * Synchronously read the current [NetworkState] snapshot.
     */
    fun getCurrentNetworkState(): NetworkState {
        val cm = connectivityManager ?: return NetworkState(isConnected = false)
        val activeNetwork = cm.activeNetwork ?: return NetworkState(isConnected = false)
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val links = cm.getLinkProperties(activeNetwork)

        val capsInfo = caps?.let { parseCapabilities(it) } ?: NetworkCapabilitiesInfo()
        val linkInfo = links?.let { parseLinkProperties(it) } ?: LinkPropertiesInfo()

        return NetworkState(
            isConnected = true,
            activeTransport = capsInfo.transports.firstOrNull() ?: TransportType.UNKNOWN,
            capabilities = capsInfo,
            linkProperties = linkInfo,
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun parseCapabilities(capabilities: NetworkCapabilities): NetworkCapabilitiesInfo {
        val transports = mutableListOf<TransportType>()
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            transports.add(TransportType.WIFI)
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            transports.add(TransportType.CELLULAR)
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            transports.add(TransportType.ETHERNET)
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            transports.add(TransportType.VPN)
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            transports.add(TransportType.BLUETOOTH)
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) {
            transports.add(TransportType.LOWPAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) {
            transports.add(TransportType.USB)
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isNotMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val isNotVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

        return NetworkCapabilitiesInfo(
            hasInternet = hasInternet,
            isValidated = isValidated,
            isMetered = !isNotMetered,
            isVpn = !isNotVpn,
            downstreamBandwidthKbps = capabilities.linkDownstreamBandwidthKbps,
            upstreamBandwidthKbps = capabilities.linkUpstreamBandwidthKbps,
            transports = transports
        )
    }

    private fun parseLinkProperties(linkProperties: LinkProperties): LinkPropertiesInfo {
        val ipv4List = mutableListOf<String>()
        val ipv6List = mutableListOf<String>()

        for (la in linkProperties.linkAddresses) {
            val addr = la.address
            if (addr is Inet4Address) {
                ipv4List.add("${addr.hostAddress}/${la.prefixLength}")
            } else if (addr is Inet6Address) {
                ipv6List.add("${addr.hostAddress}/${la.prefixLength}")
            }
        }

        val dnsList = linkProperties.dnsServers.mapNotNull { it.hostAddress }

        // Find default gateway from routes (route with no destination or 0.0.0.0/0 destination)
        var gateway: String? = null
        for (route in linkProperties.routes) {
            if (route.isDefaultRoute && route.gateway != null) {
                gateway = route.gateway?.hostAddress
                break
            }
        }

        val mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            linkProperties.mtu
        } else {
            0
        }

        return LinkPropertiesInfo(
            interfaceName = linkProperties.interfaceName,
            ipv4Addresses = ipv4List,
            ipv6Addresses = ipv6List,
            dnsServers = dnsList,
            gateway = gateway,
            mtu = mtu,
            domains = linkProperties.domains
        )
    }
}

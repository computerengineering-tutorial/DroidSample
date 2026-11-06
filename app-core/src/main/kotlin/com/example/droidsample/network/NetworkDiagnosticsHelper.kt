package com.example.droidsample.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.system.measureTimeMillis

/**
 * Result of a DNS resolution benchmark.
 */
data class DnsBenchmarkResult(
    val hostname: String,
    val resolvedIps: List<String>,
    val durationMs: Long,
    val isSuccessful: Boolean,
    val errorMessage: String? = null
)

/**
 * Result of a TCP socket reachability test.
 */
data class SocketPingResult(
    val host: String,
    val port: Int,
    val latencyMs: Long,
    val isReachable: Boolean,
    val errorMessage: String? = null
)

/**
 * Result of an HTTP connectivity / captive portal probe.
 */
data class HttpProbeResult(
    val url: String,
    val statusCode: Int,
    val latencyMs: Long,
    val isCaptivePortal: Boolean,
    val isSuccess: Boolean,
    val message: String
)

/**
 * Summary of multiple sequential ping benchmarks.
 */
data class LatencySeriesSummary(
    val host: String,
    val attempts: Int,
    val successful: Int,
    val minMs: Long,
    val maxMs: Long,
    val avgMs: Double,
    val lossPercent: Double,
    val samples: List<Long>
)

/**
 * Executes DNS, TCP socket reachability, and HTTP connectivity diagnostics.
 */
object NetworkDiagnosticsHelper {

    private const val DEFAULT_SOCKET_TIMEOUT_MS = 3000
    private const val DEFAULT_HTTP_TIMEOUT_MS = 4000
    private const val CAPTIVE_PORTAL_URL = "http://connectivitycheck.gstatic.com/generate_204"

    /**
     * Measures DNS resolution time for a specified hostname.
     */
    suspend fun benchmarkDns(hostname: String): DnsBenchmarkResult = withContext(Dispatchers.IO) {
        var resolvedList = emptyList<String>()
        var errorMsg: String? = null
        var isSuccess = false

        val duration = measureTimeMillis {
            try {
                val addresses = InetAddress.getAllByName(hostname)
                resolvedList = addresses.mapNotNull { it.hostAddress }
                isSuccess = resolvedList.isNotEmpty()
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                isSuccess = false
            }
        }

        DnsBenchmarkResult(
            hostname = hostname,
            resolvedIps = resolvedList,
            durationMs = duration,
            isSuccessful = isSuccess,
            errorMessage = errorMsg
        )
    }

    /**
     * Measures TCP handshake latency to the given host and port.
     */
    suspend fun benchmarkTcpSocket(
        host: String,
        port: Int = 443,
        timeoutMs: Int = DEFAULT_SOCKET_TIMEOUT_MS
    ): SocketPingResult = withContext(Dispatchers.IO) {
        var isReachable = false
        var errorMsg: String? = null
        var latencyMs: Long = 0

        try {
            val socket = Socket()
            val socketAddress = InetSocketAddress(host, port)
            val start = System.currentTimeMillis()
            socket.connect(socketAddress, timeoutMs)
            latencyMs = System.currentTimeMillis() - start
            isReachable = true
            socket.close()
        } catch (e: Exception) {
            errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            latencyMs = timeoutMs.toLong()
            isReachable = false
        }

        SocketPingResult(
            host = host,
            port = port,
            latencyMs = latencyMs,
            isReachable = isReachable,
            errorMessage = errorMsg
        )
    }

    /**
     * Executes a series of TCP socket pings to calculate latency distribution and packet loss.
     */
    suspend fun runPingSeries(
        host: String,
        port: Int = 443,
        count: Int = 5,
        timeoutMs: Int = DEFAULT_SOCKET_TIMEOUT_MS
    ): LatencySeriesSummary = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Long>()
        var successCount = 0

        for (i in 0 until count) {
            val result = benchmarkTcpSocket(host, port, timeoutMs)
            if (result.isReachable) {
                samples.add(result.latencyMs)
                successCount++
            } else {
                samples.add(-1L) // Indicates loss
            }
        }

        val successfulSamples = samples.filter { it >= 0 }
        val min = successfulSamples.minOrNull() ?: 0L
        val max = successfulSamples.maxOrNull() ?: 0L
        val avg = if (successfulSamples.isNotEmpty()) successfulSamples.average() else 0.0
        val loss = ((count - successCount).toDouble() / count.toDouble()) * 100.0

        LatencySeriesSummary(
            host = host,
            attempts = count,
            successful = successCount,
            minMs = min,
            maxMs = max,
            avgMs = avg,
            lossPercent = loss,
            samples = samples
        )
    }

    /**
     * Probes HTTP connectivity and evaluates captive portal existence.
     */
    suspend fun probeHttpConnectivity(
        targetUrl: String = CAPTIVE_PORTAL_URL,
        timeoutMs: Int = DEFAULT_HTTP_TIMEOUT_MS
    ): HttpProbeResult = withContext(Dispatchers.IO) {
        var statusCode = -1
        var latencyMs: Long = 0
        var isCaptive = false
        var isSuccess = false
        var message = ""

        try {
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.requestMethod = "GET"

            val start = System.currentTimeMillis()
            connection.connect()
            statusCode = connection.responseCode
            latencyMs = System.currentTimeMillis() - start

            when (statusCode) {
                HttpURLConnection.HTTP_NO_CONTENT -> {
                    // Standard 204: Internet connection verified, no captive portal
                    isSuccess = true
                    isCaptive = false
                    message = "Internet Verified (No Captive Portal)"
                }
                HttpURLConnection.HTTP_OK -> {
                    // 200 OK on generate_204 URL means a captive portal intercepted the page
                    if (targetUrl == CAPTIVE_PORTAL_URL) {
                        isSuccess = true
                        isCaptive = true
                        message = "Captive Portal Detected (Login Required)"
                    } else {
                        isSuccess = true
                        isCaptive = false
                        message = "HTTP 200 OK"
                    }
                }
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                307, 308 -> {
                    isSuccess = true
                    isCaptive = true
                    message = "Captive Portal Redirect Detected (${statusCode})"
                }
                else -> {
                    isSuccess = false
                    isCaptive = false
                    message = "HTTP Response Code: $statusCode"
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            latencyMs = timeoutMs.toLong()
            isSuccess = false
            isCaptive = false
            message = "Network probe failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }

        HttpProbeResult(
            url = targetUrl,
            statusCode = statusCode,
            latencyMs = latencyMs,
            isCaptivePortal = isCaptive,
            isSuccess = isSuccess,
            message = message
        )
    }
}

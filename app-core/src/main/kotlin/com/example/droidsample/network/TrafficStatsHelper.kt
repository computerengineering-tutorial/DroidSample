package com.example.droidsample.network

import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Snapshot of traffic bytes and packets.
 */
data class TrafficSnapshot(
    val totalRxBytes: Long = 0,
    val totalTxBytes: Long = 0,
    val totalRxPackets: Long = 0,
    val totalTxPackets: Long = 0,
    val mobileRxBytes: Long = 0,
    val mobileTxBytes: Long = 0,
    val mobileRxPackets: Long = 0,
    val mobileTxPackets: Long = 0,
    val uidRxBytes: Long = 0,
    val uidTxBytes: Long = 0,
    val uidRxPackets: Long = 0,
    val uidTxPackets: Long = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Real-time transfer rates computed from two consecutive snapshots.
 */
data class RealTimeTrafficRate(
    val rxBytesPerSec: Long = 0,
    val txBytesPerSec: Long = 0,
    val totalRxBytes: Long = 0,
    val totalTxBytes: Long = 0,
    val uidRxBytesPerSec: Long = 0,
    val uidTxBytesPerSec: Long = 0,
    val uidTotalRxBytes: Long = 0,
    val uidTotalTxBytes: Long = 0,
    val mobileRxBytes: Long = 0,
    val mobileTxBytes: Long = 0,
    val rxSpeedFormatted: String = "0 B/s",
    val txSpeedFormatted: String = "0 B/s",
    val uidRxSpeedFormatted: String = "0 B/s",
    val uidTxSpeedFormatted: String = "0 B/s"
)

/**
 * Helper class for monitoring cumulative traffic statistics and real-time bandwidth.
 */
object TrafficStatsHelper {

    /**
     * Reads current cumulative [TrafficSnapshot] from system [TrafficStats].
     */
    fun getSnapshot(): TrafficSnapshot {
        val myUid = Process.myUid()

        val totalRx = sanitize(TrafficStats.getTotalRxBytes())
        val totalTx = sanitize(TrafficStats.getTotalTxBytes())
        val totalRxPkt = sanitize(TrafficStats.getTotalRxPackets())
        val totalTxPkt = sanitize(TrafficStats.getTotalTxPackets())

        val mobileRx = sanitize(TrafficStats.getMobileRxBytes())
        val mobileTx = sanitize(TrafficStats.getMobileTxBytes())
        val mobileRxPkt = sanitize(TrafficStats.getMobileRxPackets())
        val mobileTxPkt = sanitize(TrafficStats.getMobileTxPackets())

        val uidRx = sanitize(TrafficStats.getUidRxBytes(myUid))
        val uidTx = sanitize(TrafficStats.getUidTxBytes(myUid))
        val uidRxPkt = sanitize(TrafficStats.getUidRxPackets(myUid))
        val uidTxPkt = sanitize(TrafficStats.getUidTxPackets(myUid))

        return TrafficSnapshot(
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            totalRxPackets = totalRxPkt,
            totalTxPackets = totalTxPkt,
            mobileRxBytes = mobileRx,
            mobileTxBytes = mobileTx,
            mobileRxPackets = mobileRxPkt,
            mobileTxPackets = mobileTxPkt,
            uidRxBytes = uidRx,
            uidTxBytes = uidTx,
            uidRxPackets = uidRxPkt,
            uidTxPackets = uidTxPkt,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Periodically polls traffic counters to compute real-time bandwidth rate.
     *
     * @param pollIntervalMs Interval between ticks in milliseconds.
     */
    fun observeTrafficRate(pollIntervalMs: Long = 1000L): Flow<RealTimeTrafficRate> = flow {
        var previous = getSnapshot()

        while (true) {
            delay(pollIntervalMs)
            val current = getSnapshot()
            val timeDeltaSec = (current.timestampMs - previous.timestampMs).coerceAtLeast(1) / 1000.0

            val rxDiff = (current.totalRxBytes - previous.totalRxBytes).coerceAtLeast(0)
            val txDiff = (current.totalTxBytes - previous.totalTxBytes).coerceAtLeast(0)
            val uidRxDiff = (current.uidRxBytes - previous.uidRxBytes).coerceAtLeast(0)
            val uidTxDiff = (current.uidTxBytes - previous.uidTxBytes).coerceAtLeast(0)

            val rxRate = (rxDiff / timeDeltaSec).toLong()
            val txRate = (txDiff / timeDeltaSec).toLong()
            val uidRxRate = (uidRxDiff / timeDeltaSec).toLong()
            val uidTxRate = (uidTxDiff / timeDeltaSec).toLong()

            emit(
                RealTimeTrafficRate(
                    rxBytesPerSec = rxRate,
                    txBytesPerSec = txRate,
                    totalRxBytes = current.totalRxBytes,
                    totalTxBytes = current.totalTxBytes,
                    uidRxBytesPerSec = uidRxRate,
                    uidTxBytesPerSec = uidTxRate,
                    uidTotalRxBytes = current.uidRxBytes,
                    uidTotalTxBytes = current.uidTxBytes,
                    mobileRxBytes = current.mobileRxBytes,
                    mobileTxBytes = current.mobileTxBytes,
                    rxSpeedFormatted = formatBytesPerSecond(rxRate),
                    txSpeedFormatted = formatBytesPerSecond(txRate),
                    uidRxSpeedFormatted = formatBytesPerSecond(uidRxRate),
                    uidTxSpeedFormatted = formatBytesPerSecond(uidTxRate)
                )
            )

            previous = current
        }
    }

    /**
     * Replaces negative uninitialized or unsupported return values with 0.
     */
    private fun sanitize(value: Long): Long {
        return if (value == TrafficStats.UNSUPPORTED.toLong() || value < 0) 0L else value
    }

    /**
     * Formats a byte quantity into human-readable notation (B, KB, MB, GB, TB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"

        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)

        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)

        val gb = mb / 1024.0
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)

        val tb = gb / 1024.0
        return String.format(Locale.US, "%.2f TB", tb)
    }

    /**
     * Formats bytes per second into standard speed notation (B/s, KB/s, MB/s, GB/s).
     */
    fun formatBytesPerSecond(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 B/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    /**
     * Formats bits per second into network throughput notation (bps, Kbps, Mbps, Gbps).
     */
    fun formatBitsPerSecond(bps: Long): String {
        if (bps <= 0) return "0 bps"
        if (bps < 1000) return "$bps bps"

        val kbps = bps / 1000.0
        if (kbps < 1000) return String.format(Locale.US, "%.2f Kbps", kbps)

        val mbps = kbps / 1000.0
        if (mbps < 1000) return String.format(Locale.US, "%.2f Mbps", mbps)

        val gbps = mbps / 1000.0
        return String.format(Locale.US, "%.2f Gbps", gbps)
    }

    /**
     * Formats packet count with thousands separators.
     */
    fun formatPackets(packets: Long): String {
        val safe = if (packets < 0) 0L else packets
        return String.format(Locale.US, "%,d pkts", safe)
    }
}

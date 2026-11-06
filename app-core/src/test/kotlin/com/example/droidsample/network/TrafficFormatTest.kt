package com.example.droidsample.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFormatTest {

    @Test
    fun testFormatBytesZeroAndNegative() {
        assertEquals("0 B", TrafficStatsHelper.formatBytes(0))
        assertEquals("0 B", TrafficStatsHelper.formatBytes(-500))
    }

    @Test
    fun testFormatBytesUnits() {
        assertEquals("500 B", TrafficStatsHelper.formatBytes(500))
        assertEquals("1.00 KB", TrafficStatsHelper.formatBytes(1024))
        assertEquals("1.50 KB", TrafficStatsHelper.formatBytes(1536))
        assertEquals("1.00 MB", TrafficStatsHelper.formatBytes(1024 * 1024))
        assertEquals("2.50 MB", TrafficStatsHelper.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.00 GB", TrafficStatsHelper.formatBytes(1024L * 1024 * 1024))
        assertEquals("1.00 TB", TrafficStatsHelper.formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun testFormatBytesPerSecond() {
        assertEquals("0 B/s", TrafficStatsHelper.formatBytesPerSecond(0))
        assertEquals("0 B/s", TrafficStatsHelper.formatBytesPerSecond(-100))
        assertEquals("512 B/s", TrafficStatsHelper.formatBytesPerSecond(512))
        assertEquals("1.00 KB/s", TrafficStatsHelper.formatBytesPerSecond(1024))
        assertEquals("10.00 MB/s", TrafficStatsHelper.formatBytesPerSecond(10L * 1024 * 1024))
    }

    @Test
    fun testFormatBitsPerSecond() {
        assertEquals("0 bps", TrafficStatsHelper.formatBitsPerSecond(0))
        assertEquals("0 bps", TrafficStatsHelper.formatBitsPerSecond(-50))
        assertEquals("750 bps", TrafficStatsHelper.formatBitsPerSecond(750))
        assertEquals("1.00 Kbps", TrafficStatsHelper.formatBitsPerSecond(1000))
        assertEquals("100.00 Mbps", TrafficStatsHelper.formatBitsPerSecond(100_000_000L))
        assertEquals("1.00 Gbps", TrafficStatsHelper.formatBitsPerSecond(1_000_000_000L))
    }

    @Test
    fun testFormatPackets() {
        assertEquals("0 pkts", TrafficStatsHelper.formatPackets(0))
        assertEquals("0 pkts", TrafficStatsHelper.formatPackets(-10))
        assertEquals("1,234 pkts", TrafficStatsHelper.formatPackets(1234))
        assertEquals("1,000,000 pkts", TrafficStatsHelper.formatPackets(1_000_000))
    }
}

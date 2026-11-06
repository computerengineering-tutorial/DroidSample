package com.example.droidsample.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoTest {

    @Test
    fun testFormatFileSizeUnits() {
        assertEquals("0 B", StorageHelper.formatFileSize(0))
        assertEquals("0 B", StorageHelper.formatFileSize(-10))
        assertEquals("512.00 B", StorageHelper.formatFileSize(512))
        assertEquals("1.00 KB", StorageHelper.formatFileSize(1024))
        assertEquals("1.50 KB", StorageHelper.formatFileSize(1536))
        assertEquals("1.00 MB", StorageHelper.formatFileSize(1024L * 1024L))
        assertEquals("1.00 GB", StorageHelper.formatFileSize(1024L * 1024L * 1024L))
    }

    @Test
    fun testFormatBytesToGbAndMb() {
        val oneGb = 1024L * 1024L * 1024L
        val formattedGb = DeviceInfoProvider.formatBytesToGb(oneGb)
        assertEquals("1.00 GB", formattedGb)

        val oneMb = 1024L * 1024L
        val formattedMb = DeviceInfoProvider.formatBytesToMb(oneMb)
        assertEquals("1.0 MB", formattedMb)
    }

    @Test
    fun testStorageCapacityPercentageCalculations() {
        val total = 128L * 1024L * 1024L * 1024L // 128 GB
        val available = 32L * 1024L * 1024L * 1024L // 32 GB
        val used = total - available

        val freePct = (available.toDouble() / total.toDouble()) * 100.0
        val usedPct = (used.toDouble() / total.toDouble()) * 100.0

        val info = StorageCapacityInfo(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            freePercentage = freePct,
            usedPercentage = usedPct
        )

        assertEquals(25.0, info.freePercentage, 0.01)
        assertEquals(75.0, info.usedPercentage, 0.01)
        assertEquals(total, info.totalBytes)
        assertEquals(used, info.usedBytes)
        assertEquals(available, info.availableBytes)
    }

    @Test
    fun testRamInfoPercentageCalculations() {
        val total = 8L * 1024L * 1024L * 1024L // 8 GB
        val avail = 4L * 1024L * 1024L * 1024L // 4 GB
        val used = total - avail
        val usedPct = (used.toDouble() / total.toDouble()) * 100.0

        val ramInfo = RamInfo(
            totalMemBytes = total,
            availableMemBytes = avail,
            usedMemBytes = used,
            thresholdBytes = 500L * 1024L * 1024L,
            isLowMemory = false,
            usedPercentage = usedPct
        )

        assertEquals(50.0, ramInfo.usedPercentage, 0.01)
        assertEquals(false, ramInfo.isLowMemory)
    }
}

package com.example.droidsample.system

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating notification channel constants, group configurations,
 * foreground service bitmask parsing, and background constraint structures.
 */
class NotificationChannelTest {

    @Test
    fun testNotificationChannelIdsNotEmpty() {
        assertNotNull(NotificationHelper.CHANNEL_ID_HIGH)
        assertNotNull(NotificationHelper.CHANNEL_ID_DEFAULT)
        assertNotNull(NotificationHelper.CHANNEL_ID_LOW)
        assertNotNull(NotificationHelper.CHANNEL_ID_MIN)
        assertNotNull(NotificationHelper.CHANNEL_ID_SERVICE)
        assertNotNull(NotificationHelper.CHANNEL_ID_PROGRESS)

        assertTrue(NotificationHelper.CHANNEL_ID_HIGH.isNotBlank())
        assertTrue(NotificationHelper.CHANNEL_ID_DEFAULT.isNotBlank())
        assertTrue(NotificationHelper.CHANNEL_ID_LOW.isNotBlank())
        assertTrue(NotificationHelper.CHANNEL_ID_MIN.isNotBlank())
        assertTrue(NotificationHelper.CHANNEL_ID_SERVICE.isNotBlank())
        assertTrue(NotificationHelper.CHANNEL_ID_PROGRESS.isNotBlank())
    }

    @Test
    fun testChannelGroupsDefined() {
        assertEquals("group_system_general", NotificationHelper.GROUP_ID_GENERAL)
        assertEquals("group_system_services", NotificationHelper.GROUP_ID_SYSTEM)
    }

    @Test
    fun testForegroundServiceTypeDescriptor() {
        val noneDesc = AppForegroundService.describeServiceTypes(0)
        assertEquals("None", noneDesc)

        val syncDesc = AppForegroundService.describeServiceTypes(AppForegroundService.TYPE_DATA_SYNC)
        assertEquals("Data Sync", syncDesc)

        val multiDesc = AppForegroundService.describeServiceTypes(
            AppForegroundService.TYPE_DATA_SYNC or AppForegroundService.TYPE_LOCATION
        )
        assertTrue(multiDesc.contains("Data Sync"))
        assertTrue(multiDesc.contains("Location"))

        val allDesc = AppForegroundService.describeServiceTypes(
            AppForegroundService.TYPE_DATA_SYNC or
                    AppForegroundService.TYPE_LOCATION or
                    AppForegroundService.TYPE_CAMERA or
                    AppForegroundService.TYPE_MICROPHONE or
                    AppForegroundService.TYPE_MEDIA_PLAYBACK
        )
        assertTrue(allDesc.contains("Camera"))
        assertTrue(allDesc.contains("Microphone"))
        assertTrue(allDesc.contains("Media Playback"))
    }

    @Test
    fun testWorkSchedulerConstraintsConversion() {
        val defaultConstraints = WorkSchedulerConstraints()
        assertEquals(NetworkType.CONNECTED, defaultConstraints.networkType)
        assertFalse(defaultConstraints.requiresCharging)
        assertTrue(defaultConstraints.requiresBatteryNotLow)
        assertFalse(defaultConstraints.requiresDeviceIdle)
        assertFalse(defaultConstraints.requiresStorageNotLow)

        val customConstraints = WorkSchedulerConstraints(
            networkType = NetworkType.UNMETERED,
            requiresCharging = true,
            requiresDeviceIdle = true,
            requiresBatteryNotLow = false,
            requiresStorageNotLow = true
        )
        val androidxConstraints = customConstraints.toWorkConstraints()
        assertEquals(NetworkType.UNMETERED, androidxConstraints.requiredNetworkType)
        assertTrue(androidxConstraints.requiresCharging())
        assertFalse(androidxConstraints.requiresBatteryNotLow())
        assertTrue(androidxConstraints.requiresStorageNotLow())
    }

    @Test
    fun testSystemEventFormatting() {
        val event = SystemBroadcastEvent(
            id = 42L,
            timestamp = 1700000000000L,
            action = "android.intent.action.BATTERY_CHANGED",
            summary = "Battery 95% | AC Charger",
            category = EventCategory.BATTERY,
            extras = mapOf("level" to "95", "scale" to "100")
        )

        assertEquals(42L, event.id)
        assertEquals(EventCategory.BATTERY, event.category)
        assertEquals(2, event.extras.size)
        assertTrue(event.formattedTime.isNotBlank())
    }
}

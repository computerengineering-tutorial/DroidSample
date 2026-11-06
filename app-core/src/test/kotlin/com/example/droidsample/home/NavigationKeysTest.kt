package com.example.droidsample.home

import androidx.navigation3.runtime.NavKey
import com.example.droidsample.AppNavKey
import com.example.droidsample.AudioNavKey
import com.example.droidsample.CallingNavKey
import com.example.droidsample.CameraNavKey
import com.example.droidsample.ConnectivityNavKey
import com.example.droidsample.HomeNavKey
import com.example.droidsample.LocationNavKey
import com.example.droidsample.Main
import com.example.droidsample.MediaNavKey
import com.example.droidsample.NetworkNavKey
import com.example.droidsample.SensorsNavKey
import com.example.droidsample.SmsNavKey
import com.example.droidsample.StorageSecurityNavKey
import com.example.droidsample.SystemNavKey
import com.example.droidsample.TelephonyNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating route identity and uniqueness for [AppNavKey] destinations.
 */
class NavigationKeysTest {

    private val allKeys: List<AppNavKey> = listOf(
        HomeNavKey,
        CameraNavKey,
        AudioNavKey,
        MediaNavKey,
        TelephonyNavKey,
        CallingNavKey,
        SmsNavKey,
        LocationNavKey,
        SensorsNavKey,
        ConnectivityNavKey,
        NetworkNavKey,
        SystemNavKey,
        StorageSecurityNavKey
    )

    @Test
    fun testAllKeysImplementNavKey() {
        allKeys.forEach { key ->
            assertTrue("$key must implement NavKey", key is NavKey)
            assertTrue("$key must implement AppNavKey", key is AppNavKey)
        }
    }

    @Test
    fun testAllNavKeysAreUnique() {
        assertEquals("Expected exactly 13 unique navigation keys", 13, allKeys.size)
        val distinctCount = allKeys.distinct().size
        assertEquals("All NavKey instances must be distinct", 13, distinctCount)
    }

    @Test
    fun testLegacyMainAliasIsIdenticalToHomeNavKey() {
        assertSame("Legacy Main key alias must refer directly to HomeNavKey", HomeNavKey, Main)
    }

    @Test
    fun testKeyClassNames() {
        assertEquals("HomeNavKey", HomeNavKey::class.java.simpleName)
        assertEquals("CameraNavKey", CameraNavKey::class.java.simpleName)
        assertEquals("AudioNavKey", AudioNavKey::class.java.simpleName)
        assertEquals("MediaNavKey", MediaNavKey::class.java.simpleName)
        assertEquals("TelephonyNavKey", TelephonyNavKey::class.java.simpleName)
        assertEquals("CallingNavKey", CallingNavKey::class.java.simpleName)
        assertEquals("SmsNavKey", SmsNavKey::class.java.simpleName)
        assertEquals("LocationNavKey", LocationNavKey::class.java.simpleName)
        assertEquals("SensorsNavKey", SensorsNavKey::class.java.simpleName)
        assertEquals("ConnectivityNavKey", ConnectivityNavKey::class.java.simpleName)
        assertEquals("NetworkNavKey", NetworkNavKey::class.java.simpleName)
        assertEquals("SystemNavKey", SystemNavKey::class.java.simpleName)
        assertEquals("StorageSecurityNavKey", StorageSecurityNavKey::class.java.simpleName)
    }
}

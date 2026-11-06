package com.example.droidsample.home

import com.example.droidsample.ui.home.FeatureCatalog
import com.example.droidsample.ui.home.FeatureCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating the completeness and search behavior of [FeatureCatalog].
 */
class FeatureCatalogTest {

    @Test
    fun testAll12ModulesRegistered() {
        val modules = FeatureCatalog.modules
        assertEquals("Expected exactly 12 core native capability modules", 12, modules.size)

        // Ensure module numbers 1 to 12 are all present and sorted
        val numbers = modules.map { it.moduleNumber }
        assertEquals((1..12).toList(), numbers)
    }

    @Test
    fun testModuleIdsAndNavKeysUnique() {
        val modules = FeatureCatalog.modules
        val ids = modules.map { it.id }
        assertEquals("Module IDs must be unique", ids.distinct().size, ids.size)

        val keys = modules.map { it.navKey }
        assertEquals("Every module must map to a unique NavKey", keys.distinct().size, keys.size)
    }

    @Test
    fun testCatalogHasOver100NativeApis() {
        val totalApis = FeatureCatalog.totalApiCount()
        assertTrue("Expected catalog to contain at least 100 native Android APIs, found $totalApis", totalApis >= 100)

        FeatureCatalog.modules.forEach { module ->
            assertTrue(
                "Module ${module.name} should have at least 5 key APIs cataloged",
                module.keyApis.size >= 5
            )
            module.keyApis.forEach { api ->
                assertFalse("API name must not be blank", api.name.isBlank())
                assertFalse("API description must not be blank", api.description.isBlank())
            }
        }
    }

    @Test
    fun testCategoryFiltering() {
        val hardware = FeatureCatalog.findByCategory(FeatureCategory.HARDWARE)
        val telephony = FeatureCatalog.findByCategory(FeatureCategory.TELEPHONY_COMMS)
        val connectivity = FeatureCatalog.findByCategory(FeatureCategory.CONNECTIVITY)
        val system = FeatureCatalog.findByCategory(FeatureCategory.SYSTEM_OS)

        assertTrue(hardware.any { it.id == "camera" })
        assertTrue(hardware.any { it.id == "audio" })
        assertTrue(hardware.any { it.id == "media" })
        assertTrue(hardware.any { it.id == "sensors" })

        assertTrue(telephony.any { it.id == "telephony" })
        assertTrue(telephony.any { it.id == "calling" })
        assertTrue(telephony.any { it.id == "sms" })

        assertTrue(connectivity.any { it.id == "location" })
        assertTrue(connectivity.any { it.id == "connectivity" })
        assertTrue(connectivity.any { it.id == "network" })

        assertTrue(system.any { it.id == "system" })
        assertTrue(system.any { it.id == "storage" })

        val all = FeatureCatalog.findByCategory(FeatureCategory.ALL)
        assertEquals(12, all.size)
    }

    @Test
    fun testKeywordSearch() {
        // Search by module title
        val cameraResult = FeatureCatalog.search("Camera")
        assertTrue(cameraResult.any { it.id == "camera" })

        // Search by nested API keyword: "GNSS" should match Location
        val gnssResult = FeatureCatalog.search("GNSS")
        assertTrue(gnssResult.any { it.id == "location" })

        // Search by nested API keyword: "MediaCodec" should match Media
        val codecResult = FeatureCatalog.search("MediaCodec")
        assertTrue(codecResult.any { it.id == "media" })

        // Search by nested API keyword: "Biometric" should match Storage/Security
        val bioResult = FeatureCatalog.search("Biometric")
        assertTrue(bioResult.any { it.id == "storage" })

        // Search by non-existent keyword
        val emptyResult = FeatureCatalog.search("NonExistentTermXYZ123")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun testSearchApisGranular() {
        val matched = FeatureCatalog.searchApis("Accelerometer")
        assertTrue(matched.isNotEmpty())
        assertEquals("sensors", matched.first().first.id)
    }
}

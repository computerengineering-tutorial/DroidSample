package com.example.droidsample.telephony

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying telephony data structures, SIM subscription mapping,
 * network technology classification, and identifier audit models.
 */
class SubscriptionDataTest {

    @Test
    fun testSimSubscriptionDataSlotAndPlmnFormatting() {
        val sim1 = SimSubscriptionData(
            subscriptionId = 1,
            simSlotIndex = 0,
            displayName = "T-Mobile",
            carrierName = "T-Mobile US",
            countryIso = "US",
            mcc = "310",
            mnc = "260",
            isDataRoaming = false,
            isEmbedded = false,
            isDefaultVoice = true,
            isDefaultSms = true,
            isDefaultData = true
        )

        assertEquals("SIM 1", sim1.displaySlotNumber)
        assertEquals("310-260", sim1.plmn)
        assertTrue(sim1.isDefaultVoice)
        assertTrue(sim1.isDefaultSms)
        assertTrue(sim1.isDefaultData)
        assertFalse(sim1.isEmbedded)
        assertFalse(sim1.isDataRoaming)

        val esim = SimSubscriptionData(
            subscriptionId = 2,
            simSlotIndex = -1,
            displayName = "Jio eSIM",
            carrierName = "Jio",
            countryIso = "IN",
            mcc = "405",
            mnc = "854",
            isDataRoaming = true,
            isEmbedded = true,
            isDefaultVoice = false,
            isDefaultSms = false,
            isDefaultData = false
        )

        assertEquals("Embedded (eSIM)", esim.displaySlotNumber)
        assertEquals("405-854", esim.plmn)
        assertTrue(esim.isEmbedded)
        assertTrue(esim.isDataRoaming)
        assertFalse(esim.isDefaultVoice)
    }

    @Test
    fun testPlmnUnknownWhenMccOrMncNull() {
        val sim = SimSubscriptionData(
            subscriptionId = 3,
            simSlotIndex = 1,
            displayName = "Unknown SIM",
            carrierName = "Unknown",
            countryIso = "",
            mcc = null,
            mnc = null,
            isDataRoaming = false,
            isEmbedded = false,
            isDefaultVoice = false,
            isDefaultSms = false,
            isDefaultData = false
        )

        assertEquals("SIM 2", sim.displaySlotNumber)
        assertEquals("Unknown", sim.plmn)
    }

    @Test
    fun testSubscriptionsStateDefaultValues() {
        val state = SubscriptionsState()
        assertEquals(0, state.activeSubscriptionCount)
        assertTrue(state.activeSubscriptions.isEmpty())
        assertFalse(state.isMultiSimSupported)
        assertFalse(state.hasPermission)
    }

    @Test
    fun testNetworkGenerationClassification() {
        val (nrName, nrGen) = CellularNetworkManager.classifyNetworkType(TelephonyManager.NETWORK_TYPE_NR)
        assertEquals(NetworkGeneration.GEN_5G, nrGen)
        assertEquals("5G NR", nrName)

        val (lteName, lteGen) = CellularNetworkManager.classifyNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
        assertEquals(NetworkGeneration.GEN_4G, lteGen)
        assertEquals("LTE (4G)", lteName)

        val (hspaName, hspaGen) = CellularNetworkManager.classifyNetworkType(TelephonyManager.NETWORK_TYPE_HSPA)
        assertEquals(NetworkGeneration.GEN_3G, hspaGen)
        assertEquals("HSPA (3G)", hspaName)

        val (gsmName, gsmGen) = CellularNetworkManager.classifyNetworkType(TelephonyManager.NETWORK_TYPE_GSM)
        assertEquals(NetworkGeneration.GEN_2G, gsmGen)
        assertEquals("GSM (2G)", gsmName)
    }

    @Test
    fun testCellInfoScannerSanitization() {
        // Int.MAX_VALUE and -1 represent unavailable radio metrics
        assertNull(CellInfoScanner.sanitizeInt(Int.MAX_VALUE))
        assertNull(CellInfoScanner.sanitizeInt(-1))
        assertEquals(Integer.valueOf(123), CellInfoScanner.sanitizeInt(123))

        // Long.MAX_VALUE and -1L represent unavailable identities
        assertNull(CellInfoScanner.sanitizeLong(Long.MAX_VALUE))
        assertNull(CellInfoScanner.sanitizeLong(-1L))
        assertEquals(java.lang.Long.valueOf(456789L), CellInfoScanner.sanitizeLong(456789L))
    }

    @Test
    fun testIdentifierAuditResultStatusChip() {
        val protectedImei = IdentifierAuditResult(
            identifierName = "IMEI",
            accessible = false,
            valueOrMasked = null,
            restrictionReason = "Android 10+ restriction",
            requiredPermission = "READ_PRIVILEGED_PHONE_STATE",
            apiLevelIntroducedRestriction = 29,
            severity = RestrictionSeverity.SYSTEM_RESTRICTED
        )
        assertEquals("Protected (29+)", protectedImei.statusChipText)

        val accessibleId = IdentifierAuditResult(
            identifierName = "Sample",
            accessible = true,
            valueOrMasked = "12****78",
            restrictionReason = "Accessible",
            requiredPermission = "READ_PHONE_STATE",
            apiLevelIntroducedRestriction = 29,
            severity = RestrictionSeverity.ACCESSIBLE
        )
        assertEquals("Accessible", accessibleId.statusChipText)
    }

    @Test
    fun testCarrierConfigSummaryDataModel() {
        val summary = CarrierConfigSummary(
            subId = 1,
            isVoLteAvailable = true,
            isVoWifiAvailable = true,
            isVtAvailable = false,
            isDataRoamingAllowed = true,
            is5gNrEnabled = true,
            maxMmsSizeBytes = 1048576,
            carrierProperties = mapOf("custom_key" to "custom_value")
        )

        assertEquals(1, summary.subId)
        assertTrue(summary.isVoLteAvailable)
        assertTrue(summary.isVoWifiAvailable)
        assertFalse(summary.isVtAvailable)
        assertTrue(summary.isDataRoamingAllowed)
        assertTrue(summary.is5gNrEnabled)
        assertEquals(1048576, summary.maxMmsSizeBytes)
        assertEquals("custom_value", summary.carrierProperties["custom_key"])
    }
}

package com.example.droidsample.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScreeningRuleTest {

    @Test
    fun testNormalIncomingCallAllowed() {
        val rules = CallScreeningRules(
            blockSpamNumbers = true,
            silenceUnknownNumbers = false,
            customBlacklist = setOf("+19999999999")
        )

        val decision = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "+14155550123",
            displayName = "Alice Friend",
            rules = rules
        )

        assertFalse(decision.disallow)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
        assertFalse(decision.skipCallLog)
        assertFalse(decision.skipNotification)
        assertTrue(decision.reason.contains("Allowed"))
    }

    @Test
    fun testExactBlacklistMatchBlocked() {
        val rules = CallScreeningRules(
            customBlacklist = setOf("+10000000000", "1234567890"),
            skipCallLogInSpam = true,
            skipNotificationInSpam = true
        )

        val decision = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "+10000000000",
            displayName = "Spammer Inc",
            rules = rules
        )

        assertTrue(decision.disallow)
        assertTrue(decision.reject)
        assertTrue(decision.silence)
        assertTrue(decision.skipCallLog)
        assertTrue(decision.skipNotification)
        assertTrue(decision.reason.contains("Exact blacklist match"))
    }

    @Test
    fun testSpamPatternRegexBlocked() {
        val rules = CallScreeningRules(
            blockSpamNumbers = true,
            blockedPatterns = listOf("900.*", "8005550100"),
            skipCallLogInSpam = false,
            skipNotificationInSpam = true
        )

        // Matching 900 regex
        val decision1 = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "9001234567",
            displayName = null,
            rules = rules
        )
        assertTrue(decision1.disallow)
        assertTrue(decision1.reject)
        assertFalse(decision1.skipCallLog)
        assertTrue(decision1.skipNotification)
        assertTrue(decision1.reason.contains("Spam pattern match"))

        // Matching 8005550100 exact substring
        val decision2 = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "8005550100",
            displayName = "Telemarketer",
            rules = rules
        )
        assertTrue(decision2.disallow)
        assertTrue(decision2.reject)
    }

    @Test
    fun testSilenceUnknownCallers() {
        val rules = CallScreeningRules(
            blockSpamNumbers = true,
            silenceUnknownNumbers = true,
            customBlacklist = emptySet(),
            blockedPatterns = emptyList()
        )

        // Caller with no contact display name -> silenced, but not rejected
        val decisionUnknown = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "+15559876543",
            displayName = null,
            rules = rules
        )
        assertFalse(decisionUnknown.disallow)
        assertFalse(decisionUnknown.reject)
        assertTrue(decisionUnknown.silence)
        assertTrue(decisionUnknown.reason.contains("Silenced"))

        // Caller with contact display name -> allowed without silence
        val decisionKnown = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "+15559876543",
            displayName = "Dr. Robert",
            rules = rules
        )
        assertFalse(decisionKnown.disallow)
        assertFalse(decisionKnown.reject)
        assertFalse(decisionKnown.silence)
    }

    @Test
    fun testHiddenCallerIdHandling() {
        val rulesAllowHidden = CallScreeningRules(
            blockUnknownNumbersWithoutCallerId = false,
            silenceUnknownNumbers = false
        )
        val decisionAllowed = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = "",
            displayName = null,
            rules = rulesAllowHidden
        )
        assertFalse(decisionAllowed.disallow)
        assertFalse(decisionAllowed.reject)

        val rulesBlockHidden = CallScreeningRules(
            blockUnknownNumbersWithoutCallerId = true,
            skipCallLogInSpam = true,
            skipNotificationInSpam = true
        )
        val decisionBlocked = CallScreeningRepository.evaluateCall(
            handleSchemeSpecificPart = null,
            displayName = null,
            rules = rulesBlockHidden
        )
        assertTrue(decisionBlocked.disallow)
        assertTrue(decisionBlocked.reject)
        assertTrue(decisionBlocked.silence)
        assertTrue(decisionBlocked.skipCallLog)
        assertTrue(decisionBlocked.skipNotification)
        assertTrue(decisionBlocked.reason.contains("Empty/Hidden Caller ID"))
    }

    @Test
    fun testBlacklistModification() {
        val initialSize = CallScreeningRepository.rules.value.customBlacklist.size
        val testNumber = "+19998887777"

        CallScreeningRepository.addBlockedNumber(testNumber)
        assertTrue(CallScreeningRepository.rules.value.customBlacklist.contains(testNumber))

        CallScreeningRepository.removeBlockedNumber(testNumber)
        assertFalse(CallScreeningRepository.rules.value.customBlacklist.contains(testNumber))
        assertEquals(initialSize, CallScreeningRepository.rules.value.customBlacklist.size)
    }
}

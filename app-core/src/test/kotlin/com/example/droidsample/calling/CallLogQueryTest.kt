package com.example.droidsample.calling

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogQueryTest {

    @Test
    fun testFormatDuration() {
        assertEquals("0s", CallLogRepository.formatDuration(0))
        assertEquals("0s", CallLogRepository.formatDuration(-10))
        assertEquals("45s", CallLogRepository.formatDuration(45))
        assertEquals("1:00", CallLogRepository.formatDuration(60))
        assertEquals("2:05", CallLogRepository.formatDuration(125))
        assertEquals("10:30", CallLogRepository.formatDuration(630))
        assertEquals("1:00:00", CallLogRepository.formatDuration(3600))
        assertEquals("1:01:05", CallLogRepository.formatDuration(3665))
        assertEquals("2:30:15", CallLogRepository.formatDuration(9015))
    }

    @Test
    fun testCallTypeMapping() {
        assertEquals(CallType.INCOMING, CallType.fromCallLogType(CallLog.Calls.INCOMING_TYPE))
        assertEquals(CallType.OUTGOING, CallType.fromCallLogType(CallLog.Calls.OUTGOING_TYPE))
        assertEquals(CallType.MISSED, CallType.fromCallLogType(CallLog.Calls.MISSED_TYPE))
        assertEquals(CallType.VOICEMAIL, CallType.fromCallLogType(CallLog.Calls.VOICEMAIL_TYPE))
        assertEquals(CallType.REJECTED, CallType.fromCallLogType(CallLog.Calls.REJECTED_TYPE))
        assertEquals(CallType.BLOCKED, CallType.fromCallLogType(CallLog.Calls.BLOCKED_TYPE))
        assertEquals(CallType.OTHER, CallType.fromCallLogType(99999))
    }

    @Test
    fun testFilterEntriesBySearchQuery() {
        val sampleEntries = listOf(
            CallLogEntry(1L, "+14155550100", "Alice Smith", 1000L, 60L, CallType.INCOMING),
            CallLogEntry(2L, "+16505550200", "Bob Jones", 2000L, 120L, CallType.OUTGOING),
            CallLogEntry(3L, "+12125550300", null, 3000L, 0L, CallType.MISSED),
            CallLogEntry(4L, "+18005550400", "Spam Caller", 4000L, 0L, CallType.BLOCKED)
        )

        // Empty search matches all
        val all = CallLogRepository.filterEntries(sampleEntries, "", null)
        assertEquals(4, all.size)

        // Filter by name (case-insensitive)
        val aliceResults = CallLogRepository.filterEntries(sampleEntries, "alice", null)
        assertEquals(1, aliceResults.size)
        assertEquals("Alice Smith", aliceResults[0].cachedName)

        // Filter by phone number substring
        val numResults = CallLogRepository.filterEntries(sampleEntries, "650", null)
        assertEquals(1, numResults.size)
        assertEquals("Bob Jones", numResults[0].cachedName)

        // Non-existent search
        val none = CallLogRepository.filterEntries(sampleEntries, "NonExistent", null)
        assertTrue(none.isEmpty())
    }

    @Test
    fun testFilterEntriesByType() {
        val sampleEntries = listOf(
            CallLogEntry(1L, "+14155550100", "Alice", 1000L, 60L, CallType.INCOMING),
            CallLogEntry(2L, "+16505550200", "Bob", 2000L, 120L, CallType.OUTGOING),
            CallLogEntry(3L, "+12125550300", "Charlie", 3000L, 0L, CallType.MISSED),
            CallLogEntry(4L, "+18005550400", "Spam", 4000L, 0L, CallType.MISSED)
        )

        val missedCalls = CallLogRepository.filterEntries(sampleEntries, "", CallType.MISSED)
        assertEquals(2, missedCalls.size)
        assertTrue(missedCalls.all { it.type == CallType.MISSED })

        val incomingCalls = CallLogRepository.filterEntries(sampleEntries, "", CallType.INCOMING)
        assertEquals(1, incomingCalls.size)
        assertEquals("Alice", incomingCalls[0].cachedName)
    }

    @Test
    fun testFilterEntriesCombined() {
        val sampleEntries = listOf(
            CallLogEntry(1L, "+14155550100", "Alice", 1000L, 60L, CallType.MISSED),
            CallLogEntry(2L, "+16505550200", "Alice", 2000L, 120L, CallType.INCOMING),
            CallLogEntry(3L, "+12125550300", "Bob", 3000L, 0L, CallType.MISSED)
        )

        // Search for "Alice" AND type MISSED
        val combined = CallLogRepository.filterEntries(sampleEntries, "Alice", CallType.MISSED)
        assertEquals(1, combined.size)
        assertEquals(1L, combined[0].id)
    }
}

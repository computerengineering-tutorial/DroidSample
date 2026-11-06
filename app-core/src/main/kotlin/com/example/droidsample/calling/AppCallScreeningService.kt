package com.example.droidsample.calling

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Configuration rule parameters for Call Screening.
 */
data class CallScreeningRules(
    val blockSpamNumbers: Boolean = true,
    val silenceUnknownNumbers: Boolean = false,
    val skipCallLogInSpam: Boolean = false,
    val skipNotificationInSpam: Boolean = true,
    val blockUnknownNumbersWithoutCallerId: Boolean = false,
    val blockedPatterns: List<String> = listOf("8005550100", "8885550199", "900.*"),
    val customBlacklist: Set<String> = setOf("+10000000000", "1234567890")
)

data class ScreenedCallLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val handle: String,
    val callerName: String?,
    val isBlocked: Boolean,
    val isSilenced: Boolean,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Singleton repository holding screening rules and the log of screened incoming calls.
 */
object CallScreeningRepository {
    private val _rules = MutableStateFlow(CallScreeningRules())
    val rules: StateFlow<CallScreeningRules> = _rules.asStateFlow()

    private val _history = MutableStateFlow<List<ScreenedCallLogEntry>>(emptyList())
    val history: StateFlow<List<ScreenedCallLogEntry>> = _history.asStateFlow()

    fun updateRules(transform: (CallScreeningRules) -> CallScreeningRules) {
        _rules.update(transform)
    }

    fun addBlockedNumber(number: String) {
        _rules.update { current ->
            current.copy(customBlacklist = current.customBlacklist + number.trim())
        }
    }

    fun removeBlockedNumber(number: String) {
        _rules.update { current ->
            current.copy(customBlacklist = current.customBlacklist - number.trim())
        }
    }

    fun recordScreenedCall(entry: ScreenedCallLogEntry) {
        _history.update { current ->
            listOf(entry) + current.take(99) // Keep last 100 entries
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    /**
     * Pure evaluator function for unit testability.
     */
    fun evaluateCall(
        handleSchemeSpecificPart: String?,
        displayName: String?,
        rules: CallScreeningRules
    ): ScreeningDecision {
        val number = handleSchemeSpecificPart?.trim().orEmpty()

        if (number.isEmpty()) {
            return if (rules.blockUnknownNumbersWithoutCallerId) {
                ScreeningDecision(
                    disallow = true,
                    reject = true,
                    silence = true,
                    skipCallLog = rules.skipCallLogInSpam,
                    skipNotification = rules.skipNotificationInSpam,
                    reason = "Blocked: Empty/Hidden Caller ID"
                )
            } else {
                ScreeningDecision(
                    disallow = false,
                    reject = false,
                    silence = rules.silenceUnknownNumbers,
                    skipCallLog = false,
                    skipNotification = false,
                    reason = if (rules.silenceUnknownNumbers) "Silenced: Hidden caller" else "Allowed: Caller ID absent"
                )
            }
        }

        // Check custom exact blacklist
        if (rules.customBlacklist.contains(number)) {
            return ScreeningDecision(
                disallow = true,
                reject = true,
                silence = true,
                skipCallLog = rules.skipCallLogInSpam,
                skipNotification = rules.skipNotificationInSpam,
                reason = "Blocked: Exact blacklist match ($number)"
            )
        }

        // Check patterns (regex)
        if (rules.blockSpamNumbers) {
            val matchingPattern = rules.blockedPatterns.firstOrNull { pattern ->
                try {
                    number.matches(Regex(pattern)) || number.contains(pattern)
                } catch (_: Exception) {
                    false
                }
            }

            if (matchingPattern != null) {
                return ScreeningDecision(
                    disallow = true,
                    reject = true,
                    silence = true,
                    skipCallLog = rules.skipCallLogInSpam,
                    skipNotification = rules.skipNotificationInSpam,
                    reason = "Blocked: Spam pattern match '$matchingPattern'"
                )
            }
        }

        if (rules.silenceUnknownNumbers && displayName.isNullOrBlank()) {
            return ScreeningDecision(
                disallow = false,
                reject = false,
                silence = true,
                skipCallLog = false,
                skipNotification = false,
                reason = "Silenced: Unknown caller with no contacts display name"
            )
        }

        return ScreeningDecision(
            disallow = false,
            reject = false,
            silence = false,
            skipCallLog = false,
            skipNotification = false,
            reason = "Allowed: Normal incoming call"
        )
    }
}

data class ScreeningDecision(
    val disallow: Boolean,
    val reject: Boolean,
    val silence: Boolean,
    val skipCallLog: Boolean,
    val skipNotification: Boolean,
    val reason: String
)

/**
 * System CallScreeningService handling incoming call triage.
 * Manifest declared with BIND_SCREENING_SERVICE.
 */
class AppCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val handleUri = callDetails.handle
        val rawNumber = handleUri?.schemeSpecificPart ?: ""
        val callerName = callDetails.callerDisplayName

        val currentRules = CallScreeningRepository.rules.value
        val decision = CallScreeningRepository.evaluateCall(rawNumber, callerName, currentRules)

        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(decision.disallow)
            .setRejectCall(decision.reject)
            .setSkipCallLog(decision.skipCallLog)
            .setSkipNotification(decision.skipNotification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            responseBuilder.setSilenceCall(decision.silence)
        }

        val response = responseBuilder.build()
        respondToCall(callDetails, response)

        CallScreeningRepository.recordScreenedCall(
            ScreenedCallLogEntry(
                handle = rawNumber.ifEmpty { "Private/Restricted" },
                callerName = callerName,
                isBlocked = decision.disallow,
                isSilenced = decision.silence,
                reason = decision.reason
            )
        )

        Log.i("CallScreening", "Screened call from $rawNumber -> ${decision.reason}")
    }
}

package com.example.droidsample.location

import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.Settings

/**
 * Result evaluation of mock location and anti-spoofing checks.
 */
data class MockEvaluationResult(
    val isMock: Boolean,
    val confidenceScore: Float, // 0.0 = Verified Genuine, 1.0 = Definite Mock
    val hasMockFlag: Boolean,
    val mockSettingsActive: Boolean,
    val hasSuspiciousAccuracy: Boolean,
    val hasSuspiciousSpeed: Boolean,
    val summary: String
)

/**
 * Detects mock locations, GPS spoofing, and system developer mock settings.
 */
object MockLocationDetector {

    /**
     * Checks if a [Location] object was flagged as mock by Android OS.
     */
    fun isLocationMock(location: Location?): Boolean {
        if (location == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Checks if developer options have mock location enabled (on supported Android API levels).
     */
    fun isMockSettingsEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                ) != "0"
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inspects location attributes for suspicious anomalies common in GPS spoofing apps:
     * - Accuracy of exactly 0.0m or negative
     * - Speed exceeding commercial jet aircraft limits (> 300 m/s = ~1080 km/h)
     */
    fun hasSuspiciousTelemetry(location: Location): Pair<Boolean, Boolean> {
        val suspiciousAccuracy = location.hasAccuracy() && location.accuracy <= 0.0f
        val suspiciousSpeed = location.hasSpeed() && location.speed > 300.0f
        return Pair(suspiciousAccuracy, suspiciousSpeed)
    }

    /**
     * Evaluates domain [LocationData] against spoofing heuristics.
     */
    fun evaluateLocationData(locationData: LocationData?, context: Context? = null): MockEvaluationResult {
        if (locationData == null) {
            return MockEvaluationResult(
                isMock = false,
                confidenceScore = 0f,
                hasMockFlag = false,
                mockSettingsActive = false,
                hasSuspiciousAccuracy = false,
                hasSuspiciousSpeed = false,
                summary = "No location data to evaluate"
            )
        }

        val hasFlag = locationData.isMock
        val mockSettings = context?.let { isMockSettingsEnabled(it) } ?: false
        val suspiciousAccuracy = locationData.horizontalAccuracyMeters != null && locationData.horizontalAccuracyMeters <= 0.0f
        val suspiciousSpeed = locationData.speedMetersPerSecond != null && locationData.speedMetersPerSecond > 300.0f

        var score = 0f
        val reasons = mutableListOf<String>()

        if (hasFlag) {
            score += 0.8f
            reasons.add("System Location.isMock flag set")
        }
        if (mockSettings) {
            score += 0.3f
            reasons.add("Mock location allowed in settings")
        }
        if (suspiciousAccuracy) {
            score += 0.2f
            reasons.add("Unrealistic accuracy: ${locationData.horizontalAccuracyMeters}m")
        }
        if (suspiciousSpeed) {
            score += 0.3f
            reasons.add("Supersonic speed: ${locationData.speedMetersPerSecond} m/s")
        }

        score = score.coerceIn(0f, 1f)
        val isMock = score >= 0.5f

        val summary = when {
            score >= 0.8f -> "Definite Mock Location (${reasons.joinToString(", ")})"
            score >= 0.4f -> "Suspicious Coordinates (${reasons.joinToString(", ")})"
            else -> "Genuine Hardware Fix"
        }

        return MockEvaluationResult(
            isMock = isMock,
            confidenceScore = score,
            hasMockFlag = hasFlag,
            mockSettingsActive = mockSettings,
            hasSuspiciousAccuracy = suspiciousAccuracy,
            hasSuspiciousSpeed = suspiciousSpeed,
            summary = summary
        )
    }

    /**
     * Comprehensive evaluation of an Android [Location] instance.
     */
    fun evaluateLocation(location: Location?, context: Context?): MockEvaluationResult {
        if (location == null) {
            return MockEvaluationResult(
                isMock = false,
                confidenceScore = 0f,
                hasMockFlag = false,
                mockSettingsActive = false,
                hasSuspiciousAccuracy = false,
                hasSuspiciousSpeed = false,
                summary = "No location provided"
            )
        }

        val hasFlag = isLocationMock(location)
        val mockSettings = context?.let { isMockSettingsEnabled(it) } ?: false
        val (suspiciousAcc, suspiciousSpd) = hasSuspiciousTelemetry(location)

        var score = 0f
        val reasons = mutableListOf<String>()

        if (hasFlag) {
            score += 0.8f
            reasons.add("System Location.isMock flag set")
        }
        if (mockSettings) {
            score += 0.3f
            reasons.add("Mock location allowed in settings")
        }
        if (suspiciousAcc) {
            score += 0.2f
            reasons.add("Unrealistic 0m accuracy")
        }
        if (suspiciousSpd) {
            score += 0.3f
            reasons.add("Extreme speed > 300 m/s")
        }

        score = score.coerceIn(0f, 1f)
        val isMock = score >= 0.5f

        val summary = when {
            score >= 0.8f -> "Definite Mock Location (${reasons.joinToString(", ")})"
            score >= 0.4f -> "Suspicious Coordinates (${reasons.joinToString(", ")})"
            else -> "Genuine Hardware Fix"
        }

        return MockEvaluationResult(
            isMock = isMock,
            confidenceScore = score,
            hasMockFlag = hasFlag,
            mockSettingsActive = mockSettings,
            hasSuspiciousAccuracy = suspiciousAcc,
            hasSuspiciousSpeed = suspiciousSpd,
            summary = summary
        )
    }
}

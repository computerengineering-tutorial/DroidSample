package com.example.droidsample.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating mock location heuristic rules, security scores,
 * and anomaly detection.
 */
class MockLocationRuleTest {

    @Test
    fun testNullLocationReturnsSafeDefault() {
        val evaluation = MockLocationDetector.evaluateLocationData(null)
        assertFalse(evaluation.isMock)
        assertEquals(0f, evaluation.confidenceScore, 0.001f)
        assertFalse(evaluation.hasMockFlag)
        assertFalse(evaluation.hasSuspiciousAccuracy)
        assertFalse(evaluation.hasSuspiciousSpeed)
    }

    @Test
    fun testMockFlagProducesHighConfidenceMockDetection() {
        val mockData = LocationData(
            latitude = 37.7749,
            longitude = -122.4194,
            isMock = true
        )

        val evaluation = MockLocationDetector.evaluateLocationData(mockData)
        assertTrue(evaluation.isMock)
        assertTrue(evaluation.hasMockFlag)
        assertTrue(evaluation.confidenceScore >= 0.8f)
        assertTrue(evaluation.summary.contains("Definite Mock Location"))
    }

    @Test
    fun testGenuineFixEvaluatesAsClean() {
        val genuineData = LocationData(
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 50.0,
            horizontalAccuracyMeters = 4.5f,
            speedMetersPerSecond = 12.0f, // ~43 km/h
            isMock = false
        )

        val evaluation = MockLocationDetector.evaluateLocationData(genuineData)
        assertFalse(evaluation.isMock)
        assertEquals(0f, evaluation.confidenceScore, 0.001f)
        assertFalse(evaluation.hasMockFlag)
        assertFalse(evaluation.hasSuspiciousAccuracy)
        assertFalse(evaluation.hasSuspiciousSpeed)
        assertTrue(evaluation.summary.contains("Genuine Hardware Fix"))
    }

    @Test
    fun testZeroAccuracyTriggersSuspicion() {
        val zeroAccData = LocationData(
            latitude = 37.7749,
            longitude = -122.4194,
            horizontalAccuracyMeters = 0.0f,
            isMock = false
        )

        val evaluation = MockLocationDetector.evaluateLocationData(zeroAccData)
        assertTrue(evaluation.hasSuspiciousAccuracy)
        assertTrue(evaluation.confidenceScore > 0f)
    }

    @Test
    fun testSupersonicSpeedTriggersSuspicion() {
        val supersonicData = LocationData(
            latitude = 37.7749,
            longitude = -122.4194,
            speedMetersPerSecond = 500.0f, // 1800 km/h (unrealistic for ground/commercial)
            isMock = false
        )

        val evaluation = MockLocationDetector.evaluateLocationData(supersonicData)
        assertTrue(evaluation.hasSuspiciousSpeed)
        assertTrue(evaluation.confidenceScore > 0f)
    }

    @Test
    fun testCombinedAnomaliesElevateScore() {
        val suspiciousData = LocationData(
            latitude = 37.7749,
            longitude = -122.4194,
            horizontalAccuracyMeters = 0.0f,
            speedMetersPerSecond = 600.0f,
            isMock = false
        )

        val evaluation = MockLocationDetector.evaluateLocationData(suspiciousData)
        assertTrue(evaluation.hasSuspiciousAccuracy)
        assertTrue(evaluation.hasSuspiciousSpeed)
        // Score: 0.2 (accuracy) + 0.3 (speed) = 0.5 -> flagged as mock/suspicious
        assertTrue(evaluation.isMock)
        assertTrue(evaluation.confidenceScore >= 0.5f)
    }
}

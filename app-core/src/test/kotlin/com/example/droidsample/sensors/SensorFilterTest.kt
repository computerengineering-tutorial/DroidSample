package com.example.droidsample.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SensorFilterTest {

    private val delta = 0.001f

    @Test
    fun testLowPassFilterInitialization() {
        val lpf = LowPassFilter(alpha = 0.2f)
        val initial = floatArrayOf(9.8f, 0.0f, -0.5f)

        // First sample should pass through verbatim to seed the state
        val out = lpf.filter(initial)
        assertEquals(9.8f, out[0], delta)
        assertEquals(0.0f, out[1], delta)
        assertEquals(-0.5f, out[2], delta)
    }

    @Test
    fun testLowPassFilterStepResponse() {
        val alpha = 0.5f
        val lpf = LowPassFilter(alpha = alpha)

        // Initial state at 0
        lpf.filter(floatArrayOf(0f, 0f, 0f))

        // Step change to 10
        val sample1 = lpf.filter(floatArrayOf(10f, 0f, 0f))
        // 0.5 * 10 + 0.5 * 0 = 5.0
        assertEquals(5.0f, sample1[0], delta)

        // Next sample still at 10
        val sample2 = lpf.filter(floatArrayOf(10f, 0f, 0f))
        // 0.5 * 10 + 0.5 * 5.0 = 7.5
        assertEquals(7.5f, sample2[0], delta)

        // Next sample still at 10
        val sample3 = lpf.filter(floatArrayOf(10f, 0f, 0f))
        // 0.5 * 10 + 0.5 * 7.5 = 8.75
        assertEquals(8.75f, sample3[0], delta)
    }

    @Test
    fun testLowPassFilterNoiseAttenuation() {
        val lpf = LowPassFilter(alpha = 0.1f)
        lpf.filter(floatArrayOf(10f, 10f, 10f))

        // High frequency noise fluctuating around 10
        val noisyInputs = listOf(
            floatArrayOf(12f, 8f, 11f),
            floatArrayOf(8f, 12f, 9f),
            floatArrayOf(11f, 9f, 12f),
            floatArrayOf(9f, 11f, 8f)
        )

        var lastOut = floatArrayOf(10f, 10f, 10f)
        for (noisy in noisyInputs) {
            lastOut = lpf.filter(noisy)
        }

        // Output should stay close to 10 with variance significantly reduced
        assertTrue("X should remain smooth around 10", abs(lastOut[0] - 10f) < 1.0f)
        assertTrue("Y should remain smooth around 10", abs(lastOut[1] - 10f) < 1.0f)
        assertTrue("Z should remain smooth around 10", abs(lastOut[2] - 10f) < 1.0f)
    }

    @Test
    fun testHighPassFilterDCOffsetDecay() {
        val alpha = 0.8f
        val hpf = HighPassFilter(alpha = alpha)

        // First sample initializes
        hpf.filter(floatArrayOf(9.8f, 0f, 0f))

        // Constant input (DC signal) should decay rapidly to zero
        var currentOut = floatArrayOf(0f, 0f, 0f)
        for (i in 0 until 20) {
            currentOut = hpf.filter(floatArrayOf(9.8f, 0f, 0f))
        }

        assertEquals("Constant DC component should be completely attenuated", 0.0f, currentOut[0], 0.05f)
    }

    @Test
    fun testComputeAlphaFormulas() {
        // Cutoff = 5 Hz, sample interval = 0.02s (50 Hz)
        val lpfAlpha = LowPassFilter.computeAlpha(cutoffFrequencyHz = 5f, sampleIntervalSec = 0.02f)
        assertTrue("LPF alpha should be in (0, 1)", lpfAlpha in 0.0f..1.0f)

        val hpfAlpha = HighPassFilter.computeAlpha(cutoffFrequencyHz = 5f, sampleIntervalSec = 0.02f)
        assertTrue("HPF alpha should be in (0, 1)", hpfAlpha in 0.0f..1.0f)
    }

    @Test
    fun testFilterReset() {
        val lpf = LowPassFilter(0.2f)
        lpf.filter(floatArrayOf(100f, 100f, 100f))
        lpf.reset()

        // After reset, next value should re-initialize the baseline
        val out = lpf.filter(floatArrayOf(5f, 5f, 5f))
        assertEquals(5.0f, out[0], delta)
    }
}

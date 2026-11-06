package com.example.droidsample.sensors

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Filter mode selection for motion signals.
 */
enum class FilterMode {
    NONE,
    LOW_PASS,
    HIGH_PASS
}

/**
 * First-order Infinite Impulse Response (IIR) Low-Pass Filter.
 * Used for smoothing sensor noise and isolating static gravity components.
 * Formula: y[n] = alpha * x[n] + (1 - alpha) * y[n-1]
 */
class LowPassFilter(var alpha: Float = 0.25f) {
    private var output: FloatArray? = null

    init {
        require(alpha in 0f..1f) { "Alpha must be between 0.0 and 1.0" }
    }

    /**
     * Filters a 3-axis input array [x, y, z].
     */
    fun filter(input: FloatArray): FloatArray {
        val current = output
        if (current == null || current.size != input.size) {
            output = input.clone()
            return input.clone()
        }

        val result = FloatArray(input.size)
        for (i in input.indices) {
            result[i] = alpha * input[i] + (1.0f - alpha) * current[i]
        }
        output = result
        return result
    }

    fun reset() {
        output = null
    }

    companion object {
        /**
         * Computes alpha from cutoff frequency (fc in Hz) and sample interval (dt in seconds).
         * RC = 1 / (2 * pi * fc)
         * alpha = dt / (RC + dt)
         */
        fun computeAlpha(cutoffFrequencyHz: Float, sampleIntervalSec: Float): Float {
            if (cutoffFrequencyHz <= 0f || sampleIntervalSec <= 0f) return 1.0f
            val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFrequencyHz)
            return sampleIntervalSec / (rc + sampleIntervalSec)
        }
    }
}

/**
 * First-order Infinite Impulse Response (IIR) High-Pass Filter.
 * Used for isolating rapid changes and removing constant biases (like gravity).
 * Formula: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
 */
class HighPassFilter(var alpha: Float = 0.8f) {
    private var prevInput: FloatArray? = null
    private var prevOutput: FloatArray? = null

    init {
        require(alpha in 0f..1f) { "Alpha must be between 0.0 and 1.0" }
    }

    /**
     * Filters a 3-axis input array [x, y, z].
     */
    fun filter(input: FloatArray): FloatArray {
        val pIn = prevInput
        val pOut = prevOutput

        if (pIn == null || pOut == null || pIn.size != input.size) {
            prevInput = input.clone()
            prevOutput = FloatArray(input.size)
            return FloatArray(input.size)
        }

        val result = FloatArray(input.size)
        for (i in input.indices) {
            result[i] = alpha * (pOut[i] + input[i] - pIn[i])
        }
        prevInput = input.clone()
        prevOutput = result
        return result
    }

    fun reset() {
        prevInput = null
        prevOutput = null
    }

    companion object {
        /**
         * Computes alpha from cutoff frequency (fc in Hz) and sample interval (dt in seconds).
         * RC = 1 / (2 * pi * fc)
         * alpha = RC / (RC + dt)
         */
        fun computeAlpha(cutoffFrequencyHz: Float, sampleIntervalSec: Float): Float {
            if (cutoffFrequencyHz <= 0f || sampleIntervalSec <= 0f) return 0.0f
            val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFrequencyHz)
            return rc / (rc + sampleIntervalSec)
        }
    }
}

/**
 * Mathematical utilities for rotation matrices and orientation Euler angles.
 * Implements Android SensorManager matrix calculations in pure Kotlin for unit testability.
 */
object OrientationMath {

    /**
     * Computes the 9-element rotation matrix R and inclination matrix I from gravity and geomagnetic vectors.
     * Equivalent to [android.hardware.SensorManager.getRotationMatrix].
     */
    fun computeRotationMatrix(
        gravity: FloatArray,
        geomagnetic: FloatArray,
        outR: FloatArray,
        outI: FloatArray? = null
    ): Boolean {
        var ax = gravity[0]
        var ay = gravity[1]
        var az = gravity[2]

        val normSqA = ax * ax + ay * ay + az * az
        val gThreshold = 0.01f
        if (normSqA < gThreshold) {
            // Free-fall or invalid gravity
            return false
        }

        val normA = 1.0f / sqrt(normSqA)
        ax *= normA
        ay *= normA
        az *= normA

        val ex = geomagnetic[0]
        val ey = geomagnetic[1]
        val ez = geomagnetic[2]

        // Cross product of E x A -> H (east vector)
        var hx = ey * az - ez * ay
        var hy = ez * ax - ex * az
        var hz = ex * ay - ey * ax

        val normSqH = hx * hx + hy * hy + hz * hz
        if (normSqH < 0.0001f) {
            // Magnetic field nearly parallel to gravity
            return false
        }

        val normH = 1.0f / sqrt(normSqH)
        hx *= normH
        hy *= normH
        hz *= normH

        // Cross product of A x H -> M (north vector)
        val mx = ay * hz - az * hy
        val my = az * hx - ax * hz
        val mz = ax * hy - ay * hx

        if (outR.size >= 9) {
            outR[0] = hx; outR[1] = hy; outR[2] = hz
            outR[3] = mx; outR[4] = my; outR[5] = mz
            outR[6] = ax; outR[7] = ay; outR[8] = az
        }

        if (outI != null && outI.size >= 9) {
            // Inclination matrix
            val invH = 1.0f / sqrt(ex * ex + ey * ey + ez * ez)
            val c = (ex * mx + ey * my + ez * mz) * invH
            val s = (ex * ax + ey * ay + ez * az) * invH
            outI[0] = 1f; outI[1] = 0f; outI[2] = 0f
            outI[3] = 0f; outI[4] = c;  outI[5] = s
            outI[6] = 0f; outI[7] = -s; outI[8] = c
        }

        return true
    }

    /**
     * Extracts Euler orientation angles (Azimuth, Pitch, Roll) in radians from a 9-element rotation matrix.
     * Equivalent to [android.hardware.SensorManager.getOrientation].
     * values[0]: Azimuth (rotation around -z axis, [-pi, pi])
     * values[1]: Pitch (rotation around -x axis, [-pi/2, pi/2])
     * values[2]: Roll (rotation around y axis, [-pi, pi])
     */
    fun computeOrientation(r: FloatArray, values: FloatArray): FloatArray {
        require(r.size >= 9 && values.size >= 3) { "Matrix must have >= 9 elements and values >= 3" }

        // Azimuth: atan2(R[1], R[4])
        values[0] = atan2(r[1], r[4])
        // Pitch: asin(-R[7])
        val sinPitch = -r[7].coerceIn(-1.0f, 1.0f)
        values[1] = asin(sinPitch)
        // Roll: atan2(-R[6], R[8])
        values[2] = atan2(-r[6], r[8])

        return values
    }

    /**
     * Normalizes radians to degrees in range [0, 360).
     */
    fun radiansToDegrees360(rad: Float): Float {
        val deg = Math.toDegrees(rad.toDouble()).toFloat()
        return (deg % 360f + 360f) % 360f
    }

    /**
     * Converts radians to standard signed degrees [-180, 180].
     */
    fun radiansToDegrees(rad: Float): Float {
        return Math.toDegrees(rad.toDouble()).toFloat()
    }
}

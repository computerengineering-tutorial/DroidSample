package com.example.droidsample.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class RotationMatrixTest {

    private val delta = 0.01f

    @Test
    fun testDeviceFlatFacingNorth() {
        // Flat on table (gravity along +Z in Android sensor coords is ~9.81)
        val gravity = floatArrayOf(0f, 0f, 9.81f)
        // Geomagnetic pointing North (+Y axis) with slight downward inclination (-Z)
        val geomagnetic = floatArrayOf(0f, 25f, -40f)

        val r = FloatArray(9)
        val i = FloatArray(9)
        val success = OrientationMath.computeRotationMatrix(gravity, geomagnetic, r, i)
        assertTrue("Rotation matrix computation should succeed", success)

        val orientation = FloatArray(3)
        OrientationMath.computeOrientation(r, orientation)

        val azimuthDeg = OrientationMath.radiansToDegrees360(orientation[0])
        val pitchDeg = OrientationMath.radiansToDegrees(orientation[1])
        val rollDeg = OrientationMath.radiansToDegrees(orientation[2])

        // Facing North flat on table -> Azimuth ~ 0° (or 360°), Pitch ~ 0°, Roll ~ 0°
        val normalizedAzimuth = if (azimuthDeg > 350f) 360f - azimuthDeg else azimuthDeg
        assertTrue("Azimuth should be near 0° North, but was $azimuthDeg", normalizedAzimuth < 5.0f)
        assertTrue("Pitch should be near 0°, but was $pitchDeg", abs(pitchDeg) < 5.0f)
        assertTrue("Roll should be near 0°, but was $rollDeg", abs(rollDeg) < 5.0f)
    }

    @Test
    fun testDeviceFlatFacingEast() {
        val gravity = floatArrayOf(0f, 0f, 9.81f)
        // Device rotated 90 degrees clockwise (facing East): North magnetic field now lies along -X axis
        val geomagnetic = floatArrayOf(-25f, 0f, -40f)

        val r = FloatArray(9)
        val success = OrientationMath.computeRotationMatrix(gravity, geomagnetic, r)
        assertTrue(success)

        val orientation = FloatArray(3)
        OrientationMath.computeOrientation(r, orientation)

        val azimuthDeg = OrientationMath.radiansToDegrees360(orientation[0])
        assertEquals("Azimuth should be ~90° for East", 90f, azimuthDeg, 5.0f)
    }

    @Test
    fun testFreeFallGravityFailsGracefully() {
        val zeroGravity = floatArrayOf(0f, 0f, 0f)
        val geomagnetic = floatArrayOf(0f, 25f, -40f)
        val r = FloatArray(9)

        val success = OrientationMath.computeRotationMatrix(zeroGravity, geomagnetic, r)
        assertFalse("Free fall should fail rotation matrix computation", success)
    }

    @Test
    fun testRotationMatrixOrthonormality() {
        val gravity = floatArrayOf(1.5f, 4.2f, 8.7f)
        val geomagnetic = floatArrayOf(5f, 22f, -35f)

        val r = FloatArray(9)
        val success = OrientationMath.computeRotationMatrix(gravity, geomagnetic, r)
        assertTrue(success)

        // Row 1 length must be 1.0
        val row1Norm = r[0] * r[0] + r[1] * r[1] + r[2] * r[2]
        assertEquals("Row 1 must be unit vector", 1.0f, row1Norm, delta)

        // Row 2 length must be 1.0
        val row2Norm = r[3] * r[3] + r[4] * r[4] + r[5] * r[5]
        assertEquals("Row 2 must be unit vector", 1.0f, row2Norm, delta)

        // Row 3 length must be 1.0
        val row3Norm = r[6] * r[6] + r[7] * r[7] + r[8] * r[8]
        assertEquals("Row 3 must be unit vector", 1.0f, row3Norm, delta)

        // Dot product between Row 1 and Row 2 must be 0 (orthogonal)
        val dot12 = r[0] * r[3] + r[1] * r[4] + r[2] * r[5]
        assertEquals("Row 1 and 2 must be orthogonal", 0.0f, dot12, delta)
    }

    @Test
    fun testBarometricAltitudeCalculation() {
        val seaLevelPressure = 1013.25f

        // At sea level pressure, altitude should be 0 meters
        val seaLevelAlt = EnvironmentalSensorManager.calculateAltitude(seaLevelPressure, 1013.25f)
        assertEquals("Altitude at standard sea level pressure should be 0", 0f, seaLevelAlt, 0.1f)

        // At lower pressure (e.g. 900 hPa), altitude should be positive (around 988 m)
        val mountainAlt = EnvironmentalSensorManager.calculateAltitude(seaLevelPressure, 900.0f)
        assertTrue("Altitude at 900 hPa should be > 900m", mountainAlt > 900f && mountainAlt < 1100f)
    }
}

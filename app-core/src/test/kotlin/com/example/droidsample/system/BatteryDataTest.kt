package com.example.droidsample.system

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying decoding of battery intent parameters,
 * percentage calculation, thermal conversion, and health statuses.
 */
class BatteryDataTest {

    @Test
    fun testPercentageCalculationStandard() {
        val percent = PowerBatteryManager.calculatePercentage(level = 80, scale = 100)
        assertEquals(80, percent)
    }

    @Test
    fun testPercentageCalculationNonStandardScale() {
        // Some hardware devices report scale as 200 or 50
        val percentHalf = PowerBatteryManager.calculatePercentage(level = 25, scale = 50)
        assertEquals(50, percentHalf)

        val percentDouble = PowerBatteryManager.calculatePercentage(level = 150, scale = 200)
        assertEquals(75, percentDouble)
    }

    @Test
    fun testPercentageCalculationBoundaryConditions() {
        assertEquals(0, PowerBatteryManager.calculatePercentage(level = 0, scale = 100))
        assertEquals(100, PowerBatteryManager.calculatePercentage(level = 100, scale = 100))
        assertEquals(100, PowerBatteryManager.calculatePercentage(level = 120, scale = 100)) // overflow clamp
        assertEquals(0, PowerBatteryManager.calculatePercentage(level = -1, scale = 100)) // negative level clamp
        assertEquals(0, PowerBatteryManager.calculatePercentage(level = 50, scale = 0)) // division by zero safety
        assertEquals(0, PowerBatteryManager.calculatePercentage(level = 50, scale = -10))
    }

    @Test
    fun testTemperatureConversion() {
        // BatteryManager reports temperature in tenths of a degree Celsius (e.g. 315 -> 31.5°C)
        val temp = PowerBatteryManager.convertTemperature(315)
        assertEquals(31.5, temp, 0.001)

        val coldTemp = PowerBatteryManager.convertTemperature(-45)
        assertEquals(-4.5, coldTemp, 0.001)

        val zeroTemp = PowerBatteryManager.convertTemperature(0)
        assertEquals(0.0, zeroTemp, 0.001)
    }

    @Test
    fun testHealthStatusDecoding() {
        assertEquals("Good", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("Overheat", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals("Dead", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_DEAD))
        assertEquals("Over Voltage", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE))
        assertEquals("Unspecified Failure", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE))
        assertEquals("Cold", PowerBatteryManager.parseHealth(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals("Unknown", PowerBatteryManager.parseHealth(999))
    }

    @Test
    fun testPluggedSourceDecoding() {
        assertEquals("AC Charger", PowerBatteryManager.parsePlugged(BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("USB Port", PowerBatteryManager.parsePlugged(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("Wireless Dock", PowerBatteryManager.parsePlugged(BatteryManager.BATTERY_PLUGGED_WIRELESS))
        assertEquals("Unplugged (Battery)", PowerBatteryManager.parsePlugged(0))
        assertTrue(PowerBatteryManager.parsePlugged(8).startsWith("Other"))
    }
}

package com.example.droidsample.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DistanceCalculator] covering Haversine distance,
 * initial bearing calculation, radius containment, and boundary validations.
 */
class DistanceCalculatorTest {

    @Test
    fun testIdenticalPointsHaveZeroDistance() {
        val lat = 37.7749
        val lon = -122.4194
        val dist = DistanceCalculator.haversineDistance(lat, lon, lat, lon)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun testHaversineDistanceBetweenLondonAndParis() {
        // London: 51.5074° N, 0.1278° W
        // Paris: 48.8566° N, 2.3522° E
        // Known distance: ~343.5 km (343,500 meters)
        val londonLat = 51.5074
        val londonLon = -0.1278
        val parisLat = 48.8566
        val parisLon = 2.3522

        val distanceMeters = DistanceCalculator.haversineDistance(londonLat, londonLon, parisLat, parisLon)

        // Allow ±1% tolerance for Earth ellipsoid variance vs spherical model
        assertEquals(343500.0, distanceMeters, 5000.0)
    }

    @Test
    fun testHaversineDistanceBetweenNewYorkAndLosAngeles() {
        // New York: 40.7128° N, 74.0060° W
        // Los Angeles: 34.0522° N, 118.2437° W
        // Known great circle distance: ~3,935 km
        val nyLat = 40.7128
        val nyLon = -74.0060
        val laLat = 34.0522
        val laLon = -118.2437

        val distanceMeters = DistanceCalculator.haversineDistance(nyLat, nyLon, laLat, laLon)

        assertEquals(3935000.0, distanceMeters, 20000.0)
    }

    @Test
    fun testDistanceSymmetry() {
        val lat1 = 12.9716
        val lon1 = 77.5946
        val lat2 = 28.7041
        val lon2 = 77.1025

        val d1 = DistanceCalculator.haversineDistance(lat1, lon1, lat2, lon2)
        val d2 = DistanceCalculator.haversineDistance(lat2, lon2, lat1, lon1)

        assertEquals(d1, d2, 0.0001)
    }

    @Test
    fun testEquatorialDegreeDistance() {
        // 1 degree of longitude at equator = ~111.195 km
        val d = DistanceCalculator.haversineDistance(0.0, 0.0, 0.0, 1.0)
        assertEquals(111195.0, d, 500.0)
    }

    @Test
    fun testInitialBearingCardinals() {
        // North
        val northBearing = DistanceCalculator.initialBearing(0.0, 0.0, 10.0, 0.0)
        assertEquals(0.0, northBearing, 0.01)

        // East
        val eastBearing = DistanceCalculator.initialBearing(0.0, 0.0, 0.0, 10.0)
        assertEquals(90.0, eastBearing, 0.01)

        // South
        val southBearing = DistanceCalculator.initialBearing(10.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, southBearing, 0.01)

        // West
        val westBearing = DistanceCalculator.initialBearing(0.0, 10.0, 0.0, 0.0)
        assertEquals(270.0, westBearing, 0.01)
    }

    @Test
    fun testBearingToCardinalString() {
        assertEquals("N", DistanceCalculator.bearingToCardinal(0.0))
        assertEquals("N", DistanceCalculator.bearingToCardinal(359.0))
        assertEquals("N", DistanceCalculator.bearingToCardinal(10.0))
        assertEquals("NE", DistanceCalculator.bearingToCardinal(45.0))
        assertEquals("E", DistanceCalculator.bearingToCardinal(90.0))
        assertEquals("SE", DistanceCalculator.bearingToCardinal(135.0))
        assertEquals("S", DistanceCalculator.bearingToCardinal(180.0))
        assertEquals("SW", DistanceCalculator.bearingToCardinal(225.0))
        assertEquals("W", DistanceCalculator.bearingToCardinal(270.0))
        assertEquals("NW", DistanceCalculator.bearingToCardinal(315.0))
    }

    @Test
    fun testIsWithinRadius() {
        val centerLat = 37.7749
        val centerLon = -122.4194

        // Exact point is within 10m
        assertTrue(DistanceCalculator.isWithinRadius(centerLat, centerLon, centerLat, centerLon, 10.0))

        // Point ~111 km away is not within 500m
        assertFalse(DistanceCalculator.isWithinRadius(centerLat, centerLon, centerLat + 1.0, centerLon, 500.0))

        // Point ~111 km away is within 150 km
        assertTrue(DistanceCalculator.isWithinRadius(centerLat, centerLon, centerLat + 1.0, centerLon, 150_000.0))
    }

    @Test
    fun testValidCoordinateRanges() {
        assertTrue(DistanceCalculator.isValidCoordinate(0.0, 0.0))
        assertTrue(DistanceCalculator.isValidCoordinate(90.0, 180.0))
        assertTrue(DistanceCalculator.isValidCoordinate(-90.0, -180.0))

        assertFalse(DistanceCalculator.isValidCoordinate(90.001, 0.0))
        assertFalse(DistanceCalculator.isValidCoordinate(-90.001, 0.0))
        assertFalse(DistanceCalculator.isValidCoordinate(0.0, 180.001))
        assertFalse(DistanceCalculator.isValidCoordinate(0.0, -180.001))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidCoordinatesThrowException() {
        DistanceCalculator.haversineDistance(100.0, 0.0, 0.0, 0.0)
    }
}

package com.example.droidsample.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility object for pure mathematical calculations on geographic coordinates:
 * Haversine great-circle distance, initial bearing, proximity radius tests, and coordinate validation.
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0 // Mean radius of Earth in meters

    /**
     * Calculates the great-circle distance between two points on the Earth surface using the Haversine formula.
     *
     * @param lat1 Latitude of point 1 in degrees (-90.0 to 90.0)
     * @param lon1 Longitude of point 1 in degrees (-180.0 to 180.0)
     * @param lat2 Latitude of point 2 in degrees (-90.0 to 90.0)
     * @param lon2 Longitude of point 2 in degrees (-180.0 to 180.0)
     * @return Distance in meters
     */
    fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            throw IllegalArgumentException("Invalid coordinate parameters provided for distance calculation.")
        }

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val originLatRad = Math.toRadians(lat1)
        val targetLatRad = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(originLatRad) * cos(targetLatRad) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the initial bearing (forward azimuth) from point 1 to point 2 along a great-circle path.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Bearing in degrees, normalized to 0.0 <= bearing < 360.0
     */
    fun initialBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            throw IllegalArgumentException("Invalid coordinate parameters provided for bearing calculation.")
        }

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)

        var bearingDeg = Math.toDegrees(atan2(y, x))
        bearingDeg = (bearingDeg + 360.0) % 360.0

        return bearingDeg
    }

    /**
     * Checks if a target coordinate is within a given radius (in meters) of a center coordinate.
     */
    fun isWithinRadius(
        centerLat: Double,
        centerLon: Double,
        currentLat: Double,
        currentLon: Double,
        radiusMeters: Double
    ): Boolean {
        require(radiusMeters >= 0.0) { "Radius must be non-negative" }
        val distance = haversineDistance(centerLat, centerLon, currentLat, currentLon)
        return distance <= radiusMeters
    }

    /**
     * Validates whether latitude and longitude numbers are within acceptable geographic ranges.
     */
    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Converts bearing degrees (0..360) to a cardinal direction string (N, NE, E, SE, S, SW, W, NW).
     */
    fun bearingToCardinal(bearingDegrees: Double): String {
        val normalized = (bearingDegrees % 360.0 + 360.0) % 360.0
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "N"
            normalized < 67.5 -> "NE"
            normalized < 112.5 -> "E"
            normalized < 157.5 -> "SE"
            normalized < 202.5 -> "S"
            normalized < 247.5 -> "SW"
            normalized < 292.5 -> "W"
            else -> "NW"
        }
    }
}

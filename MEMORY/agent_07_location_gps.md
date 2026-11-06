# Subagent 07: Location, GPS & Geofencing Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android location providers, GNSS satellite tracking, Geocoding, Geofencing, and mock location detection APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/location/`
  - `NativeLocationManager.kt` (`LocationManager` GPS, Network, Passive providers)
  - `GnssStatusManager.kt` (`GnssStatus.Callback` - satellite inventory, constellations, SNR, elevation, azimuth)
  - `GeocodingHelper.kt` (`Geocoder` forward & reverse geocoding with Android 13+ async callbacks)
  - `GeofenceManager.kt` (`LocationManager.addProximityAlert()` and Geofencing boundaries)
  - `MockLocationDetector.kt` (`Location.isMock()` and developer option checks)
  - `LocationTrackerService.kt` (Foreground service for continuous background location updates)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/location/`
  - `LocationScreen.kt` (Parent dashboard)
  - `GpsDashboardView.kt` (Live coordinates, speedometer, altitude, accuracy indicator, compass bearing)
  - `GnssSatellitesView.kt` (Visual list of tracked/used GNSS satellites with constellation flags)
  - `GeocodingView.kt` (Address lookup, reverse geocode coordinate picker)
  - `GeofencingView.kt` (Proximity alert setup with radius trigger test)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/location/`
  - `DistanceCalculatorTest.kt` (Haversine distance and bearing calculation tests)
  - `MockLocationRuleTest.kt` (Mock location indicator validation tests)

## Detailed Implementation Checklist

### 1. Android Native LocationManager
- [ ] Inspect available providers: `LocationManager.GPS_PROVIDER`, `NETWORK_PROVIDER`, `PASSIVE_PROVIDER`.
- [ ] Check provider enabled status: `isProviderEnabled()`.
- [ ] Query `getLastKnownLocation(provider)`.
- [ ] Request single update: `getCurrentLocation()` (Android 11+).
- [ ] Request continuous updates: `requestLocationUpdates()` with `LocationListener`.
- [ ] Read comprehensive location metrics:
  - Latitude, Longitude.
  - Altitude (WGS84) & Vertical Accuracy (`getVerticalAccuracyMeters()`).
  - Speed (`getSpeed()`) & Speed Accuracy (`getSpeedAccuracyMetersPerSecond()`).
  - Bearing (`getBearing()`) & Bearing Accuracy (`getBearingAccuracyDegrees()`).
  - Horizontal Accuracy (`getAccuracy()`).
  - Elapsed Realtime (`getElapsedRealtimeNanos()`).
  - Is Mock Location (`isMock()`).

### 2. GNSS Satellite Status (`GnssStatus.Callback`)
- [ ] Register `GnssStatus.Callback` via `LocationManager.registerGnssStatusCallback()`.
- [ ] Track GNSS events: `onStarted()`, `onStopped()`, `onFirstFix(ttffMillis)`, `onSatelliteStatusChanged(status)`.
- [ ] For each satellite in `GnssStatus`:
  - Satellite ID (SVID).
  - Constellation type: `CONSTELLATION_GPS`, `CONSTELLATION_GLONASS`, `CONSTELLATION_BEIDOU`, `CONSTELLATION_GALILEO`, `CONSTELLATION_QZSS`, `CONSTELLATION_SBAS`.
  - Carrier-to-noise density (C/N0 in dB-Hz).
  - Elevation angle and Azimuth angle in degrees.
  - Has ephemeris data (`hasEphemerisData()`).
  - Has almanac data (`hasAlmanacData()`).
  - Used in fix calculation (`usedInFix()`).
  - Carrier frequency (`hasCarrierFrequencyHz()`).

### 3. Geocoding & Reverse Geocoding
- [ ] `Geocoder.isPresent()` hardware/service availability check.
- [ ] Forward geocoding: address string to list of `Address` coordinates.
- [ ] Reverse geocoding: latitude/longitude to street, city, state, postal code, country name.
- [ ] Dual implementation:
  - Modern Android 13+ (API 33+) callback-based `getFromLocation(lat, lon, maxResults, GeocodeListener)`.
  - Legacy synchronous fallback on older API levels with coroutine dispatcher.

### 4. Geofencing & Proximity Alerts
- [ ] `LocationManager.addProximityAlert(latitude, longitude, radiusMeters, expiration, pendingIntent)`.
- [ ] Handle enter / exit proximity broadcasts.
- [ ] Calculate real-time distance to target waypoint using Haversine formula and `Location.distanceBetween()`.

### 5. Mock Location Detection
- [ ] Check `location.isMock()` (API 31+) and legacy `location.isFromMockProvider()`.
- [ ] Check system settings `Settings.Secure.ALLOW_MOCK_LOCATION` where permitted.
- [ ] Display prominent security badge if mock location is active.

### 6. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Real-Time GPS Dashboard (Digital speedometer, coordinates card, accuracy circle, compass pointer).
  2. GNSS Sky & Satellite Viewer (Constellation breakdown, satellite count in view vs used in fix, C/N0 bar chart).
  3. Geocoding Lab (Search address, display reverse geocoded components in structured card).
  4. Proximity & Geofence Monitor (Set target coordinate and radius, view live distance countdown and trigger alerts).
- Manage runtime permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`).

### 7. Tests
- Tests for Haversine distance calculations, coordinate boundary validation, and GNSS constellation name resolution.

# Subagent 08: Sensor Suite Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android sensor discovery, motion, environmental, orientation, and health sensor monitoring APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/sensors/`
  - `SensorInventoryManager.kt` (`SensorManager.getSensorList()`, metadata, power, reporting modes)
  - `MotionSensorManager.kt` (Accelerometer, Gyroscope, Gravity, Linear Acceleration, Rotation Vector)
  - `EnvironmentalSensorManager.kt` (Light, Barometer/Pressure, Ambient Temperature, Relative Humidity)
  - `PositionSensorManager.kt` (Magnetometer, Proximity, Orientation matrix calculation)
  - `HealthSensorManager.kt` (Step Counter, Step Detector, Significant Motion Trigger)
  - `NovelSensorManager.kt` (Hinge Angle, Device Posture for foldables)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/sensors/`
  - `SensorsScreen.kt` (Parent dashboard)
  - `SensorCatalogView.kt` (Comprehensive hardware sensor list with vendor, version, delay, power)
  - `MotionOscilloscopeView.kt` (Real-time X/Y/Z multi-line graph visualizer on Compose Canvas)
  - `EnvironmentalSensorsView.kt` (Gauges for Lux light, hPa pressure, altitude estimation)
  - `Compass3DView.kt` (3D rotation tilt visualizer and azimuth compass)
  - `StepTrackerView.kt` (Live step counter with animated progress dial)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/sensors/`
  - `RotationMatrixTest.kt` (Matrix math tests for orientation Euler angles)
  - `SensorFilterTest.kt` (Low-pass and high-pass noise filtering tests)

## Detailed Implementation Checklist

### 1. Sensor Discovery & Metadata
- [ ] `SensorManager.getSensorList(Sensor.TYPE_ALL)`.
- [ ] For each sensor, read:
  - Name, Vendor, Version, Type string.
  - Maximum Range, Resolution, Power consumption in mA.
  - Min Delay / Max Delay in microseconds.
  - Reporting mode (Continuous, On-change, One-shot, Special-trigger).
  - Is Wake-up sensor vs Non-wake-up sensor.
  - Is Dynamic sensor (`isDynamicSensor()`).
  - FIFO max event count and reserved count.

### 2. Motion Sensors
- [ ] Accelerometer (`Sensor.TYPE_ACCELEROMETER`): raw 3-axis force in m/s^2.
- [ ] Gyroscope (`Sensor.TYPE_GYROSCOPE`): angular speed in rad/s.
- [ ] Gravity (`Sensor.TYPE_GRAVITY`): directional gravity vector.
- [ ] Linear Acceleration (`Sensor.TYPE_LINEAR_ACCELERATION`): acceleration minus gravity.
- [ ] Rotation Vector (`Sensor.TYPE_ROTATION_VECTOR`): unit quaternion components.
- [ ] Game Rotation Vector (`Sensor.TYPE_GAME_ROTATION_VECTOR`): uncalibrated for gaming.

### 3. Environmental Sensors
- [ ] Ambient Light (`Sensor.TYPE_LIGHT`): illuminance in Lux (lx).
- [ ] Atmospheric Pressure / Barometer (`Sensor.TYPE_PRESSURE`): pressure in hPa/mbar.
  - Calculate estimated barometric altitude using `SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)`.
- [ ] Ambient Temperature (`Sensor.TYPE_AMBIENT_TEMPERATURE`): degrees Celsius.
- [ ] Relative Humidity (`Sensor.TYPE_RELATIVE_HUMIDITY`): percent %.

### 4. Position & Magnetic Sensors
- [ ] Magnetic Field / Magnetometer (`Sensor.TYPE_MAGNETIC_FIELD`): geomagnetic field in micro-Tesla (uT).
- [ ] Proximity (`Sensor.TYPE_PROXIMITY`): distance in cm or binary Near/Far indicator.
- [ ] Orientation calculation:
  - Fuse Accelerometer and Magnetometer via `SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)`.
  - Extract Azimuth, Pitch, and Roll using `SensorManager.getOrientation(R, values)`.

### 5. Health, Pedometer & Novel Sensors
- [ ] Step Counter (`Sensor.TYPE_STEP_COUNTER`): total steps since device boot (requires `ACTIVITY_RECOGNITION`).
- [ ] Step Detector (`Sensor.TYPE_STEP_DETECTOR`): event on every individual step.
- [ ] Significant Motion Trigger (`Sensor.TYPE_SIGNIFICANT_MOTION`): `TriggerEventListener` for waking up device.
- [ ] Hinge Angle (`Sensor.TYPE_HINGE_ANGLE`): foldable screen angle in degrees (0 - 360).

### 6. Sensor Listener Management & Rates
- [ ] Configurable sampling delays:
  - `SensorManager.SENSOR_DELAY_FASTEST` (0 ms)
  - `SensorManager.SENSOR_DELAY_GAME` (20 ms)
  - `SensorManager.SENSOR_DELAY_UI` (66 ms)
  - `SensorManager.SENSOR_DELAY_NORMAL` (200 ms)
- [ ] Batching support via `maxReportLatencyUs`.
- [ ] Clean lifecycle unregistration in `onPause` / `onDispose`.

### 7. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Sensor Catalog (Card per hardware sensor with status indicator [Active / Standby / Not Present]).
  2. Live Motion Oscilloscope (Real-time Canvas wave with red/green/blue curves for X/Y/Z).
  3. Environmental Meters (Digital gauges for Lux, barometric pressure, estimated altitude).
  4. 3D Orientation & Compass (Azimuth dial pointing North + pitch/roll tilt box).
  5. Step Counter (Circular step tracker with daily target and step event animation).
- Manage runtime permissions (`ACTIVITY_RECOGNITION`, `BODY_SENSORS`).

### 8. Tests
- Tests for rotation matrix conversion, pitch/roll calculation, and low-pass filter smoothing.

package com.example.droidsample.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build

/**
 * Categorization of Android hardware and software sensors.
 */
enum class SensorCategory(val displayName: String) {
    ALL("All Sensors"),
    MOTION("Motion"),
    ENVIRONMENTAL("Environmental"),
    POSITION("Position & Orientation"),
    HEALTH("Health & Pedometer"),
    OTHER("Other")
}

/**
 * Sensor reporting mode indicating when events are dispatched.
 */
enum class SensorReportingMode(val displayName: String) {
    CONTINUOUS("Continuous"),
    ON_CHANGE("On Change"),
    ONE_SHOT("One-Shot"),
    SPECIAL_TRIGGER("Special Trigger"),
    UNKNOWN("Unknown")
}

/**
 * Comprehensive metadata description for a hardware or composite sensor.
 */
data class SensorMetadata(
    val id: Int,
    val name: String,
    val vendor: String,
    val version: Int,
    val type: Int,
    val typeName: String,
    val category: SensorCategory,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val reportingMode: SensorReportingMode,
    val isWakeUpSensor: Boolean,
    val isDynamicSensor: Boolean,
    val fifoMaxEventCount: Int,
    val fifoReservedEventCount: Int,
    val stringType: String
)

/**
 * Discovers and inspects device hardware and synthetic sensors via [SensorManager].
 */
class SensorInventoryManager(private val context: Context) {

    val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    /**
     * Queries all available sensors on the device.
     */
    fun getAllSensors(): List<SensorMetadata> {
        val rawList = sensorManager.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
        return rawList.map { sensorToMetadata(it) }
    }

    /**
     * Filters sensors by broad architectural category.
     */
    fun getSensorsByCategory(category: SensorCategory): List<SensorMetadata> {
        val all = getAllSensors()
        return if (category == SensorCategory.ALL) all else all.filter { it.category == category }
    }

    /**
     * Checks if a specific sensor type is present on this hardware.
     */
    fun isSensorAvailable(sensorType: Int): Boolean {
        return sensorManager.getDefaultSensor(sensorType) != null
    }

    /**
     * Returns the default sensor instance for a given type.
     */
    fun getDefaultSensor(sensorType: Int): Sensor? {
        return sensorManager.getDefaultSensor(sensorType)
    }

    private fun sensorToMetadata(sensor: Sensor): SensorMetadata {
        val reportingMode = when (sensor.reportingMode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> SensorReportingMode.CONTINUOUS
            Sensor.REPORTING_MODE_ON_CHANGE -> SensorReportingMode.ON_CHANGE
            Sensor.REPORTING_MODE_ONE_SHOT -> SensorReportingMode.ONE_SHOT
            Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> SensorReportingMode.SPECIAL_TRIGGER
            else -> SensorReportingMode.UNKNOWN
        }

        val isDynamic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sensor.isDynamicSensor
        } else {
            false
        }

        val stringType = sensor.stringType ?: "android.sensor.unknown"

        return SensorMetadata(
            id = sensor.type,
            name = sensor.name.orEmpty(),
            vendor = sensor.vendor.orEmpty(),
            version = sensor.version,
            type = sensor.type,
            typeName = getSensorTypeName(sensor.type),
            category = getSensorCategory(sensor.type),
            maxRange = sensor.maximumRange,
            resolution = sensor.resolution,
            powerMa = sensor.power,
            minDelayUs = sensor.minDelay,
            maxDelayUs = sensor.maxDelay,
            reportingMode = reportingMode,
            isWakeUpSensor = sensor.isWakeUpSensor,
            isDynamicSensor = isDynamic,
            fifoMaxEventCount = sensor.fifoMaxEventCount,
            fifoReservedEventCount = sensor.fifoReservedEventCount,
            stringType = stringType
        )
    }

    companion object {
        @Suppress("DEPRECATION")
        fun getSensorCategory(sensorType: Int): SensorCategory {
            return when (sensorType) {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                Sensor.TYPE_GRAVITY,
                Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GAME_ROTATION_VECTOR -> SensorCategory.MOTION

                Sensor.TYPE_LIGHT,
                Sensor.TYPE_PRESSURE,
                Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_TEMPERATURE -> SensorCategory.ENVIRONMENTAL

                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                Sensor.TYPE_PROXIMITY,
                Sensor.TYPE_ORIENTATION,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> SensorCategory.POSITION

                Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_STEP_DETECTOR,
                Sensor.TYPE_SIGNIFICANT_MOTION,
                Sensor.TYPE_HEART_RATE,
                Sensor.TYPE_HEART_BEAT -> SensorCategory.HEALTH

                else -> SensorCategory.OTHER
            }
        }

        fun getSensorTypeName(sensorType: Int): String {
            return when (sensorType) {
                Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
                Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (Uncalibrated)"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope"
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (Uncalibrated)"
                Sensor.TYPE_GRAVITY -> "Gravity"
                Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
                Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
                Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
                Sensor.TYPE_LIGHT -> "Ambient Light"
                Sensor.TYPE_PRESSURE -> "Barometer / Pressure"
                Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
                Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
                Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer (Magnetic Field)"
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (Uncalibrated)"
                Sensor.TYPE_PROXIMITY -> "Proximity"
                Sensor.TYPE_STEP_COUNTER -> "Step Counter"
                Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
                Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Rotation Vector"
                Sensor.TYPE_STATIONARY_DETECT -> "Stationary Detect"
                Sensor.TYPE_MOTION_DETECT -> "Motion Detect"
                Sensor.TYPE_HEART_RATE -> "Heart Rate"
                Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Off-body Detect"
                Sensor.TYPE_HINGE_ANGLE -> "Hinge Angle"
                else -> "Custom / Unknown ($sensorType)"
            }
        }
    }
}

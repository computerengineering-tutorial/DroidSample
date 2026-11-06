package com.example.droidsample.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Magnetometer reading representing geomagnetic flux density in micro-Tesla (μT).
 */
data class MagneticFieldSample(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val totalFieldUt: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val timestamp: Long = 0L
)

/**
 * Proximity sensor reading indicating distance in cm and binary Near/Far state.
 */
data class ProximitySample(
    val distanceCm: Float = 0f,
    val isNear: Boolean = false,
    val maxRangeCm: Float = 5f,
    val timestamp: Long = 0L
)

/**
 * 3D Device orientation angles computed from accelerometer + magnetometer sensor fusion.
 */
data class OrientationSample(
    val azimuthDegrees: Float = 0f, // Compass heading: 0° = North, 90° = East, 180° = South, 270° = West
    val pitchDegrees: Float = 0f,   // Tilt forward / backward (-90° to +90°)
    val rollDegrees: Float = 0f,    // Tilt left / right (-180° to +180°)
    val azimuthRad: Float = 0f,
    val pitchRad: Float = 0f,
    val rollRad: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val timestamp: Long = 0L
)

/**
 * Manages position and magnetic sensors: Magnetometer, Proximity, and fused 3D Orientation.
 */
class PositionSensorManager(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    fun hasMagnetometer(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    fun hasProximitySensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
    fun hasOrientationFusion(): Boolean = hasMagnetometer() && (
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
    )

    /**
     * Emits raw 3-axis geomagnetic field readings in μT.
     */
    fun observeMagneticField(
        samplingDelay: SamplingDelay = SamplingDelay.UI
    ): Flow<MagneticFieldSample> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val total = sqrt(x * x + y * y + z * z)

                trySend(
                    MagneticFieldSample(
                        x = x,
                        y = y,
                        z = z,
                        totalFieldUt = total,
                        accuracy = event.accuracy,
                        timestamp = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Emits proximity sensor measurements in cm and near/far status.
     */
    fun observeProximity(
        samplingDelay: SamplingDelay = SamplingDelay.UI
    ): Flow<ProximitySample> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val maxRange = sensor.maximumRange

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.isEmpty()) return
                val distance = event.values[0]
                val isNear = distance < maxRange

                trySend(
                    ProximitySample(
                        distanceCm = distance,
                        isNear = isNear,
                        maxRangeCm = maxRange,
                        timestamp = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Fuses Accelerometer and Magnetometer streams to calculate full 3D Orientation (Azimuth, Pitch, Roll).
     */
    fun observeOrientation(
        samplingDelay: SamplingDelay = SamplingDelay.UI
    ): Flow<OrientationSample> = callbackFlow {
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelSensor == null || magSensor == null) {
            close()
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

        val rMatrix = FloatArray(9)
        val iMatrix = FloatArray(9)
        val orientationRad = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                        lastAccuracy = event.accuracy
                    }
                }

                if (hasGravity && hasGeomagnetic) {
                    val success = SensorManager.getRotationMatrix(rMatrix, iMatrix, gravity, geomagnetic)
                    if (success) {
                        SensorManager.getOrientation(rMatrix, orientationRad)
                        val azRad = orientationRad[0]
                        val pitchRad = orientationRad[1]
                        val rollRad = orientationRad[2]

                        val azDeg = OrientationMath.radiansToDegrees360(azRad)
                        val pitchDeg = OrientationMath.radiansToDegrees(pitchRad)
                        val rollDeg = OrientationMath.radiansToDegrees(rollRad)

                        trySend(
                            OrientationSample(
                                azimuthDegrees = azDeg,
                                pitchDegrees = pitchDeg,
                                rollDegrees = rollDeg,
                                azimuthRad = azRad,
                                pitchRad = pitchRad,
                                rollRad = rollRad,
                                accuracy = lastAccuracy,
                                timestamp = event.timestamp
                            )
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    lastAccuracy = accuracy
                }
            }
        }

        sensorManager.registerListener(listener, accelSensor, samplingDelay.delayUs)
        sensorManager.registerListener(listener, magSensor, samplingDelay.delayUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

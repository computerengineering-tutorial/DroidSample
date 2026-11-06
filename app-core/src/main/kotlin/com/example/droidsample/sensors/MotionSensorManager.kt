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
 * Sampling delay profiles for hardware sensors.
 */
enum class SamplingDelay(val delayUs: Int, val displayName: String) {
    FASTEST(SensorManager.SENSOR_DELAY_FASTEST, "Fastest (0 ms)"),
    GAME(SensorManager.SENSOR_DELAY_GAME, "Game (20 ms)"),
    UI(SensorManager.SENSOR_DELAY_UI, "UI (66 ms)"),
    NORMAL(SensorManager.SENSOR_DELAY_NORMAL, "Normal (200 ms)")
}

/**
 * 3-Axis motion data sample with vector magnitude calculation.
 */
data class MotionSample(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestamp: Long = 0L,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val sensorType: Int = Sensor.TYPE_ACCELEROMETER
) {
    val magnitude: Float
        get() = sqrt(x * x + y * y + z * z)
}

/**
 * Rotation vector sample representing device 3D orientation quaternion.
 */
data class RotationVectorSample(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val scalar: Float = 1f,
    val estimatedAccuracyRad: Float = 0f,
    val timestamp: Long = 0L
)

/**
 * Manages motion-based sensors: Accelerometer, Gyroscope, Gravity, Linear Acceleration,
 * and Rotation Vectors.
 */
class MotionSensorManager(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    /**
     * Checks if a specific motion sensor is supported on this hardware.
     */
    fun hasSensor(sensorType: Int): Boolean {
        return sensorManager.getDefaultSensor(sensorType) != null
    }

    /**
     * Emits a reactive flow of 3-axis readings from the requested motion sensor.
     * Optionally applies a [LowPassFilter] or [HighPassFilter].
     */
    fun observeMotionSensor(
        sensorType: Int,
        samplingDelay: SamplingDelay = SamplingDelay.UI,
        maxReportLatencyUs: Int = 0,
        filterMode: FilterMode = FilterMode.NONE,
        filterAlpha: Float = 0.2f
    ): Flow<MotionSample> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val lowPass = if (filterMode == FilterMode.LOW_PASS) LowPassFilter(filterAlpha) else null
        val highPass = if (filterMode == FilterMode.HIGH_PASS) HighPassFilter(filterAlpha) else null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return

                var rawValues = floatArrayOf(event.values[0], event.values[1], event.values[2])
                if (lowPass != null) {
                    rawValues = lowPass.filter(rawValues)
                } else if (highPass != null) {
                    rawValues = highPass.filter(rawValues)
                }

                trySend(
                    MotionSample(
                        x = rawValues[0],
                        y = rawValues[1],
                        z = rawValues[2],
                        timestamp = event.timestamp,
                        accuracy = event.accuracy,
                        sensorType = sensorType
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Accuracy updates can be handled or forwarded
            }
        }

        if (maxReportLatencyUs > 0) {
            sensorManager.registerListener(listener, sensor, samplingDelay.delayUs, maxReportLatencyUs)
        } else {
            sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Emits unit quaternion components from [Sensor.TYPE_ROTATION_VECTOR]
     * or [Sensor.TYPE_GAME_ROTATION_VECTOR].
     */
    fun observeRotationVector(
        useGameRotationVector: Boolean = false,
        samplingDelay: SamplingDelay = SamplingDelay.GAME
    ): Flow<RotationVectorSample> = callbackFlow {
        val sensorType = if (useGameRotationVector) {
            Sensor.TYPE_GAME_ROTATION_VECTOR
        } else {
            Sensor.TYPE_ROTATION_VECTOR
        }

        val sensor = sensorManager.getDefaultSensor(sensorType)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

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
                val scalar = if (event.values.size > 3) event.values[3] else 1f
                val accuracy = if (event.values.size > 4) event.values[4] else 0f

                trySend(
                    RotationVectorSample(
                        x = x,
                        y = y,
                        z = z,
                        scalar = scalar,
                        estimatedAccuracyRad = accuracy,
                        timestamp = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

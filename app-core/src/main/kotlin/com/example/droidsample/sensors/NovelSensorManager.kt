package com.example.droidsample.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Geometric posture classification for foldable and dual-screen form factors.
 */
enum class DevicePosture(val displayName: String, val description: String) {
    CLOSED("Closed", "0° - Device is fully closed"),
    HALF_OPENED("Half-Opened / Flex", "1° - 169° - Laptop / tabletop tent posture"),
    FLAT("Flat / Unfolded", "170° - 190° - Fully flat tablet posture"),
    REVERSE_FLEX("Reverse Flex", "191° - 359° - Reverse folded / display outward"),
    FLIPPED("Flipped", "360° - Fully flipped back-to-back"),
    UNKNOWN("Unknown / Unavailable", "Hinge sensor not present on this hardware")
}

/**
 * Hinge angle reading for foldable devices.
 */
data class HingeAngleSample(
    val angleDegrees: Float = 0f,
    val posture: DevicePosture = DevicePosture.UNKNOWN,
    val timestamp: Long = 0L
)

/**
 * Manages novel and emerging hardware sensors such as the Foldable Hinge Angle sensor.
 */
class NovelSensorManager(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    /**
     * Checks if the device has a physical or virtual hinge angle sensor.
     */
    fun hasHingeAngleSensor(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        } else {
            false
        }
    }

    /**
     * Emits real-time hinge angle measurements (0° - 360°) and derived [DevicePosture].
     */
    fun observeHingeAngle(
        samplingDelay: SamplingDelay = SamplingDelay.UI
    ): Flow<HingeAngleSample> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            close()
            return@callbackFlow
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.isEmpty()) return
                val angle = event.values[0]
                val posture = classifyPosture(angle)

                trySend(
                    HingeAngleSample(
                        angleDegrees = angle,
                        posture = posture,
                        timestamp = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    companion object {
        fun classifyPosture(angle: Float): DevicePosture {
            return when {
                angle <= 2f -> DevicePosture.CLOSED
                angle in 3f..169f -> DevicePosture.HALF_OPENED
                angle in 170f..190f -> DevicePosture.FLAT
                angle in 191f..358f -> DevicePosture.REVERSE_FLEX
                angle >= 359f -> DevicePosture.FLIPPED
                else -> DevicePosture.UNKNOWN
            }
        }
    }
}

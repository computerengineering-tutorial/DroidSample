package com.example.droidsample.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pedometer step counting data.
 */
data class StepCountSample(
    val totalBootSteps: Long = 0L,
    val sessionSteps: Long = 0L,
    val timestamp: Long = 0L
)

/**
 * Manages health and pedometer sensors: Step Counter, Step Detector, and Significant Motion Trigger.
 */
class HealthSensorManager(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var initialBootSteps: Long = -1L

    /**
     * Checks if the required activity recognition permission is granted.
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasStepCounter(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    fun hasStepDetector(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    fun hasSignificantMotion(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null

    /**
     * Emits cumulative step counts from [Sensor.TYPE_STEP_COUNTER].
     */
    fun observeStepCounter(
        samplingDelay: SamplingDelay = SamplingDelay.UI
    ): Flow<StepCountSample> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.isEmpty()) return
                val currentBootSteps = event.values[0].toLong()

                if (initialBootSteps < 0L) {
                    initialBootSteps = currentBootSteps
                }

                val sessionSteps = (currentBootSteps - initialBootSteps).coerceAtLeast(0L)

                trySend(
                    StepCountSample(
                        totalBootSteps = currentBootSteps,
                        sessionSteps = sessionSteps,
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
     * Resets the session step baseline so session count restarts at 0.
     */
    fun resetSessionSteps() {
        initialBootSteps = -1L
    }

    /**
     * Emits a timestamp for every individual step detected by [Sensor.TYPE_STEP_DETECTOR].
     */
    fun observeStepDetector(): Flow<Long> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty() && event.values[0] == 1.0f) {
                    trySend(event.timestamp)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Arms the [Sensor.TYPE_SIGNIFICANT_MOTION] one-shot trigger.
     * Invokes [onTrigger] when significant movement (walking, driving, cycling) is detected.
     */
    fun armSignificantMotionTrigger(
        onTrigger: (TriggerEvent) -> Unit
    ): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return false

        val triggerListener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                if (event != null) {
                    onTrigger(event)
                }
            }
        }

        return sensorManager.requestTriggerSensor(triggerListener, sensor)
    }

    /**
     * Cancels an armed significant motion trigger.
     */
    fun cancelSignificantMotionTrigger(listener: TriggerEventListener): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return false
        return sensorManager.cancelTriggerSensor(listener, sensor)
    }
}

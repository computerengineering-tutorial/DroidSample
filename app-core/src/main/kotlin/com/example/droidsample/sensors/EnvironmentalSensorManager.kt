package com.example.droidsample.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.pow

/**
 * Snapshot of environmental sensor readings.
 */
data class EnvironmentalData(
    val lightLux: Float? = null,
    val pressureHpa: Float? = null,
    val estimatedAltitudeMeters: Float? = null,
    val ambientTemperatureCelsius: Float? = null,
    val relativeHumidityPercent: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages environmental sensors: Ambient Light, Barometer/Pressure, Ambient Temperature,
 * and Relative Humidity.
 */
class EnvironmentalSensorManager(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    /**
     * Checks availability of environmental sensors.
     */
    fun hasLightSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    fun hasPressureSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    fun hasTemperatureSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null
    fun hasHumiditySensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY) != null

    /**
     * Emits ambient light intensity in Lux (lx).
     */
    fun observeLight(samplingDelay: SamplingDelay = SamplingDelay.UI): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    trySend(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Emits atmospheric pressure in hectopascals (hPa / mbar) and calculated altitude in meters.
     */
    fun observePressureAndAltitude(
        samplingDelay: SamplingDelay = SamplingDelay.UI,
        seaLevelPressureHpa: Float = SensorManager.PRESSURE_STANDARD_ATMOSPHERE
    ): Flow<Pair<Float, Float>> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    val pressure = event.values[0]
                    val altitude = calculateAltitude(seaLevelPressureHpa, pressure)
                    trySend(Pair(pressure, altitude))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Emits ambient room temperature in degrees Celsius (°C).
     */
    fun observeAmbientTemperature(samplingDelay: SamplingDelay = SamplingDelay.UI): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    trySend(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Emits relative ambient humidity as a percentage (%).
     */
    fun observeRelativeHumidity(samplingDelay: SamplingDelay = SamplingDelay.UI): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    trySend(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingDelay.delayUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    /**
     * Combined flow aggregating all available environmental readings into [EnvironmentalData].
     */
    fun observeAllEnvironmental(samplingDelay: SamplingDelay = SamplingDelay.UI): Flow<EnvironmentalData> = callbackFlow {
        var currentData = EnvironmentalData()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.isEmpty()) return

                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        currentData = currentData.copy(
                            lightLux = event.values[0],
                            timestamp = event.timestamp
                        )
                    }
                    Sensor.TYPE_PRESSURE -> {
                        val pressure = event.values[0]
                        val altitude = calculateAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                        currentData = currentData.copy(
                            pressureHpa = pressure,
                            estimatedAltitudeMeters = altitude,
                            timestamp = event.timestamp
                        )
                    }
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                        currentData = currentData.copy(
                            ambientTemperatureCelsius = event.values[0],
                            timestamp = event.timestamp
                        )
                    }
                    Sensor.TYPE_RELATIVE_HUMIDITY -> {
                        currentData = currentData.copy(
                            relativeHumidityPercent = event.values[0],
                            timestamp = event.timestamp
                        )
                    }
                }
                trySend(currentData)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val types = listOf(
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY
        )

        var registeredCount = 0
        for (type in types) {
            val s = sensorManager.getDefaultSensor(type)
            if (s != null) {
                sensorManager.registerListener(listener, s, samplingDelay.delayUs)
                registeredCount++
            }
        }

        if (registeredCount == 0) {
            // Still emit default empty object so UI knows sensors aren't present
            trySend(currentData)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        /**
         * Calculates altitude above sea level using the barometric formula.
         * H = 44330 * (1 - (p / p0)^(1/5.255))
         */
        fun calculateAltitude(p0: Float, p: Float): Float {
            if (p <= 0f || p0 <= 0f) return 0f
            return 44330.0f * (1.0f - (p / p0).pow(1.0f / 5.255f))
        }
    }
}

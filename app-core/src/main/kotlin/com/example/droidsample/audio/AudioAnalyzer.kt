package com.example.droidsample.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time audio signal processing & mathematical analysis utility.
 * Performs RMS energy calculation, peak amplitude detection, dBFS conversion,
 * waveform downsampling for Compose Canvas rendering, and FFT frequency spectrum analysis.
 */
object AudioAnalyzer {

    const val MIN_DB: Float = -100.0f
    const val MAX_DB: Float = 0.0f

    /**
     * Standard frequency band definitions in Hz: (Name, MinHz, MaxHz)
     */
    val FREQUENCY_BANDS = listOf(
        BandDef("Sub-bass", 20f, 60f),
        BandDef("Bass", 60f, 250f),
        BandDef("Low Mid", 250f, 500f),
        BandDef("Mid", 500f, 2000f),
        BandDef("High Mid", 2000f, 4000f),
        BandDef("Presence", 4000f, 6000f),
        BandDef("Brilliance", 6000f, 12000f),
        BandDef("Ultra High", 12000f, 20000f)
    )

    data class BandDef(val name: String, val minFreq: Float, val maxFreq: Float)

    data class AudioMetrics(
        val rms: Float,
        val peak: Float,
        val dbFs: Float,
        val downsampledWaveform: FloatArray,
        val frequencyBands: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioMetrics) return false
            if (rms != other.rms) return false
            if (peak != other.peak) return false
            if (dbFs != other.dbFs) return false
            if (!downsampledWaveform.contentEquals(other.downsampledWaveform)) return false
            if (!frequencyBands.contentEquals(other.frequencyBands)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = rms.hashCode()
            result = 31 * result + peak.hashCode()
            result = 31 * result + dbFs.hashCode()
            result = 31 * result + downsampledWaveform.contentHashCode()
            result = 31 * result + frequencyBands.contentHashCode()
            return result
        }
    }

    /**
     * Computes the Root Mean Square (RMS) of normalized float samples [-1.0f, 1.0f].
     * Returns a value in [0.0f, 1.0f].
     */
    fun calculateRms(samples: FloatArray, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        val count = min(length, samples.size)
        for (i in 0 until count) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Computes the Root Mean Square (RMS) of 16-bit signed shorts.
     * Returns a normalized value in [0.0f, 1.0f].
     */
    fun calculateRms(samples: ShortArray, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        val count = min(length, samples.size)
        for (i in 0 until count) {
            val v = samples[i].toDouble() / Short.MAX_VALUE
            sum += v * v
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Finds the peak absolute amplitude of normalized float samples.
     * Returns a value in [0.0f, 1.0f].
     */
    fun calculatePeak(samples: FloatArray, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var maxVal = 0f
        val count = min(length, samples.size)
        for (i in 0 until count) {
            val abs = kotlin.math.abs(samples[i])
            if (abs > maxVal) {
                maxVal = abs
            }
        }
        return maxVal.coerceIn(0f, 1f)
    }

    /**
     * Finds the peak absolute amplitude of 16-bit short samples.
     * Returns a normalized value in [0.0f, 1.0f].
     */
    fun calculatePeak(samples: ShortArray, length: Int = samples.size): Float {
        if (length <= 0) return 0f
        var maxVal = 0
        val count = min(length, samples.size)
        for (i in 0 until count) {
            val abs = kotlin.math.abs(samples[i].toInt())
            if (abs > maxVal) {
                maxVal = abs
            }
        }
        return (maxVal.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
    }

    /**
     * Converts a linear amplitude [0.0f, 1.0f] to decibels relative to full scale (dBFS).
     * Output is clamped between [MIN_DB] (-100 dBFS) and [MAX_DB] (0 dBFS).
     */
    fun amplitudeToDbFs(amplitude: Float): Float {
        if (amplitude <= 1e-5f) return MIN_DB
        val db = 20.0f * log10(amplitude)
        return db.coerceIn(MIN_DB, MAX_DB)
    }

    /**
     * Downsamples an array of normalized float samples into a fixed number of [targetPoints]
     * for smooth real-time oscilloscope or waveform drawing.
     */
    fun downsampleWaveform(samples: FloatArray, length: Int = samples.size, targetPoints: Int = 100): FloatArray {
        val count = min(length, samples.size)
        if (count <= 0 || targetPoints <= 0) return FloatArray(targetPoints)
        val result = FloatArray(targetPoints)
        val chunkSize = count.toDouble() / targetPoints

        for (i in 0 until targetPoints) {
            val startIdx = (i * chunkSize).toInt().coerceIn(0, count - 1)
            val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(startIdx + 1, count)
            var maxMagnitude = 0f
            var sampleWithMaxMag = 0f

            for (j in startIdx until endIdx) {
                val s = samples[j]
                val abs = kotlin.math.abs(s)
                if (abs >= maxMagnitude) {
                    maxMagnitude = abs
                    sampleWithMaxMag = s
                }
            }
            result[i] = sampleWithMaxMag
        }
        return result
    }

    /**
     * Computes Fast Fourier Transform (FFT) on a power-of-two window with Hann windowing,
     * and maps the resulting frequency bins into defined octave/spectrum bands.
     *
     * @param samples normalized float samples
     * @param length length of input to use
     * @param sampleRate sampling rate in Hz (e.g., 44100 or 48000)
     * @param fftSize size of FFT window (must be power of two, defaults to 512)
     * @return array of band magnitudes normalized to [0.0f, 1.0f]
     */
    fun calculateFrequencyBands(
        samples: FloatArray,
        length: Int = samples.size,
        sampleRate: Int = 44100,
        fftSize: Int = 512
    ): FloatArray {
        val numBands = FREQUENCY_BANDS.size
        val bandMagnitudes = FloatArray(numBands)
        val count = min(length, samples.size)
        if (count < fftSize) {
            return bandMagnitudes
        }

        // Apply Hann window and prepare complex real and imaginary arrays
        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        for (i in 0 until fftSize) {
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
            real[i] = samples[i].toDouble() * hann
            imag[i] = 0.0
        }

        // Execute Radix-2 Cooley-Tukey FFT
        cooleyTukeyFft(real, imag)

        // Compute magnitude spectrum for bins 0 until fftSize / 2
        val halfSize = fftSize / 2
        val magnitudes = FloatArray(halfSize)
        val binWidth = sampleRate.toFloat() / fftSize

        for (i in 0 until halfSize) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]) / fftSize
            magnitudes[i] = mag.toFloat()
        }

        // Aggregate bins into frequency bands
        for (b in 0 until numBands) {
            val band = FREQUENCY_BANDS[b]
            val minBin = (band.minFreq / binWidth).toInt().coerceIn(0, halfSize - 1)
            val maxBin = (band.maxFreq / binWidth).toInt().coerceIn(minBin + 1, halfSize)
            var bandSum = 0f
            var binCount = 0

            for (bin in minBin until maxBin) {
                bandSum += magnitudes[bin]
                binCount++
            }

            val avgMag = if (binCount > 0) bandSum / binCount else 0f
            // Scale and log compress to normalize nicely for visualizer
            val amplified = avgMag * 15f
            bandMagnitudes[b] = amplified.coerceIn(0f, 1f)
        }

        return bandMagnitudes
    }

    /**
     * In-place Radix-2 Cooley-Tukey Decimation-In-Time FFT algorithm.
     */
    fun cooleyTukeyFft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        // Bit reversal
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Butterfly computation
        var step = 1
        while (step < n) {
            val jump = step shl 1
            val delta = -PI / step
            val sinDeltaHalf = sin(0.5 * delta)
            val cosDeltaHalf = cos(0.5 * delta)
            val alpha = 2.0 * sinDeltaHalf * sinDeltaHalf
            val beta = sin(delta)

            var curCos = 1.0
            var curSin = 0.0

            for (m in 0 until step) {
                for (i in m until n step jump) {
                    val pair = i + step
                    val tr = curCos * real[pair] - curSin * imag[pair]
                    val ti = curCos * imag[pair] + curSin * real[pair]

                    real[pair] = real[i] - tr
                    imag[pair] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                }
                val newCos = curCos - (alpha * curCos + beta * curSin)
                val newSin = curSin - (alpha * curSin - beta * curCos)
                curCos = newCos
                curSin = newSin
            }
            step = jump
        }
    }

    /**
     * Analyzes a buffer of normalized float samples and returns comprehensive [AudioMetrics].
     */
    fun analyze(
        samples: FloatArray,
        length: Int = samples.size,
        sampleRate: Int = 44100,
        waveformPoints: Int = 100
    ): AudioMetrics {
        val rms = calculateRms(samples, length)
        val peak = calculatePeak(samples, length)
        val dbFs = amplitudeToDbFs(peak)
        val waveform = downsampleWaveform(samples, length, waveformPoints)
        val bands = calculateFrequencyBands(samples, length, sampleRate)
        return AudioMetrics(rms, peak, dbFs, waveform, bands)
    }

    /**
     * Converts a 16-bit PCM ShortArray to normalized FloatArray [-1.0f, 1.0f].
     */
    fun shortArrayToNormalizedFloats(shorts: ShortArray, length: Int = shorts.size): FloatArray {
        val count = min(length, shorts.size)
        val floats = FloatArray(count)
        for (i in 0 until count) {
            floats[i] = (shorts[i].toFloat() / Short.MAX_VALUE).coerceIn(-1.0f, 1.0f)
        }
        return floats
    }

    /**
     * Converts raw 16-bit Little-Endian PCM byte array to ShortArray.
     */
    fun byteArrayToShortArray(bytes: ByteArray, byteLength: Int = bytes.size): ShortArray {
        val shortCount = byteLength / 2
        val shorts = ShortArray(shortCount)
        for (i in 0 until shortCount) {
            val b0 = bytes[i * 2].toInt() and 0xFF
            val b1 = bytes[i * 2 + 1].toInt()
            shorts[i] = ((b1 shl 8) or b0).toShort()
        }
        return shorts
    }

    /**
     * Converts raw 8-bit unsigned PCM byte array to normalized FloatArray [-1.0f, 1.0f].
     */
    fun pcm8BitToNormalizedFloats(bytes: ByteArray, byteLength: Int = bytes.size): FloatArray {
        val count = min(byteLength, bytes.size)
        val floats = FloatArray(count)
        for (i in 0 until count) {
            // 8-bit PCM is unsigned: 0 = -1.0, 128 = 0.0, 255 = ~+1.0
            val unsigned = bytes[i].toInt() and 0xFF
            floats[i] = ((unsigned - 128) / 128.0f).coerceIn(-1.0f, 1.0f)
        }
        return floats
    }
}

package com.example.droidsample.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalysisTest {

    @Test
    fun testRmsSilence() {
        val silence = FloatArray(1024) { 0f }
        val rms = AudioAnalyzer.calculateRms(silence)
        assertEquals(0f, rms, 0.0001f)
    }

    @Test
    fun testRmsConstantFullScale() {
        val dc = FloatArray(1024) { 1.0f }
        val rms = AudioAnalyzer.calculateRms(dc)
        assertEquals(1.0f, rms, 0.0001f)
    }

    @Test
    fun testRmsSineWave() {
        val length = 44100
        val sine = FloatArray(length) { i ->
            sin(2.0 * PI * 440.0 * i / 44100.0).toFloat()
        }
        val rms = AudioAnalyzer.calculateRms(sine)
        // Theoretical RMS of sine wave with amplitude 1.0 is 1/sqrt(2) ≈ 0.7071
        assertEquals(0.7071f, rms, 0.01f)
    }

    @Test
    fun testPeakAmplitude() {
        val samples = floatArrayOf(0.1f, -0.45f, 0.82f, -0.95f, 0.3f)
        val peak = AudioAnalyzer.calculatePeak(samples)
        assertEquals(0.95f, peak, 0.0001f)
    }

    @Test
    fun testAmplitudeToDbFs() {
        assertEquals(0f, AudioAnalyzer.amplitudeToDbFs(1.0f), 0.01f)
        // 0.5 amplitude is 20 * log10(0.5) ≈ -6.02 dBFS
        assertEquals(-6.02f, AudioAnalyzer.amplitudeToDbFs(0.5f), 0.05f)
        // 0.1 amplitude is 20 * log10(0.1) = -20 dBFS
        assertEquals(-20.0f, AudioAnalyzer.amplitudeToDbFs(0.1f), 0.05f)
        // Zero amplitude should clamp to MIN_DB (-100 dBFS)
        assertEquals(-100.0f, AudioAnalyzer.amplitudeToDbFs(0.0f), 0.01f)
    }

    @Test
    fun testDownsampleWaveform() {
        val input = FloatArray(1000) { (it % 10) / 10f }
        val downsampled = AudioAnalyzer.downsampleWaveform(input, targetPoints = 50)
        assertEquals(50, downsampled.size)
        // Values must be within [-1f, 1f]
        for (v in downsampled) {
            assertTrue(v in -1f..1f)
        }
    }

    @Test
    fun testByteArrayToShortArray() {
        // Little endian representation:
        // Short 0x0100 (256) -> bytes: [0x00, 0x01]
        // Short -1 (0xFFFF) -> bytes: [0xFF, 0xFF]
        val bytes = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFF.toByte())
        val shorts = AudioAnalyzer.byteArrayToShortArray(bytes)
        assertEquals(2, shorts.size)
        assertEquals(256.toShort(), shorts[0])
        assertEquals((-1).toShort(), shorts[1])
    }

    @Test
    fun testShortArrayToNormalizedFloats() {
        val shorts = shortArrayOf(0, Short.MAX_VALUE, (-Short.MAX_VALUE).toShort())
        val floats = AudioAnalyzer.shortArrayToNormalizedFloats(shorts)
        assertEquals(0f, floats[0], 0.001f)
        assertEquals(1.0f, floats[1], 0.001f)
        assertEquals(-1.0f, floats[2], 0.001f)
    }

    @Test
    fun testFrequencyBandsSineWaveDetection() {
        val sampleRate = 44100
        val fftSize = 512
        // Generate a 100 Hz tone (Bass band is 60 - 250 Hz)
        val bassTone = FloatArray(fftSize) { i ->
            sin(2.0 * PI * 100.0 * i / sampleRate).toFloat()
        }
        val bands = AudioAnalyzer.calculateFrequencyBands(bassTone, length = fftSize, sampleRate = sampleRate, fftSize = fftSize)
        assertEquals(AudioAnalyzer.FREQUENCY_BANDS.size, bands.size)

        // Band 1 is Bass (60 - 250 Hz), index 1
        val bassEnergy = bands[1]
        val highEnergy = bands[7] // Ultra High
        assertTrue("Bass energy ($bassEnergy) should exceed ultra-high energy ($highEnergy)", bassEnergy > highEnergy)
    }
}

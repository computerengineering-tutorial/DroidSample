package com.example.droidsample.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioConfigTest {

    @Test
    fun testPcmByteRateCalculation() {
        val sampleRate = 44100
        val channels = 2 // Stereo
        val bytesPerSample = 2 // 16-bit PCM

        val bytesPerFrame = channels * bytesPerSample
        val byteRate = sampleRate * bytesPerFrame

        assertEquals(4, bytesPerFrame)
        assertEquals(176400, byteRate) // 176.4 KB/s
    }

    @Test
    fun testPcmFloatByteRateCalculation() {
        val sampleRate = 48000
        val channels = 1 // Mono
        val bytesPerSample = 4 // Float PCM (32-bit)

        val bytesPerFrame = channels * bytesPerSample
        val byteRate = sampleRate * bytesPerFrame

        assertEquals(4, bytesPerFrame)
        assertEquals(192000, byteRate)
    }

    @Test
    fun testDurationCalculationFromBytes() {
        val sampleRate = 16000
        val channels = 1
        val bytesPerSample = 2
        val bytesPerSecond = sampleRate * channels * bytesPerSample // 32,000 bytes/sec

        val totalBytes = 64000L
        val durationMs = (totalBytes * 1000L) / bytesPerSecond

        assertEquals(2000L, durationMs) // 2.0 seconds
    }

    @Test
    fun testValidSampleRates() {
        val supportedRates = setOf(8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000, 192000)
        assertTrue(supportedRates.contains(44100))
        assertTrue(supportedRates.contains(48000))
        assertTrue(supportedRates.contains(96000))
        assertFalse(supportedRates.contains(12345))
    }

    @Test
    fun testWavHeaderSize() {
        val header = PcmAudioRecorder.createWavHeader(
            pcmDataSize = 1000,
            sampleRate = 44100,
            channels = 2,
            bitsPerSample = 16
        )
        assertEquals(44, header.size)
        // Check "RIFF"
        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])

        // Check "WAVE"
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])
    }
}

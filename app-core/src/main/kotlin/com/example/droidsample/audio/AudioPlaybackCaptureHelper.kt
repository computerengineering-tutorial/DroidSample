package com.example.droidsample.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Helper for capturing internal system audio playback introduced in Android 10 (API 29).
 * Requires user consent via [MediaProjection] (screen/audio capture prompt).
 *
 * Android Rules & Limitations:
 * 1. Only captures apps that allow it (apps targeting API 29+ allow by default; apps targeting API 28- opt in).
 * 2. Apps can opt out by setting android:allowAudioPlaybackCapture="false" in their manifest.
 * 3. Cannot capture telephony (USAGE_VOICE_COMMUNICATION, USAGE_CALL), notifications, alarms, or DRM protected content.
 */
object AudioPlaybackCaptureHelper {

    /**
     * Checks if the device running platform supports internal audio playback capture (Android 10+).
     */
    val isPlaybackCaptureSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Builds an [AudioPlaybackCaptureConfiguration] using an authorized [MediaProjection].
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun buildCaptureConfiguration(
        mediaProjection: MediaProjection,
        matchingUsages: List<Int> = listOf(
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_UNKNOWN
        ),
        excludeUsages: List<Int> = listOf(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.USAGE_NOTIFICATION
        ),
        matchingUids: List<Int> = emptyList(),
        excludeUids: List<Int> = emptyList()
    ): AudioPlaybackCaptureConfiguration {
        val builder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)

        matchingUsages.forEach { builder.addMatchingUsage(it) }
        excludeUsages.forEach { builder.excludeUsage(it) }
        matchingUids.forEach { builder.addMatchingUid(it) }
        excludeUids.forEach { builder.excludeUid(it) }

        return builder.build()
    }

    /**
     * Creates an [AudioRecord] instance configured to capture internal audio from the system.
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun createPlaybackCaptureRecord(
        mediaProjection: MediaProjection,
        sampleRate: Int = 44100,
        channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO,
        encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        bufferMultiplier: Int = 2
    ): AudioRecord? {
        val captureConfig = buildCaptureConfiguration(mediaProjection)
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBufferSize <= 0) return null

        val audioFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize * bufferMultiplier)
            .build()
    }
}

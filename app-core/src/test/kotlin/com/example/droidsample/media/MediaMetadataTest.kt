package com.example.droidsample.media

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataTest {

    @Test
    fun trackDetailInfo_correctlyClassifiesVideoAndAudio() {
        val videoTrack = MediaPipelineProcessor.TrackDetailInfo(
            trackIndex = 0,
            mimeType = "video/avc",
            isVideo = true,
            isAudio = false,
            width = 1920,
            height = 1080,
            frameRate = 30
        )
        assertTrue(videoTrack.isVideo)
        assertFalse(videoTrack.isAudio)
        assertEquals(1920, videoTrack.width)
        assertEquals(1080, videoTrack.height)
        assertEquals(30, videoTrack.frameRate)

        val audioTrack = MediaPipelineProcessor.TrackDetailInfo(
            trackIndex = 1,
            mimeType = "audio/mp4a-latm",
            isVideo = false,
            isAudio = true,
            channelCount = 2,
            sampleRate = 48000
        )
        assertFalse(audioTrack.isVideo)
        assertTrue(audioTrack.isAudio)
        assertEquals(2, audioTrack.channelCount)
        assertEquals(48000, audioTrack.sampleRate)
    }

    @Test
    fun mediaFileInfo_holdsContainerAttributes() {
        val fileInfo = MediaPipelineProcessor.MediaFileInfo(
            title = "Test Video",
            durationMs = 125000L,
            width = 3840,
            height = 2160,
            rotation = 90,
            bitrate = 20_000_000L,
            mimeType = "video/mp4",
            date = "2026-11-06",
            location = null,
            hasVideo = true,
            hasAudio = true,
            fileSizeBytes = 312_500_000L,
            tracks = emptyList()
        )

        assertEquals("Test Video", fileInfo.title)
        assertEquals(125000L, fileInfo.durationMs)
        assertEquals(3840, fileInfo.width)
        assertEquals(2160, fileInfo.height)
        assertEquals(90, fileInfo.rotation)
        assertTrue(fileInfo.hasVideo)
        assertTrue(fileInfo.hasAudio)
    }

    @Test
    fun cameraVideoRecorder_targetQualities_haveConsistentLabels() {
        assertEquals("SD (480p)", CameraVideoRecorder.TargetQuality.SD.label)
        assertEquals("HD (720p)", CameraVideoRecorder.TargetQuality.HD.label)
        assertEquals("FHD (1080p)", CameraVideoRecorder.TargetQuality.FHD.label)
        assertEquals("UHD (4K)", CameraVideoRecorder.TargetQuality.UHD.label)
    }

    @Test
    fun recordingStatus_stateTransitions() {
        val idleState: CameraVideoRecorder.RecordingStatus = CameraVideoRecorder.RecordingStatus.Idle
        assertEquals(CameraVideoRecorder.RecordingStatus.Idle, idleState)

        val startingState = CameraVideoRecorder.RecordingStatus.Starting(CameraVideoRecorder.TargetQuality.FHD)
        assertEquals(CameraVideoRecorder.TargetQuality.FHD, startingState.targetQuality)

        val recordingState = CameraVideoRecorder.RecordingStatus.Recording(
            durationSeconds = 15,
            bytesRecorded = 15_000_000L,
            isAudioEnabled = true
        )
        assertEquals(15L, recordingState.durationSeconds)
        assertTrue(recordingState.isAudioEnabled)

        val pausedState = CameraVideoRecorder.RecordingStatus.Paused(15L, 15_000_000L)
        assertEquals(15L, pausedState.durationSeconds)

        val completedState = CameraVideoRecorder.RecordingStatus.Completed(
            outputUri = null,
            durationSeconds = 30L,
            bytesRecorded = 30_000_000L
        )
        assertEquals(30L, completedState.durationSeconds)
        assertEquals(30_000_000L, completedState.bytesRecorded)
        assertNull(completedState.outputUri)
    }

    @Test
    fun screenConfig_defaultValues() {
        val config = ScreenRecorderManager.ScreenConfig()
        assertEquals(1080, config.width)
        assertEquals(1920, config.height)
        assertEquals(30, config.frameRate)
        assertEquals(8_000_000, config.bitrate)
        assertFalse(config.enableAudio)
    }
}

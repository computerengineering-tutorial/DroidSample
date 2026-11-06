package com.example.droidsample.camera

import android.hardware.camera2.CaptureRequest
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class CameraStateTest {

    @Test
    fun testCamera1StateTransitions() {
        var state: Camera1State = Camera1State.Uninitialized
        assertEquals(Camera1State.Uninitialized, state)

        val params = Camera1ParametersInfo(isZoomSupported = true, maxZoom = 10)
        state = Camera1State.Opened(0, params)
        assertTrue(state is Camera1State.Opened)
        assertEquals(0, (state as Camera1State.Opened).cameraId)
        assertEquals(10, (state as Camera1State.Opened).parameters.maxZoom)

        state = Camera1State.Previewing(0, CameraSize(1920, 1080))
        assertTrue(state is Camera1State.Previewing)
        assertEquals(1920, (state as Camera1State.Previewing).selectedPreviewSize.width)

        state = Camera1State.Capturing(0)
        assertTrue(state is Camera1State.Capturing)

        state = Camera1State.Error("Device disconnected")
        assertTrue(state is Camera1State.Error)
        assertEquals("Device disconnected", (state as Camera1State.Error).message)
    }

    @Test
    fun testCamera2ControlSettingsMutation() {
        val defaultSettings = Camera2ControlSettings()
        assertEquals(CaptureRequest.CONTROL_AE_MODE_ON, defaultSettings.aeMode)
        assertEquals(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE, defaultSettings.afMode)
        assertEquals(1.0f, defaultSettings.zoomRatio, 0.001f)
        assertNull(defaultSettings.iso)
        assertNull(defaultSettings.exposureTimeNs)

        // Switch to manual Pro controls
        val proSettings = defaultSettings.copy(
            aeMode = CaptureRequest.CONTROL_AE_MODE_OFF,
            iso = 800,
            exposureTimeNs = 20_000_000L, // 20ms
            afMode = CaptureRequest.CONTROL_AF_MODE_OFF,
            focusDistanceDiopters = 2.5f,
            zoomRatio = 2.0f,
            exposureCompensation = 2
        )

        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, proSettings.aeMode)
        assertEquals(800, proSettings.iso)
        assertEquals(20_000_000L, proSettings.exposureTimeNs)
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, proSettings.afMode)
        assertEquals(2.5f, proSettings.focusDistanceDiopters ?: 0f, 0.001f)
        assertEquals(2.0f, proSettings.zoomRatio, 0.001f)
        assertEquals(2, proSettings.exposureCompensation)
    }

    @Test
    fun testCameraAnalysisStats() {
        val stats = CameraAnalysisStats(
            averageLuminance = 128.5,
            fps = 29.8,
            frameWidth = 1920,
            frameHeight = 1080,
            timestampMs = 1700000000000L
        )

        assertEquals(128.5, stats.averageLuminance, 0.001)
        assertEquals(29.8, stats.fps, 0.001)
        assertEquals(1920, stats.frameWidth)
        assertEquals(1080, stats.frameHeight)
    }

    @Test
    fun testConcurrentCameraState() {
        var concurrentState: ConcurrentCameraState = ConcurrentCameraState.Idle
        assertEquals(ConcurrentCameraState.Idle, concurrentState)

        concurrentState = ConcurrentCameraState.CheckingSupport
        assertEquals(ConcurrentCameraState.CheckingSupport, concurrentState)

        concurrentState = ConcurrentCameraState.Supported(concurrentCombinationsCount = 2)
        assertTrue(concurrentState is ConcurrentCameraState.Supported)
        assertEquals(2, (concurrentState as ConcurrentCameraState.Supported).concurrentCombinationsCount)

        concurrentState = ConcurrentCameraState.Streaming
        assertEquals(ConcurrentCameraState.Streaming, concurrentState)

        concurrentState = ConcurrentCameraState.Unsupported("No concurrent hardware support")
        assertTrue(concurrentState is ConcurrentCameraState.Unsupported)
    }
}

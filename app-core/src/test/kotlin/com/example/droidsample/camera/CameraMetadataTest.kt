package com.example.droidsample.camera

import com.example.droidsample.ui.camera.CameraDetailedMetadata
import org.junit.Assert.*
import org.junit.Test

class CameraMetadataTest {

    @Test
    fun testCameraSizeStringFormatting() {
        val size = CameraSize(1920, 1080)
        assertEquals("1920x1080", size.toString())
        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun testCamera1ParametersInfoDefaults() {
        val params = Camera1ParametersInfo()
        assertTrue(params.supportedPreviewSizes.isEmpty())
        assertTrue(params.supportedPictureSizes.isEmpty())
        assertFalse(params.isZoomSupported)
        assertEquals(0, params.maxZoom)
        assertEquals(0, params.minExposureCompensation)
        assertEquals(0, params.maxExposureCompensation)
    }

    @Test
    fun testCamera1ParametersInfoCustomValues() {
        val params = Camera1ParametersInfo(
            supportedPreviewSizes = listOf(CameraSize(1920, 1080), CameraSize(1280, 720)),
            supportedPictureSizes = listOf(CameraSize(4000, 3000)),
            supportedFocusModes = listOf("auto", "continuous-picture"),
            supportedFlashModes = listOf("auto", "on", "off"),
            isZoomSupported = true,
            maxZoom = 10,
            zoomRatios = listOf(100, 200, 300),
            minExposureCompensation = -4,
            maxExposureCompensation = 4,
            exposureCompensationStep = 0.5f,
            supportedFpsRanges = listOf(Pair(15000, 30000), Pair(30000, 30000))
        )

        assertEquals(2, params.supportedPreviewSizes.size)
        assertEquals(1, params.supportedPictureSizes.size)
        assertTrue(params.isZoomSupported)
        assertEquals(10, params.maxZoom)
        assertEquals(3, params.zoomRatios.size)
        assertEquals(-4, params.minExposureCompensation)
        assertEquals(4, params.maxExposureCompensation)
        assertEquals(0.5f, params.exposureCompensationStep, 0.001f)
        assertEquals(2, params.supportedFpsRanges.size)
    }

    @Test
    fun testCameraDetailedMetadataModel() {
        val metadata = CameraDetailedMetadata(
            cameraId = "0",
            facing = "Back",
            hardwareLevel = "FULL",
            sensorOrientation = 90,
            activeArray = null,
            isoRange = null,
            exposureRangeMs = Pair(0.1, 1000.0),
            capabilities = listOf("BACKWARD_COMPATIBLE", "MANUAL_SENSOR", "RAW"),
            physicalCameras = listOf("2", "3"),
            flashAvailable = true,
            jpegSizes = emptyList(),
            yuvSizes = emptyList(),
            focalLengths = listOf(4.25f, 6.0f)
        )

        assertEquals("0", metadata.cameraId)
        assertEquals("Back", metadata.facing)
        assertEquals("FULL", metadata.hardwareLevel)
        assertEquals(90, metadata.sensorOrientation)
        assertTrue(metadata.flashAvailable)
        assertEquals(3, metadata.capabilities.size)
        assertTrue(metadata.capabilities.contains("MANUAL_SENSOR"))
        assertEquals(2, metadata.physicalCameras.size)
        assertEquals(Pair(0.1, 1000.0), metadata.exposureRangeMs)
        assertEquals(2, metadata.focalLengths.size)
    }

    @Test
    fun testAvailableExtensionsDefaults() {
        val extensions = AvailableExtensions()
        assertFalse(extensions.isBokeAvailable)
        assertFalse(extensions.isHdrAvailable)
        assertFalse(extensions.isNightAvailable)
        assertFalse(extensions.isFaceRetouchAvailable)
        assertFalse(extensions.isAutoAvailable)

        val updated = extensions.copy(isHdrAvailable = true, isNightAvailable = true)
        assertTrue(updated.isHdrAvailable)
        assertTrue(updated.isNightAvailable)
        assertFalse(updated.isBokeAvailable)
        assertFalse(updated.isFaceRetouchAvailable)
    }
}

package com.example.droidsample.camera

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CameraNdkTest {

    private lateinit var bridge: CameraNdkBridge

    @Before
    fun setUp() {
        bridge = CameraNdkBridge()
    }

    @Test
    fun testInitializationAndDefaultState() {
        assertEquals(NdkCameraState.Idle, bridge.state.value)
        val initialized = bridge.initialize()
        assertTrue(initialized)
        assertEquals(NdkCameraState.Idle, bridge.state.value)
    }

    @Test
    fun testCameraIdListingAndMetadataInspection() {
        bridge.initialize()
        val cameraIds = bridge.getCameraIdList()
        assertTrue("Camera ID list should not be empty", cameraIds.isNotEmpty())
        assertTrue("Camera ID 0 should be present", cameraIds.contains("0"))

        val backInfo = bridge.getCameraCharacteristicsInfo("0")
        assertEquals("0", backInfo.cameraId)
        assertEquals(NdkCameraConstants.ACAMERA_LENS_FACING_BACK, backInfo.facing)
        assertEquals("Back", backInfo.facingName)
        assertEquals(NdkCameraConstants.ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL_FULL, backInfo.hardwareLevel)
        assertEquals("FULL", backInfo.hardwareLevelName)
        assertTrue(backInfo.hasFlash)
        assertTrue(backInfo.supportsOis)
        assertNotNull(backInfo.isoRange)
        assertTrue((backInfo.isoRange?.second ?: 0) >= 3200)
        assertNotNull(backInfo.exposureTimeRangeNs)
        assertTrue(backInfo.supportedPreviewSizes.isNotEmpty())
        assertTrue(backInfo.supportedJpegSizes.isNotEmpty())

        val frontInfo = bridge.getCameraCharacteristicsInfo("1")
        assertEquals("1", frontInfo.cameraId)
        assertEquals(NdkCameraConstants.ACAMERA_LENS_FACING_FRONT, frontInfo.facing)
        assertEquals("Front", frontInfo.facingName)
    }

    @Test
    fun testCameraOpenAndCloseLifecycle() {
        bridge.initialize()
        val opened = bridge.openCamera("0")
        assertTrue(opened)

        val state = bridge.state.value
        assertTrue("State should be Opened", state is NdkCameraState.Opened)
        assertEquals("0", (state as NdkCameraState.Opened).cameraId)
        assertEquals("Back", state.info.facingName)

        bridge.close()
        assertEquals(NdkCameraState.Idle, bridge.state.value)
    }

    @Test
    fun testControlSettingsMutation() {
        bridge.initialize()
        bridge.openCamera("0")

        val initial = bridge.settings.value
        assertEquals(NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_ON, initial.aeMode)
        assertEquals(NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE, initial.afMode)
        assertEquals(1.0f, initial.zoomRatio, 0.001f)
        assertNull(initial.iso)
        assertNull(initial.exposureTimeNs)

        val manualSettings = initial.copy(
            aeMode = NdkCameraConstants.ACAMERA_CONTROL_AE_MODE_OFF,
            iso = 800,
            exposureTimeNs = 20_000_000L,
            afMode = NdkCameraConstants.ACAMERA_CONTROL_AF_MODE_OFF,
            focusDistanceDiopters = 2.5f,
            exposureCompensation = 2,
            zoomRatio = 3.0f,
            flashMode = NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH,
            oisEnabled = true
        )

        val updated = bridge.updateSettings(manualSettings)
        assertTrue(updated)
        assertEquals(manualSettings, bridge.settings.value)
        assertEquals(800, bridge.settings.value.iso)
        assertEquals(20_000_000L, bridge.settings.value.exposureTimeNs)
        assertEquals(2.5f, bridge.settings.value.focusDistanceDiopters ?: 0f, 0.001f)
        assertEquals(3.0f, bridge.settings.value.zoomRatio, 0.001f)
        assertEquals(NdkCameraConstants.ACAMERA_FLASH_MODE_TORCH, bridge.settings.value.flashMode)
        assertTrue(bridge.settings.value.oisEnabled)
    }

    @Test
    fun testStreamingLifecycle() {
        bridge.initialize()
        bridge.openCamera("0")

        val started = bridge.startStreaming()
        assertTrue(started)
        assertTrue("State should be Streaming", bridge.state.value is NdkCameraState.Streaming)
        val streamState = bridge.state.value as NdkCameraState.Streaming
        assertEquals("0", streamState.cameraId)

        val stopped = bridge.stopStreaming()
        assertTrue(stopped)
        assertTrue("State should revert to Opened", bridge.state.value is NdkCameraState.Opened)

        bridge.close()
        assertEquals(NdkCameraState.Idle, bridge.state.value)
    }

    @Test
    fun testStillCaptureSimulation() {
        bridge.initialize()
        bridge.openCamera("0")

        var capturedBytes: ByteArray? = null
        val bytes = bridge.captureStill { capturedBytes = it }

        assertNotNull(bytes)
        assertTrue((bytes?.size ?: 0) > 0)
        assertArrayEquals(bytes, capturedBytes)

        val state = bridge.state.value
        assertTrue("State should be PhotoCaptured", state is NdkCameraState.PhotoCaptured)
        val photoState = state as NdkCameraState.PhotoCaptured
        assertEquals("0", photoState.cameraId)
        assertEquals(bytes?.size, photoState.jpegBytes.size)

        bridge.close()
    }

    @Test
    fun testAfTriggerAndCancel() {
        bridge.initialize()
        bridge.openCamera("0")

        assertTrue(bridge.triggerAf())
        assertTrue(bridge.cancelAf())

        bridge.close()
    }

    @Test
    fun testNdkCameraErrorMapping() {
        assertEquals("Success", NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_OK))
        assertEquals(
            "Camera device disconnected",
            NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_ERROR_CAMERA_DISCONNECTED)
        )
        assertEquals(
            "Insufficient native memory",
            NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_ERROR_NOT_ENOUGH_MEMORY)
        )
        assertEquals(
            "Fatal camera device error",
            NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_ERROR_CAMERA_DEVICE)
        )
        assertEquals(
            "Camera permission denied",
            NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_ERROR_PERMISSION_DENIED)
        )
        assertEquals(
            "Native stream configuration failed",
            NdkCameraConstants.getErrorMessage(NdkCameraConstants.ACAMERA_ERROR_STREAM_CONFIGURE_FAIL)
        )
    }
}

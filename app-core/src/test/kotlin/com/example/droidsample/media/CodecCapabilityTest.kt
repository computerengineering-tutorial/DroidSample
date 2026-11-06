package com.example.droidsample.media

import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecCapabilityTest {

    @Test
    fun colorFormatName_resolvesKnownFormats() {
        assertEquals(
            "COLOR_FormatYUV420Flexible",
            MediaCodecInspector.resolveColorFormatName(CodecCapabilities.COLOR_FormatYUV420Flexible)
        )
        assertEquals(
            "COLOR_FormatSurface",
            MediaCodecInspector.resolveColorFormatName(CodecCapabilities.COLOR_FormatSurface)
        )
        assertEquals(
            "COLOR_FormatYUV420SemiPlanar",
            MediaCodecInspector.resolveColorFormatName(CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        )
        assertEquals(
            "COLOR_FormatYUV420Planar",
            MediaCodecInspector.resolveColorFormatName(CodecCapabilities.COLOR_FormatYUV420Planar)
        )
    }

    @Test
    fun colorFormatName_handlesUnknownFormat() {
        val unknownFormat = 0x999999
        val name = MediaCodecInspector.resolveColorFormatName(unknownFormat)
        assertTrue(name.startsWith("Format (0x"))
    }

    @Test
    fun profileName_resolvesAvcProfiles() {
        assertEquals("Baseline", MediaCodecInspector.resolveProfileName("video/avc", CodecProfileLevel.AVCProfileBaseline))
        assertEquals("Main", MediaCodecInspector.resolveProfileName("video/avc", CodecProfileLevel.AVCProfileMain))
        assertEquals("High", MediaCodecInspector.resolveProfileName("video/avc", CodecProfileLevel.AVCProfileHigh))
    }

    @Test
    fun profileName_resolvesHevcProfiles() {
        assertEquals("Main", MediaCodecInspector.resolveProfileName("video/hevc", CodecProfileLevel.HEVCProfileMain))
        assertEquals("Main 10", MediaCodecInspector.resolveProfileName("video/hevc", CodecProfileLevel.HEVCProfileMain10))
    }

    @Test
    fun profileName_resolvesVp9Profiles() {
        assertEquals(
            "Profile 0 (8-bit 4:2:0)",
            MediaCodecInspector.resolveProfileName("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Profile0)
        )
        assertEquals(
            "Profile 2 (10-bit 4:2:0)",
            MediaCodecInspector.resolveProfileName("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Profile2)
        )
    }

    @Test
    fun profileName_resolvesAacProfiles() {
        assertEquals("AAC LC", MediaCodecInspector.resolveProfileName("audio/mp4a-latm", CodecProfileLevel.AACObjectLC))
        assertEquals("AAC HE", MediaCodecInspector.resolveProfileName("audio/mp4a-latm", CodecProfileLevel.AACObjectHE))
    }

    @Test
    fun computeOverviewStats_countsAccurately() {
        val inspector = MediaCodecInspector()
        val mockCodecs = listOf(
            MediaCodecInspector.CodecSummary(
                name = "c2.android.avc.decoder",
                canonicalName = "c2.android.avc.decoder",
                isEncoder = false,
                isHardwareAccelerated = false,
                isSoftwareOnly = true,
                isVendor = false,
                supportedTypes = listOf(
                    MediaCodecInspector.CodecTypeDetail(
                        mimeType = "video/avc",
                        isVideo = true,
                        isAudio = false,
                        colorFormats = emptyList(),
                        profileLevels = emptyList(),
                        maxInstances = 4
                    )
                )
            ),
            MediaCodecInspector.CodecSummary(
                name = "c2.qti.avc.encoder",
                canonicalName = "c2.qti.avc.encoder",
                isEncoder = true,
                isHardwareAccelerated = true,
                isSoftwareOnly = false,
                isVendor = true,
                supportedTypes = listOf(
                    MediaCodecInspector.CodecTypeDetail(
                        mimeType = "video/avc",
                        isVideo = true,
                        isAudio = false,
                        colorFormats = emptyList(),
                        profileLevels = emptyList(),
                        maxInstances = 8
                    )
                )
            ),
            MediaCodecInspector.CodecSummary(
                name = "c2.android.aac.decoder",
                canonicalName = "c2.android.aac.decoder",
                isEncoder = false,
                isHardwareAccelerated = false,
                isSoftwareOnly = true,
                isVendor = false,
                supportedTypes = listOf(
                    MediaCodecInspector.CodecTypeDetail(
                        mimeType = "audio/mp4a-latm",
                        isVideo = false,
                        isAudio = true,
                        colorFormats = emptyList(),
                        profileLevels = emptyList(),
                        maxInstances = 4
                    )
                )
            )
        )

        val stats = inspector.computeOverviewStats(mockCodecs)
        assertEquals(3, stats.totalCodecs)
        assertEquals(1, stats.totalEncoders)
        assertEquals(2, stats.totalDecoders)
        assertEquals(1, stats.hardwareAcceleratedCount)
        assertEquals(2, stats.softwareOnlyCount)
        assertEquals(2, stats.videoCodecsCount)
        assertEquals(1, stats.audioCodecsCount)
    }
}

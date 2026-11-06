package com.example.droidsample.media

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build

/**
 * Technical inspector and classifier for Android hardware and software media codecs.
 */
class MediaCodecInspector(
    private val codecListType: Int = MediaCodecList.ALL_CODECS
) {

    data class IntRangeInfo(val lower: Int, val upper: Int)
    data class DoubleRangeInfo(val lower: Double, val upper: Double)

    data class VideoCapabilitiesInfo(
        val widthRange: IntRangeInfo,
        val heightRange: IntRangeInfo,
        val bitrateRange: IntRangeInfo,
        val frameRateRange: DoubleRangeInfo,
        val isSizeSupported1080p: Boolean,
        val isSizeSupported4K: Boolean
    )

    data class AudioCapabilitiesInfo(
        val bitrateRange: IntRangeInfo,
        val maxInputChannelCount: Int,
        val sampleRateRanges: List<Int>
    )

    data class CodecTypeDetail(
        val mimeType: String,
        val isVideo: Boolean,
        val isAudio: Boolean,
        val colorFormats: List<ColorFormatInfo>,
        val profileLevels: List<ProfileLevelInfo>,
        val maxInstances: Int,
        val videoCapabilities: VideoCapabilitiesInfo? = null,
        val audioCapabilities: AudioCapabilitiesInfo? = null
    )

    data class ColorFormatInfo(
        val formatId: Int,
        val formatName: String
    )

    data class ProfileLevelInfo(
        val profile: Int,
        val profileName: String,
        val level: Int,
        val levelName: String
    )

    data class CodecSummary(
        val name: String,
        val canonicalName: String,
        val isEncoder: Boolean,
        val isHardwareAccelerated: Boolean,
        val isSoftwareOnly: Boolean,
        val isVendor: Boolean,
        val supportedTypes: List<CodecTypeDetail>
    )

    data class CodecOverviewStats(
        val totalCodecs: Int,
        val totalEncoders: Int,
        val totalDecoders: Int,
        val hardwareAcceleratedCount: Int,
        val softwareOnlyCount: Int,
        val videoCodecsCount: Int,
        val audioCodecsCount: Int
    )

    /**
     * Inspects and returns full list of available codecs on the device.
     */
    fun getSupportedCodecs(): List<CodecSummary> {
        val codecList = MediaCodecList(codecListType)
        val result = mutableListOf<CodecSummary>()

        for (info in codecList.codecInfos) {
            val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
            }

            val isSw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isSoftwareOnly
            } else {
                info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")
            }

            val isVendor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isVendor
            } else {
                !info.name.startsWith("OMX.google.")
            }

            val canonicalName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.canonicalName
            } else {
                info.name
            }

            val types = mutableListOf<CodecTypeDetail>()
            for (mime in info.supportedTypes) {
                try {
                    val caps = info.getCapabilitiesForType(mime)
                    types.add(inspectCapabilities(mime, caps))
                } catch (_: Exception) {
                    // Codec might fail capability query for certain formats on emulators
                }
            }

            result.add(
                CodecSummary(
                    name = info.name,
                    canonicalName = canonicalName,
                    isEncoder = info.isEncoder,
                    isHardwareAccelerated = isHw,
                    isSoftwareOnly = isSw,
                    isVendor = isVendor,
                    supportedTypes = types
                )
            )
        }
        return result
    }

    /**
     * Compute statistics over the device codecs.
     */
    fun computeOverviewStats(codecs: List<CodecSummary>): CodecOverviewStats {
        var encoders = 0
        var decoders = 0
        var hw = 0
        var sw = 0
        var video = 0
        var audio = 0

        for (c in codecs) {
            if (c.isEncoder) encoders++ else decoders++
            if (c.isHardwareAccelerated) hw++
            if (c.isSoftwareOnly) sw++
            if (c.supportedTypes.any { it.isVideo }) video++
            if (c.supportedTypes.any { it.isAudio }) audio++
        }

        return CodecOverviewStats(
            totalCodecs = codecs.size,
            totalEncoders = encoders,
            totalDecoders = decoders,
            hardwareAcceleratedCount = hw,
            softwareOnlyCount = sw,
            videoCodecsCount = video,
            audioCodecsCount = audio
        )
    }

    private fun inspectCapabilities(mime: String, caps: CodecCapabilities): CodecTypeDetail {
        val isVideo = mime.startsWith("video/")
        val isAudio = mime.startsWith("audio/")

        val colorFormats = caps.colorFormats?.map { formatId ->
            ColorFormatInfo(formatId, resolveColorFormatName(formatId))
        } ?: emptyList()

        val profileLevels = caps.profileLevels?.map { pl ->
            ProfileLevelInfo(
                profile = pl.profile,
                profileName = resolveProfileName(mime, pl.profile),
                level = pl.level,
                levelName = resolveLevelName(mime, pl.level)
            )
        } ?: emptyList()

        val maxInstances = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            caps.maxSupportedInstances
        } else {
            1
        }

        val videoCaps = if (isVideo) {
            caps.videoCapabilities?.let { vc ->
                val wRange = vc.supportedWidths
                val hRange = vc.supportedHeights
                val brRange = vc.bitrateRange
                val frRange = vc.supportedFrameRates

                VideoCapabilitiesInfo(
                    widthRange = IntRangeInfo(wRange.lower, wRange.upper),
                    heightRange = IntRangeInfo(hRange.lower, hRange.upper),
                    bitrateRange = IntRangeInfo(brRange.lower, brRange.upper),
                    frameRateRange = DoubleRangeInfo(frRange.lower.toDouble(), frRange.upper.toDouble()),
                    isSizeSupported1080p = vc.isSizeSupported(1920, 1080),
                    isSizeSupported4K = vc.isSizeSupported(3840, 2160)
                )
            }
        } else null

        val audioCaps = if (isAudio) {
            caps.audioCapabilities?.let { ac ->
                val brRange = ac.bitrateRange
                val sampleRates = ac.supportedSampleRates ?: intArrayOf()
                AudioCapabilitiesInfo(
                    bitrateRange = IntRangeInfo(brRange.lower, brRange.upper),
                    maxInputChannelCount = ac.maxInputChannelCount,
                    sampleRateRanges = sampleRates.toList()
                )
            }
        } else null

        return CodecTypeDetail(
            mimeType = mime,
            isVideo = isVideo,
            isAudio = isAudio,
            colorFormats = colorFormats,
            profileLevels = profileLevels,
            maxInstances = maxInstances,
            videoCapabilities = videoCaps,
            audioCapabilities = audioCaps
        )
    }

    companion object {
        @Suppress("DEPRECATION")
        fun resolveColorFormatName(format: Int): String {
            return when (format) {
                CodecCapabilities.COLOR_FormatYUV420Flexible -> "COLOR_FormatYUV420Flexible"
                CodecCapabilities.COLOR_FormatYUV420Planar -> "COLOR_FormatYUV420Planar"
                CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> "COLOR_FormatYUV420SemiPlanar"
                CodecCapabilities.COLOR_FormatSurface -> "COLOR_FormatSurface"
                CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> "COLOR_FormatYUV420PackedPlanar"
                CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> "COLOR_FormatYUV420PackedSemiPlanar"
                CodecCapabilities.COLOR_Format32bitABGR8888 -> "COLOR_Format32bitABGR8888"
                CodecCapabilities.COLOR_Format32bitARGB8888 -> "COLOR_Format32bitARGB8888"
                CodecCapabilities.COLOR_Format16bitRGB565 -> "COLOR_Format16bitRGB565"
                CodecCapabilities.COLOR_Format24bitRGB888 -> "COLOR_Format24bitRGB888"
                CodecCapabilities.COLOR_Format24bitBGR888 -> "COLOR_Format24bitBGR888"
                0x7FA30C00 -> "OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m"
                0x7FA30C03 -> "OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka"
                0x7F000100 -> "COLOR_TI_FormatYUV420PackedSemiPlanar"
                else -> "Format (0x${Integer.toHexString(format).uppercase()})"
            }
        }

        fun resolveProfileName(mime: String, profile: Int): String {
            return when (mime) {
                "video/avc", "video/mp4v-es" -> when (profile) {
                    CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                    CodecProfileLevel.AVCProfileMain -> "Main"
                    CodecProfileLevel.AVCProfileExtended -> "Extended"
                    CodecProfileLevel.AVCProfileHigh -> "High"
                    CodecProfileLevel.AVCProfileHigh10 -> "High 10"
                    CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
                    CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
                    CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
                    CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
                    else -> "Profile $profile"
                }
                "video/hevc" -> when (profile) {
                    CodecProfileLevel.HEVCProfileMain -> "Main"
                    CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                    CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
                    CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
                    CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR10+"
                    else -> "Profile $profile"
                }
                "video/x-vnd.on2.vp9" -> when (profile) {
                    CodecProfileLevel.VP9Profile0 -> "Profile 0 (8-bit 4:2:0)"
                    CodecProfileLevel.VP9Profile1 -> "Profile 1 (8-bit 4:2:2 / 4:4:4)"
                    CodecProfileLevel.VP9Profile2 -> "Profile 2 (10-bit 4:2:0)"
                    CodecProfileLevel.VP9Profile3 -> "Profile 3 (10-bit 4:2:2 / 4:4:4)"
                    else -> "Profile $profile"
                }
                "audio/mp4a-latm" -> when (profile) {
                    CodecProfileLevel.AACObjectMain -> "AAC Main"
                    CodecProfileLevel.AACObjectLC -> "AAC LC"
                    CodecProfileLevel.AACObjectSSR -> "AAC SSR"
                    CodecProfileLevel.AACObjectLTP -> "AAC LTP"
                    CodecProfileLevel.AACObjectHE -> "AAC HE"
                    CodecProfileLevel.AACObjectScalable -> "AAC Scalable"
                    CodecProfileLevel.AACObjectERLC -> "AAC ER LC"
                    CodecProfileLevel.AACObjectLD -> "AAC LD"
                    CodecProfileLevel.AACObjectHE_PS -> "AAC HE PS"
                    CodecProfileLevel.AACObjectELD -> "AAC ELD"
                    CodecProfileLevel.AACObjectXHE -> "AAC xHE"
                    else -> "AAC Profile $profile"
                }
                else -> "Profile $profile"
            }
        }

        fun resolveLevelName(mime: String, level: Int): String {
            return when (mime) {
                "video/avc" -> when (level) {
                    CodecProfileLevel.AVCLevel1 -> "1"
                    CodecProfileLevel.AVCLevel1b -> "1b"
                    CodecProfileLevel.AVCLevel11 -> "1.1"
                    CodecProfileLevel.AVCLevel12 -> "1.2"
                    CodecProfileLevel.AVCLevel13 -> "1.3"
                    CodecProfileLevel.AVCLevel2 -> "2"
                    CodecProfileLevel.AVCLevel21 -> "2.1"
                    CodecProfileLevel.AVCLevel22 -> "2.2"
                    CodecProfileLevel.AVCLevel3 -> "3"
                    CodecProfileLevel.AVCLevel31 -> "3.1"
                    CodecProfileLevel.AVCLevel32 -> "3.2"
                    CodecProfileLevel.AVCLevel4 -> "4"
                    CodecProfileLevel.AVCLevel41 -> "4.1"
                    CodecProfileLevel.AVCLevel42 -> "4.2"
                    CodecProfileLevel.AVCLevel5 -> "5"
                    CodecProfileLevel.AVCLevel51 -> "5.1"
                    CodecProfileLevel.AVCLevel52 -> "5.2"
                    CodecProfileLevel.AVCLevel6 -> "6"
                    CodecProfileLevel.AVCLevel61 -> "6.1"
                    CodecProfileLevel.AVCLevel62 -> "6.2"
                    else -> "Level $level"
                }
                "video/hevc" -> when (level) {
                    CodecProfileLevel.HEVCMainTierLevel1 -> "Main Tier 1"
                    CodecProfileLevel.HEVCMainTierLevel2 -> "Main Tier 2"
                    CodecProfileLevel.HEVCMainTierLevel21 -> "Main Tier 2.1"
                    CodecProfileLevel.HEVCMainTierLevel3 -> "Main Tier 3"
                    CodecProfileLevel.HEVCMainTierLevel31 -> "Main Tier 3.1"
                    CodecProfileLevel.HEVCMainTierLevel4 -> "Main Tier 4"
                    CodecProfileLevel.HEVCMainTierLevel41 -> "Main Tier 4.1"
                    CodecProfileLevel.HEVCMainTierLevel5 -> "Main Tier 5"
                    CodecProfileLevel.HEVCMainTierLevel51 -> "Main Tier 5.1"
                    CodecProfileLevel.HEVCMainTierLevel6 -> "Main Tier 6"
                    CodecProfileLevel.HEVCMainTierLevel61 -> "Main Tier 6.1"
                    CodecProfileLevel.HEVCMainTierLevel62 -> "Main Tier 6.2"
                    CodecProfileLevel.HEVCHighTierLevel4 -> "High Tier 4"
                    CodecProfileLevel.HEVCHighTierLevel41 -> "High Tier 4.1"
                    CodecProfileLevel.HEVCHighTierLevel5 -> "High Tier 5"
                    CodecProfileLevel.HEVCHighTierLevel51 -> "High Tier 5.1"
                    CodecProfileLevel.HEVCHighTierLevel6 -> "High Tier 6"
                    CodecProfileLevel.HEVCHighTierLevel61 -> "High Tier 6.1"
                    CodecProfileLevel.HEVCHighTierLevel62 -> "High Tier 6.2"
                    else -> "Level $level"
                }
                else -> "Level $level"
            }
        }
    }
}

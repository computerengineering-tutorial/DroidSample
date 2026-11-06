package com.example.droidsample.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MicrophoneDirection
import android.media.MicrophoneInfo
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Inspects microphone hardware characteristics via Android P+ (API 28+) [MicrophoneInfo].
 * Exposes physical mic location, 3D coordinate position, orientation vectors,
 * sensitivity, max/min SPL, frequency response points, and directionality modes.
 */
class MicrophoneCharacteristicsManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    data class MicrophoneDetail(
        val id: Int,
        val type: Int,
        val typeName: String,
        val address: String,
        val location: Int,
        val locationName: String,
        val group: Int,
        val indexInGroup: Int,
        val positionX: Float,
        val positionY: Float,
        val positionZ: Float,
        val orientationAzimuth: Float,
        val orientationElevation: Float,
        val orientationRoll: Float,
        val frequencyResponse: List<Pair<Float, Float>>,
        val channelMapping: List<Pair<Int, Int>>,
        val sensitivityDbFs: Float,
        val maxSplDb: Float,
        val minSplDb: Float,
        val directionality: Int,
        val directionalityName: String
    )

    /**
     * Queries all available platform microphones (API 28+).
     */
    fun queryAvailableMicrophones(): List<MicrophoneDetail> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()

        return try {
            val list = audioManager.microphones
            list.map { mapMicrophoneInfo(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Queries active microphones for a currently recording [AudioRecord] (API 28+).
     */
    fun queryActiveMicrophones(audioRecord: AudioRecord?): List<MicrophoneDetail> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioRecord == null) return emptyList()

        return try {
            val list = audioRecord.activeMicrophones
            list.map { mapMicrophoneInfo(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Sets preferred microphone directionality on an active [AudioRecord] (API 29+).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setDirection(audioRecord: AudioRecord?, direction: Int): Boolean {
        return audioRecord?.setPreferredMicrophoneDirection(direction) ?: false
    }

    /**
     * Sets preferred microphone field dimension / zoom factor on an active [AudioRecord] (API 29+).
     * @param zoom factor between -1.0f (widest) and 1.0f (narrowest).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setFieldDimension(audioRecord: AudioRecord?, zoom: Float): Boolean {
        return audioRecord?.setPreferredMicrophoneFieldDimension(zoom) ?: false
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.P)
        fun mapMicrophoneInfo(info: MicrophoneInfo): MicrophoneDetail {
            val pos = info.position
            val orientation = info.orientation
            val freqResponse = info.frequencyResponse.map { Pair(it.first, it.second) }
            val channelMapping = info.channelMapping.map { Pair(it.first.toInt(), it.second.toInt()) }

            return MicrophoneDetail(
                id = info.id,
                type = info.type,
                typeName = AudioRoutingManager.getDeviceTypeName(info.type),
                address = info.address.ifBlank { "Internal" },
                location = info.location,
                locationName = getLocationName(info.location),
                group = info.group,
                indexInGroup = info.indexInTheGroup,
                positionX = pos?.x ?: 0f,
                positionY = pos?.y ?: 0f,
                positionZ = pos?.z ?: 0f,
                orientationAzimuth = orientation?.x ?: 0f,
                orientationElevation = orientation?.y ?: 0f,
                orientationRoll = orientation?.z ?: 0f,
                frequencyResponse = freqResponse,
                channelMapping = channelMapping,
                sensitivityDbFs = info.sensitivity,
                maxSplDb = info.maxSpl,
                minSplDb = info.minSpl,
                directionality = info.directionality,
                directionalityName = getDirectionalityName(info.directionality)
            )
        }

        fun getLocationName(location: Int): String {
            return when (location) {
                MicrophoneInfo.LOCATION_MAINBODY -> "Main (Chassis)"
                MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> "Main (Movable)"
                MicrophoneInfo.LOCATION_PERIPHERAL -> "Peripheral (Headset / USB / BT)"
                MicrophoneInfo.LOCATION_UNKNOWN -> "Unknown"
                else -> "Location $location"
            }
        }

        fun getDirectionalityName(directionality: Int): String {
            return when (directionality) {
                MicrophoneInfo.DIRECTIONALITY_OMNI -> "Omnidirectional"
                MicrophoneInfo.DIRECTIONALITY_BI_DIRECTIONAL -> "Bidirectional (Figure-8)"
                MicrophoneInfo.DIRECTIONALITY_CARDIOID -> "Cardioid"
                MicrophoneInfo.DIRECTIONALITY_HYPER_CARDIOID -> "Hypercardioid"
                MicrophoneInfo.DIRECTIONALITY_SUPER_CARDIOID -> "Supercardioid"
                MicrophoneInfo.DIRECTIONALITY_UNKNOWN -> "Unknown"
                else -> "Directionality $directionality"
            }
        }
    }
}

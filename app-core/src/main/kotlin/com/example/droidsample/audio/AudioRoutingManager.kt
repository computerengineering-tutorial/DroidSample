package com.example.droidsample.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages audio device hardware discovery, dynamic device hotplug callbacks,
 * communication device routing (API 31+ & legacy SCO), audio focus requests, and audio modes.
 */
@Suppress("DEPRECATION")
class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    data class DeviceInfoSummary(
        val id: Int,
        val productName: String,
        val type: Int,
        val typeName: String,
        val isSource: Boolean,
        val isSink: Boolean,
        val channelCounts: List<Int>,
        val sampleRates: List<Int>,
        val encodings: List<Int>,
        val rawDevice: AudioDeviceInfo
    )

    data class RoutingState(
        val inputDevices: List<DeviceInfoSummary> = emptyList(),
        val outputDevices: List<DeviceInfoSummary> = emptyList(),
        val activeCommunicationDevice: DeviceInfoSummary? = null,
        val isSpeakerphoneOn: Boolean = false,
        val isBluetoothScoOn: Boolean = false,
        val audioMode: Int = AudioManager.MODE_NORMAL,
        val audioFocusState: Int = AudioManager.AUDIOFOCUS_NONE
    )

    private val _routingState = MutableStateFlow(RoutingState())
    val routingState: StateFlow<RoutingState> = _routingState.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private var legacyFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    init {
        refreshDevices()
        registerDeviceCallback()
    }

    /**
     * Refreshes the list of currently connected input and output devices.
     */
    fun refreshDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { mapDeviceInfo(it) }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { mapDeviceInfo(it) }

        val activeCommDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { mapDeviceInfo(it) }
        } else null

        _routingState.value = _routingState.value.copy(
            inputDevices = inputs,
            outputDevices = outputs,
            activeCommunicationDevice = activeCommDevice,
            isSpeakerphoneOn = audioManager.isSpeakerphoneOn,
            isBluetoothScoOn = audioManager.isBluetoothScoOn,
            audioMode = audioManager.mode
        )
    }

    private fun registerDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    refreshDevices()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    refreshDevices()
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            audioDeviceCallback = callback
        }
    }

    /**
     * Requests routing of communication audio to a specific device (API 31+).
     */
    fun setCommunicationDevice(device: AudioDeviceInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val success = audioManager.setCommunicationDevice(device)
            refreshDevices()
            success
        } else {
            false
        }
    }

    /**
     * Clears the active communication device, resetting back to system default (API 31+).
     */
    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            refreshDevices()
        }
    }

    /**
     * Controls speakerphone toggle for communications.
     */
    fun setSpeakerphoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
        _routingState.value = _routingState.value.copy(isSpeakerphoneOn = on)
    }

    /**
     * Controls Bluetooth SCO toggle for telephony / voice communications.
     */
    fun setBluetoothScoOn(on: Boolean) {
        if (on) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
        _routingState.value = _routingState.value.copy(isBluetoothScoOn = on)
    }

    /**
     * Sets the platform audio mode (e.g. MODE_NORMAL, MODE_IN_COMMUNICATION, MODE_IN_CALL).
     */
    fun setAudioMode(mode: Int) {
        audioManager.mode = mode
        _routingState.value = _routingState.value.copy(audioMode = mode)
    }

    /**
     * Requests Audio Focus with specified gain type (e.g. AUDIOFOCUS_GAIN, AUDIOFOCUS_GAIN_TRANSIENT).
     */
    fun requestAudioFocus(
        focusGain: Int = AudioManager.AUDIOFOCUS_GAIN,
        usage: Int = AudioAttributes.USAGE_MEDIA,
        contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC
    ): Int {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val focusRequest = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    _routingState.value = _routingState.value.copy(audioFocusState = focusChange)
                }
                .build()

            activeFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                _routingState.value = _routingState.value.copy(audioFocusState = focusChange)
            }
            legacyFocusChangeListener = listener
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                focusGain
            )
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _routingState.value = _routingState.value.copy(audioFocusState = focusGain)
        }
        return result
    }

    /**
     * Abandons active audio focus.
     */
    fun abandonAudioFocus(): Int {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            legacyFocusChangeListener?.let { audioManager.abandonAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        _routingState.value = _routingState.value.copy(audioFocusState = AudioManager.AUDIOFOCUS_NONE)
        return result
    }

    /**
     * Unregisters listeners and releases resources.
     */
    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
            audioDeviceCallback = null
        }
        abandonAudioFocus()
    }

    companion object {
        fun mapDeviceInfo(info: AudioDeviceInfo): DeviceInfoSummary {
            val channelCounts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.channelCounts.toList()
            } else emptyList()

            val sampleRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.sampleRates.toList()
            } else emptyList()

            val encodings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.encodings.toList()
            } else emptyList()

            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                info.productName?.toString().takeIf { !it.isNullOrBlank() } ?: "Device #${info.id}"
            } else "Device #${info.id}"

            val typeName = getDeviceTypeName(info.type)

            return DeviceInfoSummary(
                id = info.id,
                productName = name,
                type = info.type,
                typeName = typeName,
                isSource = info.isSource,
                isSink = info.isSink,
                channelCounts = channelCounts,
                sampleRates = sampleRates,
                encodings = encodings,
                rawDevice = info
            )
        }

        fun getDeviceTypeName(type: Int): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset (Mic+Spk)"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (Voice)"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP (Media)"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI Audio Return Channel"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio Accessory"
                AudioDeviceInfo.TYPE_DOCK -> "Audio Dock"
                AudioDeviceInfo.TYPE_FM -> "FM Broadcast"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
                AudioDeviceInfo.TYPE_FM_TUNER -> "FM Tuner"
                AudioDeviceInfo.TYPE_TV_TUNER -> "TV Tuner"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony Network"
                AudioDeviceInfo.TYPE_AUX_LINE -> "Auxiliary Line"
                AudioDeviceInfo.TYPE_IP -> "IP Audio Stream"
                AudioDeviceInfo.TYPE_BUS -> "System Bus Audio"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Built-in Speaker (Safe)"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
                AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE Broadcast"
                AudioDeviceInfo.TYPE_DOCK_ANALOG -> "Dock Analog"
                else -> "Audio Device ($type)"
            }
        }
    }
}

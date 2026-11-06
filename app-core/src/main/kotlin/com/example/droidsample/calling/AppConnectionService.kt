package com.example.droidsample.calling

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VoipState {
    INITIALIZING,
    RINGING,
    DIALING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTED
}

data class ActiveVoipCall(
    val callId: String,
    val phoneNumber: String,
    val displayName: String,
    val isIncoming: Boolean,
    val state: VoipState,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val audioRoute: String = "EARPIECE",
    val dtmfTones: String = "",
    val durationSeconds: Long = 0,
    val disconnectReason: String? = null
)

/**
 * Singleton state holder connecting AppConnectionService to the Compose UI.
 */
@Suppress("DEPRECATION")
object VoipCallManager {
    private val _currentCall = MutableStateFlow<ActiveVoipCall?>(null)
    val currentCall: StateFlow<ActiveVoipCall?> = _currentCall.asStateFlow()

    private var activeConnection: VoipConnection? = null

    internal fun registerConnection(connection: VoipConnection, incoming: Boolean, number: String, name: String) {
        activeConnection = connection
        _currentCall.value = ActiveVoipCall(
            callId = java.util.UUID.randomUUID().toString(),
            phoneNumber = number,
            displayName = name,
            isIncoming = incoming,
            state = if (incoming) VoipState.RINGING else VoipState.DIALING
        )
    }

    internal fun unregisterConnection() {
        activeConnection = null
    }

    fun updateCallState(state: VoipState, reason: String? = null) {
        _currentCall.update { current ->
            current?.copy(
                state = state,
                disconnectReason = reason ?: current.disconnectReason
            )
        }
    }

    fun updateAudio(isMuted: Boolean, isSpeaker: Boolean, routeName: String) {
        _currentCall.update { current ->
            current?.copy(
                isMuted = isMuted,
                isSpeakerOn = isSpeaker,
                audioRoute = routeName
            )
        }
    }

    fun appendDtmf(c: Char) {
        _currentCall.update { current ->
            current?.copy(dtmfTones = current.dtmfTones + c)
        }
    }

    fun incrementDuration() {
        _currentCall.update { current ->
            if (current != null && current.state == VoipState.ACTIVE) {
                current.copy(durationSeconds = current.durationSeconds + 1)
            } else {
                current
            }
        }
    }

    // UI Action dispatchers
    fun answer() {
        activeConnection?.onAnswer() ?: updateCallState(VoipState.ACTIVE)
    }

    fun reject() {
        activeConnection?.onReject() ?: run {
            updateCallState(VoipState.DISCONNECTED, "Rejected by user")
            _currentCall.value = null
        }
    }

    fun disconnect() {
        activeConnection?.onDisconnect() ?: run {
            updateCallState(VoipState.DISCONNECTED, "Ended by user")
            _currentCall.value = null
        }
    }

    fun setHold(hold: Boolean) {
        if (hold) {
            activeConnection?.onHold() ?: updateCallState(VoipState.ON_HOLD)
        } else {
            activeConnection?.onUnhold() ?: updateCallState(VoipState.ACTIVE)
        }
    }

    fun sendDtmf(c: Char) {
        activeConnection?.onPlayDtmfTone(c)
        activeConnection?.onStopDtmfTone()
        appendDtmf(c)
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        activeConnection?.let { conn ->
            val route = if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            conn.setAudioRoute(route)
        } ?: run {
            _currentCall.update { current ->
                current?.copy(
                    isSpeakerOn = speakerOn,
                    audioRoute = if (speakerOn) "SPEAKER" else "EARPIECE"
                )
            }
        }
    }

    fun simulateLocalCall(number: String, name: String, incoming: Boolean) {
        _currentCall.value = ActiveVoipCall(
            callId = java.util.UUID.randomUUID().toString(),
            phoneNumber = number,
            displayName = name,
            isIncoming = incoming,
            state = if (incoming) VoipState.RINGING else VoipState.DIALING
        )
    }
}

/**
 * Telecom Connection object managing the lifecycle of an active VoIP call session.
 */
@Suppress("DEPRECATION")
class VoipConnection : Connection() {

    init {
        val caps = CAPABILITY_HOLD or
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_MUTE
        connectionCapabilities = caps
        connectionProperties = PROPERTY_SELF_MANAGED
    }

    override fun onAnswer(videoState: Int) {
        onAnswer()
    }

    override fun onAnswer() {
        setActive()
        VoipCallManager.updateCallState(VoipState.ACTIVE)
        Log.i("VoipConnection", "Call answered -> ACTIVE")
    }

    override fun onReject() {
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED, "Rejected"))
        destroy()
        VoipCallManager.updateCallState(VoipState.DISCONNECTED, "Call Rejected")
        VoipCallManager.unregisterConnection()
        Log.i("VoipConnection", "Call rejected")
    }

    override fun onDisconnect() {
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL, "Disconnected locally"))
        destroy()
        VoipCallManager.updateCallState(VoipState.DISCONNECTED, "Call Ended")
        VoipCallManager.unregisterConnection()
        Log.i("VoipConnection", "Call disconnected")
    }

    override fun onHold() {
        setOnHold()
        VoipCallManager.updateCallState(VoipState.ON_HOLD)
        Log.i("VoipConnection", "Call on hold")
    }

    override fun onUnhold() {
        setActive()
        VoipCallManager.updateCallState(VoipState.ACTIVE)
        Log.i("VoipConnection", "Call resumed active from hold")
    }

    override fun onPlayDtmfTone(c: Char) {
        VoipCallManager.appendDtmf(c)
        Log.i("VoipConnection", "DTMF tone: $c")
    }

    override fun onStopDtmfTone() {
        // Stop DTMF playback
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        if (state == null) return

        val routeName = when (state.route) {
            CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
            CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
            CallAudioState.ROUTE_WIRED_HEADSET -> "WIRED_HEADSET"
            CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
            else -> "UNKNOWN"
        }

        VoipCallManager.updateAudio(
            isMuted = state.isMuted,
            isSpeaker = state.route == CallAudioState.ROUTE_SPEAKER,
            routeName = routeName
        )
        Log.i("VoipConnection", "Audio state changed: route=$routeName, muted=${state.isMuted}")
    }
}

/**
 * ConnectionService for Self-Managed VoIP connections.
 * Manifest declared with BIND_TELECOM_CONNECTION_SERVICE.
 */
class AppConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i("AppConnectionService", "onCreateIncomingConnection: $request")
        val extras = request?.extras
        val number = extras?.getString(TelecomManager.EXTRA_CALL_BACK_NUMBER)
            ?: request?.address?.schemeSpecificPart
            ?: "Unknown"
        val name = extras?.getString("EXTRA_CALLER_NAME") ?: "Incoming VoIP Call"

        val connection = VoipConnection().apply {
            setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
            setRinging()
        }

        VoipCallManager.registerConnection(connection, incoming = true, number = number, name = name)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i("AppConnectionService", "onCreateOutgoingConnection: $request")
        val number = request?.address?.schemeSpecificPart ?: "Unknown"
        val extras = request?.extras
        val callExtras = extras?.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
        val name = callExtras?.getString("EXTRA_RECIPIENT_NAME") ?: "VoIP Contact"

        val connection = VoipConnection().apply {
            setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
            setDialing()
        }

        VoipCallManager.registerConnection(connection, incoming = false, number = number, name = name)
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("AppConnectionService", "onCreateIncomingConnectionFailed")
        VoipCallManager.updateCallState(VoipState.DISCONNECTED, "Incoming connection failed by Telecom")
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e("AppConnectionService", "onCreateOutgoingConnectionFailed")
        VoipCallManager.updateCallState(VoipState.DISCONNECTED, "Outgoing connection failed by Telecom")
    }
}

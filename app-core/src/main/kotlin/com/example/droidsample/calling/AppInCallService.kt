package com.example.droidsample.calling

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class InCallSessionState {
    IDLE,
    CONNECTING,
    RINGING,
    DIALING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTED
}

data class CurrentInCallInfo(
    val callId: String,
    val number: String,
    val displayName: String,
    val state: InCallSessionState,
    val isIncoming: Boolean,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val durationSeconds: Long = 0,
    val disconnectCause: String? = null
)

/**
 * Singleton state holder connecting [AppInCallService] to the Compose in-call UI.
 */
@Suppress("DEPRECATION")
object InCallManager {
    private const val TAG = "InCallManager"
    private var activeCall: Call? = null
    private var inCallService: AppInCallService? = null

    private val _currentCall = MutableStateFlow<CurrentInCallInfo?>(null)
    val currentCall: StateFlow<CurrentInCallInfo?> = _currentCall.asStateFlow()

    internal fun registerService(service: AppInCallService) {
        inCallService = service
    }

    internal fun unregisterService() {
        inCallService = null
    }

    internal fun onCallAdded(call: Call) {
        activeCall = call
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val name = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: number.ifBlank { "Unknown Caller" }
        val isIncoming = call.state == Call.STATE_RINGING

        _currentCall.value = CurrentInCallInfo(
            callId = System.currentTimeMillis().toString(),
            number = number,
            displayName = name,
            state = mapCallState(call.state),
            isIncoming = isIncoming
        )

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                _currentCall.update { current ->
                    current?.copy(state = mapCallState(state))
                }
                if (state == Call.STATE_DISCONNECTED) {
                    val cause = call.details?.disconnectCause?.description?.toString()
                    _currentCall.update { it?.copy(disconnectCause = cause) }
                }
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                val updatedNumber = details.handle?.schemeSpecificPart.orEmpty()
                val updatedName = details.callerDisplayName?.takeIf { it.isNotBlank() }
                    ?: updatedNumber.ifBlank { "Unknown Caller" }
                _currentCall.update { current ->
                    current?.copy(
                        number = updatedNumber.ifBlank { current.number },
                        displayName = updatedName
                    )
                }
            }
        })
    }

    internal fun onCallRemoved(call: Call) {
        if (activeCall == call) {
            activeCall = null
            _currentCall.value = null
        }
    }

    fun answerCall() {
        activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
            ?: run {
                // If demo/simulation mode
                _currentCall.update { it?.copy(state = InCallSessionState.ACTIVE) }
            }
    }

    fun rejectCall() {
        activeCall?.reject(false, null)
            ?: run {
                _currentCall.update { it?.copy(state = InCallSessionState.DISCONNECTED, disconnectCause = "Call Declined") }
            }
    }

    fun disconnectCall() {
        activeCall?.disconnect()
            ?: run {
                _currentCall.update { it?.copy(state = InCallSessionState.DISCONNECTED, disconnectCause = "Call Ended") }
            }
    }

    fun toggleHold() {
        val call = activeCall
        if (call != null) {
            if (call.state == Call.STATE_HOLDING) {
                call.unhold()
            } else if (call.state == Call.STATE_ACTIVE) {
                call.hold()
            }
        } else {
            _currentCall.update { current ->
                if (current != null) {
                    val nextState = if (current.state == InCallSessionState.ON_HOLD) InCallSessionState.ACTIVE else InCallSessionState.ON_HOLD
                    current.copy(state = nextState)
                } else null
            }
        }
    }

    fun setMute(muted: Boolean) {
        inCallService?.setMuted(muted)
        _currentCall.update { it?.copy(isMuted = muted) }
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        val route = if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        inCallService?.setAudioRoute(route)
        _currentCall.update { it?.copy(isSpeakerOn = speakerOn) }
    }

    fun playDtmf(digit: Char) {
        activeCall?.playDtmfTone(digit)
        activeCall?.stopDtmfTone()
    }

    fun incrementDuration() {
        _currentCall.update { current ->
            if (current != null && current.state == InCallSessionState.ACTIVE) {
                current.copy(durationSeconds = current.durationSeconds + 1)
            } else {
                current
            }
        }
    }

    /**
     * Simulates an in-call session to test the custom in-call phone screen without network/cellular.
     */
    fun startSimulatedCall(context: Context, number: String, name: String, incoming: Boolean) {
        _currentCall.value = CurrentInCallInfo(
            callId = "SIM_${System.currentTimeMillis()}",
            number = number,
            displayName = name,
            state = if (incoming) InCallSessionState.RINGING else InCallSessionState.DIALING,
            isIncoming = incoming
        )
        val intent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        context.startActivity(intent)
    }

    fun clearCall() {
        activeCall = null
        _currentCall.value = null
    }

    private fun mapCallState(state: Int): InCallSessionState = when (state) {
        Call.STATE_RINGING -> InCallSessionState.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING -> InCallSessionState.DIALING
        Call.STATE_ACTIVE -> InCallSessionState.ACTIVE
        Call.STATE_HOLDING -> InCallSessionState.ON_HOLD
        Call.STATE_DISCONNECTED -> InCallSessionState.DISCONNECTED
        else -> InCallSessionState.CONNECTING
    }
}

/**
 * Android Telecom InCallService implementation.
 * Bound by the OS when this app is the Default Phone / Dialer app.
 */
@Suppress("DEPRECATION")
class AppInCallService : InCallService() {
    private val tag = "AppInCallService"

    override fun onCreate() {
        super.onCreate()
        InCallManager.registerService(this)
    }

    override fun onDestroy() {
        InCallManager.unregisterService()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(tag, "onCallAdded: state=${call.state}, details=${call.details}")
        InCallManager.onCallAdded(call)

        // Launch custom in-call phone screen
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(tag, "onCallRemoved: $call")
        InCallManager.onCallRemoved(call)
    }
}

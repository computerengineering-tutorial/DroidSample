@file:Suppress("DEPRECATION")

package com.example.droidsample.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Model representing the observed call state.
 */
sealed interface CallState {
    data object Idle : CallState
    data class Ringing(val incomingNumber: String?) : CallState
    data object Offhook : CallState
}

data class CallStateInfo(
    val state: CallState,
    val rawStateInt: Int,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Monitor for observing telephony call states (IDLE, RINGING, OFFHOOK).
 * Uses Android 12+ TelephonyCallback when available, falling back to PhoneStateListener on older versions.
 */
@Suppress("DEPRECATION")
class CallStateMonitor(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Observes call state transitions as a Kotlin Coroutines cold Flow.
     */
    fun observeCallState(): Flow<CallStateInfo> = callbackFlow {
        val tm = telephonyManager
        if (tm == null) {
            trySend(
                CallStateInfo(
                    state = CallState.Idle,
                    rawStateInt = TelephonyManager.CALL_STATE_IDLE,
                    description = "Telephony service unavailable"
                )
            )
            close()
            return@callbackFlow
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    val info = mapStateToInfo(state, null)
                    trySend(info)
                }
            }

            try {
                tm.registerTelephonyCallback(executor, callback)
            } catch (e: SecurityException) {
                trySend(
                    CallStateInfo(
                        state = CallState.Idle,
                        rawStateInt = TelephonyManager.CALL_STATE_IDLE,
                        description = "Permission error: ${e.message}"
                    )
                )
            }

            awaitClose {
                try {
                    tm.unregisterTelephonyCallback(callback)
                } catch (_: Exception) {}
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    val info = mapStateToInfo(state, phoneNumber)
                    trySend(info)
                }
            }

            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)

            awaitClose {
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    companion object {
        fun mapStateToInfo(state: Int, incomingNumber: String?): CallStateInfo {
            val callState = when (state) {
                TelephonyManager.CALL_STATE_IDLE -> CallState.Idle
                TelephonyManager.CALL_STATE_RINGING -> CallState.Ringing(incomingNumber)
                TelephonyManager.CALL_STATE_OFFHOOK -> CallState.Offhook
                else -> CallState.Idle
            }

            val desc = when (state) {
                TelephonyManager.CALL_STATE_IDLE -> "IDLE: Device has no active or ringing calls"
                TelephonyManager.CALL_STATE_RINGING -> "RINGING: Incoming call waiting (${incomingNumber ?: "number hidden/restricted"})"
                TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK: At least one call exists that is dialing, active, or on hold"
                else -> "UNKNOWN STATE ($state)"
            }

            return CallStateInfo(
                state = callState,
                rawStateInt = state,
                description = desc
            )
        }
    }
}

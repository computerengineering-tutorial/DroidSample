package com.example.droidsample.calling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Helper utility for triggering outgoing cellular and telecom calls.
 * Covers:
 * 1. Intent.ACTION_DIAL (Safe, user confirmation required, no dangerous permission).
 * 2. Intent.ACTION_CALL (Direct call, requires CALL_PHONE dangerous permission).
 * 3. TelecomManager.placeCall() (Direct system telecom call dispatch with PhoneAccount extras).
 */
object CallTriggerHelper {

    /**
     * Sanitizes and formats the given phone number string into a standard "tel:" URI.
     */
    fun createTelUri(phoneNumber: String): Uri {
        val sanitized = phoneNumber.trim().replace(" ", "").replace("-", "")
        return Uri.parse("tel:${Uri.encode(sanitized)}")
    }

    /**
     * Checks if the app currently holds the CALL_PHONE permission.
     */
    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Opens the system dialer with the phone number pre-filled.
     * Does NOT require CALL_PHONE permission because the user explicitly presses Call in the dialer.
     */
    fun openDialer(context: Context, phoneNumber: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = createTelUri(phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Initiates a direct phone call immediately without opening the dialer UI.
     * Requires Manifest.permission.CALL_PHONE.
     */
    fun makeDirectCall(context: Context, phoneNumber: String): Result<Unit> = runCatching {
        if (!hasCallPhonePermission(context)) {
            throw SecurityException("Manifest.permission.CALL_PHONE is required to place direct calls.")
        }
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = createTelUri(phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Places a call programmatically via TelecomManager.
     * Can optionally bind the call to a specific registered PhoneAccountHandle (e.g. SIM slot or VoIP account).
     * Requires Manifest.permission.CALL_PHONE.
     */
    fun placeTelecomCall(
        context: Context,
        phoneNumber: String,
        phoneAccountHandle: PhoneAccountHandle? = null
    ): Result<Unit> = runCatching {
        if (!hasCallPhonePermission(context)) {
            throw SecurityException("Manifest.permission.CALL_PHONE is required for TelecomManager.placeCall().")
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: throw IllegalStateException("TelecomManager service is unavailable on this device.")

        val extras = Bundle().apply {
            if (phoneAccountHandle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
        }

        telecomManager.placeCall(createTelUri(phoneNumber), extras)
    }
}

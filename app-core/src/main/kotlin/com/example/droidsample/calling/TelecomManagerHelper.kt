package com.example.droidsample.calling

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.droidsample.R

/**
 * TelecomManager helper for managing PhoneAccounts, PhoneAccountHandles,
 * self-managed VoIP call requests, and incoming/outgoing Telecom connections.
 */
object TelecomManagerHelper {

    private const val VOIP_ACCOUNT_ID = "DroidSample_VoIP_Account"
    private const val VOIP_LABEL = "DroidSample VoIP Service"

    fun getTelecomManager(context: Context): TelecomManager? {
        return context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    /**
     * Creates the PhoneAccountHandle pointing to AppConnectionService.
     */
    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context, AppConnectionService::class.java)
        return PhoneAccountHandle(componentName, VOIP_ACCOUNT_ID)
    }

    /**
     * Checks if MANAGE_OWN_CALLS (Android 8.0+) is granted.
     */
    fun hasManageOwnCallsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.MANAGE_OWN_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Registers a self-managed PhoneAccount with TelecomManager.
     */
    fun registerVoipPhoneAccount(context: Context): Result<Unit> = runCatching {
        val telecomManager = getTelecomManager(context)
            ?: throw IllegalStateException("TelecomManager is unavailable")

        val handle = getPhoneAccountHandle(context)

        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)

        var capabilities = PhoneAccount.CAPABILITY_CALL_PROVIDER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            capabilities = capabilities or PhoneAccount.CAPABILITY_SELF_MANAGED
        }

        val phoneAccount = PhoneAccount.builder(handle, VOIP_LABEL)
            .setAddress(Uri.fromParts("tel", "1000", null))
            .setSubscriptionAddress(Uri.fromParts("tel", "1000", null))
            .setCapabilities(capabilities)
            .setIcon(icon)
            .setShortDescription("In-app VoIP telecom connection provider")
            .setHighlightColor(0xFF0066CC.toInt())
            .build()

        telecomManager.registerPhoneAccount(phoneAccount)
    }

    /**
     * Unregisters the PhoneAccount.
     */
    fun unregisterVoipPhoneAccount(context: Context): Result<Unit> = runCatching {
        val telecomManager = getTelecomManager(context) ?: return@runCatching
        val handle = getPhoneAccountHandle(context)
        telecomManager.unregisterPhoneAccount(handle)
    }

    /**
     * Verifies if the PhoneAccount is registered and enabled by the system.
     */
    fun isPhoneAccountRegistered(context: Context): Boolean {
        val telecomManager = getTelecomManager(context) ?: return false
        val handle = getPhoneAccountHandle(context)
        return try {
            val account = telecomManager.getPhoneAccount(handle)
            account != null && account.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Requests Telecom framework to inject an incoming call to our AppConnectionService.
     */
    fun simulateIncomingVoipCall(
        context: Context,
        callerNumber: String,
        callerName: String = "VoIP Test Caller"
    ): Result<Unit> = runCatching {
        val telecomManager = getTelecomManager(context)
            ?: throw IllegalStateException("TelecomManager unavailable")

        if (!hasManageOwnCallsPermission(context)) {
            throw SecurityException("MANAGE_OWN_CALLS permission is required for self-managed VoIP calls.")
        }

        val handle = getPhoneAccountHandle(context)
        val extras = Bundle().apply {
            putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts("tel", callerNumber, null)
            )
            putString(TelecomManager.EXTRA_CALL_BACK_NUMBER, callerNumber)
            putString("EXTRA_CALLER_NAME", callerName)
        }

        telecomManager.addNewIncomingCall(handle, extras)
    }

    /**
     * Requests Telecom framework to initiate a self-managed outgoing VoIP call.
     */
    fun startOutgoingVoipCall(
        context: Context,
        destinationNumber: String,
        recipientName: String = "Recipient"
    ): Result<Unit> = runCatching {
        val telecomManager = getTelecomManager(context)
            ?: throw IllegalStateException("TelecomManager unavailable")

        if (!hasManageOwnCallsPermission(context)) {
            throw SecurityException("MANAGE_OWN_CALLS permission is required.")
        }

        val handle = getPhoneAccountHandle(context)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
            val callExtras = Bundle().apply {
                putString("EXTRA_RECIPIENT_NAME", recipientName)
                putString("EXTRA_PHONE_NUMBER", destinationNumber)
            }
            putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callExtras)
        }

        telecomManager.placeCall(Uri.fromParts("tel", destinationNumber, null), extras)
    }

    /**
     * Checks if this application is currently the device's Default Phone (Dialer) app.
     */
    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = getTelecomManager(context) ?: return false
            @Suppress("DEPRECATION")
            telecomManager.defaultDialerPackage == context.packageName
        }
    }

    /**
     * Creates an Intent to prompt the user to set this app as the Default Phone app.
     */
    fun createDefaultDialerIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }
}

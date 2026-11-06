package com.example.droidsample.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Encapsulates the privileges and capabilities granted to the Default SMS App on Android.
 */
data class SmsRolePrivilege(
    val title: String,
    val description: String,
    val isDefaultOnly: Boolean
)

/**
 * Helper to check and request the Default SMS App role using RoleManager (API 29+)
 * or legacy Telephony.Sms intents (API 19-28).
 */
class DefaultSmsRoleManager(private val context: Context) {

    companion object {
        /**
         * List of security-critical privileges unlocked when the app is granted Default SMS App status.
         */
        val PRIVILEGES = listOf(
            SmsRolePrivilege(
                title = "Telephony Provider Write Access",
                description = "Ability to insert, update, and delete messages directly in Telephony.Sms (inbox, sent, drafts). Non-default apps receive a SecurityException.",
                isDefaultOnly = true
            ),
            SmsRolePrivilege(
                title = "Exclusive SMS_DELIVER Broadcast",
                description = "Receives android.provider.Telephony.SMS_DELIVER directly. Can write to database before secondary apps receive SMS_RECEIVED.",
                isDefaultOnly = true
            ),
            SmsRolePrivilege(
                title = "WAP Push & MMS Download",
                description = "Directly receives WAP_PUSH_DELIVER_ACTION and initiates cellular APN data bearer connection to carrier MMSC.",
                isDefaultOnly = true
            ),
            SmsRolePrivilege(
                title = "Rate Limit & Doze Exemption",
                description = "Exempt from system SMS sending frequency rate limits (anti-spam throttling) and background execution restrictions.",
                isDefaultOnly = true
            ),
            SmsRolePrivilege(
                title = "Read SMS & Contacts Access",
                description = "Reads SMS messages and contacts for threaded conversation grouping.",
                isDefaultOnly = false
            )
        )
    }

    /**
     * Checks if this application currently holds the Default SMS App role.
     */
    fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
            defaultPackage == context.packageName
        }
    }

    /**
     * Checks if the platform supports requesting the SMS role via RoleManager.
     */
    fun isRoleAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true
        } else {
            true
        }
    }

    /**
     * Creates an Intent to prompt the user to make this application the default SMS app.
     */
    fun createRequestRoleIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    /**
     * Returns the package name of the current default SMS application on the device.
     */
    fun getCurrentDefaultSmsPackage(): String {
        return Telephony.Sms.getDefaultSmsPackage(context) ?: "None / System"
    }
}

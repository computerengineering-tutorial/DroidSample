package com.example.droidsample.storage

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val minSdk: Int = 1,
    val maxSdk: Int? = null,
    val isGranted: Boolean
)

data class SpecialAccessItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val settingsAction: String,
    val settingsUri: Uri? = null
)

data class RoleItem(
    val roleName: String,
    val displayName: String,
    val isAvailable: Boolean,
    val isHeld: Boolean
)

data class BiometricStatus(
    val canAuthenticateCode: Int,
    val message: String,
    val isAvailable: Boolean
)

/**
 * Central manager for checking and requesting dangerous runtime permissions,
 * system roles (RoleManager), Special App Access (All Files, Overlays, Battery),
 * and Biometric authentication capability.
 */
class PermissionRoleManager(private val context: Context) {

    /**
     * Inspects the status of all core dangerous permissions used across DroidSample.
     */
    fun getPermissionsMatrix(): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        fun addPerm(perm: String, title: String, desc: String, minSdk: Int = 1, maxSdk: Int? = null) {
            val applies = (Build.VERSION.SDK_INT >= minSdk) && (maxSdk == null || Build.VERSION.SDK_INT <= maxSdk)
            val granted = if (applies) {
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            list.add(
                PermissionItem(
                    permission = perm,
                    title = title,
                    description = desc,
                    minSdk = minSdk,
                    maxSdk = maxSdk,
                    isGranted = granted
                )
            )
        }

        addPerm(Manifest.permission.CAMERA, "Camera", "Capture photos and test camera preview")
        addPerm(Manifest.permission.RECORD_AUDIO, "Record Audio", "Capture microphone audio")
        addPerm(Manifest.permission.ACCESS_FINE_LOCATION, "Fine Location", "Precise GPS and GNSS coordinates")
        addPerm(Manifest.permission.ACCESS_COARSE_LOCATION, "Coarse Location", "Approximate network location")
        addPerm(Manifest.permission.READ_CONTACTS, "Contacts", "Read address book contacts")
        addPerm(Manifest.permission.READ_CALL_LOG, "Call Log", "Read incoming and outgoing call log")
        addPerm(Manifest.permission.READ_PHONE_STATE, "Phone State", "Read SIM state and carrier network details")
        addPerm(Manifest.permission.SEND_SMS, "Send SMS", "Send test SMS messages")
        addPerm(Manifest.permission.READ_SMS, "Read SMS", "Read incoming SMS messages")

        // Media permissions for Android 13+ (API 33+)
        addPerm(Manifest.permission.READ_MEDIA_IMAGES, "Read Media Images", "Access gallery photos", minSdk = 33)
        addPerm(Manifest.permission.READ_MEDIA_VIDEO, "Read Media Video", "Access gallery videos", minSdk = 33)
        addPerm(Manifest.permission.READ_MEDIA_AUDIO, "Read Media Audio", "Access music and audio tracks", minSdk = 33)

        // Legacy external storage (Android 12 and below)
        addPerm(Manifest.permission.READ_EXTERNAL_STORAGE, "Read Storage (Legacy)", "Access shared storage on Android <=12", maxSdk = 32)
        addPerm(Manifest.permission.WRITE_EXTERNAL_STORAGE, "Write Storage (Legacy)", "Write shared storage on Android <=29", maxSdk = 29)

        // Notification permission for Android 13+
        addPerm(Manifest.permission.POST_NOTIFICATIONS, "Post Notifications", "Display system notifications", minSdk = 33)

        return list
    }

    /**
     * Inspects Special App Access permissions:
     * - All Files Access (MANAGE_EXTERNAL_STORAGE)
     * - System Alert Window (Can Draw Overlays)
     * - Battery Optimization Whitelist
     */
    fun getSpecialAppAccess(): List<SpecialAccessItem> {
        val items = mutableListOf<SpecialAccessItem>()

        // 1. All Files Access (API 30+)
        val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val allFilesAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        items.add(
            SpecialAccessItem(
                title = "All Files Access",
                description = "Broad access to external storage (MANAGE_EXTERNAL_STORAGE)",
                isGranted = hasAllFiles,
                settingsAction = allFilesAction,
                settingsUri = Uri.parse("package:${context.packageName}")
            )
        )

        // 2. System Alert Window (Overlay)
        val canDrawOverlays = Settings.canDrawOverlays(context)
        items.add(
            SpecialAccessItem(
                title = "Display Over Other Apps",
                description = "Allows showing floating UI or system alerts (SYSTEM_ALERT_WINDOW)",
                isGranted = canDrawOverlays,
                settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                settingsUri = Uri.parse("package:${context.packageName}")
            )
        )

        // 3. Battery Optimizations
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        items.add(
            SpecialAccessItem(
                title = "Ignore Battery Optimizations",
                description = "Allow running foreground background diagnostics without throttling",
                isGranted = ignoringBattery,
                settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                settingsUri = Uri.parse("package:${context.packageName}")
            )
        )

        return items
    }

    /**
     * Inspects system roles available via RoleManager (Android 10+ / API 29+).
     */
    fun getRoles(): List<RoleItem> {
        val items = mutableListOf<RoleItem>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return items
        }

        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return items

        val supportedRoles = listOf(
            Pair(RoleManager.ROLE_SMS, "Default SMS App"),
            Pair(RoleManager.ROLE_DIALER, "Default Phone App"),
            Pair(RoleManager.ROLE_CALL_SCREENING, "Call Screening Service"),
            Pair(RoleManager.ROLE_BROWSER, "Default Browser"),
            Pair(RoleManager.ROLE_HOME, "Home Launcher")
        )

        for ((role, displayName) in supportedRoles) {
            val available = roleManager.isRoleAvailable(role)
            val held = if (available) roleManager.isRoleHeld(role) else false
            items.add(
                RoleItem(
                    roleName = role,
                    displayName = displayName,
                    isAvailable = available,
                    isHeld = held
                )
            )
        }
        return items
    }

    /**
     * Builds an Intent to request a system role via RoleManager.
     */
    fun createRequestRoleIntent(role: String): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.createRequestRoleIntent(role)
        }
        return null
    }

    /**
     * Checks Biometric hardware and enrollment capability.
     */
    fun checkBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val code = biometricManager.canAuthenticate(authenticators)
        val (message, isAvailable) = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> Pair("Biometric authentication is ready and enrolled", true)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Pair("No biometric hardware present on this device", false)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Pair("Biometric features are currently unavailable", false)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Pair("No biometric credentials or device lock enrolled", false)
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Pair("Security update required for biometrics", false)
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> Pair("Biometric status is unknown", false)
            else -> Pair("Status code: $code", false)
        }

        return BiometricStatus(
            canAuthenticateCode = code,
            message = message,
            isAvailable = isAvailable
        )
    }

    /**
     * Launches a BiometricPrompt on the given FragmentActivity.
     */
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Biometric Authentication Test",
        subtitle: String = "Verify your fingerprint or face to authenticate",
        negativeButtonText: String = "Cancel",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        prompt.authenticate(promptInfo)
    }
}

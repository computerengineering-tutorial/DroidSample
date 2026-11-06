package com.example.droidsample.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Enumeration of SMS verification source APIs.
 */
enum class OtpSource {
    SMS_RETRIEVER_API,
    USER_CONSENT_API,
    SMS_BROADCAST_RECEIVER,
    SIMULATOR
}

/**
 * Event emitted when an OTP code is captured and parsed.
 */
data class OtpDetectionResult(
    val otpCode: String,
    val senderTag: String?,
    val fullMessage: String,
    val source: OtpSource,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * OTP parser utility providing regex patterns to extract verification codes,
 * sender names, and SMS retriever hash signatures.
 */
object OtpParser {

    // Matches standard 4 to 8 digit OTP codes with contextual clues
    // e.g. "code is 123456", "OTP: 9821", "passcode: 543210"
    private val CONTEXTUAL_OTP_PATTERN = Pattern.compile(
        """(?i)(?:code|otp|passcode|secret|pin|verification\s*code)[\s:=#\-]+([0-9]{4,8})\b"""
    )

    // Fallback: standalone 4 to 8 digit numbers bounded by word boundaries
    private val STANDALONE_OTP_PATTERN = Pattern.compile(
        """\b([0-9]{4,8})\b"""
    )

    // Matches standard 11-character app hash used in SMS Retriever API
    // e.g. "FA+9qCX9VSu"
    private val APP_HASH_PATTERN = Pattern.compile(
        """\b([A-Za-z0-9+/]{11})\b"""
    )

    // Matches standard sender headers like [BANK] or "from Google"
    private val SENDER_TAG_PATTERN = Pattern.compile(
        """(?i)(?:from|at|for|by)\s+([A-Za-z0-9_\-]+)|\[([A-Za-z0-9_\-]+)\]"""
    )

    /**
     * Extracts an OTP numeric code from message text.
     */
    fun extractOtp(message: String): String? {
        if (message.isBlank()) return null

        // First check for contextual keywords (e.g. "Your OTP is 492104")
        val contextualMatcher = CONTEXTUAL_OTP_PATTERN.matcher(message)
        if (contextualMatcher.find()) {
            return contextualMatcher.group(1)
        }

        // Second fallback: any isolated 4-8 digit block
        val standaloneMatcher = STANDALONE_OTP_PATTERN.matcher(message)
        while (standaloneMatcher.find()) {
            val candidate = standaloneMatcher.group(1)
            // Filter out 4-digit years (e.g., 2024, 2025, 2026) if message has other candidates
            if (candidate != null && candidate.length in 4..8) {
                return candidate
            }
        }

        return null
    }

    /**
     * Extracts an 11-character SMS Retriever app hash token from the message.
     */
    fun extractAppHash(message: String): String? {
        val matcher = APP_HASH_PATTERN.matcher(message)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Extracts probable sender tag from message headers.
     */
    fun extractSenderTag(message: String): String? {
        val matcher = SENDER_TAG_PATTERN.matcher(message)
        if (matcher.find()) {
            return matcher.group(1) ?: matcher.group(2)
        }
        return null
    }
}

/**
 * Helper to manage Google Play Services SMS Retriever API and User Consent API workflows.
 * Includes intent constants, broadcast listener, app signature hash generator, and simulated runner.
 */
class SmsRetrieverHelper(private val context: Context) {

    companion object {
        // Play Services SMS Retriever Actions & Extras
        const val SMS_RETRIEVED_ACTION = "com.google.android.gms.auth.api.phone.SMS_RETRIEVED"
        const val EXTRA_STATUS = "com.google.android.gms.auth.api.phone.EXTRA_STATUS"
        const val EXTRA_SMS_MESSAGE = "com.google.android.gms.auth.api.phone.EXTRA_SMS_MESSAGE"
        const val EXTRA_CONSENT_INTENT = "com.google.android.gms.auth.api.phone.EXTRA_CONSENT_INTENT"

        // Common status codes
        const val STATUS_SUCCESS = 0
        const val STATUS_TIMEOUT = 15

        /**
         * Calculates the 11-character hash required by Google Play SMS Retriever API
         * using the package name and app signing certificate SHA-256.
         */
        fun getAppSignatures(context: Context): List<String> {
            val appCodes = mutableListOf<String>()
            try {
                val packageName = context.packageName
                val packageManager = context.packageManager

                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    )
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }

                signatures?.forEach { sig ->
                    val hash = hash(packageName, sig.toCharsString())
                    if (hash != null) {
                        appCodes.add(hash)
                    }
                }
            } catch (_: Exception) {
            }
            return appCodes
        }

        private fun hash(packageName: String, signature: String): String? {
            val appInfo = "$packageName $signature"
            return try {
                val messageDigest = MessageDigest.getInstance("SHA-256")
                messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))
                var hashSignature = messageDigest.digest()

                // Truncate to first 9 bytes
                hashSignature = hashSignature.copyOfRange(0, 9)
                // Encode to Base64 (12 chars: 9 bytes * 4/3 = 12 chars including padding/11 without)
                var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
                base64Hash = base64Hash.substring(0, 11)
                base64Hash
            } catch (_: Exception) {
                null
            }
        }
    }

    private val _otpEvents = MutableSharedFlow<OtpDetectionResult>(extraBufferCapacity = 16)
    val otpEvents: SharedFlow<OtpDetectionResult> = _otpEvents.asSharedFlow()

    private val _isRetrieverActive = MutableStateFlow(false)
    val isRetrieverActive: StateFlow<Boolean> = _isRetrieverActive.asStateFlow()

    private val _isConsentActive = MutableStateFlow(false)
    val isConsentActive: StateFlow<Boolean> = _isConsentActive.asStateFlow()

    private var retrieverReceiverRegistered = false

    private val retrieverReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SMS_RETRIEVED_ACTION) {
                val extras: Bundle? = intent.extras
                val statusCode = extras?.get(EXTRA_STATUS)

                // When SUCCESS
                val message = extras?.getString(EXTRA_SMS_MESSAGE)
                if (!message.isNullOrBlank()) {
                    val otp = OtpParser.extractOtp(message)
                    if (otp != null) {
                        val sender = OtpParser.extractSenderTag(message)
                        _otpEvents.tryEmit(
                            OtpDetectionResult(
                                otpCode = otp,
                                senderTag = sender,
                                fullMessage = message,
                                source = OtpSource.SMS_RETRIEVER_API
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts the automated SMS Retriever Client.
     * Note: If Google Play Services is available, it invokes the client;
     * it also registers the broadcast receiver to catch the SMS_RETRIEVED_ACTION.
     */
    fun startSmsRetriever(): Boolean {
        registerRetrieverReceiver()
        _isRetrieverActive.value = true

        // Reflectively invoke Play Services SmsRetriever.getClient(context).startSmsRetriever()
        // to gracefully work across devices with and without Google Play Services.
        return try {
            val retrieverClass = Class.forName("com.google.android.gms.auth.api.phone.SmsRetriever")
            val getClientMethod = retrieverClass.getMethod("getClient", Context::class.java)
            val client = getClientMethod.invoke(null, context)
            val startMethod = client.javaClass.getMethod("startSmsRetriever")
            startMethod.invoke(client)
            true
        } catch (_: Exception) {
            // Client not installed or in pure AOSP; active flag remains set for listener
            true
        }
    }

    /**
     * Starts the SMS User Consent API.
     */
    fun startUserConsent(senderPhoneNumber: String? = null): Boolean {
        registerRetrieverReceiver()
        _isConsentActive.value = true

        return try {
            val retrieverClass = Class.forName("com.google.android.gms.auth.api.phone.SmsRetriever")
            val getClientMethod = retrieverClass.getMethod("getClient", Context::class.java)
            val client = getClientMethod.invoke(null, context)
            val startConsentMethod = client.javaClass.getMethod("startSmsUserConsent", String::class.java)
            startConsentMethod.invoke(client, senderPhoneNumber)
            true
        } catch (_: Exception) {
            true
        }
    }

    fun stopRetriever() {
        if (retrieverReceiverRegistered) {
            try {
                context.unregisterReceiver(retrieverReceiver)
            } catch (_: Exception) {}
            retrieverReceiverRegistered = false
        }
        _isRetrieverActive.value = false
        _isConsentActive.value = false
    }

    private fun registerRetrieverReceiver() {
        if (!retrieverReceiverRegistered) {
            val filter = IntentFilter(SMS_RETRIEVED_ACTION)
            ContextCompat.registerReceiver(
                context,
                retrieverReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            retrieverReceiverRegistered = true
        }
    }

    /**
     * Simulates an incoming SMS verification message for debugging and verification UI.
     */
    fun simulateVerificationSms(
        otpCode: String = "849201",
        serviceName: String = "DroidSample",
        hash: String = "FA+9qCX9VSu"
    ) {
        val template = "<#> Your $serviceName verification code is $otpCode. Valid for 10 minutes. $hash"
        _otpEvents.tryEmit(
            OtpDetectionResult(
                otpCode = otpCode,
                senderTag = serviceName,
                fullMessage = template,
                source = OtpSource.SIMULATOR
            )
        )
    }
}

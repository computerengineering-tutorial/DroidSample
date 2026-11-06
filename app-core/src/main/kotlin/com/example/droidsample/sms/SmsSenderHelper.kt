package com.example.droidsample.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Representation of an active SIM card subscription.
 */
data class SimSubscription(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String? = null,
    val isDefaultSms: Boolean = false
)

/**
 * Information regarding SMS encoding (GSM 7-bit vs UCS-2 16-bit).
 */
data class SmsEncodingInfo(
    val isUnicode: Boolean,
    val characterCount: Int,
    val segmentCount: Int,
    val remainingInCurrentSegment: Int,
    val encodingName: String,
    val segmentLimit: Int
)

/**
 * Record of sent SMS status tracking.
 */
data class SmsDispatchRecord(
    val id: String = UUID.randomUUID().toString(),
    val address: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val subscriptionId: Int,
    val isMultipart: Boolean,
    val segments: Int,
    val sentStatus: String = "Pending",
    val deliveryStatus: String = "Pending",
    val errorMessage: String? = null
)

/**
 * Helper class managing SMS sending via SmsManager, SIM selection,
 * multipart concatenation, Unicode/GSM segment calculations, and delivery receipts.
 */
class SmsSenderHelper(private val context: Context) {

    companion object {
        const val ACTION_SMS_SENT = "com.example.droidsample.sms.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.droidsample.sms.SMS_DELIVERED"
        const val EXTRA_DISPATCH_ID = "extra_dispatch_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"

        // GSM 7-bit default alphabet character set
        private val GSM_7BIT_CHARS = HashSet<Char>().apply {
            val basic = "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
            for (c in basic) add(c)
            // GSM 7-bit extension characters (each counts as 2 chars in GSM 7-bit):
            // ^ { } \ [ ~ ] | €
            val extension = "^{}\\[~]|€"
            for (c in extension) add(c)
        }

        private val GSM_EXTENSION_CHARS = setOf('^', '{', '}', '\\', '[', '~', ']', '|', '€')

        /**
         * Checks if a character sequence contains any character outside the GSM 7-bit alphabet.
         */
        fun isUnicodeText(text: String): Boolean {
            for (c in text) {
                if (!GSM_7BIT_CHARS.contains(c)) {
                    return true
                }
            }
            return false
        }

        /**
         * Calculates GSM 7-bit vs UCS-2 character and segment counts.
         * Single GSM: 160 chars. Multipart GSM: 153 chars/segment.
         * Single UCS-2: 70 chars. Multipart UCS-2: 67 chars/segment.
         */
        fun calculateSmsEncoding(text: String): SmsEncodingInfo {
            if (text.isEmpty()) {
                return SmsEncodingInfo(
                    isUnicode = false,
                    characterCount = 0,
                    segmentCount = 1,
                    remainingInCurrentSegment = 160,
                    encodingName = "GSM 7-bit",
                    segmentLimit = 160
                )
            }

            val unicode = isUnicodeText(text)
            if (unicode) {
                // In UCS-2 (Unicode like Hindi, Emoji, Cyrillic, Chinese):
                // Characters are 16-bit code units. Emojis may take 2 chars (surrogate pairs).
                val charCount = text.length
                val singleLimit = 70
                val multipartLimit = 67

                val segmentCount = if (charCount <= singleLimit) {
                    1
                } else {
                    (charCount + multipartLimit - 1) / multipartLimit
                }

                val limit = if (segmentCount <= 1) singleLimit else multipartLimit
                val usedInCurrent = if (segmentCount <= 1) {
                    charCount
                } else {
                    charCount - (segmentCount - 1) * multipartLimit
                }
                val remaining = (limit - usedInCurrent).coerceAtLeast(0)

                return SmsEncodingInfo(
                    isUnicode = true,
                    characterCount = charCount,
                    segmentCount = segmentCount,
                    remainingInCurrentSegment = remaining,
                    encodingName = "UCS-2 (Unicode)",
                    segmentLimit = limit
                )
            } else {
                // GSM 7-bit
                var gsmCharWeight = 0
                for (c in text) {
                    gsmCharWeight += if (GSM_EXTENSION_CHARS.contains(c)) 2 else 1
                }

                val singleLimit = 160
                val multipartLimit = 153

                val segmentCount = if (gsmCharWeight <= singleLimit) {
                    1
                } else {
                    (gsmCharWeight + multipartLimit - 1) / multipartLimit
                }

                val limit = if (segmentCount <= 1) singleLimit else multipartLimit
                val usedInCurrent = if (segmentCount <= 1) {
                    gsmCharWeight
                } else {
                    gsmCharWeight - (segmentCount - 1) * multipartLimit
                }
                val remaining = (limit - usedInCurrent).coerceAtLeast(0)

                return SmsEncodingInfo(
                    isUnicode = false,
                    characterCount = gsmCharWeight,
                    segmentCount = segmentCount,
                    remainingInCurrentSegment = remaining,
                    encodingName = "GSM 7-bit",
                    segmentLimit = limit
                )
            }
        }
    }

    private val _dispatchHistory = MutableStateFlow<List<SmsDispatchRecord>>(emptyList())
    val dispatchHistory: StateFlow<List<SmsDispatchRecord>> = _dispatchHistory.asStateFlow()

    private var receiversRegistered = false

    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dispatchId = intent?.getStringExtra(EXTRA_DISPATCH_ID) ?: return
            val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
            val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)

            val statusText = when (resultCode) {
                Activity.RESULT_OK -> "Sent (Part ${partIndex + 1}/$totalParts OK)"
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Failed: Generic error"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "Failed: No cellular service"
                SmsManager.RESULT_ERROR_NULL_PDU -> "Failed: Null PDU"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Failed: Radio turned off"
                SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Failed: SMS rate limit exceeded"
                SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Failed: Short code not allowed"
                SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Failed: Short code never allowed"
                else -> "Failed: Error code $resultCode"
            }

            updateDispatchRecord(dispatchId) { record ->
                record.copy(
                    sentStatus = if (resultCode == Activity.RESULT_OK && totalParts > 1) {
                        "Sent ($totalParts parts delivered to network)"
                    } else statusText,
                    errorMessage = if (resultCode != Activity.RESULT_OK) statusText else null
                )
            }
        }
    }

    private val deliveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dispatchId = intent?.getStringExtra(EXTRA_DISPATCH_ID) ?: return
            val statusText = when (resultCode) {
                Activity.RESULT_OK -> "Delivered to recipient"
                else -> "Delivery report: code $resultCode"
            }

            updateDispatchRecord(dispatchId) { record ->
                record.copy(deliveryStatus = statusText)
            }
        }
    }

    init {
        registerCallbacks()
    }

    fun registerCallbacks() {
        if (!receiversRegistered) {
            ContextCompat.registerReceiver(
                context,
                sentReceiver,
                IntentFilter(ACTION_SMS_SENT),
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                deliveryReceiver,
                IntentFilter(ACTION_SMS_DELIVERED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiversRegistered = true
        }
    }

    fun unregisterCallbacks() {
        if (receiversRegistered) {
            try {
                context.unregisterReceiver(sentReceiver)
                context.unregisterReceiver(deliveryReceiver)
            } catch (_: Exception) {
            }
            receiversRegistered = false
        }
    }

    /**
     * Resolves the proper SmsManager instance for default SIM or selected subscription ID.
     */
    fun getSmsManager(subscriptionId: Int? = null): SmsManager {
        val baseManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        return if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                baseManager.createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            baseManager
        }
    }

    /**
     * Retrieves active SIM subscriptions from SubscriptionManager.
     */
    @Suppress("DEPRECATION")
    fun getAvailableSimCards(): List<SimSubscription> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        return try {
            val activeList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()

            activeList?.map { info ->
                SimSubscription(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: "Unknown Carrier",
                    number = info.number,
                    isDefaultSms = (info.subscriptionId == defaultSubId)
                )
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Sends an SMS message (single or multipart) with delivery and sent tracking.
     */
    fun sendSms(
        destinationAddress: String,
        messageText: String,
        subscriptionId: Int? = null
    ): Result<SmsDispatchRecord> {
        if (destinationAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Destination address cannot be empty"))
        }
        if (messageText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }

        return try {
            val smsManager = getSmsManager(subscriptionId)
            val parts = smsManager.divideMessage(messageText)
            val isMultipart = parts.size > 1
            val effectiveSubId = subscriptionId ?: SubscriptionManager.getDefaultSmsSubscriptionId()

            val dispatchRecord = SmsDispatchRecord(
                address = destinationAddress,
                message = messageText,
                subscriptionId = effectiveSubId,
                isMultipart = isMultipart,
                segments = parts.size,
                sentStatus = "In flight",
                deliveryStatus = "Awaiting delivery report"
            )

            // Add record to state flow
            _dispatchHistory.value = listOf(dispatchRecord) + _dispatchHistory.value

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            if (isMultipart) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()

                for (i in parts.indices) {
                    val sentIntent = Intent(ACTION_SMS_SENT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_DISPATCH_ID, dispatchRecord.id)
                        putExtra(EXTRA_PART_INDEX, i)
                        putExtra(EXTRA_TOTAL_PARTS, parts.size)
                    }
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            (dispatchRecord.id.hashCode() xor i),
                            sentIntent,
                            pendingIntentFlags
                        )
                    )

                    val deliveryIntent = Intent(ACTION_SMS_DELIVERED).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_DISPATCH_ID, dispatchRecord.id)
                        putExtra(EXTRA_PART_INDEX, i)
                    }
                    deliveryIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            (dispatchRecord.id.hashCode() xor (i + 1000)),
                            deliveryIntent,
                            pendingIntentFlags
                        )
                    )
                }

                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                val sentIntent = Intent(ACTION_SMS_SENT).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_DISPATCH_ID, dispatchRecord.id)
                    putExtra(EXTRA_PART_INDEX, 0)
                    putExtra(EXTRA_TOTAL_PARTS, 1)
                }
                val sentPI = PendingIntent.getBroadcast(
                    context,
                    dispatchRecord.id.hashCode(),
                    sentIntent,
                    pendingIntentFlags
                )

                val deliveryIntent = Intent(ACTION_SMS_DELIVERED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_DISPATCH_ID, dispatchRecord.id)
                }
                val deliveryPI = PendingIntent.getBroadcast(
                    context,
                    dispatchRecord.id.hashCode() + 1,
                    deliveryIntent,
                    pendingIntentFlags
                )

                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    messageText,
                    sentPI,
                    deliveryPI
                )
            }

            Result.success(dispatchRecord)
        } catch (e: Exception) {
            val failureRecord = SmsDispatchRecord(
                address = destinationAddress,
                message = messageText,
                subscriptionId = subscriptionId ?: -1,
                isMultipart = false,
                segments = 1,
                sentStatus = "Failed",
                deliveryStatus = "None",
                errorMessage = e.localizedMessage ?: "Unknown error"
            )
            _dispatchHistory.value = listOf(failureRecord) + _dispatchHistory.value
            Result.failure(e)
        }
    }

    private fun updateDispatchRecord(id: String, transform: (SmsDispatchRecord) -> SmsDispatchRecord) {
        _dispatchHistory.value = _dispatchHistory.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    fun clearHistory() {
        _dispatchHistory.value = emptyList()
    }
}

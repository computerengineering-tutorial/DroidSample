package com.example.droidsample.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Data model for a received SMS broadcast event.
 */
data class ReceivedSms(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val body: String,
    val timestamp: Long,
    val subscriptionId: Int,
    val format: String,
    val pduCount: Int,
    val rawPduHexList: List<String>,
    val serviceCenterAddress: String? = null
)

/**
 * BroadcastReceiver for handling android.provider.Telephony.SMS_RECEIVED broadcasts.
 * Extracts PDUs, decodes GSM/CDMA formats, concatenates multipart messages,
 * and emits them to reactive SharedFlow and StateFlow streams.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private val _receivedMessagesFlow = MutableSharedFlow<ReceivedSms>(extraBufferCapacity = 64)
        val receivedMessagesFlow: SharedFlow<ReceivedSms> = _receivedMessagesFlow.asSharedFlow()

        private val _receivedHistoryState = MutableStateFlow<List<ReceivedSms>>(emptyList())
        val receivedHistoryState: StateFlow<List<ReceivedSms>> = _receivedHistoryState.asStateFlow()

        private val scope = CoroutineScope(Dispatchers.Default)

        /**
         * Clears the in-memory log of received broadcasts.
         */
        fun clearHistory() {
            _receivedHistoryState.value = emptyList()
        }

        /**
         * Simulates receiving an SMS message for testing or debug demonstrations.
         */
        fun simulateIncomingSms(
            sender: String,
            body: String,
            subscriptionId: Int = 1
        ) {
            val fakePduHex = body.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }
            val simulated = ReceivedSms(
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis(),
                subscriptionId = subscriptionId,
                format = "3gpp",
                pduCount = 1,
                rawPduHexList = listOf(fakePduHex),
                serviceCenterAddress = "+10000000000"
            )
            scope.launch {
                _receivedMessagesFlow.emit(simulated)
                _receivedHistoryState.value = listOf(simulated) + _receivedHistoryState.value
            }
        }

        /**
         * Helper to parse PDUs directly from intent extras.
         */
        @Suppress("DEPRECATION")
        fun parseSmsMessages(intent: Intent): List<SmsMessage> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList() ?: emptyList()
            } else {
                val extras = intent.extras ?: return emptyList()
                val pdus = extras.get("pdus") as? Array<*> ?: return emptyList()
                val format = extras.getString("format")

                pdus.mapNotNull { pdu ->
                    val pduBytes = pdu as? ByteArray ?: return@mapNotNull null
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format != null) {
                            SmsMessage.createFromPdu(pduBytes, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pduBytes)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = parseSmsMessages(intent)
        if (messages.isEmpty()) return

        val subscriptionId = intent.getIntExtra(
            "subscription",
            intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.getDefaultSmsSubscriptionId()
            )
        )

        val format = intent.getStringExtra("format") ?: "unknown"

        // Group PDUs by sender and timestamp to reconstruct multipart messages
        val firstMsg = messages.first()
        val sender = firstMsg.displayOriginatingAddress ?: firstMsg.originatingAddress ?: "Unknown"
        val timestamp = firstMsg.timestampMillis
        val serviceCenter = firstMsg.serviceCenterAddress

        // Concatenate all parts for this incoming batch
        val fullBodyBuilder = StringBuilder()
        val hexList = mutableListOf<String>()

        for (msg in messages) {
            val bodyPart = msg.displayMessageBody ?: msg.messageBody
            if (bodyPart != null) {
                fullBodyBuilder.append(bodyPart)
            }
            // Capture raw PDU bytes as hex if available
            msg.pdu?.let { pduBytes ->
                val hex = pduBytes.joinToString("") { "%02X".format(it) }
                hexList.add(hex)
            }
        }

        val receivedSms = ReceivedSms(
            sender = sender,
            body = fullBodyBuilder.toString(),
            timestamp = timestamp,
            subscriptionId = subscriptionId,
            format = format,
            pduCount = messages.size,
            rawPduHexList = hexList,
            serviceCenterAddress = serviceCenter
        )

        scope.launch {
            _receivedMessagesFlow.emit(receivedSms)
            _receivedHistoryState.value = listOf(receivedSms) + _receivedHistoryState.value
        }
    }
}

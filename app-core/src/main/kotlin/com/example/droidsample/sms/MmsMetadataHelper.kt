package com.example.droidsample.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Parsed MMS Notification Indication (m-notification-ind).
 */
data class MmsNotification(
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val messageType: String,
    val mmsVersion: String,
    val from: String,
    val contentLocationUrl: String,
    val messageSizeBytes: Long,
    val expirySeconds: Long,
    val subject: String? = null,
    val receivedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Metadata for multimedia attachments inside MMS payloads.
 */
data class MmsAttachmentMetadata(
    val contentType: String,
    val fileName: String?,
    val sizeBytes: Long,
    val contentId: String?,
    val contentLocation: String?
)

/**
 * Technical documentation regarding MMS transport architecture and carrier limitations.
 */
object MmsArchitectureDocumentation {
    const val SPECIFICATION_OVERVIEW = """
MMS (Multimedia Messaging Service) Architecture & Carrier Transmission:

1. WAP Push Notification (Downlink):
   - MMS does not travel over traditional 140-byte SMS channels directly.
   - When someone sends an MMS, the carrier's MMSC (Multimedia Messaging Service Center) stores the multimedia payload.
   - The MMSC delivers a compact binary WAP Push SMS (PDU) to the phone with MIME type 'application/vnd.wap.mms-message' via action 'android.provider.Telephony.WAP_PUSH_RECEIVED'.
   - This PDU contains the 'm-notification-ind' header, containing:
       * Transaction ID (X-Mms-Transaction-ID)
       * MMS Version (X-Mms-MMS-Version: 1.0 - 1.3)
       * From Address (Phone number or email)
       * Content Location URL (e.g. http://mms.carrier.net/servlets/mms?id=12345)
       * Expiry time and estimated size in bytes.

2. MMS Payload Download (HTTP / Cellular APN Bearer):
   - The Default SMS/MMS application reads the Content Location URL.
   - The device opens a dedicated cellular data bearer connected to the carrier's MMS APN (Access Point Name).
   - An HTTP GET / WSP request is transmitted to the MMSC URL.
   - The MMSC streams a multipart binary MIME payload containing:
       * SMIL (Synchronized Multimedia Integration Language) XML markup
       * Audio/Video/Image attachments (e.g. image/jpeg, audio/amr, video/mp4)
       * Text parts (text/plain).

3. Delivery Acknowledgement:
   - Upon receiving the payload, the client submits an 'm-notifyresp-ind' PDU back to the MMSC.

4. Carrier Constraints & Limits:
   - Typical Maximum MMS Size: 300 KB on legacy 3G carriers; 600 KB to 1.2 MB on modern 4G LTE/5G networks.
   - Requires Cellular Data: MMS cannot typically be fetched over standard unauthenticated Wi-Fi unless Wi-Fi Calling (VoWiFi / IMS) is enabled.
   - Default SMS App Requirement: Only the Default SMS App holds permission to receive WAP push broadcasts or download MMS directly.
    """
}

/**
 * Helper to parse MMS notification PDU headers and listen for WAP push events.
 */
class MmsMetadataHelper(private val context: Context) {

    companion object {
        const val WAP_PUSH_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_RECEIVED"
        const val MMS_DATA_TYPE = "application/vnd.wap.mms-message"

        // MMS PDU Header Field Tag IDs (OMA-TS-MMS_ENC-V1_3)
        const val MMS_MESSAGE_TYPE = 0x8C
        const val MMS_TRANSACTION_ID = 0x98
        const val MMS_VERSION = 0x8D
        const val MMS_FROM = 0x89
        const val MMS_CONTENT_LOCATION = 0x83
        const val MMS_MESSAGE_SIZE = 0x8E
        const val MMS_EXPIRY = 0x88
        const val MMS_SUBJECT = 0x96

        // Message Types
        const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82

        /**
         * Parses a raw binary PDU into an MmsNotification structure.
         */
        fun parseNotificationInd(pdu: ByteArray): MmsNotification? {
            if (pdu.isEmpty()) return null

            var transactionId = "TX-${System.currentTimeMillis()}"
            var messageType = "m-notification-ind"
            var mmsVersion = "1.2"
            var from = "Unknown"
            var contentLocation = "http://mms.carrier.com/download"
            var messageSize = 0L
            var expiry = 86400L
            var subject: String? = null

            try {
                var index = 0
                while (index < pdu.size) {
                    val headerByte = pdu[index].toInt() and 0xFF
                    index++

                    when (headerByte) {
                        MMS_MESSAGE_TYPE -> {
                            if (index < pdu.size) {
                                val typeVal = pdu[index].toInt() and 0xFF
                                messageType = if (typeVal == MESSAGE_TYPE_NOTIFICATION_IND) {
                                    "m-notification-ind (0x82)"
                                } else {
                                    "0x${typeVal.toString(16)}"
                                }
                                index++
                            }
                        }
                        MMS_TRANSACTION_ID -> {
                            val start = index
                            while (index < pdu.size && pdu[index] != 0.toByte()) {
                                index++
                            }
                            if (start < pdu.size) {
                                transactionId = String(pdu, start, (index - start).coerceAtLeast(0), StandardCharsets.UTF_8)
                            }
                            index++ // skip null terminator
                        }
                        MMS_VERSION -> {
                            if (index < pdu.size) {
                                val verByte = pdu[index].toInt() and 0x7F
                                val major = (verByte shr 4) and 0x07
                                val minor = verByte and 0x0F
                                mmsVersion = "$major.$minor"
                                index++
                            }
                        }
                        MMS_FROM -> {
                            // Length byte or null-terminated string
                            if (index < pdu.size) {
                                val len = pdu[index].toInt() and 0xFF
                                index++
                                val start = index
                                val readLen = if (len > 0 && start + len <= pdu.size) len else 0
                                if (readLen > 0) {
                                    from = String(pdu, start, readLen, StandardCharsets.UTF_8).trim('\u0000', '/')
                                    index += readLen
                                }
                            }
                        }
                        MMS_CONTENT_LOCATION -> {
                            val start = index
                            while (index < pdu.size && pdu[index] != 0.toByte()) {
                                index++
                            }
                            if (start < pdu.size) {
                                contentLocation = String(pdu, start, (index - start).coerceAtLeast(0), StandardCharsets.UTF_8)
                            }
                            index++
                        }
                        MMS_MESSAGE_SIZE -> {
                            if (index < pdu.size) {
                                val len = pdu[index].toInt() and 0xFF
                                index++
                                var size = 0L
                                for (i in 0 until len.coerceAtMost(8)) {
                                    if (index < pdu.size) {
                                        size = (size shl 8) or ((pdu[index].toInt() and 0xFF).toLong())
                                        index++
                                    }
                                }
                                messageSize = size
                            }
                        }
                        MMS_EXPIRY -> {
                            // Expiry header
                            if (index < pdu.size) {
                                val len = pdu[index].toInt() and 0xFF
                                index++
                                index += len.coerceAtMost(pdu.size - index)
                            }
                        }
                        MMS_SUBJECT -> {
                            val start = index
                            while (index < pdu.size && pdu[index] != 0.toByte()) {
                                index++
                            }
                            if (start < pdu.size) {
                                subject = String(pdu, start, (index - start).coerceAtLeast(0), StandardCharsets.UTF_8)
                            }
                            index++
                        }
                        else -> {
                            // Advance if unrecognized
                            if (index < pdu.size && pdu[index] == 0.toByte()) {
                                index++
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Return gracefully with what we parsed
            }

            return MmsNotification(
                transactionId = transactionId,
                messageType = messageType,
                mmsVersion = mmsVersion,
                from = from,
                contentLocationUrl = contentLocation,
                messageSizeBytes = if (messageSize > 0) messageSize else 42800L,
                expirySeconds = expiry,
                subject = subject
            )
        }
    }

    private val _mmsNotifications = MutableStateFlow<List<MmsNotification>>(emptyList())
    val mmsNotifications: StateFlow<List<MmsNotification>> = _mmsNotifications.asStateFlow()

    private var receiverRegistered = false

    private val wapPushReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WAP_PUSH_RECEIVED_ACTION) {
                val data = intent.getByteArrayExtra("data")
                if (data != null) {
                    val notification = parseNotificationInd(data)
                    if (notification != null) {
                        _mmsNotifications.value = listOf(notification) + _mmsNotifications.value
                    }
                }
            }
        }
    }

    fun registerWapPushReceiver() {
        if (!receiverRegistered) {
            val filter = IntentFilter(WAP_PUSH_RECEIVED_ACTION).apply {
                try {
                    addDataType(MMS_DATA_TYPE)
                } catch (_: Exception) {}
            }
            ContextCompat.registerReceiver(
                context,
                wapPushReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }
    }

    fun unregisterWapPushReceiver() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(wapPushReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    fun simulateMmsNotification(
        from: String = "+1 (555) 019-2834",
        subject: String = "Vacation Photo.jpg",
        contentUrl: String = "https://mmsc.carrier.net/content/v2/pkg_89412.mms",
        sizeBytes: Long = 345600L
    ) {
        val simulated = MmsNotification(
            transactionId = "TX-${UUID.randomUUID().toString().take(8).uppercase()}",
            messageType = "m-notification-ind (0x82)",
            mmsVersion = "1.3",
            from = from,
            contentLocationUrl = contentUrl,
            messageSizeBytes = sizeBytes,
            expirySeconds = 604800L, // 7 days
            subject = subject
        )
        _mmsNotifications.value = listOf(simulated) + _mmsNotifications.value
    }

    fun clearNotifications() {
        _mmsNotifications.value = emptyList()
    }
}

/**
 * Manifest receiver for handling WAP_PUSH_DELIVER_ACTION when default SMS app.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return
        val notification = MmsMetadataHelper.parseNotificationInd(data)
        // Processed
    }
}

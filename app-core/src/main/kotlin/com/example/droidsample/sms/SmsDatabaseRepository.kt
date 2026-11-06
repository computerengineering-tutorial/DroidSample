package com.example.droidsample.sms

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data model for a single SMS message stored in the Telephony database.
 */
data class SmsMessageItem(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int, // 1: Inbox, 2: Sent, 3: Draft, 4: Outbox, 5: Failed, 6: Queued
    val status: Int
) {
    val typeLabel: String
        get() = when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
            Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
            Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
            else -> "Unknown ($type)"
        }
}

/**
 * Summary model of a conversation thread grouped by THREAD_ID.
 */
data class SmsConversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val unreadCount: Int
)

/**
 * Repository providing access to the Android Telephony SMS content provider
 * (Telephony.Sms, Inbox, Sent, Conversations).
 * Handles READ_SMS checks and documents write restrictions (Default SMS role).
 */
class SmsDatabaseRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Checks if READ_SMS permission is granted.
     */
    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads all SMS messages across all boxes, ordered chronologically.
     */
    suspend fun getAllMessages(limit: Int = 100): Result<List<SmsMessageItem>> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) {
            return@withContext Result.failure(SecurityException("android.permission.READ_SMS permission is not granted"))
        }

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )

        val uri: Uri = Telephony.Sms.CONTENT_URI
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        try {
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val list = mutableListOf<SmsMessageItem>()
                val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadCol = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readCol = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val statusCol = c.getColumnIndexOrThrow(Telephony.Sms.STATUS)

                while (c.moveToNext()) {
                    list.add(
                        SmsMessageItem(
                            id = c.getLong(idCol),
                            threadId = c.getLong(threadCol),
                            address = c.getString(addrCol) ?: "Unknown",
                            body = c.getString(bodyCol) ?: "",
                            date = c.getLong(dateCol),
                            read = c.getInt(readCol) == 1,
                            type = c.getInt(typeCol),
                            status = c.getInt(statusCol)
                        )
                    )
                }
                return@withContext Result.success(list)
            } ?: Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries grouped conversation threads.
     */
    suspend fun getConversations(limit: Int = 50): Result<List<SmsConversation>> = withContext(Dispatchers.IO) {
        val messagesResult = getAllMessages(limit = 500)
        if (messagesResult.isFailure) {
            return@withContext Result.failure(messagesResult.exceptionOrNull()!!)
        }

        val messages = messagesResult.getOrDefault(emptyList())
        val grouped = messages.groupBy { it.threadId }

        val conversations = grouped.map { (threadId, msgs) ->
            val latest = msgs.maxByOrNull { it.date } ?: msgs.first()
            val unreadCount = msgs.count { !it.read && it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
            SmsConversation(
                threadId = threadId,
                address = latest.address,
                snippet = latest.body,
                date = latest.date,
                messageCount = msgs.size,
                unreadCount = unreadCount
            )
        }.sortedByDescending { it.date }.take(limit)

        Result.success(conversations)
    }

    /**
     * Reads all messages for a specific conversation thread.
     */
    suspend fun getMessagesForThread(threadId: Long): Result<List<SmsMessageItem>> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) {
            return@withContext Result.failure(SecurityException("android.permission.READ_SMS permission is not granted"))
        }

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )

        val uri: Uri = Telephony.Sms.CONTENT_URI
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        try {
            val cursor: Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val list = mutableListOf<SmsMessageItem>()
                val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadCol = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readCol = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val statusCol = c.getColumnIndexOrThrow(Telephony.Sms.STATUS)

                while (c.moveToNext()) {
                    list.add(
                        SmsMessageItem(
                            id = c.getLong(idCol),
                            threadId = c.getLong(threadCol),
                            address = c.getString(addrCol) ?: "Unknown",
                            body = c.getString(bodyCol) ?: "",
                            date = c.getLong(dateCol),
                            read = c.getInt(readCol) == 1,
                            type = c.getInt(typeCol),
                            status = c.getInt(statusCol)
                        )
                    )
                }
                return@withContext Result.success(list)
            } ?: Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks an SMS message as read or unread.
     * Note: On Android 4.4+ (KitKat and above), modifying the SMS database requires
     * the app to be selected as the Default SMS app. Otherwise a SecurityException is raised.
     */
    suspend fun markAsRead(messageId: Long, read: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, if (read) 1 else 0)
            }
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
            val rows = contentResolver.update(uri, values, null, null)
            Result.success(rows)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "Writing to SMS Provider requires default SMS app role: ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks all messages in a conversation thread as read or unread.
     */
    suspend fun markThreadRead(threadId: Long, read: Boolean = true): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, if (read) 1 else 0)
            }
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId.toString())
            val rows = contentResolver.update(uri, values, selection, selectionArgs)
            Result.success(rows)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "Writing to SMS Provider requires default SMS app role: ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an entire conversation thread.
     */
    suspend fun deleteThread(threadId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId.toString())
            val rows = contentResolver.delete(uri, selection, selectionArgs)
            Result.success(rows)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "Deleting from SMS Provider requires default SMS app role: ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inserts an SMS record directly into Telephony.Sms.Sent (used when default SMS app).
     */
    suspend fun insertSentMessage(address: String, body: String): Result<Uri?> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            val uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            Result.success(uri)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "Writing to SMS Provider requires default SMS app role: ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an SMS message from the provider.
     */
    suspend fun deleteMessage(messageId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
            val rows = contentResolver.delete(uri, null, null)
            Result.success(rows)
        } catch (e: SecurityException) {
            Result.failure(
                SecurityException(
                    "Deleting from SMS Provider requires default SMS app role: ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates realistic mock SMS conversation items for preview/testing when device database is empty or unpermitted.
     */
    fun getMockConversations(): List<SmsConversation> {
        val now = System.currentTimeMillis()
        return listOf(
            SmsConversation(
                threadId = 1L,
                address = "BANK-ALERT",
                snippet = "Your OTP for transaction of USD 45.00 is 839201. Valid for 10 minutes.",
                date = now - 1000 * 60 * 5,
                messageCount = 3,
                unreadCount = 1
            ),
            SmsConversation(
                threadId = 2L,
                address = "+1 (555) 234-5678",
                snippet = "Hey! Are you still joining us for the Android Developers meet tonight?",
                date = now - 1000 * 60 * 45,
                messageCount = 12,
                unreadCount = 0
            ),
            SmsConversation(
                threadId = 3L,
                address = "VERIFY-APP",
                snippet = "<#> 482109 is your verification passcode for MyApp. @example.com #482109",
                date = now - 1000 * 60 * 120,
                messageCount = 1,
                unreadCount = 0
            ),
            SmsConversation(
                threadId = 4L,
                address = "+91 98765 43210",
                snippet = "नमस्ते! आज शाम को मिलते हैं। प्रोजेक्ट पूरा हो गया।",
                date = now - 1000 * 60 * 60 * 6,
                messageCount = 5,
                unreadCount = 2
            ),
            SmsConversation(
                threadId = 5L,
                address = "CARRIER-SVC",
                snippet = "Data pack renewed successfully. 2.5 GB / day remaining. Dial *121# for info.",
                date = now - 1000 * 60 * 60 * 24,
                messageCount = 4,
                unreadCount = 0
            )
        )
    }

    fun getMockMessagesForThread(threadId: Long): List<SmsMessageItem> {
        val now = System.currentTimeMillis()
        return when (threadId) {
            1L -> listOf(
                SmsMessageItem(
                    id = 101L,
                    threadId = 1L,
                    address = "BANK-ALERT",
                    body = "Your balance for account XX8920 is USD 1,420.50.",
                    date = now - 1000 * 60 * 60 * 4,
                    read = true,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = 0
                ),
                SmsMessageItem(
                    id = 102L,
                    threadId = 1L,
                    address = "BANK-ALERT",
                    body = "Your OTP for transaction of USD 45.00 is 839201. Valid for 10 minutes. Do not share with anyone.",
                    date = now - 1000 * 60 * 5,
                    read = false,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = 0
                )
            )
            4L -> listOf(
                SmsMessageItem(
                    id = 401L,
                    threadId = 4L,
                    address = "+91 98765 43210",
                    body = "नमस्ते! आज शाम को मिलते हैं।",
                    date = now - 1000 * 60 * 60 * 8,
                    read = true,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = 0
                ),
                SmsMessageItem(
                    id = 402L,
                    threadId = 4L,
                    address = "+91 98765 43210",
                    body = "हाँ ज़रूर, 6 बजे कैफ़े पर मिलते हैं!",
                    date = now - 1000 * 60 * 60 * 7,
                    read = true,
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    status = 0
                ),
                SmsMessageItem(
                    id = 403L,
                    threadId = 4L,
                    address = "+91 98765 43210",
                    body = "प्रोजेक्ट पूरा हो गया।",
                    date = now - 1000 * 60 * 60 * 6,
                    read = false,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = 0
                )
            )
            else -> listOf(
                SmsMessageItem(
                    id = 201L,
                    threadId = threadId,
                    address = "+1 (555) 234-5678",
                    body = "Hello! This is a test message.",
                    date = now - 1000 * 60 * 30,
                    read = true,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = 0
                )
            )
        }
    }
}

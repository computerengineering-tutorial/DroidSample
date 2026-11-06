package com.example.droidsample.calling

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallType(val displayName: String) {
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    MISSED("Missed"),
    VOICEMAIL("Voicemail"),
    REJECTED("Rejected"),
    BLOCKED("Blocked"),
    OTHER("Other");

    companion object {
        fun fromCallLogType(type: Int): CallType = when (type) {
            CallLog.Calls.INCOMING_TYPE -> INCOMING
            CallLog.Calls.OUTGOING_TYPE -> OUTGOING
            CallLog.Calls.MISSED_TYPE -> MISSED
            CallLog.Calls.VOICEMAIL_TYPE -> VOICEMAIL
            CallLog.Calls.REJECTED_TYPE -> REJECTED
            CallLog.Calls.BLOCKED_TYPE -> BLOCKED
            else -> OTHER
        }
    }
}

data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val dateEpochMillis: Long,
    val durationSeconds: Long,
    val type: CallType
) {
    val formattedDuration: String
        get() = CallLogRepository.formatDuration(durationSeconds)

    val displayName: String
        get() = if (!cachedName.isNullOrBlank()) cachedName else number.ifBlank { "Unknown" }
}

object CallLogRepository {

    val PROJECTION = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.TYPE
    )

    fun hasReadCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Formats duration in seconds to "HH:mm:ss" or "mm:ss" or "Xs".
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> "${secs}s"
        }
    }

    /**
     * Parses a single row from a Cursor containing CallLog.Calls columns.
     */
    fun parseCursorRow(cursor: Cursor): CallLogEntry {
        val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
        val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)

        val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
        val number = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
        val duration = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L
        val typeRaw = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1

        return CallLogEntry(
            id = id,
            number = number,
            cachedName = name,
            dateEpochMillis = date,
            durationSeconds = duration,
            type = CallType.fromCallLogType(typeRaw)
        )
    }

    /**
     * Queries call logs from ContentResolver.
     */
    suspend fun queryCallLogs(
        context: Context,
        limit: Int = 100
    ): Result<List<CallLogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasReadCallLogPermission(context)) {
                return@runCatching emptyList()
            }

            val entries = mutableListOf<CallLogEntry>()

            val cursor = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = Bundle().apply {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        PROJECTION,
                        queryArgs,
                        null
                    )
                } else {
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        PROJECTION,
                        null,
                        null,
                        "${CallLog.Calls.DATE} DESC"
                    )
                }
            } catch (e: Exception) {
                // Fallback for devices where queryArgs is not supported by ContentProvider
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    PROJECTION,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
            }

            cursor?.use { c ->
                var count = 0
                while (c.moveToNext() && count < limit) {
                    entries.add(parseCursorRow(c))
                    count++
                }
            }

            entries
        }
    }

    /**
     * In-memory search and filter for call log entries.
     */
    fun filterEntries(
        entries: List<CallLogEntry>,
        searchQuery: String,
        selectedType: CallType?
    ): List<CallLogEntry> {
        return entries.filter { entry ->
            val matchesQuery = searchQuery.isBlank() ||
                    entry.number.contains(searchQuery, ignoreCase = true) ||
                    (entry.cachedName?.contains(searchQuery, ignoreCase = true) == true)

            val matchesType = selectedType == null || entry.type == selectedType

            matchesQuery && matchesType
        }
    }

}

package com.example.droidsample.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

data class DocumentMetadata(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val isDirectory: Boolean = false
)

/**
 * Storage Access Framework (SAF) & Photo Picker Helper.
 * Handles system picker intents, persistable URI permissions, and document I/O.
 */
class SafHelper(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Builds an Intent for ACTION_OPEN_DOCUMENT.
     */
    fun createOpenDocumentIntent(mimeTypes: Array<String> = arrayOf("*/*")): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            if (mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        }
    }

    /**
     * Builds an Intent for ACTION_CREATE_DOCUMENT.
     */
    fun createCreateDocumentIntent(
        fileName: String = "DroidSample_Document.txt",
        mimeType: String = "text/plain"
    ): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    /**
     * Builds an Intent for ACTION_OPEN_DOCUMENT_TREE.
     */
    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        }
    }

    /**
     * Persists URI permission across device reboots.
     */
    fun takePersistablePermissions(uri: Uri, flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION): Boolean {
        return try {
            contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspects document details (display name, size, mimeType) from a SAF URI.
     */
    fun getDocumentMetadata(uri: Uri): DocumentMetadata {
        var displayName = uri.lastPathSegment ?: "Unknown"
        var size = 0L
        var mimeType = contentResolver.getType(uri)
        var isDir = false

        if (DocumentsContract.isDocumentUri(context, uri)) {
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = it.getString(nameIndex) ?: displayName
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !it.isNull(sizeIndex)) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        } else if (DocumentsContract.isTreeUri(uri)) {
            isDir = true
            displayName = "Directory: ${uri.lastPathSegment ?: "Tree"}"
        }

        return DocumentMetadata(
            uri = uri,
            displayName = displayName,
            size = size,
            mimeType = mimeType,
            isDirectory = isDir
        )
    }

    /**
     * Reads text content from a document URI.
     */
    @Throws(IOException::class)
    fun readTextFromUri(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } ?: throw IOException("Could not open input stream for URI: $uri")
    }

    /**
     * Writes text content to a document URI.
     */
    @Throws(IOException::class)
    fun writeTextToUri(uri: Uri, text: String): Boolean {
        return try {
            contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                outputStream.write(text.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns Photo Picker visual media request config helper.
     */
    companion object {
        fun imageOnlyRequest(): androidx.activity.result.PickVisualMediaRequest {
            return androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        }

        fun videoOnlyRequest(): androidx.activity.result.PickVisualMediaRequest {
            return androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        }

        fun imageAndVideoRequest(): androidx.activity.result.PickVisualMediaRequest {
            return androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        }
    }
}

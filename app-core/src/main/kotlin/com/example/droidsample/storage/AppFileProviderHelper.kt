package com.example.droidsample.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Helper class for interacting with AndroidX FileProvider.
 * Encapsulates secure URI generation and share/view intent construction.
 */
class AppFileProviderHelper(private val context: Context) {

    val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"

    /**
     * Converts a local File into a secure content:// URI via FileProvider.
     */
    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(context, fileProviderAuthority, file)
    }

    /**
     * Creates a temporary sample file in the app's cache directory and returns its content URI.
     */
    @Throws(IOException::class)
    fun createShareableSampleFile(
        fileName: String = "DroidSample_Share_${System.currentTimeMillis()}.txt",
        content: String = "Hello from DroidSample! Secure FileProvider sharing."
    ): Pair<File, Uri> {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val uri = getUriForFile(file)
        return Pair(file, uri)
    }

    /**
     * Builds an ACTION_SEND Intent configured for sharing via FileProvider.
     */
    fun createShareIntent(uri: Uri, mimeType: String = "text/plain", subject: String = "DroidSample File"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Builds an ACTION_VIEW Intent configured for opening a FileProvider URI in an external viewer.
     */
    fun createViewIntent(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

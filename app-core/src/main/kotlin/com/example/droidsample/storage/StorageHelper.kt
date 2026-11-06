package com.example.droidsample.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Data representation of an internal or external storage directory.
 */
data class StorageLocationInfo(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val fileCount: Int,
    val isExternal: Boolean,
    val isRemovable: Boolean = false
)

/**
 * Helper utility for managing internal and external application storage,
 * calculating directory usage, cache eviction, and scoped storage rules.
 */
class StorageHelper(private val context: Context) {

    /**
     * Inspects primary app-specific storage locations:
     * - Internal Files (`context.filesDir`)
     * - Internal Cache (`context.cacheDir`)
     * - External App-Specific Files (`context.getExternalFilesDir(null)`)
     * - External App-Specific Cache (`context.externalCacheDir`)
     */
    fun getStorageLocations(): List<StorageLocationInfo> {
        val locations = mutableListOf<StorageLocationInfo>()

        // 1. Internal files
        val filesDir = context.filesDir
        locations.add(inspectDirectory("Internal Files", filesDir, isExternal = false))

        // 2. Internal cache
        val cacheDir = context.cacheDir
        locations.add(inspectDirectory("Internal Cache", cacheDir, isExternal = false))

        // 3. External app-specific files
        val extFilesDir = context.getExternalFilesDir(null)
        if (extFilesDir != null) {
            locations.add(inspectDirectory("External App Files", extFilesDir, isExternal = true))
        }

        // 4. External Documents directory
        val extDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDocsDir != null && extDocsDir != extFilesDir) {
            locations.add(inspectDirectory("External Documents", extDocsDir, isExternal = true))
        }

        // 5. External app-specific cache
        val extCacheDir = context.externalCacheDir
        if (extCacheDir != null) {
            locations.add(inspectDirectory("External Cache", extCacheDir, isExternal = true))
        }

        return locations
    }

    private fun inspectDirectory(name: String, dir: File, isExternal: Boolean): StorageLocationInfo {
        var fileCount = 0
        var totalDirectoryBytes = 0L

        if (dir.exists()) {
            val stats = calculateDirectoryUsage(dir)
            fileCount = stats.first
            totalDirectoryBytes = stats.second
        }

        val statFs = try {
            if (dir.exists()) StatFs(dir.absolutePath) else null
        } catch (_: Exception) {
            null
        }

        val totalBytes = statFs?.totalBytes ?: totalDirectoryBytes
        val freeBytes = statFs?.availableBytes ?: 0L
        val usedBytes = if (statFs != null) (totalBytes - freeBytes).coerceAtLeast(0L) else totalDirectoryBytes

        val isRemovable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isExternal) {
            try {
                Environment.isExternalStorageRemovable(dir)
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        return StorageLocationInfo(
            name = name,
            path = dir.absolutePath,
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            fileCount = fileCount,
            isExternal = isExternal,
            isRemovable = isRemovable
        )
    }

    /**
     * Recursively calculates file count and total size in bytes for a directory.
     * Returns Pair(fileCount, totalBytes).
     */
    fun calculateDirectoryUsage(dir: File): Pair<Int, Long> {
        if (!dir.exists() || !dir.isDirectory) {
            return if (dir.exists()) Pair(1, dir.length()) else Pair(0, 0L)
        }

        var count = 0
        var size = 0L
        val files = dir.listFiles() ?: return Pair(0, 0L)

        for (file in files) {
            if (file.isDirectory) {
                val sub = calculateDirectoryUsage(file)
                count += sub.first
                size += sub.second
            } else {
                count++
                size += file.length()
            }
        }
        return Pair(count, size)
    }

    /**
     * Clears internal cache and external cache files.
     * Returns the total number of bytes freed.
     */
    fun clearCache(): Long {
        var freedBytes = 0L
        val internalCache = context.cacheDir
        freedBytes += deleteDirectoryContents(internalCache)

        val externalCache = context.externalCacheDir
        if (externalCache != null) {
            freedBytes += deleteDirectoryContents(externalCache)
        }
        return freedBytes
    }

    private fun deleteDirectoryContents(dir: File): Long {
        if (!dir.exists()) return 0L
        var deletedSize = 0L
        val files = dir.listFiles() ?: return 0L

        for (file in files) {
            if (file.isDirectory) {
                deletedSize += deleteDirectoryContents(file)
                file.delete()
            } else {
                val len = file.length()
                if (file.delete()) {
                    deletedSize += len
                }
            }
        }
        return deletedSize
    }

    /**
     * Writes sample text file to internal app storage.
     */
    @Throws(IOException::class)
    fun writeInternalSampleFile(fileName: String, content: String): File {
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    /**
     * Reads text file from internal app storage.
     */
    @Throws(IOException::class)
    fun readInternalSampleFile(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) throw IOException("File does not exist: $fileName")
        return FileInputStream(file).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Writes sample text file to external app-specific documents storage.
     */
    @Throws(IOException::class)
    fun writeExternalSampleFile(fileName: String, content: String): File {
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!extDir.exists()) extDir.mkdirs()
        val file = File(extDir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    /**
     * Lists all files currently stored in internal `filesDir`.
     */
    fun listInternalFiles(): List<File> {
        return context.filesDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Deletes a file by name from internal filesDir.
     */
    fun deleteInternalFile(fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    companion object {
        /**
         * Formats bytes into human-readable B, KB, MB, GB format.
         */
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                .coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.2f %s", value, units[digitGroups])
        }
    }
}

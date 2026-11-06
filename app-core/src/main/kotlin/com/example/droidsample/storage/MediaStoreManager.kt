package com.example.droidsample.storage

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val mediaType: MediaType,
    val durationMs: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val resolution: String? = null
)

/**
 * Manager class for querying, inserting, updating, and deleting items from the system MediaStore.
 * Complies with Android 10+ Scoped Storage requirements (e.g. IS_PENDING, RELATIVE_PATH).
 */
class MediaStoreManager(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Queries images from MediaStore.Images.Media.EXTERNAL_CONTENT_URI.
     */
    fun queryImages(limit: Int = 50): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val mimeType = cursor.getString(mimeCol) ?: "image/*"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else null

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            dateAdded = dateAdded,
                            mediaType = MediaType.IMAGE,
                            resolution = resolution
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
            // Handle permission or query errors gracefully
        }
        return items
    }

    /**
     * Queries videos from MediaStore.Video.Media.EXTERNAL_CONTENT_URI.
     */
    fun queryVideos(limit: Int = 50): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val mimeType = cursor.getString(mimeCol) ?: "video/*"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val duration = if (durCol != -1) cursor.getLong(durCol) else null
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else null

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            dateAdded = dateAdded,
                            mediaType = MediaType.VIDEO,
                            durationMs = duration,
                            resolution = resolution
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
            // Silently handle query failure
        }
        return items
    }

    /**
     * Queries audio tracks from MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.
     */
    fun queryAudio(limit: Int = 50): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Audio_$id"
                    val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val duration = if (durCol != -1) cursor.getLong(durCol) else null
                    val artist = if (artistCol != -1) cursor.getString(artistCol) else null
                    val album = if (albumCol != -1) cursor.getString(albumCol) else null

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            mimeType = mimeType,
                            size = size,
                            dateAdded = dateAdded,
                            mediaType = MediaType.AUDIO,
                            durationMs = duration,
                            artist = artist,
                            album = album
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
            // Silently handle query failure
        }
        return items
    }

    /**
     * Inserts a sample bitmap image into the Pictures MediaStore collection.
     * Uses IS_PENDING = 1 while writing and 0 when finished on Android 10+.
     */
    @Throws(IOException::class)
    fun insertSampleImage(title: String = "DroidSample_${System.currentTimeMillis()}"): Uri? {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DroidSample")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = contentResolver.insert(collectionUri, values) ?: return null

        try {
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(33, 150, 243)) // Material Blue
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 32f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("DroidSample Media", 200f, 200f, paint)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, values, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            contentResolver.delete(imageUri, null, null)
            throw IOException("Failed to write sample image: ${e.message}", e)
        }
    }

    /**
     * Inserts a sample audio file into MediaStore.Audio.
     */
    @Throws(IOException::class)
    fun insertSampleAudio(title: String = "DroidSample_Chime_${System.currentTimeMillis()}"): Uri? {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, "DroidSample Synthetic")
            put(MediaStore.Audio.Media.ALBUM, "DroidSample Diagnostics")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/DroidSample")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val audioUri = contentResolver.insert(collectionUri, values) ?: return null

        try {
            contentResolver.openOutputStream(audioUri)?.use { outputStream ->
                // Generate a 1-second 440 Hz sinusoidal WAV audio file
                val sampleRate = 44100
                val durationSeconds = 1
                val numSamples = sampleRate * durationSeconds
                val data = ByteArray(numSamples * 2)

                val freq = 440.0
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                    val sample = (Math.sin(angle) * 32767).toInt().toShort()
                    data[i * 2] = (sample.toInt() and 0xFF).toByte()
                    data[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }

                val header = createWavHeader(data.size, sampleRate, 1, 16)
                outputStream.write(header)
                outputStream.write(data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(audioUri, values, null, null)
            }
            return audioUri
        } catch (e: Exception) {
            contentResolver.delete(audioUri, null, null)
            throw IOException("Failed to write sample audio: ${e.message}", e)
        }
    }

    /**
     * Deletes a media item by URI. Handles RecoverableSecurityException if on Android 10+.
     */
    fun deleteMediaItem(uri: Uri): Boolean {
        return try {
            val rowsDeleted = contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                // Return false and let caller request user confirmation using e.userAction.actionIntent
                false
            } else {
                false
            }
        }
    }

    private fun createWavHeader(dataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // PCM subchunk size
        header[20] = 1; header[21] = 0 // Format 1 = PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0 // Block align
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        return header
    }
}

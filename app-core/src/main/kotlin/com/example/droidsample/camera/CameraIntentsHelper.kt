package com.example.droidsample.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the intent creation, URI generation, and result extraction for
 * system camera captures and the Android Photo Picker.
 */
object CameraIntentsHelper {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * Creates a new timestamped file in app-specific cache/external directory for photos.
     */
    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Creates a new timestamped file for video recordings.
     */
    fun createVideoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File.createTempFile("VID_${timeStamp}_", ".mp4", storageDir)
    }

    /**
     * Returns a content:// URI for the given file using the app's FileProvider.
     */
    fun getUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}$FILE_PROVIDER_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Constructs an Intent for MediaStore.ACTION_IMAGE_CAPTURE targeting the supplied output URI.
     */
    fun createCaptureImageIntent(outputUri: Uri): Intent {
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Constructs an Intent for MediaStore.ACTION_VIDEO_CAPTURE targeting the supplied output URI.
     */
    fun createCaptureVideoIntent(outputUri: Uri, quality: Int = 1, maxDurationSecs: Int = 60): Intent {
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, quality)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxDurationSecs)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Builds a PhotoPicker request for images only.
     */
    fun buildImagePickRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    /**
     * Builds a PhotoPicker request for videos only.
     */
    fun buildVideoPickRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    }

    /**
     * Builds a PhotoPicker request for both images and videos.
     */
    fun buildVisualMediaPickRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    }
}

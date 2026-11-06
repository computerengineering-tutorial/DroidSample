package com.example.droidsample.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Media pipeline processing engine using MediaMetadataRetriever, MediaExtractor, and MediaMuxer.
 */
class MediaPipelineProcessor(private val context: Context) {

    data class TrackDetailInfo(
        val trackIndex: Int,
        val mimeType: String?,
        val isVideo: Boolean,
        val isAudio: Boolean,
        val width: Int = 0,
        val height: Int = 0,
        val frameRate: Int = 0,
        val channelCount: Int = 0,
        val sampleRate: Int = 0,
        val bitrate: Long = 0,
        val durationUs: Long = 0,
        val language: String? = null
    )

    data class MediaFileInfo(
        val title: String?,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val bitrate: Long,
        val mimeType: String?,
        val date: String?,
        val location: String?,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val fileSizeBytes: Long,
        val tracks: List<TrackDetailInfo>
    )

    /**
     * Inspects media file by extracting metadata and tracks.
     */
    suspend fun inspectMediaFile(uri: Uri): Result<MediaFileInfo> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: run {
                return@withContext Result.failure(IllegalArgumentException("Could not open file descriptor for URI: $uri"))
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

            // Inspect tracks using MediaExtractor
            val trackCount = extractor.trackCount
            val tracks = mutableListOf<TrackDetailInfo>()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME)
                val isVideoTrack = trackMime?.startsWith("video/") == true
                val isAudioTrack = trackMime?.startsWith("audio/") == true

                val trackWidth = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
                val trackHeight = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
                val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else 0
                val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                val trackBitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE).toLong() else 0L
                val trackDuration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                val language = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) else null

                tracks.add(
                    TrackDetailInfo(
                        trackIndex = i,
                        mimeType = trackMime,
                        isVideo = isVideoTrack,
                        isAudio = isAudioTrack,
                        width = trackWidth,
                        height = trackHeight,
                        frameRate = frameRate,
                        channelCount = channelCount,
                        sampleRate = sampleRate,
                        bitrate = trackBitrate,
                        durationUs = trackDuration,
                        language = language
                    )
                )
            }

            val fileSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            }.getOrDefault(0L)

            Result.success(
                MediaFileInfo(
                    title = title,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    rotation = rotation,
                    bitrate = bitrate,
                    mimeType = mimeType,
                    date = date,
                    location = location,
                    hasVideo = hasVideo,
                    hasAudio = hasAudio,
                    fileSizeBytes = fileSize,
                    tracks = tracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            retriever.release()
            extractor.release()
        }
    }

    /**
     * Extracts a video frame thumbnail at a given time in microseconds.
     */
    suspend fun extractThumbnailAtTime(
        uri: Uri,
        timeUs: Long,
        option: Int = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val bitmap = retriever.getFrameAtTime(timeUs, option)
                if (bitmap != null) {
                    Result.success(bitmap)
                } else {
                    Result.failure(IllegalStateException("No frame decoded at timestamp $timeUs us"))
                }
            } ?: Result.failure(IllegalArgumentException("Could not open file descriptor for URI: $uri"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            retriever.release()
        }
    }

    /**
     * Demuxes and remuxes a video to strip its audio track or create a video-only MP4 container.
     */
    suspend fun extractVideoTrackOnly(
        inputUri: Uri,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read $inputUri"))

            val trackCount = extractor.trackCount
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                return@withContext Result.failure(IllegalStateException("No video track found in input media"))
            }

            extractor.selectTrack(videoTrackIndex)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            muxer.start()

            val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1024 * 1024)
            } else {
                1024 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            extractor.release()
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts the audio track from an MP4 file into an M4A/AAC file container.
     */
    suspend fun extractAudioTrackOnly(
        inputUri: Uri,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read $inputUri"))

            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(IllegalStateException("No audio track found in input media"))
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                256 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            extractor.release()
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}

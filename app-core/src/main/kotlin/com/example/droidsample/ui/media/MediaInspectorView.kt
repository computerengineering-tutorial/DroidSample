package com.example.droidsample.ui.media

import android.graphics.Bitmap
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.droidsample.media.MediaPipelineProcessor
import com.example.droidsample.media.MediaPipelineProcessor.MediaFileInfo
import com.example.droidsample.media.MediaPlayerController
import com.example.droidsample.media.MediaPlayerController.PlayerState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun MediaInspectorView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val processor = remember { MediaPipelineProcessor(context) }
    val playerController = remember { MediaPlayerController(context) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var mediaInfo by remember { mutableStateOf<MediaFileInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var extractedThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var thumbnailTimeMs by remember { mutableFloatStateOf(0f) }
    var isExtractingThumbnail by remember { mutableStateOf(false) }

    var demuxResultPath by remember { mutableStateOf<String?>(null) }
    var isDemuxing by remember { mutableStateOf(false) }

    val playerState by playerController.playerState.collectAsState()
    val currentPositionMs by playerController.currentPositionMs.collectAsState()
    val totalDurationMs by playerController.durationMs.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            isLoading = true
            errorMessage = null
            extractedThumbnail = null
            demuxResultPath = null

            playerController.loadMedia(uri)

            scope.launch {
                val res = processor.inspectMediaFile(uri)
                isLoading = false
                res.onSuccess { info ->
                    mediaInfo = info
                    // Extract initial thumbnail
                    if (info.hasVideo && info.durationMs > 0) {
                        processor.extractThumbnailAtTime(uri, 0L).onSuccess {
                            extractedThumbnail = it
                        }
                    }
                }.onFailure { err ->
                    errorMessage = "Failed to inspect file: ${err.message}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Picker / Action Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Media Inspector & Demuxer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedUri?.lastPathSegment ?: "No file loaded",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                Button(onClick = { filePicker.launch(arrayOf("video/*", "audio/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Media")
                }
            }
        }

        // Loading or Error feedback
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Integrated Player & Video Preview
        if (selectedUri != null && mediaInfo?.hasVideo == true) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        playerController.attachSurface(holder)
                                    }
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        playerController.attachSurface(null)
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Play / Pause Overlay Controls
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPlaying = playerState is PlayerState.Playing
                            IconButton(onClick = {
                                if (isPlaying) playerController.pause() else playerController.play()
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            val posSec = currentPositionMs / 1000
                            val durSec = totalDurationMs / 1000
                            Text(
                                text = String.format(Locale.US, "%02d:%02d / %02d:%02d", posSec / 60, posSec % 60, durSec / 60, durSec % 60),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )

                            Slider(
                                value = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f,
                                onValueChange = { fraction ->
                                    playerController.seekTo((fraction * totalDurationMs).toInt())
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Detailed Metadata Table
        mediaInfo?.let { info ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Container Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    MetadataRow("MIME Type", info.mimeType ?: "Unknown")
                    MetadataRow("Duration", "${info.durationMs} ms (${info.durationMs / 1000}s)")
                    if (info.hasVideo) {
                        MetadataRow("Resolution", "${info.width} x ${info.height}")
                        MetadataRow("Rotation", "${info.rotation}°")
                    }
                    if (info.bitrate > 0) {
                        MetadataRow("Bitrate", "${info.bitrate / 1000} Kbps (${info.bitrate / 1_000_000f} Mbps)")
                    }
                    if (info.fileSizeBytes > 0) {
                        MetadataRow("File Size", "${info.fileSizeBytes / 1024} KB (${info.fileSizeBytes / (1024 * 1024)} MB)")
                    }
                    info.date?.let { MetadataRow("Encoded Date", it) }
                    info.location?.let { MetadataRow("Geo Location", it) }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tracks (${info.tracks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    info.tracks.forEach { track ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (track.isVideo) Icons.Default.Movie else Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Track #${track.trackIndex}: ${track.mimeType ?: "Unknown"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (track.isVideo) {
                                        Text(
                                            text = "${track.width}x${track.height} @ ${track.frameRate} fps",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (track.isAudio) {
                                        Text(
                                            text = "${track.channelCount} channels, ${track.sampleRate} Hz",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Frame Thumbnail Scrubber (MediaMetadataRetriever)
            if (info.hasVideo && info.durationMs > 0) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Frame Extraction (MediaMetadataRetriever)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        extractedThumbnail?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Extracted Frame",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Timestamp", style = MaterialTheme.typography.labelMedium)
                            Text("${thumbnailTimeMs.toLong()} ms", fontWeight = FontWeight.Bold)
                        }

                        Slider(
                            value = thumbnailTimeMs,
                            onValueChange = { thumbnailTimeMs = it },
                            onValueChangeFinished = {
                                selectedUri?.let { u ->
                                    isExtractingThumbnail = true
                                    scope.launch {
                                        processor.extractThumbnailAtTime(u, (thumbnailTimeMs * 1000).toLong())
                                            .onSuccess { extractedThumbnail = it }
                                        isExtractingThumbnail = false
                                    }
                                }
                            },
                            valueRange = 0f..info.durationMs.toFloat()
                        )

                        if (isExtractingThumbnail) {
                            Text("Extracting frame...", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // MediaMuxer Actions: Strip Audio / Demux Tracks
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pipeline Actions (MediaExtractor + MediaMuxer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Perform container remuxing, extract elementary streams or strip audio tracks without re-encoding.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (info.hasVideo && info.hasAudio) {
                            FilledTonalButton(
                                onClick = {
                                    selectedUri?.let { u ->
                                        isDemuxing = true
                                        val out = File(context.cacheDir, "VIDEO_MUTED_${System.currentTimeMillis()}.mp4")
                                        scope.launch {
                                            processor.extractVideoTrackOnly(u, out).onSuccess { f ->
                                                demuxResultPath = "Video only saved to: ${f.name} (${f.length() / 1024} KB)"
                                            }.onFailure { err ->
                                                demuxResultPath = "Error: ${err.message}"
                                            }
                                            isDemuxing = false
                                        }
                                    }
                                },
                                enabled = !isDemuxing
                            ) {
                                Icon(Icons.Default.Transform, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mute (Strip Audio)")
                            }
                        }

                        if (info.hasAudio) {
                            FilledTonalButton(
                                onClick = {
                                    selectedUri?.let { u ->
                                        isDemuxing = true
                                        val out = File(context.cacheDir, "AUDIO_EXTRACT_${System.currentTimeMillis()}.m4a")
                                        scope.launch {
                                            processor.extractAudioTrackOnly(u, out).onSuccess { f ->
                                                demuxResultPath = "Audio saved to: ${f.name} (${f.length() / 1024} KB)"
                                            }.onFailure { err ->
                                                demuxResultPath = "Error: ${err.message}"
                                            }
                                            isDemuxing = false
                                        }
                                    }
                                },
                                enabled = !isDemuxing
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Extract Audio Track")
                            }
                        }
                    }

                    if (isDemuxing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("Processing media tracks with MediaMuxer...", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    demuxResultPath?.let { res ->
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

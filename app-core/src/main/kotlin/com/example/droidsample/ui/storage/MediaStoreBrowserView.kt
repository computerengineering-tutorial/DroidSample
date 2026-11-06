package com.example.droidsample.ui.storage

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.storage.MediaItem
import com.example.droidsample.storage.MediaStoreManager
import com.example.droidsample.storage.MediaType
import com.example.droidsample.storage.StorageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaStoreBrowserView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mediaStoreManager = remember { MediaStoreManager(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Images", "Audio", "Videos")

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedItemForDetail by remember { mutableStateOf<MediaItem?>(null) }
    var isInserting by remember { mutableStateOf(false) }

    // Reload function
    val reloadCurrentTab = {
        mediaItems = when (selectedTab) {
            0 -> mediaStoreManager.queryImages(60)
            1 -> mediaStoreManager.queryAudio(60)
            2 -> mediaStoreManager.queryVideos(60)
            else -> emptyList()
        }
    }

    LaunchedEffect(selectedTab) {
        reloadCurrentTab()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                    icon = {
                        when (index) {
                            0 -> Icon(Icons.Default.Image, contentDescription = null)
                            1 -> Icon(Icons.Default.MusicNote, contentDescription = null)
                            2 -> Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        }
                    }
                )
            }
        }

        // Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${mediaItems.size} items found",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == 0) {
                    FilledTonalButton(
                        onClick = {
                            isInserting = true
                            try {
                                val uri = mediaStoreManager.insertSampleImage()
                                if (uri != null) {
                                    Toast.makeText(context, "Sample image inserted!", Toast.LENGTH_SHORT).show()
                                    reloadCurrentTab()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Insert failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isInserting = false
                            }
                        },
                        enabled = !isInserting
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Demo Photo")
                    }
                } else if (selectedTab == 1) {
                    FilledTonalButton(
                        onClick = {
                            isInserting = true
                            try {
                                val uri = mediaStoreManager.insertSampleAudio()
                                if (uri != null) {
                                    Toast.makeText(context, "Sample 440Hz chime inserted!", Toast.LENGTH_SHORT).show()
                                    reloadCurrentTab()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Insert failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isInserting = false
                            }
                        },
                        enabled = !isInserting
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Demo Audio")
                    }
                }

                IconButton(onClick = { reloadCurrentTab() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No ${tabTitles[selectedTab].lowercase()} found in MediaStore",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ensure Storage/Media permissions are granted in the Permissions tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(mediaItems) { item ->
                    MediaItemRow(
                        item = item,
                        onClick = { selectedItemForDetail = item },
                        onDelete = {
                            val success = mediaStoreManager.deleteMediaItem(item.uri)
                            if (success) {
                                Toast.makeText(context, "Deleted item", Toast.LENGTH_SHORT).show()
                                reloadCurrentTab()
                            } else {
                                Toast.makeText(context, "Delete blocked by scoped storage permissions", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // Detail Dialog
    selectedItemForDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForDetail = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${item.mediaType.name}", fontWeight = FontWeight.Bold)
                    Text("MIME: ${item.mimeType}")
                    Text("Size: ${StorageHelper.formatFileSize(item.size)}")
                    if (item.durationMs != null) {
                        Text("Duration: ${formatDuration(item.durationMs)}")
                    }
                    if (item.resolution != null) {
                        Text("Resolution: ${item.resolution}")
                    }
                    if (item.artist != null) {
                        Text("Artist: ${item.artist}")
                    }
                    if (item.album != null) {
                        Text("Album: ${item.album}")
                    }
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(item.dateAdded * 1000L))
                    Text("Date Added: $dateStr")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "URI: ${item.uri}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MediaItemRow(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (item.mediaType) {
                        MediaType.IMAGE -> Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        MediaType.AUDIO -> Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        MediaType.VIDEO -> Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subText = buildString {
                    append(StorageHelper.formatFileSize(item.size))
                    if (item.durationMs != null && item.durationMs > 0) {
                        append(" • ").append(formatDuration(item.durationMs))
                    }
                    if (item.resolution != null) {
                        append(" • ").append(item.resolution)
                    }
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

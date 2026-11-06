package com.example.droidsample.ui.storage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.droidsample.storage.AppFileProviderHelper
import com.example.droidsample.storage.DocumentMetadata
import com.example.droidsample.storage.SafHelper
import com.example.droidsample.storage.StorageHelper
import com.example.droidsample.storage.StorageLocationInfo

@Composable
fun StorageExplorerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storageHelper = remember { StorageHelper(context) }
    val safHelper = remember { SafHelper(context) }
    val fileProviderHelper = remember { AppFileProviderHelper(context) }

    var storageLocations by remember { mutableStateOf(storageHelper.getStorageLocations()) }
    var internalFiles by remember { mutableStateOf(storageHelper.listInternalFiles()) }
    var selectedDocMetadata by remember { mutableStateOf<DocumentMetadata?>(null) }
    var docContentPreview by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // SAF Open Document Launcher
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val meta = safHelper.getDocumentMetadata(uri)
            selectedDocMetadata = meta
            docContentPreview = try {
                safHelper.readTextFromUri(uri).take(500)
            } catch (_: Exception) {
                "[Binary or Non-text content]"
            }
            statusMessage = "Opened document: ${meta.displayName}"
        }
    }

    // SAF Create Document Launcher
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val content = "Created by DroidSample SAF on ${java.util.Date()}"
            val success = safHelper.writeTextToUri(uri, content)
            if (success) {
                statusMessage = "Created & wrote to document successfully!"
            } else {
                statusMessage = "Failed writing to document."
            }
        }
    }

    // Android 13+ Photo Picker Launcher
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val meta = safHelper.getDocumentMetadata(uri)
            selectedDocMetadata = meta
            docContentPreview = "Photo Picker Selected: ${meta.displayName} (${meta.mimeType})"
            statusMessage = "Picked media: ${meta.displayName}"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Alert / Feedback
        statusMessage?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Storage Locations Summary Card
        item {
            Text(
                text = "Application Storage Directory Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(storageLocations) { location: StorageLocationInfo ->
            StorageLocationCard(location = location)
        }

        // Cache Management Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cache Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clear temporary files inside context.cacheDir and context.externalCacheDir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val freed = storageHelper.clearCache()
                            storageLocations = storageHelper.getStorageLocations()
                            internalFiles = storageHelper.listInternalFiles()
                            statusMessage = "Freed ${StorageHelper.formatFileSize(freed)} from cache."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clean Cache")
                    }
                }
            }
        }

        // Internal Files I/O Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App-Internal Storage Operations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val ts = System.currentTimeMillis()
                                storageHelper.writeInternalSampleFile(
                                    "sample_$ts.txt",
                                    "Sample diagnostic log written at $ts"
                                )
                                internalFiles = storageHelper.listInternalFiles()
                                storageLocations = storageHelper.getStorageLocations()
                                statusMessage = "Created sample_$ts.txt in filesDir"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create File")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val (file, uri) = fileProviderHelper.createShareableSampleFile()
                                    val shareIntent = fileProviderHelper.createShareIntent(uri)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Test File"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share via URI")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Files in filesDir: ${internalFiles.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    internalFiles.take(5).forEach { file ->
                        Text(
                            text = "• ${file.name} (${StorageHelper.formatFileSize(file.length())})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Storage Access Framework (SAF) & Photo Picker Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage Access Framework & Photo Picker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Interact with system file provider without requesting broad storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open Document")
                        }

                        Button(
                            onClick = { createDocLauncher.launch("DroidSample_Note.txt") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create Document")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            pickMediaLauncher.launch(SafHelper.imageAndVideoRequest())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Visual Media (Photo Picker)")
                    }

                    selectedDocMetadata?.let { meta ->
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selected Document:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Name: ${meta.displayName}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Size: ${StorageHelper.formatFileSize(meta.size)}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "MIME: ${meta.mimeType ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "URI: ${meta.uri}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                        docContentPreview?.let { preview ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preview,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageLocationCard(location: StorageLocationInfo) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (location.isExternal) "External" else "Internal") }
                )
            }
            Text(
                text = location.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Files: ${location.fileCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Used: ${StorageHelper.formatFileSize(location.usedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            if (location.totalBytes > 0) {
                val progress = (location.usedBytes.toFloat() / location.totalBytes.toFloat()).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

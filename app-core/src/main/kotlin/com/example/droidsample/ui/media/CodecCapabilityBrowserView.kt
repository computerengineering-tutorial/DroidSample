package com.example.droidsample.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.media.MediaCodecInspector
import com.example.droidsample.media.MediaCodecInspector.CodecOverviewStats
import com.example.droidsample.media.MediaCodecInspector.CodecSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CodecFilter {
    ALL, ENCODERS, DECODERS, HARDWARE, SOFTWARE, VIDEO, AUDIO
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodecCapabilityBrowserView(
    modifier: Modifier = Modifier
) {
    val inspector = remember { MediaCodecInspector() }
    var allCodecs by remember { mutableStateOf<List<CodecSummary>>(emptyList()) }
    var stats by remember { mutableStateOf<CodecOverviewStats?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CodecFilter.ALL) }
    var expandedCodecName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val codecs = inspector.getSupportedCodecs()
            val computedStats = inspector.computeOverviewStats(codecs)
            allCodecs = codecs
            stats = computedStats
        }
    }

    val filteredCodecs = remember(allCodecs, searchQuery, activeFilter) {
        allCodecs.filter { codec ->
            val matchesSearch = searchQuery.isBlank() ||
                codec.name.contains(searchQuery, ignoreCase = true) ||
                codec.canonicalName.contains(searchQuery, ignoreCase = true) ||
                codec.supportedTypes.any { it.mimeType.contains(searchQuery, ignoreCase = true) }

            val matchesFilter = when (activeFilter) {
                CodecFilter.ALL -> true
                CodecFilter.ENCODERS -> codec.isEncoder
                CodecFilter.DECODERS -> !codec.isEncoder
                CodecFilter.HARDWARE -> codec.isHardwareAccelerated
                CodecFilter.SOFTWARE -> codec.isSoftwareOnly
                CodecFilter.VIDEO -> codec.supportedTypes.any { it.isVideo }
                CodecFilter.AUDIO -> codec.supportedTypes.any { it.isAudio }
            }

            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Statistics overview card
        stats?.let { s ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Device Codec Engine",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(label = "Total", value = s.totalCodecs.toString())
                        StatItem(label = "HW Encoders", value = s.totalEncoders.toString())
                        StatItem(label = "HW Decoders", value = s.totalDecoders.toString())
                        StatItem(label = "HW Accel", value = s.hardwareAcceleratedCount.toString())
                        StatItem(label = "SW Only", value = s.softwareOnlyCount.toString())
                    }
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search codec or MIME (e.g., avc, hevc, vp9)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Filter chips row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CodecFilter.values()) { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Text(
            text = "Showing ${filteredCodecs.size} codecs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Codec list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredCodecs, key = { it.name }) { codec ->
                val isExpanded = expandedCodecName == codec.name

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedCodecName = if (isExpanded) null else codec.name
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpanded) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = codec.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                if (codec.canonicalName != codec.name) {
                                    Text(
                                        text = "Alias of: ${codec.canonicalName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // HW / SW badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (codec.isHardwareAccelerated) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (codec.isHardwareAccelerated) "HW" else "SW",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Enc / Dec badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (codec.isEncoder) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Text(
                                        text = if (codec.isEncoder) "ENC" else "DEC",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            }
                        }

                        // Mime type tags
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            codec.supportedTypes.forEach { type ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = type.mimeType,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // Expandable technical capabilities view
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider()

                                codec.supportedTypes.forEach { type ->
                                    Text(
                                        text = "MIME: ${type.mimeType}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Text(
                                        text = "Max Supported Instances: ${type.maxInstances}",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    // Video technical capabilities
                                    type.videoCapabilities?.let { vc ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "Video Capabilities",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "• Width Range: [${vc.widthRange.lower} - ${vc.widthRange.upper}]",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "• Height Range: [${vc.heightRange.lower} - ${vc.heightRange.upper}]",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "• Bitrate Range: [${vc.bitrateRange.lower / 1000} - ${vc.bitrateRange.upper / 1000}] Kbps",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "• Frame Rate Range: [${vc.frameRateRange.lower.toInt()} - ${vc.frameRateRange.upper.toInt()}] FPS",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "• 1080p Support: ${if (vc.isSizeSupported1080p) "YES" else "NO"} | 4K Support: ${if (vc.isSizeSupported4K) "YES" else "NO"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Color formats
                                    if (type.colorFormats.isNotEmpty()) {
                                        Column {
                                            Text(
                                                text = "Supported Color Formats (${type.colorFormats.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                type.colorFormats.forEach { cf ->
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                    ) {
                                                        Text(
                                                            text = cf.formatName,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Profile & Levels
                                    if (type.profileLevels.isNotEmpty()) {
                                        Column {
                                            Text(
                                                text = "Profiles & Levels (${type.profileLevels.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                type.profileLevels.take(10).forEach { pl ->
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "${pl.profileName} [${pl.levelName}]",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                                if (type.profileLevels.size > 10) {
                                                    Text(
                                                        text = "+${type.profileLevels.size - 10} more",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

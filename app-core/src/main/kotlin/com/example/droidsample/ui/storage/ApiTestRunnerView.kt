package com.example.droidsample.ui.storage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.droidsample.storage.FeatureTestResult
import com.example.droidsample.storage.TestStatus
import com.example.droidsample.storage.TestSuiteReport
import com.example.droidsample.storage.TestSuiteRunner

@Composable
fun ApiTestRunnerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runner = remember { TestSuiteRunner(context) }
    val fileProviderHelper = remember { AppFileProviderHelper(context) }

    var report by remember { mutableStateOf<TestSuiteReport?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var exportDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(Title, Text)

    fun runTests() {
        isRunning = true
        report = runner.runAllDiagnostics()
        isRunning = false
    }

    LaunchedEffect(Unit) {
        if (report == null) {
            runTests()
        }
    }

    val categories = remember(report) {
        val set = mutableSetOf("All")
        report?.results?.forEach { set.add(it.category) }
        set.toList()
    }

    val filteredResults = remember(report, selectedCategory) {
        val list = report?.results ?: emptyList()
        if (selectedCategory == "All") list else list.filter { it.category == selectedCategory }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Runner Header Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Master API Diagnostic Suite",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = report?.deviceModel ?: "Android Diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { runTests() },
                        enabled = !isRunning
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Run All")
                    }
                }

                report?.let { rep ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPill(label = "Pass", count = rep.passCount, color = MaterialTheme.colorScheme.primaryContainer)
                        MetricPill(label = "Fail", count = rep.failCount, color = MaterialTheme.colorScheme.errorContainer)
                        MetricPill(label = "Blocked", count = rep.blockedCount, color = MaterialTheme.colorScheme.tertiaryContainer)
                        MetricPill(label = "N/A", count = rep.unsupportedCount, color = MaterialTheme.colorScheme.surfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (rep.passRatePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pass Rate: ${String.format(java.util.Locale.US, "%.1f", rep.passRatePercent)}% (${rep.passCount}/${rep.totalTests})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val md = TestSuiteRunner.exportToMarkdown(rep)
                                exportDialogContent = Pair("Markdown Capability Report", md)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export MD")
                        }

                        OutlinedButton(
                            onClick = {
                                val json = TestSuiteRunner.exportToJson(rep)
                                exportDialogContent = Pair("JSON Diagnostic Report", json)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export JSON")
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostic Tests List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredResults) { test ->
                TestResultCard(test = test)
            }
        }
    }

    // Export Dialog
    exportDialogContent?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { exportDialogContent = null },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.heightIn(max = 350.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    text = content,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DroidSample Report", content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try {
                            val fileName = if (title.contains("JSON")) "diagnostic_report.json" else "diagnostic_report.md"
                            val (file, uri) = fileProviderHelper.createShareableSampleFile(fileName, content)
                            val shareIntent = fileProviderHelper.createShareIntent(uri, "text/plain", title)
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Report"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Share")
                }
            }
        )
    }
}

@Composable
private fun MetricPill(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", style = MaterialTheme.typography.labelSmall)
            Text(text = count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TestResultCard(test: FeatureTestResult) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "[${test.featureId}] ${test.apiName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${test.category} • Min SDK ${test.minSdk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(test.status.name) },
                    colors = when (test.status) {
                        TestStatus.PASS -> AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        TestStatus.FAIL -> AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        TestStatus.BLOCKED -> AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        TestStatus.UNSUPPORTED -> AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Output Data: ${test.outputData}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    if (test.permissionRequired != null) {
                        Text(
                            text = "Permission: ${test.permissionRequired}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (test.roleRequired != null) {
                        Text(
                            text = "Role: ${test.roleRequired}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (test.errorMessage != null) {
                        Text(
                            text = "Error: ${test.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

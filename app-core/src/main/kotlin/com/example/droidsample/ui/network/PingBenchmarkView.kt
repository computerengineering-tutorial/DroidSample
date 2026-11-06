package com.example.droidsample.ui.network

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.network.DnsBenchmarkResult
import com.example.droidsample.network.HttpProbeResult
import com.example.droidsample.network.LatencySeriesSummary
import com.example.droidsample.network.NetworkDiagnosticsHelper
import com.example.droidsample.network.SocketPingResult
import kotlinx.coroutines.launch

/**
 * Diagnostics and latency benchmarking view (DNS resolution, TCP socket reachability, and HTTP probe).
 */
@Composable
fun PingBenchmarkView(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var targetHost by remember { mutableStateOf("dns.google") }
    var targetPort by remember { mutableStateOf("443") }

    var isRunningDns by remember { mutableStateOf(false) }
    var dnsResult by remember { mutableStateOf<DnsBenchmarkResult?>(null) }

    var isRunningTcp by remember { mutableStateOf(false) }
    var tcpResult by remember { mutableStateOf<SocketPingResult?>(null) }
    var seriesResult by remember { mutableStateOf<LatencySeriesSummary?>(null) }

    var isRunningHttp by remember { mutableStateOf(false) }
    var httpResult by remember { mutableStateOf<HttpProbeResult?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Target Host Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Benchmark Target Endpoint",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("Hostname / IP") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = { targetHost = "dns.google"; targetPort = "443" },
                        label = { Text("Google DNS") }
                    )
                    SuggestionChip(
                        onClick = { targetHost = "one.one.one.one"; targetPort = "443" },
                        label = { Text("Cloudflare") }
                    )
                    SuggestionChip(
                        onClick = { targetHost = "connectivitycheck.gstatic.com"; targetPort = "80" },
                        label = { Text("Captive Portal") }
                    )
                }
            }
        }

        // DNS Resolution Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DNS Resolution Benchmark",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRunningDns = true
                                dnsResult = NetworkDiagnosticsHelper.benchmarkDns(targetHost)
                                isRunningDns = false
                            }
                        },
                        enabled = !isRunningDns && targetHost.isNotBlank()
                    ) {
                        if (isRunningDns) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Resolve")
                        }
                    }
                }

                dnsResult?.let { res ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (res.isSuccessful) "Resolved in ${res.durationMs} ms" else "Resolution Failed",
                            fontWeight = FontWeight.Bold,
                            color = if (res.isSuccessful) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${res.resolvedIps.size} record(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (res.resolvedIps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        res.resolvedIps.forEach { ip ->
                            Text(
                                text = "• $ip",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (res.errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Error: ${res.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // TCP Socket Benchmark & Latency Series Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TCP Ping Latency Series",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRunningTcp = true
                                val port = targetPort.toIntOrNull() ?: 443
                                tcpResult = NetworkDiagnosticsHelper.benchmarkTcpSocket(targetHost, port)
                                seriesResult = NetworkDiagnosticsHelper.runPingSeries(targetHost, port, count = 5)
                                isRunningTcp = false
                            }
                        },
                        enabled = !isRunningTcp && targetHost.isNotBlank()
                    ) {
                        if (isRunningTcp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Ping (x5)")
                        }
                    }
                }

                seriesResult?.let { series ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(label = "Min", value = "${series.minMs} ms")
                        StatItem(label = "Avg", value = String.format(java.util.Locale.US, "%.1f ms", series.avgMs))
                        StatItem(label = "Max", value = "${series.maxMs} ms")
                        StatItem(label = "Loss", value = "${series.lossPercent.toInt()}%")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Latency sample visual bars
                    Text(
                        text = "RTT Samples: ${series.samples.map { if (it >= 0) "${it}ms" else "timeout" }.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // HTTP Connectivity & Captive Portal Probe Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Captive Portal HTTP Probe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRunningHttp = true
                                httpResult = NetworkDiagnosticsHelper.probeHttpConnectivity()
                                isRunningHttp = false
                            }
                        },
                        enabled = !isRunningHttp
                    ) {
                        if (isRunningHttp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Probe")
                        }
                    }
                }

                httpResult?.let { probe ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (probe.isSuccess && !probe.isCaptivePortal) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (probe.isSuccess && !probe.isCaptivePortal) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = probe.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: HTTP ${probe.statusCode} • Round-Trip: ${probe.latencyMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

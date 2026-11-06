package com.example.droidsample.ui.network

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.network.NetworkState
import com.example.droidsample.network.TransportType

/**
 * Overview dashboard displaying active connection status, transport badges, IP routing, and bandwidth.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkDashboardView(
    state: NetworkState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Hero Card
        StatusHeroCard(state = state)

        // Capabilities & Flags Card
        CapabilitiesCard(state = state)

        // IP & Routing Details Card
        IpRoutingCard(state = state)

        // Bandwidth & Interface Card
        BandwidthCard(state = state)
    }
}

@Composable
private fun StatusHeroCard(state: NetworkState) {
    val isConnected = state.isConnected
    val isValidated = state.capabilities.isValidated

    val backgroundColor = when {
        !isConnected -> MaterialTheme.colorScheme.errorContainer
        isValidated -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val contentColor = when {
        !isConnected -> MaterialTheme.colorScheme.onErrorContainer
        isValidated -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getTransportIcon(state.activeTransport, isConnected),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = contentColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (isConnected) {
                        getTransportTitle(state.activeTransport)
                    } else {
                        "No Internet Connection"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isValidated) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            !isConnected -> "Disconnected"
                            isValidated -> "Validated Internet Reachability"
                            else -> "Connected (Internet Not Validated)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilitiesCard(state: NetworkState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Network Capabilities & Transport",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metered status chip
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (state.capabilities.isMetered) "Metered Data" else "Unmetered (Free)")
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (state.capabilities.isMetered) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    )
                )

                // VPN status chip
                if (state.capabilities.isVpn) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("VPN Active") }
                    )
                }

                // Transport chips
                state.capabilities.transports.forEach { transport ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(transport.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IpRoutingCard(state: NetworkState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IP & Routing Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = "Interface",
                value = state.linkProperties.interfaceName ?: "None"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerDefaults.color.copy(alpha = 0.5f))

            InfoRow(
                label = "IPv4 Address",
                value = state.linkProperties.ipv4Addresses.firstOrNull() ?: "Unavailable"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerDefaults.color.copy(alpha = 0.5f))

            InfoRow(
                label = "IPv6 Address",
                value = state.linkProperties.ipv6Addresses.firstOrNull() ?: "Unavailable",
                isMonospace = true
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerDefaults.color.copy(alpha = 0.5f))

            InfoRow(
                label = "Default Gateway",
                value = state.linkProperties.gateway ?: "None / Automatic"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DividerDefaults.color.copy(alpha = 0.5f))

            InfoRow(
                label = "DNS Servers",
                value = if (state.linkProperties.dnsServers.isEmpty()) {
                    "None"
                } else {
                    state.linkProperties.dnsServers.joinToString(", ")
                }
            )
        }
    }
}

@Composable
private fun BandwidthCard(state: NetworkState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Link Bandwidth & MTU",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BandwidthStat(
                    label = "Downstream Link",
                    value = formatBandwidth(state.capabilities.downstreamBandwidthKbps)
                )
                BandwidthStat(
                    label = "Upstream Link",
                    value = formatBandwidth(state.capabilities.upstreamBandwidthKbps)
                )
                BandwidthStat(
                    label = "MTU",
                    value = if (state.linkProperties.mtu > 0) "${state.linkProperties.mtu} B" else "Auto"
                )
            }
        }
    }
}

@Composable
private fun BandwidthStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun getTransportIcon(transport: TransportType, isConnected: Boolean): ImageVector {
    if (!isConnected) return Icons.Default.Cancel
    return when (transport) {
        TransportType.WIFI -> Icons.Default.Wifi
        TransportType.CELLULAR -> Icons.Default.SignalCellularAlt
        TransportType.ETHERNET -> Icons.Default.Lan
        TransportType.VPN -> Icons.Default.VpnKey
        else -> Icons.Default.NetworkCheck
    }
}

private fun getTransportTitle(transport: TransportType): String {
    return when (transport) {
        TransportType.WIFI -> "Connected via Wi-Fi"
        TransportType.CELLULAR -> "Connected via Cellular (Mobile)"
        TransportType.ETHERNET -> "Connected via Ethernet"
        TransportType.VPN -> "Secure VPN Tunnel Active"
        TransportType.BLUETOOTH -> "Connected via Bluetooth Tethering"
        TransportType.USB -> "Connected via USB Tethering"
        else -> "Network Connected"
    }
}

private fun formatBandwidth(kbps: Int): String {
    return when {
        kbps <= 0 -> "Unknown"
        kbps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Gbps", kbps / 1_000_000.0)
        kbps >= 1_000 -> String.format(java.util.Locale.US, "%.1f Mbps", kbps / 1_000.0)
        else -> "$kbps Kbps"
    }
}

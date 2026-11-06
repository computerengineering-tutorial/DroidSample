package com.example.droidsample.ui.telephony

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.droidsample.telephony.FormattedNumberResult
import com.example.droidsample.telephony.PhoneNumberHelper

@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneNumberView(
    deviceLine1Number: String?,
    detectedCountryIso: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    var inputNumber by remember { mutableStateOf("6502530000") }
    var selectedIso by remember { mutableStateOf(detectedCountryIso) }

    // Comparison tool states
    var compareNum1 by remember { mutableStateOf("+1 650 253 0000") }
    var compareNum2 by remember { mutableStateOf("650-253-0000") }

    val formattedResult = remember(inputNumber, selectedIso) {
        PhoneNumberHelper.formatComprehensive(inputNumber, selectedIso)
    }

    val isLooseMatch = remember(compareNum1, compareNum2) {
        PhoneNumberHelper.compareNumbers(compareNum1, compareNum2, useStrict = false)
    }
    val isStrictMatch = remember(compareNum1, compareNum2) {
        PhoneNumberHelper.compareNumbers(compareNum1, compareNum2, useStrict = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Line 1 Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Device Line",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Line 1 Phone Number",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = deviceLine1Number ?: "Not Provisioned / Restricted",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (deviceLine1Number != null) {
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(deviceLine1Number)) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Number",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Formatter & Normalizer Tool Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Interactive Phone Formatter & E.164",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputNumber,
                            onValueChange = { inputNumber = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = selectedIso,
                            onValueChange = { selectedIso = it.uppercase() },
                            label = { Text("Country ISO") },
                            modifier = Modifier.weight(0.3f),
                            singleLine = true
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val callingCode = formattedResult.countryCallingCode
                        if (callingCode != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Calling Code: $callingCode",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (formattedResult.isValidGlobal) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (formattedResult.isValidGlobal) "Valid Global Number" else "Unverified Structure",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (formattedResult.isValidGlobal) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    // Output formats
                    NumberFormatRow(
                        label = "ITU-T E.164 Format",
                        value = formattedResult.e164 ?: "Unable to parse into E.164",
                        isHighlight = true
                    )

                    NumberFormatRow(
                        label = "International Format",
                        value = formattedResult.international.ifEmpty { "N/A" }
                    )

                    NumberFormatRow(
                        label = "National Format",
                        value = formattedResult.national.ifEmpty { "N/A" }
                    )

                    NumberFormatRow(
                        label = "Normalized Digits",
                        value = formattedResult.normalized.ifEmpty { "N/A" }
                    )
                }
            }
        }

        // Phone Number Comparison Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Compare",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone Number Equality Matcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = compareNum1,
                        onValueChange = { compareNum1 = it },
                        label = { Text("Number 1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = compareNum2,
                        onValueChange = { compareNum2 = it },
                        label = { Text("Number 2") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Loose Comparison (PhoneNumberUtils.compare)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        MatchBadge(isMatch = isLooseMatch)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Strict Comparison (compareStrictly)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        MatchBadge(isMatch = isStrictMatch)
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun NumberFormatRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = { clipboardManager.setText(AnnotatedString(value)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun MatchBadge(isMatch: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isMatch) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMatch) Icons.Default.Check else Icons.Default.Clear,
                contentDescription = if (isMatch) "Match" else "Mismatch",
                modifier = Modifier.size(14.dp),
                tint = if (isMatch) Color(0xFF2E7D32) else Color(0xFFD32F2F)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isMatch) "Match" else "Different",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMatch) Color(0xFF2E7D32) else Color(0xFFD32F2F)
            )
        }
    }
}

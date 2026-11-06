package com.example.droidsample.ui.sms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.droidsample.sms.OtpDetectionResult
import com.example.droidsample.sms.OtpParser
import com.example.droidsample.sms.SmsRetrieverHelper
import kotlinx.coroutines.flow.collectLatest

@Suppress("DEPRECATION")
@Composable
fun SmsVerificationView(
    retrieverHelper: SmsRetrieverHelper,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var otpDigits by remember { mutableStateOf(List(6) { "" }) }
    var detectedEvent by remember { mutableStateOf<OtpDetectionResult?>(null) }
    var sandboxMessage by remember {
        mutableStateOf("<#> 741952 is your DroidSample verification code. Valid for 10 minutes. FA+9qCX9VSu")
    }

    val isRetrieverActive by retrieverHelper.isRetrieverActive.collectAsState()
    val isConsentActive by retrieverHelper.isConsentActive.collectAsState()

    // Query 11-char app hashes
    val appSignatures = remember { SmsRetrieverHelper.getAppSignatures(context) }
    val displayHash = appSignatures.firstOrNull() ?: "FA+9qCX9VSu"

    // Listen to OTP detections from Retriever or Simulator
    LaunchedEffect(retrieverHelper) {
        retrieverHelper.otpEvents.collectLatest { event ->
            detectedEvent = event
            val code = event.otpCode
            val newDigits = MutableList(6) { "" }
            for (i in 0 until 6.coerceAtMost(code.length)) {
                newDigits[i] = code[i].toString()
            }
            otpDigits = newDigits
        }
    }

    // Parsed results in sandbox
    val sandboxOtp = remember(sandboxMessage) { OtpParser.extractOtp(sandboxMessage) }
    val sandboxSender = remember(sandboxMessage) { OtpParser.extractSenderTag(sandboxMessage) }
    val sandboxHash = remember(sandboxMessage) { OtpParser.extractAppHash(sandboxMessage) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SMS Retriever & Zero-Permission OTP Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automated OTP detection without requiring READ_SMS or RECEIVE_SMS permissions. Uses Google Play Services SMS Retriever and SMS User Consent APIs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // App Hash Signature Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Signature Hash (11 Chars):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(displayHash))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Hash", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayHash,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Include this 11-char token at the end of SMS messages sent from your server to enable automated zero-interaction auto-fill.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Interactive 6-digit OTP Box
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verification Code",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        OtpDigitBox(digit = otpDigits.getOrElse(i) { "" })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            retrieverHelper.simulateVerificationSms(
                                otpCode = (100000..999999).random().toString(),
                                serviceName = "DroidAuth",
                                hash = displayHash
                            )
                        }
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Auto-Fill")
                    }

                    OutlinedButton(onClick = { otpDigits = List(6) { "" } }) {
                        Text("Clear")
                    }
                }
            }
        }

        // Detected event summary banner
        detectedEvent?.let { event ->
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "OTP Detected: ${event.otpCode}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Source: ${event.source}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        event.senderTag?.let { sender ->
                            Text(
                                text = "Sender: $sender",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = event.fullMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Retriever / Consent Controls
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "API Listener Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SMS Retriever Client", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Requires 11-char hash, 0 user clicks", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                if (isRetrieverActive) retrieverHelper.stopRetriever()
                                else retrieverHelper.startSmsRetriever()
                            },
                            colors = if (isRetrieverActive)
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            else
                                ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isRetrieverActive) "Active (Stop)" else "Start")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SMS User Consent API", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("No hash needed, prompts 1-tap dialog", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                if (isConsentActive) retrieverHelper.stopRetriever()
                                else retrieverHelper.startUserConsent()
                            },
                            colors = if (isConsentActive)
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            else
                                ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isConsentActive) "Active (Stop)" else "Start")
                        }
                    }
                }
            }
        }

        // Regex Parser Sandbox
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Regex Token Extraction Sandbox",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste any incoming bank, telecom, or service message below to test token parsing:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sandboxMessage,
                        onValueChange = { sandboxMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Extracted OTP:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    sandboxOtp ?: "None found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sandboxOtp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Detected Sender:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    sandboxSender ?: "None",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Extracted App Hash:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    sandboxHash ?: "None",
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
fun OtpDigitBox(digit: String) {
    val borderColor by animateColorAsState(
        targetValue = if (digit.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 250),
        label = "otpBorder"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .size(46.dp)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

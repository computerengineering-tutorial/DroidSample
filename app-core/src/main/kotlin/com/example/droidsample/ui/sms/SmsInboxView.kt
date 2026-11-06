package com.example.droidsample.ui.sms

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.droidsample.sms.SmsConversation
import com.example.droidsample.sms.SmsDatabaseRepository
import com.example.droidsample.sms.SmsMessageItem
import com.example.droidsample.sms.SmsSenderHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxView(
    repository: SmsDatabaseRepository,
    senderHelper: SmsSenderHelper,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var useMockData by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf<List<SmsConversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedThread by remember { mutableStateOf<SmsConversation?>(null) }
    var threadMessages by remember { mutableStateOf<List<SmsMessageItem>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }

    // Dialogs / actions state
    var messageToDelete by remember { mutableStateOf<SmsMessageItem?>(null) }
    var threadToDelete by remember { mutableStateOf<SmsConversation?>(null) }

    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val refreshConversations: () -> Unit = {
        coroutineScope.launch {
            if (useMockData || !hasReadPermission) {
                conversations = repository.getMockConversations()
            } else {
                loadConversations(repository, false) { list, err ->
                    conversations = list
                    errorMessage = err
                }
            }
        }
    }

    val refreshThread: (Long) -> Unit = { threadId ->
        coroutineScope.launch {
            threadMessages = if (useMockData || !hasReadPermission) {
                repository.getMockMessagesForThread(threadId)
            } else {
                repository.getMessagesForThread(threadId).getOrDefault(emptyList())
            }
            refreshConversations()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPermission = granted
        if (granted) {
            refreshConversations()
        }
    }

    LaunchedEffect(hasReadPermission, useMockData) {
        isLoading = true
        if (useMockData || !hasReadPermission) {
            conversations = repository.getMockConversations()
            isLoading = false
        } else {
            loadConversations(repository, false) { list, err ->
                conversations = list
                errorMessage = err
                isLoading = false
            }
        }
    }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.address.contains(searchQuery, ignoreCase = true) ||
                        it.snippet.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Source Toggle Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search threads by address or message...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Data source mode indicator and permission banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (useMockData || !hasReadPermission) "Data Source: Mock Simulator" else "Data Source: Telephony Provider",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (useMockData || !hasReadPermission) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { refreshConversations() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                Text(
                    text = "Mock Mode",
                    style = MaterialTheme.typography.labelSmall
                )
                Switch(
                    checked = useMockData,
                    onCheckedChange = { useMockData = it }
                )
            }
        }

        if (!hasReadPermission && !useMockData) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "READ_SMS Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "To read on-device SMS database, please grant permission or toggle Mock Mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Grant")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Conversations List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredConversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No conversation threads found.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredConversations) { conv ->
                    ConversationItemCard(
                        conversation = conv,
                        onClick = {
                            selectedThread = conv
                            refreshThread(conv.threadId)
                        },
                        onMarkReadToggle = {
                            coroutineScope.launch {
                                val newReadStatus = conv.unreadCount > 0
                                repository.markThreadRead(conv.threadId, newReadStatus).onSuccess {
                                    Toast.makeText(context, if (newReadStatus) "Marked as read" else "Marked as unread", Toast.LENGTH_SHORT).show()
                                    refreshConversations()
                                }.onFailure {
                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteClick = {
                            threadToDelete = conv
                        }
                    )
                }
            }
        }
    }

    // Thread Messages Detail Modal / Sheet
    selectedThread?.let { thread ->
        AlertDialog(
            onDismissRequest = { selectedThread = null },
            modifier = Modifier.fillMaxWidth(0.95f),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = thread.address,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Thread #${thread.threadId} • ${threadMessages.size} messages",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val hasUnread = threadMessages.any { !it.read }
                                repository.markThreadRead(thread.threadId, hasUnread).onSuccess {
                                    Toast.makeText(context, if (hasUnread) "Thread marked read" else "Thread marked unread", Toast.LENGTH_SHORT).show()
                                    refreshThread(thread.threadId)
                                }.onFailure {
                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            val hasUnread = threadMessages.any { !it.read }
                            Icon(
                                if (hasUnread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
                                contentDescription = "Toggle Read Status"
                            )
                        }
                        IconButton(onClick = { threadToDelete = thread }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Thread", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { selectedThread = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(threadMessages) { msg ->
                            val isSent = msg.type == 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isSent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.widthIn(max = 290.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = msg.body,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = msg.typeLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                if (!msg.read && !isSent) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "• Unread",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatTime(msg.date),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        // Action Row for single message (Mark Read/Unread, Delete)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.markAsRead(msg.id, !msg.read).onSuccess {
                                                            refreshThread(thread.threadId)
                                                        }.onFailure {
                                                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (msg.read) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                                    contentDescription = if (msg.read) "Mark Unread" else "Mark Read",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { messageToDelete = msg },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Message",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick reply composer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Quick reply...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    senderHelper.sendSms(
                                        destinationAddress = thread.address,
                                        messageText = replyText
                                    )
                                    replyText = ""
                                    // Refresh thread after slight delay
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1000)
                                        refreshThread(thread.threadId)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val hasUnread = threadMessages.any { !it.read }
                            repository.markThreadRead(thread.threadId, hasUnread).onSuccess {
                                Toast.makeText(context, if (hasUnread) "Marked as read" else "Marked as unread", Toast.LENGTH_SHORT).show()
                                refreshThread(thread.threadId)
                            }.onFailure {
                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    val hasUnread = threadMessages.any { !it.read }
                    Text(if (hasUnread) "Mark All Read" else "Mark All Unread")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedThread = null }) {
                    Text("Done")
                }
            }
        )
    }

    // Single message delete confirmation dialog
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this SMS from the device database? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteMessage(msg.id).onSuccess {
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                                selectedThread?.let { refreshThread(it.threadId) }
                            }.onFailure {
                                Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            messageToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Thread delete confirmation dialog
    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { Text("Delete Entire Conversation") },
            text = { Text("Are you sure you want to delete all messages with ${thread.address}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteThread(thread.threadId).onSuccess {
                                Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
                                if (selectedThread?.threadId == thread.threadId) {
                                    selectedThread = null
                                }
                                refreshConversations()
                            }.onFailure {
                                Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                            threadToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun loadConversations(
    repository: SmsDatabaseRepository,
    forceMock: Boolean,
    onResult: (List<SmsConversation>, String?) -> Unit
) {
    if (forceMock) {
        onResult(repository.getMockConversations(), null)
        return
    }

    val res = repository.getConversations()
    if (res.isSuccess) {
        val list = res.getOrDefault(emptyList())
        if (list.isEmpty()) {
            onResult(repository.getMockConversations(), null)
        } else {
            onResult(list, null)
        }
    } else {
        onResult(repository.getMockConversations(), res.exceptionOrNull()?.message)
    }
}

@Composable
fun ConversationItemCard(
    conversation: SmsConversation,
    onClick: () -> Unit,
    onMarkReadToggle: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (conversation.unreadCount > 0)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initial
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.address.firstOrNull()?.uppercase() ?: "#",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = conversation.address,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Text(
                        text = formatTime(conversation.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (conversation.unreadCount > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("${conversation.unreadCount}")
                }
            }

            // Quick actions on the card: Mark read/unread & Delete
            IconButton(
                onClick = onMarkReadToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (conversation.unreadCount > 0) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
                    contentDescription = "Toggle Read Status",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Thread",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val date = Date(millis)
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { time = date }

    return if (now.get(Calendar.DATE) == msgTime.get(Calendar.DATE)) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}

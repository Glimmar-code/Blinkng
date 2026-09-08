package com.blinkng.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopConversation
import com.blinkng.desktop.data.DesktopMessage
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(state: DesktopAppState) {
    var conversations by remember { mutableStateOf<List<DesktopConversation>>(emptyList()) }
    var selected by remember { mutableStateOf<DesktopConversation?>(null) }
    var messages by remember { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val myUserId = state.session?.userId.orEmpty()

    suspend fun loadConversations() {
        loading = true
        runCatching { state.client.fetchConversations() }
            .onSuccess {
                conversations = it
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    suspend fun loadMessages(conversation: DesktopConversation?) {
        val target = conversation ?: run {
            messages = emptyList()
            return
        }
        runCatching { state.client.fetchMessages(target.id) }
            .onSuccess { messages = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(Unit) { loadConversations() }
    LaunchedEffect(selected?.id) { loadMessages(selected) }

    val active = selected
    if (active == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages", fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { loadConversations() } }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
            HorizontalDivider()
            error?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (loading) {
                    item {
                        Text(
                            "Loading conversations…",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!loading && conversations.isEmpty()) {
                    item {
                        Text(
                            "No conversations yet.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(conversations, key = { it.id }) { conversation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            error = null
                            selected = conversation
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(conversation.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (conversation.isGroup) "Group conversation" else "Direct conversation",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    selected = null
                    messages = emptyList()
                    error = null
                },
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Messages")
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (active.isGroup) "Group chat" else "Blink conversation",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { error = "Desktop voice-call media adapter is not active yet." }) {
                Icon(Icons.Rounded.Call, contentDescription = "Voice call")
            }
            IconButton(onClick = { error = "Desktop video-call media adapter is not active yet." }) {
                Icon(Icons.Rounded.Videocam, contentDescription = "Video call")
            }
        }
        HorizontalDivider()
        error?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == myUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp).width(360.dp)) {
                            Text(message.content)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    when {
                                        message.readAt != null -> "Read"
                                        message.deliveredAt != null -> "Delivered"
                                        else -> "Sent"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (message.messageType != "text") Text(message.messageType, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${active.title}") },
                maxLines = 5,
            )
            Button(
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch {
                        runCatching { state.client.sendMessage(active.id, text) }
                            .onSuccess { messages = messages + it }
                            .onFailure { error = it.message; draft = text }
                    }
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Send")
            }
        }
    }
}

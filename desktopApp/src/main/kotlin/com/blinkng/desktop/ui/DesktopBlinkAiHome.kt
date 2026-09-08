package com.blinkng.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopBlinkAiConversation
import com.blinkng.desktop.data.DesktopBlinkAiService
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private data class DesktopAiMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val metadata: String = "",
)

@Composable
fun HomeWithBlinkAiScreen(state: DesktopAppState) {
    var showBlinkAi by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { showBlinkAi = true },
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("AI", fontWeight = FontWeight.Black)
                Text("  Blink AI", fontWeight = FontWeight.SemiBold)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HomeScreen(state)
        }
    }

    if (showBlinkAi) {
        DesktopBlinkAiDialog(
            state = state,
            onDismiss = { showBlinkAi = false },
        )
    }
}

@Composable
private fun DesktopBlinkAiDialog(
    state: DesktopAppState,
    onDismiss: () -> Unit,
) {
    val service = remember(state.client) { DesktopBlinkAiService(state.client) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var previousInteractionId by remember { mutableStateOf<String?>(null) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("fast") }
    var tone by remember { mutableStateOf("balanced") }
    var responseLength by remember { mutableStateOf("medium") }
    var usePersonalContext by remember { mutableStateOf(true) }
    var useWebSearch by remember { mutableStateOf(true) }
    var temporaryChat by remember { mutableStateOf(false) }
    var customInstructions by remember { mutableStateOf("") }
    var imageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var conversations by remember { mutableStateOf<List<DesktopBlinkAiConversation>>(emptyList()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf(2L) }
    var messages by remember {
        mutableStateOf(
            listOf(
                DesktopAiMessage(
                    id = 1L,
                    text = "Hi! I’m Blink AI for Windows. Choose a mode, use current web search, attach images or a voice note, or continue a saved chat.",
                    fromUser = false,
                )
            )
        )
    }
    var lastPrompt by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { service.cancelActiveRequest() }
    }

    fun resetChat() {
        service.cancelActiveRequest()
        previousInteractionId = null
        activeConversationId = null
        sending = false
        error = null
        imageFiles = emptyList()
        audioFile = null
        messages = listOf(DesktopAiMessage(nextId++, "New chat started. What would you like help with?", false))
    }

    fun refreshHistory() {
        if (temporaryChat) return
        historyLoading = true
        scope.launch {
            runCatching { service.loadConversations() }
                .onSuccess { conversations = it }
                .onFailure { error = it.message ?: "Couldn’t load Blink AI history." }
            historyLoading = false
        }
    }

    fun send(promptOverride: String? = null, addUserBubble: Boolean = true) {
        val prompt = (promptOverride ?: input).trim()
        if ((prompt.isBlank() && imageFiles.isEmpty() && audioFile == null) || sending) return
        if (addUserBubble) {
            val visible = when {
                prompt.isNotBlank() -> prompt
                imageFiles.isNotEmpty() && audioFile != null -> "Analyze these images and this voice note."
                imageFiles.isNotEmpty() -> "Analyze ${if (imageFiles.size == 1) "this image" else "these images"}."
                else -> "Listen to this voice note."
            }
            messages = messages + DesktopAiMessage(nextId++, visible, true)
        }
        if (promptOverride == null) input = ""
        sending = true
        error = null
        lastPrompt = prompt
        val sentImages = imageFiles
        val sentAudio = audioFile
        scope.launch {
            runCatching {
                service.ask(
                    message = prompt,
                    previousInteractionId = previousInteractionId,
                    imageFiles = sentImages,
                    audioFile = sentAudio,
                    usePersonalContext = usePersonalContext,
                    useWebSearch = useWebSearch,
                    mode = mode,
                    tone = tone,
                    responseLength = responseLength,
                    temporaryChat = temporaryChat,
                    customInstructions = customInstructions,
                )
            }.onSuccess { reply ->
                previousInteractionId = reply.interactionId ?: previousInteractionId
                activeConversationId = reply.conversationId ?: activeConversationId
                val metadata = buildList {
                    reply.mode?.let { add(it.replaceFirstChar(Char::uppercaseChar)) }
                    reply.model?.let { add(it) }
                    reply.latencyMs?.let { add("${it}ms") }
                    if (reply.searchedWeb) add("Web")
                    if (temporaryChat) add("Temporary")
                }.joinToString(" • ")
                messages = messages + DesktopAiMessage(nextId++, reply.text, false, metadata)
                imageFiles = emptyList()
                audioFile = null
            }.onFailure { throwable ->
                error = if (throwable.message?.contains("Canceled", ignoreCase = true) == true) {
                    "Generation stopped."
                } else {
                    throwable.message ?: "Blink AI couldn't answer that request."
                }
            }
            sending = false
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Blink AI settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Response length", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("short", "medium", "long").forEach { item ->
                            FilterChip(
                                selected = responseLength == item,
                                onClick = { responseLength = item },
                                label = { Text(item.replaceFirstChar(Char::uppercaseChar)) },
                            )
                        }
                    }
                    Text("Tone", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("balanced", "concise", "friendly", "professional").forEach { item ->
                            FilterChip(
                                selected = tone == item,
                                onClick = { tone = item },
                                label = { Text(item.replaceFirstChar(Char::uppercaseChar)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = { customInstructions = it.take(1_200) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        label = { Text("Custom instructions") },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } },
        )
    }

    if (historyOpen) {
        val filtered = conversations.filter {
            historyQuery.isBlank() || it.title.contains(historyQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { if (!historyLoading) historyOpen = false },
            title = { Text("Blink AI history") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = historyQuery,
                        onValueChange = { historyQuery = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search chats") },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (historyLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                            Text("Loading…")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.id }) { conversation ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(conversation.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                        TextButton(
                                            onClick = {
                                                historyLoading = true
                                                scope.launch {
                                                    runCatching { service.loadConversationMessages(conversation.id) }
                                                        .onSuccess { rows ->
                                                            messages = rows.map { row ->
                                                                DesktopAiMessage(nextId++, row.content, row.role == "user")
                                                            }.ifEmpty {
                                                                listOf(DesktopAiMessage(nextId++, "This conversation is empty.", false))
                                                            }
                                                            previousInteractionId = rows.lastOrNull { it.providerInteractionId != null }?.providerInteractionId
                                                            activeConversationId = conversation.id
                                                            historyOpen = false
                                                        }
                                                        .onFailure { error = it.message }
                                                    historyLoading = false
                                                }
                                            }
                                        ) { Text("Open") }
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { service.deleteConversation(conversation.id) }
                                                        .onSuccess {
                                                            if (activeConversationId == conversation.id) resetChat()
                                                            refreshHistory()
                                                        }
                                                        .onFailure { error = it.message }
                                                }
                                            }
                                        ) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { refreshHistory() }) { Text("Refresh") } },
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (sending) service.cancelActiveRequest()
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blink AI", fontWeight = FontWeight.Black)
                    Text("Windows • Gemini • web • memory • media", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { resetChat() }, enabled = !sending) { Text("New") }
                TextButton(
                    onClick = {
                        historyOpen = true
                        refreshHistory()
                    },
                    enabled = !sending && !temporaryChat,
                ) { Text("History") }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("fast", "deep", "code", "research", "write", "study").forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = { Text(item.replaceFirstChar(Char::uppercaseChar)) },
                            enabled = !sending,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(8.dp)) {
                        DesktopAiSwitchRow(
                            title = "Use my Blink context",
                            checked = usePersonalContext,
                            enabled = previousInteractionId == null && !temporaryChat && !sending,
                            onChanged = { usePersonalContext = it },
                        )
                        DesktopAiSwitchRow(
                            title = "Web search",
                            checked = useWebSearch,
                            enabled = !sending,
                            onChanged = { useWebSearch = it },
                        )
                        DesktopAiSwitchRow(
                            title = "Temporary chat",
                            checked = temporaryChat,
                            enabled = previousInteractionId == null && !sending,
                            onChanged = {
                                temporaryChat = it
                                if (it) usePersonalContext = false
                            },
                        )
                        TextButton(onClick = { settingsOpen = true }, enabled = !sending) { Text("Response settings") }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        message.text,
                                        color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (message.metadata.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(message.metadata, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        if (!sending && lastPrompt != null) {
                            TextButton(onClick = { send(lastPrompt, addUserBubble = false) }) { Text("Retry") }
                        }
                    }
                }

                if (imageFiles.isNotEmpty()) {
                    Text("Images: ${imageFiles.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall)
                }
                audioFile?.let { Text("Voice note: ${it.name}", style = MaterialTheme.typography.bodySmall) }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Choose Blink AI images"
                                fileSelectionMode = JFileChooser.FILES_ONLY
                                isMultiSelectionEnabled = true
                                fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff", "heic", "heif")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                imageFiles = chooser.selectedFiles.toList().take(6)
                            }
                        },
                        enabled = !sending,
                    ) { Text("Images ${if (imageFiles.isEmpty()) "" else "(${imageFiles.size})"}") }
                    OutlinedButton(
                        onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "Choose Blink AI voice note"
                                fileSelectionMode = JFileChooser.FILES_ONLY
                                isMultiSelectionEnabled = false
                                fileFilter = FileNameExtensionFilter("Audio", "wav", "mp3", "aiff", "aac", "ogg", "flac", "m4a", "opus", "webm")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                audioFile = chooser.selectedFile
                            }
                        },
                        enabled = !sending,
                    ) { Text("Voice note") }
                    if (imageFiles.isNotEmpty() || audioFile != null) {
                        TextButton(
                            onClick = {
                                imageFiles = emptyList()
                                audioFile = null
                            },
                            enabled = !sending,
                        ) { Text("Clear media") }
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(8_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("Message Blink AI") },
                    enabled = !sending,
                )

                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                        Text("Blink AI is answering…", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                service.cancelActiveRequest()
                                sending = false
                                error = "Generation stopped."
                            }
                        ) { Text("Stop") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { send() },
                enabled = (input.isNotBlank() || imageFiles.isNotEmpty() || audioFile != null) && !sending,
            ) {
                if (sending) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Send")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !sending) { Text("Close") }
        },
    )
}

@Composable
private fun DesktopAiSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled)
    }
}

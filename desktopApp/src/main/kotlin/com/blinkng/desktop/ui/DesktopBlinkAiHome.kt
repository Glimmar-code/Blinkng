package com.blinkng.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.ai.BlinkAiExperienceCatalog
import com.blinkng.shared.ai.BlinkAiExperiencePage
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
    var compactFeedChrome by remember { mutableStateOf(false) }

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
                if (!compactFeedChrome) {
                    Text("  Blink AI", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HomeScreen(
                state = state,
                onFeedCompactChange = { compactFeedChrome = it },
            )
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
    var experiencePage by remember { mutableStateOf(BlinkAiExperiencePage.WELCOME) }
    var activeExploreId by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(2L) }
    var messages by remember {
        mutableStateOf(
            listOf(
                DesktopAiMessage(
                    id = 1L,
                    text = "Hi! I’m Blink AI for Windows. Ask anything, attach media, search the web, or choose a focused mode.",
                    fromUser = false,
                )
            )
        )
    }
    var lastPrompt by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { service.cancelActiveRequest() }
    }

    fun friendlyError(throwable: Throwable, fallback: String): String {
        val message = throwable.message?.trim().orEmpty()
        return when {
            message.contains("Canceled", ignoreCase = true) -> "Generation stopped."
            message.contains("not configured", ignoreCase = true) ||
                message.contains("missing_gemini_key", ignoreCase = true) ->
                "Blink AI is temporarily unavailable because its server AI provider is not configured."
            message.isNotBlank() -> message
            else -> fallback
        }
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
                .onFailure { error = friendlyError(it, "Couldn’t load Blink AI history.") }
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
                error = friendlyError(throwable, "Blink AI couldn't answer that request.")
            }
            sending = false
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Blink AI settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Context & privacy", fontWeight = FontWeight.Bold)
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

                    Text("Response", fontWeight = FontWeight.Bold)
                    Text("Length", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("short", "medium", "long").forEach { item ->
                            FilterChip(
                                selected = responseLength == item,
                                onClick = { responseLength = item },
                                label = { Text(item.replaceFirstChar(Char::uppercaseChar)) },
                            )
                        }
                    }
                    Text("Tone", style = MaterialTheme.typography.labelLarge)
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
                    } else if (filtered.isEmpty()) {
                        Text("No saved conversations found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                        .onFailure {
                                                            error = friendlyError(it, "Couldn’t open that conversation.")
                                                        }
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
                                                        .onFailure {
                                                            error = friendlyError(it, "Couldn’t delete that conversation.")
                                                        }
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
                    Text("BLINK AI", fontWeight = FontWeight.Black)
                    Text(
                        when (experiencePage) {
                            BlinkAiExperiencePage.WELCOME -> "Your AI inside BLINK"
                            BlinkAiExperiencePage.EXPLORE -> "Explore"
                            BlinkAiExperiencePage.CHAT -> "AI Chat"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        text = {
            when (experiencePage) {
                BlinkAiExperiencePage.WELCOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Hi there 👋", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap to chat", fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xFF063D34),
                        ) {
                            Text(
                                "≋",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF35E1BE),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Welcome to BLINK AI", fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Ask questions, study, write, code, research, analyze media or get help using BLINK.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { experiencePage = BlinkAiExperiencePage.EXPLORE }) {
                            Text("Explore AI tools")
                        }
                    }
                }

                BlinkAiExperiencePage.EXPLORE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Let’s Explore", fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Choose a focused BLINK AI mode.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BlinkAiExperienceCatalog.categories.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                rowItems.forEach { category ->
                                    Surface(
                                        onClick = {
                                            activeExploreId = category.id
                                            mode = category.mode
                                            resetChat()
                                            input = category.starterPrompt
                                            experiencePage = BlinkAiExperiencePage.CHAT
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 116.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (activeExploreId == category.id) {
                                            Color(0xFF0A3B33)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Text(
                                                category.title,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                category.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                BlinkAiExperiencePage.CHAT -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { resetChat() }, enabled = !sending) { Text("New chat") }
                    TextButton(
                        onClick = {
                            historyOpen = true
                            refreshHistory()
                        },
                        enabled = !sending && !temporaryChat,
                    ) { Text("History") }
                    TextButton(onClick = { settingsOpen = true }, enabled = !sending) { Text("Settings") }
                }

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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    val status = buildList {
                        add(mode.replaceFirstChar(Char::uppercaseChar))
                        if (useWebSearch) add("Web")
                        if (usePersonalContext && !temporaryChat) add("Blink context")
                        if (temporaryChat) add("Temporary")
                    }.joinToString(" • ")
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp, max = 330.dp),
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                it,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (!sending && lastPrompt != null) {
                                TextButton(onClick = { send(lastPrompt, addUserBubble = false) }) { Text("Retry") }
                            }
                        }
                    }
                }

                if (imageFiles.isNotEmpty()) {
                    Text("Images: ${imageFiles.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall)
                }
                audioFile?.let { Text("Voice note: ${it.name}", style = MaterialTheme.typography.bodySmall) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.take(8_000) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 6,
                            label = { Text("Message Blink AI") },
                            enabled = !sending,
                        )

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
                                        fileFilter = FileNameExtensionFilter(
                                            "Images",
                                            "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff", "heic", "heif",
                                        )
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        imageFiles = chooser.selectedFiles.toList().take(6)
                                    }
                                },
                                enabled = !sending,
                            ) { Text("Images${if (imageFiles.isEmpty()) "" else " (${imageFiles.size})"}") }

                            OutlinedButton(
                                onClick = {
                                    val chooser = JFileChooser().apply {
                                        dialogTitle = "Choose Blink AI voice note"
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        isMultiSelectionEnabled = false
                                        fileFilter = FileNameExtensionFilter(
                                            "Audio",
                                            "wav", "mp3", "aiff", "aac", "ogg", "flac", "m4a", "opus", "webm",
                                        )
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
                    }
                }

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
                }
            }
        },
        confirmButton = {
            when (experiencePage) {
                BlinkAiExperiencePage.WELCOME -> {
                    Button(onClick = { experiencePage = BlinkAiExperiencePage.CHAT }) {
                        Text("Start chatting")
                    }
                }
                BlinkAiExperiencePage.EXPLORE -> {
                    Button(onClick = { experiencePage = BlinkAiExperiencePage.CHAT }) {
                        Text("Open chat")
                    }
                }
                BlinkAiExperiencePage.CHAT -> {
                    Button(
                        onClick = { send() },
                        enabled = (input.isNotBlank() || imageFiles.isNotEmpty() || audioFile != null) && !sending,
                    ) {
                        if (sending) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text("Send")
                    }
                }
            }
        },
        dismissButton = {
            when (experiencePage) {
                BlinkAiExperiencePage.WELCOME -> {
                    OutlinedButton(onClick = onDismiss, enabled = !sending) { Text("Close") }
                }
                BlinkAiExperiencePage.EXPLORE -> {
                    TextButton(onClick = { experiencePage = BlinkAiExperiencePage.WELCOME }, enabled = !sending) {
                        Text("Back")
                    }
                }
                BlinkAiExperiencePage.CHAT -> {
                    TextButton(onClick = { experiencePage = BlinkAiExperiencePage.EXPLORE }, enabled = !sending) {
                        Text("Explore")
                    }
                }
            }
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

package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkAiAction
import com.example.data.supabase.BlinkAiConversation
import com.example.data.supabase.BlinkAiService
import kotlinx.coroutines.launch
import java.util.Locale

private data class BlinkAiMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val metadata: String = ""
)

private data class BlinkAiRequestSnapshot(
    val text: String,
    val imageUris: List<Uri>,
    val audioUri: Uri?,
    val mode: String,
    val tone: String,
    val responseLength: String,
    val usePersonalContext: Boolean,
    val useWebSearch: Boolean,
    val temporaryChat: Boolean,
    val customInstructions: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkAiSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val service = remember { BlinkAiService() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var interactionId by remember { mutableStateOf<String?>(null) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(2L) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var usePersonalContext by remember { mutableStateOf(true) }
    var useWebSearch by remember { mutableStateOf(true) }
    var temporaryChat by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("fast") }
    var tone by remember { mutableStateOf("balanced") }
    var responseLength by remember { mutableStateOf("medium") }
    var customInstructions by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<BlinkAiAction?>(null) }
    var pendingActionImageUri by remember { mutableStateOf<Uri?>(null) }
    var lastRequest by remember { mutableStateOf<BlinkAiRequestSnapshot?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf<List<BlinkAiConversation>>(emptyList()) }
    var historyQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<BlinkAiConversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var ttsReady by remember { mutableStateOf(false) }

    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) textToSpeech.language = Locale.getDefault()
    }
    DisposableEffect(Unit) {
        onDispose {
            service.cancelActiveRequest()
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    var messages by remember {
        mutableStateOf(
            listOf(
                BlinkAiMessage(
                    id = 1L,
                    text = "Hi! I’m Blink AI. Ask anything, choose a mode, search the web when needed, attach images or a voice note, or let me help with confirmed Blink profile changes.",
                    fromUser = false
                )
            )
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageUris = (imageUris + uris).distinct().take(6)
            error = null
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            audioUri = uri
            error = null
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrBlank()) {
            input = listOf(input.trim(), spoken).filter { it.isNotBlank() }.joinToString(" ").take(8_000)
        }
    }

    fun appendAssistant(text: String, metadata: String = "") {
        messages = messages + BlinkAiMessage(nextId++, text, false, metadata)
    }

    fun startNewChat() {
        service.cancelActiveRequest()
        interactionId = null
        activeConversationId = null
        pendingAction = null
        pendingActionImageUri = null
        imageUris = emptyList()
        audioUri = null
        error = null
        isSending = false
        messages = listOf(
            BlinkAiMessage(
                id = nextId++,
                text = "New chat started. What would you like help with?",
                fromUser = false
            )
        )
    }

    fun performSend(snapshot: BlinkAiRequestSnapshot, showUserBubble: Boolean) {
        if (isSending) return
        val visibleText = when {
            snapshot.text.isNotBlank() -> snapshot.text
            snapshot.imageUris.isNotEmpty() && snapshot.audioUri != null -> "Analyze these images and this voice note."
            snapshot.imageUris.isNotEmpty() -> "Analyze ${if (snapshot.imageUris.size == 1) "this image" else "these images"}."
            else -> "Listen to this voice note."
        }
        if (showUserBubble) messages = messages + BlinkAiMessage(nextId++, visibleText, true)
        error = null
        isSending = true
        lastRequest = snapshot

        scope.launch {
            service.ask(
                context = context,
                message = snapshot.text,
                previousInteractionId = interactionId,
                imageUris = snapshot.imageUris,
                audioUri = snapshot.audioUri,
                usePersonalContext = snapshot.usePersonalContext,
                useWebSearch = snapshot.useWebSearch,
                mode = snapshot.mode,
                tone = snapshot.tone,
                responseLength = snapshot.responseLength,
                temporaryChat = snapshot.temporaryChat,
                customInstructions = snapshot.customInstructions
            ).onSuccess { reply ->
                if (reply.action == null) {
                    interactionId = reply.interactionId ?: interactionId
                    activeConversationId = reply.conversationId ?: activeConversationId
                    imageUris = emptyList()
                    audioUri = null
                } else {
                    pendingAction = reply.action
                    pendingActionImageUri = snapshot.imageUris.firstOrNull()
                    audioUri = null
                }
                val metadata = buildList {
                    reply.mode?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
                    reply.model?.let { add(it) }
                    reply.latencyMs?.let { add("${it}ms") }
                    if (reply.searchedWeb) add("Web")
                    if (snapshot.temporaryChat) add("Temporary")
                }.joinToString(" • ")
                appendAssistant(reply.text, metadata)
            }.onFailure { throwable ->
                error = if (throwable.message?.contains("Canceled", ignoreCase = true) == true) {
                    "Generation stopped."
                } else {
                    throwable.message ?: "Blink AI couldn’t answer that request."
                }
            }
            isSending = false
        }
    }

    fun sendMessage() {
        val text = input.trim()
        if ((text.isBlank() && imageUris.isEmpty() && audioUri == null) || isSending) return
        val snapshot = BlinkAiRequestSnapshot(
            text = text,
            imageUris = imageUris,
            audioUri = audioUri,
            mode = mode,
            tone = tone,
            responseLength = responseLength,
            usePersonalContext = usePersonalContext,
            useWebSearch = useWebSearch,
            temporaryChat = temporaryChat,
            customInstructions = customInstructions
        )
        input = ""
        performSend(snapshot, showUserBubble = true)
    }

    fun retryLast() {
        lastRequest?.let { performSend(it, showUserBubble = false) }
    }

    fun confirmPendingAction() {
        val action = pendingAction ?: return
        if (isSending) return
        error = null
        isSending = true
        scope.launch {
            service.executeAction(
                context = context,
                action = action,
                imageUri = pendingActionImageUri
            ).onSuccess { reply ->
                appendAssistant(reply.text, if (reply.actionCompleted) "Action completed" else "")
                pendingAction = null
                pendingActionImageUri = null
                imageUris = emptyList()
                audioUri = null
            }.onFailure { throwable ->
                error = throwable.message ?: "Blink AI couldn’t complete that action."
            }
            isSending = false
        }
    }

    fun refreshHistory() {
        historyLoading = true
        scope.launch {
            service.loadConversations().onSuccess {
                conversations = it
            }.onFailure {
                error = it.message ?: "Couldn’t load Blink AI history."
            }
            historyLoading = false
        }
    }

    fun openConversation(conversation: BlinkAiConversation) {
        historyLoading = true
        scope.launch {
            service.loadConversationMessages(conversation.id).onSuccess { rows ->
                messages = rows.map { row ->
                    BlinkAiMessage(
                        id = nextId++,
                        text = row.content,
                        fromUser = row.role == "user"
                    )
                }.ifEmpty {
                    listOf(BlinkAiMessage(nextId++, "This conversation is empty.", false))
                }
                interactionId = rows.lastOrNull { it.providerInteractionId != null }?.providerInteractionId
                activeConversationId = conversation.id
                historyOpen = false
                error = null
            }.onFailure {
                error = it.message ?: "Couldn’t open that conversation."
            }
            historyLoading = false
        }
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, "Share Blink AI response"))
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!isSending) pendingAction = null },
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                Button(onClick = { confirmPendingAction() }, enabled = !isSending) {
                    if (isSending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }, enabled = !isSending) { Text("Cancel") }
            }
        )
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
                                label = { Text(item.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Text("Tone", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("balanced", "concise", "friendly", "professional").forEach { item ->
                            FilterChip(
                                selected = tone == item,
                                onClick = { tone = item },
                                label = { Text(item.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = { customInstructions = it.take(1_200) },
                        label = { Text("Custom instructions") },
                        placeholder = { Text("Example: explain coding answers step by step") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } }
        )
    }

    if (historyOpen) {
        val filtered = remember(conversations, historyQuery) {
            val q = historyQuery.trim()
            if (q.isBlank()) conversations else conversations.filter { it.title.contains(q, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { if (!historyLoading) historyOpen = false },
            title = { Text("Blink AI history") },
            text = {
                Column {
                    OutlinedTextField(
                        value = historyQuery,
                        onValueChange = { historyQuery = it.take(120) },
                        label = { Text("Search chats") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (historyLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Loading…")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered, key = { it.id }) { conversation ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(conversation.title, fontWeight = FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { openConversation(conversation) }) { Text("Open") }
                                            TextButton(onClick = {
                                                renameTarget = conversation
                                                renameText = conversation.title
                                            }) { Text("Rename") }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    service.deleteConversation(conversation.id).onSuccess {
                                                        if (activeConversationId == conversation.id) startNewChat()
                                                        refreshHistory()
                                                    }.onFailure { error = it.message }
                                                }
                                            }) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { refreshHistory() }) { Text("Refresh") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            service.renameConversation(target.id, renameText).onSuccess {
                                renameTarget = null
                                refreshHistory()
                            }.onFailure { error = it.message }
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isSending) service.cancelActiveRequest()
            onDismiss()
        },
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blink AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Gemini • web • memory • image + voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { startNewChat() }, enabled = !isSending) { Text("New") }
                TextButton(onClick = {
                    historyOpen = true
                    refreshHistory()
                }, enabled = !isSending && !temporaryChat) { Text("History") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("fast", "deep", "code", "research", "write", "study").forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { if (!isSending) mode = item },
                        label = { Text(item.replaceFirstChar { it.uppercase() }) },
                        enabled = !isSending
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    SettingSwitchRow(
                        title = "Use my Blink context",
                        subtitle = "Profile + your recent Blink activity. Private DMs stay excluded.",
                        checked = usePersonalContext,
                        enabled = interactionId == null && !isSending && !temporaryChat,
                        onCheckedChange = { usePersonalContext = it }
                    )
                    SettingSwitchRow(
                        title = "Web search",
                        subtitle = "Use current web sources when fresh information is useful.",
                        checked = useWebSearch,
                        enabled = !isSending,
                        onCheckedChange = { useWebSearch = it }
                    )
                    SettingSwitchRow(
                        title = "Temporary chat",
                        subtitle = "Do not save this conversation or use saved AI memory.",
                        checked = temporaryChat,
                        enabled = interactionId == null && !isSending,
                        onCheckedChange = {
                            temporaryChat = it
                            if (it) usePersonalContext = false
                        }
                    )
                    TextButton(onClick = { settingsOpen = true }, enabled = !isSending) {
                        Text("Response settings & custom instructions")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = RoundedCornerShape(18.dp),
                            color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    message.text,
                                    color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (message.metadata.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        message.metadata,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                if (!message.fromUser) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Text("Copy") }
                                        TextButton(onClick = { shareText(message.text) }) { Text("Share") }
                                        TextButton(
                                            onClick = {
                                                if (ttsReady) textToSpeech.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, "blink-ai-${message.id}")
                                            },
                                            enabled = ttsReady
                                        ) { Text("Read") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSending && pendingAction == null) {
                    item(key = "blink_ai_loading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Blink AI is answering…", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                service.cancelActiveRequest()
                                isSending = false
                                error = "Generation stopped."
                            }) { Text("Stop") }
                        }
                    }
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (lastRequest != null && !isSending) {
                            TextButton(onClick = { retryLast() }) { Text("Retry") }
                        }
                    }
                }
            }

            if (imageUris.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageUris.forEach { uri ->
                        Surface(shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Image attached to Blink AI",
                                    modifier = Modifier.size(58.dp),
                                    contentScale = ContentScale.Crop
                                )
                                TextButton(onClick = { imageUris = imageUris - uri }, enabled = !isSending) { Text("Remove") }
                            }
                        }
                    }
                }
            }

            if (audioUri != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice note attached", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { audioUri = null }, enabled = !isSending) { Text("Remove") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 8_000) input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Blink AI anything…") },
                minLines = 1,
                maxLines = 5,
                enabled = !isSending && pendingAction == null
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !isSending && pendingAction == null) {
                    Text("Images ${if (imageUris.isNotEmpty()) "(${imageUris.size})" else ""}")
                }
                OutlinedButton(onClick = { audioPicker.launch("audio/*") }, enabled = !isSending && pendingAction == null) {
                    Text("Voice note")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            speechLauncher.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Blink AI")
                            )
                        }.onFailure { error = "Speech recognition isn’t available on this device." }
                    },
                    enabled = !isSending && pendingAction == null
                ) { Text("Dictate") }
                Button(
                    onClick = { sendMessage() },
                    enabled = (input.isNotBlank() || imageUris.isNotEmpty() || audioUri != null) && !isSending && pendingAction == null
                ) {
                    if (isSending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Send")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

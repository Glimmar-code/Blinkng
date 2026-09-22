package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkAiAction
import com.example.data.supabase.BlinkAiConversation
import com.example.data.supabase.BlinkAiService
import com.blinkng.shared.ai.BlinkAiExperienceCatalog
import com.blinkng.shared.ai.BlinkAiExperiencePage
import com.example.util.startActivitySafely
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
    var experiencePage by remember { mutableStateOf(BlinkAiExperiencePage.WELCOME) }
    var activeExploreId by remember { mutableStateOf<String?>(null) }

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
                    text = "Hi! I’m Blink AI. Ask anything, attach media, search the web, or use one of the focused modes below.",
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
            input = listOf(input.trim(), spoken)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .take(8_000)
        }
    }

    fun appendAssistant(text: String, metadata: String = "") {
        messages = messages + BlinkAiMessage(nextId++, text, false, metadata)
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

        if (showUserBubble) {
            messages = messages + BlinkAiMessage(nextId++, visibleText, true)
        }
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
                error = friendlyError(throwable, "Blink AI couldn’t answer that request.")
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
                error = friendlyError(throwable, "Blink AI couldn’t complete that action.")
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
                error = friendlyError(it, "Couldn’t load Blink AI history.")
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
                error = friendlyError(it, "Couldn’t open that conversation.")
            }
            historyLoading = false
        }
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivitySafely(Intent.createChooser(intent, "Share Blink AI response"), "No compatible app is available to share this response.")
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
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }, enabled = !isSending) {
                    Text("Cancel")
                }
            }
        )
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Blink AI settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Context & privacy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    SettingSwitchRow(
                        title = "Use my Blink context",
                        subtitle = "Profile and recent Blink activity. Private DMs stay excluded.",
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
                        subtitle = "Do not save this chat or use saved AI memory.",
                        checked = temporaryChat,
                        enabled = interactionId == null && !isSending,
                        onCheckedChange = {
                            temporaryChat = it
                            if (it) usePersonalContext = false
                        }
                    )

                    Text(
                        "Response",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Length", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("short", "medium", "long").forEach { item ->
                            FilterChip(
                                selected = responseLength == item,
                                onClick = { responseLength = item },
                                label = { Text(item.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Text("Tone", style = MaterialTheme.typography.labelLarge)
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
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Done") }
            }
        )
    }

    if (historyOpen) {
        val filtered = remember(conversations, historyQuery) {
            val q = historyQuery.trim()
            if (q.isBlank()) {
                conversations
            } else {
                conversations.filter { it.title.contains(q, ignoreCase = true) }
            }
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
                    } else if (filtered.isEmpty()) {
                        Text(
                            "No saved conversations found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TextButton(onClick = { openConversation(conversation) }) {
                                                Text("Open")
                                            }
                                            TextButton(onClick = {
                                                renameTarget = conversation
                                                renameText = conversation.title
                                            }) {
                                                Text("Rename")
                                            }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    service.deleteConversation(conversation.id).onSuccess {
                                                        if (activeConversationId == conversation.id) startNewChat()
                                                        refreshHistory()
                                                    }.onFailure {
                                                        error = friendlyError(it, "Couldn’t delete that conversation.")
                                                    }
                                                }
                                            }) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyOpen = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { refreshHistory() }) { Text("Refresh") }
            }
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
                            }.onFailure {
                                error = friendlyError(it, "Couldn’t rename that conversation.")
                            }
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    Dialog(
        onDismissRequest = {
            if (isSending) service.cancelActiveRequest()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = Color(0xFF020807),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlinkMark(size = 34.dp, showText = false)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            "BLINK AI",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            when (experiencePage) {
                                BlinkAiExperiencePage.WELCOME -> "Your AI inside BLINK"
                                BlinkAiExperiencePage.EXPLORE -> "Explore"
                                BlinkAiExperiencePage.CHAT -> "AI Chat"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                    TextButton(
                        onClick = {
                            settingsOpen = true
                        },
                        enabled = !isSending
                    ) {
                        Text("•••", color = Color.White, fontWeight = FontWeight.Black)
                    }
                    TextButton(
                        onClick = {
                            if (isSending) service.cancelActiveRequest()
                            onDismiss()
                        }
                    ) {
                        Text("Close", color = Color.White.copy(alpha = 0.78f))
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (experiencePage) {
                    BlinkAiExperiencePage.WELCOME -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Spacer(Modifier.height(38.dp))
                            Text(
                                "Hi there 👋",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.68f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap to chat",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(22.dp))
                            Surface(
                                modifier = Modifier.size(74.dp),
                                shape = CircleShape,
                                color = Color(0xFF063D34)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "≋",
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF35E1BE)
                                    )
                                }
                            }
                            Spacer(Modifier.height(54.dp))
                            Text(
                                "Welcome to BLINK AI",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ask questions, study, write, code, research, analyze media or get help using BLINK.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.66f)
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    experiencePage = BlinkAiExperiencePage.CHAT
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start chatting", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    experiencePage = BlinkAiExperiencePage.EXPLORE
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Explore AI tools")
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    BlinkAiExperiencePage.EXPLORE -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "Let’s Explore",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                "Choose a focused BLINK AI mode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.62f)
                            )
                            Spacer(Modifier.height(16.dp))

                            BlinkAiExperienceCatalog.categories.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { category ->
                                        Surface(
                                            onClick = {
                                                activeExploreId = category.id
                                                mode = category.mode
                                                startNewChat()
                                                input = category.starterPrompt
                                                experiencePage = BlinkAiExperiencePage.CHAT
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 126.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (activeExploreId == category.id) {
                                                Color(0xFF0A3B33)
                                            } else {
                                                Color(0xFF061714)
                                            }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0B2E28)
                                                ) {
                                                    Text(
                                                        category.title.take(1),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF35E1BE)
                                                    )
                                                }
                                                Text(
                                                    category.title,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    category.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }

                    BlinkAiExperiencePage.CHAT -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { startNewChat() }, enabled = !isSending) {
                                Text("New chat", color = Color.White)
                            }
                            TextButton(
                                onClick = {
                                    historyOpen = true
                                    refreshHistory()
                                },
                                enabled = !isSending && !temporaryChat
                            ) {
                                Text("History", color = Color.White)
                            }
                            TextButton(onClick = { settingsOpen = true }, enabled = !isSending) {
                                Text("Settings", color = Color.White)
                            }
                            TextButton(
                                onClick = { experiencePage = BlinkAiExperiencePage.EXPLORE },
                                enabled = !isSending
                            ) {
                                Text("Explore", color = Color(0xFF35E1BE))
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF061714)
                        ) {
                            val status = buildList {
                                add(mode.replaceFirstChar { it.uppercase() })
                                if (useWebSearch) add("Web")
                                if (usePersonalContext && !temporaryChat) add("BLINK context")
                                if (temporaryChat) add("Temporary")
                            }.joinToString(" • ")
                            Text(
                                status,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.58f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (message.fromUser) {
                                            Color(0xFF0B8E78)
                                        } else {
                                            Color(0xFF071815)
                                        }
                                    ) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                                            Text(
                                                message.text,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            if (message.metadata.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    message.metadata,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.52f)
                                                )
                                            }

                                            if (!message.fromUser) {
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    TextButton(onClick = {
                                                        clipboard.setText(AnnotatedString(message.text))
                                                    }) {
                                                        Text("Copy", color = Color(0xFF35E1BE))
                                                    }
                                                    TextButton(onClick = { shareText(message.text) }) {
                                                        Text("Share", color = Color(0xFF35E1BE))
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            if (ttsReady) {
                                                                textToSpeech.speak(
                                                                    message.text,
                                                                    TextToSpeech.QUEUE_FLUSH,
                                                                    null,
                                                                    "blink-ai-${message.id}"
                                                                )
                                                            }
                                                        },
                                                        enabled = ttsReady
                                                    ) {
                                                        Text("Read", color = Color(0xFF35E1BE))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isSending && pendingAction == null) {
                                item(key = "blink_ai_loading") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF061714)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text(
                                                "  BLINK AI is answering…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.72f)
                                            )
                                            Spacer(Modifier.weight(1f))
                                            TextButton(onClick = {
                                                service.cancelActiveRequest()
                                                isSending = false
                                                error = "Generation stopped."
                                            }) {
                                                Text("Stop")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        error?.let { message ->
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF071815)
                                    ) {
                                        Column(
                                            Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Image attached to BLINK AI",
                                                modifier = Modifier.size(58.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                            TextButton(
                                                onClick = { imageUris = imageUris - uri },
                                                enabled = !isSending
                                            ) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (audioUri != null) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF071815),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Voice note attached",
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    TextButton(onClick = { audioUri = null }, enabled = !isSending) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF061411)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { if (it.length <= 8_000) input = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Message BLINK AI…") },
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
                                    OutlinedButton(
                                        onClick = { imagePicker.launch("image/*") },
                                        enabled = !isSending && pendingAction == null
                                    ) {
                                        Text("Image${if (imageUris.isNotEmpty()) " (${imageUris.size})" else ""}")
                                    }
                                    OutlinedButton(
                                        onClick = { audioPicker.launch("audio/*") },
                                        enabled = !isSending && pendingAction == null
                                    ) {
                                        Text("Audio")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                speechLauncher.launch(
                                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                                        .putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                        )
                                                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to BLINK AI")
                                                )
                                            }.onFailure {
                                                error = "Speech recognition isn’t available on this device."
                                            }
                                        },
                                        enabled = !isSending && pendingAction == null
                                    ) {
                                        Text("Voice")
                                    }
                                    Button(
                                        onClick = { sendMessage() },
                                        enabled = (input.isNotBlank() || imageUris.isNotEmpty() || audioUri != null) &&
                                            !isSending && pendingAction == null
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Send")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF04110F)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { experiencePage = BlinkAiExperiencePage.EXPLORE },
                            enabled = !isSending
                        ) {
                            Text(
                                "Explore",
                                color = if (experiencePage == BlinkAiExperiencePage.EXPLORE) {
                                    Color(0xFF35E1BE)
                                } else {
                                    Color.White.copy(alpha = 0.58f)
                                }
                            )
                        }
                        TextButton(
                            onClick = {
                                historyOpen = true
                                refreshHistory()
                            },
                            enabled = !isSending && !temporaryChat
                        ) {
                            Text("History", color = Color.White.copy(alpha = 0.58f))
                        }
                        TextButton(
                            onClick = { experiencePage = BlinkAiExperiencePage.CHAT },
                            enabled = !isSending
                        ) {
                            Text(
                                "Chat",
                                color = if (experiencePage == BlinkAiExperiencePage.CHAT) {
                                    Color(0xFF35E1BE)
                                } else {
                                    Color.White.copy(alpha = 0.58f)
                                }
                            )
                        }
                    }
                }
            }
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
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkAiAction
import com.example.data.supabase.BlinkAiService
import kotlinx.coroutines.launch

private data class BlinkAiMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkAiSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val service = remember { BlinkAiService() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var interactionId by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(2L) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var usePersonalContext by remember { mutableStateOf(true) }
    var pendingAction by remember { mutableStateOf<BlinkAiAction?>(null) }
    var pendingActionImageUri by remember { mutableStateOf<Uri?>(null) }

    var messages by remember {
        mutableStateOf(
            listOf(
                BlinkAiMessage(
                    id = 1L,
                    text = "Hi! I’m Blink AI. I can use your Blink profile and activity to personalize answers, understand images and voice notes you attach, and help with confirmed profile tasks such as changing your profile photo, cover photo, bio, headline and academic details.",
                    fromUser = false
                )
            )
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
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

    fun appendAssistant(text: String) {
        messages = messages + BlinkAiMessage(nextId++, text, false)
    }

    fun sendMessage() {
        val text = input.trim()
        if ((text.isBlank() && imageUri == null && audioUri == null) || isSending) return

        val sentImage = imageUri
        val sentAudio = audioUri
        val visibleText = when {
            text.isNotBlank() -> text
            sentImage != null && sentAudio != null -> "Analyze this image and voice note."
            sentImage != null -> "Analyze this image."
            else -> "Listen to this voice note."
        }

        messages = messages + BlinkAiMessage(nextId++, visibleText, true)
        input = ""
        error = null
        isSending = true

        scope.launch {
            service.ask(
                context = context,
                message = text,
                previousInteractionId = interactionId,
                imageUri = sentImage,
                audioUri = sentAudio,
                usePersonalContext = usePersonalContext
            ).onSuccess { reply ->
                if (reply.action == null) {
                    interactionId = reply.interactionId ?: interactionId
                    imageUri = null
                    audioUri = null
                } else {
                    pendingAction = reply.action
                    pendingActionImageUri = sentImage
                    audioUri = null
                }
                appendAssistant(reply.text)
            }.onFailure { throwable ->
                error = throwable.message ?: "Blink AI couldn’t answer that request."
            }
            isSending = false
        }
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
                appendAssistant(reply.text)
                pendingAction = null
                pendingActionImageUri = null
                imageUri = null
                audioUri = null
            }.onFailure { throwable ->
                error = throwable.message ?: "Blink AI couldn’t complete that action."
            }
            isSending = false
        }
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                if (!isSending) pendingAction = null
            },
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                Button(
                    onClick = { confirmPendingAction() },
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingAction = null },
                    enabled = !isSending
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    Text(
                        text = "Blink AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fast • personal • image + voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss, enabled = !isSending) {
                    Text("Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use my Blink context",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Profile + your recent Blink activity. Private DMs are excluded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usePersonalContext,
                        onCheckedChange = { usePersonalContext = it },
                        enabled = interactionId == null && !isSending
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.88f),
                            shape = RoundedCornerShape(18.dp),
                            color = if (message.fromUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = message.text,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = if (message.fromUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (isSending && pendingAction == null) {
                    item(key = "blink_ai_loading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "  Blink AI is answering…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            imageUri?.let { uri ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Image attached to Blink AI",
                            modifier = Modifier.size(52.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text("Image attached", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Blink AI can inspect it or use it for a confirmed profile-photo task.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { imageUri = null },
                            enabled = !isSending && pendingAction == null
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }

            if (audioUri != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Voice note attached", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Blink AI can transcribe, summarize and answer questions about it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { audioUri = null },
                            enabled = !isSending
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 4_000) input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Blink AI anything…") },
                minLines = 1,
                maxLines = 4,
                enabled = !isSending && pendingAction == null
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !isSending && pendingAction == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Image")
                }
                OutlinedButton(
                    onClick = { audioPicker.launch("audio/*") },
                    enabled = !isSending && pendingAction == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Voice note")
                }
                Button(
                    onClick = { sendMessage() },
                    enabled = (input.isNotBlank() || imageUri != null || audioUri != null) && !isSending && pendingAction == null,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

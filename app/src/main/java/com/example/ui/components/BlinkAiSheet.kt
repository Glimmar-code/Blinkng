package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val service = remember { BlinkAiService() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var interactionId by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextId by remember { mutableStateOf(2L) }
    var messages by remember {
        mutableStateOf(
            listOf(
                BlinkAiMessage(
                    id = 1L,
                    text = "Hi! I’m Blink AI. Ask me a question, get help writing something, summarize text, brainstorm ideas, or learn something new.",
                    fromUser = false
                )
            )
        )
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank() || isSending) return

        val userMessage = BlinkAiMessage(nextId++, text, true)
        messages = messages + userMessage
        input = ""
        error = null
        isSending = true

        scope.launch {
            service.ask(text, interactionId)
                .onSuccess { reply ->
                    interactionId = reply.interactionId ?: interactionId
                    messages = messages + BlinkAiMessage(nextId++, reply.text, false)
                }
                .onFailure { throwable ->
                    error = throwable.message ?: "Blink AI couldn’t answer that request."
                }
            isSending = false
        }
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
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
                        text = "Your AI assistant inside Blink",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Surface(
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

                if (isSending) {
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
                                text = "  Blink AI is thinking…",
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

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 4_000) input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Blink AI anything…") },
                minLines = 1,
                maxLines = 5,
                enabled = !isSending
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { sendMessage() },
                enabled = input.isNotBlank() && !isSending,
                modifier = Modifier.fillMaxWidth()
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.blinkng.desktop.data.DesktopBlinkAiService
import kotlinx.coroutines.launch

/**
 * Windows home host that keeps Blink AI available above the desktop feed while reusing the
 * existing HomeScreen. Android and Windows therefore expose the same authenticated AI service
 * without duplicating or reviving the retired Android legacy feed implementation.
 */
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
    var response by remember { mutableStateOf("Hi! I’m Blink AI. Ask me anything about Blink or your campus experience.") }
    var previousInteractionId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Blink AI", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(response, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("Message Blink AI") },
                    enabled = !sending,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val message = input.trim()
                    if (message.isBlank() || sending) return@Button
                    sending = true
                    error = null
                    scope.launch {
                        runCatching {
                            service.ask(
                                message = message,
                                previousInteractionId = previousInteractionId,
                                usePersonalContext = true,
                            )
                        }.onSuccess { reply ->
                            response = reply.text
                            previousInteractionId = reply.interactionId ?: previousInteractionId
                            input = ""
                        }.onFailure { throwable ->
                            error = throwable.message ?: "Blink AI couldn't answer that request."
                        }
                        sending = false
                    }
                },
                enabled = input.isNotBlank() && !sending,
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

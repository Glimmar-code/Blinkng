package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Consent-first chat additions. These send ordinary supported BLINK text messages
 * rather than claiming a native interactive-poll or live-location backend exists.
 */
@Composable
internal fun BlinkChatExtrasDialog(
    mode: String,
    contact: Pair<String, String>? = null,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(mode) { mutableStateOf("") }
    var details by remember(mode) { mutableStateOf("") }
    var other by remember(mode) { mutableStateOf("") }
    val isContact = mode == "contact"
    val isLocation = mode == "location"
    val isArticle = mode == "article"
    val isPoll = mode == "poll"
    val heading = when(mode) {
        "location" -> "Share a place"
        "contact" -> "Share contact"
        "article" -> "Share article"
        "poll" -> "Compose poll"
        else -> "Share"
    }
    val text = when {
        isLocation && title.isNotBlank() -> {
            val place = title.trim()
            "📍 $place\nhttps://maps.google.com/?q=${Uri.encode(place)}"
        }
        isArticle && details.startsWith("https://", ignoreCase=true) ->
            "📰 ${title.trim()}\n${details.trim()}"
        isContact && contact != null ->
            "👤 Contact: ${contact.first}\nPhone: ${contact.second}"
        isPoll && title.isNotBlank() -> {
            val choices = details.lines().map(String::trim).filter(String::isNotBlank)
                .distinct().take(10)
            if (choices.size >= 2)
                "📊 Poll question: ${title.trim()}\n" +
                    choices.mapIndexed { index, option -> "${index + 1}. $option" }
                        .joinToString("\n") +
                    "\n(Reply with your choice number)"
            else ""
        }
        else -> ""
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(Color(0xFF182129), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(12.dp))
            when {
                isLocation -> {
                    Text("Enter a place or coordinates to share. No location permission is needed.",
                        color = Color.LightGray)
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(200)},
                        label={Text("Place, address or coordinates")}, singleLine=true,
                        modifier=Modifier.fillMaxWidth()
                    )
                }
                isArticle -> {
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(150)},
                        label={Text("Article title")},modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=details, onValueChange={details=it.take(1000)},
                        label={Text("HTTPS article link")},singleLine=true,
                        modifier=Modifier.fillMaxWidth(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri)
                    )
                }
                isContact -> {
                    Text(contact?.first.orEmpty(), color=Color.White,
                        style=MaterialTheme.typography.titleMedium)
                    Text(contact?.second.orEmpty(), color=Color.LightGray)
                    Text("Only the selected name and phone number will be shared.",
                        color=Color.LightGray)
                }
                isPoll -> {
                    Text("Text poll: people reply with an option number. Voting buttons are not available yet.",
                        color=Color.LightGray)
                    OutlinedTextField(
                        value=title, onValueChange={title=it.take(200)},
                        label={Text("Question")},modifier=Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value=details,onValueChange={details=it.take(1400)},
                        label={Text("Options (one per line, 2–10)")},
                        minLines=4,maxLines=10,
                        modifier=Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick=onDismiss) { Text("Cancel") }
                Button(
                    enabled=text.isNotBlank(),
                    onClick={onSend(text)},
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00A884))
                ) { Text("Send") }
            }
        }
    }
}

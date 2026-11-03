package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared UI validation. Server-side poll validation remains authoritative. */
internal fun isBlinkPollReady(
    question: String,
    options: List<String>,
    durationHours: Int
): Boolean {
    val cleaned = options.map { it.trim() }
    return question.trim().isNotBlank() &&
        question.length <= 240 &&
        cleaned.size in 2..4 &&
        cleaned.all { it.isNotBlank() && it.length <= 120 } &&
        cleaned.map { it.lowercase() }.distinct().size == cleaned.size &&
        durationHours in listOf(1, 24, 72, 168)
}

/**
 * Reference-first Poll creation. Done returns the editable, unsent poll to
 * Create Post; publishing remains a separate Post Details -> Post Options action.
 */
@Composable
internal fun BlinkReferencePollScreen(
    question: String,
    onQuestionChanged: (String) -> Unit,
    options: List<String>,
    onOptionsChanged: (List<String>) -> Unit,
    durationHours: Int,
    onDurationChanged: (Int) -> Unit,
    enabled: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val ready = isBlinkPollReady(question, options, durationHours)
    Column(Modifier.fillMaxSize().background(Color.Black)
        .testTag("blink_poll_fullscreen")) {
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).testTag("blink_poll_back")
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back from poll",
                    tint = Color.White)
            }
            Text("Poll", color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
            Button(
                onClick = onDone,
                enabled = enabled && ready,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 12.dp).height(36.dp)
                    .testTag("blink_poll_done"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
            ) { Text("Done", color = Color.White, fontSize = 12.sp) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (!ready) {
                Text(
                    "Enter a question and 2–4 different answers to continue.",
                    color = Color(0xFFB9C0CF),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("blink_poll_validation_hint")
                )
            }
            PollComposer(
                question = question,
                onQuestionChanged = onQuestionChanged,
                options = options,
                onOptionsChanged = onOptionsChanged,
                durationHours = durationHours,
                onDurationChanged = onDurationChanged,
                enabled = enabled,
                standalone = true
            )
        }
    }
}

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
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 12.dp).height(36.dp)
                    .testTag("blink_poll_done"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
            ) { Text("Done", color = Color.White, fontSize = 12.sp) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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

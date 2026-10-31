package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatPollDraft
import com.example.data.models.ChatMessage
import com.example.data.supabase.BlinkChatNativePollService
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Vote counts and percentages come ONLY from the authenticated poll RPC. */
@Composable
internal fun BlinkChatNativePollCard(
    message: ChatMessage,
    fallback: BlinkChatPollDraft?,
    palette: MessagePalette,
    textColor: Color,
    onLegacyChoice: (Int) -> Unit
) {
    val service = remember { BlinkChatNativePollService() }
    val scope = rememberCoroutineScope()
    var poll by remember(message.id) { mutableStateOf<JSONObject?>(null) }
    var sending by remember(message.id) { mutableStateOf(false) }
    var error by remember(message.id) { mutableStateOf<String?>(null) }
    var loading by remember(message.id) { mutableStateOf(true) }
    var loadError by remember(message.id) { mutableStateOf<String?>(null) }
    var refreshKey by remember(message.id) { mutableIntStateOf(0) }
    val validMessageId = remember(message.id) {
        runCatching { java.util.UUID.fromString(message.id) }.isSuccess
    }

    LaunchedEffect(message.id, refreshKey) {
        if (!validMessageId) {
            loading = false
            loadError = "Poll results are unavailable for this message."
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        service.results(message.id)
            .onSuccess { response ->
                if (BlinkChatPollResultPolicy.parse(response, message.id) != null) {
                    poll = response
                } else {
                    poll = null
                    loadError = "Poll results could not be verified."
                }
            }
            .onFailure {
                poll = null
                loadError = "Couldn't load poll results. Check your connection."
            }
        loading = false
    }
    val result = poll?.let { BlinkChatPollResultPolicy.parse(it, message.id) }
    if (result == null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fallback != null) {
                // Explicitly labeled reply-only fallback; never imply a
                // prepared reply is a counted server vote.
                BlinkChatLegacyPollCard(fallback, palette, textColor, onLegacyChoice)
            }
            if (message.messageType == "poll" || fallback == null) {
                BlinkChatPollRecoveryControls(
                    loading = loading,
                    message = loadError,
                    allowRetry = validMessageId,
                    palette = palette,
                    onRetry = { if (!loading) refreshKey++ }
                )
            }
        }
        return
    }
    BlinkChatPollResultsCard(
        result = result, palette = palette, textColor = textColor,
        sending = sending, error = error,
        onChoice = { index ->
            if (!sending && !result.closed) {
                sending = true
                scope.launch {
                    service.vote(message.id, index)
                        .onSuccess { response ->
                            if (BlinkChatPollResultPolicy.parse(response, message.id) != null) {
                                poll = response
                                error = null
                            } else {
                                error = "Poll results could not be verified. Reopen the chat."
                            }
                        }
                        .onFailure { error = "Could not record your vote. Try again." }
                    sending = false
                }
            }
        }
    )
}

/**
 * Poll failure and retry are explicit, never a fabricated percentage.
 * Useful on offline devices and when the native poll RPC is temporarily down.
 */
@Composable
internal fun BlinkChatPollRecoveryControls(
    loading: Boolean,
    message: String?,
    allowRetry: Boolean,
    palette: MessagePalette,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(min = 180.dp, max = 305.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("chat_poll_recovery"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (loading) "Loading poll results…" else
                    message ?: "Poll results are unavailable.",
                color = palette.textSecondary, fontSize = 11.sp,
                maxLines = 2
            )
        }
        if (!loading && allowRetry) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 36.dp)
                    .testTag("chat_poll_retry")
            ) {
                Text("Try again", color = palette.accent, fontSize = 12.sp)
            }
        }
    }
}

/** Reference-style result card. Accepts validated, server-provided data only. */
@Composable
internal fun BlinkChatPollResultsCard(
    result: BlinkChatPollResult,
    palette: MessagePalette,
    textColor: Color,
    sending: Boolean,
    error: String?,
    onChoice: (Int) -> Unit
) {
    val dark = palette.mode == MessageThemeMode.DARK
    val foreground = if (dark) palette.textPrimary else textColor
    Surface(
        color = if (dark) Color.Transparent else palette.glassElevated,
        shape = RoundedCornerShape(13.dp),
        border = if (dark) null else BorderStroke(1.dp, palette.border),
        modifier = Modifier.widthIn(min = 218.dp, max = 305.dp)
            .testTag("chat_reference_native_poll")
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null,
                    tint = palette.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Quick Poll", color = foreground, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
            Text(result.question, color = if (dark) palette.textSecondary else foreground,
                fontSize = 12.sp, maxLines = 3)
            result.options.forEachIndexed { index, option ->
                val selected = result.myVote == index
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(
                            enabled = !result.closed && !sending,
                            role = Role.Button,
                            onClickLabel = "Vote for ${option.label}",
                            onClick = { onChoice(index) }
                        )
                        .testTag("chat_reference_native_poll_choice_$index")
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (selected) palette.accent else Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (selected) palette.accent else palette.textMuted)
                    ) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            Text(('A' + index).toString(),
                                color = if (selected) Color.White else foreground,
                                fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(option.label, color = foreground, fontSize = 11.sp,
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    LinearProgressIndicator(
                        progress = { result.progress(index) },
                        color = if (selected) palette.accent else palette.textMuted,
                        trackColor = palette.glass,
                        modifier = Modifier.width(70.dp).height(6.dp)
                            .testTag("chat_reference_native_poll_bar_$index")
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${result.percent(index)}%", color = palette.textSecondary,
                        fontSize = 10.sp, modifier = Modifier.width(32.dp)
                            .testTag("chat_reference_native_poll_percent_$index"))
                }
            }
            Text(
                "${result.totalVotes} " +
                    (if (result.totalVotes == 1) "vote" else "votes") +
                    (if (result.closed) " · Closed" else ""),
                color = palette.textMuted, fontSize = 10.sp
            )
            if (sending) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent
                )
            }
            if (error != null) Text(error, color = palette.danger, fontSize = 11.sp)
        }
    }
}

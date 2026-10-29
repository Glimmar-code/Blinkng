package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatPollDraft
import com.example.data.models.ChatMessage
import com.example.data.supabase.BlinkChatNativePollService
import com.example.ui.theme.MessagePalette
import com.example.ui.theme.MessageThemeMode
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Counted results appear ONLY when returned by an authorized server RPC. */
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
    LaunchedEffect(message.id) {
        if (runCatching { java.util.UUID.fromString(message.id) }.isSuccess) {
            service.results(message.id).onSuccess { poll = it }
        }
    }
    val actual = poll
    if (actual == null) {
        if (fallback != null) BlinkChatLegacyPollCard(
            fallback, palette, textColor, onLegacyChoice
        ) else Text("Poll results unavailable · Open chat again later",
            color = textColor, fontSize = 12.sp)
        return
    }
    val total = actual.optInt("total_votes").coerceAtLeast(0)
    val options = actual.optJSONArray("options")
    val mine = if (actual.isNull("my_vote")) -1 else actual.optInt("my_vote", -1)
    val closed = actual.optBoolean("closed")
    val referenceDark = palette.mode == MessageThemeMode.DARK
    val cardText = if (referenceDark) palette.textPrimary else textColor
    Surface(
        color = if (referenceDark) palette.incomingBubble else palette.glassElevated,
        shape = RoundedCornerShape(17.dp),
        border = if (referenceDark) null else BorderStroke(1.dp, palette.border),
        modifier = Modifier.widthIn(min = 220.dp, max = 305.dp)
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("▥  Quick Poll", color = cardText, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
            Text(actual.optString("question"), color = palette.textSecondary, fontSize = 12.sp)
            for (index in 0 until (options?.length() ?: 0)) {
                val item = options?.optJSONObject(index) ?: continue
                val count = item.optInt("votes").coerceAtLeast(0)
                val percent = if (total > 0) count * 100 / total else 0
                val selected = mine == index
                Column(
                    modifier = Modifier.fillMaxWidth().clickable(
                        enabled = !closed && !sending
                    ) {
                        sending = true
                        scope.launch {
                            service.vote(message.id, index)
                                .onSuccess { poll = it; error = null }
                                .onFailure { error = "Could not record your vote. Try again." }
                            sending = false
                        }
                    }.padding(vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (selected) palette.accent else Color.Transparent,
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp,
                                if (selected) palette.accent else palette.textMuted)
                        ) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                Text(('A' + index).toString(), color = cardText, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(item.optString("label"), color = cardText, fontSize = 12.sp,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Text("$percent%", color = palette.textSecondary, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else count.toFloat() / total },
                        color = if (selected) palette.accent else palette.textMuted,
                        trackColor = palette.glass,
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    )
                }
            }
            Text(total.toString() + (if (total == 1) " vote" else " votes") +
                if (closed) " · Closed" else " · Tap a choice to vote",
                color = palette.textMuted, fontSize = 10.sp)
            if (sending) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                color = palette.accent)
            if (error != null) Text(error.orEmpty(), color = palette.danger,
                fontSize = 11.sp)
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkChatPollDraft
import com.example.data.models.ChatMessage
import com.example.data.supabase.BlinkChatNativePollService
import com.example.ui.theme.MessagePalette
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
    Surface(color = palette.glassElevated,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.widthIn(min = 210.dp, max = 292.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("▥ " + actual.optString("question"), color = textColor,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(total.toString() + (if (total == 1) " vote" else " votes") +
                if (closed) " · Closed" else " · Tap to vote",
                color = palette.textSecondary, fontSize = 11.sp)
            for (index in 0 until (options?.length() ?: 0)) {
                val item = options?.optJSONObject(index) ?: continue
                val count = item.optInt("votes").coerceAtLeast(0)
                val percent = if (total > 0) count * 100 / total else 0
                Surface(
                    color = if (mine == index) palette.accent.copy(alpha = .17f)
                        else palette.glass,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp,
                        if (mine == index) palette.accent else palette.border),
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
                    }
                ) {
                    Column(Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row {
                            Text(item.optString("label"), color = textColor,
                                fontWeight = if (mine == index) FontWeight.Bold
                                    else FontWeight.Normal,
                                fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(percent.toString() + "%", color = palette.accent,
                                fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else count.toFloat() / total },
                            color = palette.accent,
                            trackColor = palette.border,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }
            if (mine >= 0) Text("Your choice is selected; you can change it.",
                color = palette.textSecondary, fontSize = 10.sp)
            if (sending) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(), color = palette.accent)
            if (error != null) Text(error.orEmpty(), color = palette.danger, fontSize = 11.sp)
        }
    }
}

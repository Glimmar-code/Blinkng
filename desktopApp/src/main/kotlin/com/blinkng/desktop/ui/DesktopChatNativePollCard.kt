package com.blinkng.desktop.ui

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
import com.blinkng.desktop.data.DesktopSupabaseClient
import com.blinkng.shared.BlinkChatReferenceStyle
import com.blinkng.shared.BlinkChatPollDraft
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Windows parity: results are server authoritative, never locally inferred. */
@Composable
internal fun DesktopChatNativePollCard(
    id: String,
    fallback: BlinkChatPollDraft?,
    client: DesktopSupabaseClient,
    onFallbackChoice: (Int) -> Unit
) {
    var result by remember(id) { mutableStateOf<JSONObject?>(null) }
    var waiting by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        if (runCatching { java.util.UUID.fromString(id) }.isSuccess) {
            runCatching { client.chatPollResults(id) }.onSuccess { result = it }
        }
    }
    val p = result
    if (p == null) {
        if (fallback != null) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("▥  " + fallback.question, fontWeight = FontWeight.Bold)
                Text("Text poll · replies are not native votes", fontSize = 11.sp)
                fallback.options.forEachIndexed { index, label ->
                    TextButton(onClick = { onFallbackChoice(index) }) {
                        Text((index + 1).toString() + ". " + label + " · Reply")
                    }
                }
            }
        } else Text("Poll results unavailable", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val options = p.optJSONArray("options")
    val total = p.optInt("total_votes").coerceAtLeast(0)
    val mine = if (p.isNull("my_vote")) -1 else p.optInt("my_vote", -1)
    val closed = p.optBoolean("closed")
    Surface(shape = RoundedCornerShape(14.dp), color = Color(BlinkChatReferenceStyle.Incoming),
        border = BorderStroke(1.dp, Color(BlinkChatReferenceStyle.Border)),
        modifier = Modifier.widthIn(max = 340.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("▥  " + p.optString("question"),
                fontWeight = FontWeight.Bold, color = Color.White)
            Text(total.toString() + " votes" + if (closed) " · Closed" else " · Select choice",
                fontSize = 11.sp, color = Color(BlinkChatReferenceStyle.Subtle))
            for (index in 0 until (options?.length() ?: 0)) {
                val item = options?.optJSONObject(index) ?: continue
                val count = item.optInt("votes").coerceAtLeast(0)
                Surface(
                    shape = RoundedCornerShape(9.dp), color = Color(BlinkChatReferenceStyle.Elevated),
                    border = BorderStroke(1.dp,
                        if (mine == index) Color(BlinkChatReferenceStyle.Blue) else Color(BlinkChatReferenceStyle.Border)),
                    modifier = Modifier.clickable(enabled = !closed && !waiting) {
                        waiting = true
                        scope.launch {
                            runCatching { client.chatVotePoll(id, index) }
                                .onSuccess { result = it; error = null }
                                .onFailure { error = "Could not save vote. Try again." }
                            waiting = false
                        }
                    }
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row {
                            Text(item.optString("label"),
                                color = Color.White, modifier = Modifier.weight(1f),
                                fontSize = 12.sp)
                            Text(if (total > 0) (count * 100 / total).toString() + "%" else "0%",
                                color = Color(BlinkChatReferenceStyle.BlueBright), fontSize = 11.sp)
                        }
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else count.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(BlinkChatReferenceStyle.Blue), trackColor = Color(BlinkChatReferenceStyle.Border))
                    }
                }
            }
            if (waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error.orEmpty(), color = Color(0xFFFFAAA0),
                fontSize = 11.sp)
        }
    }
}

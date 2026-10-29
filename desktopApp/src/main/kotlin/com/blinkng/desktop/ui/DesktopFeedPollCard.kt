package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.data.DesktopFeedPoll
import com.blinkng.desktop.data.DesktopSupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

/** Counts and selections come from the authenticated server, never the preview fixture. */
@Composable
internal fun DesktopFeedPollCard(postId: String, client: DesktopSupabaseClient) {
    var poll by remember(postId) { mutableStateOf<DesktopFeedPoll?>(null) }
    var loading by remember(postId) { mutableStateOf(true) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        try { poll = client.fetchFeedPoll(postId); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Poll unavailable." }
        finally { loading = false }
    }
    LaunchedEffect(postId) { refresh() }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text("Refresh poll") }
        }
        poll?.let { value ->
            val closed = value.expiresAt?.let { !Instant.now().isBefore(Instant.parse(it)) } ?: false
            val total = value.options.sumOf { it.votes.coerceAtLeast(0) }
            Text(value.question, fontWeight = FontWeight.Bold)
            value.options.forEach { option ->
                OutlinedButton(onClick = {
                    loading = true
                    scope.launch {
                        try { poll = client.voteFeedPoll(postId, option.id); error = null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Vote could not be confirmed. Refresh before retrying." }
                        finally { loading = false }
                    }
                }, enabled = !loading && !closed && value.votedOptionId == null, modifier = Modifier.fillMaxWidth()) {
                    Text((if (option.id == value.votedOptionId) "✓  " else "") + option.text, Modifier.weight(1f))
                    if (closed || value.votedOptionId != null) Text("${if (total == 0) 0 else option.votes * 100L / total}%")
                }
            }
            Text("$total votes${if (closed) " · Closed" else ""}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkMentionTokenPolicy
import com.blinkng.shared.BlinkUserSuggestionPolicy
import com.example.data.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared backend-driven suggestions used by the Create Post caption/text composer.
 * The server applies RLS, privacy filters and blocks; the UI only ranks visible rows.
 */
@Composable
fun BlinkPostMentionSuggestions(
    text: String,
    onTextChanged: (String) -> Unit,
    searchProfiles: (suspend (String, Int, String?, String?) -> List<UserProfile>)?,
    modifier: Modifier = Modifier
) {
    if (searchProfiles == null) return
    val token = remember(text) { BlinkMentionTokenPolicy.active(text) }
    var candidates by remember(token?.query) { mutableStateOf<List<UserProfile>>(emptyList()) }
    var loading by remember(token?.query) { mutableStateOf(false) }
    var visibleCount by remember(token?.query) { mutableIntStateOf(10) }
    var hasMoreServer by remember(token?.query) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token?.query) {
        val query = token?.query.orEmpty()
        if (query.isBlank()) return@LaunchedEffect
        delay(220)
        loading = true
        try {
            val result = searchProfiles(query, 60, null, null)
            candidates = result.distinctBy { it.id }
            hasMoreServer = result.size >= 60
        } catch (_: Exception) {
            candidates = emptyList()
            hasMoreServer = false
        } finally {
            loading = false
        }
    }

    if (token == null || token.query.isBlank()) return
    val ranked = BlinkUserSuggestionPolicy.rank(
        candidates, token.query,
        username = { it.username }, displayName = { it.fullName }
    )
    if (ranked.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Mention a BLINK student", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ranked.take(visibleCount), key = { it.id }) { person ->
                AssistChip(
                    onClick = {
                        val current = BlinkMentionTokenPolicy.active(text)
                        if (current != null) {
                            onTextChanged(
                                BlinkMentionTokenPolicy.replace(text, current, person.username)
                            )
                        }
                    },
                    label = {
                        Column {
                            Text(person.fullName.ifBlank { person.username }, maxLines = 1)
                            Text("@" + person.username, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
            if (ranked.size > visibleCount || hasMoreServer) {
                item(key = "more_mention_results") {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            if (ranked.size > visibleCount) {
                                visibleCount += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                            } else {
                                val last = candidates.lastOrNull()
                                if (last != null) {
                                    scope.launch {
                                        loading = true
                                        try {
                                            val next = searchProfiles(
                                                token.query, 60, last.username, last.id
                                            )
                                            candidates = (candidates + next).distinctBy { it.id }
                                            hasMoreServer = next.size >= 60
                                            visibleCount += BlinkUserSuggestionPolicy.DEFAULT_PAGE_SIZE
                                        } catch (_: Exception) {
                                            hasMoreServer = false
                                        } finally { loading = false }
                                    }
                                }
                            }
                        }
                    ) { Text("See more") }
                }
            }
        }
    }
}

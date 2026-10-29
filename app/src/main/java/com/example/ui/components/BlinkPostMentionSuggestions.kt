package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
    var hasSearched by remember(token?.query) { mutableStateOf(false) }
    var visibleCount by remember(token?.query) { mutableIntStateOf(7) }
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
            hasSearched = true
        }
    }

    if (token == null || token.query.isBlank()) return
    val ranked = BlinkUserSuggestionPolicy.rank(
        candidates, token.query,
        username = { it.username }, displayName = { it.fullName }
    )
    if (ranked.isEmpty()) {
        if (loading || hasSearched) {
            Surface(
                modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = Color(0xFF17191F)
            ) {
                Text(
                    if (loading) "Finding students..." else "No matching student accounts",
                    color = Color(0xFFD5D7DF), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Mention a BLINK student", color = Color(0xFFE8EAF1), style = MaterialTheme.typography.labelMedium)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ranked.take(visibleCount), key = { it.id }) { person ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color(0xFF17191F),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val current = BlinkMentionTokenPolicy.active(text)
                        if (current != null) {
                            onTextChanged(
                                BlinkMentionTokenPolicy.replace(text, current, person.username)
                            )
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = person.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(person.fullName.ifBlank { person.username }, color = Color.White, maxLines = 1)
                            Text("@" + person.username, color = Color(0xFFA2A9BC), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
                    ) { Text("See more", color = Color(0xFF8B81FF)) }
                }
            }
        }
    }
}

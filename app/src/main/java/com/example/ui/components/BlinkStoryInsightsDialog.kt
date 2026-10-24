package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.SupabaseService
import org.json.JSONObject

private data class StoryInsightPerson(
    val id: String,
    val username: String,
    val name: String,
    val visitedAt: String
)

/** No viewing history is cached across accounts or stored on the device. */
@Composable
fun BlinkStoryInsightsDialog(
    storyId: String,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    var loading by remember(storyId) { mutableStateOf(true) }
    var error by remember(storyId) { mutableStateOf<String?>(null) }
    var viewers by remember(storyId) { mutableStateOf<List<StoryInsightPerson>>(emptyList()) }
    var likers by remember(storyId) { mutableStateOf<List<StoryInsightPerson>>(emptyList()) }

    LaunchedEffect(storyId) {
        loading = true
        error = null
        runCatching { SupabaseService().myStoryInsights(storyId) }
            .onSuccess { json ->
                fun parse(key: String): List<StoryInsightPerson> {
                    val array = json.optJSONArray(key) ?: return emptyList()
                    return buildList {
                        for (index in 0 until array.length()) {
                            val row: JSONObject = array.optJSONObject(index) ?: continue
                            val id = row.optString("user_id")
                            val username = row.optString("username")
                            if (id.isBlank() || username.isBlank()) continue
                            add(StoryInsightPerson(
                                id = id,
                                username = username,
                                name = row.optString("name").ifBlank { username },
                                visitedAt = row.optString("viewed_at")
                            ))
                        }
                    }
                }
                viewers = parse("viewers")
                likers = parse("likers")
            }
            .onFailure {
                error = "Couldn't load viewer details. Please try again."
            }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Status insights",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Only the status owner can see viewer identities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    error != null -> Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        Text("Viewed by (${viewers.size})", fontWeight = FontWeight.SemiBold)
                        if (viewers.isEmpty()) {
                            Text("No views yet.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                items(viewers, key = { "v_${it.id}" }) { person ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                onDismiss()
                                                onProfileClick(person.username)
                                            }
                                            .padding(vertical = 7.dp)
                                    ) {
                                        Text(person.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            "@${person.username} · ${person.visitedAt.take(16).replace('T', ' ')} UTC",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("Liked by (${likers.size})", fontWeight = FontWeight.SemiBold)
                        if (likers.isEmpty()) {
                            Text("No likes yet.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                                items(likers, key = { "l_${it.id}" }) { person ->
                                    Text(
                                        "@${person.username}",
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            onDismiss()
                                            onProfileClick(person.username)
                                        }.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.SupabaseService
import com.example.data.repository.FollowStateStore
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class InsightPerson(
    val id: String,
    val name: String,
    val username: String,
    val visitedAt: String = "",
)

@Composable
fun BlinkStoryInsightsDialog(
    storyId: String,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()
    var pendingFollow by remember(storyId) { mutableStateOf<String?>(null) }
    var loading by remember(storyId) { mutableStateOf(true) }
    var error by remember(storyId) { mutableStateOf<String?>(null) }
    var viewers by remember(storyId) { mutableStateOf<List<InsightPerson>>(emptyList()) }
    var likers by remember(storyId) { mutableStateOf<List<InsightPerson>>(emptyList()) }

    LaunchedEffect(storyId) {
        FollowStateStore.refresh()
        loading = true
        error = null
        runCatching { SupabaseService().myStoryInsights(storyId) }
            .onSuccess { json ->
                fun read(key: String): List<InsightPerson> {
                    val array = json.optJSONArray(key) ?: return emptyList()
                    return buildList {
                        for (i in 0 until array.length()) {
                            val row = array.optJSONObject(i) ?: continue
                            val username = row.optString("username")
                            val id = row.optString("user_id")
                            if (id.isBlank() || username.isBlank()) continue
                            add(InsightPerson(
                                id, row.optString("name").ifBlank { username },
                                username, row.optString("viewed_at"),
                            ))
                        }
                    }
                }
                viewers = read("viewers")
                likers = read("likers")
            }.onFailure { error = it.message ?: "Unable to load story insights." }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Story Insights", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("Only you can view the identities below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Viewers (${viewers.size})", fontWeight = FontWeight.SemiBold)
                    if (viewers.isEmpty()) Text("No views yet.")
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        items(viewers, key = { "v_${it.id}" }) { person ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                        .clickable { onDismiss(); onProfileClick(person.username) }
                                ) {
                                    Text(person.name, fontWeight = FontWeight.SemiBold)
                                    Text("@${person.username} · ${person.visitedAt.take(16).replace('T', ' ')} UTC",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (person.id in followingIds) {
                                    OutlinedButton(
                                        onClick = { onDismiss(); onProfileClick(person.username) },
                                    ) { Text("Following") }
                                } else {
                                    Button(
                                        enabled = pendingFollow == null,
                                        onClick = {
                                            pendingFollow = person.id
                                            scope.launch {
                                                val saved = runCatching {
                                                    FollowStateStore.setFollowing(person.id, true)
                                                }.getOrDefault(false)
                                                if (!saved) error = "Could not follow @${person.username}."
                                                pendingFollow = null
                                            }
                                        },
                                    ) { Text(if (pendingFollow == person.id) "..." else "Follow") }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Liked by (${likers.size})", fontWeight = FontWeight.SemiBold)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
                        items(likers, key = { "l_${it.id}" }) { person ->
                            Text("@${person.username}", modifier = Modifier.fillMaxWidth()
                                .clickable { onDismiss(); onProfileClick(person.username) }
                                .padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

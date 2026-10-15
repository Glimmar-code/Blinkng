package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.models.FeedPost
import com.example.data.repository.FollowStateStore
import com.example.data.repository.PostRepository
import com.example.data.supabase.BlinkEconomyService
import com.example.ui.components.PostCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Paid discovery is separated from organic ranking and always marked.
 * Content campaigns hydrate an authenticated FeedPost rather than displaying
 * the shortened boost payload as a made-up user post.
 */
@Composable
internal fun BlinkPromotedDiscoveryRail(
    surface: String,
    currentUsername: String,
    isDark: Boolean,
    onProfileClick: (String) -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onVotePoll: (String, String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = remember { BlinkEconomyService() }
    val repository = remember { PostRepository() }
    val scope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()
    var placements by remember(surface) {
        mutableStateOf<List<BlinkPromotedDiscoveryPlacement>>(emptyList())
    }

    LaunchedEffect(surface) {
        service.promotedBoostSlots(surface, 5)
            .onSuccess { payload ->
                placements = parseBlinkPromotedPlacements(payload)
                    .filter {
                        it.targetType == "PROFILE" ||
                            (it.targetType in setOf("POST", "REEL") && it.targetId.isNotBlank())
                    }
            }
            .onFailure { placements = emptyList() }
    }
    if (placements.isEmpty()) return

    val profiles = placements.filter { it.targetType == "PROFILE" }
    val content = placements.filter { it.targetType == "POST" || it.targetType == "REEL" }.take(2)

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(7.dp))
            Text("Promoted", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text("Sponsored discovery", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }

        if (profiles.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { "profile-boost-" + it.campaignId }) { placement ->
                    LaunchedEffect(placement.campaignId) {
                        service.recordBoostDelivery(placement.campaignId, "IMPRESSION", surface)
                    }
                    Card(
                        onClick = {
                            scope.launch {
                                service.recordBoostDelivery(placement.campaignId, "CARD_OPEN", surface)
                            }
                            onProfileClick(placement.ownerUsername)
                        },
                        modifier = Modifier.width(240.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Promoted profile", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(placement.ownerName.ifBlank { placement.ownerUsername }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${placement.ownerUsername}", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            placement.profileBio?.takeIf(String::isNotBlank)?.let {
                                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        content.forEach { placement ->
            var livePost by remember(placement.campaignId) { mutableStateOf<FeedPost?>(null) }
            var loading by remember(placement.campaignId) { mutableStateOf(true) }
            var retry by remember(placement.campaignId) { mutableIntStateOf(0) }

            LaunchedEffect(placement.targetId, retry) {
                loading = true
                livePost = runCatching { repository.fetchPostById(placement.targetId) }.getOrNull()
                loading = false
            }
            livePost?.let { resolved ->
                LaunchedEffect(placement.campaignId, resolved.id) {
                    service.recordBoostDelivery(placement.campaignId, "IMPRESSION", surface)
                }
                val post = resolved.copy(isSponsored = true, adLabel = "Promoted")
                Column {
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = {
                            onLikePost(post.id)
                            scope.launch {
                                delay(350)
                                runCatching { repository.fetchPostById(post.id) }.getOrNull()?.let { livePost = it }
                            }
                        },
                        onComment = { onCommentPost(post.id) },
                        onBookmark = {
                            onBookmarkPost(post.id)
                            scope.launch {
                                delay(350)
                                runCatching { repository.fetchPostById(post.id) }.getOrNull()?.let { livePost = it }
                            }
                        },
                        onShare = { onSharePost(post.id) },
                        onVotePoll = { id, option ->
                            onVotePoll(id, option)
                            scope.launch {
                                delay(350)
                                runCatching { repository.fetchPostById(post.id) }.getOrNull()?.let { livePost = it }
                            }
                        },
                        onOptionsClick = { onOptionsClick(post) },
                        onProfileClick = onProfileClick,
                        isAuthor = post.authorUsername.equals(currentUsername, true),
                        authorProfileId = post.authorProfileId,
                        isFollowingAuthor = post.authorProfileId in followingIds,
                        onFollowAuthor = { id -> scope.launch { FollowStateStore.setFollowing(id, true) } },
                        onUnfollowAuthor = { id -> scope.launch { FollowStateStore.setFollowing(id, false) } },
                        onDelete = { onDeletePost(post.id) },
                        trackExposure = false,
                        xFeedStyle = true,
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                service.recordBoostDelivery(placement.campaignId, "CARD_OPEN", surface)
                            }
                            onPostClick(post)
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(if (post.isReel) "Watch reel" else "Open post")
                    }
                }
            } ?: if (loading) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text("Loading promoted post…", modifier = Modifier.padding(16.dp))
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Promoted post unavailable", modifier = Modifier.weight(1f))
                        TextButton(onClick = { retry++ }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

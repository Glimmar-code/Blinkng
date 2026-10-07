package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.ProfileConnectionKind
import com.example.data.models.ProfileNotificationMode
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileConnectionsSheet(
    kind: ProfileConnectionKind,
    profiles: List<UserProfile>,
    loading: Boolean,
    followingIds: Set<String>,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onFollowToggle: (UserProfile, Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (kind == ProfileConnectionKind.FOLLOWERS) "Followers" else "Following",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
                profiles.isEmpty() -> {
                    Text(
                        if (kind == ProfileConnectionKind.FOLLOWERS) "No followers to show yet." else "Not following anyone yet.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(profiles, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.avatarUrl,
                                    contentDescription = item.fullName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.fullName.ifBlank { item.username },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.verificationBadge != com.example.data.models.VerificationBadge.NONE) {
                                            Spacer(Modifier.width(4.dp))
                                            VerifiedMark(item.verificationBadge, size = 14.dp)
                                        }
                                    }
                                    Text(
                                        "@${item.username}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    item.university.takeIf { it.isNotBlank() }?.let {
                                        Text(it, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                TextButton(onClick = { onOpenProfile(item.username) }) {
                                    Text("View")
                                }
                                val following = item.id in followingIds
                                OutlinedButton(onClick = { onFollowToggle(item, !following) }) {
                                    Text(if (following) "Following" else "Follow", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ProfileContentSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close profile search")
            }
        },
        placeholder = { Text("Search this profile's posts and reels") }
    )
}

@Composable
fun ProfileNotificationPreferenceDialog(
    selected: ProfileNotificationMode,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ProfileNotificationMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Profile notifications", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    ProfileNotificationMode.ALL to "All posts and reels",
                    ProfileNotificationMode.REELS to "Reels only",
                    ProfileNotificationMode.IMPORTANT to "Important updates",
                    ProfileNotificationMode.OFF to "Off"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == mode,
                            onClick = { if (!saving) onSelect(mode) }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(if (saving) "Saving…" else "Done")
            }
        }
    )
}

@Composable
fun ProfileReportDialog(
    reason: String,
    saving: Boolean,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Report profile", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose the reason that best describes the problem.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                listOf("Spam or scam", "Harassment", "Impersonation", "Inappropriate content", "Other").forEach { option ->
                    FilterChip(
                        selected = reason == option,
                        onClick = { if (!saving) onReasonChange(option) },
                        label = { Text(option) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = reason.isNotBlank() && !saving
            ) { Text(if (saving) "Sending…" else "Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
fun ProfileOwnerInsightsCard(
    profile: UserProfile,
    posts: List<FeedPost>,
    modifier: Modifier = Modifier
) {
    val owned = posts.filter {
        it.author.equals(profile.username, ignoreCase = true) ||
            it.authorUsername.equals(profile.username, ignoreCase = true)
    }
    val contentViews = owned.sumOf { it.viewsCount.toLong() }
    val likes = owned.sumOf { it.likes.toLong() }
    val comments = owned.sumOf { it.commentsCount.toLong() }
    val shares = owned.sumOf { it.sharesCount.toLong() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Private profile insights", fontWeight = FontWeight.Black, fontSize = 15.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InsightMetric("Profile views", profile.profileViewsThisWeek.toLong(), "7d")
                InsightMetric("Content views", contentViews, "all")
                InsightMetric("Likes", likes, "all")
                InsightMetric("Comments", comments, "all")
                InsightMetric("Shares", shares, "all")
            }
            Text(
                "Only you can see these analytics.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsightMetric(label: String, value: Long, window: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 14.sp, color = BlinkPink)
        Text(label, fontSize = 8.5.sp, maxLines = 1)
        Text(window, fontSize = 7.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

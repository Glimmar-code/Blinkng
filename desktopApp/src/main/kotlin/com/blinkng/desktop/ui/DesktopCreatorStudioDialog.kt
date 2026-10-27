package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.data.DesktopFeedPost
import com.blinkng.desktop.data.DesktopProfile
import com.blinkng.shared.BlinkCreatorItem
import com.blinkng.shared.BlinkCreatorStudioPolicy
import com.blinkng.shared.BlinkCreatorStudioSummary
import androidx.compose.ui.window.Dialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale

/**
 * Desktop parity for Android's Creator Studio.
 * Uses the SAME shared aggregation and authenticated profile/content feed;
 * not an invented audience, view history or reach prediction.
 */
@Composable
internal fun DesktopCreatorStudioDialog(
    profile: DesktopProfile,
    posts: List<DesktopFeedPost>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    val summary = BlinkCreatorStudioPolicy.summarize(
        posts.filter { it.userId == profile.id }.map { post ->
            BlinkCreatorItem(
                id = post.id,
                caption = post.caption.orEmpty().ifBlank { post.text.orEmpty() },
                isReel = post.isReel,
                qualifiedViews = post.viewCount,
                likes = post.likeCount,
                comments = post.commentCount,
                shares = post.shareCount,
                saves = null, // Desktop feed does not expose aggregate saves.
                tags = post.hashtags,
            )
        }
    )
    val tier = profile.verificationTier.uppercase()
    val member = profile.isVerified && tier in setOf("BLUE", "GOLD")
    val gold = member && tier == "GOLD"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(.78f).heightIn(max = 740.dp)) {
            LazyColumn(
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("BLINK Creator Studio", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text(if (gold) "Gold creator tools"
                                else if (member) "Blue creator tools" else "Preview creator tools",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
                item {
                    Text(
                        "Loaded-content snapshot only: ${summary.contentCount} original items. " +
                            "Views are qualified BLINK views, not unique people. Save totals " +
                            "are unavailable on Windows and are excluded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DesktopCreatorStat("Posts", summary.postCount.toLong(), Modifier.weight(1f))
                        DesktopCreatorStat("Reels", summary.reelCount.toLong(), Modifier.weight(1f))
                        DesktopCreatorStat("Views", summary.qualifiedViews, Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DesktopCreatorStat("Likes", summary.likes, Modifier.weight(1f))
                        DesktopCreatorStat("Comments", summary.comments, Modifier.weight(1f))
                        DesktopCreatorStat("Shares", summary.shares, Modifier.weight(1f))
                    }
                }
                if (!member) {
                    item {
                        Text("Blue adds performance insights and top content. " +
                            "Gold adds hashtag insights and a copyable report.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Open Coins & Wallet to explore verification. " +
                            "Paid status is not government identity verification.")
                    }
                } else {
                    item {
                        Text("Content performance", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Interactions per 100 qualified views: " +
                            String.format(Locale.US, "%.1f", summary.interactionsPer100Views))
                        Text("Desktop does not report saves, so this excludes save interactions.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Text("Top posts and Reels", fontWeight = FontWeight.Bold)
                    }
                    if (summary.topContent.isEmpty()) {
                        item { Text("No loaded content to compare.") }
                    } else {
                        items(summary.topContent, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.title, modifier = Modifier.weight(1f), maxLines = 2)
                                Spacer(Modifier.width(10.dp))
                                Text("${item.qualifiedViews} views")
                            }
                        }
                    }
                    if (gold) {
                        item {
                            Text("Top hashtags", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                        }
                        if (summary.topTags.isEmpty()) {
                            item { Text("No hashtags in loaded content.") }
                        } else {
                            items(summary.topTags, key = { it.tag }) { tag ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("#${tag.tag} · ${tag.contentCount} items")
                                    Text("${tag.qualifiedViews} views")
                                }
                            }
                        }
                        item {
                            var copyError: String? = null
                            OutlinedButton(onClick = {
                                try {
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                        StringSelection(desktopCreatorReport(summary)), null)
                                } catch (_: Exception) {
                                    copyError = "Clipboard unavailable"
                                }
                            }) { Text("Copy Gold performance report") }
                        }
                    } else {
                        item {
                            Text("Gold adds hashtag analysis and a copyable performance report.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onRefresh, enabled = !loading) {
                        Text(if (loading) "Refreshing…" else "Refresh profile content")
                    }
                }
            }
        }
    }
}

private fun desktopCreatorReport(summary: BlinkCreatorStudioSummary): String = buildString {
    appendLine("BLINK Creator Studio · loaded-content snapshot")
    appendLine("${summary.postCount} posts · ${summary.reelCount} Reels")
    appendLine("${summary.qualifiedViews} qualified views · ${summary.likes} likes · " +
        "${summary.comments} comments · ${summary.shares} shares")
    appendLine("Interactions per 100 qualified views (saves unavailable): " +
        String.format(Locale.US, "%.1f", summary.interactionsPer100Views))
    summary.topContent.forEach { appendLine("${it.title}: ${it.qualifiedViews} views") }
    summary.topTags.forEach { appendLine("#${it.tag}: ${it.qualifiedViews} views") }
    appendLine("Based on loaded content, not full history or unique people.")
}

@Composable
private fun DesktopCreatorStat(title: String, n: Long, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp)) {
            Text(n.toString(), fontWeight = FontWeight.Bold)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.BlinkCreatorStudioAnalytics
import com.example.data.models.BlinkCreatorStudioSummary
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import java.text.NumberFormat
import java.util.Locale

/**
 * Creator Studio uses loaded, real post/reel aggregates only. It never invents
 * unique visitors, watch retention, posting-time recommendations or followers.
 * Essential counts remain visible for free. Derived breakdowns are an optional
 * membership presentation feature, not a server-side data access control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlinkCreatorStudioSheet(
    profile: UserProfile,
    posts: List<FeedPost>,
    postsLoading: Boolean,
    onDismiss: () -> Unit,
    onVerify: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPost: (String) -> Unit
) {
    val summary = remember(posts) { BlinkCreatorStudioAnalytics.summarize(posts) }
    val blueOrGold = profile.hasActiveMarketVerification()
    val isGold = blueOrGold && profile.verificationBadge == VerificationBadge.GOLD
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("blink_creator_studio_sheet")
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Creator Studio", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (isGold) "Gold creator tools" else if (blueOrGold) "Blue creator tools"
                            else "Preview your creator tools",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.Insights, contentDescription = null)
                }
            }
            item {
                Text(
                    "Figures cover only ${summary.contentCount} currently loaded original posts and Reels. " +
                        "Views are BLINK's qualified view counts, not unique people. " +
                        "Refresh your profile to update this snapshot.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (postsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorStudioStat("Posts", summary.postCount.toLong(), Modifier.weight(1f))
                    CreatorStudioStat("Reels", summary.reelCount.toLong(), Modifier.weight(1f))
                    CreatorStudioStat("Views", summary.qualifiedViews, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreatorStudioStat("Likes", summary.likes, Modifier.weight(1f))
                    CreatorStudioStat("Comments", summary.comments, Modifier.weight(1f))
                    CreatorStudioStat("Shares", summary.shares, Modifier.weight(1f))
                }
            }
            if (!blueOrGold) {
                item {
                    StudioFeatureCard(
                        title = "Unlock performance insights",
                        description = "Blue members can compare content, see interaction breakdowns " +
                            "and discover their strongest loaded posts. Gold adds hashtag insights " +
                            "and a shareable content report."
                    ) {
                        Button(onClick = { onDismiss(); onVerify() },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Verified, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Explore Blue & Gold Verified")
                        }
                    }
                }
            } else {
                item {
                    StudioFeatureCard(
                        title = "Content performance",
                        description = "Interactions are likes, comments, shares and any reported saves. " +
                            "This is not a prediction of reach."
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Interactions per 100 qualified views",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.1f", summary.interactionsPer100Views),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Saves reported", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatStudioNumber(summary.knownSaves),
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        if (summary.contentsWithSaveCount < summary.contentCount) {
                            Text("Save counts missing for ${summary.contentCount - summary.contentsWithSaveCount} " +
                                "loaded items; those saves are excluded.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Text("Top content by qualified views", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                }
                if (summary.topContent.isEmpty()) {
                    item {
                        Text(
                            if (postsLoading) "Loading published content…" else "No published posts loaded yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(summary.topContent, key = { it.id }) { metric ->
                        OutlinedCard(
                            onClick = { onDismiss(); onOpenPost(metric.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (metric.isReel) Icons.Default.SmartDisplay
                                    else Icons.Default.Article, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(metric.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        "${formatStudioNumber(metric.qualifiedViews)} views · " +
                                            "${formatStudioNumber(metric.interactions)} interactions",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open content")
                            }
                        }
                    }
                }
                if (!isGold) {
                    item {
                        StudioFeatureCard(
                            title = "Grow with Gold",
                            description = "Add real hashtag-performance insights and share a " +
                                "snapshot of your loaded content performance."
                        ) {
                            OutlinedButton(
                                onClick = { onDismiss(); onVerify() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Explore Gold Verified")
                            }
                        }
                    }
                } else {
                    item {
                        Text("Your strongest hashtags", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("Sorted by qualified views across loaded content with each tag.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (summary.topTags.isEmpty()) {
                        item {
                            Text("No hashtags found in loaded content.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(summary.topTags, key = { it.tag }) { tag ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("#${tag.tag} · ${tag.contentCount} items",
                                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text("${formatStudioNumber(tag.qualifiedViews)} views",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = {
                            val text = creatorStudioReport(summary)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(share, "Share Creator Studio report")
                                )
                            } catch (_: Exception) {
                                // No installed share handler; preserve the current screen.
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share Gold content report")
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !postsLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (postsLoading) "Updating…" else "Refresh profile content")
                }
            }
        }
    }
}

private fun formatStudioNumber(n: Long): String = NumberFormat.getIntegerInstance().format(n)

private fun creatorStudioReport(summary: BlinkCreatorStudioSummary): String = buildString {
    appendLine("BLINK Creator Studio · loaded-content snapshot")
    appendLine("${summary.postCount} posts · ${summary.reelCount} Reels")
    appendLine("${summary.qualifiedViews} qualified views · ${summary.likes} likes · " +
        "${summary.comments} comments · ${summary.shares} shares")
    appendLine("Reported saves: ${summary.knownSaves} " +
        "(${summary.contentsWithSaveCount}/${summary.contentCount} items supplied save counts)")
    appendLine("Interactions per 100 qualified views: " +
        String.format(Locale.US, "%.1f", summary.interactionsPer100Views))
    appendLine("Top content:")
    summary.topContent.forEach { appendLine("• ${it.title} — ${it.qualifiedViews} views") }
    appendLine("Top tags:")
    summary.topTags.forEach { appendLine("• #${it.tag} — ${it.qualifiedViews} views") }
    appendLine("This is a snapshot of loaded items, not lifetime totals or unique viewers.")
}

@Composable
private fun CreatorStudioStat(label: String, value: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(formatStudioNumber(value), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 1)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudioFeatureCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

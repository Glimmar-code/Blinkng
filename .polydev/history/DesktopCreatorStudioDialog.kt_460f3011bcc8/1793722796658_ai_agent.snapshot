package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onUseAiPrompt: (String) -> Unit = {},
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
    // Honor the server's verified flag and any explicit expiry returned by the profile API.
    // Legacy profile projections without expiry keep the prior server-verified fallback.
    val notExpired = profile.verificationExpiresAtMillis == 0L ||
        profile.verificationExpiresAtMillis > System.currentTimeMillis()
    val member = profile.isVerified && notExpired && tier in setOf("BLUE", "GOLD")
    val gold = member && tier == "GOLD"
    var clipboardStatus by remember { mutableStateOf<String?>(null) }
    var promptTopic by remember(profile.id) { mutableStateOf("") }
    var promptAudience by remember(profile.id) { mutableStateOf("students") }
    var promptKind by remember(profile.id) { mutableStateOf("Post ideas") }
    var eventName by remember(profile.id) { mutableStateOf("") }
    var eventTime by remember(profile.id) { mutableStateOf("") }
    var eventVenue by remember(profile.id) { mutableStateOf("") }
    var eventNotice by remember(profile.id) { mutableStateOf<String?>(null) }
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
                            OutlinedButton(onClick = {
                                try {
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                        StringSelection(desktopCreatorReport(summary)), null)
                                    clipboardStatus = "Report copied"
                                } catch (_: Exception) {
                                    clipboardStatus = "Clipboard unavailable on this device"
                                }
                            }) { Text("Copy Gold performance report") }
                            clipboardStatus?.let { status ->
                                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        item {
                            Text("Gold adds hashtag analysis and a copyable performance report.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (member) {
                    item {
                        HorizontalDivider()
                        Text("BLINK AI creator workspace", fontWeight = FontWeight.Bold)
                        Text("Prepare a prompt for the existing BLINK AI. You choose when to Send; " +
                            "service availability and existing limits still apply.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(promptTopic, { promptTopic = it.take(160) },
                            label = { Text("Creative topic") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(promptAudience, { promptAudience = it.take(80) },
                            label = { Text("Audience") }, modifier = Modifier.fillMaxWidth())
                    }
                    items(com.blinkng.shared.BlinkVerifiedCreatorTools.promptKinds) { kind ->
                        FilterChip(selected = promptKind == kind, onClick = { promptKind = kind },
                            label = { Text(kind) })
                    }
                    item {
                        Button(onClick = {
                            val prompt = com.blinkng.shared.BlinkVerifiedCreatorTools.prompt(
                                promptKind, promptTopic, promptAudience)
                            onDismiss()
                            onUseAiPrompt(prompt)
                        }) { Text("Open this brief in BLINK AI") }
                    }
                    if (gold) {
                        item {
                            HorizontalDivider()
                            Text("Gold Event Organizer", fontWeight = FontWeight.Bold)
                            Text("Share a validated invitation draft. No RSVP collection or automatic publishing.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(eventName, { eventName = it.take(120) },
                                label = { Text("Event name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(eventTime, { eventTime = it.take(120) },
                                label = { Text("Confirmed date and time") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(eventVenue, { eventVenue = it.take(160) },
                                label = { Text("Confirmed venue / online format") }, modifier = Modifier.fillMaxWidth())
                            eventNotice?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = {
                                try {
                                    val invite = com.blinkng.shared.BlinkVerifiedCreatorTools.eventInvite(
                                        eventName, eventTime, eventVenue, promptAudience)
                                    Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(StringSelection(invite), null)
                                    eventNotice = "Event invitation copied for review and sharing"
                                } catch (e: IllegalArgumentException) {
                                    eventNotice = e.message
                                } catch (_: Exception) {
                                    eventNotice = "Clipboard unavailable"
                                }
                            }) { Text("Copy event invitation") }
                        }
                        item {
                            HorizontalDivider()
                            Text("Gold Creator Portfolio", fontWeight = FontWeight.Bold)
                            val portfolio = com.blinkng.shared.BlinkVerifiedCreatorTools.portfolio(
                                profile.username, profile.fullName,
                                profile.professionalHeadline.orEmpty(), profile.bio.orEmpty(),
                                summary.topTags.firstOrNull()?.tag
                            )
                            Text(portfolio)
                            OutlinedButton(onClick = {
                                try {
                                    Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(StringSelection(portfolio), null)
                                    clipboardStatus = "Portfolio copied"
                                } catch (_: Exception) { clipboardStatus = "Clipboard unavailable" }
                            }) { Text("Copy public portfolio") }
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

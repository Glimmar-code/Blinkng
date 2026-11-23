package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.broadcast.*
import kotlinx.coroutines.launch

/**
 * Windows presentation of the shared broadcast contract. It intentionally
 * shows an integration hold rather than fabricated users, offers or rewards.
 */
@Composable
fun BlinkDesktopBroadcastScreen(state: DesktopAppState) {
    var screen by remember { mutableStateOf("inbox") }
    var broadcasts by remember { mutableStateOf<List<Broadcast>>(emptyList()) }
    var inbox by remember { mutableStateOf<List<BroadcastInboxItem>>(emptyList()) }
    var prefs by remember { mutableStateOf(BroadcastNotificationPrefs()) }
    var campaign by remember { mutableStateOf<Broadcast?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var adminDraft by remember { mutableStateOf(BroadcastDraft()) }
    var confirmEveryone by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val repository = state.broadcastRepository

    fun refresh() {
        loading = true
        scope.launch {
            runCatching {
                broadcasts = repository.getActiveBroadcasts()
                inbox = repository.getInbox()
                prefs = repository.getPrefs()
            }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(state.selectedBroadcastId, broadcasts) {
        state.selectedBroadcastId?.let { id ->
            broadcasts.firstOrNull { it.id == id }?.let { selected ->
                campaign = selected.takeIf { it.type == BroadcastType.CAMPAIGN }
                if (campaign != null) screen = "campaign" else screen = "inbox"
            }
        }
    }
    fun perform(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action(); refresh() }.onFailure { error = it.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (screen != "inbox") IconButton(onClick = { screen = "inbox" }) {
                Icon(Icons.Rounded.ArrowBack, "Back")
            }
            Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (screen) {
                        "settings" -> "Announcement preferences"
                        "admin" -> "Broadcast administration"
                        "campaign" -> campaign?.title ?: "Campaign"
                        else -> "Announcements"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Shared BLINK announcement, campaign and promotion contract",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { screen = "settings" }) {
                Icon(Icons.Rounded.Settings, "Notification settings")
            }
            if (state.adminCapability.allowed) {
                TextButton(onClick = { screen = "admin" }) { Text("Broadcast admin") }
            }
        }
        if (loading) {
            repeat(2) {
                Surface(Modifier.fillMaxWidth().height(95.dp), shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {}
            }
        } else if (error != null) {
            BroadcastDesktopMessage(
                title = "Broadcast backend not connected",
                message = "No sample notices or rewards are shown in the Windows app. Connect the server adapter to load real content.",
                action = "Retry",
                onAction = ::refresh,
            )
        } else when (screen) {
            "settings" -> {
                DesktopPref("Push notifications", prefs.pushEnabled) { prefs = prefs.copy(pushEnabled = it); perform { repository.updatePrefs(prefs) } }
                DesktopPref("Announcements", prefs.announcements) { prefs = prefs.copy(announcements = it); perform { repository.updatePrefs(prefs) } }
                DesktopPref("Campaigns", prefs.campaigns) { prefs = prefs.copy(campaigns = it); perform { repository.updatePrefs(prefs) } }
                DesktopPref("Offers", prefs.offers) { prefs = prefs.copy(offers = it); perform { repository.updatePrefs(prefs) } }
                DesktopPref("Email", prefs.emailEnabled) { prefs = prefs.copy(emailEnabled = it); perform { repository.updatePrefs(prefs) } }
                DesktopPref("Quiet hours", prefs.quietHoursEnabled) { prefs = prefs.copy(quietHoursEnabled = it); perform { repository.updatePrefs(prefs) } }
                if (prefs.quietHoursEnabled) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(prefs.quietHoursStart, { prefs = prefs.copy(quietHoursStart = it.take(5)) }, label = { Text("Start") })
                    OutlinedTextField(prefs.quietHoursEnd, { prefs = prefs.copy(quietHoursEnd = it.take(5)) }, label = { Text("End") })
                    Button(onClick = { perform { repository.updatePrefs(prefs) } }) { Text("Save preferences") }
                }
            }
            "campaign" -> campaign?.let { item ->
                BroadcastDesktopCard(item,
                    onOpen = {},
                    onAction = {
                        if (item.joined) perform { repository.getCampaignProgress(item.id) }
                        else perform { repository.joinCampaign(item.id) }
                    },
                    actionLabel = if (item.joined) "View progress" else "Join campaign",
                )
                Text(item.body, style = MaterialTheme.typography.bodyLarge)
                Text("Progress · ${item.progress} / ${item.target ?: item.rules.target ?: 0}")
                LinearProgressIndicator(
                    progress = {
                        val target = (item.target ?: item.rules.target ?: 0).coerceAtLeast(1)
                        (item.progress.toFloat() / target).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                item.rules.rewardTiers.forEach { Text("${it.threshold} · ${it.reward}") }
                Button(onClick = { perform { repository.getLeaderboard(item.id) } }) { Text("Load top 20") }
            }
            "admin" -> {
                if (!state.adminCapability.allowed) {
                    BroadcastDesktopMessage("Administrator access required", "This route is limited to verified BLINK administrators.")
                } else {
                    Text("Admin authoring is in migration until the shared Supabase broadcast API is connected.")
                    Text("Sample data is not sent or displayed as a live audience.")
                    OutlinedTextField(adminDraft.title, { adminDraft = adminDraft.copy(title = it.take(60)) }, label = { Text("Title") })
                    OutlinedTextField(adminDraft.body, { adminDraft = adminDraft.copy(body = it.take(240)) },
                        label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(confirmEveryone, { confirmEveryone = it.take(7) },
                        label = { Text("Type CONFIRM for everyone") })
                    Button(enabled = adminDraft.audience.kind != BroadcastAudienceKind.EVERYONE || confirmEveryone == "CONFIRM",
                        onClick = { perform { repository.createBroadcast(adminDraft) } }) {
                        Text("Save draft")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(broadcasts, key = { it.id }) { item ->
                            BroadcastDesktopCard(item, onOpen = { campaign = item; screen = "campaign" },
                                onAction = { perform { repository.endBroadcast(item.id) } }, actionLabel = "End now")
                        }
                    }
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Announcements", "Campaigns", "Offers").forEachIndexed { index, label ->
                        FilterChip(tab == index, { tab = index }, label = { Text(label) })
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { perform { repository.markAllRead() } }) { Text("Mark all as read") }
                }
                val visibleInbox = inbox.filter { tab == 0 || it.broadcast.type.ordinal == tab - 1 }
                if (visibleInbox.isEmpty() && broadcasts.isEmpty()) {
                    BroadcastDesktopMessage(
                        "No announcements yet",
                        "When the backend is connected, real BLINK updates and offers will appear here.",
                    )
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(broadcasts.filter { tab == 0 || it.type.ordinal == tab - 1 }, key = { it.id }) { item ->
                        BroadcastDesktopCard(
                            item,
                            onOpen = { if (item.type == BroadcastType.CAMPAIGN) { campaign = item; screen = "campaign" } },
                            onAction = {
                                when (item.type) {
                                    BroadcastType.ANNOUNCEMENT -> perform { repository.markRead(item.id) }
                                    BroadcastType.CAMPAIGN -> if (item.joined) { campaign = item; screen = "campaign" }
                                    else -> perform { repository.joinCampaign(item.id) }
                                    BroadcastType.PROMO -> perform { repository.claimPromo(item.id) }
                                }
                            },
                            actionLabel = when (item.type) {
                                BroadcastType.ANNOUNCEMENT -> item.ctaLabel.ifBlank { "Read" }
                                BroadcastType.CAMPAIGN -> if (item.joined) "View progress" else "Join campaign"
                                BroadcastType.PROMO -> when (item.claimState) {
                                    BroadcastClaimState.AVAILABLE -> "Claim offer"
                                    BroadcastClaimState.CLAIMING -> "Claiming…"
                                    BroadcastClaimState.CLAIMED -> "Claimed"
                                    BroadcastClaimState.LIMIT_REACHED -> "Limit reached"
                                    BroadcastClaimState.EXPIRED -> "Expired"
                                    BroadcastClaimState.SOLD_OUT -> "Sold out"
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastDesktopCard(
    item: Broadcast,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    actionLabel: String,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().background(
                if (item.type == BroadcastType.CAMPAIGN) Brush.linearGradient(
                    listOf(Color(0xFF182F54), Color(0xFF6741AE), Color(0xFF9E426E))
                ) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            ).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.type.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item.imageUrl?.takeIf { it.startsWith("https://") }?.let {
                AsyncImage(it, contentDescription = item.title, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (item.isPreview) Text("PREVIEW ONLY · no real reward", color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.weight(1f))
                if (actionLabel.isNotBlank()) Button(onClick = onAction) { Text(actionLabel) }
                if (item.type == BroadcastType.CAMPAIGN) TextButton(onClick = onOpen) { Text("Details") }
            }
        }
    }
}

@Composable
private fun DesktopPref(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun BroadcastDesktopMessage(
    title: String,
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

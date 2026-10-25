package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralStats
import com.blinkng.shared.discovery.BlinkReferralCoinStatus
import com.blinkng.shared.discovery.BlinkReferralSourceStats
import com.blinkng.shared.discovery.BlinkReferralGrowthStats
import com.blinkng.shared.discovery.BlinkReferralLeaderboardEntry
import kotlinx.coroutines.launch
import com.example.data.supabase.BlinkReferralClient

@Composable
internal fun BlinkReferGrowDialog(username: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<BlinkReferralStats?>(null) }
    var growth by remember { mutableStateOf<BlinkReferralGrowthStats?>(null) }
    var coins by remember { mutableStateOf<BlinkReferralCoinStatus?>(null) }
    var sources by remember { mutableStateOf<BlinkReferralSourceStats?>(null) }
    var linkStats by remember { mutableStateOf<com.blinkng.shared.discovery.BlinkReferralLinkStats?>(null) }
    var leaders by remember { mutableStateOf<List<BlinkReferralLeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        runCatching { BlinkReferralClient.dashboard() }
            .onSuccess { stats = it }
            .onFailure { error = "Referral statistics are unavailable until the server update is ready." }
        growth = runCatching { BlinkReferralClient.growthStats() }.getOrNull()
        coins = runCatching { BlinkReferralClient.coinRewardStatus() }.getOrNull()
        sources = runCatching { BlinkReferralClient.sourceStats() }.getOrNull()
        linkStats = runCatching { BlinkReferralClient.linkStats() }.getOrNull()
        leaders = runCatching { BlinkReferralClient.leaderboard() }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLINK Refer & Grow") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BlinkReferralPolicy.PROMO_MESSAGE)
                if (stats == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                stats?.let {
                    Text("Lifetime confirmed referrals: ${it.total}")
                    Text("Achievement: ${it.achievementTier}")
                    Text(BlinkReferralPolicy.milestoneLabel(it))
                    Text("Active referrals (last 30 days): ${it.activeLast30Days}")
                    Text("Current visibility: ${it.visibilityLabel} (${it.visibilityPoints} points)")
                    Text("Each referral adds 3 points for 10 days, then 2 for 10 days, then 1 for 10 days. The boost expires after 30 days, but achievements remain.")
                    Text("No guaranteed views, follows or Coins.")
                }
                growth?.let {
                    Text("Real profile views this week: ${it.profileViewsThisWeek}")
                    Text("Followers gained this week: ${it.followersGainedThisWeek}")
                    Text("Referral link share actions: ${it.shareActions}")
                    Text("Search and recommendation view sources are not yet tracked separately.",
                        style = MaterialTheme.typography.labelSmall)
                }
                sources?.let {
                    Text("Profile opens from Search (7 days): ${it.searchProfileOpens}")
                    Text("Profile opens from new-student recommendations: ${it.recommendationProfileOpens}")
                    Text("Only unique daily in-app opens are counted.", style = MaterialTheme.typography.labelSmall)
                }
                linkStats?.let {
                    Text("Unique invite-browser opens (30 days, estimated): ${it.estimatedUniqueOpens30Days}")
                    Text("Confirmed referral signups (30 days): ${it.confirmedSignups30Days}")
                    Text("Link opens are estimates and cannot earn Coins.", style = MaterialTheme.typography.labelSmall)
                }
                coins?.let {
                    Text("Referral milestone Coins earned: ${it.earnedCoins}")
                    if (it.rewardEnabled) {
                        Text("Next milestone: ${it.nextMilestone ?: "All unlocked"} · ${it.nextRewardCoins} Coins")
                    } else Text("Milestone Coin awards are awaiting owner approval.",
                        style = MaterialTheme.typography.labelSmall)
                }
                if (leaders.isNotEmpty()) {
                    Text("Top connectors this week", style = MaterialTheme.typography.titleSmall)
                    leaders.take(5).forEach { person ->
                        Text("#${person.position} @${person.username} · ${person.confirmedReferrals} referrals")
                    }
                    leaders.firstOrNull { it.username.equals(username, ignoreCase = true) }?.let {
                        Text("Your weekly leaderboard position: #${it.position}")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Share your invite link. A new account must be confirmed to count.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = username.isNotBlank(),
                onClick = {
                    val link = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${BlinkReferralPolicy.INVITATION_MESSAGE}\n$link")
                    }
                    context.startActivity(Intent.createChooser(intent, "Invite to BLINK"))
                    scope.launch { runCatching { BlinkReferralClient.recordShareAction("share") } }
                },
            ) { Text("Invite friends") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val url = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    clipboard.setText(AnnotatedString(url))
                    copied = true
                    scope.launch { runCatching { BlinkReferralClient.recordShareAction("copy") } }
                }) { Text(if (copied) "Copied!" else "Copy link") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

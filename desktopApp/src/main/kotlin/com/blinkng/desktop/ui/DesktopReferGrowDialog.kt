package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralStats
import com.blinkng.shared.discovery.BlinkReferralCoinStatus
import com.blinkng.shared.discovery.BlinkReferralSourceStats
import com.blinkng.shared.discovery.BlinkReferralGrowthStats
import com.blinkng.shared.discovery.BlinkReferralLeaderboardEntry
import kotlinx.coroutines.launch

@Composable
internal fun DesktopReferGrowDialog(state: DesktopAppState, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<BlinkReferralStats?>(null) }
    var growth by remember { mutableStateOf<BlinkReferralGrowthStats?>(null) }
    var coins by remember { mutableStateOf<BlinkReferralCoinStatus?>(null) }
    var sources by remember { mutableStateOf<BlinkReferralSourceStats?>(null) }
    var leaders by remember { mutableStateOf<List<BlinkReferralLeaderboardEntry>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state.session?.userId) {
        runCatching { state.client.referralDashboard() }
            .onSuccess { stats = it }
            .onFailure { error = true }
        growth = runCatching { state.client.referralGrowthStats() }.getOrNull()
        coins = runCatching { state.client.referralCoinRewardStatus() }.getOrNull()
        sources = runCatching { state.client.referralSourceStats() }.getOrNull()
        leaders = runCatching { state.client.referralLeaderboard() }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLINK Refer & Grow") },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BlinkReferralPolicy.PROMO_MESSAGE)
                if (stats == null && !error) LinearProgressIndicator(Modifier.fillMaxWidth())
                stats?.let {
                    Text("Lifetime confirmed referrals: ${it.total}")
                    Text("Achievement: ${it.achievementTier}")
                    Text(BlinkReferralPolicy.milestoneLabel(it))
                    Text("Active in 30 days: ${it.activeLast30Days}")
                    Text("Current visibility: ${it.visibilityLabel} (${it.visibilityPoints} points)")
                    Text("New referrals add 3 points for days 0-9, 2 for days 10-19, and 1 for days 20-29. Lifetime badges never fade.")
                    Text("Discovery doesn't guarantee views or follows.")
                }
                growth?.let {
                    Text("Real profile views this week: ${it.profileViewsThisWeek}")
                    Text("Followers gained this week: ${it.followersGainedThisWeek}")
                    Text("Referral link share actions: ${it.shareActions}")
                    Text("Search/recommendation attribution is not tracked separately.",
                        style = MaterialTheme.typography.labelSmall)
                }
                sources?.let {
                    Text("Unique Search profile opens (7 days): ${it.searchProfileOpens}")
                    Text("Unique recommended profile opens (7 days): ${it.recommendationProfileOpens}")
                }
                coins?.let {
                    Text("Earned referral milestone Coins: ${it.earnedCoins}")
                    Text(if (it.rewardEnabled)
                        "Next milestone: ${it.nextMilestone ?: "All unlocked"} · ${it.nextRewardCoins} Coins"
                        else "Milestone Coin awards await owner approval.")
                }
                if (leaders.isNotEmpty()) {
                    Text("Top connectors this week", style = MaterialTheme.typography.titleSmall)
                    leaders.take(5).forEach { person ->
                        Text("#${person.position} @${person.username} · ${person.confirmedReferrals} referrals")
                    }
                    leaders.firstOrNull { it.username.equals(state.profile?.username, true) }?.let {
                        Text("Your weekly position: #${it.position}")
                    }
                }
                if (error) Text("Referrals are unavailable until the server update is ready.")
                Text("Referral visibility increases discovery opportunities, not guaranteed followers.")
                if (copied) Text("Invitation copied to clipboard.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.profile?.username?.isNotBlank() == true,
                onClick = {
                    val username = state.profile?.username ?: return@TextButton
                    val link = runCatching { BlinkReferralPolicy.inviteUrl(username) }.getOrNull()
                        ?: return@TextButton
                    clipboard.setText(AnnotatedString("${BlinkReferralPolicy.INVITATION_MESSAGE}\n$link"))
                    copied = true
                    scope.launch { runCatching { state.client.recordReferralShare("desktop") } }
                }
            ) { Text("Copy invitation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

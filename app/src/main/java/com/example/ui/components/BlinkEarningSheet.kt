package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkEarningHubPolicy
import com.example.data.supabase.BlinkEconomyService
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class WalletActivity(
    val id: String,
    val label: String,
    val amount: Long,
    val date: String,
)

private fun parseWalletHistory(data: JSONObject): List<WalletActivity> {
    val rows = data.optJSONArray("transactions") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id").takeUnless { it == "null" }.orEmpty()
            if (id.isBlank()) continue
            val title = row.optString("item_name")
                .takeIf { it.isNotBlank() && !it.equals("null", true) }
                ?: BlinkEarningHubPolicy.readableTransaction(row.optString("kind"))
            add(WalletActivity(
                id = id,
                label = title,
                amount = row.optLong("amount", 0L),
                date = row.optString("created_at").take(16).replace('T', ' '),
            ))
        }
    }
}

/** BLINK Menu → Earning, showing ONLY server-owned coins and actual missions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkEarningSheet(
    accountId: String,
    balance: Long,
    dailyStreak: Int,
    adsToday: Int,
    policy: BlinkEconomyPolicy,
    missions: List<BlinkDailyMission>,
    loadingMissions: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onClaimMission: (String) -> Unit,
    onWatchOptionalAd: () -> Unit,
    onOpenVerification: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(accountId) { BlinkEconomyService() }
    var history by remember(accountId) { mutableStateOf<List<WalletActivity>>(emptyList()) }
    var confirmedBalance by remember(accountId) { mutableStateOf<Long?>(null) }
    var historyLoading by remember(accountId) { mutableStateOf(true) }
    var historyError by remember(accountId) { mutableStateOf<String?>(null) }

    suspend fun refreshHistory() {
        historyLoading = true
        historyError = null
        api.state().onSuccess { response ->
            confirmedBalance = response.optLong("balance").coerceAtLeast(0)
            history = parseWalletHistory(response)
        }.onFailure {
            confirmedBalance = null
            historyError = "Couldn't load wallet history. Check your connection and retry."
            history = emptyList()
        }
        historyLoading = false
    }

    LaunchedEffect(accountId) {
        if (accountId.isNotBlank()) {
            onRefresh()
            refreshHistory()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Earning", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onRefresh()
                        scope.launch { refreshHistory() }
                    },
                    enabled = !historyLoading && !loadingMissions,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Refresh")
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AVAILABLE BLINK COINS",
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
                        style = MaterialTheme.typography.labelMedium)
                    Text(confirmedBalance?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface)
                    Text(
                        if (confirmedBalance == null)
                            "Balance not confirmed. Last displayed balance: ${balance.coerceAtLeast(0)} coins."
                        else "In-app points only · not cash · withdrawals unavailable",
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Your rewards", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(
                "Daily activity streak: ${dailyStreak.coerceAtLeast(0)} days. " +
                    "Check-ins don't award extra coins unless a server-listed mission says so.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (loadingMissions) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (missions.isEmpty()) {
                Text("No daily missions available right now.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                missions.forEach { mission ->
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(mission.title, fontWeight = FontWeight.Bold)
                            if (mission.description.isNotBlank()) {
                                Text(mission.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${mission.progress.coerceAtLeast(0).coerceAtMost(mission.target)}/${mission.target} · " +
                                    "${mission.coinReward} coins · ${mission.xpReward} XP",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LinearProgressIndicator(
                                progress = { mission.progressFraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            when (BlinkEarningHubPolicy.action(mission)) {
                                BlinkEarningHubPolicy.MissionAction.CLAIM ->
                                    Button(onClick = { onClaimMission(mission.key) }) {
                                        Text("Claim completed mission")
                                    }
                                BlinkEarningHubPolicy.MissionAction.COMPLETED ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                        Text("Reward claimed", modifier = Modifier.padding(start = 7.dp))
                                    }
                                BlinkEarningHubPolicy.MissionAction.IN_PROGRESS ->
                                    Text("Not completed", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Optional ads", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(
                "You can earn coins from an eligible rewarded ad. Ads are optional. " +
                    "The server must confirm rewards before your balance changes.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Ads completed today: ${adsToday.coerceIn(0, policy.rewardedAdDailyLimit)}/${policy.rewardedAdDailyLimit}",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = policy.canWatchRewardedAd(adsToday) && accountId.isNotBlank(),
                onClick = onWatchOptionalAd,
            ) { Text("Watch an optional rewarded ad") }

            HorizontalDivider()
            Text("Invite friends", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(
                "Invite people you know. Invites currently earn no coins; no contacts are uploaded.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val invite = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Join me on BLINK: https://www.blink.com.ng")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(invite, "Share BLINK invitation"))
                    }
                },
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share invitation")
            }

            HorizontalDivider()
            Text("Use your coins", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = onOpenVerification,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("View Blue verification · 3,000 coins") }

            HorizontalDivider()
            Text("Coin history", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            if (historyLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                historyError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { refreshHistory() } }) { Text("Retry") }
                }
                if (history.isEmpty() && historyError == null) {
                    Text("No recorded wallet transactions yet.",
                        style = MaterialTheme.typography.bodySmall)
                }
                history.take(30).forEach { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.label, style = MaterialTheme.typography.bodyMedium)
                            Text(tx.date + " UTC", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            BlinkEarningHubPolicy.coinChangeLabel(tx.amount),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Close") }
        }
    }
}

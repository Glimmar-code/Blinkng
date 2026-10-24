package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEarningHubPolicy
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat

private data class DesktopWalletTransaction(
    val id: String, val name: String, val amount: Long, val date: String
)

private fun walletRows(payload: JSONObject): List<DesktopWalletTransaction> {
    val rows = payload.optJSONArray("transactions") ?: return emptyList()
    return buildList {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("id").trim()
            if (id.isEmpty() || id == "null") continue
            val name = row.optString("item_name").trim()
            add(DesktopWalletTransaction(
                id = id,
                name = name.takeIf { it.isNotBlank() && it != "null" }
                    ?: BlinkEarningHubPolicy.readableTransaction(row.optString("kind")),
                amount = row.optLong("amount", 0),
                date = row.optString("created_at").take(16).replace('T', ' ')
            ))
        }
    }
}

private fun walletMissions(payload: JSONObject?): List<BlinkDailyMission> {
    val array = payload?.optJSONArray("missions") ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val key = row.optString("key")
            val target = row.optInt("target")
            if (key.isBlank() || target <= 0) continue
            add(BlinkDailyMission(
                key = key, title = row.optString("title").ifBlank { "Daily mission" },
                description = row.optString("description"),
                progress = row.optInt("progress").coerceAtLeast(0),
                target = target,
                coinReward = row.optInt("coin_reward").coerceAtLeast(0),
                xpReward = row.optInt("xp_reward").coerceAtLeast(0),
                claimed = row.optBoolean("claimed")
            ))
        }
    }
}

/** Same authenticated store and mission RPCs as Android. No fabricated ledger or coin totals. */
@Composable
fun BlinkDesktopWalletScreen(state: DesktopAppState, onNavigate: (String) -> Unit) {
    val accountId = state.profile?.id.orEmpty()
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var balance by remember(accountId) { mutableStateOf<Long?>(null) }
    var history by remember(accountId) { mutableStateOf<List<DesktopWalletTransaction>>(emptyList()) }
    var missions by remember(accountId) { mutableStateOf<List<BlinkDailyMission>>(emptyList()) }
    var tab by remember(accountId) { mutableIntStateOf(0) }
    var filter by remember(accountId) { mutableIntStateOf(0) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var missionError by remember(accountId) { mutableStateOf<String?>(null) }
    var busyMission by remember(accountId) { mutableStateOf<String?>(null) }
    val color = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outlineVariant

    suspend fun reload() {
        if (accountId.isBlank()) {
            error = "Sign in to view your wallet."
            loading = false
            return
        }
        loading = true
        error = null
        missionError = null
        runCatching { actions.getStoreState() }.onSuccess { result ->
            balance = if (result.has("balance") && !result.isNull("balance"))
                result.optLong("balance").coerceAtLeast(0L) else null
            history = walletRows(result)
            if (balance == null) error = "Your balance could not be confirmed."
        }.onFailure {
            balance = null
            history = emptyList()
            error = "Couldn't load your secure wallet. Check your connection and retry."
        }
        runCatching { actions.getDailyMissions() }.onSuccess {
            missions = walletMissions(it)
        }.onFailure {
            missions = emptyList()
            missionError = "Daily missions could not be refreshed."
        }
        loading = false
    }
    LaunchedEffect(accountId) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.widthIn(max = 900.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNavigate("menu") }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Menu")
                    }
                    Text("Wallet & Coins", fontSize = 30.sp,
                        fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Refresh")
                    }
                }
                Text("Your BLINK Coins, daily missions, and real transaction history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                color = Color(0xFF141619), shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AVAILABLE BLINK COINS", color = Color(0xFFBDD1DD),
                        fontWeight = FontWeight.SemiBold)
                    Text(balance?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—",
                        fontSize = 46.sp, color = Color.White, fontWeight = FontWeight.Black)
                    Text(
                        if (balance != null) "Server-confirmed balance"
                        else if (loading) "Verifying wallet balance…" else "Balance unavailable",
                        color = Color(0xFFBDC6D1)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .18f))
                    Text("BLINK Coins are in-app only. They are not cash and cannot be withdrawn.",
                        color = Color(0xFFBDC6D1))
                }
            }
        }
        item {
            Row(Modifier.widthIn(max = 900.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onNavigate("store") }) {
                    Icon(Icons.Rounded.Storefront, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Coins in BLINK Store")
                }
                OutlinedButton(onClick = { tab = 1 }) { Text("Earn Rewards") }
            }
        }
        if (error != null) {
            item {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { reload() } }) { Text("Retry") }
            }
        }
        item {
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.widthIn(max = 900.dp)
            ) {
                listOf("Overview", "Rewards", "History").forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
        }
        when (tab) {
            0 -> {
                item { Text("Your wallet", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                item {
                    DesktopWalletCard(color, border) {
                        Text("Daily missions: ${missions.count { BlinkEarningHubPolicy.action(it) == BlinkEarningHubPolicy.MissionAction.CLAIM }} ready to claim")
                        Text("Available transactions: ${history.size}")
                        Text("Only server-returned activity is shown. Figures may be incomplete.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Text("Recent activity", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                if (loading) item { LinearProgressIndicator(Modifier.widthIn(max = 900.dp).fillMaxWidth()) }
                else if (history.isEmpty()) item { Text("No recorded transactions available yet.") }
                else items(history.take(3), key = { "recent_${it.id}" }) { tx -> DesktopWalletRow(tx, color, border) }
                item { OutlinedButton(onClick = { tab = 2 }) { Text("View available history") } }
            }
            1 -> {
                item { Text("Daily missions", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                if (missionError != null) item { Text(missionError.orEmpty(), color = MaterialTheme.colorScheme.error) }
                if (loading) item { LinearProgressIndicator(Modifier.widthIn(max = 900.dp).fillMaxWidth()) }
                else if (missions.isEmpty()) item { Text("No daily missions available right now.") }
                else items(missions, key = { "reward_${it.key}" }) { mission ->
                    DesktopWalletCard(color, border) {
                        Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(mission.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${mission.progress.coerceAtMost(mission.target)}/${mission.target} • +${mission.coinReward} coins • +${mission.xpReward} XP")
                        LinearProgressIndicator(progress = { mission.progressFraction },
                            modifier = Modifier.fillMaxWidth())
                        when (BlinkEarningHubPolicy.action(mission)) {
                            BlinkEarningHubPolicy.MissionAction.CLAIM ->
                                Button(
                                    enabled = busyMission == null,
                                    onClick = {
                                        busyMission = mission.key
                                        scope.launch {
                                            runCatching { actions.claimDailyMission(mission.key) }
                                                .onSuccess { result ->
                                                    if (result.optBoolean("success", true)) {
                                                        busyMission = null
                                                        reload()
                                                    } else {
                                                        missionError = result.optString("message")
                                                            .ifBlank { "The server did not confirm this reward." }
                                                        busyMission = null
                                                    }
                                                }
                                                .onFailure {
                                                    missionError = it.message ?: "Unable to claim reward."
                                                    busyMission = null
                                                }
                                        }
                                    }
                                ) { Text(if (busyMission == mission.key) "Claiming…" else "Claim reward") }
                            BlinkEarningHubPolicy.MissionAction.COMPLETED ->
                                Text("✓ Reward claimed", color = Color(0xFF13B889))
                            BlinkEarningHubPolicy.MissionAction.IN_PROGRESS -> Text("In progress")
                        }
                    }
                }
                item { Text("Optional rewarded ads are available only on supported Android devices.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> {
                item { Text("Coin transaction history", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Earned", "Used").forEachIndexed { index, label ->
                            FilterChip(selected = filter == index, onClick = { filter = index },
                                label = { Text(label) })
                        }
                    }
                }
                val selected = when (filter) {
                    1 -> history.filter { it.amount > 0 }
                    2 -> history.filter { it.amount < 0 }
                    else -> history
                }
                if (loading) item { LinearProgressIndicator(Modifier.widthIn(max = 900.dp).fillMaxWidth()) }
                else if (selected.isEmpty()) item { Text("No matching transactions available.") }
                else items(selected, key = { "tx_${it.id}" }) { tx -> DesktopWalletRow(tx, color, border) }
            }
        }
    }
}

@Composable
private fun DesktopWalletCard(
    color: Color, border: Color, content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
        color = color, border = BorderStroke(1.dp, border), shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content)
    }
}

@Composable
private fun DesktopWalletRow(row: DesktopWalletTransaction, color: Color, border: Color) {
    DesktopWalletCard(color, border) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (row.amount >= 0) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                null, tint = if (row.amount >= 0) Color(0xFF13B889) else
                    MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, fontWeight = FontWeight.SemiBold)
                Text(row.date + " UTC", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(BlinkEarningHubPolicy.coinChangeLabel(row.amount), fontWeight = FontWeight.Bold)
        }
    }
}

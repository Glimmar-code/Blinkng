package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkEarningHubPolicy
import com.example.data.supabase.BlinkEconomyService
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat

internal data class WalletLedgerRow(
    val id: String,
    val description: String,
    val amount: Long,
    val createdAt: String
)

internal fun decodeWalletLedger(payload: JSONObject): List<WalletLedgerRow> {
    val array = payload.optJSONArray("transactions") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val id = row.optString("id").trim()
            if (id.isBlank() || id == "null") continue
            val name = row.optString("item_name").trim()
            add(WalletLedgerRow(
                id = id,
                description = name.takeIf { it.isNotBlank() && it != "null" }
                    ?: BlinkEarningHubPolicy.readableTransaction(row.optString("kind")),
                amount = row.optLong("amount", 0L),
                createdAt = row.optString("created_at").take(16).replace('T', ' ')
            ))
        }
    }
}

private val WalletBlack = Color(0xFF141619)
private val WalletGreen = Color(0xFF13B889)
private val WalletMuted = Color(0xFF90959D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkWalletScreen(
    accountId: String,
    lastKnownBalance: Long,
    dailyStreak: Int,
    adsToday: Int,
    policy: BlinkEconomyPolicy,
    missions: List<BlinkDailyMission>,
    loadingMissions: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClaimMission: (String) -> Unit,
    onWatchOptionalAd: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenStore: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val service = remember(accountId) { BlinkEconomyService() }
    var balance by remember(accountId) { mutableStateOf<Long?>(null) }
    var history by remember(accountId) { mutableStateOf<List<WalletLedgerRow>>(emptyList()) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var tab by remember(accountId) { mutableIntStateOf(0) }
    var historyFilter by remember(accountId) { mutableIntStateOf(0) }
    val foreground = MaterialTheme.colorScheme.onSurface
    val background = if (isDark) Color(0xFF0D0F12) else Color(0xFFF9FAFC)
    val surface = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
    val amountFormatter = remember { NumberFormat.getIntegerInstance() }
    val compact = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.5f

    suspend fun refresh() {
        if (accountId.isBlank()) return
        loading = true
        error = null
        service.state().onSuccess { response ->
            balance = if (response.has("balance") && !response.isNull("balance")) {
                response.optLong("balance").coerceAtLeast(0L)
            } else null
            history = decodeWalletLedger(response)
            if (balance == null) error = "Your balance could not be confirmed. Please retry."
        }.onFailure {
            balance = null
            history = emptyList()
            error = "Couldn't reach your BLINK wallet. Check your internet and retry."
        }
        loading = false
    }

    LaunchedEffect(accountId) {
        if (accountId.isNotBlank()) {
            onRefresh()
            refresh()
        } else {
            loading = false
            error = "Sign in to access your personal wallet."
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("blink_wallet_page"),
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Wallet & Coins", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Menu")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onRefresh()
                        scope.launch { refresh() }
                    }, enabled = !loading && accountId.isNotBlank()) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh wallet")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("wallet_balance_card"),
                    color = WalletBlack,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = WalletGreen)
                            Spacer(Modifier.width(9.dp))
                            Text("AVAILABLE BLINK COINS", color = Color(0xFFD9DFE7),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            balance?.let { amountFormatter.format(it) } ?: "—",
                            fontSize = if (compact) 26.sp else 39.sp, fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.testTag("wallet_balance_value")
                        )
                        Text(
                            when {
                                loading -> "Verifying your account balance…"
                                balance != null -> "Server-confirmed balance"
                                else -> "Last displayed balance: ${amountFormatter.format(lastKnownBalance.coerceAtLeast(0))} • not verified"
                            },
                            color = Color(0xFFBFC8D1), fontSize = 12.sp,
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = .13f))
                        Text(
                            "BLINK Coins are for in-app features, not cash. They cannot be withdrawn or transferred as money.",
                            color = Color(0xFFD3D9E0), fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }
            }
            item {
                if (compact) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Button(
                            onClick = onOpenStore,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WalletBlack)
                        ) { Text("Use Coins in BLINK Store") }
                        OutlinedButton(
                            onClick = { tab = 1 },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) { Text("Earn Rewards") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenStore,
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WalletBlack)
                        ) {
                            Icon(Icons.Rounded.Storefront, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Use Coins", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { tab = 1 },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Rounded.CardGiftcard, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Earn Rewards", maxLines = 1)
                        }
                    }
                }
            }
            if (error != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { scope.launch { refresh() } }) {
                                Text("Retry wallet connection")
                            }
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab, containerColor = background) {
                    listOf("Overview", "Rewards", "History").forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("wallet_tab_${title.lowercase()}")
                        )
                    }
                }
            }
            when (tab) {
                0 -> {
                    item { WalletSectionHeader("Your wallet", "A quick look at your BLINK activity.") }
                    item {
                        WalletPanel(surface, border) {
                            WalletInfoRow(Icons.Rounded.LocalFireDepartment, "Daily streak",
                                "${dailyStreak.coerceAtLeast(0)} days", foreground)
                            HorizontalDivider()
                            WalletInfoRow(Icons.Rounded.TaskAlt, "Available missions",
                                "${missions.count { BlinkEarningHubPolicy.action(it) == BlinkEarningHubPolicy.MissionAction.CLAIM }} ready to claim",
                                foreground)
                            HorizontalDivider()
                            WalletInfoRow(Icons.Rounded.ReceiptLong, "Recent wallet transactions",
                                if (loading) "Loading…" else "${history.size} available", foreground)
                        }
                    }
                    item { WalletSectionHeader("Coin activity", "Amounts shown come from your server transaction history.") }
                    if (loading) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    } else if (history.isEmpty()) {
                        item { WalletEmpty("No recorded wallet transactions available yet.") }
                    } else {
                        items(history.take(3), key = { "overview_${it.id}" }) { tx -> WalletTransaction(tx, surface, border) }
                        item {
                            TextButton(onClick = { tab = 2 }) { Text("View available history  →") }
                        }
                    }
                    item {
                        WalletPanel(surface, border) {
                            Text("Verification", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Use eligible BLINK Coins toward verification if your account qualifies.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = onOpenVerification, modifier = Modifier.fillMaxWidth()) {
                                Text("Explore verification")
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        WalletSectionHeader("Daily missions", "Earn coins and XP only from server-listed, completed activities.")
                    }
                    if (loadingMissions) {
                        item { CircularProgressIndicator() }
                    } else if (missions.isEmpty()) {
                        item { WalletEmpty("No daily missions are available right now.") }
                    } else {
                        items(missions, key = { "mission_${it.key}" }) { mission ->
                            WalletPanel(surface, border) {
                                Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (mission.description.isNotBlank()) {
                                    Text(mission.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${mission.progress.coerceIn(0, mission.target.coerceAtLeast(1))}/${mission.target} • +${mission.coinReward} coins • +${mission.xpReward} XP",
                                    style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(
                                    progress = { mission.progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                when (BlinkEarningHubPolicy.action(mission)) {
                                    BlinkEarningHubPolicy.MissionAction.CLAIM -> Button(
                                        onClick = { onClaimMission(mission.key) }
                                    ) { Text("Claim reward") }
                                    BlinkEarningHubPolicy.MissionAction.COMPLETED ->
                                        Text("✓ Reward claimed", color = WalletGreen)
                                    BlinkEarningHubPolicy.MissionAction.IN_PROGRESS ->
                                        Text("Not completed yet",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { WalletSectionHeader("Optional rewarded ads", "Ad rewards are optional and must be confirmed by the server.") }
                    item {
                        WalletPanel(surface, border) {
                            Text("Ads completed today: ${adsToday.coerceIn(0, policy.rewardedAdDailyLimit)}/${policy.rewardedAdDailyLimit}")
                            OutlinedButton(
                                enabled = policy.canWatchRewardedAd(adsToday) && accountId.isNotBlank(),
                                onClick = onWatchOptionalAd, modifier = Modifier.fillMaxWidth()
                            ) { Text("Watch an optional rewarded ad") }
                        }
                    }
                }
                else -> {
                    item { WalletSectionHeader("Coin history", "Only transactions returned by your BLINK account are shown.") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Earned", "Used").forEachIndexed { index, label ->
                                FilterChip(selected = historyFilter == index,
                                    onClick = { historyFilter = index },
                                    label = { Text(label) })
                            }
                        }
                    }
                    val visible = when (historyFilter) {
                        1 -> history.filter { it.amount > 0L }
                        2 -> history.filter { it.amount < 0L }
                        else -> history
                    }
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else if (visible.isEmpty()) item { WalletEmpty("No matching wallet transactions available.") }
                    else items(visible, key = { "history_${it.id}" }) { tx -> WalletTransaction(tx, surface, border) }
                }
            }
        }
    }
}

@Composable
private fun WalletSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WalletPanel(
    color: Color, border: Color, content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = color, border = BorderStroke(1.dp, border), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun WalletInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    value: String, foreground: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = WalletGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), color = foreground,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = foreground, maxLines = 2)
    }
}

@Composable
private fun WalletEmpty(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WalletTransaction(tx: WalletLedgerRow, surface: Color, border: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = surface, border = BorderStroke(1.dp, border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (tx.amount >= 0) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                null, tint = if (tx.amount >= 0) WalletGreen else WalletMuted
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.description, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(tx.createdAt.takeIf { it.isNotBlank() }?.plus(" UTC") ?: "Date unavailable",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text(BlinkEarningHubPolicy.coinChangeLabel(tx.amount),
                fontWeight = FontWeight.Bold,
                color = if (tx.amount > 0) WalletGreen else MaterialTheme.colorScheme.onSurface)
        }
    }
}

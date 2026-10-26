package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import com.blinkng.shared.BlinkEconomyDefaults
import com.blinkng.shared.BlinkEconomyPolicy
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
    var adsToday by remember(accountId) { mutableIntStateOf(0) }
    var policy by remember(accountId) { mutableStateOf(BlinkEconomyDefaults.policy) }
    var actionBusy by remember(accountId) { mutableStateOf(false) }
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
        runCatching { actions.getEconomyStatus() }.onSuccess { status ->
            adsToday = status.optInt("ads_today", 0).coerceAtLeast(0)
            policy = BlinkEconomyDefaults.policy.copy(
                rewardedAdDailyLimit = status.optInt("rewarded_ad_daily_limit", 15).coerceIn(1,15),
                cashCheckoutEnabled = status.optBoolean("cash_checkout_enabled", false))
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

    val verified = state.profile?.isVerified == true &&
        state.profile?.verificationTier?.uppercase() != "FREE"
    val bg = Color(0xFF05020E)
    val indigo = Color(0xFF931AFF)
    val pale = Color(0xFFAAA6D4)
    val gold = Color(0xFFFFD54B)
    val card = Color(0xFF120E26)
    val edge = Color(0xFF483079)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (tab == 0) onNavigate("menu") else tab = 0 }) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(listOf("Coins & Wallet","Earn Coins","Rewards","Get Verified","History")[tab],
                    fontSize = 27.sp, color = Color.White,
                    fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                    Icon(Icons.Rounded.Refresh, null, tint = pale)
                    Text("Refresh", color = pale)
                }
            }
        }
        if (error != null) item {
            DesktopWalletCard(card, edge) {
                Text(error.orEmpty(), color = Color(0xFFFF93B0))
                TextButton(onClick = { scope.launch { reload() } }) { Text("Retry") }
            }
        }
        if (loading) item {
            LinearProgressIndicator(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), color = indigo)
        }
        if (tab == 0) {
            item {
                Box(
                    Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0343B4),
                            Color(0xFF150C36),Color(0xFF5105B6))),RoundedCornerShape(24.dp))
                        .border(1.dp,indigo,RoundedCornerShape(24.dp))
                ) {
                    Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MonetizationOn, null, tint = gold,
                                modifier = Modifier.size(68.dp))
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text("Your BLINK Coins", color = pale, fontSize = 15.sp)
                                Text(balance?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—",
                                    fontWeight = FontWeight.Black, color = Color.White, fontSize = 46.sp)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .2f))
                        Text("Wallet Balance • In-app Coins only. Coin gifting remains available. No cash withdrawals.",
                            color = Color.White)
                    }
                }
            }
            item {
                DesktopWalletCard(card, edge) {
                    Text("Overview", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    DesktopWalletAction("Earn a lot of coins watching ads",
                        "10 Coins for each completed ad • Android ad support", Icons.Rounded.PlayArrow) {
                        tab = 1
                    }
                    DesktopWalletAction("Complete daily tasks",
                        "Earn BLINK Coins and level XP (HP) from daily missions", Icons.Rounded.TaskAlt) {
                        tab = 2
                    }
                }
            }
            item {
                DesktopWalletCard(card, edge) {
                    Text("Buy Coins", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    policy.coinPacks.forEach { pack ->
                        DesktopWalletAction(
                            "₦" + NumberFormat.getIntegerInstance().format(pack.priceNgn),
                            NumberFormat.getIntegerInstance().format(pack.coins) + " Coins" +
                                if (policy.cashCheckoutEnabled) "" else " • Checkout unavailable",
                            Icons.Rounded.MonetizationOn,
                            enabled = policy.cashCheckoutEnabled && !actionBusy
                        ) {
                            // A provider URL must be verified; client callbacks never grant coins.
                            scope.launch {
                                actionBusy = true
                                runCatching {
                                    val response = actions.initializePaystackCoinCheckout(pack.id)
                                    val url = response.optString("authorization_url")
                                        .ifBlank { response.optString("checkout_url") }
                                        .ifBlank { response.optString("checkoutUrl") }
                                    require(com.blinkng.shared.BlinkCoinCheckoutPolicy.trustedHostedCheckoutUrl(url)) {
                                        "A trusted payment page was not returned."
                                    }
                                    if (java.awt.Desktop.isDesktopSupported()) {
                                        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                                    } else error("Open payments on a supported browser.")
                                }.onFailure { error = it.message ?: "Secure checkout unavailable." }
                                actionBusy = false
                            }
                        }
                    }
                }
            }
            item {
                DesktopWalletAction("Get Verified with Coins",
                    "3,000 Coins for Blue Verification", Icons.Rounded.Verified) { tab = 3 }
            }
            item {
                Row(Modifier.widthIn(max = 1000.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        DesktopWalletAction("Rewards", "Daily milestones and missions",
                            Icons.Rounded.CardGiftcard) { tab = 2 }
                    }
                    Box(Modifier.weight(1f)) {
                        DesktopWalletAction("History", "Your real transactions",
                            Icons.Rounded.History) { tab = 4 }
                    }
                }
            }
        }
        if (tab == 1) {
            item {
                DesktopWalletCard(card, edge) {
                    Text("Watch Rewarded Ads", color = Color.White, fontSize = 25.sp,
                        fontWeight = FontWeight.Black)
                    Text("10 Coins per Ad", color = gold, fontSize = 30.sp,
                        fontWeight = FontWeight.Black)
                    val max = policy.effectiveAdDailyLimit(verified)
                    Text("Ads watched today: " + adsToday + " / " + max, color = pale)
                    LinearProgressIndicator(
                        progress = { (adsToday.toFloat()/max.coerceAtLeast(1)).coerceIn(0f,1f) },
                        color = indigo, modifier = Modifier.fillMaxWidth())
                    Text("Optional rewarded videos currently play on supported Android devices; " +
                        "Windows shares the same authenticated balance and reward history.",
                        color = pale)
                }
            }
            item {
                DesktopWalletCard(card,edge) {
                    Text("Daily Milestones",fontSize = 23.sp,
                        fontWeight = FontWeight.Black,color = Color.White)
                    policy.rewardedMilestones.forEach { milestone ->
                        val locked = milestone.ads > 10 && !verified
                        DesktopWalletAction(milestone.ads.toString() + " Ads",
                            milestone.totalCoins.toString() + " Coins Total" +
                                if (locked) " • Verified accounts only"
                                else if (adsToday >= milestone.ads) " • Already credited" else "",
                            if (locked) Icons.Rounded.Lock else Icons.Rounded.EmojiEvents
                        ) { tab = if (locked) 3 else 2 }
                    }
                }
            }
        }
        if (tab == 2) {
            item {
                DesktopWalletCard(card, edge) {
                    Text("Reward Center",fontSize = 26.sp,
                        fontWeight = FontWeight.Black,color = Color.White)
                    Text("Daily tasks earn BLINK Coins and level XP (HP).",color = pale)
                    Text("Ads completed today: " + adsToday, color = gold)
                    Text("Ad milestone Coins are credited automatically after server confirmation.",
                        color = pale)
                }
            }
            item {
                DesktopWalletCard(card,edge) {
                    Text("Claim Rewards",fontSize = 23.sp,
                        fontWeight = FontWeight.Black,color = Color.White)
                    policy.rewardedMilestones.forEach { m ->
                        val locked = m.ads > 10 && !verified
                        DesktopWalletAction("Watch " + m.ads + " Ads",
                            if (locked) "Locked • Verified accounts only"
                            else if (adsToday >= m.ads) "Already credited • " + m.totalCoins + " total Coins"
                            else adsToday.toString() + "/" + m.ads + " • " + m.totalCoins + " total Coins",
                            if (locked) Icons.Rounded.Lock else Icons.Rounded.CardGiftcard) {
                            tab = if (locked) 3 else 1
                        }
                    }
                }
            }
            item {
                DesktopWalletCard(card, edge) {
                    Text("Other Rewards • Daily missions", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 20.sp)
                    if (missionError != null) Text(missionError.orEmpty(), color = Color(0xFFFF91AF))
                    if (missions.isEmpty()) Text("No missions available right now.", color = pale)
                    else missions.forEach { mission ->
                        Text(mission.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(mission.progress.toString() + "/" + mission.target + " • +" +
                            mission.coinReward + " Coins • +" + mission.xpReward + " level XP (HP)", color = pale)
                        LinearProgressIndicator(
                            progress = { mission.progressFraction },
                            modifier = Modifier.fillMaxWidth(), color = indigo)
                        if (BlinkEarningHubPolicy.action(mission) == BlinkEarningHubPolicy.MissionAction.CLAIM) {
                            Button(onClick = {
                                busyMission = mission.key
                                scope.launch {
                                    runCatching { actions.claimDailyMission(mission.key) }
                                        .onSuccess { result ->
                                            if (result.optBoolean("success", true)) reload()
                                            else missionError = result.optString("message")
                                        }
                                        .onFailure { missionError = it.message }
                                    busyMission = null
                                }
                            },enabled = busyMission == null) { Text("Claim reward") }
                        } else if (mission.claimed) Text("Claimed", color = Color(0xFF27DDA3))
                    }
                }
            }
        }
        if (tab == 3) {
            item {
                DesktopWalletCard(card, edge) {
                    Icon(Icons.Rounded.Verified, null, tint = Color(0xFF4CA8FF),
                        modifier = Modifier.size(80.dp))
                    Text("Blue Verification", color = Color.White,
                        fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("3,000 Coins", color = gold,
                        fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("Your confirmed balance: " + (balance?.toString() ?: "—"), color = pale)
                    LinearProgressIndicator(
                        progress = { policy.verificationProgress(balance ?: 0L) },
                        modifier = Modifier.fillMaxWidth(), color = indigo)
                }
            }
            item {
                DesktopWalletCard(card, edge) {
                    Text("What You Get", color = Color.White, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold)
                    listOf("Verified Badge", "More Profile Trust",
                        "Better Visibility", "Stand Out on BLINK").forEach {
                        Text("✓ " + it, color = Color.White)
                    }
                }
            }
            item {
                Button(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6510E8)),
                    enabled = !verified && !actionBusy && balance != null &&
                        balance!! >= policy.blueVerificationCoinCost,
                    onClick = {
                        actionBusy = true
                        scope.launch {
                            runCatching { actions.purchaseBlueVerificationWithCoins() }
                                .onSuccess { result ->
                                    if (result.optBoolean("success")) {
                                        state.refreshProfile()
                                        reload()
                                    } else error = "Verification was not confirmed."
                                }
                                .onFailure { error = it.message ?: "Verification unavailable." }
                            actionBusy = false
                        }
                    }
                ) { Text(if (verified) "Already Verified" else "Use 3,000 Coins") }
            }
            item {
                DesktopWalletAction("Need more Coins?", "Browse packs or earn with Android ads",
                    Icons.Rounded.MonetizationOn) { tab = 0 }
            }
        }
        if (tab == 4) {
            item {
                DesktopWalletCard(card, edge) {
                    Text("Coin History", color = Color.White,
                        fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Your complete available Coin activity, confirmed by BLINK.",
                        color = pale)
                    Text("Earned +" + history.filter { it.amount > 0 }.sumOf { it.amount } +
                        "   Spent " + history.filter { it.amount < 0 }.sumOf { it.amount },
                        fontSize = 20.sp, color = gold)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("All", "Earned", "Spent").forEachIndexed { index, label ->
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
            if (selected.isEmpty() && !loading) item {
                Text("No matching Coin transactions available.", color = pale)
            }
            else items(selected, key = { it.id }) { tx -> DesktopWalletRow(tx, card, edge) }
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


@Composable
private fun DesktopWalletAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth()
        .background(Brush.horizontalGradient(listOf(Color(0xFF211445),Color(0xFF100C26))),shape)
        .border(1.dp,Color(0xFF473184),shape)
        .clickable(enabled = enabled,onClick = onClick)
        .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon,null,tint = if (enabled) Color(0xFFAA81FF) else Color.Gray,
            modifier = Modifier.size(35.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White,fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle,color = Color(0xFFB5ADCE),fontSize = 12.sp)
        }
        if (enabled) Icon(Icons.Rounded.ChevronRight,null,tint = Color(0xFFD3B5FF))
    }
}

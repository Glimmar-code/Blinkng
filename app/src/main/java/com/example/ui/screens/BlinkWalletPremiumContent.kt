package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkCoinPack
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkEarningHubPolicy
import java.text.NumberFormat

// Screenshot-inspired surfaces. Coin totals, claims, verification and payment remain server-authoritative.
private val night = Color(0xFF05020E)
private val panel = Color(0xFF100C24)
private val tile = Color(0xFF15102F)
private val soft = Color(0xFFA7A2D1)
private val gold = Color(0xFFFFD54B)
private val violet = Color(0xFF8D12FB)
private val blue = Color(0xFF087BFF)
private val neon = Brush.horizontalGradient(listOf(blue, Color(0xFF3814FC), violet))
private val rim = Brush.linearGradient(listOf(blue.copy(alpha = .9f), violet.copy(alpha = .95f)))

private fun comma(value: Long) = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun WalletLogo() {
    Text("B", color = Color(0xFFC67EFF), fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable
private fun CoinArtwork(modifier: Modifier = Modifier) {
    val reduceMotion = com.example.performance.rememberBlinkReduceMotion()
    val floatTransition = rememberInfiniteTransition(label = "wallet_coin_float")
    val floatOffset by floatTransition.animateFloat(
        initialValue = -2f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400), RepeatMode.Reverse),
        label = "coin_hover"
    )
    Box(modifier.size(112.dp).graphicsLayer {
        translationY = if (reduceMotion) 0f else floatOffset
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).background(
            Brush.radialGradient(listOf(violet.copy(alpha = .48f), Color.Transparent)), CircleShape))
        Box(Modifier.size(76.dp).background(
            Brush.linearGradient(listOf(Color(0xFFFFFA98), Color(0xFFEF9700), Color(0xFF9B4700))),
            CircleShape).border(3.dp, gold, CircleShape),
            contentAlignment = Alignment.Center) {
            Text("B", color = Color(0xFF79410D), fontSize = 43.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NeonCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape).background(if (hero)
        Brush.linearGradient(listOf(Color(0xFF0642AA), Color(0xFF110C32), Color(0xFF4A048E)))
        else Brush.linearGradient(listOf(panel, Color(0xFF0C0C22), Color(0xFF210D39))))
        .border(1.dp, if (hero) rim else Brush.linearGradient(
            listOf(Color(0xFF4C4390), Color(0xFF32105F))), shape)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
    }
}

@Composable
private fun NeonRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
    trailing: String? = null,
    locked: Boolean = false
) {
    val shape = RoundedCornerShape(17.dp)
    val click = if (action == null) Modifier else Modifier.clickable(onClick = action)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1333), Color(0xFF110C2A))))
            .border(1.dp, Color(0xFF3D2A77), shape).then(click)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3115A0), Color(0xFF34105B))))
            .border(1.dp, violet, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(if (locked) Icons.Rounded.Lock else icon, null,
                tint = if (locked) soft else Color(0xFF9F75FF),
                modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.sp, color = soft, maxLines = 3)
        }
        if (trailing != null) Text(trailing, color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (action != null) Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFC19DFF))
    }
}

@Composable
private fun GradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.ChevronRight,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val reduceMotion = com.example.performance.rememberBlinkReduceMotion()
    val glow = rememberInfiniteTransition(label = "wallet_button_glow")
    val glowAlpha by glow.animateFloat(
        initialValue = .5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2100), RepeatMode.Reverse),
        label = "cta_glow"
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 65.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(if (enabled) neon else Brush.horizontalGradient(listOf(Color(0xFF262038), Color(0xFF39304B))))
        .border(1.dp, if (enabled) Color(0xFFBD96FF).copy(alpha = if (reduceMotion) .8f else glowAlpha) else Color(0xFF484060), RoundedCornerShape(20.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (enabled) Color.White else soft)
        Spacer(Modifier.width(11.dp))
        Text(text, color = if (enabled) Color.White else soft, fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WalletHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(subtitle, fontSize = 12.sp, color = soft)
    }
}

/** Five real destinations, backed by the existing BLINK economy and ledger rather than sample screenshot figures. */
@Composable
internal fun BlinkWalletPremiumContent(
    accountId: String,
    balance: Long?,
    history: List<WalletLedgerRow>,
    loading: Boolean,
    error: String?,
    adsToday: Int,
    isVerified: Boolean,
    policy: BlinkEconomyPolicy,
    missions: List<BlinkDailyMission>,
    loadingMissions: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWatchAd: () -> Unit,
    onClaimMission: (String) -> Unit,
    onVerifyCoins: () -> Unit,
    onOpenVerification: () -> Unit,
    onBuyPack: (BlinkCoinPack) -> Unit
) {
    var page by remember(accountId) { mutableIntStateOf(0) }
    var filter by remember(accountId) { mutableIntStateOf(0) }
    BackHandler { if (page == 0) onBack() else page = 0 }
    val allTitles = listOf("Coins & Wallet", "Earn Coins", "Rewards", "Get Verified", "History")
    Column(Modifier.fillMaxSize().background(night).testTag("blink_wallet_premium")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page == 0) onBack() else page = 0 }) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Color.White)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(allTitles[page], fontSize = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            WalletLogo()
        }
        Crossfade(targetState = page, animationSpec = tween(230), label = "wallet_pages") { current ->
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp)) {
                if (error != null) item {
                    NeonCard {
                        Text(error, color = Color(0xFFFFA9BF))
                        TextButton(onClick = onRetry) { Text("Retry secure wallet", color = Color.White) }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = violet) }
                when (current) {
                    0 -> {
                        item {
                            NeonCard(hero = true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoinArtwork(Modifier.size(106.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Your BLINK Coins", color = soft)
                                        Text(balance?.let(::comma) ?: "—", fontSize = 38.sp,
                                            fontWeight = FontWeight.Black, color = Color.White)
                                        Text("🟡 Coins", color = gold, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(onClick = { /* balance is already the current page */ }) {
                                        Icon(Icons.Rounded.AddCircle, "Coins and wallet", tint = Color(0xFFB768FF))
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Wallet Balance", color = soft, fontSize = 12.sp)
                                        Text("In-app Coins only", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Not cash • No withdrawals", color = soft, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Button(onClick = { page = 3 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF581AFB))) {
                                        Text("Get Verified")
                                    }
                                }
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Overview", "Earn more. Do more on BLINK.")
                                NeonRow(Icons.Rounded.PlayCircle, "Earn a lot of coins watching ads",
                                    "Watch rewarded ads and earn 10 coins per completed ad.",
                                    action = { page = 1 })
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Buy Coins", "Available packs • secure checkout")
                                policy.coinPacks.forEach { pack ->
                                    NeonRow(Icons.Rounded.MonetizationOn,
                                        "₦" + comma(pack.priceNgn.toLong()),
                                        comma(pack.coins.toLong()) + " Coins",
                                        trailing = if (policy.cashCheckoutEnabled) null else "Unavailable",
                                        action = if (policy.cashCheckoutEnabled) ({ onBuyPack(pack) }) else null)
                                }
                                if (!policy.cashCheckoutEnabled)
                                    Text("Payments are currently unavailable for this account.",
                                        color = soft, fontSize = 12.sp)
                            }
                        }
                        item {
                            NeonRow(Icons.Rounded.Verified, "Get Verified with Coins",
                                "Use " + comma(policy.blueVerificationCoinCost.toLong()) +
                                    " Coins for Blue Verification", action = { page = 3 })
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) {
                                    NeonRow(Icons.Rounded.CardGiftcard, "Rewards", "Daily milestones",
                                        action = { page = 2 })
                                }
                                Box(Modifier.weight(1f)) {
                                    NeonRow(Icons.Rounded.History, "History", "Coin activity",
                                        action = { page = 4 })
                                }
                            }
                        }
                    }
                    1 -> {
                        item {
                            NeonCard(hero = true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoinArtwork(Modifier.size(110.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Watch Rewarded Ads", fontWeight = FontWeight.Bold,
                                            color = Color.White, fontSize = 21.sp)
                                        Text("10 Coins per Ad", fontWeight = FontWeight.Black,
                                            color = gold, fontSize = 25.sp)
                                        Text("Complete a rewarded ad to earn coins.",
                                            color = soft, fontSize = 13.sp)
                                    }
                                }
                                val max = policy.effectiveAdDailyLimit(isVerified)
                                Text("Ads watched today • " + adsToday.coerceAtLeast(0) + " / " + max,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(
                                    progress = { (adsToday.toFloat() / max.coerceAtLeast(1)).coerceIn(0f,1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    color = violet, trackColor = Color(0xFF372562))
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Daily Milestones", "Bonus Coins are credited automatically.")
                                policy.rewardedMilestones.sortedBy { it.ads }.forEach { m ->
                                    val locked = m.ads > 10 && !isVerified
                                    NeonRow(Icons.Rounded.PlayCircle,
                                        m.ads.toString() + " Ads",
                                        m.totalCoins.toString() + " Coins Total" +
                                            if (locked) " • Verified users only" else if (adsToday >= m.ads) " • Credited" else "",
                                        locked = locked,
                                        action = { page = 2 })
                                }
                            }
                        }
                        item {
                            GradientButton(
                                if (policy.canWatchRewardedAd(adsToday, isVerified)) "Watch Ad"
                                else if (isVerified) "Daily limit reached" else "Verify to unlock 15 ads",
                                icon = Icons.Rounded.PlayArrow,
                                onClick = if (policy.canWatchRewardedAd(adsToday, isVerified))
                                    onWatchAd else ({ page = 3 }))
                        }
                        item { Text("Rewards require completed, server-confirmed ads. New ads may not be available.",
                            modifier = Modifier.fillMaxWidth(), color = soft, fontSize = 12.sp) }
                    }
                    2 -> {
                        item {
                            NeonCard(hero = true) {
                                WalletHeading("Reward Center", "Watch ads, complete tasks and earn BLINK Coins.")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CoinArtwork(Modifier.size(84.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Ads watched today", color = soft)
                                        Text(adsToday.coerceAtLeast(0).toString(), color = Color.White,
                                            fontWeight = FontWeight.Black, fontSize = 27.sp)
                                        Text("Daily milestone Coins are credited automatically.",
                                            color = soft, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Claim Rewards", "Never claim the same reward twice.")
                                policy.rewardedMilestones.sortedBy { it.ads }.forEach { m ->
                                    val locked = m.ads > 10 && !isVerified
                                    NeonRow(Icons.Rounded.CardGiftcard,
                                        "Watch " + m.ads + " Ads",
                                        if (locked) "Verified users only"
                                        else if (adsToday >= m.ads) "Reward already credited to your wallet"
                                        else adsToday.coerceAtLeast(0).toString() + " / " + m.ads,
                                        trailing = if (locked) "Locked" else m.totalCoins.toString() + " total",
                                        locked = locked,
                                        action = if (locked) ({ page = 3 }) else ({ page = 1 }))
                                }
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Other Rewards", "Real daily missions from your account")
                                if (loadingMissions) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else if (missions.isEmpty()) Text("No missions available right now.", color = soft)
                                else missions.forEach { mission ->
                                    NeonRow(Icons.Rounded.EmojiEvents, mission.title,
                                        mission.progress.toString() + "/" + mission.target +
                                            " • +" + mission.coinReward + " Coins / +" + mission.xpReward + " XP",
                                        trailing = if (mission.claimed) "Claimed" else null,
                                        action = if (BlinkEarningHubPolicy.action(mission) ==
                                            BlinkEarningHubPolicy.MissionAction.CLAIM)
                                            ({ onClaimMission(mission.key) }) else null)
                                }
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("Reward Tips", "Follow these tips to earn more Coins.")
                                NeonRow(Icons.Rounded.PlayCircle, "Watch more ads",
                                    "Milestone bonuses are awarded after completed ads.", action = { page = 1 })
                                NeonRow(Icons.Rounded.Schedule, "Daily reset",
                                    "Today's ad progress resets at 00:00 UTC.")
                            }
                        }
                        item { GradientButton("Earn Coins", Icons.Rounded.CardGiftcard) { page = 1 } }
                    }
                    3 -> {
                        item {
                            NeonCard(hero = true) {
                                WalletHeading("Blue Verification", "Unlock your BLINK Blue badge.")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(112.dp).background(
                                        Brush.radialGradient(listOf(blue, Color(0xFF272198))), CircleShape),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Verified, null, tint = Color(0xFF69DCFF),
                                            modifier = Modifier.size(82.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(comma(policy.blueVerificationCoinCost.toLong()) + " Coins",
                                            color = gold, fontSize = 23.sp, fontWeight = FontWeight.Black)
                                        Text("Your balance: " + (balance?.let(::comma) ?: "—"),
                                            color = Color.White, fontSize = 15.sp)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { policy.verificationProgress(balance ?: 0L) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    color = violet, trackColor = Color(0xFF372562))
                            }
                        }
                        item {
                            NeonCard {
                                WalletHeading("What You Get", "Be real. Be seen. Be BLINK.")
                                NeonRow(Icons.Rounded.Verified, "Verified Badge",
                                    "Display the official blue badge on your profile.")
                                NeonRow(Icons.Rounded.Shield, "More Profile Trust",
                                    "Let others recognize your verified account.")
                                NeonRow(Icons.Rounded.BarChart, "Better Visibility",
                                    "See BLINK's verified account benefits.")
                                NeonRow(Icons.Rounded.Star, "Stand Out on BLINK",
                                    "Join verified creators on BLINK.")
                            }
                        }
                        item {
                            GradientButton(
                                if (isVerified) "Already verified"
                                else "Use " + comma(policy.blueVerificationCoinCost.toLong()) + " Coins",
                                Icons.Rounded.Verified,
                                enabled = !isVerified && balance != null &&
                                    balance!! >= policy.blueVerificationCoinCost && !loading,
                                onClick = onVerifyCoins)
                        }
                        item {
                            NeonRow(Icons.Rounded.MonetizationOn, "Need more Coins?",
                                "Earn Coins by watching ads, or choose a coin pack.",
                                action = { page = 1 })
                        }
                        item { TextButton(onClick = onOpenVerification) {
                            Text("Other verification options", color = Color(0xFFB49AFF))
                        } }
                    }
                    else -> {
                        item {
                            val positive = history.filter { it.amount > 0 }
                                .sumOf { it.amount }
                            val negative = history.filter { it.amount < 0 }
                                .sumOf { it.amount }
                            NeonCard(hero = true) {
                                WalletHeading("Coin History", "Your server-confirmed transactions.")
                                Text("Earned: +" + comma(positive) + "    Spent: " + comma(negative),
                                    fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Black)
                                Text("Totals reflect available history, not a fabricated monthly balance.",
                                    color = soft, fontSize = 11.sp)
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                listOf("All", "Earned", "Spent").forEachIndexed { index, label ->
                                    FilterChip(
                                        selected = filter == index,
                                        onClick = { filter = index },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF5313DF),
                                            selectedLabelColor = Color.White))
                                }
                            }
                        }
                        val selected = when (filter) {
                            1 -> history.filter { it.amount > 0 }
                            2 -> history.filter { it.amount < 0 }
                            else -> history
                        }
                        if (selected.isEmpty() && !loading) item {
                            NeonCard {
                                Text("No matching transactions yet.", color = Color.White)
                            }
                        }
                        items(selected, key = { it.id }) { tx ->
                            NeonRow(
                                if (tx.amount >= 0) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                                tx.description,
                                tx.createdAt.takeIf { it.isNotBlank() }?.plus(" UTC") ?: "Date unavailable",
                                trailing = (if (tx.amount > 0) "+" else "") + comma(tx.amount))
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.*
import com.blinkng.shared.BlinkEconomyDefaults
import com.blinkng.shared.BlinkEconomyPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetVerifiedSheet(
    profile: UserProfile,
    isDark: Boolean,
    blinkCoinBalance: Long = 0L,
    economyPolicy: BlinkEconomyPolicy = BlinkEconomyDefaults.policy,
    rewardedAdsToday: Int = 0,
    onDismiss: () -> Unit,
    onWatchAdForCoins: () -> Unit = {},
    onBuyBlinkCoins: () -> Unit = {},
    onVerifyWithCoins: () -> Unit = {},
    onUpgrade: (VerificationBadge) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBadge by remember {
        mutableStateOf(
            if (profile.verificationBadge == VerificationBadge.BLUE) VerificationBadge.GOLD else VerificationBadge.BLUE
        )
    }
    val isGoldEligible = profile.followerCount >= 1000

    val sheetBg = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkBorder else LightBorder

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = BlinkPink.copy(alpha = 0.5f))
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(BlinkPink.copy(alpha = 0.3f), BlinkPurple.copy(alpha = 0.1f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = if (selectedBadge == VerificationBadge.GOLD) BlinkGold else BlinkBlue,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Get Verified on Blink",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Unlock BLINK Verified status with Blink Coins or a secure cash checkout when available.",
                        fontSize = 13.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // User Info Snapshot
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF1E1729) else Color(0xFFF6F4FA),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                error = painterResource(R.drawable.ic_default_profile),
                                fallback = painterResource(R.drawable.ic_default_profile),
                                contentDescription = profile.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = profile.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textPrimary
                                    )
                                    if (profile.verificationBadge != VerificationBadge.NONE) {
                                        VerifiedMark(badge = profile.verificationBadge, size = 14.dp)
                                    }
                                }
                                Text(
                                    text = "@${profile.username} • ${profile.followerCount} followers",
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = when (profile.verificationBadge) {
                                VerificationBadge.GOLD -> BlinkGold.copy(alpha = 0.2f)
                                VerificationBadge.BLUE -> BlinkBlue.copy(alpha = 0.2f)
                                VerificationBadge.NONE -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when (profile.verificationBadge) {
                                    VerificationBadge.GOLD -> "Gold Active"
                                    VerificationBadge.BLUE -> "Blue Active"
                                    VerificationBadge.NONE -> "Unverified"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (profile.verificationBadge) {
                                    VerificationBadge.GOLD -> BlinkGold
                                    VerificationBadge.BLUE -> BlinkBlue
                                    VerificationBadge.NONE -> textSecondary
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Tiers Selector
            // 1. Blue Verified Card (₦800)
            item {
                val isSelected = selectedBadge == VerificationBadge.BLUE
                val isCurrent = profile.verificationBadge == VerificationBadge.BLUE

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BlinkBlue.copy(alpha = 0.1f) else cardBg(isDark)
                    ),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) BlinkBlue else borderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedBadge = VerificationBadge.BLUE }
                        .testTag("tier_blue_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VerifiedMark(badge = VerificationBadge.BLUE, size = 26.dp)
                                Column {
                                    Text(
                                        text = "BLINK Verified",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Premium BLINK status",
                                        fontSize = 12.sp,
                                        color = BlinkBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₦800",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = BlinkBlue
                                )
                                Text(
                                    text = "One-time fee",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Features checklist
                        VerificationFeatureItem(
                            icon = Icons.Default.Verified,
                            text = "Blue BLINK Verified badge across supported identity surfaces",
                            highlight = true,
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Person,
                            text = "Premium verified profile treatment and status card",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Storefront,
                            text = "Recognizable seller identity on supported Marketplace surfaces",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.WorkspacePremium,
                            text = "A permanent status purchase — it does not claim real-world identity verification",
                            isDark = isDark
                        )

                        if (isCurrent) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BlinkBlue.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✓ Currently Active on your account",
                                    color = BlinkBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Gold Verified Card (₦2000 + 1k Followers)
            item {
                val isSelected = selectedBadge == VerificationBadge.GOLD
                val isCurrent = profile.verificationBadge == VerificationBadge.GOLD

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BlinkGold.copy(alpha = 0.12f) else cardBg(isDark)
                    ),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) BlinkGold else borderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedBadge = VerificationBadge.GOLD }
                        .testTag("tier_gold_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VerifiedMark(badge = VerificationBadge.GOLD, size = 26.dp)
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Gold Verification",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = textPrimary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = BlinkGold.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "VIP",
                                                color = BlinkGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Top Campus Creator & Pro Merchant",
                                        fontSize = 12.sp,
                                        color = BlinkGold,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₦2,000",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = BlinkGold
                                )
                                Text(
                                    text = "+ 1k followers req",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Follower Requirement Progress Indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isGoldEligible) Color(0xFF132818) else if (isDark) Color(0xFF281C10) else Color(0xFFFFF7ED),
                            border = BorderStroke(
                                1.dp,
                                if (isGoldEligible) Color(0xFF22C55E).copy(alpha = 0.5f) else BlinkGold.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            imageVector = if (isGoldEligible) Icons.Default.CheckCircle else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isGoldEligible) Color(0xFF22C55E) else BlinkGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isGoldEligible) "Follower Requirement Met!" else "Requirement: 1,000 Followers",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGoldEligible) Color(0xFF22C55E) else if (isDark) Color.White else Color(0xFF9A3412)
                                        )
                                    }
                                    Text(
                                        text = "${profile.followerCount} / 1,000",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGoldEligible) Color(0xFF22C55E) else BlinkGold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val progress = (profile.followerCount.toFloat() / 1000f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = if (isGoldEligible) Color(0xFF22C55E) else BlinkGold,
                                    trackColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var showFollowerGrowthChart by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BlinkGold.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, BlinkGold.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFollowerGrowthChart = !showFollowerGrowthChart }
                                .testTag("toggle_follower_growth_chart")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = BlinkGold, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (showFollowerGrowthChart) "Hide 30-Day Growth Trends" else "View 30-Day Follower Growth Trends",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                                Icon(
                                    imageVector = if (showFollowerGrowthChart) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = BlinkGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (showFollowerGrowthChart) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FollowerGrowthChart(
                                profile = profile,
                                isDark = isDark,
                                onOpenGetVerified = { /* in sheet */ },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Gold features checklist
                        VerificationFeatureItem(
                            icon = Icons.Default.Star,
                            text = "Gold creator status and premium identity treatment",
                            highlight = true,
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Bolt,
                            text = "Gold creator cosmetics and supported premium surfaces",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Storefront,
                            text = "Gold merchant identity treatment on supported Marketplace surfaces",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.WorkspacePremium,
                            text = "Gold status treatment on supported leaderboard and notification surfaces",
                            isDark = isDark
                        )

                        if (isCurrent) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BlinkGold.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "👑 Highest Verification Level Active",
                                    color = BlinkGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // BLINK Verified economy progress and purchase actions
            item {
                val verificationCost = economyPolicy.blueVerificationCoinCost
                val remaining = economyPolicy.verificationCoinsRemaining(blinkCoinBalance)
                val progress = economyPolicy.verificationProgress(blinkCoinBalance)
                val nextAdNumber = (rewardedAdsToday + 1).coerceAtMost(economyPolicy.rewardedAdDailyLimit)
                val nextAdReward = economyPolicy.rewardForCompletedAd(nextAdNumber)
                val canWatch = economyPolicy.canWatchRewardedAd(rewardedAdsToday)
                val alreadyBlueOrGold = profile.verificationBadge != VerificationBadge.NONE

                if (selectedBadge == VerificationBadge.BLUE) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = BlinkBlue.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, BlinkBlue.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("BLINK Verified", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                    Text(
                                        "$blinkCoinBalance / $verificationCost coins",
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                                Text(
                                    if (alreadyBlueOrGold) "ACTIVE" else if (remaining == 0L) "READY" else "$remaining left",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (alreadyBlueOrGold || remaining == 0L) Color(0xFF16A34A) else BlinkBlue
                                )
                            }

                            LinearProgressIndicator(
                                progress = { if (alreadyBlueOrGold) 1f else progress },
                                color = BlinkBlue,
                                trackColor = BlinkBlue.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(100.dp))
                            )

                            Text(
                                if (alreadyBlueOrGold) {
                                    "BLINK Verified is already active on this account."
                                } else {
                                    "Earn coins with rewarded ads or buy a coin pack. Paying ₦${economyPolicy.blueVerificationCashNgn} is the faster cash route once secure checkout is enabled."
                                },
                                fontSize = 11.5.sp,
                                color = textSecondary,
                                lineHeight = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                economyPolicy.rewardedMilestones.forEach { milestone ->
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (rewardedAdsToday >= milestone.ads) {
                                            Color(0xFF16A34A).copy(alpha = 0.10f)
                                        } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("${milestone.ads} ads", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            Text("${milestone.totalCoins} coins", fontSize = 10.sp, color = textSecondary)
                                        }
                                    }
                                }
                            }

                            Text(
                                "$rewardedAdsToday / ${economyPolicy.rewardedAdDailyLimit} rewarded ads today",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )

                            Button(
                                onClick = onVerifyWithCoins,
                                enabled = !alreadyBlueOrGold && remaining == 0L,
                                colors = ButtonDefaults.buttonColors(containerColor = BlinkBlue),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    when {
                                        alreadyBlueOrGold -> "BLINK Verified Active"
                                        remaining == 0L -> "Use $verificationCost coins"
                                        else -> "Need $remaining more coins"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onWatchAdForCoins,
                                    enabled = canWatch,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        if (canWatch) "Watch ad +$nextAdReward" else "Daily limit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = onBuyBlinkCoins,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Buy coins", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { onUpgrade(VerificationBadge.BLUE) },
                                enabled = !alreadyBlueOrGold && economyPolicy.cashCheckoutEnabled,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("Pay ₦${economyPolicy.blueVerificationCashNgn} securely", fontWeight = FontWeight.Bold)
                            }
                            if (!economyPolicy.cashCheckoutEnabled && !alreadyBlueOrGold) {
                                Text(
                                    "Cash checkout is prepared but stays off until a verified Paystack/payment checkout is connected. No fake payment can activate a badge.",
                                    fontSize = 10.5.sp,
                                    color = textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (economyPolicy.coinPacks.isNotEmpty()) {
                                HorizontalDivider()
                                Text("Blink Coin packs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                economyPolicy.coinPacks.forEach { pack ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("₦${pack.priceNgn}", fontSize = 11.5.sp, color = textSecondary)
                                        Text(
                                            "${pack.coins} coins" + if (pack.bonusCoins > 0) "  (+${pack.bonusCoins})" else "",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val isCurrentBadge = profile.verificationBadge == selectedBadge
                    val eligible = isGoldEligible && !isCurrentBadge
                    Button(
                        onClick = { onUpgrade(VerificationBadge.GOLD) },
                        enabled = eligible && economyPolicy.cashCheckoutEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = BlinkGold),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            when {
                                isCurrentBadge -> "Gold Verification Active"
                                !isGoldEligible -> "Requires 1,000 Followers (${profile.followerCount}/1,000)"
                                economyPolicy.cashCheckoutEnabled -> "Continue to secure Gold checkout"
                                else -> "Secure Gold checkout not connected"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    highlight: Boolean = false,
    isDark: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) BlinkPink else if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight && isDark) Color.White else if (highlight) LightTextPrimary else if (isDark) DarkTextSecondary else LightTextSecondary,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun cardBg(isDark: Boolean): Color {
    return if (isDark) Color(0xFF1B1424) else Color.White
}

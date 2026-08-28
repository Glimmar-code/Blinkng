package com.example.ui.components

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetVerifiedSheet(
    profile: UserProfile,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onUpgrade: (VerificationBadge) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBadge by remember {
        mutableStateOf(
            if (profile.verificationBadge == VerificationBadge.BLUE) VerificationBadge.GOLD else VerificationBadge.BLUE
        )
    }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("Campus Wallet / OPay") }
    var isProcessingPayment by remember { mutableStateOf(false) }

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
                        text = "Establish campus trust, boost post reach, and unlock selling on Aluta Market",
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
                                        text = "Blue Verification",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Verified Student & Market Seller",
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
                            icon = Icons.Default.Storefront,
                            text = "Required: Unlocks posting & selling on Aluta Market",
                            highlight = true,
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.CheckCircle,
                            text = "Blue checkmark badge on profile, posts & comments",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.TrendingUp,
                            text = "2x View weight on feed posts & campus reels",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Security,
                            text = "Student fraud prevention & verified campus trust",
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
                            text = "Prestigious Gold Tick Mark on profile & top feed placement",
                            highlight = true,
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Bolt,
                            text = "5x Maximum view reach when viewing other posts",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.Storefront,
                            text = "Pro Merchant Status & Top search placement on Aluta Market",
                            isDark = isDark
                        )
                        VerificationFeatureItem(
                            icon = Icons.Default.WorkspacePremium,
                            text = "Leaderboard VIP spotlight & priority notifications",
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

            // Action Button (Pay & Unlock)
            item {
                Spacer(modifier = Modifier.height(8.dp))

                val isCurrentBadge = profile.verificationBadge == selectedBadge
                val isButtonEnabled = if (selectedBadge == VerificationBadge.GOLD) {
                    isGoldEligible && !isCurrentBadge
                } else {
                    !isCurrentBadge
                }

                Button(
                    onClick = { showPaymentDialog = true },
                    enabled = isButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedBadge == VerificationBadge.GOLD) BlinkGold else BlinkBlue,
                        disabledContainerColor = if (isDark) Color(0xFF2A2336) else Color(0xFFE5E5EA)
                    ),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("get_verified_pay_btn")
                ) {
                    if (isCurrentBadge) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Already Activated",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    } else if (selectedBadge == VerificationBadge.GOLD && !isGoldEligible) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Requires 1,000 Followers (${profile.followerCount}/1,000)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
                        )
                    } else {
                        Text(
                            text = if (selectedBadge == VerificationBadge.BLUE) "Pay ₦800 for Blue Verification" else "Pay ₦2,000 for Gold Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (selectedBadge == VerificationBadge.GOLD) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }

    // Payment Confirmation Dialog
    if (showPaymentDialog) {
        val amountText = if (selectedBadge == VerificationBadge.BLUE) "₦800" else "₦2,000"
        val tierTitle = if (selectedBadge == VerificationBadge.BLUE) "Blue Verification Tick" else "Gold VIP Verification"

        Dialog(onDismissRequest = { if (!isProcessingPayment) showPaymentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = sheetBg,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VerifiedMark(badge = selectedBadge, size = 48.dp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Confirm Campus Payment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "You are subscribing to $tierTitle for @${profile.username}",
                        fontSize = 13.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Price display box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedBadge == VerificationBadge.GOLD) BlinkGold.copy(alpha = 0.12f) else BlinkBlue.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "Total Amount Due:",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary
                            )
                            Text(
                                text = amountText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selectedBadge == VerificationBadge.GOLD) BlinkGold else BlinkBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment Method Selector
                    Text(
                        text = "Choose Payment Option:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "Campus Wallet / OPay" to Icons.Default.AccountBalanceWallet,
                        "Debit Card / Transfer" to Icons.Default.CreditCard,
                        "USSD / Bank App" to Icons.Default.PhoneAndroid
                    ).forEach { (method, icon) ->
                        val isMethodSelected = selectedPaymentMethod == method
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMethodSelected) BlinkPink.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (isMethodSelected) BlinkPink else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedPaymentMethod = method }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isMethodSelected) BlinkPink else textSecondary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = method,
                                    fontSize = 13.sp,
                                    fontWeight = if (isMethodSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isProcessingPayment) {
                        CircularProgressIndicator(
                            color = if (selectedBadge == VerificationBadge.GOLD) BlinkGold else BlinkBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Securing transaction on blockchain & Supabase...",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    } else {
                        Button(
                            onClick = {
                                isProcessingPayment = true
                                // Simulate instant successful transaction
                                onUpgrade(selectedBadge)
                                showPaymentDialog = false
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedBadge == VerificationBadge.GOLD) BlinkGold else BlinkBlue
                            ),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_payment_btn")
                        ) {
                            Text(
                                text = "Complete $amountText Payment",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selectedBadge == VerificationBadge.GOLD) Color.Black else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showPaymentDialog = false }
                        ) {
                            Text("Cancel", color = textSecondary, fontSize = 13.sp)
                        }
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

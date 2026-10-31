package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkProfileThemes
import com.blinkng.shared.profileRankLabel
import com.blinkng.shared.xpProgress
import com.example.R
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.ProfileFollowInteractButton
import com.example.ui.components.VerifiedMark
import java.text.NumberFormat

/**
 * Reference-inspired full-bleed profile. Theme is always the PROFILE OWNER'S selection,
 * never derived from the visitor's Android light/dark preference.
 */
@Composable
internal fun BlinkPremiumProfileDashboard(
    profile: UserProfile,
    isMe: Boolean,
    blinkCoinBalance: Long,
    isDark: Boolean,
    isFollowing: Boolean,
    followBusy: Boolean,
    postCount: Int,
    totalLoadedViews: Long,
    totalLoadedLikes: Long,
    profileCompletion: Int,
    metricsComplete: Boolean = false,
    onAvatarClick: () -> Unit,
    hasActiveStory: Boolean = false,
    onStoryClick: () -> Unit = {},
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onFollow: () -> Unit,
    onVerify: () -> Unit,
    onEarnCoins: () -> Unit,
    onWallet: () -> Unit,
    onCampusRank: () -> Unit,
    onShowBadges: () -> Unit,
    onShare: () -> Unit,
    onCopyUsername: () -> Unit,
    onVisitors: () -> Unit,
    onFollowers: () -> Unit = {},
    onFollowing: () -> Unit = {},
    onTopAction: (String) -> Unit,
    onGiftCoins: (Int) -> Unit = {},
    onGameChallenge: () -> Unit = {},
    onMentorRequest: () -> Unit = {},
    onFriendRequest: () -> Unit = {},
    onRoommateRequest: () -> Unit = {},
    onStudyMateRequest: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onOpenConnectHub: () -> Unit = {},
    onCreatorStudio: () -> Unit = {},
) {
    val theme = BlinkProfileThemes.resolve(profile.profileTheme)
    val ink = Color.White
    val softInk = Color.White.copy(alpha = .75f)
    val card = Color(theme.cardArgb)
    val accent = Color(theme.accentArgb)
    var showDetails by remember(profile.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(theme.topArgb), Color(theme.bottomArgb))))
            .testTag("blink_premium_profile_dashboard")
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ink)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onTopAction("menu") },
                modifier = Modifier.size(48.dp).testTag("profile_three_dot_menu")
            ) {
                Icon(Icons.Default.MoreVert, "Profile options", tint = ink)
            }
        }

        if (profile.coverPhotoUrl.isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(112.dp)
                .clip(RoundedCornerShape(17.dp))) {
                AsyncImage(
                    model = profile.coverPhotoUrl,
                    contentDescription = "Profile cover photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(theme.bottomArgb).copy(alpha = .72f)))
                ))
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(if (hasActiveStory) 88.dp else 80.dp)
                    .then(
                        if (hasActiveStory) Modifier.border(
                            BorderStroke(2.dp, Brush.sweepGradient(listOf(accent, Color.White, accent))),
                            RoundedCornerShape(23.dp)
                        ) else Modifier
                    )
                    .clickable(enabled = hasActiveStory, onClick = onStoryClick)
                    .padding(if (hasActiveStory) 4.dp else 0.dp)
            ) {
                AsyncImage(
                    model = profile.avatarUrl,
                    fallback = painterResource(R.drawable.ic_default_profile),
                    error = painterResource(R.drawable.ic_default_profile),
                    contentDescription = "View profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable(onClick = onAvatarClick)
                )
                if (hasActiveStory) {
                    IconButton(
                        onClick = onStoryClick,
                        modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)
                            .background(card, CircleShape)
                    ) {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = "Open story",
                            tint = accent, modifier = Modifier.size(25.dp))
                    }
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.fullName.ifBlank { profile.username },
                        color = ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.verificationBadge != VerificationBadge.NONE) {
                        Spacer(Modifier.width(5.dp))
                        VerifiedMark(profile.verificationBadge, size = 17.dp)
                    }
                }
                Text("@${profile.username}", color = softInk, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                com.blinkng.shared.BlinkVerifiedIdentitySignature.label(
                    profile.verificationBadge.name, profile.hasActiveMarketVerification()
                )?.let { signature ->
                    Surface(
                        color = if (profile.verificationBadge == VerificationBadge.GOLD)
                            Color(0xFF6B4A12).copy(alpha = .55f)
                        else Color(0xFF174B71).copy(alpha = .48f),
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .22f))
                    ) {
                        Text(signature, color = ink, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                if (!isMe) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileFollowInteractButton(
                            isFollowing = isFollowing, enabled = !followBusy,
                            onFollow = onFollow, onUnfollow = onFollow,
                            onMessage = onMessage, onGiftCoins = onGiftCoins,
                            onGameChallenge = onGameChallenge, onMentorRequest = onMentorRequest,
                            onFriendRequest = onFriendRequest, onRoommateRequest = onRoommateRequest,
                            onStudyMateRequest = onStudyMateRequest, onViewProfile = onViewProfile,
                            onOpenConnectHub = onOpenConnectHub, darkSurface = true,
                            followingLabel = "Following"
                        )
                        Surface(
                            color = Color.White.copy(alpha = .12f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .24f)),
                            modifier = Modifier.clickable(onClick = onMessage)
                        ) {
                            Text("Message", color = ink, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                } else {
                    TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                        Text("Edit profile", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Surface(
            color = card.copy(alpha = .94f), shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PremiumProfileStat("Posts", postCount.toLong(), Modifier.weight(1f))
                PremiumProfileStat("Followers", profile.followerCount.toLong(), Modifier.weight(1f).clickable(onClick = onFollowers))
                PremiumProfileStat("Following", profile.followingCount.toLong(), Modifier.weight(1f).clickable(onClick = onFollowing))
                PremiumProfileStat("Views", totalLoadedViews, Modifier.weight(1f))
            }
        }
        Text(if (metricsComplete) "Totals from all active content" else "Engagement reflects loaded posts",
            color = softInk, fontSize = 11.sp)

        Surface(
            color = card.copy(alpha = .92f), shape = RoundedCornerShape(19.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (profile.professionalHeadline.isNotBlank())
                    Text(profile.professionalHeadline, color = ink, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                if (profile.bio.isNotBlank()) Text(profile.bio, color = ink, fontSize = 12.sp)
                if (profile.university.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(profile.university, color = softInk, fontSize = 11.sp, maxLines = 2)
                    }
                }
                if (isMe && profile.bio.isBlank() && profile.professionalHeadline.isBlank() &&
                    profile.university.isBlank()) {
                    Text("No bio added yet", color = softInk, fontSize = 11.sp)
                }
            }
        }

        // Exactly three public rank/progress cards. The owner gets two more
        // private actions without exposing coin balance to profile visitors.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PremiumProfileShortcut(
                text = "Campus Rank", icon = Icons.Default.EmojiEvents,
                modifier = Modifier.weight(1f), onClick = onCampusRank
            )
            PremiumProfileShortcut(
                text = "World Rank", icon = Icons.Default.Public,
                modifier = Modifier.weight(1f), onClick = onCampusRank
            )
            PremiumProfileShortcut(
                text = "HP Level", icon = Icons.Default.AutoAwesome,
                modifier = Modifier.weight(1f),
                onClick = if (isMe) onCreatorStudio else { { showDetails = true } }
            )
        }
        if (isMe) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PremiumProfileShortcut(
                    text = "Earn Coins", icon = Icons.Default.Paid,
                    modifier = Modifier.weight(1f), onClick = onEarnCoins
                )
                PremiumProfileShortcut(
                    text = "Get Verified", icon = Icons.Default.Verified,
                    modifier = Modifier.weight(1f), onClick = onVerify
                )
            }
        }

        if (isMe) {
            OutlinedButton(
                onClick = onCreatorStudio,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Creator Studio · Content insights", fontWeight = FontWeight.SemiBold)
            }
        }

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Hide profile details" else "More profile details", color = softInk)
        }

        if (showDetails) {
            Surface(
                color = card, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val xp = xpProgress(profile.totalXp, profile.xpLevel)
                    Text("Campus ${profileRankLabel(profile.campusRank)} · World ${profileRankLabel(profile.worldRank)} · HP Level ${xp.level} · ${xp.tierLabel}",
                        color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Following ${profile.followingCount}", color = softInk,
                        fontSize = 11.sp, modifier = Modifier.clickable(onClick = onFollowing))
                    TextButton(onClick = onShowBadges) { Text("Badges and achievements", color = accent) }
                    if (isMe) {
                        Text("BLINK Coins: ${NumberFormat.getIntegerInstance().format(blinkCoinBalance)}  ›",
                            color = softInk, fontSize = 11.sp,
                            modifier = Modifier.clickable(onClick = onWallet)
                                .padding(vertical = 5.dp))
                        TextButton(onClick = onVisitors) { Text("Profile visitors", color = accent) }
                        if (profileCompletion < 100)
                            TextButton(onClick = onEdit) { Text("Complete profile · ${profileCompletion}%", color = accent) }
                    }
                    TextButton(onClick = onShare) { Text("Share profile", color = accent) }
                    TextButton(onClick = onCopyUsername) { Text("Copy username", color = accent) }
                }
            }
        }
    }
}

@Composable
private fun PremiumProfileStat(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(NumberFormat.getIntegerInstance().format(value.coerceAtLeast(0)),
            color = Color.White, fontSize = 14.sp,
            fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = .75f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun PremiumProfileShortcut(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
        color = Color.White.copy(alpha = .10f),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = Color.White, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

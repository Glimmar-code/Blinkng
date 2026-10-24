package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.blinkng.shared.BlinkAdaptiveLayoutPolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.profileRankLabel
import com.blinkng.shared.xpProgress
import com.example.R
import com.example.data.models.UserProfile
import com.example.ui.components.ProfileFollowInteractButton
import com.example.data.models.VerificationBadge
import java.text.NumberFormat

/** Full-bleed BLINK profile dashboard. Never uses screenshot sample values as user data. */
private val ProfilePurple = Color(0xFF762BEE)
private val ProfileGreen = Color(0xFF16AA66)
private val ProfileHex = GenericShape { size, _ ->
    addPath(Path().apply {
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height / 4f)
        lineTo(size.width, size.height * 3f / 4f)
        lineTo(size.width / 2f, size.height)
        lineTo(0f, size.height * 3f / 4f)
        lineTo(0f, size.height / 4f)
        close()
    })
}

@Composable
internal fun BlinkReferenceProfileDashboard(
    profile: UserProfile,
    isMe: Boolean,
    blinkCoinBalance: Long,
    isDark: Boolean,
    isFollowing: Boolean,
    followBusy: Boolean,
    postCount: Int,
    profileCompletion: Int,
    onAvatarClick: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onFollow: () -> Unit,
    onVerify: () -> Unit,
    onEarnCoins: () -> Unit,
    onShowBadges: () -> Unit,
    onShare: () -> Unit,
    onCopyUsername: () -> Unit,
    onVisitors: () -> Unit,
    onTopAction: (String) -> Unit,
    // Existing Follow → Interact capabilities are surfaced here rather than
    // retaining a second, older profile header below this dashboard.
    onGiftCoins: (Int) -> Unit = {},
    onGameChallenge: () -> Unit = {},
    onMentorRequest: () -> Unit = {},
    onFriendRequest: () -> Unit = {},
    onRoommateRequest: () -> Unit = {},
    onStudyMateRequest: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onOpenConnectHub: () -> Unit = {}
) {
    val bg = if (isDark) Color(0xFF0B0B0B) else Color.White
    val card = if (isDark) Color(0xFF1D1D1D) else Color.White
    val ink = if (isDark) Color.White else Color.Black
    val muted = if (isDark) Color(0xFFBBBBBB) else Color(0xFF777777)
    val border = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE)
    val compactProfile = BlinkAdaptiveLayoutPolicy.useCompactPhoneLayout(
        LocalConfiguration.current.screenWidthDp.toFloat(),
        LocalDensity.current.fontScale
    )
    // At 200% font scale a 320dp phone cannot keep the avatar, name, metadata,
    // button and two rank cards in their ordinary side-by-side arrangement.
    val accessibilityProfile = LocalConfiguration.current.screenWidthDp < 340 ||
        LocalDensity.current.fontScale >= 1.6f
    val xp = xpProgress(profile.totalXp, profile.xpLevel)
    val studentVerified = profile.badges.any {
        val text = (it.id + " " + it.title).lowercase().replace('_', ' ')
        text.contains("verified student")
    }

    Column(
        Modifier.fillMaxWidth().testTag("blink_reference_profile_dashboard")
            .background(bg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = profile.avatarUrl, fallback = painterResource(R.drawable.ic_default_profile),
                error = painterResource(R.drawable.ic_default_profile), contentDescription = "Close profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack)
            )
            Spacer(Modifier.width(10.dp))
            Text(if (compactProfile) "BLINK" else "B L I N K", color = ink, fontWeight = FontWeight.Black,
                fontSize = if (compactProfile) 17.sp else 20.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            listOf(
                Triple(Icons.Default.Search, "Search", "search"),
                Triple(Icons.Default.SportsEsports, "Games", "games"),
                Triple(Icons.Default.Notifications, "Notifications", "notifications"),
                Triple(Icons.Default.Menu, "Menu", "menu")
            ).forEach { (icon, label, action) ->
                IconButton(onClick = { onTopAction(action) }, modifier = Modifier.size(33.dp)) {
                    Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (profile.coverPhotoUrl.isNotBlank()) {
            AsyncImage(
                model = profile.coverPhotoUrl,
                contentDescription = "Profile cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(108.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
        if (accessibilityProfile) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        fallback = painterResource(R.drawable.ic_default_profile),
                        error = painterResource(R.drawable.ic_default_profile),
                        contentDescription = "View avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onAvatarClick)
                    )
                    if (profile.isBlinkVip) {
                        Icon(Icons.Default.EmojiEvents, "BLINK VIP",
                            tint = Color(0xFFF5B71B), modifier = Modifier.align(Alignment.TopEnd).size(19.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.fullName.ifBlank { profile.username },
                            modifier = Modifier.weight(1f, fill = false),
                            color = ink, fontWeight = FontWeight.Black, fontSize = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        if (profile.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(5.dp))
                            com.example.ui.components.VerifiedMark(
                                badge = profile.verificationBadge, size = 18.dp
                            )
                        }
                    }
                    if (profile.university.isNotBlank()) {
                        Text(profile.university, color = muted, fontSize = 12.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Keep extended metadata below the avatar on accessibility layouts.
                if (profile.username.isNotBlank()) {
                    Text("@${profile.username}", color = muted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (profile.professionalHeadline.isNotBlank()) {
                    Text(profile.professionalHeadline, color = ink, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (profile.academicLevel.isNotBlank()) {
                    Text(profile.academicLevel, color = muted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (profile.department.isNotBlank()) {
                    Text(profile.department, color = muted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (profile.dailyStreak > 0) {
                    Text("🔥 ${profile.dailyStreak} day streak", color = muted,
                        fontSize = 11.sp, maxLines = 1)
                }
            if (profile.bio.isNotBlank()) {
                Text(profile.bio, color = muted, fontSize = 12.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = if (isMe) onEdit else onMessage,
                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = bg),
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.heightIn(min = 42.dp)
                ) {
                    Text(if (isMe) "Edit Profile" else "Message", fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(if (isMe) Icons.Default.Edit else Icons.Default.Chat,
                        null, modifier = Modifier.size(14.dp))
                }
                if (!isMe) {
                    ProfileFollowInteractButton(
                        isFollowing = isFollowing,
                        enabled = !followBusy,
                        onFollow = onFollow,
                        onUnfollow = onFollow,
                        onMessage = onMessage,
                        onGiftCoins = onGiftCoins,
                        onGameChallenge = onGameChallenge,
                        onMentorRequest = onMentorRequest,
                        onFriendRequest = onFriendRequest,
                        onRoommateRequest = onRoommateRequest,
                        onStudyMateRequest = onStudyMateRequest,
                        onViewProfile = onViewProfile,
                        onOpenConnectHub = onOpenConnectHub,
                        darkSurface = isDark
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (compactProfile) 82.dp else 98.dp)) {
                    AsyncImage(
                        model = profile.avatarUrl, fallback = painterResource(R.drawable.ic_default_profile),
                        error = painterResource(R.drawable.ic_default_profile), contentDescription = "View avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(if (compactProfile) 80.dp else 96.dp).clip(CircleShape).clickable(onClick = onAvatarClick)
                    )
                    if (profile.isBlinkVip) {
                        Icon(
                            Icons.Default.EmojiEvents, "BLINK VIP",
                            tint = Color(0xFFF5B71B),
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.fullName.ifBlank { profile.username },
                            color = ink, fontWeight = FontWeight.Black, fontSize = if (compactProfile) 17.sp else 19.sp,
                            maxLines = if (compactProfile) 2 else 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (profile.verificationBadge != VerificationBadge.NONE) {
                            Spacer(Modifier.width(5.dp))
                            com.example.ui.components.VerifiedMark(
                                badge = profile.verificationBadge, size = 18.dp
                            )
                        }
                    }
                    if (profile.username.isNotBlank())
                        Text("@${profile.username}", color = muted, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.professionalHeadline.isNotBlank())
                        Text(profile.professionalHeadline, color = ink, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (profile.university.isNotBlank())
                        Text(profile.university, color = muted, fontSize = 12.sp, maxLines = 2)
                    if (profile.academicLevel.isNotBlank())
                        Text(profile.academicLevel, color = muted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.department.isNotBlank())
                        Text(profile.department, color = muted, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.dailyStreak > 0)
                        Text("🔥 ${profile.dailyStreak} day streak", color = muted,
                            fontSize = 11.sp, maxLines = 1)
                    if (profile.bio.isNotBlank())
                        Text(profile.bio, color = muted, fontSize = 12.sp, maxLines = 2)
                    Button(
                        onClick = if (isMe) onEdit else onMessage,
                        colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = bg),
                        shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text(if (isMe) "Edit Profile" else "Message", fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(if (isMe) Icons.Default.Edit else Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                    }
                    if (!isMe) {
                        ProfileFollowInteractButton(
                            isFollowing = isFollowing,
                            enabled = !followBusy,
                            onFollow = onFollow,
                            onUnfollow = onFollow,
                            onMessage = onMessage,
                            onGiftCoins = onGiftCoins,
                            onGameChallenge = onGameChallenge,
                            onMentorRequest = onMentorRequest,
                            onFriendRequest = onFriendRequest,
                            onRoommateRequest = onRoommateRequest,
                            onStudyMateRequest = onStudyMateRequest,
                            onViewProfile = onViewProfile,
                            onOpenConnectHub = onOpenConnectHub,
                            darkSurface = isDark
                        )
                    }
                }
            }
        }

        val worldRankCard: @Composable (Modifier) -> Unit = { cardModifier ->
            ProfileReferenceCard(card, border, cardModifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, null, tint = ink, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("World Rank", color = ink, fontSize = 12.sp)
                        Text(profileRankLabel(profile.worldRank), color = ink,
                            fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (profile.campusRank > 0) "Campus " + profileRankLabel(profile.campusRank)
                            else "BLINK leaderboard",
                            color = muted, fontSize = 9.sp, maxLines = 2
                        )
                    }
                }
            }
        }
        val coinsCard: @Composable (Modifier) -> Unit = { cardModifier ->
            ProfileReferenceCard(card, border, cardModifier,
                onClick = if (isMe) onEarnCoins else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(31.dp).clip(CircleShape).background(Color(0xFFFBBE17)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Paid, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Coins", color = ink, fontSize = 12.sp)
                        Text(
                            if (isMe) NumberFormat.getIntegerInstance().format(blinkCoinBalance) else "Private",
                            color = ink, fontSize = if (isMe) 18.sp else 13.sp,
                            fontWeight = FontWeight.Black, maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(if (isMe) "Earn more coins" else "Owner only",
                            color = muted, fontSize = 9.sp, maxLines = 2)
                    }
                    if (isMe) Icon(Icons.Default.AddBox, "Earn coins", tint = ink, modifier = Modifier.size(21.dp))
                }
            }
        }
        if (accessibilityProfile) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                worldRankCard(Modifier.fillMaxWidth())
                coinsCard(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                worldRankCard(Modifier.weight(1f))
                coinsCard(Modifier.weight(1f))
            }
        }

        Surface(Modifier.fillMaxWidth(), color = if (isDark) Color(0xFF21172E) else Color(0xFFF5F1FF),
            shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(ProfileHex).background(ProfilePurple),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(31.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (accessibilityProfile) {
                        // At 200% fonts even a 320dp phone cannot display
                        // "Current Rank" next to Level and XP without wrapping
                        // the title into disconnected columns.
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Current Rank", color = muted, fontSize = 11.sp)
                            Text(xp.tierLabel, color = ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("Level " + xp.level, color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                if (xp.level >= 100) "MAX LEVEL" else
                                    NumberFormat.getIntegerInstance().format(xp.xpIntoLevel) + " / " +
                                        NumberFormat.getIntegerInstance().format(xp.xpForLevel) + " XP",
                                color = muted, fontSize = 10.sp
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Current Rank", color = muted, fontSize = 11.sp)
                                Text(xp.tierLabel, color = ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Level " + xp.level, color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(if (xp.level >= 100) "MAX LEVEL" else
                                    NumberFormat.getIntegerInstance().format(xp.xpIntoLevel) + " / " +
                                        NumberFormat.getIntegerInstance().format(xp.xpForLevel) + " XP",
                                    color = muted, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { xp.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(20.dp)),
                        color = ProfilePurple, trackColor = Color(0xFFDED4F0)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Connect, engage and complete activities to level up and unlock exclusive rewards.",
                        color = muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }

        ProfileReferenceCard(card, border, Modifier.fillMaxWidth()) {
            ProfileReferenceHeading("Your Badges", ink, muted, onShowBadges)
            Spacer(Modifier.height(9.dp))
            val badges = listOf(
                Triple("Verified Student", Icons.Default.School, Color(0xFF782BEE)),
                Triple("Social Connector", Icons.Default.Groups, Color(0xFFF0A000)),
                Triple("Campus Explorer", Icons.Default.Star, Color(0xFF2584E7)),
                Triple("Event Attendee", Icons.Default.Event, Color(0xFF13A46C)),
                Triple("Top Creator", Icons.Default.Whatshot, Color(0xFFE8322A))
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                badges.forEach { (title, icon, badgeColor) ->
                    val earned = profile.badges.any { badge ->
                        badge.title.equals(title, ignoreCase = true) ||
                            badge.id.replace('_', ' ').equals(title, ignoreCase = true)
                    }
                    Column(
                        modifier = Modifier.weight(1f).clickable(onClick = onShowBadges),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(43.dp).alpha(if (earned) 1f else .48f)
                            .clip(ProfileHex).background(badgeColor), contentAlignment = Alignment.Center) {
                            Icon(icon, if (earned) title + " earned" else title + " locked",
                                tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(title.replaceFirst(" ", "\n"), color = if (earned) ink else muted,
                            fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2)
                    }
                }
            }
        }

        ProfileReferenceCard(card, border, Modifier.fillMaxWidth()) {
            ProfileReferenceHeading("Get Verified", ink, muted, onVerify)
            ProfileReferenceVerifyRow(Icons.Default.School, "Verify Your Student Status",
                "Show you're a real student", studentVerified, ink, muted, onVerify)
            HorizontalDivider(color = border)
            ProfileReferenceVerifyRow(Icons.Default.VerifiedUser, "Verify Your Profile",
                "Build trust and get a verified badge",
                profile.verificationBadge != VerificationBadge.NONE, ink, muted, onVerify)
        }
        // A single statistics row; secondary actions remain below rather than crowding counts.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(NumberFormat.getIntegerInstance().format(postCount),
                    color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Posts", color = muted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(NumberFormat.getIntegerInstance().format(profile.followerCount),
                    color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Followers", color = muted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(NumberFormat.getIntegerInstance().format(profile.followingCount),
                    color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Following", color = muted, fontSize = 10.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onShare) { Text("Share", fontSize = 11.sp) }
            TextButton(onClick = onCopyUsername) { Text("Copy username", fontSize = 11.sp) }
            if (isMe) TextButton(onClick = onVisitors) { Text("Visitors", fontSize = 11.sp) }
        }
        if (isMe && profileCompletion < 100) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
                shape = RoundedCornerShape(14.dp), color = card,
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Complete your profile · $profileCompletion%", color = ink,
                        fontSize = 12.sp, modifier = Modifier.weight(1f),
                        maxLines = 2)
                    Icon(Icons.Default.ChevronRight, contentDescription = "Edit profile", tint = muted)
                }
            }
        }
    }
}

@Composable
private fun ProfileReferenceCard(
    bg: Color, border: Color, modifier: Modifier,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        color = bg, border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(17.dp), shadowElevation = 2.dp
    ) { Column(Modifier.padding(12.dp), content = content) }
}

@Composable
private fun ProfileReferenceHeading(title: String, ink: Color, muted: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text("View All", color = muted, fontSize = 11.sp)
            Icon(Icons.Default.ChevronRight, null, tint = muted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ProfileReferenceVerifyRow(
    icon: ImageVector, title: String, subtitle: String,
    complete: Boolean, ink: Color, muted: Color, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(subtitle, color = muted, fontSize = 10.sp, maxLines = 2)
        }
        if (complete) Icon(Icons.Default.CheckCircle, "Completed", tint = ProfileGreen, modifier = Modifier.size(20.dp))
        Icon(Icons.Default.ChevronRight, null, tint = muted, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun BlinkReferenceProfileBottomNav(
    isDark: Boolean,
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    val bg = if (isDark) Color(0xFF111111) else Color.White
    val ink = if (isDark) Color.White else Color.Black
    val muted = if (isDark) Color.LightGray else Color.Gray
    // Five full text labels cannot fit within 320dp at 200% font scaling.
    // Retain accessible descriptions on every icon-only tab instead of
    // clipping neighboring labels into each other.
    val iconOnly = LocalDensity.current.fontScale >= 1.6f
    Surface(color = bg, modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 2.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                Triple("Home", Icons.Default.Home, "home"),
                Triple("Reels", Icons.Default.SmartDisplay, "reels"),
                Triple("Market", Icons.Default.Storefront, "market"),
                Triple("Connect", Icons.Default.Groups, "connect"),
                Triple("Message", Icons.Default.ChatBubbleOutline, "messages")
            ).forEach { (label, icon, route) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onNavigate(route) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = label,
                        tint = if (route == selectedRoute) ink else muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    if (!iconOnly) {
                        Text(label, color = if (route == selectedRoute) ink else muted,
                            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

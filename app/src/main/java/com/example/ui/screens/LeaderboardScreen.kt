package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LeaderboardUser
import com.example.data.models.VerificationBadge
import com.example.ui.components.FacultyBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    onProfileClick: (String) -> Unit,
    isDark: Boolean
) {
    // Always fill up to 100 positions, using null for empty slots
    val totalLeaderboardCount = 100
    val top1 = users.getOrNull(0)
    val top2 = users.getOrNull(1)
    val top3 = users.getOrNull(2)

    // Generate list for ranks 4 through 100
    val remainingSlots = remember(users) {
        List(totalLeaderboardCount - 3) { index ->
            val rankNumber = index + 4
            val userForSlot = users.getOrNull(index + 3)
            Pair(rankNumber, userForSlot)
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("leaderboard_screen")
    ) {
        // Header
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -40 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Trophy",
                            tint = BlinkGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Campus Leaderboard",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "Weekly top contributors, student builders & creator rankings",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Top 3 Podium with Entrance Animation
        item {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(700)) + slideInVertically(
                    initialOffsetY = { 80 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 2nd Place (Silver)
                    PodiumUserCard(
                        user = top2,
                        rank = 2,
                        accentColor = Color(0xFFC0C0C0),
                        podiumHeight = 110.dp,
                        onProfileClick = onProfileClick,
                        isDark = isDark
                    )

                    // 1st Place (Gold)
                    PodiumUserCard(
                        user = top1,
                        rank = 1,
                        accentColor = BlinkGold,
                        podiumHeight = 140.dp,
                        onProfileClick = onProfileClick,
                        isDark = isDark
                    )

                    // 3rd Place (Bronze)
                    PodiumUserCard(
                        user = top3,
                        rank = 3,
                        accentColor = Color(0xFFCD7F32),
                        podiumHeight = 90.dp,
                        onProfileClick = onProfileClick,
                        isDark = isDark
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Rankings (Top 100)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
            )
        }

        // List of Ranks 4 to 100
        itemsIndexed(remainingSlots) { index, (rank, user) ->
            val animatedScale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.95f,
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = (index % 10) * 30,
                    easing = FastOutSlowInEasing
                ),
                label = "scale"
            )

            val animatedAlpha by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = (index % 10) * 30
                ),
                label = "alpha"
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (user != null) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .scale(animatedScale)
                    .alpha(animatedAlpha)
                    .animateContentSize()
                    .then(
                        if (user != null) {
                            Modifier.clickable { onProfileClick(user.username) }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "#$rank",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (user != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(36.dp)
                    )

                    if (user?.avatar.isNullOrEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Placeholder Avatar",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = user?.avatar,
                            contentDescription = user?.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = user?.fullName ?: "None",
                                fontSize = 14.sp,
                                fontWeight = if (user != null) FontWeight.Bold else FontWeight.Medium,
                                color = if (user != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                            if (user != null && user.verificationBadge != VerificationBadge.NONE) {
                                VerifiedMark(badge = user.verificationBadge, size = 13.dp)
                            }
                            if (user != null && user.faculty.isNotEmpty()) {
                                FacultyBadge(tag = user.faculty)
                            }
                        }

                        Text(
                            text = if (user != null) "@${user.username} • ${user.university}" else "Unclaimed spot",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (user != null) 1f else 0.5f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (user != null) "${user.points}" else "--",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (user != null) BlinkPink else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "points",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumUserCard(
    user: LeaderboardUser?,
    rank: Int,
    accentColor: Color,
    podiumHeight: androidx.compose.ui.unit.Dp,
    onProfileClick: (String) -> Unit,
    isDark: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .then(
                if (user != null) {
                    Modifier.clickable { onProfileClick(user.username) }
                } else {
                    Modifier
                }
            )
    ) {
        // Crown icon for 1st place
        if (rank == 1) {
            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = "Champion",
                tint = BlinkGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Avatar with colored border
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.size(if (rank == 1) 68.dp else 56.dp)
        ) {
            if (user?.avatar.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.5.dp, accentColor.copy(alpha = if (user != null) 1f else 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Placeholder",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(if (rank == 1) 32.dp else 26.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = user?.avatar,
                    contentDescription = user?.fullName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.5.dp, accentColor, CircleShape)
                )
            }

            Surface(
                shape = CircleShape,
                color = accentColor,
                modifier = Modifier
                    .offset(y = 6.dp)
                    .size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = user?.fullName?.split(" ")?.firstOrNull() ?: if (user != null) user.username else "None",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (user != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
            if (user != null && user.verificationBadge != VerificationBadge.NONE) {
                VerifiedMark(badge = user.verificationBadge, size = 12.dp)
            }
        }

        Text(
            text = if (user != null) "${user.points} pts" else "-- pts",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (user != null) BlinkPink else MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Pedestal base
        Surface(
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            color = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(podiumHeight)
                .border(
                    1.dp,
                    accentColor.copy(alpha = if (user != null) 0.4f else 0.2f),
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                Text(
                    text = if (user != null) "${user.streakDays}d 🔥" else "0d 🔥",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (user != null) BlinkGold else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "streak",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
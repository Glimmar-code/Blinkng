package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.FacultyBadge
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    userProfile: UserProfile = UserProfile(),
    onProfileClick: (String) -> Unit,
    isDark: Boolean
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    // Pre-populate campus & world leaderboard lists
    val campusUsers = remember(users) {
        if (users.isNotEmpty()) users else defaultCampusLeaderboard()
    }

    val worldUsers = remember(users) {
        defaultWorldLeaderboard()
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("leaderboard_screen")
    ) {
        // Main Header & Tab Bar
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { -30 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
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
                            text = "Leaderboard",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Swipe ↔ to switch",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = if (pagerState.currentPage == 0)
                        "Top student builders & creators at ${userProfile.university}"
                    else
                        "Global student rankings across top universities worldwide",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Swipe Tab Switcher (Campus Rank vs World Rank)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFF2F4F7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val tabs = listOf(
                            Pair("🏛️ Campus Rank", 0),
                            Pair("🌐 World Rank", 1)
                        )

                        tabs.forEach { (title, pageIndex) ->
                            val isSelected = pagerState.currentPage == pageIndex
                            val bgTabColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                label = "tab_bg"
                            )
                            val textTabColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    if (pageIndex == 0) BlinkGold else BlinkBlue
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                label = "tab_text"
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = bgTabColor,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex)
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = textTabColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Pager: Page 0 = Campus Rank, Page 1 = World Rank
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val isCampusTab = page == 0
            val currentList = if (isCampusTab) campusUsers else worldUsers
            val currentRankNumber = if (isCampusTab) userProfile.campusRank else userProfile.worldRank

            val totalLeaderboardCount = 10
            val top1 = currentList.getOrNull(0)
            val top2 = currentList.getOrNull(1)
            val top3 = currentList.getOrNull(2)

            val remainingSlots = remember(currentList) {
                List(totalLeaderboardCount - 3) { index ->
                    val rankNumber = index + 4
                    val userForSlot = currentList.getOrNull(index + 3)
                    Pair(rankNumber, userForSlot)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // User Rank Showcase Header Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isCampusTab) BlinkGold.copy(alpha = 0.12f) else BlinkBlue.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCampusTab) BlinkGold.copy(alpha = 0.4f) else BlinkBlue.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isCampusTab) BlinkGold.copy(alpha = 0.25f) else BlinkBlue.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = if (isCampusTab) "🏛️" else "🌐",
                                    fontSize = 22.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCampusTab) "Your Campus Standing" else "Your World Standing",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isCampusTab) "Ranked #${currentRankNumber} at ${userProfile.university}"
                                    else "Ranked #${currentRankNumber} Globally",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isCampusTab) BlinkGold else BlinkBlue
                            ) {
                                Text(
                                    text = "#${currentRankNumber}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Top 3 Podium
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
                        text = if (isCampusTab) "Campus Rankings (Top 10)" else "World Rankings (Top 10)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                // List of Ranks 4 to 100
                itemsIndexed(remainingSlots) { index, (rank, user) ->
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0.95f,
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = (index % 10) * 20,
                            easing = FastOutSlowInEasing
                        ),
                        label = "scale"
                    )

                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = (index % 10) * 20
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
                                    color = if (isCampusTab) BlinkGold else BlinkBlue
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

// Sample mock data for Campus Leaderboard fallback
private fun defaultCampusLeaderboard() = listOf(
    LeaderboardUser(
        rank = 1,
        username = "zara_codes",
        fullName = "Zara Adeleke",
        avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&auto=format&fit=crop&q=80",
        points = 14200,
        faculty = "Computer Science",
        university = "University of Lagos",
        level = "400L",
        streakDays = 24,
        verificationBadge = VerificationBadge.GOLD
    ),
    LeaderboardUser(
        rank = 2,
        username = "aluta_daily",
        fullName = "Aluta News Network",
        avatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=500&auto=format&fit=crop&q=80",
        points = 12850,
        faculty = "Engineering",
        university = "University of Lagos",
        level = "Campus Media",
        streakDays = 18,
        verificationBadge = VerificationBadge.GOLD
    ),
    LeaderboardUser(
        rank = 3,
        username = "kemi.adeleke",
        fullName = "Kemi Adeleke",
        avatar = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500&auto=format&fit=crop&q=80",
        points = 9600,
        faculty = "Law",
        university = "University of Lagos",
        level = "300L",
        streakDays = 15,
        verificationBadge = VerificationBadge.BLUE
    ),
    LeaderboardUser(
        rank = 4,
        username = "tunde_shots",
        fullName = "Tunde Bakare",
        avatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&auto=format&fit=crop&q=80",
        points = 8400,
        faculty = "Arts & Humanities",
        university = "University of Lagos",
        level = "500L",
        streakDays = 12,
        verificationBadge = VerificationBadge.NONE
    ),
    LeaderboardUser(
        rank = 5,
        username = "amara.creatives",
        fullName = "Amara Chukwu",
        avatar = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500&auto=format&fit=crop&q=80",
        points = 7200,
        faculty = "Environmental",
        university = "University of Lagos",
        level = "200L",
        streakDays = 9,
        verificationBadge = VerificationBadge.BLUE
    )
)

// Sample mock data for World Leaderboard
private fun defaultWorldLeaderboard() = listOf(
    LeaderboardUser(
        rank = 1,
        username = "ethan_mit",
        fullName = "Ethan Vance",
        avatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&auto=format&fit=crop&q=80",
        points = 28500,
        faculty = "AI & Quantum",
        university = "MIT (USA)",
        level = "PhD Candidate",
        streakDays = 45,
        verificationBadge = VerificationBadge.GOLD
    ),
    LeaderboardUser(
        rank = 2,
        username = "zara_codes",
        fullName = "Zara Adeleke",
        avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&auto=format&fit=crop&q=80",
        points = 24100,
        faculty = "Computer Science",
        university = "University of Lagos (NG)",
        level = "400L",
        streakDays = 24,
        verificationBadge = VerificationBadge.GOLD
    ),
    LeaderboardUser(
        rank = 3,
        username = "sophia_oxford",
        fullName = "Sophia Chen",
        avatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
        points = 21900,
        faculty = "Neuroscience",
        university = "Oxford University (UK)",
        level = "PostGrad",
        streakDays = 30,
        verificationBadge = VerificationBadge.BLUE
    ),
    LeaderboardUser(
        rank = 4,
        username = "aluta_daily",
        fullName = "Aluta News Network",
        avatar = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=500&auto=format&fit=crop&q=80",
        points = 18400,
        faculty = "Engineering",
        university = "University of Lagos (NG)",
        level = "Campus Media",
        streakDays = 18,
        verificationBadge = VerificationBadge.GOLD
    ),
    LeaderboardUser(
        rank = 5,
        username = "alex_stanford",
        fullName = "Alex Rivera",
        avatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&auto=format&fit=crop&q=80",
        points = 16800,
        faculty = "Biotech",
        university = "Stanford University (USA)",
        level = "300L",
        streakDays = 14,
        verificationBadge = VerificationBadge.NONE
    )
)

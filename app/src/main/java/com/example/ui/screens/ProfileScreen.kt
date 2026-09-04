package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.MarketItem
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.components.FacultyBadge
import com.example.ui.components.FollowerGrowthChart
import com.example.ui.components.PostCard
import com.example.ui.components.VerifiedMark
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/* ============================================================================
 * PROFILE SCREEN
 *
 * A polished profile experience with restrained interaction motion:
 *  - Parallax cover header with collapsing title
 *  - Staggered entrance for the identity block
 *  - Spring-driven avatar reveal with a static premium glow
 *  - A real sliding-pill tab indicator (measured, not faked)
 *  - Lazy post and marketplace content for smooth scrolling
 *  - A count-up animation on the follower stat
 *  - A completion bar that animates in from zero
 *  - A refresh FAB that spins while refreshing and hides on scroll-down
 * ==========================================================================*/

private object ProfileMotion {
    val TabIndicatorSpec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    isMe: Boolean,
    userPosts: List<FeedPost>,
    likedPosts: List<FeedPost>,
    savedPosts: List<FeedPost>,
    userMarketItems: List<MarketItem>,
    onBack: () -> Unit,
    onEditProfileClick: () -> Unit,
    onDirectMessage: (String) -> Unit,
    onEndorseSkill: (String) -> Unit,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onMarketItemClick: (MarketItem) -> Unit,
    onOpenGetVerified: () -> Unit = {},
    isDark: Boolean,
    onFollowChanged: (Boolean) -> Unit = {},
    onRefreshProfile: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isFollowing by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var showAvatarViewer by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Entrance choreography — content reveals itself once, on first composition.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        contentVisible = true
    }

    val tabs = remember(isMe) {
        if (isMe) {
            listOf("Posts", "Growth", "Liked", "Saved", "Market", "Skills", "About")
        } else {
            listOf("Posts", "Growth", "Liked", "Market", "Skills", "About")
        }
    }

    val bgColor = if (isDark) DarkBackground else LightBackground
    val cardBg = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkBorder else LightBorder

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    val profileCompletion = remember(profile) { calculateProfileCompletion(profile) }

    val headerCollapsed by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }

    // Parallax offset derived straight from scroll position of the very first item.
    val parallaxOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) {
                (scrollState.firstVisibleItemScrollOffset * 0.5f)
            } else 400f
        }
    }

    // Update the FAB only when scroll direction meaningfully changes. Avoid mutating
    // Compose state from inside derivedStateOf, which can invalidate every scroll frame.
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        var previousIndex = scrollState.firstVisibleItemIndex
        var previousOffset = scrollState.firstVisibleItemScrollOffset
        snapshotFlow {
            scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val nextVisible = when {
                index == 0 && offset < 24 -> true
                index < previousIndex -> true
                index > previousIndex -> false
                offset < previousOffset - 12 -> true
                offset > previousOffset + 12 -> false
                else -> fabVisible
            }
            if (nextVisible != fabVisible) fabVisible = nextVisible
            previousIndex = index
            previousOffset = offset
        }
    }

    val animatedFollowScale by animateFloatAsState(
        targetValue = if (isFollowing) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "followScale"
    )

    // Keep the premium avatar glow static so the whole profile header does not
    // invalidate on every animation frame while the user scrolls.
    val glowAlpha = 0.24f

    val refreshRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = if (isRefreshing) {
            infiniteRepeatable(tween(800, easing = LinearEasing))
        } else {
            tween(0)
        },
        label = "refreshRotation"
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(900)
            isRefreshing = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("profile_screen"),
        color = bgColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 130.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                // ============================================================
                // COVER HEADER — parallax + gradient wash
                // ============================================================
                item(key = "cover") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .graphicsLayer {
                                    translationY = -parallaxOffset * 0.4f
                                    val scaleFactor = 1f + (parallaxOffset / 900f).coerceIn(0f, 0.25f)
                                    scaleX = scaleFactor
                                    scaleY = scaleFactor
                                }
                        ) {
                            if (profile.coverPhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = profile.coverPhotoUrl,
                                    contentDescription = "Profile cover photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(BlinkPink, BlinkPurple, BlinkBlue)
                                            )
                                        )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.60f),
                                            Color.Transparent,
                                            bgColor.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircleToolbarButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack,
                                testTag = "profile_back_btn"
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                CircleToolbarButton(
                                    icon = Icons.Default.Share,
                                    contentDescription = "Share profile",
                                    onClick = { showShareSheet = true }
                                )
                                CircleToolbarButton(
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More profile options",
                                    onClick = { showMoreSheet = true }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = headerCollapsed,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp),
                            enter = fadeIn(tween(200)) + slideInVertically(
                                animationSpec = tween(220),
                                initialOffsetY = { -10 }
                            ),
                            exit = fadeOut(tween(150)) + slideOutVertically(tween(150))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.Black.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = profile.fullName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("@${profile.username}", color = BlinkPink, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // IDENTITY — staggered entrance
                // ============================================================
                item(key = "identity") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .offset(y = (-40).dp)
                    ) {

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {

                            EntranceItem(visible = contentVisible, delayMillis = 0) {
                                Box(modifier = Modifier.size(100.dp)) {
                                    val avatarScale by animateFloatAsState(
                                        targetValue = if (contentVisible) 1f else 0.6f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        ),
                                        label = "avatarScale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scale(avatarScale)
                                            .clip(CircleShape)
                                            .background(BlinkPink.copy(alpha = glowAlpha))
                                            .padding(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(bgColor)
                                                .padding(3.dp)
                                        ) {
                                            AsyncImage(
                                                model = profile.avatarUrl,
                                                contentDescription = "Profile picture",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .clickable { showAvatarViewer = true }
                                            )
                                        }
                                    }

                                    if (profile.onlineNow) {
                                        Box(
                                            modifier = Modifier
                                                .size(19.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(Color(0xFF22C55E), CircleShape)
                                                .border(3.dp, bgColor, CircleShape)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            EntranceItem(visible = contentVisible, delayMillis = 90, fromRight = true) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    if (isMe) {
                                        SmallActionButton(
                                            icon = Icons.Default.Edit,
                                            title = "Edit",
                                            tint = textPrimary,
                                            onClick = onEditProfileClick,
                                            testTag = "profile_edit_btn"
                                        )

                                        SmallActionButton(
                                            icon = Icons.Default.Verified,
                                            title = when (profile.verificationBadge) {
                                                VerificationBadge.GOLD -> "VIP Gold"
                                                VerificationBadge.BLUE -> "Get Gold"
                                                VerificationBadge.NONE -> "Get Verified"
                                            },
                                            tint = when (profile.verificationBadge) {
                                                VerificationBadge.GOLD -> BlinkGold
                                                VerificationBadge.BLUE -> BlinkGold
                                                VerificationBadge.NONE -> BlinkPink
                                            },
                                            onClick = onOpenGetVerified,
                                            testTag = "profile_verify_btn"
                                        )
                                    } else {
                                        SmallActionButton(
                                            icon = Icons.AutoMirrored.Filled.Chat,
                                            title = "Message",
                                            tint = Color.White,
                                            background = BlinkPurple,
                                            onClick = { onDirectMessage(profile.username) },
                                            testTag = "profile_message_btn"
                                        )

                                        SmallActionButton(
                                            icon = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                                            title = if (isFollowing) "Following" else "Follow",
                                            tint = if (isFollowing) textPrimary else if (isDark) BlinkBlack else BlinkCream,
                                            background = if (isFollowing) {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            } else {
                                                if (isDark) BlinkCream else BlinkBlack
                                            },
                                            onClick = {
                                                isFollowing = !isFollowing
                                                onFollowChanged(isFollowing)
                                            },
                                            modifier = Modifier.scale(animatedFollowScale),
                                            testTag = "profile_follow_btn"
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        EntranceItem(visible = contentVisible, delayMillis = 60) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.fullName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                AnimatedVisibility(
                                    visible = profile.verificationBadge != VerificationBadge.NONE,
                                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                                ) {
                                    VerifiedMark(badge = profile.verificationBadge, size = 20.dp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "@${profile.username}",
                            fontSize = 13.sp,
                            color = BlinkPink,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (profile.professionalHeadline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = profile.professionalHeadline,
                                fontSize = 13.5.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(7.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FacultyBadge(tag = profile.faculty)
                            Text(
                                text = "${profile.university} • ${profile.academicLevel}",
                                fontSize = 11.5.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (profile.department.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(profile.department, fontSize = 11.sp, color = textSecondary)
                            }
                        }

                        if (profile.bio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(11.dp))
                            Text(
                                text = profile.bio,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ==========================================================
                        // QUICK ACTIONS
                        // ==========================================================
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            item {
                                OutlinePill(
                                    icon = Icons.Default.Link,
                                    text = "Share profile",
                                    onClick = { showShareSheet = true }
                                )
                            }

                            item {
                                OutlinePill(
                                    icon = Icons.Default.ContentCopy,
                                    text = "Copy username",
                                    onClick = {
                                        clipboard.setText(AnnotatedString("@${profile.username}"))
                                        Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            if (profile.links.website.isNotBlank()) {
                                item {
                                    OutlinePill(
                                        icon = Icons.Default.Language,
                                        text = "Website",
                                        onClick = { openExternalUrl(context, profile.links.website) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ==========================================================
                        // COMPLETION CARD
                        // ==========================================================
                        AnimatedVisibility(
                            visible = isMe && profileCompletion < 100,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                ProfileCompletionCard(
                                    completion = profileCompletion,
                                    animate = contentVisible,
                                    onClick = { onEditProfileClick() }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // ==========================================================
                        // STATS & RANKS
                        // ==========================================================
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ProfileMetric(value = userPosts.size, label = "Posts", animateCount = true)
                                DividerMetric()
                                ProfileMetric(
                                    value = profile.followerCount + if (isFollowing) 1 else 0,
                                    label = "Followers",
                                    animateCount = true
                                )
                                DividerMetric()
                                ProfileMetric(value = profile.followingCount, label = "Following", animateCount = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Daily Streak, World Rank & Campus Rank Showcase Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🔥", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${profile.dailyStreak}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BlinkPink)
                                    }
                                    Text("Daily Streak", fontSize = 9.5.sp, color = textSecondary)
                                }

                                DividerMetric()

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏛️", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("#${profile.campusRank}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BlinkGold)
                                    }
                                    Text("Campus Rank", fontSize = 9.5.sp, color = textSecondary)
                                }

                                DividerMetric()

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🌐", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("#${profile.worldRank}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BlinkBlue)
                                    }
                                    Text("World Rank", fontSize = 9.5.sp, color = textSecondary)
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // PROFILE TRUST
                // ============================================================
                item(key = "trust") {
                    TrustBanner(
                        profile = profile,
                        isMe = isMe,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        borderColor = borderColor,
                        onVerify = onOpenGetVerified
                    )
                }

                // ============================================================
                // TABS — real sliding pill indicator
                // ============================================================
                item(key = "tabs") {
                    AnimatedTabRow(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary
                    )
                }

                // Keep post cards as real LazyColumn items. The old single-item Column
                // composed every profile post at once and animated each one on entry.
                when (selectedTab) {
                    0 -> profilePostItems(
                        keyPrefix = "posts",
                        posts = userPosts,
                        profile = profile,
                        canDelete = isMe,
                        onDelete = onDeletePost,
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onLike = onLikePost,
                        onComment = onCommentPost,
                        onBookmark = onBookmarkPost,
                        onShare = onSharePost,
                        onOptions = onOptionsClick,
                        onProfileClick = onProfileClick
                    )

                    1 -> item(key = "growth") {
                        FollowerGrowthChart(
                            profile = profile,
                            isDark = isDark,
                            onOpenGetVerified = onOpenGetVerified,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }

                    2 -> profilePostItems(
                        keyPrefix = "liked",
                        posts = likedPosts,
                        profile = profile,
                        canDelete = isMe,
                        onDelete = onDeletePost,
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        emptyTitle = "No liked posts yet ❤️",
                        emptySubtitle = "Posts you like will be collected here.",
                        onLike = onLikePost,
                        onComment = onCommentPost,
                        onBookmark = onBookmarkPost,
                        onShare = onSharePost,
                        onOptions = onOptionsClick,
                        onProfileClick = onProfileClick
                    )

                    3 -> if (isMe) {
                        profilePostItems(
                            keyPrefix = "saved",
                            posts = savedPosts,
                            profile = profile,
                            canDelete = true,
                            onDelete = onDeletePost,
                            isDark = isDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            emptyTitle = "Nothing saved yet 🔖",
                            emptySubtitle = "Save useful campus posts, tips and deals.",
                            onLike = onLikePost,
                            onComment = onCommentPost,
                            onBookmark = onBookmarkPost,
                            onShare = onSharePost,
                            onOptions = onOptionsClick,
                            onProfileClick = onProfileClick
                        )
                    } else {
                        profileMarketItems(
                            items = userMarketItems,
                            isDark = isDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onItemClick = onMarketItemClick
                        )
                    }

                    4 -> if (isMe) {
                        profileMarketItems(
                            items = userMarketItems,
                            isDark = isDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onItemClick = onMarketItemClick
                        )
                    } else item(key = "skills") {
                        SkillsAndBadgesSection(
                            profile, isMe, cardBg, borderColor, textPrimary, textSecondary,
                            onEndorseSkill, onOpenGetVerified
                        )
                    }

                    5 -> item(key = if (isMe) "skills" else "about") {
                        if (isMe) {
                            SkillsAndBadgesSection(
                                profile, isMe, cardBg, borderColor, textPrimary, textSecondary,
                                onEndorseSkill, onOpenGetVerified
                            )
                        } else {
                            AboutSection(profile, cardBg, borderColor, textPrimary, textSecondary)
                        }
                    }

                    6 -> item(key = "about") {
                        AboutSection(profile, cardBg, borderColor, textPrimary, textSecondary)
                    }
                }
            }

            // ============================================================
            // FLOATING REFRESH — hides on scroll-down, spins while active
            // ============================================================
            AnimatedVisibility(
                visible = fabVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 82.dp),
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        isRefreshing = true
                        onRefreshProfile()
                    },
                    containerColor = cardBg,
                    contentColor = BlinkPink,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh profile",
                        modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }
                    )
                }
            }
        }
    }

    // ================================================================
    // AVATAR VIEWER
    // ================================================================
    if (showAvatarViewer) {
        AlertDialog(
            onDismissRequest = { showAvatarViewer = false },
            confirmButton = {},
            text = {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = "Full profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                )
            }
        )
    }

    // ================================================================
    // SHARE SHEET
    // ================================================================
    if (showShareSheet) {
        ProfileShareSheet(
            username = profile.username,
            fullName = profile.fullName,
            onDismiss = { showShareSheet = false },
            onCopy = {
                clipboard.setText(AnnotatedString("https://blink.app/@${profile.username}"))
                Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                showShareSheet = false
            }
        )
    }

    // ================================================================
    // MORE SHEET
    // ================================================================
    if (showMoreSheet) {
        ProfileMoreSheet(
            isMe = isMe,
            profile = profile,
            onDismiss = { showMoreSheet = false }
        )
    }
}

// =====================================================================
// ENTRANCE HELPER — fade + slide, staggered by delayMillis
// =====================================================================

@Composable
private fun EntranceItem(
    visible: Boolean,
    delayMillis: Int,
    fromRight: Boolean = false,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(320, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        ) + slideInHorizontally(
            animationSpec = tween(360, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { full -> if (fromRight) full / 4 else -full / 4 }
        ),
        exit = fadeOut()
    ) {
        content()
    }
}

// =====================================================================
// ANIMATED TAB ROW — measured, spring-driven sliding indicator
// =====================================================================

@Composable
private fun AnimatedTabRow(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color
) {
    val density = LocalDensity.current
    val tabOffsets = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() } // index -> (x, width)
    val scrollState = rememberLazyListState()

    val indicatorX by animateDpAsState(
        targetValue = tabOffsets[selectedTab]?.first ?: 0.dp,
        animationSpec = ProfileMotion.TabIndicatorSpec,
        label = "tabIndicatorX"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = tabOffsets[selectedTab]?.second ?: 0.dp,
        animationSpec = ProfileMotion.TabIndicatorSpec,
        label = "tabIndicatorWidth"
    )

    Box(
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
    ) {
        // Sliding highlight, drawn behind the tab labels.
        if (tabOffsets.containsKey(selectedTab)) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .offset(x = indicatorX)
                    .width(indicatorWidth)
                    .height(32.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        LazyRow(
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(tabs.indices.toList(), key = { it }) { index ->
                val selected = selectedTab == index
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else textPrimary,
                    animationSpec = tween(220),
                    label = "tabTextColor"
                )

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) Color.Transparent else borderColor),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) }
                        .onGloballyPositioned { coords ->
                            val x = with(density) { coords.positionInParent().x.toDp() }
                            val w = with(density) { coords.size.width.toDp() }
                            tabOffsets[index] = x to w
                        }
                        .testTag("profile_tab_$index")
                ) {
                    Text(
                        tabs[index],
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Auto-scroll the row so the active tab stays in view.
        LaunchedEffect(selectedTab) {
            scrollState.animateScrollToItem(
                index = (selectedTab - 1).coerceAtLeast(0)
            )
        }
    }
}

// =====================================================================
// PROFILE POSTS
// =====================================================================

private fun LazyListScope.profilePostItems(
    keyPrefix: String,
    posts: List<FeedPost>,
    profile: UserProfile,
    canDelete: Boolean,
    onDelete: (String) -> Unit,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    emptyTitle: String = "No posts yet 📝",
    emptySubtitle: String = "Posts published by @${profile.username} will show up here.",
    onLike: (String) -> Unit,
    onComment: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onShare: (String) -> Unit,
    onOptions: (FeedPost) -> Unit,
    onProfileClick: (String) -> Unit
) {
    if (posts.isEmpty()) {
        item(key = "${keyPrefix}_empty", contentType = "profile_empty") {
            EmptyProfileState(
                title = emptyTitle,
                subtitle = emptySubtitle,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
        return
    }

    items(
        items = posts,
        key = { post -> "${keyPrefix}_${post.id}" },
        contentType = { "profile_post" }
    ) { post ->
        PostCard(
            post = post,
            isDark = isDark,
            onLike = { onLike(post.id) },
            onComment = { onComment(post.id) },
            onBookmark = { onBookmark(post.id) },
            onShare = { onShare(post.id) },
            onOptionsClick = { onOptions(post) },
            onProfileClick = onProfileClick,
            isAuthor = canDelete && post.author.equals(profile.username, true),
            onDelete = { onDelete(post.id) }
        )
    }
}

// =====================================================================
// COMPLETION
// =====================================================================

@Composable
private fun ProfileCompletionCard(
    completion: Int,
    animate: Boolean,
    onClick: () -> Unit
) {
    val animatedCompletion by animateIntAsState(
        targetValue = if (animate) completion else 0,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "completionProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BlinkPink.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, BlinkPink.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BlinkPink,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(9.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Complete your profile", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "A complete profile gets more trust and discoverability.",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "$animatedCompletion%",
                    color = BlinkPink,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedCompletion / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = BlinkPink,
                trackColor = BlinkPink.copy(alpha = 0.10f)
            )
        }
    }
}

// =====================================================================
// TRUST
// =====================================================================

@Composable
private fun TrustBanner(
    profile: UserProfile,
    isMe: Boolean,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    onVerify: () -> Unit
) {
    val verified = profile.verificationBadge != VerificationBadge.NONE

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (verified) {
            when (profile.verificationBadge) {
                VerificationBadge.GOLD -> BlinkGold.copy(alpha = 0.10f)
                VerificationBadge.BLUE -> BlinkBlue.copy(alpha = 0.08f)
                VerificationBadge.NONE -> cardBg
            }
        } else cardBg,
        border = BorderStroke(
            1.dp,
            when (profile.verificationBadge) {
                VerificationBadge.GOLD -> BlinkGold
                VerificationBadge.BLUE -> BlinkBlue
                VerificationBadge.NONE -> borderColor
            }.copy(alpha = if (verified) 0.55f else 1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerifiedMark(
                badge = if (verified) profile.verificationBadge else VerificationBadge.BLUE,
                size = 28.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        profile.verificationBadge == VerificationBadge.GOLD -> "Gold VIP verified"
                        profile.verificationBadge == VerificationBadge.BLUE -> "Blue verified student"
                        else -> "Build trust on Blink"
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Text(
                    if (verified) {
                        "Verified identity and stronger profile trust."
                    } else {
                        "Complete your student profile and verification."
                    },
                    fontSize = 9.5.sp,
                    color = textSecondary
                )
            }

            if (isMe) {
                TextButton(onClick = onVerify) {
                    Text(
                        when (profile.verificationBadge) {
                            VerificationBadge.GOLD -> "VIP"
                            VerificationBadge.BLUE -> "Upgrade"
                            VerificationBadge.NONE -> "Verify"
                        },
                        color = when (profile.verificationBadge) {
                            VerificationBadge.GOLD -> BlinkGold
                            VerificationBadge.BLUE -> BlinkBlue
                            VerificationBadge.NONE -> BlinkPink
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// =====================================================================
// SKILLS
// =====================================================================

@Composable
private fun SkillsAndBadgesSection(
    profile: UserProfile,
    isMe: Boolean,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onEndorseSkill: (String) -> Unit,
    onOpenGetVerified: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Skills & Campus Endorsements", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textPrimary)

        profile.skillEndorsements.forEachIndexed { index, endorsement ->
            var visible by remember(endorsement.skill) { mutableStateOf(false) }
            LaunchedEffect(endorsement.skill) {
                delay(index * 40L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(240)) + slideInVertically(initialOffsetY = { it / 8 })
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEndorseSkill(endorsement.skill) }
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (endorsement.endorsedByMe) {
                                BlinkGold.copy(alpha = 0.14f)
                            } else {
                                BlinkPink.copy(alpha = 0.10f)
                            }
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (endorsement.endorsedByMe) BlinkGold else BlinkPink,
                                modifier = Modifier.padding(9.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(endorsement.skill, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = textPrimary)
                            Text("Campus endorsement", fontSize = 9.sp, color = textSecondary)
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (endorsement.endorsedByMe) BlinkGold else BlinkPink.copy(alpha = 0.10f)
                        ) {
                            Text(
                                "${endorsement.endorsements}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (endorsement.endorsedByMe) Color.Black else BlinkPink,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        Text("Campus achievements", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textPrimary)

        profile.badges.forEachIndexed { index, badge ->
            var visible by remember(badge.title) { mutableStateOf(false) }
            LaunchedEffect(badge.title) {
                delay(index * 40L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.94f)
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = BlinkGold.copy(alpha = 0.12f)) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = BlinkGold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(badge.description, fontSize = 9.5.sp, color = textSecondary)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// ABOUT
// =====================================================================

@Composable
private fun AboutSection(
    profile: UserProfile,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(title = "Student identity", cardBg = cardBg, borderColor = borderColor, textPrimary = textPrimary) {
            ProfileInfoRow(Icons.Default.School, "University", profile.university, textPrimary, textSecondary)
            ProfileInfoRow(Icons.Default.AccountBalance, "Faculty", profile.faculty, textPrimary, textSecondary)
            ProfileInfoRow(Icons.Default.MenuBook, "Department", profile.department, textPrimary, textSecondary)
            ProfileInfoRow(Icons.Default.TrendingUp, "Academic level", profile.academicLevel, textPrimary, textSecondary)
        }

        InfoCard(title = "Contact", cardBg = cardBg, borderColor = borderColor, textPrimary = textPrimary) {
            if (profile.email.value.isNotBlank()) {
                ProfileInfoRow(Icons.Default.Email, "Email", profile.email.value, textPrimary, textSecondary)
            }
            if (profile.phone.value.isNotBlank()) {
                ProfileInfoRow(Icons.Default.Phone, "Phone", profile.phone.value, textPrimary, textSecondary)
            }
            if (profile.currentCityState.isNotBlank()) {
                ProfileInfoRow(Icons.Default.LocationOn, "Location", profile.currentCityState, textPrimary, textSecondary)
            }
        }

        InfoCard(title = "Links & portfolio", cardBg = cardBg, borderColor = borderColor, textPrimary = textPrimary) {
            if (profile.links.website.isNotBlank()) {
                ProfileLink(Icons.Default.Language, "Website", profile.links.website)
            }
            if (profile.links.linkedin.isNotBlank()) {
                ProfileLink(Icons.Default.Link, "LinkedIn", profile.links.linkedin)
            }
            if (profile.links.twitter.isNotBlank()) {
                ProfileLink(Icons.Default.Share, "X / Twitter", profile.links.twitter)
            }
            if (profile.links.instagram.isNotBlank()) {
                ProfileLink(Icons.Default.CameraAlt, "Instagram", profile.links.instagram)
            }
        }
    }
}

// =====================================================================
// MARKET
// =====================================================================

private fun LazyListScope.profileMarketItems(
    items: List<MarketItem>,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    onItemClick: (MarketItem) -> Unit
) {
    if (items.isEmpty()) {
        item(key = "market_empty", contentType = "profile_empty") {
            EmptyProfileState(
                title = "No market listings 🛒",
                subtitle = "Items listed by this student will appear here.",
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
        return
    }

    items(
        items = items.chunked(2),
        key = { row -> "market_${row.joinToString("_") { it.id }}" },
        contentType = { "market_row" }
    ) { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            row.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    ProductCard(item = item, onClick = { onItemClick(item) }, isDark = isDark)
                }
            }

            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// =====================================================================
// COMMON UI
// =====================================================================

@Composable
private fun CircleToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "toolbarBtnScale"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .testTag(testTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            }
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    background: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val animatedBackground by animateColorAsState(
        targetValue = background ?: MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "actionBtnBg"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = animatedBackground,
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                label = "actionBtnIcon"
            ) { animatedIcon ->
                Icon(animatedIcon, contentDescription = title, tint = tint, modifier = Modifier.size(15.dp))
            }

            Spacer(modifier = Modifier.width(5.dp))

            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 2 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "actionBtnText"
            ) { animatedTitle ->
                Text(animatedTitle, color = tint, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun OutlinePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pillScale"
    )

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }
}

@Composable
private fun ProfileMetric(
    value: Int,
    label: String,
    animateCount: Boolean = false
) {
    val displayValue by if (animateCount) {
        val animatable = remember { Animatable(0f) }
        LaunchedEffect(value) {
            animatable.animateTo(
                targetValue = value.toFloat(),
                animationSpec = tween(900, easing = FastOutSlowInEasing)
            )
        }
        remember { derivedStateOf { animatable.value.roundToInt() } }
    } else {
        remember(value) { mutableIntStateOf(value) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(displayValue.toString(), fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DividerMetric() {
    Box(
        modifier = Modifier
            .height(25.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun EmptyProfileState(
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + scaleIn(initialScale = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = BlinkPink.copy(alpha = 0.10f)) {
                Icon(
                    Icons.Default.Inbox,
                    contentDescription = null,
                    tint = BlinkPink,
                    modifier = Modifier.padding(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(11.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            content()
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    textPrimary: Color,
    textSecondary: Color
) {
    if (value.isBlank()) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = BlinkPink, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Column {
            Text(title, fontSize = 8.5.sp, color = textSecondary)
            Text(value, fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProfileLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    url: String
) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (pressed) 0.6f else 1f, tween(100), label = "linkAlpha")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable {
                pressed = true
                openExternalUrl(context, url)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = BlinkPink, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(9.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(url, fontSize = 11.sp, color = BlinkPurple, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Icon(Icons.Default.OpenInNew, contentDescription = "Open $title", modifier = Modifier.size(15.dp))
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileShareSheet(
    username: String,
    fullName: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Share profile", fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text("@$username", fontSize = 11.sp, color = BlinkPink)

            Spacer(modifier = Modifier.height(15.dp))

            FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(7.dp))
                Text("Copy profile link")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "$fullName • Blink Campus",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 15.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMoreSheet(
    isMe: Boolean,
    profile: UserProfile,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Profile options", fontSize = 21.sp, fontWeight = FontWeight.Black)

            Spacer(modifier = Modifier.height(10.dp))

            ProfileOption(Icons.Default.Search, "Search posts", "Find posts from @${profile.username}")
            ProfileOption(Icons.Default.Share, "Share profile", "Share this student's Blink profile")
            ProfileOption(Icons.Default.NotificationsNone, "Profile notifications", "Get updates when this profile posts")

            if (!isMe) {
                ProfileOption(Icons.Default.VolumeOff, "Mute user", "See fewer updates from this profile")
                ProfileOption(Icons.Default.Flag, "Report profile", "Report suspicious or inappropriate activity")
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProfileOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    var pressed by remember { mutableStateOf(false) }
    val translateX by animateDpAsState(if (pressed) 4.dp else 0.dp, tween(120), label = "optionTranslate")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = translateX)
            .clickable {
                pressed = true
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, contentDescription = title, modifier = Modifier.padding(10.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            Text(subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(140)
            pressed = false
        }
    }
}

// =====================================================================
// HELPERS
// =====================================================================

private fun calculateProfileCompletion(profile: UserProfile): Int {
    val values = listOf(
        profile.fullName.isNotBlank(),
        profile.username.isNotBlank(),
        profile.avatarUrl.isNotBlank(),
        profile.coverPhotoUrl.isNotBlank(),
        profile.professionalHeadline.isNotBlank(),
        profile.currentJobTitle.isNotBlank(),
        profile.bio.isNotBlank(),
        profile.university.isNotBlank(),
        profile.faculty.isNotBlank(),
        profile.department.isNotBlank(),
        profile.academicLevel.isNotBlank(),
        profile.email.value.isNotBlank(),
        profile.phone.value.isNotBlank(),
        profile.links.website.isNotBlank(),
        profile.links.linkedin.isNotBlank()
    )

    return (values.count { it } * 100 / values.size).coerceIn(0, 100)
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    try {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }

        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
    }
}

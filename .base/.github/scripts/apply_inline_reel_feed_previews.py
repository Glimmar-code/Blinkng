from pathlib import Path


def require_replace(text: str, old: str, new: str, label: str, *, replace_all: bool = False) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"Could not find expected source block for {label}")
    return text.replace(old, new) if replace_all else text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Premium home feed: interleave ranked reel teasers every 10-20 visible posts.
# The post/reel lists themselves are never reordered, so server ranking remains
# authoritative. Inline previews are deliberately NOT wired to view tracking.
# -----------------------------------------------------------------------------
premium_path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
premium = premium_path.read_text(encoding="utf-8")

random_import = "import kotlin.random.Random\n"
if random_import not in premium:
    anchor = "import kotlinx.coroutines.flow.collectLatest\n"
    if anchor not in premium:
        raise RuntimeError("Could not find PremiumFeedScreen import anchor")
    premium = premium.replace(anchor, anchor + random_import, 1)

old_enum = "private enum class PremiumFeedFilter { ALL, PHOTOS, POLLS }\n"
new_enum = '''private enum class PremiumFeedFilter { ALL, PHOTOS, POLLS }

private sealed interface PremiumHomeRow {
    data class PostRow(val post: FeedPost, val sourceIndex: Int) : PremiumHomeRow
    data class ReelPreviewRow(val reel: FeedPost, val slot: Int) : PremiumHomeRow
}

private fun buildPremiumHomeRows(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    seed: Int
): List<PremiumHomeRow> {
    if (posts.isEmpty()) return emptyList()
    if (reels.isEmpty()) {
        return posts.mapIndexed { index, post -> PremiumHomeRow.PostRow(post, index) }
    }

    val random = Random(seed)
    val rows = ArrayList<PremiumHomeRow>(posts.size + (posts.size / 10) + 1)
    var postsSincePreview = 0
    var nextGap = random.nextInt(10, 21)
    var reelSlot = 0

    posts.forEachIndexed { index, post ->
        rows += PremiumHomeRow.PostRow(post, index)
        postsSincePreview += 1

        if (postsSincePreview >= nextGap) {
            // Keep the exact ranked reel order supplied by the existing feed algorithm.
            // Cycling only occurs if the post list is longer than the currently loaded
            // reel page, and inline autoplay itself never emits a view event.
            rows += PremiumHomeRow.ReelPreviewRow(
                reel = reels[reelSlot % reels.size],
                slot = reelSlot
            )
            reelSlot += 1
            postsSincePreview = 0
            nextGap = random.nextInt(10, 21)
        }
    }
    return rows
}
'''
premium = require_replace(premium, old_enum, new_enum, "premium home row model")

old_following_state = '''    val followingIds by FollowStateStore.followingIds.collectAsState()

    LaunchedEffect(currentUsername) {
'''
new_following_state = '''    val followingIds by FollowStateStore.followingIds.collectAsState()
    var launchReelId by rememberSaveable(resumeUserKey) { mutableStateOf<String?>(null) }
    var launchReelPositionMs by rememberSaveable(resumeUserKey) { mutableStateOf(0L) }

    fun openReelsAt(reelId: String? = null, positionMs: Long = 0L) {
        launchReelId = reelId
        launchReelPositionMs = positionMs.coerceAtLeast(0L)
        onSubTabChanged(1)
    }

    LaunchedEffect(currentUsername) {
'''
premium = require_replace(premium, old_following_state, new_following_state, "reel launch state")

old_home_call = '''        0 -> PremiumHomeFeed(
            posts = posts,
            currentUsername = currentUsername,
'''
new_home_call = '''        0 -> PremiumHomeFeed(
            posts = posts,
            reels = reels,
            currentUsername = currentUsername,
'''
premium = require_replace(premium, old_home_call, new_home_call, "pass reels into premium home")

old_home_pagination = '''            hasMorePosts = hasMorePosts,
            isLoadingMorePosts = isLoadingMorePosts,
            homeReselectSignal = homeReselectSignal,
'''
new_home_pagination = '''            hasMorePosts = hasMorePosts,
            hasMoreReels = hasMoreReels,
            isLoadingMorePosts = isLoadingMorePosts,
            isLoadingMoreReels = isLoadingMoreReels,
            homeReselectSignal = homeReselectSignal,
'''
premium = require_replace(premium, old_home_pagination, new_home_pagination, "premium home pagination args")

old_home_load_more = '''            onVotePoll = onVotePoll,
            onLoadMorePosts = onLoadMorePosts,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            onGameClick = { onSubTabChanged(3) },
            onReelClick = { onSubTabChanged(1) }
        )
'''
new_home_load_more = '''            onVotePoll = onVotePoll,
            onLoadMorePosts = onLoadMorePosts,
            onLoadMoreReels = onLoadMoreReels,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            onGameClick = { onSubTabChanged(3) },
            onReelClick = { openReelsAt() },
            onOpenInlineReel = { reelId, positionMs -> openReelsAt(reelId, positionMs) }
        )
'''
premium = require_replace(premium, old_home_load_more, new_home_load_more, "premium home reel callbacks")

old_feed_screen_tail = '''            homeReselectSignal = homeReselectSignal,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange
        )
'''
new_feed_screen_tail = '''            homeReselectSignal = homeReselectSignal,
            onBottomBarVisibilityChange = onBottomBarVisibilityChange,
            initialReelId = launchReelId,
            initialReelPositionMs = launchReelPositionMs
        )
'''
premium = require_replace(premium, old_feed_screen_tail, new_feed_screen_tail, "selected reel handoff")

# Any ordinary Reel-tab navigation clears a previous inline launch target.
premium = premium.replace("onReelClick = { onSubTabChanged(1) }", "onReelClick = { openReelsAt() }")
premium = premium.replace("onReel = { onSubTabChanged(1) }", "onReel = { openReelsAt() }")

old_home_signature = '''private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    currentUsername: String,
'''
new_home_signature = '''private fun PremiumHomeFeed(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    currentUsername: String,
'''
premium = require_replace(premium, old_home_signature, new_home_signature, "premium home reels signature")

old_home_flags = '''    errorMessage: String?,
    hasMorePosts: Boolean,
    isLoadingMorePosts: Boolean,
    homeReselectSignal: Int,
'''
new_home_flags = '''    errorMessage: String?,
    hasMorePosts: Boolean,
    hasMoreReels: Boolean,
    isLoadingMorePosts: Boolean,
    isLoadingMoreReels: Boolean,
    homeReselectSignal: Int,
'''
premium = require_replace(premium, old_home_flags, new_home_flags, "premium home reel pagination flags")

old_home_callbacks = '''    onVotePoll: (postId: String, optionId: String) -> Unit,
    onLoadMorePosts: () -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onGameClick: () -> Unit,
    onReelClick: () -> Unit
) {
'''
new_home_callbacks = '''    onVotePoll: (postId: String, optionId: String) -> Unit,
    onLoadMorePosts: () -> Unit,
    onLoadMoreReels: () -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onGameClick: () -> Unit,
    onReelClick: () -> Unit,
    onOpenInlineReel: (reelId: String, positionMs: Long) -> Unit
) {
'''
premium = require_replace(premium, old_home_callbacks, new_home_callbacks, "premium home inline reel callback")

old_filtered_tail = '''    val filteredPosts = remember(posts, filter, laneIndex, followedAuthorKeys) {
        val rankedNormalPosts = posts.filterNot { it.isReel || !it.videoUrl.isNullOrBlank() }
        val lanePosts = if (laneIndex == 1) {
            // Preserve the exact ranking/order delivered by the normal feed algorithm;
            // Following is only an author-membership filter over that ranked list.
            rankedNormalPosts.filter { post ->
                post.author.trim().removePrefix("@").lowercase() in followedAuthorKeys
            }
        } else {
            rankedNormalPosts
        }
        lanePosts.filter { post ->
            when (filter) {
                PremiumFeedFilter.ALL -> true
                PremiumFeedFilter.PHOTOS -> post.images.any { it.isNotBlank() && !it.equals("null", true) }
                PremiumFeedFilter.POLLS -> post.poll != null
            }
        }
    }

    LaunchedEffect(isOnline, isLoading, posts.isEmpty(), filteredPosts.isEmpty(), filter, laneIndex) {
'''
new_filtered_tail = '''    val filteredPosts = remember(posts, filter, laneIndex, followedAuthorKeys) {
        val rankedNormalPosts = posts.filterNot { it.isReel || !it.videoUrl.isNullOrBlank() }
        val lanePosts = if (laneIndex == 1) {
            // Preserve the exact ranking/order delivered by the normal feed algorithm;
            // Following is only an author-membership filter over that ranked list.
            rankedNormalPosts.filter { post ->
                post.author.trim().removePrefix("@").lowercase() in followedAuthorKeys
            }
        } else {
            rankedNormalPosts
        }
        lanePosts.filter { post ->
            when (filter) {
                PremiumFeedFilter.ALL -> true
                PremiumFeedFilter.PHOTOS -> post.images.any { it.isNotBlank() && !it.equals("null", true) }
                PremiumFeedFilter.POLLS -> post.poll != null
            }
        }
    }

    val rankedInlineReels = remember(reels, laneIndex, followedAuthorKeys) {
        reels.filter { reel ->
            val hasPlayableVideo = reel.isReel && !reel.videoUrl.isNullOrBlank()
            val authorKey = reel.authorUsername
                .ifBlank { reel.author }
                .trim()
                .removePrefix("@")
                .lowercase()
            hasPlayableVideo && (laneIndex == 0 || authorKey in followedAuthorKeys)
        }
    }
    val reelMixSeed = remember(laneResumeKey, filter, filteredPosts.firstOrNull()?.id) {
        "$laneResumeKey:${filter.name}:${filteredPosts.firstOrNull()?.id.orEmpty()}".hashCode()
    }
    val homeRows = remember(filteredPosts, rankedInlineReels, reelMixSeed) {
        buildPremiumHomeRows(filteredPosts, rankedInlineReels, reelMixSeed)
    }
    var activeInlineReelKey by remember(laneResumeKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(isOnline, isLoading, posts.isEmpty(), filteredPosts.isEmpty(), filter, laneIndex) {
'''
premium = require_replace(premium, old_filtered_tail, new_filtered_tail, "build inline reel rows")

old_near_end = '''    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            filteredPosts.isNotEmpty() && last >= filteredPosts.lastIndex - 3
        }
    }
'''
new_near_end = '''    val nearEnd by remember(homeRows) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            homeRows.isNotEmpty() && last >= homeRows.lastIndex - 3
        }
    }
'''
premium = require_replace(premium, old_near_end, new_near_end, "near-end calculation with reel rows")

old_pagination_effect = '''    LaunchedEffect(nearEnd, hasMorePosts, isLoadingMorePosts, laneIndex) {
        // Pagination remains the normal ranked feed pagination. Filtering happens after
        // each page arrives, preserving the server algorithm for followed authors.
        if (nearEnd && hasMorePosts && !isLoadingMorePosts) onLoadMorePosts()
    }

    LaunchedEffect(listState, filteredPosts) {
'''
new_pagination_effect = '''    LaunchedEffect(
        nearEnd,
        hasMorePosts,
        hasMoreReels,
        isLoadingMorePosts,
        isLoadingMoreReels,
        laneIndex
    ) {
        // Pagination remains the normal ranked server pagination. We only request the next
        // reel page alongside a deep For You scroll so the teaser pool can grow naturally.
        if (nearEnd && hasMorePosts && !isLoadingMorePosts) onLoadMorePosts()
        if (nearEnd && laneIndex == 0 && hasMoreReels && !isLoadingMoreReels) onLoadMoreReels()
    }

    LaunchedEffect(listState, homeRows) {
'''
premium = require_replace(premium, old_pagination_effect, new_pagination_effect, "home post/reel pagination")

old_tracker_end = '''                impressionTracker.update(ids).forEach(latestViewed)
            }
    }

    AnimatedVisibility(
'''
new_tracker_end = '''                impressionTracker.update(ids).forEach(latestViewed)
            }
    }

    // Inline reels use visibility only to control their two-second muted teaser. They are
    // intentionally excluded from PostImpressionTracker/trackContentExposure, so autoplay
    // cannot create fake views or alter the existing ranking/view-weight system.
    LaunchedEffect(listState, homeRows) {
        snapshotFlow { listState.layoutInfo }
            .collectLatest { layout ->
                activeInlineReelKey = layout.visibleItemsInfo.firstOrNull { item ->
                    val key = item.key as? String ?: return@firstOrNull false
                    key.startsWith("reel_preview:") && qualifiesForPostImpression(
                        itemOffset = item.offset,
                        itemSize = item.size,
                        viewportStart = layout.viewportStartOffset,
                        viewportEnd = layout.viewportEndOffset
                    )
                }?.key as? String
            }
    }

    AnimatedVisibility(
'''
premium = require_replace(premium, old_tracker_end, new_tracker_end, "inline reel visibility control")

old_items = '''                            else -> {
                                items(
                                    count = filteredPosts.size,
                                    key = { index -> "post:${filteredPosts[index].id}" },
                                    contentType = { index -> premiumPostContentType(filteredPosts[index]) }
                                ) { index ->
                                    val post = filteredPosts[index]
                                    PremiumPostEntrance(index = index) {
                                        PostCard(
                                            post = post,
                                            isDark = true,
                                            onLike = { onLikePost(post.id) },
                                            onComment = { onCommentPost(post.id) },
                                            onBookmark = { onBookmarkPost(post.id) },
                                            onRepost = { onRepostPost(post.id) },
                                            onShare = { onSharePost(post.id) },
                                            onOptionsClick = { onOptionsClick(post) },
                                            onProfileClick = onProfileClick,
                                            onVotePoll = onVotePoll,
                                            isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
                                                    post.authorUsername.removePrefix("@").equals(currentUsername.removePrefix("@"), ignoreCase = true),
                                            onDelete = { onDeletePost(post.id) }
                                        )
                                    }
                                }
                                if (isLoadingMorePosts) {
'''
new_items = '''                            else -> {
                                items(
                                    count = homeRows.size,
                                    key = { index ->
                                        when (val row = homeRows[index]) {
                                            is PremiumHomeRow.PostRow -> "post:${row.post.id}"
                                            is PremiumHomeRow.ReelPreviewRow -> "reel_preview:${row.slot}:${row.reel.id}"
                                        }
                                    },
                                    contentType = { index ->
                                        when (val row = homeRows[index]) {
                                            is PremiumHomeRow.PostRow -> premiumPostContentType(row.post)
                                            is PremiumHomeRow.ReelPreviewRow -> 16
                                        }
                                    }
                                ) { index ->
                                    when (val row = homeRows[index]) {
                                        is PremiumHomeRow.PostRow -> {
                                            val post = row.post
                                            PremiumPostEntrance(index = row.sourceIndex) {
                                                PostCard(
                                                    post = post,
                                                    isDark = true,
                                                    onLike = { onLikePost(post.id) },
                                                    onComment = { onCommentPost(post.id) },
                                                    onBookmark = { onBookmarkPost(post.id) },
                                                    onRepost = { onRepostPost(post.id) },
                                                    onShare = { onSharePost(post.id) },
                                                    onOptionsClick = { onOptionsClick(post) },
                                                    onProfileClick = onProfileClick,
                                                    onVotePoll = onVotePoll,
                                                    isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
                                                            post.authorUsername.removePrefix("@").equals(currentUsername.removePrefix("@"), ignoreCase = true),
                                                    onDelete = { onDeletePost(post.id) }
                                                )
                                            }
                                        }

                                        is PremiumHomeRow.ReelPreviewRow -> {
                                            val previewKey = "reel_preview:${row.slot}:${row.reel.id}"
                                            InlineReelPreviewCard(
                                                reel = row.reel,
                                                isActive = activeInlineReelKey == previewKey,
                                                onContinue = { positionMs ->
                                                    onOpenInlineReel(row.reel.id, positionMs)
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                if (isLoadingMorePosts) {
'''
premium = require_replace(premium, old_items, new_items, "interleaved premium home list")

premium_path.write_text(premium, encoding="utf-8")


# -----------------------------------------------------------------------------
# Reels screen: support opening a specific reel at the teaser playback position,
# and expose a muted two-second inline preview card that does not track views.
# -----------------------------------------------------------------------------
reels_path = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
reels_src = reels_path.read_text(encoding="utf-8")

old_video_signature_tail = '''    onHomeClick: () -> Unit = onBackToPosts,
    onConnectClick: () -> Unit = {},
    onGameClick: () -> Unit = {}
) {
'''
new_video_signature_tail = '''    onHomeClick: () -> Unit = onBackToPosts,
    onConnectClick: () -> Unit = {},
    onGameClick: () -> Unit = {},
    initialReelId: String? = null,
    initialReelPositionMs: Long = 0L
) {
'''
reels_src = require_replace(reels_src, old_video_signature_tail, new_video_signature_tail, "VideoReelsScreen launch args")

old_content_call = '''                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore
                )
'''
new_content_call = '''                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = onLoadMore,
                    initialReelId = initialReelId,
                    initialReelPositionMs = initialReelPositionMs
                )
'''
reels_src = require_replace(reels_src, old_content_call, new_content_call, "ReelsContent launch args")

old_content_signature = '''    onBackToPosts: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit
) {
'''
new_content_signature = '''    onBackToPosts: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    initialReelId: String?,
    initialReelPositionMs: Long
) {
'''
reels_src = require_replace(reels_src, old_content_signature, new_content_signature, "ReelsContent signature")

old_initial_page = '''    val initialPage = remember(reels, resumeUserKey) {
        val savedId = resumePrefs.getString("reel_id:$resumeUserKey", null)
        val byId = savedId?.let { id -> reels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val byIndex = resumePrefs.getInt("reel_index:$resumeUserKey", 0)
        (byId ?: byIndex).coerceIn(0, reels.lastIndex.coerceAtLeast(0))
    }
    val pager = rememberPagerState(
        initialPage = initialPage,
        pageCount = { reels.size }
    )
    var selectedTab by remember { mutableStateOf("For You") }
'''
new_initial_page = '''    val initialPage = remember(reels, resumeUserKey, initialReelId) {
        val requestedIndex = initialReelId
            ?.let { id -> reels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val savedId = resumePrefs.getString("reel_id:$resumeUserKey", null)
        val byId = savedId?.let { id -> reels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        val byIndex = resumePrefs.getInt("reel_index:$resumeUserKey", 0)
        (requestedIndex ?: byId ?: byIndex).coerceIn(0, reels.lastIndex.coerceAtLeast(0))
    }
    val pager = rememberPagerState(
        initialPage = initialPage,
        pageCount = { reels.size }
    )
    var pendingLaunchReelId by remember(initialReelId) { mutableStateOf(initialReelId) }
    var pendingLaunchPositionMs by remember(initialReelId, initialReelPositionMs) {
        mutableStateOf(initialReelPositionMs.coerceAtLeast(0L))
    }
    var selectedTab by remember { mutableStateOf("For You") }
'''
reels_src = require_replace(reels_src, old_initial_page, new_initial_page, "prefer requested reel")

old_reel_page_call = '''                onProfileClick = onProfileClick,
                onSwipeToHome = onBackToPosts,
                onSwipeToProfile = { onProfileClick(reel.author) }
            )
'''
new_reel_page_call = '''                onProfileClick = onProfileClick,
                onSwipeToHome = onBackToPosts,
                onSwipeToProfile = { onProfileClick(reel.author) },
                initialPositionMs = if (reel.id == pendingLaunchReelId) pendingLaunchPositionMs else 0L,
                onInitialPositionConsumed = {
                    if (pendingLaunchReelId == reel.id) {
                        pendingLaunchReelId = null
                        pendingLaunchPositionMs = 0L
                    }
                }
            )
'''
reels_src = require_replace(reels_src, old_reel_page_call, new_reel_page_call, "reel page launch seek")

old_reel_page_signature = '''    onProfileClick: (String) -> Unit,
    onSwipeToHome: () -> Unit,
    onSwipeToProfile: () -> Unit
) {
'''
new_reel_page_signature = '''    onProfileClick: (String) -> Unit,
    onSwipeToHome: () -> Unit,
    onSwipeToProfile: () -> Unit,
    initialPositionMs: Long,
    onInitialPositionConsumed: () -> Unit
) {
'''
reels_src = require_replace(reels_src, old_reel_page_signature, new_reel_page_signature, "ReelPage initial position signature")

old_reel_video_call = '''            ReelVideo(
                url = url,
                isActive = isActive,
                isMuted = isMuted,
                onProgressChange = { progress = it },
                onBufferingChange = { isBuffering = it }
            )
'''
new_reel_video_call = '''            ReelVideo(
                url = url,
                isActive = isActive,
                isMuted = isMuted,
                initialPositionMs = initialPositionMs,
                onInitialPositionConsumed = onInitialPositionConsumed,
                onProgressChange = { progress = it },
                onBufferingChange = { isBuffering = it }
            )
'''
reels_src = require_replace(reels_src, old_reel_video_call, new_reel_video_call, "ReelPage passes initial seek")

inline_card = r'''

/**
 * A lightweight home-feed teaser for a ranked reel.
 *
 * It auto-plays muted only while at least half visible, pauses after roughly two
 * seconds of actual video playback, then exposes a play button that opens the
 * full Reels surface at the current playback position. No content-exposure
 * modifier is attached here, so the teaser cannot create a view by itself.
 */
@Composable
internal fun InlineReelPreviewCard(
    reel: FeedPost,
    isActive: Boolean,
    onContinue: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val url = reel.videoUrl?.trim().orEmpty()
    var previewFinished by remember(reel.id) { mutableStateOf(false) }
    var previewPositionMs by remember(reel.id) { mutableStateOf(0L) }
    var isBuffering by remember(reel.id) { mutableStateOf(false) }

    LaunchedEffect(isActive, reel.id) {
        if (!isActive) {
            previewFinished = false
            previewPositionMs = 0L
            isBuffering = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f),
        shape = RoundedCornerShape(22.dp),
        color = Color.Black,
        tonalElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            // Keep a thumbnail behind the player to avoid a black flash while preparing.
            ReelPreview(reel)

            if (url.isNotBlank() && (isActive || previewFinished)) {
                ReelVideo(
                    url = url,
                    isActive = isActive && !previewFinished,
                    isMuted = true,
                    onProgressChange = {},
                    onBufferingChange = { isBuffering = it },
                    onPositionChange = { positionMs ->
                        previewPositionMs = positionMs.coerceAtLeast(0L)
                        if (positionMs >= 2_000L && !previewFinished) {
                            previewFinished = true
                        }
                    }
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = .76f)
                            )
                        )
                    )
            )

            Surface(
                color = Color.Black.copy(alpha = .48f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Reel preview",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 74.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "@${reel.authorUsername.ifBlank { reel.author }.removePrefix("@")}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                if (reel.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = reel.text,
                        color = Color.White.copy(alpha = .9f),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }

            AnimatedVisibility(
                visible = isBuffering && isActive && !previewFinished,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(30.dp)
                )
            }

            AnimatedVisibility(
                visible = previewFinished,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = .82f, animationSpec = tween(160)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .62f),
                    shadowElevation = 8.dp
                ) {
                    IconButton(
                        onClick = { onContinue(previewPositionMs.coerceAtLeast(0L)) },
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Continue reel",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }
    }
}
'''

reel_preview_anchor = "\n@Composable\nprivate fun ReelPreview(reel: FeedPost) {\n"
if "internal fun InlineReelPreviewCard(" not in reels_src:
    if reel_preview_anchor not in reels_src:
        raise RuntimeError("Could not find ReelPreview anchor for inline teaser")
    reels_src = reels_src.replace(reel_preview_anchor, inline_card + reel_preview_anchor, 1)

old_reel_video_signature = '''private fun ReelVideo(
    url: String,
    isActive: Boolean,
    isMuted: Boolean,
    onProgressChange: (Float) -> Unit,
    onBufferingChange: (Boolean) -> Unit
) {
'''
new_reel_video_signature = '''private fun ReelVideo(
    url: String,
    isActive: Boolean,
    isMuted: Boolean,
    initialPositionMs: Long = 0L,
    onInitialPositionConsumed: () -> Unit = {},
    onProgressChange: (Float) -> Unit,
    onBufferingChange: (Boolean) -> Unit,
    onPositionChange: (Long) -> Unit = {}
) {
'''
reels_src = require_replace(reels_src, old_reel_video_signature, new_reel_video_signature, "ReelVideo extended signature")

old_updated_callbacks = '''    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnBufferingChange by rememberUpdatedState(onBufferingChange)
'''
new_updated_callbacks = '''    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnBufferingChange by rememberUpdatedState(onBufferingChange)
    val currentOnInitialPositionConsumed by rememberUpdatedState(onInitialPositionConsumed)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
'''
reels_src = require_replace(reels_src, old_updated_callbacks, new_updated_callbacks, "ReelVideo updated callbacks")

old_active_effect = '''    LaunchedEffect(isActive, player) {
        player.playWhenReady = isActive
        if (isActive) player.play() else player.pause()
    }
'''
new_active_effect = '''    LaunchedEffect(isActive, player, initialPositionMs) {
        player.playWhenReady = isActive
        if (isActive) {
            if (initialPositionMs > 0L) {
                player.seekTo(initialPositionMs)
                currentOnInitialPositionConsumed()
            }
            player.play()
        } else {
            player.pause()
        }
    }
'''
reels_src = require_replace(reels_src, old_active_effect, new_active_effect, "ReelVideo initial seek")

old_progress_loop = '''        while (isActive) {
            val duration = player.duration
            if (duration > 0) {
                currentOnProgressChange((player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
            }
            delay(200)
        }
'''
new_progress_loop = '''        while (isActive) {
            val position = player.currentPosition.coerceAtLeast(0L)
            currentOnPositionChange(position)
            val duration = player.duration
            if (duration > 0) {
                currentOnProgressChange((position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
            }
            delay(100)
        }
'''
reels_src = require_replace(reels_src, old_progress_loop, new_progress_loop, "ReelVideo position reporting")

reels_path.write_text(reels_src, encoding="utf-8")


# -----------------------------------------------------------------------------
# FeedScreen: carry the selected reel id/position through the existing reel tab.
# -----------------------------------------------------------------------------
feed_path = Path("app/src/main/java/com/example/ui/screens/FeedScreen.kt")
feed = feed_path.read_text(encoding="utf-8")

old_feed_public_tail = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
'''
new_feed_public_tail = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    initialReelId: String? = null,
    initialReelPositionMs: Long = 0L
) {
'''
feed = require_replace(feed, old_feed_public_tail, new_feed_public_tail, "FeedScreen reel launch args")

old_legacy_call_tail = '''                onLoadMoreReels = onLoadMoreReels,
                homeReselectSignal = homeReselectSignal,
                onBottomBarVisibilityChange = onBottomBarVisibilityChange
            )
'''
new_legacy_call_tail = '''                onLoadMoreReels = onLoadMoreReels,
                homeReselectSignal = homeReselectSignal,
                onBottomBarVisibilityChange = onBottomBarVisibilityChange,
                initialReelId = initialReelId,
                initialReelPositionMs = initialReelPositionMs
            )
'''
feed = require_replace(feed, old_legacy_call_tail, new_legacy_call_tail, "content-family reel launch handoff", replace_all=True)

old_legacy_signature_tail = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
'''
new_legacy_signature_tail = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    initialReelId: String? = null,
    initialReelPositionMs: Long = 0L
) {
'''
feed = require_replace(feed, old_legacy_signature_tail, new_legacy_signature_tail, "LegacyFeedScreen reel launch args")

old_video_call_tail = '''                onLoadMore = onLoadMoreReels,
                onHomeClick = { navigate(0) },
                onConnectClick = { navigate(2) },
                onGameClick = { navigate(3) }
            )
'''
new_video_call_tail = '''                onLoadMore = onLoadMoreReels,
                onHomeClick = { navigate(0) },
                onConnectClick = { navigate(2) },
                onGameClick = { navigate(3) },
                initialReelId = initialReelId,
                initialReelPositionMs = initialReelPositionMs
            )
'''
feed = require_replace(feed, old_video_call_tail, new_video_call_tail, "VideoReelsScreen exact reel handoff")

feed_path.write_text(feed, encoding="utf-8")

print("Applied inline reel feed previews successfully")

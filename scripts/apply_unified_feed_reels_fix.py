from pathlib import Path


def once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)


# Supabase client: only the qualified-view RPC can increment views.
service = Path("app/src/main/java/com/example/data/supabase/SupabaseService.kt")
s = service.read_text()
s = once(
    s,
    '"/rest/v1/rpc/record_post_view",',
    '"/rest/v1/rpc/record_qualified_post_view",',
    "qualified view RPC endpoint",
)
s = once(
    s,
    '''                        put(
                            "p_viewer_username",
                            viewerUsername
                        )
''',
    '''                        put(
                            "p_viewer_username",
                            viewerUsername
                        )

                        put(
                            "p_viewed_for_seconds",
                            60
                        )
''',
    "qualified view duration payload",
)
service.write_text(s)

# Reels: count the active reel only after a continuous minute.
reels = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
r = reels.read_text()
r = once(
    r,
    '    onLoadMore: () -> Unit = {},\n    onHomeClick: () -> Unit = onBackToPosts,',
    '    onLoadMore: () -> Unit = {},\n    onViewed: (String) -> Unit = {},\n    onHomeClick: () -> Unit = onBackToPosts,',
    "VideoReelsScreen onViewed parameter",
)
r = once(
    r,
    '                    onLoadMore = onLoadMore\n                )',
    '                    onLoadMore = onLoadMore,\n                    onViewed = onViewed\n                )',
    "ReelsContent onViewed call",
)
r = once(
    r,
    '    isLoadingMore: Boolean,\n    onLoadMore: () -> Unit\n) {',
    '    isLoadingMore: Boolean,\n    onLoadMore: () -> Unit,\n    onViewed: (String) -> Unit\n) {',
    "ReelsContent onViewed parameter",
)
r = once(
    r,
    '    val pager = rememberPagerState(pageCount = { reels.size })\n    var selectedTab by remember { mutableStateOf("For You") }\n',
    '''    val pager = rememberPagerState(pageCount = { reels.size })
    var selectedTab by remember { mutableStateOf("For You") }

    val activeReelId = reels.getOrNull(pager.currentPage)?.id
    QualifiedViewEffect(
        contentId = activeReelId,
        isVisible = !pager.isScrollInProgress && abs(pager.currentPageOffsetFraction) < 0.01f,
        onQualified = onViewed
    )
''',
    "reel qualified view timer",
)
reels.write_text(r)

# Legacy reel caller also forwards the same qualified-view callback.
feed = Path("app/src/main/java/com/example/ui/screens/FeedScreen.kt")
f = feed.read_text()
f = once(
    f,
    '                onDelete = onDeletePost,\n                onProfileClick = onProfileClick,',
    '                onDelete = onDeletePost,\n                onProfileClick = onProfileClick,\n                onViewed = onViewedPost,',
    "legacy reel onViewed wiring",
)
feed.write_text(f)

# Premium feed: replace immediate 50%-visible impressions with a one-minute timer,
# and render Reels directly instead of routing through the retired feed pager.
premium = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
p = premium.read_text()
p = once(
    p,
    '    val impressionTracker = remember { PostImpressionTracker() }\n',
    '    var qualifiedVisiblePostIds by remember { mutableStateOf<Set<String>>(emptySet()) }\n',
    "premium feed tracker state",
)
p = once(
    p,
    '                impressionTracker.update(ids).forEach(latestViewed)\n',
    '                qualifiedVisiblePostIds = ids\n',
    "premium feed delayed qualification",
)
p = once(
    p,
    '''                                    val post = filteredPosts[index]
                                    PremiumPostEntrance(index = index) {
''',
    '''                                    val post = filteredPosts[index]
                                    QualifiedViewEffect(
                                        contentId = post.id,
                                        isVisible = post.id in qualifiedVisiblePostIds,
                                        onQualified = latestViewed
                                    )
                                    PremiumPostEntrance(index = index) {
''',
    "premium feed per-post timer",
)
start = p.index("        1 -> FeedScreen(")
end = p.index("\n\n        2 -> PremiumConnectHost(", start)
p = (
    p[:start]
    + '''        1 -> VideoReelsScreen(
            reels = reels,
            currentUsername = currentUsername,
            isDark = isDark,
            onLike = onLikePost,
            onComment = onCommentPost,
            onBookmark = onBookmarkPost,
            onShare = onSharePost,
            onDelete = onDeletePost,
            onProfileClick = onProfileClick,
            onBackToPosts = { onSubTabChanged(0) },
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            hasMore = hasMoreReels,
            isLoadingMore = isLoadingMoreReels,
            onLoadMore = onLoadMoreReels,
            onViewed = onViewedPost
        )'''
    + p[end:]
)
premium.write_text(p)

# Creator-owned cards never expose the repost action.
card = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
c = card.read_text()
repost_block = '''                PremiumPostAction(
                    icon = Icons.Default.Repeat,
                    value = formatNumber(post.repostsCount),
                    tint = repostTint,
                    description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                    onClick = onRepost
                )
'''
wrapped_repost = '''                if (!isAuthor) {
                    PremiumPostAction(
                        icon = Icons.Default.Repeat,
                        value = formatNumber(post.repostsCount),
                        tint = repostTint,
                        description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                        onClick = onRepost
                    )
                }
'''
c = once(c, repost_block, wrapped_repost, "creator repost action")
card.write_text(c)

# MainActivity: split profile posts and reels into separate datasets.
main = Path("app/src/main/java/com/example/MainActivity.kt")
m = main.read_text()
old_profile = '''                val profilePosts = if (isMyProfile) {
                    (uiState.posts + uiState.reels).distinctBy { it.id }.filter { viewModel.isMe(it.author) }
                } else {
                    (uiState.posts + uiState.reels).distinctBy { it.id }.filter { it.author.equals(profile.username, ignoreCase = true) || it.author.equals(profile.fullName, ignoreCase = true) }
                }
                val profileLikedPosts = (uiState.posts + uiState.reels).filter { it.isLiked }
'''
new_profile = '''                val profileContent = (uiState.posts + uiState.reels).distinctBy { it.id }
                val belongsToProfile: (com.example.data.models.FeedPost) -> Boolean = { item ->
                    if (isMyProfile) {
                        viewModel.isMe(item.author)
                    } else {
                        item.author.equals(profile.username, ignoreCase = true) ||
                            item.author.equals(profile.fullName, ignoreCase = true)
                    }
                }
                val profilePosts = profileContent.filter { item ->
                    belongsToProfile(item) && !item.isReel && item.videoUrl.isNullOrBlank()
                }
                val profileReels = profileContent.filter { item ->
                    belongsToProfile(item) && (item.isReel || !item.videoUrl.isNullOrBlank())
                }
                val profileLikedPosts = profileContent.filter { it.isLiked }
'''
m = once(m, old_profile, new_profile, "profile post/reel split")
m = once(
    m,
    '                    userPosts = profilePosts,\n                    likedPosts = profileLikedPosts,',
    '                    userPosts = profilePosts,\n                    userReels = profileReels,\n                    likedPosts = profileLikedPosts,',
    "ProfileScreen reels argument",
)
m = once(
    m,
    '                    onOptionsClick = { viewModel.openPostOptions(it) },\n                    onDeletePost = { viewModel.deletePost(it) },',
    '''                    onOptionsClick = { viewModel.openPostOptions(it) },
                    onOpenReel = { reel ->
                        viewModel.handleDeepLink(
                            com.example.sharing.AppDeepLink(
                                type = ShareContentType.REEL,
                                id = reel.id
                            )
                        )
                    },
                    onDeletePost = { viewModel.deletePost(it) },''',
    "profile open reel callback",
)
main.write_text(m)

# Profile: add a dedicated Reels tab/page and keep Posts strictly non-video.
profile = Path("app/src/main/java/com/example/ui/screens/ProfileScreen.kt")
q = profile.read_text()
q = once(
    q,
    '    userPosts: List<FeedPost>,\n    likedPosts: List<FeedPost>,',
    '    userPosts: List<FeedPost>,\n    userReels: List<FeedPost>,\n    likedPosts: List<FeedPost>,',
    "ProfileScreen userReels parameter",
)
q = once(
    q,
    '    onOptionsClick: (FeedPost) -> Unit,\n    onDeletePost: (String) -> Unit = {},',
    '    onOptionsClick: (FeedPost) -> Unit,\n    onOpenReel: (FeedPost) -> Unit = {},\n    onDeletePost: (String) -> Unit = {},',
    "ProfileScreen onOpenReel parameter",
)
q = once(
    q,
    '            listOf("Posts", "Growth", "Liked", "Saved", "Market", "Skills", "About")',
    '            listOf("Posts", "Reels", "Growth", "Liked", "Saved", "Market", "Skills", "About")',
    "owner profile tabs",
)
q = once(
    q,
    '            listOf("Posts", "Growth", "Liked", "Market", "Skills", "About")',
    '            listOf("Posts", "Reels", "Growth", "Liked", "Market", "Skills", "About")',
    "visitor profile tabs",
)

block_start = q.index("                when (selectedTab) {")
block_end = q.index(
    "\n                }\n            }\n\n            // ============================================================\n            // FLOATING REFRESH",
    block_start,
)
b = q[block_start:block_end]
b = once(
    b,
    '                    1 -> item(key = "growth") {',
    '''                    1 -> profileReelItems(
                        reels = userReels,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onOpenReel = onOpenReel
                    )

                    2 -> item(key = "growth") {''',
    "profile reels tab branch",
)
b = once(b, "                    2 -> profilePostItems(", "                    3 -> profilePostItems(", "liked tab shift")
b = once(b, "                    3 -> if (isMe) {", "                    4 -> if (isMe) {", "saved tab shift")
b = once(b, "                    4 -> if (isMe) {", "                    5 -> if (isMe) {", "market tab shift")
b = once(
    b,
    '                    5 -> item(key = if (isMe) "skills" else "about") {',
    '                    6 -> item(key = if (isMe) "skills" else "about") {',
    "skills tab shift",
)
b = once(b, '                    6 -> item(key = "about") {', '                    7 -> item(key = "about") {', "about tab shift")
q = q[:block_start] + b + q[block_end:]

marker = "// =====================================================================\n// PROFILE POSTS\n// =====================================================================\n"
if marker not in q:
    raise SystemExit("Missing profile posts marker")
reel_helpers = r'''// =====================================================================
// PROFILE REELS
// =====================================================================

private fun LazyListScope.profileReelItems(
    reels: List<FeedPost>,
    textPrimary: Color,
    textSecondary: Color,
    onOpenReel: (FeedPost) -> Unit
) {
    if (reels.isEmpty()) {
        item(key = "reels_empty", contentType = "profile_empty") {
            EmptyProfileState(
                title = "No reels uploaded yet 🎬",
                subtitle = "Uploaded reels will appear here separately from posts.",
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
        return
    }

    items(
        items = reels,
        key = { reel -> "profile_reel_${reel.id}" },
        contentType = { "profile_reel" }
    ) { reel ->
        ProfileReelCard(
            reel = reel,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onOpen = { onOpenReel(reel) }
        )
    }
}

@Composable
private fun ProfileReelCard(
    reel: FeedPost,
    textPrimary: Color,
    textSecondary: Color,
    onOpen: () -> Unit
) {
    val preview = reel.images.firstOrNull { it.isNotBlank() && !it.equals("null", true) }
        ?: reel.authorAvatar.takeIf { it.isNotBlank() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(154.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!preview.isNullOrBlank()) {
                    AsyncImage(
                        model = preview,
                        contentDescription = "Reel preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                )
                Icon(
                    Icons.Default.PlayCircleFilled,
                    contentDescription = "Open reel",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Reel", color = BlinkPink, fontSize = 10.sp, fontWeight = FontWeight.Black)
                if (reel.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        reel.text,
                        color = textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileReelMetric(Icons.Default.Visibility, reel.viewsCount, textSecondary)
                    ProfileReelMetric(Icons.Default.FavoriteBorder, reel.likes, textSecondary)
                    ProfileReelMetric(Icons.Default.ChatBubbleOutline, reel.commentsCount, textSecondary)
                }
                if (reel.timeAgo.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(reel.timeAgo, color = textSecondary, fontSize = 9.5.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileReelMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(value.toString(), color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

'''
q = q.replace(marker, reel_helpers + marker, 1)
profile.write_text(q)

# Defense in depth: stale UI/deep links cannot repost the creator's own content.
vm = Path("app/src/main/java/com/example/viewmodel/BlinkViewModel.kt")
v = vm.read_text()
v = once(
    v,
    '''    fun toggleRepost(postId: String) {
        viewModelScope.launch {
''',
    '''    fun toggleRepost(postId: String) {
        val target = (_uiState.value.posts + _uiState.value.reels).firstOrNull { it.id == postId }
        if (target != null && isMe(target.author)) {
            showToast("You cannot repost your own content.")
            return
        }
        viewModelScope.launch {
''',
    "creator repost guard",
)
vm.write_text(v)

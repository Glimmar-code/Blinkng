from pathlib import Path

root = Path('.')

# PremiumFeedScreen: remove Story/legacy-only parameters and the dependency on FeedScreen.
p = root / 'app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('import com.example.data.models.Story\n', '')
for line in [
    '    stories: List<Story>,\n',
    '    onAddStoryClick: () -> Unit,\n',
    '    onStoryClick: (Story) -> Unit,\n',
    '    onToggleTheme: () -> Unit,\n',
    '    onLeaderboardClick: () -> Unit = {},\n',
    '    onMarketClick: () -> Unit = {},\n',
    '    onMessageClick: () -> Unit = {},\n',
]:
    s = s.replace(line, '')

old = '''    // Preserve all existing interactive families exactly as they already work.\n    if (currentSubTab != 0) {\n        FeedScreen(\n            posts = posts,\n            reels = reels,\n            stories = stories,\n            profiles = profiles,\n            leaderboardUsers = leaderboardUsers,\n            connectHub = connectHub,\n            connectHubActions = connectHubActions,\n            isConnectHubLoading = isConnectHubLoading,\n            currentUsername = currentUsername,\n            userAvatar = userAvatar,\n            currentSubTab = currentSubTab,\n            onSubTabChanged = onSubTabChanged,\n            isDark = isDark,\n            onLikePost = onLikePost,\n            onCommentPost = onCommentPost,\n            onBookmarkPost = onBookmarkPost,\n            onRepostPost = onRepostPost,\n            onSharePost = onSharePost,\n            onOptionsClick = onOptionsClick,\n            onDeletePost = onDeletePost,\n            onProfileClick = onProfileClick,\n            onAddStoryClick = onAddStoryClick,\n            onStoryClick = onStoryClick,\n            onOpenCreatePost = onOpenCreatePost,\n            onOpenActivity = onOpenActivity,\n            onOpenMenu = onOpenMenu,\n            onToggleTheme = onToggleTheme,\n            isServerConnected = isServerConnected,\n            isLoading = isLoading,\n            isRefreshing = isRefreshing,\n            errorMessage = errorMessage,\n            onRefresh = onRefresh,\n            onRetry = onRetry,\n            onViewedPost = onViewedPost,\n            onVotePoll = onVotePoll,\n            onDirectMessage = onDirectMessage,\n            onSearchClick = onSearchClick,\n            onLeaderboardClick = onLeaderboardClick,\n            onMarketClick = onMarketClick,\n            onMessageClick = onMessageClick,\n            hasMorePosts = hasMorePosts,\n            hasMoreReels = hasMoreReels,\n            isLoadingMorePosts = isLoadingMorePosts,\n            isLoadingMoreReels = isLoadingMoreReels,\n            onLoadMorePosts = onLoadMorePosts,\n            onLoadMoreReels = onLoadMoreReels,\n            homeReselectSignal = homeReselectSignal,\n            onBottomBarVisibilityChange = onBottomBarVisibilityChange\n        )\n        return\n    }\n\n    PremiumHomeFeed(\n'''

new = '''    fun navigate(tab: Int) {\n        if (currentSubTab != tab) onSubTabChanged(tab)\n    }\n\n    when (currentSubTab) {\n        1 -> {\n            VideoReelsScreen(\n                reels = reels,\n                currentUsername = currentUsername,\n                isDark = isDark,\n                onLike = onLikePost,\n                onComment = onCommentPost,\n                onBookmark = onBookmarkPost,\n                onShare = onSharePost,\n                onDelete = onDeletePost,\n                onProfileClick = onProfileClick,\n                onBackToPosts = { navigate(0) },\n                isLoading = isLoading,\n                isRefreshing = isRefreshing,\n                onRefresh = onRefresh,\n                hasMore = hasMoreReels,\n                isLoadingMore = isLoadingMoreReels,\n                onLoadMore = onLoadMoreReels,\n                onHomeClick = { navigate(0) },\n                onConnectClick = { navigate(2) },\n                onGameClick = { navigate(3) }\n            )\n            return\n        }\n        2 -> {\n            ConnectSection(\n                profiles = profiles,\n                currentUsername = currentUsername,\n                userAvatar = userAvatar,\n                isDark = isDark,\n                onOpenMenu = onOpenMenu,\n                onOpenActivity = onOpenActivity,\n                onProfileClick = onProfileClick,\n                onDirectMessage = onDirectMessage,\n                connectHub = connectHub,\n                connectHubActions = connectHubActions,\n                isConnectHubLoading = isConnectHubLoading,\n                selectedTopTab = 2,\n                onHomeClick = { navigate(0) },\n                onReelClick = { navigate(1) },\n                onConnectClick = { navigate(2) },\n                onGameClick = { navigate(3) }\n            )\n            return\n        }\n        3 -> {\n            GameSection(\n                userAvatar = userAvatar,\n                leaderboardUsers = leaderboardUsers,\n                connectHub = connectHub,\n                connectHubActions = connectHubActions,\n                isDark = isDark,\n                onOpenMenu = onOpenMenu,\n                onOpenActivity = onOpenActivity,\n                onProfileClick = onProfileClick,\n                selectedTopTab = 3,\n                onHomeClick = { navigate(0) },\n                onReelClick = { navigate(1) },\n                onConnectClick = { navigate(2) },\n                onGameClick = { navigate(3) }\n            )\n            return\n        }\n    }\n\n    PremiumHomeFeed(\n'''

if old not in s:
    raise SystemExit('PremiumFeedScreen legacy delegation block not found')
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')

# MainActivity: remove feed Story/legacy-only arguments.
p = root / 'app/src/main/java/com/example/MainActivity.kt'
s = p.read_text(encoding='utf-8')
for line in [
    '                        stories = uiState.stories,\n',
    '                        onAddStoryClick = { viewModel.openCreateStory(true) },\n',
    '                        onStoryClick = { story -> viewModel.openStory(story) },\n',
    '                        onToggleTheme = { viewModel.toggleDarkMode() },\n',
    '                        onLeaderboardClick = { viewModel.setTab(MainTab.LEADERBOARD) },\n',
    '                        onMarketClick = { viewModel.setTab(MainTab.MARKET) },\n',
    '                        onMessageClick = { viewModel.setTab(MainTab.MESSAGES) },\n',
]:
    s = s.replace(line, '')
p.write_text(s, encoding='utf-8')

# Header profile button: enforce 48dp touch target.
p = root / 'app/src/main/java/com/example/ui/components/PremiumFeedChrome.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('                .size(46.dp)\n                .background(feedAccentBrush(), CircleShape)', '                .size(48.dp)\n                .background(feedAccentBrush(), CircleShape)')
s = s.replace('                    .size(38.dp)\n                    .background(FeedElevatedSurface, CircleShape)', '                    .size(40.dp)\n                    .background(FeedElevatedSurface, CircleShape)')
p.write_text(s, encoding='utf-8')

# PostCard: enforce 48dp touch targets on username and see-more controls.
p = root / 'app/src/main/java/com/example/ui/components/PostCard.kt'
s = p.read_text(encoding='utf-8')
s = s.replace(
    '                            modifier = Modifier.clickable { onProfileClick(post.author) }\n',
    '                            modifier = Modifier\n                                .heightIn(min = 48.dp)\n                                .clickable(role = Role.Button) { onProfileClick(post.author) }\n                                .semantics { contentDescription = "Open ${post.author} profile" }\n'
)
s = s.replace(
    '                            .padding(start = 16.dp, top = 6.dp)\n                            .clickable { expandedText = !expandedText }\n',
    '                            .padding(start = 16.dp, top = 6.dp)\n                            .heightIn(min = 48.dp)\n                            .clickable(role = Role.Button) { expandedText = !expandedText }\n                            .semantics { contentDescription = if (expandedText) "Show less post text" else "Show more post text" }\n'
)
p.write_text(s, encoding='utf-8')

# Remove the previous feed implementation after all callers are gone.
legacy = root / 'app/src/main/java/com/example/ui/screens/FeedScreen.kt'
if not legacy.exists():
    raise SystemExit('Legacy FeedScreen.kt was already missing')
legacy.unlink()

print('Premium feed is now standalone; legacy FeedScreen removed.')

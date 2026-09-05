from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEED = ROOT / "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
MESSAGES = ROOT / "app/src/main/java/com/example/ui/screens/MessagesScreen.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"


def add_import(text: str, anchor: str, new_import: str) -> str:
    if new_import in text:
        return text
    if anchor not in text:
        raise RuntimeError(f"Import anchor not found: {anchor}")
    return text.replace(anchor, anchor + "\n" + new_import, 1)


def remove_chained_lambda_call(text: str, marker: str) -> str:
    start = text.find(marker)
    if start < 0:
        return text
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found for {marker}")
    depth = 0
    in_string = False
    escaped = False
    i = brace
    while i < len(text):
        c = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + text[i + 1 :]
        i += 1
    raise RuntimeError(f"Closing brace not found for {marker}")


FEED_WRAPPER = r'''// ============================================================================
// GESTURE-DRIVEN PAGE FAMILIES
// ============================================================================

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    stories: List<Story>,
    profiles: List<UserProfile>,
    leaderboardUsers: List<LeaderboardUser>,
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isConnectHubLoading: Boolean = false,
    currentUsername: String,
    userAvatar: String,
    currentSubTab: Int,
    onSubTabChanged: (Int) -> Unit,
    isDark: Boolean,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onRepostPost: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onOptionsClick: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit = {},
    onProfileClick: (String) -> Unit,
    onAddStoryClick: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMenu: () -> Unit,
    onToggleTheme: () -> Unit,
    isServerConnected: Boolean = true,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onViewedPost: (String) -> Unit = {},
    onVotePoll: (postId: String, optionId: String) -> Unit = { _, _ -> },
    onDirectMessage: (partner: String, partnerName: String?, partnerAvatar: String?) -> Unit = { _, _, _ -> },
    onSearchClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onMarketClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
    hasMorePosts: Boolean = false,
    hasMoreReels: Boolean = false,
    isLoadingMorePosts: Boolean = false,
    isLoadingMoreReels: Boolean = false,
    onLoadMorePosts: () -> Unit = {},
    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
    val latestSubTab by rememberUpdatedState(currentSubTab)
    val latestSubTabChanged by rememberUpdatedState(onSubTabChanged)

    // Family 1: Feed <-> Reel. Foundation HorizontalPager supplies native
    // touch slop, velocity handling, fling decay and page snapping.
    val contentPagerState = rememberPagerState(
        initialPage = currentSubTab.coerceIn(0, 1),
        pageCount = { 2 }
    )

    // Family 2: Game <-> Connect. Page 0 is Game and page 1 is Connect so
    // a horizontal gesture switches only within the interactive family.
    val interactivePagerState = rememberPagerState(
        initialPage = if (currentSubTab == 3) 0 else 1,
        pageCount = { 2 }
    )

    LaunchedEffect(currentSubTab) {
        when (currentSubTab) {
            0, 1 -> if (contentPagerState.currentPage != currentSubTab) {
                contentPagerState.animateScrollToPage(currentSubTab)
            }
            3 -> if (interactivePagerState.currentPage != 0) {
                interactivePagerState.animateScrollToPage(0)
            }
            2 -> if (interactivePagerState.currentPage != 1) {
                interactivePagerState.animateScrollToPage(1)
            }
        }
    }

    val inContentFamily = currentSubTab in 0..1
    LaunchedEffect(contentPagerState, inContentFamily) {
        if (!inContentFamily) return@LaunchedEffect
        snapshotFlow { contentPagerState.settledPage }.collectLatest { page ->
            if (latestSubTab in 0..1 && latestSubTab != page) {
                latestSubTabChanged(page)
            }
        }
    }

    val inInteractiveFamily = currentSubTab == 2 || currentSubTab == 3
    LaunchedEffect(interactivePagerState, inInteractiveFamily) {
        if (!inInteractiveFamily) return@LaunchedEffect
        snapshotFlow { interactivePagerState.settledPage }.collectLatest { page ->
            val tab = if (page == 0) 3 else 2
            if ((latestSubTab == 2 || latestSubTab == 3) && latestSubTab != tab) {
                latestSubTabChanged(tab)
            }
        }
    }

    if (inContentFamily) {
        HorizontalPager(
            state = contentPagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            LegacyFeedScreen(
                posts = posts,
                reels = reels,
                stories = stories,
                profiles = profiles,
                leaderboardUsers = leaderboardUsers,
                connectHub = connectHub,
                connectHubActions = connectHubActions,
                isConnectHubLoading = isConnectHubLoading,
                currentUsername = currentUsername,
                userAvatar = userAvatar,
                currentSubTab = page,
                onSubTabChanged = onSubTabChanged,
                isDark = isDark,
                onLikePost = onLikePost,
                onCommentPost = onCommentPost,
                onBookmarkPost = onBookmarkPost,
                onRepostPost = onRepostPost,
                onSharePost = onSharePost,
                onOptionsClick = onOptionsClick,
                onDeletePost = onDeletePost,
                onProfileClick = onProfileClick,
                onAddStoryClick = onAddStoryClick,
                onStoryClick = onStoryClick,
                onOpenCreatePost = onOpenCreatePost,
                onOpenActivity = onOpenActivity,
                onOpenMenu = onOpenMenu,
                onToggleTheme = onToggleTheme,
                isServerConnected = isServerConnected,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onViewedPost = onViewedPost,
                onVotePoll = onVotePoll,
                onDirectMessage = onDirectMessage,
                onSearchClick = onSearchClick,
                onLeaderboardClick = onLeaderboardClick,
                onMarketClick = onMarketClick,
                onMessageClick = onMessageClick,
                hasMorePosts = hasMorePosts,
                hasMoreReels = hasMoreReels,
                isLoadingMorePosts = isLoadingMorePosts,
                isLoadingMoreReels = isLoadingMoreReels,
                onLoadMorePosts = onLoadMorePosts,
                onLoadMoreReels = onLoadMoreReels,
                homeReselectSignal = homeReselectSignal,
                onBottomBarVisibilityChange = onBottomBarVisibilityChange
            )
        }
    } else {
        HorizontalPager(
            state = interactivePagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val mappedTab = if (page == 0) 3 else 2
            LegacyFeedScreen(
                posts = posts,
                reels = reels,
                stories = stories,
                profiles = profiles,
                leaderboardUsers = leaderboardUsers,
                connectHub = connectHub,
                connectHubActions = connectHubActions,
                isConnectHubLoading = isConnectHubLoading,
                currentUsername = currentUsername,
                userAvatar = userAvatar,
                currentSubTab = mappedTab,
                onSubTabChanged = onSubTabChanged,
                isDark = isDark,
                onLikePost = onLikePost,
                onCommentPost = onCommentPost,
                onBookmarkPost = onBookmarkPost,
                onRepostPost = onRepostPost,
                onSharePost = onSharePost,
                onOptionsClick = onOptionsClick,
                onDeletePost = onDeletePost,
                onProfileClick = onProfileClick,
                onAddStoryClick = onAddStoryClick,
                onStoryClick = onStoryClick,
                onOpenCreatePost = onOpenCreatePost,
                onOpenActivity = onOpenActivity,
                onOpenMenu = onOpenMenu,
                onToggleTheme = onToggleTheme,
                isServerConnected = isServerConnected,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onViewedPost = onViewedPost,
                onVotePoll = onVotePoll,
                onDirectMessage = onDirectMessage,
                onSearchClick = onSearchClick,
                onLeaderboardClick = onLeaderboardClick,
                onMarketClick = onMarketClick,
                onMessageClick = onMessageClick,
                hasMorePosts = hasMorePosts,
                hasMoreReels = hasMoreReels,
                isLoadingMorePosts = isLoadingMorePosts,
                isLoadingMoreReels = isLoadingMoreReels,
                onLoadMorePosts = onLoadMorePosts,
                onLoadMoreReels = onLoadMoreReels,
                homeReselectSignal = homeReselectSignal,
                onBottomBarVisibilityChange = onBottomBarVisibilityChange
            )
        }
    }
}

'''


MESSAGES_WRAPPER = r'''// ============================================================================
// RESPONSIVE MASTER-DETAIL MESSAGES
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    conversations: List<ChatConversation>,
    activePartner: String?,
    onOpenConversation: (String) -> Unit,
    onCloseConversation: () -> Unit,
    onSendMessage: (String, String) -> Unit,
    onSendVideo: (String, Uri) -> Unit = { _, _ -> },
    onRetryMessage: ((String, ChatMessage) -> Unit)? = null,
    hasMoreMessages: (String) -> Boolean = { false },
    isLoadingOlder: (String) -> Boolean = { false },
    onLoadOlder: (String) -> Unit = {},
    isLoadingMessages: (String) -> Boolean = { false },
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    isConnected: Boolean = true,
    isLoading: Boolean = false
) {
    val selectedChat = activePartner?.takeIf { it.isNotBlank() }
    val paneOpen = selectedChat != null
    val selectedConversation = remember(conversations, selectedChat) {
        conversations.firstOrNull {
            it.partnerUsername.equals(selectedChat, ignoreCase = true)
        }
    }

    // Android system back reverses the fold before leaving Messages.
    BackHandler(enabled = paneOpen) {
        onCloseConversation()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("messages_master_detail")
    ) {
        val masterWidth by animateDpAsState(
            targetValue = if (paneOpen) 76.dp else maxWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "messageMasterWidth"
        )

        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(masterWidth)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                AnimatedVisibility(
                    visible = !paneOpen,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(90)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    MessagesInboxContent(
                        conversations = conversations,
                        activePartner = activePartner,
                        onOpenConversation = onOpenConversation,
                        onCloseConversation = onCloseConversation,
                        onSendMessage = onSendMessage,
                        onProfileClick = onProfileClick,
                        isDark = isDark,
                        isConnected = isConnected,
                        isLoading = isLoading
                    )
                }

                AnimatedVisibility(
                    visible = paneOpen,
                    enter = fadeIn(tween(durationMillis = 160, delayMillis = 60)),
                    exit = fadeOut(tween(90)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    ConversationAvatarRail(
                        conversations = conversations,
                        selectedPartner = selectedChat,
                        onOpenConversation = onOpenConversation
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                AnimatedVisibility(
                    visible = paneOpen,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(140)),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(190, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(120)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val convo = selectedConversation
                    if (convo != null) {
                        // key() intentionally swaps the active chat immediately while the
                        // detail pane stays open, giving the avatar rail true quick-switch.
                        androidx.compose.runtime.key(convo.id) {
                            ChatConversationView(
                                convo = convo,
                                onBack = onCloseConversation,
                                onSendMessage = { text ->
                                    onSendMessage(convo.partnerUsername, text)
                                },
                                onSendVideo = { uri ->
                                    onSendVideo(convo.partnerUsername, uri)
                                },
                                onProfileClick = onProfileClick,
                                isDark = isDark,
                                isConnected = isConnected,
                                onRetryMessage = onRetryMessage?.let { retry ->
                                    { message -> retry(convo.partnerUsername, message) }
                                },
                                hasMoreMessages = hasMoreMessages(convo.id),
                                isLoadingOlder = isLoadingOlder(convo.id),
                                onLoadOlder = { onLoadOlder(convo.partnerUsername) },
                                isLoadingMessages = isLoadingMessages(convo.id)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatarRail(
    conversations: List<ChatConversation>,
    selectedPartner: String?,
    onOpenConversation: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 52.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = conversations,
                key = { "avatar_rail_${it.id}" }
            ) { conversation ->
                val selected = conversation.partnerUsername.equals(
                    selectedPartner,
                    ignoreCase = true
                )
                AsyncImage(
                    model = conversation.partnerAvatar,
                    contentDescription = "Open ${conversation.partnerName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                BlinkPink
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            },
                            shape = CircleShape
                        )
                        .clickable(role = Role.Button) {
                            onOpenConversation(conversation.partnerUsername)
                        }
                )
            }
        }
    }
}

'''


def patch_feed() -> None:
    text = FEED.read_text()
    if "fun FeedScreen(\n" in text and "private fun LegacyFeedScreen(" not in text:
        text = add_import(
            text,
            "import androidx.compose.foundation.layout.width",
            "import androidx.compose.foundation.pager.HorizontalPager",
        )
        text = add_import(
            text,
            "import androidx.compose.foundation.pager.HorizontalPager",
            "import androidx.compose.foundation.pager.rememberPagerState",
        )

        legacy_marker = "@OptIn(\n    androidx.compose.foundation.ExperimentalFoundationApi::class,\n    ExperimentalMaterial3Api::class\n)\n@Composable\nfun FeedScreen("
        if legacy_marker not in text:
            raise RuntimeError("FeedScreen declaration marker changed")
        text = text.replace(
            legacy_marker,
            FEED_WRAPPER + legacy_marker.replace("fun FeedScreen(", "private fun LegacyFeedScreen("),
            1,
        )

        # Native HorizontalPager owns horizontal drag/fling. Keep the visible menu button,
        # but remove the old hand-written drag recognizer that would compete for gestures.
        text = remove_chained_lambda_call(
            text,
            ".pointerInput(selectedTopTab) {",
        )
        FEED.write_text(text)


def patch_messages() -> None:
    text = MESSAGES.read_text()
    if "private fun MessagesInboxContent(" not in text:
        text = add_import(
            text,
            "import androidx.activity.compose.rememberLauncherForActivityResult",
            "import androidx.activity.compose.BackHandler",
        )
        text = add_import(
            text,
            "import androidx.compose.animation.core.animateFloatAsState",
            "import androidx.compose.animation.core.animateDpAsState",
        )
        text = add_import(
            text,
            "import androidx.compose.foundation.layout.Box",
            "import androidx.compose.foundation.layout.BoxWithConstraints",
        )
        text = add_import(
            text,
            "import androidx.compose.ui.draw.clip",
            "import androidx.compose.ui.draw.clipToBounds",
        )

        home_marker = "// ============================================================================\n// MESSAGES HOME\n// ============================================================================\n\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun MessagesScreen("
        if home_marker not in text:
            raise RuntimeError("MessagesScreen declaration marker changed")
        replacement = (
            "// ============================================================================\n// MESSAGES HOME\n// ============================================================================\n\n"
            + MESSAGES_WRAPPER
            + "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun MessagesInboxContent("
        )
        text = text.replace(home_marker, replacement, 1)
        MESSAGES.write_text(text)


def patch_main() -> None:
    text = MAIN.read_text()

    old_send = """                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },\n                        onProfileClick = { viewModel.openProfile(it) },"""
    new_send = """                        onSendMessage = { partner, text -> viewModel.sendMessage(partner, text) },\n                        onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) },\n                        onRetryMessage = { partner, message ->\n                            viewModel.retrySendMessage(partner, message)\n                        },\n                        hasMoreMessages = { conversationId ->\n                            uiState.messageHistoryHasMore[conversationId] ?: true\n                        },\n                        isLoadingOlder = { conversationId ->\n                            uiState.loadingOlderConversationId == conversationId\n                        },\n                        onLoadOlder = { partner -> viewModel.loadOlderMessages(partner) },\n                        onProfileClick = { viewModel.openProfile(it) },"""
    if "onSendVideo = { partner, uri -> viewModel.sendVideoMessage(partner, uri) }" not in text:
        if old_send not in text:
            raise RuntimeError("MessagesScreen call marker changed")
        text = text.replace(old_send, new_send, 1)

    old_overlay = "visible = uiState.isConversationFullScreen && uiState.activeConversationPartner != null,"
    new_overlay = "visible = uiState.isConversationFullScreen && uiState.activeConversationPartner != null && uiState.selectedTab != MainTab.MESSAGES,"
    if new_overlay not in text:
        if old_overlay not in text:
            raise RuntimeError("Chat overlay visibility marker changed")
        text = text.replace(old_overlay, new_overlay, 1)

    MAIN.write_text(text)


patch_feed()
patch_messages()
patch_main()

print("Applied categorized pagers and responsive message master-detail layout.")

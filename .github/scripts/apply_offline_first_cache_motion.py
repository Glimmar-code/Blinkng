from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Missing expected block for {label} in {path}")
    path.write_text(text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# 1. Make the durable app snapshot a complete fallback, not just a few panels.
# ---------------------------------------------------------------------------
store = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"
replace_once(
    store,
    '''data class CachedAppSnapshot(
    val ownerUsername: String = "",
    val myProfile: UserProfile = UserProfile(),
    val stories: List<Story> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val mutedUsers: Set<String> = emptySet(),
    val cachedAt: Long = 0L
)''',
    '''data class CachedAppSnapshot(
    val ownerUsername: String = "",
    val myProfile: UserProfile = UserProfile(),
    val posts: List<FeedPost> = emptyList(),
    val reels: List<FeedPost> = emptyList(),
    val profiles: List<UserProfile> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val stories: List<Story> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val gameLeaderboardUsers: List<LeaderboardUser> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val mutedUsers: Set<String> = emptySet(),
    val blinkCoinBalance: Long = 0L,
    val cachedAt: Long = 0L
)''',
    "complete cached app snapshot"
)
replace_once(
    store,
    'private const val DEFAULT_CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L',
    'private const val DEFAULT_CACHE_MAX_AGE_MS = 180L * 24L * 60L * 60L * 1000L',
    "cache retention"
)


# ---------------------------------------------------------------------------
# 2. Restore the signed-in local account before any Supabase/network work.
#    The local database/snapshot becomes the first source rendered on cold start.
# ---------------------------------------------------------------------------
vm = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
replace_once(
    vm,
    '''        // Render the last authenticated account immediately. Supabase verification and
        // refresh continue in the background instead of blocking the first usable frame.
        val recoverableLocalSession = hasLocalAuthenticatedProfile() && (
            !SupabaseService.accessToken().isNullOrBlank() ||
                !SupabaseService.refreshToken().isNullOrBlank() ||
                AccountSessionStore.list(appContext).isNotEmpty()
            )
        if (recoverableLocalSession) restoreLocalSession()

        observeCachedContent()
        viewModelScope.launch { restoreCachedAppSnapshot() }''',
    '''        // Local-first startup: a previously authenticated account remains usable with
        // airplane mode / mobile data off. Cloud session verification happens afterwards.
        val hasLocalSession = hasLocalAuthenticatedProfile()
        if (hasLocalSession) restoreLocalSession()

        observeCachedContent()
        viewModelScope.launch {
            restoreCachedAppSnapshot()
            if (hasLocalSession && !_uiState.value.isOnline) {
                _uiState.value = _uiState.value.copy(
                    destination = AppDestination.MAIN,
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
            }
        }''',
    "local-first init"
)

replace_once(
    vm,
    '''private suspend fun restoreSupabaseSession() {
        try {
            // Local state is already usable when available; this call only validates and
            // refreshes the cloud session. Never blank the cached UI while it runs.
            if (_uiState.value.destination != AppDestination.MAIN && hasLocalAuthenticatedProfile()) {''',
    '''private suspend fun restoreSupabaseSession() {
        try {
            // Never make an offline cold start wait on Supabase. The last signed-in account
            // and its durable cache are the source of truth until connectivity returns.
            if (!_uiState.value.isOnline && hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                restoreCachedAppSnapshot()
                _uiState.value = _uiState.value.copy(
                    destination = AppDestination.MAIN,
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
                return
            }

            // Local state is already usable when available; this call only validates and
            // refreshes the cloud session. Never blank the cached UI while it runs.
            if (_uiState.value.destination != AppDestination.MAIN && hasLocalAuthenticatedProfile()) {''',
    "offline auth short-circuit"
)

replace_once(
    vm,
    '''        } catch (e: Exception) {
            Log.w(TAG, "restoreSupabaseSession notice: ${e.message}")
            if (hasLocalAuthenticatedProfile() &&
                (SupabaseService.accessToken() != null || AccountSessionStore.list(appContext).isNotEmpty())) {
                restoreLocalSession()
                _uiState.value = _uiState.value.copy(destination = AppDestination.MAIN)
                fetchSupabaseData()
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        }''',
    '''        } catch (e: Exception) {
            Log.w(TAG, "restoreSupabaseSession notice: ${e.message}")
            if (hasLocalAuthenticatedProfile()) {
                restoreLocalSession()
                restoreCachedAppSnapshot()
                _uiState.value = _uiState.value.copy(
                    destination = AppDestination.MAIN,
                    isFeedLoading = false,
                    isRefreshingContent = false,
                    isSyncingContent = false,
                    isLiveSupabaseConnected = false
                )
                // A temporary network/Supabase failure must not erase the offline app.
                // Reconnect handling will retry the cloud sync automatically.
            } else {
                _uiState.value = _uiState.value.copy(destination = AppDestination.SIGN_IN)
            }
        }''',
    "auth failure keeps offline cache"
)

replace_once(
    vm,
    '''    private suspend fun restoreCachedAppSnapshot() {
        val cached = offlineContentStore.loadAppSnapshot() ?: return
        val current = _uiState.value
        val activeUsername = current.myProfile.username
        if (activeUsername.isBlank() || !cached.ownerUsername.equals(activeUsername, true)) return

        _uiState.value = current.copy(
            myProfile = cached.myProfile.takeIf { it.username.equals(activeUsername, true) } ?: current.myProfile,
            stories = cached.stories.ifEmpty { current.stories },
            marketItems = cached.marketItems,
            leaderboardUsers = cached.leaderboardUsers,
            activities = cached.activities,
            connectHub = cached.connectHub,
            mutedUsers = cached.mutedUsers,
            isConnectHubLoading = false,
            activitiesLoading = false,
            isFeedLoading = false
        )
    }''',
    '''    private suspend fun restoreCachedAppSnapshot() {
        val cached = offlineContentStore.loadAppSnapshot() ?: return
        val current = _uiState.value
        val activeUsername = current.myProfile.username.ifBlank { offlineContentStore.cachedOwnerUsername() }
        if (activeUsername.isBlank() || !cached.ownerUsername.equals(activeUsername, true)) return

        val cachedProfile = cached.myProfile.takeIf { it.username.equals(activeUsername, true) }
        val restoredPosts = current.posts.ifEmpty { cached.posts }
        val restoredReels = current.reels.ifEmpty { cached.reels }
        val hasCachedFeed = restoredPosts.isNotEmpty() || restoredReels.isNotEmpty()

        _uiState.value = current.copy(
            myProfile = cachedProfile ?: current.myProfile,
            posts = restoredPosts,
            reels = restoredReels,
            profiles = current.profiles.ifEmpty { cached.profiles },
            conversations = current.conversations.ifEmpty { cached.conversations },
            stories = cached.stories.ifEmpty { current.stories },
            marketItems = current.marketItems.ifEmpty { cached.marketItems },
            leaderboardUsers = current.leaderboardUsers.ifEmpty { cached.leaderboardUsers },
            gameLeaderboardUsers = current.gameLeaderboardUsers.ifEmpty { cached.gameLeaderboardUsers },
            activities = current.activities.ifEmpty { cached.activities },
            connectHub = if (current.connectHub == ConnectHubSnapshot()) cached.connectHub else current.connectHub,
            mutedUsers = if (current.mutedUsers.isEmpty()) cached.mutedUsers else current.mutedUsers,
            blinkCoinBalance = if (current.blinkCoinBalance == 0L) cached.blinkCoinBalance else current.blinkCoinBalance,
            isConnectHubLoading = false,
            activitiesLoading = false,
            isFeedLoading = if (!current.isOnline || hasCachedFeed) false else current.isFeedLoading
        )
    }''',
    "full snapshot restore"
)

replace_once(
    vm,
    '''                        CachedAppSnapshot(
                            ownerUsername = snapshot.myProfile.username,
                            myProfile = snapshot.myProfile,
                            stories = snapshot.stories,
                            marketItems = snapshot.marketItems,
                            leaderboardUsers = snapshot.leaderboardUsers,
                            activities = snapshot.activities,
                            connectHub = snapshot.connectHub,
                            mutedUsers = snapshot.mutedUsers
                        )''',
    '''                        CachedAppSnapshot(
                            ownerUsername = snapshot.myProfile.username,
                            myProfile = snapshot.myProfile,
                            posts = snapshot.posts,
                            reels = snapshot.reels,
                            profiles = snapshot.profiles,
                            conversations = snapshot.conversations,
                            stories = snapshot.stories,
                            marketItems = snapshot.marketItems,
                            leaderboardUsers = snapshot.leaderboardUsers,
                            gameLeaderboardUsers = snapshot.gameLeaderboardUsers,
                            activities = snapshot.activities,
                            connectHub = snapshot.connectHub,
                            mutedUsers = snapshot.mutedUsers,
                            blinkCoinBalance = snapshot.blinkCoinBalance
                        )''',
    "full snapshot persist"
)

# Cache standalone Connect Hub refreshes too, not only full app syncs.
replace_once(
    vm,
    '''                .onSuccess { snapshot ->
                    _uiState.value = _uiState.value.copy(
                        connectHub = snapshot,
                        isConnectHubLoading = false
                    )
                }''',
    '''                .onSuccess { snapshot ->
                    _uiState.value = _uiState.value.copy(
                        connectHub = snapshot,
                        isConnectHubLoading = false
                    )
                    persistExtendedCache()
                }''',
    "connect hub cache persist"
)


# ---------------------------------------------------------------------------
# 3. Restrain feed motion. Avoid per-card delayed slide-ins while scrolling and
#    stop bottom navigation/FAB state from toggling for every 2px scroll event.
# ---------------------------------------------------------------------------
feed = ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
replace_once(
    feed,
    '''    var horizontalDrag by remember { mutableStateOf(0f) }
    val swipeThreshold = with(density) { 64.dp.toPx() }
''',
    '''    var horizontalDrag by remember { mutableStateOf(0f) }
    val swipeThreshold = with(density) { 64.dp.toPx() }
    val chromeScrollThreshold = with(density) { 20.dp.toPx() }
    val scrollAccumulator = remember { floatArrayOf(0f) }
    var bottomChromeVisible by remember { mutableStateOf(true) }
''',
    "feed scroll threshold state"
)

replace_once(
    feed,
    '''    val scrollConnection = remember(onBottomBarVisibilityChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -2f) {
                    fabExpanded = false
                    onBottomBarVisibilityChange(false)
                } else if (available.y > 2f) {
                    fabExpanded = true
                    onBottomBarVisibilityChange(true)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }''',
    '''    val scrollConnection = remember(onBottomBarVisibilityChange, chromeScrollThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                scrollAccumulator[0] = (scrollAccumulator[0] + available.y)
                    .coerceIn(-chromeScrollThreshold * 2f, chromeScrollThreshold * 2f)

                when {
                    scrollAccumulator[0] <= -chromeScrollThreshold && bottomChromeVisible -> {
                        bottomChromeVisible = false
                        fabExpanded = false
                        scrollAccumulator[0] = 0f
                        onBottomBarVisibilityChange(false)
                    }
                    scrollAccumulator[0] >= chromeScrollThreshold && !bottomChromeVisible -> {
                        bottomChromeVisible = true
                        fabExpanded = true
                        scrollAccumulator[0] = 0f
                        onBottomBarVisibilityChange(true)
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }''',
    "restrained chrome scroll handling"
)

replace_once(
    feed,
    '''        fabExpanded = true
        onBottomBarVisibilityChange(true)
    }

    LaunchedEffect(homeReselectSignal) {''',
    '''        scrollAccumulator[0] = 0f
        bottomChromeVisible = true
        fabExpanded = true
        onBottomBarVisibilityChange(true)
    }

    LaunchedEffect(homeReselectSignal) {''',
    "reset scroll state on lane switch"
)

replace_once(
    feed,
    '''            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            } else {
                onRefresh()
            }
            fabExpanded = true
            onBottomBarVisibilityChange(true)''',
    '''            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                // Avoid animating through hundreds of composed rows when the user is deep
                // in the feed. Jump near the top, then animate only the final short distance.
                if (listState.firstVisibleItemIndex > 8) listState.scrollToItem(8)
                listState.animateScrollToItem(0)
            } else {
                onRefresh()
            }
            scrollAccumulator[0] = 0f
            bottomChromeVisible = true
            fabExpanded = true
            onBottomBarVisibilityChange(true)''',
    "bounded scroll-to-top motion"
)

replace_once(
    feed,
    '''    val enterOffsetPx = with(density) { 8.dp.roundToPx() }

    AnimatedVisibility(
        visible = screenVisible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { enterOffsetPx },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { enterOffsetPx },
        modifier = Modifier.fillMaxSize()
    ) {''',
    '''    AnimatedVisibility(
        visible = screenVisible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
        modifier = Modifier.fillMaxSize()
    ) {''',
    "restrained screen entrance"
)

replace_once(
    feed,
    '''@Composable
private fun PremiumPostEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * 35L))
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { 24 },
        exit = fadeOut(tween(120)),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}''',
    '''@Composable
private fun PremiumPostEntrance(index: Int, content: @Composable () -> Unit) {
    // Only the first few rows get a very small initial fade. Rows composed during normal
    // scrolling render immediately, avoiding the delayed website-like card animation.
    if (index > 2) {
        content()
        return
    }

    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(70)),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}''',
    "restrained post entrance"
)


# ---------------------------------------------------------------------------
# 4. Add a regression test proving the expanded durable snapshot round-trips.
# ---------------------------------------------------------------------------
test = ROOT / "app/src/test/java/com/example/data/local/OfflineContentCodecTest.kt"
text = test.read_text()
if "fun appSnapshotRoundTripPreservesFeedAndMessages()" not in text:
    insert = '''

    @Test
    fun appSnapshotRoundTripPreservesFeedAndMessages() {
        val post = FeedPost(
            id = "cached-post",
            author = "glimmar",
            authorAvatar = "",
            timeAgo = "Now",
            text = "Still here offline",
            likes = 1,
            commentsCount = 2,
            sharesCount = 3
        )
        val message = com.example.data.models.ChatMessage(
            id = "message-1",
            text = "Cached message",
            isFromMe = true,
            senderUsername = "glimmar"
        )
        val conversation = com.example.data.models.ChatConversation(
            id = "conversation-1",
            partnerUsername = "friend",
            partnerName = "Friend",
            partnerAvatar = "",
            messages = mutableListOf(message)
        )
        val snapshot = CachedAppSnapshot(
            ownerUsername = "glimmar",
            myProfile = UserProfile(fullName = "Gideon", username = "glimmar"),
            posts = listOf(post),
            reels = listOf(post.copy(id = "cached-reel", isReel = true, videoUrl = "https://example.com/reel.mp4")),
            conversations = listOf(conversation),
            blinkCoinBalance = 42L
        )

        val restored = codec.decodeAppSnapshot(requireNotNull(codec.encodeAppSnapshot(snapshot)))

        assertNotNull(restored)
        assertEquals("cached-post", restored?.posts?.single()?.id)
        assertEquals("cached-reel", restored?.reels?.single()?.id)
        assertEquals("Cached message", restored?.conversations?.single()?.messages?.single()?.text)
        assertEquals(42L, restored?.blinkCoinBalance)
    }
'''
    marker = "\n}\n"
    if not text.endswith(marker):
        raise SystemExit(f"Unexpected test file ending in {test}")
    test.write_text(text[:-len(marker)] + insert + marker)

print("Applied offline-first cache + restrained feed motion patch")

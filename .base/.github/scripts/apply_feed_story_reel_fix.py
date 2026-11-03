from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"Expected block not found: {label}")
    return text.replace(old, new, 1)


def replace_regex_once(text: str, pattern: str, repl: str, label: str) -> str:
    updated, count = re.subn(pattern, repl, text, count=1, flags=re.MULTILINE | re.DOTALL)
    if count != 1:
        raise RuntimeError(f"Expected exactly one regex match for {label}, found {count}")
    return updated


# -----------------------------------------------------------------------------
# Feed: smooth bottom-bar visibility changes + Home reselect scroll/refresh.
# -----------------------------------------------------------------------------
feed_path = "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
feed = read(feed_path)

feed = replace_once(
    feed,
    """    isLoadingMoreReels: Boolean = false,\n    onLoadMorePosts: () -> Unit = {},\n    onLoadMoreReels: () -> Unit = {},\n    onBottomBarVisibilityChange: (Boolean) -> Unit = {}\n) {""",
    """    isLoadingMoreReels: Boolean = false,\n    onLoadMorePosts: () -> Unit = {},\n    onLoadMoreReels: () -> Unit = {},\n    homeReselectSignal: Int = 0,\n    onBottomBarVisibilityChange: (Boolean) -> Unit = {}\n) {""",
    "FeedScreen signature",
)

feed = replace_once(
    feed,
    """    val bottomBarVisibility by rememberUpdatedState(onBottomBarVisibilityChange)\n    val recordVisiblePost by rememberUpdatedState(onViewedPost)\n    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }""",
    """    val bottomBarVisibility by rememberUpdatedState(onBottomBarVisibilityChange)\n    val recordVisiblePost by rememberUpdatedState(onViewedPost)\n    val refreshFeed by rememberUpdatedState(onRefresh)\n    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }""",
    "FeedScreen updated callbacks",
)

feed = replace_regex_once(
    feed,
    r"""    val nestedScrollConnection = remember\(selectedTopTab\) \{\n        object : NestedScrollConnection \{\n            private var lastVisible = true\n\n            override fun onPreScroll\(available: Offset, source: NestedScrollSource\): Offset \{\n                val shouldBeVisible = when \{\n                    available\.y < -8f -> false\n                    available\.y > 8f -> true\n                    else -> null\n                \}\n                if \(shouldBeVisible != null && shouldBeVisible != lastVisible\) \{\n                    lastVisible = shouldBeVisible\n                    bottomBarVisibility\(shouldBeVisible\)\n                \}\n                return Offset\.Zero\n            \}\n        \}\n    \}""",
    """    val nestedScrollConnection = remember(selectedTopTab) {\n        object : NestedScrollConnection {\n            private var lastVisible = true\n            private var accumulatedScroll = 0f\n\n            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {\n                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero\n\n                // Do not animate the bottom bar for every tiny finger movement. Accumulate\n                // intentional movement and only change visibility after a meaningful swipe.\n                if ((accumulatedScroll > 0f && available.y < 0f) ||\n                    (accumulatedScroll < 0f && available.y > 0f)\n                ) {\n                    accumulatedScroll = 0f\n                }\n\n                accumulatedScroll = (accumulatedScroll + available.y).coerceIn(-160f, 160f)\n                val shouldBeVisible = when {\n                    accumulatedScroll <= -56f -> false\n                    accumulatedScroll >= 56f -> true\n                    else -> null\n                }\n\n                if (shouldBeVisible != null) {\n                    accumulatedScroll = 0f\n                    if (shouldBeVisible != lastVisible) {\n                        lastVisible = shouldBeVisible\n                        bottomBarVisibility(shouldBeVisible)\n                    }\n                }\n                return Offset.Zero\n            }\n        }\n    }""",
    "Feed nested-scroll throttling",
)

feed = replace_once(
    feed,
    """    LaunchedEffect(selectedTopTab) {\n        bottomBarVisibility(true)\n    }""",
    """    LaunchedEffect(selectedTopTab) {\n        bottomBarVisibility(true)\n    }\n\n    LaunchedEffect(homeReselectSignal, selectedTopTab) {\n        if (homeReselectSignal <= 0 || selectedTopTab != 0) return@LaunchedEffect\n\n        bottomBarVisibility(true)\n        val isAlreadyAtTop = listState.firstVisibleItemIndex == 0 &&\n            listState.firstVisibleItemScrollOffset == 0\n\n        if (!isAlreadyAtTop) {\n            // For long feeds, jump near the top first so the visible smooth animation is quick\n            // instead of trying to animate through hundreds of composed rows.\n            if (listState.firstVisibleItemIndex > 8) {\n                listState.scrollToItem(8)\n            }\n            listState.animateScrollToItem(0)\n        }\n        refreshFeed()\n    }""",
    "Feed Home reselect effect",
)

write(feed_path, feed)


# -----------------------------------------------------------------------------
# Main activity: detect reselect of bottom Home and signal FeedScreen.
# -----------------------------------------------------------------------------
main_path = "app/src/main/java/com/example/MainActivity.kt"
main = read(main_path)

main = replace_once(
    main,
    """    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }""",
    """    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }\n    var homeReselectSignal by rememberSaveable { mutableIntStateOf(0) }""",
    "MainAppContent Home signal state",
)

main = replace_once(
    main,
    """                        onLoadMorePosts = { viewModel.loadMoreFeed(false) },\n                        onLoadMoreReels = { viewModel.loadMoreFeed(true) },\n                        onBottomBarVisibilityChange = { isVisible ->""",
    """                        onLoadMorePosts = { viewModel.loadMoreFeed(false) },\n                        onLoadMoreReels = { viewModel.loadMoreFeed(true) },\n                        homeReselectSignal = homeReselectSignal,\n                        onBottomBarVisibilityChange = { isVisible ->""",
    "FeedScreen Home signal argument",
)

main = replace_once(
    main,
    """            FloatingBottomBar(\n                currentTab = uiState.selectedTab,\n                onTabSelected = {\n                    isBottomBarVisibleByScroll = true\n                    viewModel.setTab(it)\n                },\n                isDark = uiState.isDarkMode\n            )""",
    """            FloatingBottomBar(\n                currentTab = uiState.selectedTab,\n                onTabSelected = { tab ->\n                    isBottomBarVisibleByScroll = true\n                    if (tab == MainTab.HOME && uiState.selectedTab == MainTab.HOME) {\n                        viewModel.setFeedSubTab(0)\n                        homeReselectSignal++\n                    } else {\n                        viewModel.setTab(tab)\n                    }\n                },\n                isDark = uiState.isDarkMode\n            )""",
    "Bottom Home reselect behavior",
)

write(main_path, main)


# -----------------------------------------------------------------------------
# Stories: show current user's real stories, use stable IDs, and skeleton empties.
# -----------------------------------------------------------------------------
story_path = "app/src/main/java/com/example/ui/components/StoryBar.kt"
story = read(story_path)

story = replace_once(
    story,
    """    val visibleStories = remember(stories) {\n        stories.filter { !it.isUser }\n    }""",
    """    val visibleStories = remember(stories) {\n        // Keep real stories uploaded by the current user. Only hide the synthetic\n        // \"Your Story\" placeholder when it has no media.\n        stories.filterNot { it.id == \"story_me\" && it.storyImage.isBlank() }\n    }""",
    "StoryBar current-user filter",
)

story = replace_once(
    story,
    """            items(\n                items = visibleStories,\n                key = { story ->\n                    story.username\n                }\n            ) { story ->\n\n                PremiumStoryItem(\n                    story = story,\n                    pressed = pressedStoryId == story.username,\n                    onPressed = {\n                        pressedStoryId = story.username\n                    },\n                    onClick = {\n                        showNewBadge = false\n                        onStoryClick(story)\n                    }\n                )\n            }""",
    """            if (visibleStories.isEmpty()) {\n                items(4, key = { index -> \"story_skeleton_$index\" }) {\n                    StorySkeletonItem()\n                }\n            } else {\n                items(\n                    items = visibleStories,\n                    key = { story -> story.id }\n                ) { story ->\n                    PremiumStoryItem(\n                        story = story,\n                        pressed = pressedStoryId == story.id,\n                        onPressed = {\n                            pressedStoryId = story.id\n                        },\n                        onClick = {\n                            showNewBadge = false\n                            onStoryClick(story)\n                        }\n                    )\n                }\n            }""",
    "StoryBar stable items and skeletons",
)

story = replace_once(
    story,
    """// ====================================================================\n// HEADER ICON\n// ====================================================================\n\n@Composable\nprivate fun StoryHeaderIcon()""",
    """// ====================================================================\n// EMPTY STORY SKELETON\n// ====================================================================\n\n@Composable\nprivate fun StorySkeletonItem() {\n    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)\n    val highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .16f)\n\n    Column(\n        horizontalAlignment = Alignment.CenterHorizontally,\n        modifier = Modifier.width(70.dp)\n    ) {\n        Box(\n            Modifier\n                .size(64.dp)\n                .shimmerBackground(CircleShape, base, highlight)\n        )\n        Spacer(Modifier.height(7.dp))\n        Box(\n            Modifier\n                .width(46.dp)\n                .height(9.dp)\n                .shimmerBackground(RoundedCornerShape(100.dp), base, highlight)\n        )\n        Spacer(Modifier.height(4.dp))\n        Box(\n            Modifier\n                .width(32.dp)\n                .height(7.dp)\n                .shimmerBackground(RoundedCornerShape(100.dp), base, highlight)\n        )\n    }\n}\n\n// ====================================================================\n// HEADER ICON\n// ====================================================================\n\n@Composable\nprivate fun StoryHeaderIcon()""",
    "Story skeleton helper",
)

story = replace_once(
    story,
    """            .testTag(\n                \"story_item_${story.username}\"\n            )\n            .pointerInput(story.username) {""",
    """            .testTag(\n                \"story_item_${story.id}\"\n            )\n            .pointerInput(story.id) {""",
    "Story item stable pointer/test key",
)

write(story_path, story)


# -----------------------------------------------------------------------------
# Create Story: fix preview layout scope and keep caption usable with keyboard.
# -----------------------------------------------------------------------------
create_story_path = "app/src/main/java/com/example/ui/screens/CreateStoryScreen.kt"
create_story = read(create_story_path)

create_story = replace_once(
    create_story,
    """Column(Modifier.fillMaxSize().systemBarsPadding()){""",
    """Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()){""",
    "CreateStory keyboard insets",
)

create_story = replace_once(
    create_story,
    """modifier = Modifier.align(Alignment.Center),""",
    """modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),""",
    "CreateStory video preview alignment",
)

write(create_story_path, create_story)


# -----------------------------------------------------------------------------
# Reels: horizontal gestures. Swipe left -> Home; swipe right -> author profile.
# -----------------------------------------------------------------------------
reels_path = "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt"
reels = read(reels_path)

reels = replace_once(
    reels,
    """import androidx.compose.foundation.gestures.detectTapGestures""",
    """import androidx.compose.foundation.gestures.detectHorizontalDragGestures\nimport androidx.compose.foundation.gestures.detectTapGestures""",
    "Reels horizontal gesture import",
)

reels = replace_once(
    reels,
    """                onDelete = onDelete,\n                onProfileClick = onProfileClick\n            )""",
    """                onDelete = onDelete,\n                onProfileClick = onProfileClick,\n                onSwipeToHome = onBackToPosts,\n                onSwipeToProfile = { onProfileClick(reel.author) }\n            )""",
    "ReelPage swipe callbacks",
)

reels = replace_once(
    reels,
    """    onDelete: (String) -> Unit,\n    onProfileClick: (String) -> Unit\n) {""",
    """    onDelete: (String) -> Unit,\n    onProfileClick: (String) -> Unit,\n    onSwipeToHome: () -> Unit,\n    onSwipeToProfile: () -> Unit\n) {""",
    "ReelPage signature",
)

reels = replace_once(
    reels,
    """    var progress by remember(reel.id) { mutableStateOf(0f) }""",
    """    var progress by remember(reel.id) { mutableStateOf(0f) }\n    var horizontalDrag by remember(reel.id) { mutableFloatStateOf(0f) }""",
    "ReelPage horizontal state",
)

reels = replace_once(
    reels,
    """            .pointerInput(reel.id) {\n                detectTapGestures(\n                    onDoubleTap = {\n                        if (!reel.isLiked) onLike(reel.id)\n                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n                        burstTrigger++\n                    },\n                    onTap = {\n                        isMuted = !isMuted\n                        showMuteHint = true\n                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)\n                    }\n                )\n            }\n    ) {""",
    """            .pointerInput(reel.id) {\n                detectTapGestures(\n                    onDoubleTap = {\n                        if (!reel.isLiked) onLike(reel.id)\n                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)\n                        burstTrigger++\n                    },\n                    onTap = {\n                        isMuted = !isMuted\n                        showMuteHint = true\n                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)\n                    }\n                )\n            }\n            .pointerInput(reel.id, isActive) {\n                if (!isActive) return@pointerInput\n                val swipeThreshold = 84.dp.toPx()\n                detectHorizontalDragGestures(\n                    onDragStart = { horizontalDrag = 0f },\n                    onHorizontalDrag = { _, dragAmount ->\n                        horizontalDrag += dragAmount\n                    },\n                    onDragEnd = {\n                        when {\n                            horizontalDrag <= -swipeThreshold -> onSwipeToHome()\n                            horizontalDrag >= swipeThreshold -> onSwipeToProfile()\n                        }\n                        horizontalDrag = 0f\n                    },\n                    onDragCancel = { horizontalDrag = 0f }\n                )\n            }\n    ) {""",
    "ReelPage horizontal swipe detector",
)

write(reels_path, reels)


# -----------------------------------------------------------------------------
# ViewModel: do not throw away previous stories uploaded by the current user.
# -----------------------------------------------------------------------------
vm_path = "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
vm = read(vm_path)

old_create = """_uiState.value=_uiState.value.copy(stories=listOf(s)+_uiState.value.stories.filter{!it.isUser&&it.id!=\"story_me\"})"""
new_create = """_uiState.value=_uiState.value.copy(stories=listOf(s)+_uiState.value.stories.filter{it.id!=\"story_me\"&&it.id!=s.id})"""
vm = replace_once(vm, old_create, new_create, "createStory preserves own stories")

old_publish = """_uiState.value=_uiState.value.copy(stories=listOf(story)+_uiState.value.stories.filter{!it.isUser&&it.id!=\"story_me\"},isCreateStoryOpen=false)"""
new_publish = """_uiState.value=_uiState.value.copy(stories=listOf(story)+_uiState.value.stories.filter{it.id!=\"story_me\"&&it.id!=story.id},isCreateStoryOpen=false)"""
vm = replace_once(vm, old_publish, new_publish, "publishStory preserves own stories")

write(vm_path, vm)

print("Applied feed smoothness, Home refresh, story, create-story, and reel gesture fixes.")

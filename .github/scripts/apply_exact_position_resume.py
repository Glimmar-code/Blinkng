from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Persist the premium home lane, filter, and exact LazyColumn scroll position.
# -----------------------------------------------------------------------------
premium_path = "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
premium = read(premium_path)

premium = replace_once(
    premium,
    '''    var feedLane by rememberSaveable(currentUsername) { mutableIntStateOf(0) }
    val followingIds by FollowStateStore.followingIds.collectAsState()
''',
    '''    val context = LocalContext.current
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val resumeUserKey = remember(currentUsername) {
        currentUsername.trim().removePrefix("@").lowercase().ifBlank { "anonymous" }
    }
    var feedLane by rememberSaveable(resumeUserKey) {
        mutableIntStateOf(
            resumePrefs.getInt("home_lane:$resumeUserKey", 0).coerceIn(0, 1)
        )
    }
    LaunchedEffect(feedLane, resumeUserKey) {
        resumePrefs.edit()
            .putInt("home_lane:$resumeUserKey", feedLane.coerceIn(0, 1))
            .apply()
    }
    val followingIds by FollowStateStore.followingIds.collectAsState()
''',
    "premium feed lane persistence",
)

premium = replace_once(
    premium,
    '''            currentUsername = currentUsername,
            userAvatar = userAvatar,
            laneIndex = feedLane,
''',
    '''            currentUsername = currentUsername,
            userAvatar = userAvatar,
            resumeUserKey = resumeUserKey,
            laneIndex = feedLane,
''',
    "home feed resume key call",
)

premium = replace_once(
    premium,
    '''    currentUsername: String,
    userAvatar: String,
    laneIndex: Int,
''',
    '''    currentUsername: String,
    userAvatar: String,
    resumeUserKey: String,
    laneIndex: Int,
''',
    "home feed resume key signature",
)

premium = replace_once(
    premium,
    '''    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
''',
    '''    val context = LocalContext.current
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val laneResumeKey = "$resumeUserKey:$laneIndex"
    val listState = rememberLazyListState()
    var restoredLaneResumeKey by remember { mutableStateOf<String?>(null) }
    var restoringScroll by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
''',
    "home list resume setup",
)

premium = replace_once(
    premium,
    '''    var filter by remember { mutableStateOf(PremiumFeedFilter.ALL) }
''',
    '''    var filter by remember(laneResumeKey) {
        mutableStateOf(
            runCatching {
                PremiumFeedFilter.valueOf(
                    resumePrefs.getString(
                        "home_filter:$laneResumeKey",
                        PremiumFeedFilter.ALL.name
                    ) ?: PremiumFeedFilter.ALL.name
                )
            }.getOrDefault(PremiumFeedFilter.ALL)
        )
    }
    LaunchedEffect(filter, laneResumeKey) {
        resumePrefs.edit()
            .putString("home_filter:$laneResumeKey", filter.name)
            .apply()
    }
''',
    "home filter persistence",
)

premium = replace_once(
    premium,
    '''    val context = LocalContext.current
    val networkMonitor = remember(context) { NetworkMonitor(context) }
''',
    '''    val networkMonitor = remember(context) { NetworkMonitor(context) }
''',
    "remove duplicate premium context",
)

premium = replace_once(
    premium,
    '''    LaunchedEffect(laneIndex) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
        scrollAccumulator[0] = 0f
        bottomChromeVisible = true
        fabExpanded = true
        onBottomBarVisibilityChange(true)
    }
''',
    '''    LaunchedEffect(laneResumeKey, filteredPosts.isNotEmpty()) {
        if (restoredLaneResumeKey != laneResumeKey) {
            val savedIndex = resumePrefs
                .getInt("home_scroll_index:$laneResumeKey", 0)
                .coerceAtLeast(0)
            val savedOffset = resumePrefs
                .getInt("home_scroll_offset:$laneResumeKey", 0)
                .coerceAtLeast(0)

            // Wait until the cached/ranked rows have had one frame to enter the LazyColumn.
            // A saved non-zero position is only restored once content exists.
            if (savedIndex == 0 || filteredPosts.isNotEmpty()) {
                restoringScroll = true
                delay(16)
                val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                runCatching {
                    listState.scrollToItem(
                        savedIndex.coerceAtMost(maxIndex),
                        savedOffset
                    )
                }
                restoredLaneResumeKey = laneResumeKey
                restoringScroll = false
            }
        }

        scrollAccumulator[0] = 0f
        bottomChromeVisible = true
        fabExpanded = true
        onBottomBarVisibilityChange(true)
    }

    LaunchedEffect(listState, laneResumeKey) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collectLatest { (index, offset) ->
            if (!restoringScroll && restoredLaneResumeKey == laneResumeKey) {
                resumePrefs.edit()
                    .putInt("home_scroll_index:$laneResumeKey", index)
                    .putInt("home_scroll_offset:$laneResumeKey", offset)
                    .apply()
            }
        }
    }
''',
    "replace forced top reset with saved scroll restore",
)

write(premium_path, premium)


# -----------------------------------------------------------------------------
# Persist the currently viewed reel by ID (with index fallback).
# -----------------------------------------------------------------------------
reels_path = "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt"
reels = read(reels_path)

if "import kotlinx.coroutines.flow.collectLatest" not in reels:
    reels = replace_once(
        reels,
        '''import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
''',
        '''import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
''',
        "reels collectLatest import",
    )

reels = replace_once(
    reels,
    '''    val pager = rememberPagerState(pageCount = { reels.size })
    var selectedTab by remember { mutableStateOf("For You") }
''',
    '''    val context = LocalContext.current
    val resumePrefs = remember(context) {
        context.getSharedPreferences("blink_resume_positions", android.content.Context.MODE_PRIVATE)
    }
    val resumeUserKey = remember(currentUsername) {
        currentUsername.trim().removePrefix("@").lowercase().ifBlank { "anonymous" }
    }
    val initialPage = remember(reels, resumeUserKey) {
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

    LaunchedEffect(pager, reels, resumeUserKey) {
        snapshotFlow { pager.currentPage }.collectLatest { page ->
            reels.getOrNull(page)?.let { reel ->
                resumePrefs.edit()
                    .putInt("reel_index:$resumeUserKey", page)
                    .putString("reel_id:$resumeUserKey", reel.id)
                    .apply()
            }
        }
    }
''',
    "reel pager persistence",
)

write(reels_path, reels)

print("Applied exact home-feed and reel position resume patch")

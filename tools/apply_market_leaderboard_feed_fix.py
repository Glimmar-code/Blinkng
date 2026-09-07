from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    path.write_text(text.replace(old, new, 1))


# 1) Marketplace: remove visible hard-coded product suggestions and prevent legacy
# cached/mock marketplace rows from being restored. Live Supabase remains the source.
market = ROOT / "app/src/main/java/com/example/ui/screens/MarketScreen.kt"
replace_once(
    market,
    '    val searchSuggestions = listOf("iPhone", "MacBook", "Lab Coat", "Calculators", "Hostel Space", "JBL Speaker", "Textbooks")\n',
    '''    val searchSuggestions = remember(items) {\n        items.asSequence()\n            .flatMap { item -> sequenceOf(item.title, item.category) }\n            .map { it.trim() }\n            .filter { it.isNotBlank() }\n            .distinct()\n            .take(7)\n            .toList()\n    }\n''',
    "market live search suggestions",
)
replace_once(
    market,
    '''    val isVerified = verificationBadge != VerificationBadge.NONE\n\n    LazyColumn(\n''',
    '''    val marketRows = remember(filteredItems) { filteredItems.chunked(2) }\n    val isVerified = verificationBadge != VerificationBadge.NONE\n\n    LazyColumn(\n''',
    "market row memoization",
)
replace_once(
    market,
    '''        items(filteredItems.chunked(2)) { rowItems ->\n''',
    '''        items(\n            items = marketRows,\n            key = { rowItems ->\n                rowItems.joinToString(separator = "|") { item ->\n                    item.id.ifBlank { "${item.sellerUsername}:${item.title}" }\n                }\n            }\n        ) { rowItems ->\n''',
    "market stable keys",
)

vm = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
vm_text = vm.read_text()
legacy_cache = 'marketItems = current.marketItems.ifEmpty { cached.marketItems },'
if legacy_cache not in vm_text:
    raise RuntimeError("legacy market cache restore marker not found")
vm_text = vm_text.replace(legacy_cache, 'marketItems = current.marketItems,')
snapshot_marker = 'marketItems = snapshot.marketItems,'
if snapshot_marker not in vm_text:
    raise RuntimeError("snapshot market cache restore marker not found")
vm_text = vm_text.replace(snapshot_marker, 'marketItems = emptyList(),')
vm.write_text(vm_text)


# 2) Leaderboard: keep LazyColumn + stable keys, remove per-row delayed coroutines and
# animations that were being created while scrolling.
leaderboard = ROOT / "app/src/main/java/com/example/ui/screens/LeaderboardScreen.kt"
leaderboard.write_text(r'''package com.example.ui.screens

import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.LeaderboardUser
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    userProfile: UserProfile = UserProfile(),
    onProfileClick: (String) -> Unit,
    isDark: Boolean,
    onRefresh: () -> Unit = {}
) {
    var campusOnly by remember { mutableStateOf(false) }
    val all = remember(users) {
        users.asSequence()
            .filter { it.username.isNotBlank() }
            .sortedWith(
                compareByDescending<LeaderboardUser> { it.points }
                    .thenBy { it.username.lowercase() }
            )
            .mapIndexed { index, user -> user.copy(rank = index + 1) }
            .toList()
    }
    val campusName = userProfile.university
        .takeUnless { it.isBlank() || it.equals("null", true) }
        .orEmpty()
    val visible = remember(all, campusOnly, campusName) {
        if (campusOnly && campusName.isNotBlank()) {
            all.asSequence()
                .filter { it.university.equals(campusName, true) }
                .mapIndexed { index, user -> user.copy(rank = index + 1) }
                .toList()
        } else {
            all
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item(key = "leaderboard_header", contentType = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = BlinkGold.copy(alpha = .14f)) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        null,
                        tint = BlinkGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Leaderboard", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Live rankings from real Blink activity",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh live leaderboard")
                }
            }
        }

        item(key = "leaderboard_filters", contentType = "filters") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !campusOnly,
                    onClick = { campusOnly = false },
                    label = { Text("World") }
                )
                FilterChip(
                    selected = campusOnly,
                    enabled = campusName.isNotBlank(),
                    onClick = { campusOnly = true },
                    label = { Text(if (campusName.isBlank()) "Campus unavailable" else "My campus") }
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${visible.size} ranked",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (visible.isNotEmpty()) {
            item(key = "leaderboard_podium", contentType = "podium") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(1, 0, 2).forEach { idx ->
                        visible.getOrNull(idx)?.let { user ->
                            Surface(
                                Modifier
                                    .weight(1f)
                                    .height(if (idx == 0) 142.dp else 118.dp)
                                    .clickable { onProfileClick(user.username) },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (idx == 0) BlinkGold else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (idx == 0) BlinkGold.copy(alpha = .09f) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(if (idx == 0) "👑" else "#${idx + 1}", fontWeight = FontWeight.Black)
                                    AsyncImage(
                                        model = user.avatar,
                                        error = painterResource(R.drawable.ic_default_profile),
                                        fallback = painterResource(R.drawable.ic_default_profile),
                                        contentDescription = user.fullName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(if (idx == 0) 48.dp else 40.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        user.fullName.ifBlank { user.username },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${user.points} pts",
                                        fontSize = 10.sp,
                                        color = BlinkPink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item(key = "leaderboard_empty", contentType = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No live ranking data yet.", fontWeight = FontWeight.Bold)
                        TextButton(onClick = onRefresh) { Text("Refresh from Supabase") }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = visible,
                key = { _, user -> user.username },
                contentType = { _, _ -> "leaderboard_user" }
            ) { index, user ->
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable { onProfileClick(user.username) },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", modifier = Modifier.width(42.dp), fontWeight = FontWeight.Black)
                        AsyncImage(
                            model = user.avatar,
                            error = painterResource(R.drawable.ic_default_profile),
                            fallback = painterResource(R.drawable.ic_default_profile),
                            contentDescription = user.fullName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.fullName.ifBlank { user.username },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (user.verificationBadge != VerificationBadge.NONE) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = BlinkPink,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                "@${user.username}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            user.university
                                .takeUnless { it.isBlank() || it.equals("null", true) }
                                ?.let {
                                    Text(
                                        it,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${user.points}", fontWeight = FontWeight.Black, color = BlinkPink)
                            Text("points", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
''')


# 3) Feed: staged chrome behavior FULL -> PRIMARY_COLLAPSED -> IMMERSIVE.
feed = ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
replace_once(
    feed,
    '''    val chromeScrollThreshold = with(density) { 20.dp.toPx() }\n    val scrollAccumulator = remember { floatArrayOf(0f) }\n    var bottomChromeVisible by remember { mutableStateOf(true) }\n''',
    '''    val primaryCollapseThreshold = with(density) { 20.dp.toPx() }\n    val immersiveCollapseThreshold = with(density) { 56.dp.toPx() }\n    val scrollAccumulator = remember { floatArrayOf(0f) }\n    var chromeStage by remember(laneResumeKey) { mutableIntStateOf(0) }\n    var primaryHeaderVisible by remember(laneResumeKey) { mutableStateOf(true) }\n    var secondaryChromeVisible by remember(laneResumeKey) { mutableStateOf(true) }\n    var bottomChromeVisible by remember { mutableStateOf(true) }\n''',
    "feed staged chrome state",
)

old_scroll = '''    val scrollConnection = remember(onBottomBarVisibilityChange, chromeScrollThreshold) {\n        object : NestedScrollConnection {\n            override fun onPreScroll(\n                available: androidx.compose.ui.geometry.Offset,\n                source: NestedScrollSource\n            ): androidx.compose.ui.geometry.Offset {\n                scrollAccumulator[0] = (scrollAccumulator[0] + available.y)\n                    .coerceIn(-chromeScrollThreshold * 2f, chromeScrollThreshold * 2f)\n\n                when {\n                    scrollAccumulator[0] <= -chromeScrollThreshold && bottomChromeVisible -> {\n                        bottomChromeVisible = false\n                        fabExpanded = false\n                        scrollAccumulator[0] = 0f\n                        onBottomBarVisibilityChange(false)\n                    }\n                    scrollAccumulator[0] >= chromeScrollThreshold && !bottomChromeVisible -> {\n                        bottomChromeVisible = true\n                        fabExpanded = true\n                        scrollAccumulator[0] = 0f\n                        onBottomBarVisibilityChange(true)\n                    }\n                }\n                return androidx.compose.ui.geometry.Offset.Zero\n            }\n        }\n    }\n'''
new_scroll = '''    val scrollConnection = remember(\n        onBottomBarVisibilityChange,\n        primaryCollapseThreshold,\n        immersiveCollapseThreshold\n    ) {\n        object : NestedScrollConnection {\n            override fun onPreScroll(\n                available: androidx.compose.ui.geometry.Offset,\n                source: NestedScrollSource\n            ): androidx.compose.ui.geometry.Offset {\n                when {\n                    available.y < 0f -> {\n                        // Moving deeper into the feed. Collapse the primary header first,\n                        // then require a second deliberate scroll distance before entering\n                        // immersive mode. This hysteresis avoids tiny-movement flicker.\n                        scrollAccumulator[0] += -available.y\n                        when {\n                            chromeStage == 0 && scrollAccumulator[0] >= primaryCollapseThreshold -> {\n                                chromeStage = 1\n                                primaryHeaderVisible = false\n                                secondaryChromeVisible = true\n                                bottomChromeVisible = true\n                                fabExpanded = true\n                                scrollAccumulator[0] = 0f\n                                onBottomBarVisibilityChange(true)\n                            }\n                            chromeStage == 1 && scrollAccumulator[0] >= immersiveCollapseThreshold -> {\n                                chromeStage = 2\n                                primaryHeaderVisible = false\n                                secondaryChromeVisible = false\n                                bottomChromeVisible = false\n                                fabExpanded = false\n                                scrollAccumulator[0] = 0f\n                                onBottomBarVisibilityChange(false)\n                            }\n                        }\n                    }\n\n                    available.y > 0f -> {\n                        // Any meaningful reverse scroll leaves immersive mode immediately.\n                        // The primary header intentionally stays hidden until the list is\n                        // genuinely back at the first post.\n                        scrollAccumulator[0] = 0f\n                        if (chromeStage == 2) {\n                            chromeStage = 1\n                            primaryHeaderVisible = false\n                            secondaryChromeVisible = true\n                            bottomChromeVisible = true\n                            fabExpanded = true\n                            onBottomBarVisibilityChange(true)\n                        }\n                    }\n                }\n                return androidx.compose.ui.geometry.Offset.Zero\n            }\n        }\n    }\n'''
replace_once(feed, old_scroll, new_scroll, "feed staged scroll connection")

replace_once(
    feed,
    '''        scrollAccumulator[0] = 0f\n        bottomChromeVisible = true\n        fabExpanded = true\n        onBottomBarVisibilityChange(true)\n''',
    '''        scrollAccumulator[0] = 0f\n        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 1) {\n            chromeStage = 0\n            primaryHeaderVisible = true\n        } else {\n            chromeStage = 1\n            primaryHeaderVisible = false\n        }\n        secondaryChromeVisible = true\n        bottomChromeVisible = true\n        fabExpanded = true\n        onBottomBarVisibilityChange(true)\n''',
    "feed restored chrome state",
)

old_persist = '''        }.collectLatest { (index, offset) ->\n            if (!restoringScroll && restoredLaneResumeKey == laneResumeKey) {\n                resumePrefs.edit()\n                    .putInt("home_scroll_index:$laneResumeKey", index)\n                    .putInt("home_scroll_offset:$laneResumeKey", offset)\n                    .apply()\n            }\n        }\n'''
new_persist = '''        }.collectLatest { (index, offset) ->\n            if (!restoringScroll && restoredLaneResumeKey == laneResumeKey) {\n                resumePrefs.edit()\n                    .putInt("home_scroll_index:$laneResumeKey", index)\n                    .putInt("home_scroll_offset:$laneResumeKey", offset)\n                    .apply()\n            }\n\n            // The primary header is special: reverse scrolling never restores it.\n            // Only the actual top of the feed (first item, zero offset) can do that.\n            if (index == 0 && offset <= 1 && chromeStage != 0) {\n                chromeStage = 0\n                primaryHeaderVisible = true\n                secondaryChromeVisible = true\n                bottomChromeVisible = true\n                fabExpanded = true\n                scrollAccumulator[0] = 0f\n                onBottomBarVisibilityChange(true)\n            }\n        }\n'''
replace_once(feed, old_persist, new_persist, "feed top-only primary restore")

old_home = '''            if (listState.firstVisibleItemIndex >= 10) {\n                listState.scrollToItem(0)\n            } else {\n                onRefresh()\n            }\n            scrollAccumulator[0] = 0f\n            bottomChromeVisible = true\n            fabExpanded = true\n            onBottomBarVisibilityChange(true)\n'''
new_home = '''            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset > 1) {\n                listState.scrollToItem(0)\n            } else {\n                onRefresh()\n            }\n            scrollAccumulator[0] = 0f\n            chromeStage = 0\n            primaryHeaderVisible = true\n            secondaryChromeVisible = true\n            bottomChromeVisible = true\n            fabExpanded = true\n            onBottomBarVisibilityChange(true)\n'''
replace_once(feed, old_home, new_home, "feed home reselect")

old_headers = '''            Column(modifier = Modifier.fillMaxSize()) {\n                FeedTopBar(\n                    userAvatar = userAvatar,\n                    hasUnreadNotifications = hasUnreadNotifications,\n                    onSearchClick = onSearchClick,\n                    onNotificationClick = onOpenActivity,\n                    onMenuClick = onOpenMenu,\n                    onProfileClick = { onProfileClick(currentUsername) }\n                )\n\n                Box {\n                    FeedTabs(\n                        selectedIndex = laneIndex,\n                        onForYouClick = { onLaneChanged(0) },\n                        onFollowingClick = { onLaneChanged(1) },\n                        onGameClick = onGameClick,\n                        onReelClick = onReelClick,\n                        onFilterClick = { filterMenuVisible = true }\n                    )\n                    DropdownMenu(\n                        expanded = filterMenuVisible,\n                        onDismissRequest = { filterMenuVisible = false },\n                        modifier = Modifier.background(FeedElevatedSurface)\n                    ) {\n                        PremiumFilterItem("All posts", Icons.Default.Tune, filter == PremiumFeedFilter.ALL) {\n                            filter = PremiumFeedFilter.ALL\n                            filterMenuVisible = false\n                        }\n                        PremiumFilterItem("Photos", Icons.Default.Image, filter == PremiumFeedFilter.PHOTOS) {\n                            filter = PremiumFeedFilter.PHOTOS\n                            filterMenuVisible = false\n                        }\n                        PremiumFilterItem("Polls", Icons.Default.Poll, filter == PremiumFeedFilter.POLLS) {\n                            filter = PremiumFeedFilter.POLLS\n                            filterMenuVisible = false\n                        }\n                    }\n                }\n                HorizontalDivider(color = FeedBorder.copy(alpha = 0.72f))\n'''
new_headers = '''            Column(modifier = Modifier.fillMaxSize()) {\n                AnimatedVisibility(\n                    visible = primaryHeaderVisible,\n                    enter = fadeIn(tween(120)) + slideInVertically(tween(140)) { -it / 2 },\n                    exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { -it / 2 }\n                ) {\n                    FeedTopBar(\n                        userAvatar = userAvatar,\n                        hasUnreadNotifications = hasUnreadNotifications,\n                        onSearchClick = onSearchClick,\n                        onNotificationClick = onOpenActivity,\n                        onMenuClick = onOpenMenu,\n                        onProfileClick = { onProfileClick(currentUsername) }\n                    )\n                }\n\n                AnimatedVisibility(\n                    visible = secondaryChromeVisible,\n                    enter = fadeIn(tween(110)) + slideInVertically(tween(130)) { -it / 3 },\n                    exit = fadeOut(tween(90)) + slideOutVertically(tween(110)) { -it / 3 }\n                ) {\n                    Column {\n                        Box {\n                            FeedTabs(\n                                selectedIndex = laneIndex,\n                                onForYouClick = { onLaneChanged(0) },\n                                onFollowingClick = { onLaneChanged(1) },\n                                onGameClick = onGameClick,\n                                onReelClick = onReelClick,\n                                onFilterClick = { filterMenuVisible = true }\n                            )\n                            DropdownMenu(\n                                expanded = filterMenuVisible,\n                                onDismissRequest = { filterMenuVisible = false },\n                                modifier = Modifier.background(FeedElevatedSurface)\n                            ) {\n                                PremiumFilterItem("All posts", Icons.Default.Tune, filter == PremiumFeedFilter.ALL) {\n                                    filter = PremiumFeedFilter.ALL\n                                    filterMenuVisible = false\n                                }\n                                PremiumFilterItem("Photos", Icons.Default.Image, filter == PremiumFeedFilter.PHOTOS) {\n                                    filter = PremiumFeedFilter.PHOTOS\n                                    filterMenuVisible = false\n                                }\n                                PremiumFilterItem("Polls", Icons.Default.Poll, filter == PremiumFeedFilter.POLLS) {\n                                    filter = PremiumFeedFilter.POLLS\n                                    filterMenuVisible = false\n                                }\n                            }\n                        }\n                        HorizontalDivider(color = FeedBorder.copy(alpha = 0.72f))\n                    }\n                }\n'''
replace_once(feed, old_headers, new_headers, "feed animated staged headers")

old_fab = '''            CreatePostFab(\n                expanded = fabExpanded,\n                onClick = onOpenCreatePost,\n                modifier = Modifier\n                    .align(Alignment.BottomEnd)\n                    .navigationBarsPadding()\n                    .padding(end = 18.dp, bottom = 94.dp)\n            )\n'''
new_fab = '''            AnimatedVisibility(\n                visible = secondaryChromeVisible,\n                enter = fadeIn(tween(110)) + slideInVertically(tween(130)) { it / 2 },\n                exit = fadeOut(tween(90)) + slideOutVertically(tween(110)) { it / 2 },\n                modifier = Modifier.align(Alignment.BottomEnd)\n            ) {\n                CreatePostFab(\n                    expanded = fabExpanded,\n                    onClick = onOpenCreatePost,\n                    modifier = Modifier\n                        .navigationBarsPadding()\n                        .padding(end = 18.dp, bottom = 72.dp)\n                )\n            }\n'''
replace_once(feed, old_fab, new_fab, "feed fab visibility and spacing")

print("Applied marketplace, leaderboard, and staged feed chrome fixes.")

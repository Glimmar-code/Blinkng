from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEED = ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
BANNER = ROOT / "app/src/main/java/com/example/ui/components/OfflineConnectionBanner.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


banner_source = r'''package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun OfflineConnectionBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var showRestored by remember { mutableStateOf(false) }
    var previousOffline by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        val wasOffline = previousOffline
        previousOffline = visible

        if (visible) {
            showRestored = false
        } else if (wasOffline) {
            showRestored = true
            delay(2_500)
            showRestored = false
        }
    }

    val restored = !visible && showRestored
    val showBanner = visible || restored

    AnimatedVisibility(
        visible = showBanner,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 4.dp),
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(210, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(140))
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (restored) Color(0xFF16A34A) else Color(0xFFD92D20),
            shadowElevation = 4.dp,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (restored) Icons.Rounded.CheckCircle else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (restored) "Back online" else "No internet connection",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
'''

BANNER.write_text(banner_source, encoding="utf-8")

feed = FEED.read_text(encoding="utf-8")

if "import androidx.compose.ui.platform.LocalContext\n" not in feed:
    feed = replace_once(
        feed,
        "import androidx.compose.ui.platform.LocalDensity\n",
        "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity\n",
        "LocalContext import",
    )

if "import com.example.data.network.NetworkMonitor\n" not in feed:
    feed = replace_once(
        feed,
        "import com.example.data.models.UserProfile\n",
        "import com.example.data.models.UserProfile\nimport com.example.data.network.NetworkMonitor\n",
        "NetworkMonitor import",
    )

network_state = '''    val context = LocalContext.current
    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(
        initial = networkMonitor.isCurrentlyOnline()
    )
    var offlineEmptyConfirmed by remember { mutableStateOf(false) }
'''
if "var offlineEmptyConfirmed by remember" not in feed:
    feed = replace_once(
        feed,
        "    var bottomChromeVisible by remember { mutableStateOf(true) }\n",
        "    var bottomChromeVisible by remember { mutableStateOf(true) }\n\n" + network_state,
        "network state insertion",
    )

empty_confirmation = '''    LaunchedEffect(isOnline, isLoading, posts.isEmpty(), filteredPosts.isEmpty(), filter, laneIndex) {
        offlineEmptyConfirmed = false
        if (
            !isOnline &&
            !isLoading &&
            posts.isEmpty() &&
            filteredPosts.isEmpty() &&
            filter == PremiumFeedFilter.ALL &&
            laneIndex == 0
        ) {
            // Give the durable local cache a moment to hydrate before declaring it empty.
            // If cached posts arrive, this effect is cancelled and the empty card never flashes.
            delay(500)
            offlineEmptyConfirmed = true
        }
    }

'''
if "Give the durable local cache a moment to hydrate" not in feed:
    feed = replace_once(
        feed,
        "    val nearEnd by remember {\n",
        empty_confirmation + "    val nearEnd by remember {\n",
        "offline empty confirmation",
    )

old_empty_branch = '''                            filteredPosts.isEmpty() -> {
                                item(key = "empty_feed") {
                                    PremiumEmptyFeed(
                                        isFiltered = filter != PremiumFeedFilter.ALL,
                                        isFollowingLane = laneIndex == 1,
                                        onCreatePost = onOpenCreatePost,
                                        onClearFilter = { filter = PremiumFeedFilter.ALL }
                                    )
                                }
                            }
'''
new_empty_branch = '''                            filteredPosts.isEmpty() -> {
                                when {
                                    filter != PremiumFeedFilter.ALL || laneIndex == 1 -> {
                                        item(key = "empty_feed") {
                                            PremiumEmptyFeed(
                                                isFiltered = filter != PremiumFeedFilter.ALL,
                                                isFollowingLane = laneIndex == 1,
                                                offlineNoCache = false,
                                                onCreatePost = onOpenCreatePost,
                                                onClearFilter = { filter = PremiumFeedFilter.ALL }
                                            )
                                        }
                                    }

                                    !isOnline && posts.isEmpty() && !offlineEmptyConfirmed -> {
                                        // Never flash an empty-feed message while disk cache may still hydrate.
                                        items(2, key = { "cache_wait:$it" }) {
                                            PremiumFeedSkeleton()
                                        }
                                    }

                                    else -> {
                                        item(key = "empty_feed") {
                                            PremiumEmptyFeed(
                                                isFiltered = false,
                                                isFollowingLane = false,
                                                offlineNoCache = !isOnline && posts.isEmpty() && offlineEmptyConfirmed,
                                                onCreatePost = onOpenCreatePost,
                                                onClearFilter = { filter = PremiumFeedFilter.ALL }
                                            )
                                        }
                                    }
                                }
                            }
'''
if "cache_wait:$it" not in feed:
    feed = replace_once(feed, old_empty_branch, new_empty_branch, "empty feed branch")

old_signature = '''private fun PremiumEmptyFeed(
    isFiltered: Boolean,
    isFollowingLane: Boolean,
    onCreatePost: () -> Unit,
    onClearFilter: () -> Unit
) {
'''
new_signature = '''private fun PremiumEmptyFeed(
    isFiltered: Boolean,
    isFollowingLane: Boolean,
    offlineNoCache: Boolean = false,
    onCreatePost: () -> Unit,
    onClearFilter: () -> Unit
) {
'''
if "offlineNoCache: Boolean = false" not in feed:
    feed = replace_once(feed, old_signature, new_signature, "PremiumEmptyFeed signature")

feed = replace_once(
    feed,
    '''                else -> "Your feed is ready for something new"''',
    '''                else -> if (offlineNoCache) "Your feed is ready for something new" else "No posts to show right now"''',
    "empty title copy",
)
feed = replace_once(
    feed,
    '''                else -> "Share an update, photo or poll with your campus."''',
    '''                else -> if (offlineNoCache) "You're offline and there are no saved posts on this device." else "Pull to refresh for the latest posts."''',
    "empty subtitle copy",
)

FEED.write_text(feed, encoding="utf-8")
print("Applied feed cache gating and compact offline/reconnect banner changes.")

#!/usr/bin/env python3
"""Fail-closed static contract for the approved BLINK Home layout.

These checks guard structural regressions; screenshot/pixel-diff and handset QA
are still required before declaring visual equivalence.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHROME = (ROOT / "app/src/main/java/com/example/ui/components/PremiumFeedChrome.kt").read_text()
HOME = (ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt").read_text()
POST = (ROOT / "app/src/main/java/com/example/ui/components/PostCard.kt").read_text()
NAV = (ROOT / "app/src/main/java/com/example/ui/components/FloatingBottomBar.kt").read_text()
ITEMS = (ROOT / "app/src/main/java/com/example/ui/screens/BlinkItemsScreen.kt").read_text()
MAIN = (ROOT / "app/src/main/java/com/example/MainActivity.kt").read_text()
DESKTOP = (ROOT / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/ContentScreens.kt").read_text()


def method_body(code: str, start: str, end: str) -> str:
    a, b = code.find(start), code.find(end)
    if a < 0 or b <= a:
        raise AssertionError(f"Cannot isolate {start!r} through {end!r}")
    return code[a:b]


class HomeReferenceContractTests(unittest.TestCase):
    def test_header_has_four_consistent_actions(self):
        header = method_body(CHROME, "private fun FeedHeaderActions(", "private fun FeedRadialHeaderAction(")
        self.assertEqual(header.count("FeedRadialHeaderAction("), 4)
        for token in (
            'Icons.Outlined.Search', 'Icons.Default.GridView',
            'Icons.Outlined.NotificationsNone', 'Icons.Outlined.Menu',
        ):
            self.assertIn(token, header)
        self.assertNotIn("Icons.Outlined.Security", header)
        self.assertNotIn("Icons.Outlined.SportsEsports", header)
        self.assertIn('text = "BLINK"', CHROME)
        self.assertIn('contentDescription = "Open Items (includes Game)"', header)

    def test_account_summary_only_uses_real_values(self):
        panel = method_body(CHROME, "fun FeedReferenceStatsRow(", "fun FeedUtilityRow(")
        self.assertIn('worldRank > 0', panel)
        self.assertIn('coins.coerceAtLeast(0L)', panel)
        self.assertIn('onRankClick', panel)
        self.assertIn('onCoinsClick', panel)
        self.assertNotIn('1,250', panel)
        self.assertNotIn('#248', panel)

    def test_home_chrome_contains_no_stories_or_utility_strip(self):
        home = method_body(HOME, 'private fun PremiumHomeFeed(', 'private fun PremiumNewPostsPill(')
        self.assertIn('FeedReferenceStatsRow(', home)
        self.assertIn('FeedTopBar(', home)
        self.assertIn('FeedTabs(', home)
        self.assertIn('CreatePostFab(', home)
        self.assertNotIn('FeedUtilityRow(', home)
        self.assertNotIn('StoryBar(', home)

    def test_tabs_have_underlines_and_accessible_filter_action(self):
        tabs = method_body(CHROME, 'fun FeedTabs(', '/**\n * One stable create speed-dial')
        for text in ('For You', 'Following', 'RoundedCornerShape(2.dp)'):
            self.assertIn(text, tabs)
        self.assertNotIn('feed_refresh_action', tabs)
        self.assertIn('feed_filter_action', tabs)
        self.assertIn('onFilterClick', tabs)
        self.assertIn('filterActive', tabs)
        # A minimum height leaves the tab's fillMaxHeight child free to
        # consume the viewport and pushes every post offscreen.
        self.assertIn('height(58.dp)', tabs)
        self.assertNotIn('heightIn(min = 58.dp)', tabs)

    def test_posts_show_true_identity_and_reference_actions(self):
        self.assertIn('authorUniversity: String', POST)
        self.assertIn('Icons.Default.MoreVert', POST)
        self.assertIn('shouldApplyPostDoubleTapLike(post.isLiked)', POST)
        self.assertIn('internal fun shouldApplyPostDoubleTapLike', POST)
        a = POST.index('// Like remains visible for every post,')
        b = POST.index('} else {', a)
        footer = POST[a:b]
        order = ('Icons.Default.FavoriteBorder',
                 'Icons.Default.ChatBubbleOutline', 'Icons.Default.Repeat',
                 'Icons.Default.BookmarkBorder', 'Icons.Default.Share')
        starts = [footer.index(icon) for icon in order]
        self.assertEqual(starts, sorted(starts))
        self.assertNotIn('Icons.Default.Visibility', footer)
        self.assertIn('value = if (post.hideLikes) null else formatNumber(post.likes)', footer)
        self.assertIn('text = "${formatNumber(displayedViewsCount)} views"', footer)
        self.assertIn('maxLines = if (expandedText) Int.MAX_VALUE else 4', POST)
        self.assertIn('authorUniversity.trim().ifBlank', POST)
        self.assertIn('onOpenPost?.invoke()', POST)

    def test_feed_uses_one_post_view_pipeline_and_recovers_paging(self):
        home = method_body(HOME, 'private fun PremiumHomeFeed(', 'private fun PremiumNewPostsPill(')
        self.assertIn('trackExposure = isScreenActive', home)
        self.assertNotIn('impressionTracker.update(ids).forEach(latestViewed)', home)
        self.assertIn('paginationErrorMessage', home)
        self.assertIn('Retry loading posts', home)
        self.assertIn('Discover people', HOME)
        self.assertIn('followingPaginationErrorMessage', MAIN.replace('uiState.', ''))
        vm = (ROOT / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt').read_text()
        self.assertIn('isRefreshingFollowing = true', vm)
        self.assertIn('hasMoreFollowingPosts = fresh.size >= 30', vm)

    def test_filtered_feed_excludes_all_ads_and_repairs_boosted_poll_views(self):
        home = method_body(HOME, 'private fun PremiumHomeFeed(', 'private fun PremiumNewPostsPill(')
        self.assertIn('if (filter != PremiumFeedFilter.ALL) {', home)
        self.assertIn('filteredPosts.mapIndexed { index, post -> PremiumHomeRow.PostRow(post, index) }', home)
        self.assertIn('filteredAutoPageAttempts < 3', home)
        self.assertIn('listState.scrollToItem(0)', home)
        self.assertIn('locallyUnfollowedAuthorIds', home)
        boosted = method_body(home, 'is PremiumHomeRow.BoostedPostRow -> {', 'is PremiumHomeRow.MarketSponsoredRow -> {')
        self.assertIn('onVotePoll = onVotePoll', boosted)
        self.assertIn('trackExposure = isScreenActive', boosted)
        self.assertNotIn('trackExposure = false', boosted)

    def test_desktop_home_excludes_reels_and_reports_poll_metadata_errors(self):
        client = (ROOT / 'desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopSupabaseClient.kt').read_text()
        models = (ROOT / 'desktopApp/src/main/kotlin/com/blinkng/desktop/data/DesktopModels.kt').read_text()
        self.assertIn('else append("&is_reel=eq.false&video_url=is.null")', client)
        self.assertIn('pollMetadataAvailable = pollLookup.isSuccess', client)
        self.assertIn('pollMetadataAvailable: Boolean = true', models)
        self.assertIn('!post.isReel && post.videoUrl.isNullOrBlank()', DESKTOP)
        self.assertIn('pollMetadataUnavailable', DESKTOP)
        self.assertIn('autoFilterFetches < 3', DESKTOP)

    def test_phone_header_avoids_unnecessary_second_rail(self):
        header = method_body(CHROME, "fun FeedTopBar(", "private fun FeedBrandBlock(")
        # Short widths use compact icons and a compact wordmark, not a
        # mandatory second header row just because width is below 390dp.
        self.assertIn('val needsTwoRows = maxWidth < 316.dp', header)
        self.assertIn('if (needsTwoRows)', header)
        self.assertIn('compact = compactToolbar,', header)

    def test_profile_to_chat_keeps_reverse_navigation_history(self):
        # Direct Message must push the current profile route once, not first
        # close Profile and navigate to an intermediate Messages tab.
        direct_message = method_body(
            MAIN,
            "                    onDirectMessage = { partner ->",
            "                    onEndorseSkill = { skill ->"
        )
        self.assertIn("viewModel.openChatWithUser(partner", direct_message)
        self.assertNotIn("viewModel.closeProfile()", direct_message)
        self.assertNotIn("viewModel.setTab(MainTab.MESSAGES)", direct_message)
        self.assertIn("viewModel.navigateBackInHistory()", MAIN)

    def test_hidden_bottom_navigation_releases_feed_space(self):
        # MainActivity's scroll visibility must actually reach the feed's
        # viewport and FAB. Hiding the overlay alone leaves a phantom gap.
        self.assertIn('isBottomNavigationVisible = isBottomBarVisibleByScroll', MAIN)
        self.assertIn('isBottomNavigationVisible: Boolean = true', HOME)
        self.assertIn('bottom = bottomRailClearance', HOME)
        self.assertIn('bottom = createFabClearance', HOME)
        self.assertIn('if (isBottomNavigationVisible) 64.dp + bottomNavigationInset else bottomNavigationInset', HOME)
        self.assertNotIn('.padding(bottom = 64.dp + bottomNavigationInset)', HOME)
        self.assertNotIn('.padding(end = 14.dp, bottom = 144.dp + bottomNavigationInset)', HOME)

    def test_windows_feed_follows_social_action_and_views_hierarchy(self):
        start = DESKTOP.index("internal fun DesktopFeedPostCard(")
        footer = DESKTOP.index("// Five social actions first;", start)
        end = DESKTOP.index("} else {\n                val primaryMetrics", footer)
        contents = DESKTOP[footer:end]
        order = ('IconButton(onClick = onLike)', 'IconButton(onClick = onComments)',
                 'IconButton(onClick = onRepost)', 'IconButton(onClick = onBookmark)',
                 'TextButton(onClick = onCopyLink)')
        starts = [contents.index(icon) for icon in order]
        self.assertEqual(starts, sorted(starts))
        self.assertIn('"${BlinkPostFormat.count(displayedViewCount)} views"', contents)
        # The counter must use the delayed authoritative value rather than
        # immediately exposing a freshly recorded backend impression.
        self.assertIn('val displayedViewCount = desktopDisplayedViewCount(post.id, post.viewCount)', DESKTOP)
        self.assertIn('trackDesktopContentExposure(viewClient, post.id, displayedViewCount)', DESKTOP)
        self.assertIn('DESKTOP_VIEW_REFLECTION_DELAY_MS = 120_000L',
                      (ROOT / "desktopApp/src/main/kotlin/com/blinkng/desktop/ui/DesktopContentViewTracker.kt").read_text())
        self.assertIn('maxLines = if (expandedText) Int.MAX_VALUE else 4', DESKTOP)

    def test_foreground_feed_refresh_is_scoped_and_preserves_existing_posts(self):
        policy = (ROOT / "shared/src/main/kotlin/com/blinkng/shared/BlinkRefreshPolicy.kt").read_text()
        vm = (ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt").read_text()
        self.assertIn('SOCIAL_FEED_REFRESH_INTERVAL_MS: Long = 60_000L', policy)
        self.assertIn('minOf(maxAgeMillis, BlinkRefreshPolicy.SOCIAL_FEED_REFRESH_INTERVAL_MS)', vm)
        self.assertIn('visibleRefreshAt["$ownerKey:$refreshedScope"]', vm)
        self.assertIn('fun refreshFollowingIfStale()', vm)
        self.assertIn('reconcileFollowingChronologicalPage(', vm)
        self.assertIn('val evictedStaleRows =', vm)
        self.assertIn('followingRefreshRevision = if (evictedStaleRows)', vm)
        self.assertIn('followingPageEpoch++ // Invalidate', vm)
        self.assertIn('viewModel.refreshIfStale()', MAIN)
        self.assertIn('onFollowingVisible = { viewModel.refreshFollowingIfStale() }', MAIN)
        self.assertIn('if (feedLane == 1 && currentSubTab == 0 && isMainPageActive)', HOME)

    def test_five_navigation_destinations_and_unread_dot(self):
        nav = method_body(NAV, 'private val feedBottomItems = listOf(', '@Composable\nfun FeedBottomBar(')
        self.assertEqual(nav.count('FeedBottomItem('), 5)
        for tag in ('HOME', 'REELS', 'MARKET', 'CONNECT', 'MESSAGE'):
            self.assertIn(f'FeedBottomDestination.{tag}', nav)
        self.assertIn('hasUnreadMessages', NAV)
        self.assertIn('FeedBottomDestination.MESSAGE -> onMessageClick()', NAV)
        # The fifth slot is Message, and Menu stays a separately accessible header route.
        menu = (ROOT / "app/src/main/java/com/example/ui/screens/BlinkReferenceMenuScreen.kt").read_text()
        self.assertIn('onMessages: () -> Unit', menu)
        self.assertIn('"Messages & inbox" to onMessages', menu)
        self.assertIn('onMessages = {', MAIN)
        self.assertIn('onMessageClick = onMessages,', menu)
        self.assertIn('hasUnreadMessages = uiState.conversations.any', MAIN)
        search = (ROOT / 'app/src/main/java/com/example/ui/screens/BlinkReferenceSearchScreen.kt').read_text()
        inbox = (ROOT / 'app/src/main/java/com/example/ui/screens/BlinkReferenceInbox.kt').read_text()
        # Search and Inbox have a dedicated Games route rather than the
        # general Items hub; the Home header above still owns Items.
        self.assertIn('Icons.Rounded.SportsEsports, "Open games"', search)
        self.assertIn('Icons.Outlined.SportsEsports, contentDescription = "Open games"', inbox)

    def test_game_is_inside_items_and_admin_is_role_gated(self):
        self.assertIn('title = "Game"', ITEMS)
        self.assertIn('title = "Boost"', ITEMS)
        self.assertIn('title = "BLINK Giveaway"', ITEMS)
        self.assertIn('title = "Store"', ITEMS)
        self.assertIn('if (isAdmin)', ITEMS)
        self.assertIn('onOpenAdmin = {', MAIN)
        self.assertIn('onItemsClick = { feedUtilitySheet = "items" }', MAIN)
        self.assertIn('onOpenGame = { dismissUtility { feedUtilitySheet = "game" } }', MAIN)
        self.assertIn('title = "Update Center"', ITEMS)
        self.assertIn('Update available — tap to update', ITEMS)
        self.assertIn('No update now, check again later', ITEMS)
        self.assertIn('onCheckForWebsiteUpdate = onCheckForWebsiteUpdate', MAIN)
        self.assertIn('onOpenWebsiteUpdate = {', MAIN)
        popup = (ROOT / "app/src/main/java/com/example/ui/components/BlinkWebsiteUpdateDialog.kt").read_text()
        self.assertIn("fun BlinkUpdateMiniProgress(", popup)
        self.assertIn('Dialog(onDismissRequest = {})', popup)
        self.assertIn('BlinkUpdateMiniProgress(', MAIN)


if __name__ == "__main__":
    unittest.main()

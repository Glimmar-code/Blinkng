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
            'Icons.Outlined.Search', 'Icons.Outlined.SportsEsports',
            'Icons.Outlined.NotificationsNone', 'Icons.Outlined.Menu',
        ):
            self.assertIn(token, header)
        self.assertNotIn("Icons.Outlined.Security", header)
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

    def test_tabs_have_underlines_and_no_extra_visible_buttons(self):
        tabs = method_body(CHROME, 'fun FeedTabs(', '/**\n * One stable create speed-dial')
        for text in ('For You', 'Following', 'RoundedCornerShape(2.dp)'):
            self.assertIn(text, tabs)
        self.assertNotIn('feed_refresh_action', tabs)
        self.assertNotIn('feed_filter_action', tabs)

    def test_posts_show_true_identity_and_reference_actions(self):
        self.assertIn('authorUniversity: String', POST)
        self.assertIn('Icons.Default.MoreVert', POST)
        self.assertIn('onDoubleTapLike = if (xFeedStyle) onLike else null', POST)
        a = POST.index('// Five-action reference rail;')
        b = POST.index('if (displayImages.isEmpty())', a)
        footer = POST[a:b]
        order = ('Icons.Default.Visibility', 'Icons.Default.Repeat',
                 'Icons.Default.ChatBubbleOutline', 'Icons.Default.Bookmark',
                 'Icons.Default.Share')
        starts = [footer.index(icon) for icon in order]
        self.assertEqual(starts, sorted(starts))

    def test_five_navigation_destinations_and_unread_dot(self):
        nav = method_body(NAV, 'private val feedBottomItems = listOf(', '@Composable\nfun FeedBottomBar(')
        self.assertEqual(nav.count('FeedBottomItem('), 5)
        for tag in ('HOME', 'REELS', 'MARKET', 'CONNECT', 'MESSAGE'):
            self.assertIn(f'FeedBottomDestination.{tag}', nav)
        self.assertIn('hasUnreadMessages', NAV)

    def test_game_is_inside_items_and_admin_is_role_gated(self):
        self.assertIn('title = "Game"', ITEMS)
        self.assertIn('title = "Boost"', ITEMS)
        self.assertIn('title = "Drops"', ITEMS)
        self.assertIn('title = "Store"', ITEMS)
        self.assertIn('if (isAdmin)', ITEMS)
        self.assertIn('onOpenAdmin = {', MAIN)
        self.assertIn('onItemsClick = { feedUtilitySheet = "items" }', MAIN)
        self.assertIn('onOpenGame = { dismissUtility { feedUtilitySheet = "game" } }', MAIN)


if __name__ == "__main__":
    unittest.main()

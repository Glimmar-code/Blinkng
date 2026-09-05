from pathlib import Path


def require_replace(text: str, old: str, new: str, label: str, *, replace_all: bool = False) -> str:
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"Could not find expected source block for {label}")
    return text.replace(old, new) if replace_all else text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Feed post text selection + own-post repost visibility
# -----------------------------------------------------------------------------
post_card_path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
post_card = post_card_path.read_text(encoding="utf-8")

selection_import = "import androidx.compose.foundation.text.selection.SelectionContainer\n"
if selection_import not in post_card:
    import_anchor = "import androidx.compose.foundation.shape.RoundedCornerShape\n"
    if import_anchor not in post_card:
        raise RuntimeError("Could not find PostCard import anchor")
    post_card = post_card.replace(import_anchor, import_anchor + selection_import, 1)

old_post_text = '''            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    color = FeedTextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    maxLines = if (expandedText) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis
                )
'''
new_post_text = '''            if (post.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = post.text,
                        color = FeedTextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = if (expandedText) Int.MAX_VALUE else 7,
                        overflow = TextOverflow.Ellipsis
                    )
                }
'''
post_card = require_replace(post_card, old_post_text, new_post_text, "selectable post text")

old_repost_action = '''                PremiumPostAction(
                    icon = Icons.Default.Repeat,
                    value = formatNumber(post.repostsCount),
                    tint = repostTint,
                    description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                    onClick = onRepost
                )
'''
new_repost_action = '''                if (!isAuthor) {
                    PremiumPostAction(
                        icon = Icons.Default.Repeat,
                        value = formatNumber(post.repostsCount),
                        tint = repostTint,
                        description = if (post.isRepostedByMe) "Undo repost" else "Repost",
                        onClick = onRepost
                    )
                }
'''
post_card = require_replace(post_card, old_repost_action, new_repost_action, "hide repost on own posts")
post_card_path.write_text(post_card, encoding="utf-8")


# Make own-post detection resilient when FeedPost.author is a display name but
# FeedPost.authorUsername carries the actual username.
premium_feed_path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
premium_feed = premium_feed_path.read_text(encoding="utf-8")
old_author_check = "isAuthor = post.author.equals(currentUsername, ignoreCase = true),"
new_author_check = '''isAuthor = post.author.equals(currentUsername.removePrefix("@"), ignoreCase = true) ||
                                                    post.authorUsername.removePrefix("@").equals(currentUsername.removePrefix("@"), ignoreCase = true),'''
premium_feed = require_replace(
    premium_feed,
    old_author_check,
    new_author_check,
    "premium feed own-post detection",
    replace_all=True,
)
premium_feed_path.write_text(premium_feed, encoding="utf-8")


# -----------------------------------------------------------------------------
# Reels action rail: anchor to the lower-right, rising only toward mid-screen.
# -----------------------------------------------------------------------------
reels_path = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
reels = reels_path.read_text(encoding="utf-8")

old_skeleton_rail = '''        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp, bottom = 46.dp),
'''
new_skeleton_rail = '''        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 34.dp),
'''
reels = require_replace(reels, old_skeleton_rail, new_skeleton_rail, "reels skeleton action rail")

old_live_rail = '''        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 64.dp)
                .entranceEffect(delayMillis = 60),
'''
new_live_rail = '''        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 34.dp)
                .entranceEffect(delayMillis = 60),
'''
reels = require_replace(reels, old_live_rail, new_live_rail, "reels live action rail")
reels_path.write_text(reels, encoding="utf-8")


# -----------------------------------------------------------------------------
# App-level bottom navigation: never overlay the full-screen Reels surface.
# Reels is HOME sub-tab 1.
# -----------------------------------------------------------------------------
main_path = Path("app/src/main/java/com/example/MainActivity.kt")
main = main_path.read_text(encoding="utf-8")
old_bottom_bar_condition = '''                uiState.deepLinkedPost == null &&
                !uiState.isMenuOpen &&
                isBottomBarVisibleByScroll
'''
new_bottom_bar_condition = '''                uiState.deepLinkedPost == null &&
                !uiState.isMenuOpen &&
                !(uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 1) &&
                isBottomBarVisibleByScroll
'''
main = require_replace(main, old_bottom_bar_condition, new_bottom_bar_condition, "hide bottom navigation on reels")
main_path.write_text(main, encoding="utf-8")

print("Applied feed/reels interaction polish successfully")

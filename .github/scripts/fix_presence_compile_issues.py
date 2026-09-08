from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# PostCard uses Modifier.border for the presence badge.
post_path = Path("app/src/main/java/com/example/ui/components/PostCard.kt")
post = post_path.read_text()
if "import androidx.compose.foundation.border\n" not in post:
    post = replace_once(
        post,
        "import androidx.compose.foundation.background\n",
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n",
        "PostCard border import",
    )
post_path.write_text(post)

# ProfessionalSearchScreen uses Color for the offline brown dot.
search_path = Path("app/src/main/java/com/example/ui/screens/ProfessionalSearchScreen.kt")
search = search_path.read_text()
if "import androidx.compose.ui.graphics.Color\n" not in search:
    search = replace_once(
        search,
        "import androidx.compose.ui.graphics.graphicsLayer\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\n",
        "ProfessionalSearch Color import",
    )
search_path.write_text(search)

# The primary patcher intentionally stops before writing its PremiumHomeFeed
# in-memory edits when its context marker is ambiguous. The scoped continuation
# adds the map, while this step restores the matching data parameter + call.
feed_path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
feed = feed_path.read_text()
if "            profiles = profiles,\n            currentUsername = currentUsername," not in feed:
    feed = replace_once(
        feed,
        """        0 -> PremiumHomeFeed(\n            posts = posts,\n            reels = reels,\n            currentUsername = currentUsername,\n""",
        """        0 -> PremiumHomeFeed(\n            posts = posts,\n            reels = reels,\n            profiles = profiles,\n            currentUsername = currentUsername,\n""",
        "PremiumHomeFeed profiles call",
    )
if "    profiles: List<UserProfile>,\n    currentUsername: String," not in feed:
    feed = replace_once(
        feed,
        """private fun PremiumHomeFeed(\n    posts: List<FeedPost>,\n    reels: List<FeedPost>,\n    currentUsername: String,\n""",
        """private fun PremiumHomeFeed(\n    posts: List<FeedPost>,\n    reels: List<FeedPost>,\n    profiles: List<UserProfile>,\n    currentUsername: String,\n""",
        "PremiumHomeFeed profiles signature",
    )
feed_path.write_text(feed)

# Fail closed if any of the exact compile fixes are absent.
required = {
    "PostCard border import": "import androidx.compose.foundation.border\n" in post,
    "ProfessionalSearch Color import": "import androidx.compose.ui.graphics.Color\n" in search,
    "PremiumHomeFeed profiles call": "            profiles = profiles,\n            currentUsername = currentUsername," in feed,
    "PremiumHomeFeed profiles signature": "    profiles: List<UserProfile>,\n    currentUsername: String," in feed,
}
missing = [name for name, ok in required.items() if not ok]
if missing:
    raise RuntimeError("presence compile hotfix incomplete: " + ", ".join(missing))

from pathlib import Path
import runpy

# Apply the main feature patch first.
runpy.run_path(".github/scripts/apply_inline_reel_feed_previews.py", run_name="__main__")

# The public FeedScreen and private LegacyFeedScreen originally shared the same
# trailing parameter block. The v1 idempotency guard correctly patched the first
# occurrence but mistook that for both being patched. Finish any remaining
# wrapper signature here before compiling.
feed_path = Path("app/src/main/java/com/example/ui/screens/FeedScreen.kt")
feed = feed_path.read_text(encoding="utf-8")
old = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
'''
new = '''    onLoadMoreReels: () -> Unit = {},
    homeReselectSignal: Int = 0,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    initialReelId: String? = null,
    initialReelPositionMs: Long = 0L
) {
'''

if old in feed:
    feed = feed.replace(old, new)

if old in feed:
    raise RuntimeError("A FeedScreen wrapper signature is still missing reel launch parameters")

feed_path.write_text(feed, encoding="utf-8")
print("Applied inline reel feed previews v2 wrapper fix successfully")

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

# Inline reels are genuine feed exposures, so they must use the exact same shared
# post/reel view pipeline as every other surface. The tracker already de-duplicates
# continuous visibility and records again only after the content leaves qualified
# visibility and later re-enters. The coordinator keeps the visible count suppressed
# for the existing 30-second reflection window.
reels_path = Path("app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt")
reels = reels_path.read_text(encoding="utf-8")

old_doc = ''' * seconds of actual video playback, then exposes a play button that opens the
 * full Reels surface at the current playback position. No content-exposure
 * modifier is attached here, so the teaser cannot create a view by itself.
 */
'''
new_doc = ''' * seconds of actual video playback, then exposes a play button that opens the
 * full Reels surface at the current playback position. The card uses the same
 * qualified-visibility exposure tracker as the rest of the app.
 */
'''
if old_doc in reels:
    reels = reels.replace(old_doc, new_doc, 1)

old_state = '''    var previewFinished by remember(reel.id) { mutableStateOf(false) }
    var previewPositionMs by remember(reel.id) { mutableStateOf(0L) }
    var isBuffering by remember(reel.id) { mutableStateOf(false) }

    LaunchedEffect(isActive, reel.id) {
'''
new_state = '''    var previewFinished by remember(reel.id) { mutableStateOf(false) }
    var previewPositionMs by remember(reel.id) { mutableStateOf(0L) }
    var isBuffering by remember(reel.id) { mutableStateOf(false) }
    val displayedViewsCount = rememberDelayedContentViewCount(reel.id, reel.viewsCount)

    LaunchedEffect(isActive, reel.id) {
'''
if new_state not in reels:
    if old_state not in reels:
        raise RuntimeError("Could not find inline reel preview state block")
    reels = reels.replace(old_state, new_state, 1)

old_surface = '''        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f),
'''
new_surface = '''        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .trackContentExposure(reel.id, displayedViewsCount),
'''
if new_surface not in reels:
    if old_surface not in reels:
        raise RuntimeError("Could not find inline reel preview surface modifier")
    reels = reels.replace(old_surface, new_surface, 1)

reels_path.write_text(reels, encoding="utf-8")

premium_path = Path("app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt")
premium = premium_path.read_text(encoding="utf-8")
premium = premium.replace(
    '''            // reel page, and inline autoplay itself never emits a view event.
''',
    '''            // reel page. View events are still handled by the shared exposure tracker.
''',
    1,
)
premium = premium.replace(
    '''    // Inline reels use visibility only to control their two-second muted teaser. They are
    // intentionally excluded from PostImpressionTracker/trackContentExposure, so autoplay
    // cannot create fake views or alter the existing ranking/view-weight system.
''',
    '''    // This list-level visibility state only controls two-second muted autoplay. The
    // preview card itself uses the same qualified exposure tracker as full reels, so genuine
    // feed encounters follow the existing repeat-view and delayed-reflection algorithm.
''',
    1,
)
premium_path.write_text(premium, encoding="utf-8")

print("Applied inline reel feed previews v2 + shared view tracking successfully")
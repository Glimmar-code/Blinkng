from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched {label}")


main_activity = ROOT / "app/src/main/java/com/example/MainActivity.kt"
old_home_click = '''                onHomeClick = {
                    isBottomBarVisibleByScroll = true
                    if (uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 0) {
                        homeReselectSignal++
                    } else {
                        viewModel.setTab(MainTab.HOME)
                        viewModel.setFeedSubTab(0)
                    }
                },'''
new_home_click = '''                onHomeClick = {
                    isBottomBarVisibleByScroll = true

                    // A Home reselect is only valid when the user is actually looking at
                    // the main Home feed. Profiles, comments, menus, chats, reels/game,
                    // and every other surface must navigate Home without refreshing it.
                    val isHomeFeedSurface =
                        uiState.selectedTab == MainTab.HOME &&
                            uiState.feedSubTab == 0 &&
                            uiState.viewingProduct == null &&
                            uiState.viewingProfile == null &&
                            !uiState.isPostItemOpen &&
                            !uiState.isBecomeSellerOpen &&
                            !uiState.isEditProfileOpen &&
                            !uiState.isMenuOpen &&
                            uiState.activeConversationPartner == null &&
                            !uiState.isConversationFullScreen &&
                            uiState.activePostOptionsPost == null &&
                            uiState.activeCommentsPostId == null &&
                            !uiState.isActivityOpen &&
                            !uiState.isGetVerifiedOpen &&
                            !uiState.isCreatePostOpen &&
                            !uiState.isCreateStoryOpen &&
                            uiState.activeViewingStory == null &&
                            !uiState.showSellerCongratulationsDialog &&
                            uiState.deepLinkedPost == null

                    if (isHomeFeedSurface) {
                        homeReselectSignal++
                    } else {
                        // Bottom-nav Home behaves as navigation, not as a hidden refresh.
                        // Close any transient surface that can sit above a main tab first.
                        if (uiState.deepLinkedPost != null) viewModel.closeDeepLinkedPost()
                        if (uiState.activePostOptionsPost != null) viewModel.openPostOptions(null)
                        if (uiState.activeCommentsPostId != null) viewModel.openCommentsForPost(null)
                        if (uiState.isMenuOpen) viewModel.openMenu(false)
                        if (uiState.isEditProfileOpen) viewModel.openEditProfile(false)
                        if (uiState.isBecomeSellerOpen) viewModel.openBecomeSeller(false)
                        if (uiState.isPostItemOpen) viewModel.openPostItem(false)
                        if (uiState.isActivityOpen) viewModel.openActivity(false)
                        if (uiState.activeConversationPartner != null) viewModel.closeConversation()
                        if (uiState.viewingProfile != null) viewModel.closeProfile()
                        if (uiState.viewingProduct != null) viewModel.closeProductDetail()
                        if (uiState.isGetVerifiedOpen) viewModel.openGetVerified(false)
                        if (uiState.isCreatePostOpen) viewModel.openCreatePost(false)
                        if (uiState.isCreateStoryOpen) viewModel.openCreateStory(false)
                        if (uiState.activeViewingStory != null) viewModel.closeStory()
                        if (uiState.showSellerCongratulationsDialog) viewModel.dismissSellerCongratulations()

                        viewModel.setTab(MainTab.HOME)
                        viewModel.setFeedSubTab(0)
                    }
                },'''
replace_once(main_activity, old_home_click, new_home_click, "bottom-nav Home behavior")


premium_feed = ROOT / "app/src/main/java/com/example/ui/screens/PremiumFeedScreen.kt"
old_reselect = '''        if (homeReselectSignal > 0) {
            onLaneChanged(0)
            filter = PremiumFeedFilter.ALL
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                // Avoid animating through hundreds of composed rows when the user is deep
                // in the feed. Jump near the top, then animate only the final short distance.
                if (listState.firstVisibleItemIndex > 8) listState.scrollToItem(8)
                listState.animateScrollToItem(0)
            } else {
                onRefresh()
            }
            scrollAccumulator[0] = 0f
            bottomChromeVisible = true
            fabExpanded = true
            onBottomBarVisibilityChange(true)
        }'''
new_reselect = '''        if (homeReselectSignal > 0) {
            // Home-on-Home is intentionally lightweight:
            // - about ten posts deep or farther: return to the first post instantly;
            // - still within the first ten posts: keep position and refresh in place.
            // Do not reset the user's For You/Following lane or active feed filter.
            if (listState.firstVisibleItemIndex >= 10) {
                listState.scrollToItem(0)
            } else {
                onRefresh()
            }
            scrollAccumulator[0] = 0f
            bottomChromeVisible = true
            fabExpanded = true
            onBottomBarVisibilityChange(true)
        }'''
replace_once(premium_feed, old_reselect, new_reselect, "Home reselect threshold")


premium_loading = ROOT / "app/src/main/java/com/example/ui/components/PremiumLoading.kt"
loading_text = premium_loading.read_text(encoding="utf-8")
indicator_pattern = re.compile(
    r'@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun PremiumPullRefreshIndicator\([\s\S]*?\n}\n\Z'
)
new_indicator = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun PremiumPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    darkSurface: Boolean = false,
    refreshingLabel: String = "Updating your feed"
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    val visualProgress = if (isRefreshing) 1f else progress
    val refreshThresholdPx = with(LocalDensity.current) { 54.dp.toPx() }

    // Keep refresh visually quiet and familiar: a single Chrome-style rolling circle.
    // No text pill, shimmer bar, or long entrance/exit animation.
    AnimatedVisibility(
        visible = isRefreshing || progress > 0.04f,
        modifier = modifier,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(80))
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .graphicsLayer {
                    translationY = (visualProgress * refreshThresholdPx) - size.height
                    alpha = visualProgress.coerceIn(0.18f, 1f)
                    val scale = 0.9f + (visualProgress * 0.1f)
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp),
                color = if (darkSurface) Color.White else MaterialTheme.colorScheme.primary,
                strokeWidth = 2.2.dp
            )
        }
    }
}
'''
loading_text, replacements = indicator_pattern.subn(new_indicator, loading_text, count=1)
if replacements != 1:
    raise SystemExit(f"refresh indicator: expected exactly one function match, found {replacements}")
premium_loading.write_text(loading_text, encoding="utf-8")
print("patched compact circular refresh indicator")

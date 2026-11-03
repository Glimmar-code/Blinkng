from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
activity = ROOT / "app/src/main/java/com/example/MainActivity.kt"
s = activity.read_text()

# Backfill these rules only on older source. The current app owns the complete
# BackHandler/modal list (including Create Story), so this script must be idempotent.
if "uiState.isGetVerifiedOpen ||" not in s:
    s = s.replace(
        """                uiState.activePostOptionsPost != null ||
                uiState.isActivityOpen
""",
        """                uiState.activePostOptionsPost != null ||
                uiState.isActivityOpen ||
                uiState.isGetVerifiedOpen ||
                uiState.isCreatePostOpen ||
                uiState.activeViewingStory != null ||
                uiState.showSellerCongratulationsDialog
""",
        1,
    )

if "uiState.isGetVerifiedOpen -> viewModel.openGetVerified(false)" not in s:
    s = s.replace(
        """            uiState.activeConversationPartner != null -> viewModel.closeConversation()
            uiState.viewingProfile != null -> viewModel.closeProfile()
            uiState.viewingProduct != null -> viewModel.closeProductDetail()
""",
        """            uiState.activeConversationPartner != null -> viewModel.closeConversation()
            uiState.viewingProfile != null -> viewModel.closeProfile()
            uiState.viewingProduct != null -> viewModel.closeProductDetail()
            uiState.isGetVerifiedOpen -> viewModel.openGetVerified(false)
            uiState.isCreatePostOpen -> viewModel.openCreatePost(false)
            uiState.activeViewingStory != null -> viewModel.closeStory()
            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()
""",
        1,
    )

if "!uiState.isGetVerifiedOpen &&" not in s:
    s = s.replace(
        """                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                isBottomBarVisibleByScroll
""",
        """                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                !uiState.isGetVerifiedOpen &&
                !uiState.isCreatePostOpen &&
                uiState.activeViewingStory == null &&
                !uiState.showSellerCongratulationsDialog &&
                uiState.activePostOptionsPost == null &&
                uiState.activeCommentsPostId == null &&
                !uiState.isMenuOpen &&
                isBottomBarVisibleByScroll
""",
        1,
    )

activity.write_text(s)
print("UI/UX fixes checked.")

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
activity = ROOT / "app/src/main/java/com/example/MainActivity.kt"
s = activity.read_text()

# Android back should dismiss every transient surface before leaving the main screen.
s = s.replace(
'''                uiState.activePostOptionsPost != null ||
                uiState.isActivityOpen
''',
'''                uiState.activePostOptionsPost != null ||
                uiState.isActivityOpen ||
                uiState.isGetVerifiedOpen ||
                uiState.isCreatePostOpen ||
                uiState.activeViewingStory != null ||
                uiState.showSellerCongratulationsDialog
''',
1)

s = s.replace(
'''            uiState.activeConversationPartner != null -> viewModel.closeConversation()
            uiState.viewingProfile != null -> viewModel.closeProfile()
            uiState.viewingProduct != null -> viewModel.closeProductDetail()
''',
'''            uiState.activeConversationPartner != null -> viewModel.closeConversation()
            uiState.viewingProfile != null -> viewModel.closeProfile()
            uiState.viewingProduct != null -> viewModel.closeProductDetail()
            uiState.isGetVerifiedOpen -> viewModel.openGetVerified(false)
            uiState.isCreatePostOpen -> viewModel.openCreatePost(false)
            uiState.activeViewingStory != null -> viewModel.closeStory()
            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()
''',
1)

# Do not leave the persistent bottom navigation visible behind a modal/sheet.
s = s.replace(
'''                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                isBottomBarVisibleByScroll
''',
'''                !uiState.isConversationFullScreen &&
                !uiState.isActivityOpen &&
                !uiState.isGetVerifiedOpen &&
                !uiState.isCreatePostOpen &&
                uiState.activeViewingStory == null &&
                !uiState.showSellerCongratulationsDialog &&
                uiState.activePostOptionsPost == null &&
                uiState.activeCommentsPostId == null &&
                !uiState.isMenuOpen &&
                isBottomBarVisibleByScroll
''',
1)

activity.write_text(s)
print("UI/UX fixes applied.")

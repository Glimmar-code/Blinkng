from pathlib import Path

path = Path("app/src/main/java/com/example/MainActivity.kt")
text = path.read_text()

old_enabled = """                uiState.showSellerCongratulationsDialog ||\n                uiState.deepLinkedPost != null\n"""
new_enabled = """                uiState.showSellerCongratulationsDialog ||\n                uiState.deepLinkedPost != null ||\n                (uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 1)\n"""

old_when_tail = """            uiState.activeViewingStory != null -> viewModel.closeStory()\n            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()\n"""
new_when_tail = """            uiState.activeViewingStory != null -> viewModel.closeStory()\n            uiState.showSellerCongratulationsDialog -> viewModel.dismissSellerCongratulations()\n            uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 1 -> {\n                isBottomBarVisibleByScroll = true\n                viewModel.setFeedSubTab(0)\n            }\n"""

if old_enabled not in text:
    if new_enabled not in text:
        raise SystemExit("BackHandler enabled block not found")
else:
    text = text.replace(old_enabled, new_enabled, 1)

if old_when_tail not in text:
    if new_when_tail not in text:
        raise SystemExit("BackHandler action block not found")
else:
    text = text.replace(old_when_tail, new_when_tail, 1)

path.write_text(text)
print("Applied Reels back-to-Feed navigation fix.")

from pathlib import Path

main_path = Path("app/src/main/java/com/example/MainActivity.kt")
text = main_path.read_text(encoding="utf-8")

home_old = """                MainTab.HOME -> {\n                    FeedScreen(\n"""
home_new = """                MainTab.HOME -> {\n                    PremiumFeedScreen(\n"""
if home_old not in text:
    raise SystemExit("Expected HOME FeedScreen call was not found")
text = text.replace(home_old, home_new, 1)

message_anchor = """                        onDirectMessage = { partner, partnerName, partnerAvatar ->\n                            viewModel.openChatWithUser(partner, partnerName, partnerAvatar)\n                        },\n                        hasMorePosts = uiState.hasMorePosts,\n"""
message_replacement = """                        onDirectMessage = { partner, partnerName, partnerAvatar ->\n                            viewModel.openChatWithUser(partner, partnerName, partnerAvatar)\n                        },\n                        onSearchClick = { viewModel.setTab(MainTab.SEARCH) },\n                        onLeaderboardClick = { viewModel.setTab(MainTab.LEADERBOARD) },\n                        onMarketClick = { viewModel.setTab(MainTab.MARKET) },\n                        onMessageClick = { viewModel.setTab(MainTab.MESSAGES) },\n                        hasUnreadNotifications = uiState.activities.any { it.isUnread },\n                        hasMorePosts = uiState.hasMorePosts,\n"""
if message_anchor not in text:
    raise SystemExit("Expected FeedScreen callback anchor was not found")
text = text.replace(message_anchor, message_replacement, 1)

bottom_old = """            FloatingBottomBar(\n                currentTab = uiState.selectedTab,\n                onTabSelected = { tab ->\n                    isBottomBarVisibleByScroll = true\n                    if (tab == MainTab.HOME && uiState.selectedTab == MainTab.HOME) {\n                        viewModel.setFeedSubTab(0)\n                        homeReselectSignal++\n                    } else {\n                        viewModel.setTab(tab)\n                    }\n                },\n                isDark = uiState.isDarkMode\n            )\n"""
bottom_new = """            FeedBottomBar(\n                currentTab = uiState.selectedTab,\n                feedSubTab = uiState.feedSubTab,\n                onHomeClick = {\n                    isBottomBarVisibleByScroll = true\n                    if (uiState.selectedTab == MainTab.HOME && uiState.feedSubTab == 0) {\n                        homeReselectSignal++\n                    } else {\n                        viewModel.setTab(MainTab.HOME)\n                        viewModel.setFeedSubTab(0)\n                    }\n                },\n                onConnectClick = {\n                    isBottomBarVisibleByScroll = true\n                    viewModel.setTab(MainTab.HOME)\n                    viewModel.setFeedSubTab(2)\n                },\n                onLeaderboardClick = {\n                    isBottomBarVisibleByScroll = true\n                    viewModel.setTab(MainTab.LEADERBOARD)\n                },\n                onMarketClick = {\n                    isBottomBarVisibleByScroll = true\n                    viewModel.setTab(MainTab.MARKET)\n                },\n                onMessageClick = {\n                    isBottomBarVisibleByScroll = true\n                    viewModel.setTab(MainTab.MESSAGES)\n                },\n                isDark = uiState.isDarkMode\n            )\n"""
if bottom_old not in text:
    raise SystemExit("Expected FloatingBottomBar block was not found")
text = text.replace(bottom_old, bottom_new, 1)

main_path.write_text(text, encoding="utf-8")
print("Premium feed wiring applied to MainActivity.kt")

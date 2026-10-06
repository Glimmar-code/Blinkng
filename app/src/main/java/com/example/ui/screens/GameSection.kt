package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.LeaderboardUser

@Composable
fun GameSection(
    userAvatar: String,
    leaderboardUsers: List<LeaderboardUser> = emptyList(),
    connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    connectHubActions: ConnectHubActions = ConnectHubActions(),
    isDark: Boolean,
    onOpenMenu: () -> Unit,
    onOpenActivity: () -> Unit,
    onProfileClick: (String) -> Unit,
    selectedTopTab: Int,
    onHomeClick: () -> Unit,
    onReelClick: () -> Unit,
    onConnectClick: () -> Unit,
    onGameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GeneralStudyGameSection(
        userAvatar = userAvatar,
        onOpenMenu = onOpenMenu,
        onOpenActivity = onOpenActivity,
        onProfileClick = onProfileClick,
        selectedTopTab = selectedTopTab,
        onHomeClick = onHomeClick,
        onReelClick = onReelClick,
        onConnectClick = onConnectClick,
        onGameClick = onGameClick,
        modifier = modifier,
    )
}

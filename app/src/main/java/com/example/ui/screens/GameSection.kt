package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.game.BlinkLifeLaunchGate
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
    onRoundActiveChange: (Boolean) -> Unit = {},

) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Button(onClick = { BlinkLifeLaunchGate.play(context) }) {
                Text("Play BLINK LIFE 3D (Beta)")
            }
        }
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
        modifier = Modifier.weight(1f),
        onRoundActiveChange = onRoundActiveChange,
        )
    }
}

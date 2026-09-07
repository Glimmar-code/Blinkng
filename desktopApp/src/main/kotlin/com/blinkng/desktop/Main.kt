package com.blinkng.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blinkng.desktop.ui.AdminScreen
import com.blinkng.desktop.ui.BlinkAuthScreen
import com.blinkng.desktop.ui.ConnectScreen
import com.blinkng.desktop.ui.GamesScreen
import com.blinkng.desktop.ui.HomeScreen
import com.blinkng.desktop.ui.LeaderboardScreen
import com.blinkng.desktop.ui.MarketplaceScreen
import com.blinkng.desktop.ui.MessagesScreen
import com.blinkng.desktop.ui.NotificationsScreen
import com.blinkng.desktop.ui.ProfileScreen
import com.blinkng.desktop.ui.ReelsScreen
import com.blinkng.desktop.ui.SearchScreen
import com.blinkng.desktop.ui.SettingsScreen
import com.blinkng.desktop.ui.StoreScreen
import java.awt.Dimension

private val BlinkPurple = Color(0xFF6D3DF5)
private val BlinkBackground = Color(0xFF0B0B0F)
private val BlinkSurface = Color(0xFF121218)
private val BlinkSurfaceRaised = Color(0xFF191920)

private val BlinkDarkColors = darkColorScheme(
    primary = BlinkPurple,
    background = BlinkBackground,
    surface = BlinkSurface,
    surfaceVariant = BlinkSurfaceRaised,
)

private val BlinkLightColors = lightColorScheme(
    primary = BlinkPurple,
)

private data class BlinkDestination(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
)

private val baseDestinations = listOf(
    BlinkDestination("home", "Home", Icons.Rounded.Home),
    BlinkDestination("reels", "Reels", Icons.Rounded.VideoLibrary),
    BlinkDestination("connect", "Connect", Icons.Rounded.Groups),
    BlinkDestination("messages", "Messages", Icons.Rounded.Chat),
    BlinkDestination("marketplace", "Marketplace", Icons.Rounded.Storefront),
    BlinkDestination("games", "Games", Icons.Rounded.SportsEsports),
    BlinkDestination("notifications", "Notifications", Icons.Rounded.Notifications),
    BlinkDestination("store", "Blink Store", Icons.Rounded.ShoppingBag),
    BlinkDestination("leaderboard", "Leaderboard", Icons.Rounded.Leaderboard),
    BlinkDestination("profile", "Profile", Icons.Rounded.Person),
    BlinkDestination("admin", "Admin", Icons.Rounded.AdminPanelSettings, adminOnly = true),
    BlinkDestination("settings", "Settings", Icons.Rounded.Settings),
)

fun main() = application {
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
    val appState = remember { DesktopAppState() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Blinkng",
        state = windowState,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(960, 640)
            appState.initialize()
        }

        val theme = appState.settings?.theme?.lowercase()
        val colors = if (theme == "light") BlinkLightColors else BlinkDarkColors
        MaterialTheme(colorScheme = colors) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !appState.initialized -> InitializingScreen()
                    appState.session == null -> BlinkAuthScreen(appState)
                    else -> AuthenticatedShell(appState)
                }
            }
        }
    }
}

@Composable
private fun InitializingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BLINKNG", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text("Restoring your secure session…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuthenticatedShell(state: DesktopAppState) {
    val visibleDestinations = remember(state.adminCapability.allowed) {
        baseDestinations.filter { !it.adminOnly || state.adminCapability.allowed }
    }
    if (state.selectedRoute == "admin" && !state.adminCapability.allowed) state.selectedRoute = "home"

    Column(modifier = Modifier.fillMaxSize()) {
        BlinkTopBar(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1180.dp
            val showRightPanel = maxWidth >= 1420.dp && state.selectedRoute !in setOf("messages", "search")
            Row(modifier = Modifier.fillMaxSize()) {
                BlinkSidebar(
                    compact = compact,
                    destinations = visibleDestinations,
                    selectedId = state.selectedRoute,
                    onSelect = { state.selectedRoute = it },
                )
                HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (state.selectedRoute) {
                        "home" -> HomeScreen(state)
                        "reels" -> ReelsScreen(state)
                        "connect" -> ConnectScreen(state)
                        "messages" -> MessagesScreen(state)
                        "marketplace" -> MarketplaceScreen(state)
                        "games" -> GamesScreen(state)
                        "notifications" -> NotificationsScreen(state)
                        "store" -> StoreScreen(state)
                        "leaderboard" -> LeaderboardScreen(state)
                        "profile" -> ProfileScreen(state)
                        "admin" -> AdminScreen(state)
                        "settings" -> SettingsScreen(state)
                        "search" -> SearchScreen(state)
                        else -> HomeScreen(state)
                    }
                }
                if (showRightPanel) {
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    BlinkRightPanel(state)
                }
            }
        }
    }
}

@Composable
private fun BlinkTopBar(state: DesktopAppState) {
    var search by remember(state.globalSearch) { mutableStateOf(state.globalSearch) }
    val profile = state.profile
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.width(210.dp).clickable { state.selectedRoute = "home" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(BlinkPurple),
                contentAlignment = Alignment.Center,
            ) {
                Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
            }
            Text("BLINKNG", fontWeight = FontWeight.Black, fontSize = 20.sp)
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search people and posts") },
            shape = RoundedCornerShape(16.dp),
        )
        IconButton(
            onClick = {
                state.globalSearch = search.trim()
                state.selectedRoute = "search"
            },
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search")
        }
        IconButton(onClick = { state.selectedRoute = "notifications" }) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { state.selectedRoute = "profile" }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile?.fullName ?: "Blink account", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    if (profile?.isVerified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Text("@${profile?.username ?: "user"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BlinkSidebar(
    compact: Boolean,
    destinations: List<BlinkDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val width = if (compact) 88.dp else 248.dp
    LazyColumn(
        modifier = Modifier.width(width).fillMaxHeight().padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(destinations, key = { it.id }) { destination ->
            NavigationDrawerItem(
                label = {
                    if (!compact) Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                selected = selectedId == destination.id,
                onClick = { onSelect(destination.id) },
                icon = { Icon(destination.icon, contentDescription = destination.title) },
                shape = RoundedCornerShape(14.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = BlinkPurple.copy(alpha = 0.18f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun BlinkRightPanel(state: DesktopAppState) {
    val profile = state.profile
    Column(
        modifier = Modifier.width(300.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Blink", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            profile?.university ?: "Blinkng",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("${profile?.followerCount ?: 0} followers", fontWeight = FontWeight.SemiBold)
        Text("${profile?.followingCount ?: 0} following", fontWeight = FontWeight.SemiBold)
        Text("${profile?.coinBalance ?: 0} Blink Coins", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Desktop", fontWeight = FontWeight.Bold)
        Text(
            "This Windows client is connected to the same Blinkng account and production Supabase backend as Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

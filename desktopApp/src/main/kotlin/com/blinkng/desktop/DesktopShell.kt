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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberNotification
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.blinkng.desktop.data.DesktopCall
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.desktop.ui.AdminProScreen
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
import com.blinkng.desktop.ui.StoreProScreen
import kotlinx.coroutines.delay
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
private val BlinkLightColors = lightColorScheme(primary = BlinkPurple)

private data class DesktopDestination(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
)

private val desktopDestinations = listOf(
    DesktopDestination("home", "Home", Icons.Rounded.Home),
    DesktopDestination("reels", "Reels", Icons.Rounded.VideoLibrary),
    DesktopDestination("connect", "Connect", Icons.Rounded.Groups),
    DesktopDestination("messages", "Messages", Icons.Rounded.Chat),
    DesktopDestination("marketplace", "Marketplace", Icons.Rounded.Storefront),
    DesktopDestination("games", "Games", Icons.Rounded.SportsEsports),
    DesktopDestination("notifications", "Notifications", Icons.Rounded.Notifications),
    DesktopDestination("store", "Blink Store", Icons.Rounded.ShoppingBag),
    DesktopDestination("leaderboard", "Leaderboard", Icons.Rounded.Leaderboard),
    DesktopDestination("profile", "Profile", Icons.Rounded.Person),
    DesktopDestination("admin", "Admin", Icons.Rounded.AdminPanelSettings, adminOnly = true),
    DesktopDestination("settings", "Settings", Icons.Rounded.Settings),
)

fun runBlinkDesktopApplication() = application {
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
    val appState = remember { DesktopAppState() }
    val rpc = remember(appState.client) { DesktopRpcActions(appState.client) }
    val trayState = rememberTrayState()
    var isWindowVisible by remember { mutableStateOf(true) }
    var incomingCall by remember { mutableStateOf<DesktopCall?>(null) }
    var incomingCallerName by remember { mutableStateOf("Blink user") }
    var lastNotifiedCallId by remember { mutableStateOf<String?>(null) }
    var notificationSerial by remember { mutableIntStateOf(0) }
    var notificationTitle by remember { mutableStateOf("Blinkng") }
    var notificationBody by remember { mutableStateOf("") }
    val trayNotification = rememberNotification(notificationTitle, notificationBody)

    fun showNativeNotification(title: String, body: String) {
        notificationTitle = title
        notificationBody = body
        notificationSerial++
    }

    LaunchedEffect(notificationSerial) {
        if (notificationSerial > 0) trayState.sendNotification(trayNotification)
    }

    LaunchedEffect(Unit) { appState.initialize() }

    LaunchedEffect(appState.session?.userId) {
        incomingCall = null
        lastNotifiedCallId = null
        while (appState.session != null) {
            val calls = runCatching { rpc.incomingRingingCalls() }.getOrDefault(emptyList())
            val latest = calls.firstOrNull()
            incomingCall = latest
            if (latest != null) {
                incomingCallerName = runCatching { appState.client.fetchProfile(latest.callerId).fullName }
                    .getOrDefault("Blink user")
                if (latest.id != lastNotifiedCallId) {
                    lastNotifiedCallId = latest.id
                    val kind = if (latest.callType.equals("video", true)) "video" else "voice"
                    showNativeNotification("Incoming $kind call", "$incomingCallerName is calling you on Blinkng")
                }
            }
            delay(4_000)
        }
    }

    Tray(
        state = trayState,
        icon = BlinkTrayIcon,
        tooltip = "Blinkng",
        onAction = { isWindowVisible = true },
        menu = {
            Item("Open Blinkng", onClick = { isWindowVisible = true })
            Item("Notifications", onClick = {
                appState.selectedRoute = "notifications"
                isWindowVisible = true
            })
            Item("Messages", onClick = {
                appState.selectedRoute = "messages"
                isWindowVisible = true
            })
            Separator()
            Item("Quit Blinkng", onClick = ::exitApplication)
        },
    )

    if (isWindowVisible) {
        Window(
            onCloseRequest = { isWindowVisible = false },
            title = "Blinkng",
            state = windowState,
            icon = BlinkTrayIcon,
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(960, 640)
            }

            val theme = appState.settings?.theme?.lowercase()
            val colors = if (theme == "light") BlinkLightColors else BlinkDarkColors
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !appState.initialized -> InitializingScreen()
                        appState.session == null -> BlinkAuthScreen(appState)
                        else -> Column(modifier = Modifier.fillMaxSize()) {
                            incomingCall?.let { call ->
                                IncomingCallBanner(
                                    call = call,
                                    callerName = incomingCallerName,
                                    onAnswer = {
                                        runCatching { kotlinx.coroutines.runBlocking { rpc.answerCall(call.id) } }
                                        incomingCall = null
                                        appState.selectedRoute = "messages"
                                    },
                                    onDecline = {
                                        runCatching { kotlinx.coroutines.runBlocking { rpc.declineCall(call.id) } }
                                        incomingCall = null
                                    },
                                )
                            }
                            AuthenticatedShell(appState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingCallBanner(
    call: DesktopCall,
    callerName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Incoming ${if (call.callType.equals("video", true)) "video" else "voice"} call", fontWeight = FontWeight.Black)
                Text("$callerName is calling", fontSize = 12.sp)
            }
            Button(onClick = onAnswer) { Text("Answer") }
            OutlinedButton(onClick = onDecline) { Text("Decline") }
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
        desktopDestinations.filter { !it.adminOnly || state.adminCapability.allowed }
    }
    if (state.selectedRoute == "admin" && !state.adminCapability.allowed) state.selectedRoute = "home"

    Column(modifier = Modifier.fillMaxSize()) {
        DesktopTopBar(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1180.dp
            val showRightPanel = maxWidth >= 1420.dp && state.selectedRoute !in setOf("messages", "search")
            Row(modifier = Modifier.fillMaxSize()) {
                DesktopSidebar(
                    compact = compact,
                    destinations = visibleDestinations,
                    selectedId = state.selectedRoute,
                    onSelect = { state.selectedRoute = it },
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (state.selectedRoute) {
                        "home" -> com.blinkng.desktop.ui.HomeWithBlinkAiScreen(state)
                        "reels" -> ReelsScreen(state)
                        "connect" -> ConnectScreen(state)
                        "messages" -> MessagesScreen(state)
                        "marketplace" -> MarketplaceScreen(state)
                        "games" -> GamesScreen(state)
                        "notifications" -> NotificationsScreen(state)
                        "store" -> StoreProScreen(state)
                        "leaderboard" -> LeaderboardScreen(state)
                        "profile" -> ProfileScreen(state)
                        "admin" -> AdminProScreen(state)
                        "settings" -> SettingsScreen(state)
                        "search" -> com.blinkng.desktop.ui.PremiumSearchScreen(state)
                        else -> com.blinkng.desktop.ui.HomeWithBlinkAiScreen(state)
                    }
                }
                if (showRightPanel) {
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)))
                    DesktopRightPanel(state)
                }
            }
        }
    }
}

@Composable
private fun DesktopTopBar(state: DesktopAppState) {
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
            ) { Text("B", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp) }
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
        IconButton(onClick = {
            state.globalSearch = search.trim()
            state.selectedRoute = "search"
        }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
        IconButton(onClick = { state.selectedRoute = "notifications" }) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
        }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { state.selectedRoute = "profile" }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp)) }
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
private fun DesktopSidebar(
    compact: Boolean,
    destinations: List<DesktopDestination>,
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
                label = { if (!compact) Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
private fun DesktopRightPanel(state: DesktopAppState) {
    val profile = state.profile
    Column(
        modifier = Modifier.width(300.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your Blink", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(profile?.university ?: "Blinkng", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("${profile?.followerCount ?: 0} followers", fontWeight = FontWeight.SemiBold)
        Text("${profile?.followingCount ?: 0} following", fontWeight = FontWeight.SemiBold)
        Text("${profile?.coinBalance ?: 0} Blink Coins", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Windows status", fontWeight = FontWeight.Bold)
        Text(
            "Closing the window keeps Blinkng in the system tray so calls and notifications can continue while the desktop client is running.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

private object BlinkTrayIcon : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)
    override fun DrawScope.onDraw() {
        drawCircle(BlinkPurple, radius = size.minDimension / 2f)
        drawCircle(Color.White.copy(alpha = 0.18f), radius = size.minDimension / 3.2f)
    }
}

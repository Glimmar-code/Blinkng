package com.blinkng.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import java.awt.Dimension

private val BlinkPurple = Color(0xFF6D3DF5)
private val BlinkBackground = Color(0xFF0B0B0F)
private val BlinkSurface = Color(0xFF121218)
private val BlinkSurfaceRaised = Color(0xFF191920)

private val BlinkDesktopColors = darkColorScheme(
    primary = BlinkPurple,
    background = BlinkBackground,
    surface = BlinkSurface,
    surfaceVariant = BlinkSurfaceRaised,
)

private data class BlinkDestination(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    BlinkDestination("home", "Home", "Feed, stories and creation", Icons.Rounded.Home),
    BlinkDestination("reels", "Reels", "Full-screen short videos", Icons.Rounded.VideoLibrary),
    BlinkDestination("connect", "Connect", "Campus matching and communities", Icons.Rounded.Groups),
    BlinkDestination("messages", "Messages", "Chats, voice and video calls", Icons.Rounded.Chat),
    BlinkDestination("marketplace", "Marketplace", "Campus buying and selling", Icons.Rounded.Storefront),
    BlinkDestination("games", "Games", "Games, challenges and rewards", Icons.Rounded.SportsEsports),
    BlinkDestination("notifications", "Notifications", "Activity and official messages", Icons.Rounded.Notifications),
    BlinkDestination("store", "Blink Store", "Coins, VIP, boosts and purchases", Icons.Rounded.ShoppingBag),
    BlinkDestination("leaderboard", "Leaderboard", "Campus and global rankings", Icons.Rounded.Leaderboard),
    BlinkDestination("profile", "Profile", "Profile, posts, reels and settings", Icons.Rounded.Person),
    BlinkDestination("admin", "Admin", "Professional administration center", Icons.Rounded.AdminPanelSettings),
    BlinkDestination("settings", "Settings", "Account, privacy and app preferences", Icons.Rounded.Settings),
)

fun main() = application {
    val state = rememberWindowState(width = 1440.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Blinkng",
        state = state,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(960, 640)
        }

        MaterialTheme(colorScheme = BlinkDesktopColors) {
            BlinkDesktopApp()
        }
    }
}

@Composable
private fun BlinkDesktopApp() {
    var selectedId by remember { mutableStateOf("home") }
    var search by remember { mutableStateOf("") }
    val selected = destinations.first { it.id == selectedId }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BlinkTopBar(
                search = search,
                onSearchChange = { search = it },
                onProfileClick = { selectedId = "profile" },
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxWidth < 1180.dp
                val showRightPanel = maxWidth >= 1360.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    BlinkSidebar(
                        compact = compact,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                    )

                    Divider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    BlinkContent(
                        destination = selected,
                        modifier = Modifier.weight(1f),
                    )

                    if (showRightPanel) {
                        Divider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                        BlinkRightPanel()
                    }
                }
            }
        }
    }
}

@Composable
private fun BlinkTopBar(
    search: String,
    onSearchChange: (String) -> Unit,
    onProfileClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.width(210.dp),
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
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search Blinkng") },
            shape = RoundedCornerShape(16.dp),
        )

        IconButton(onClick = { }) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onProfileClick)
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
                Text("Your profile", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Blink account", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BlinkSidebar(
    compact: Boolean,
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
                    if (!compact) {
                        Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
private fun BlinkContent(
    destination: BlinkDestination,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(destination.title, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                destination.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }

        if (destination.id == "home") {
            item { CreatePostCard() }
            items(3) { index -> DesktopFeedPlaceholder(index + 1) }
        } else {
            item { FeatureMigrationCard(destination) }
        }
    }
}

@Composable
private fun CreatePostCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null)
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "Create a post on Blinkng…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DesktopFeedPlaceholder(number: Int) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column {
                    Text("Blinkng feed slot $number", fontWeight = FontWeight.Bold)
                    Text(
                        "Live Android/Supabase feed data will be connected during feature migration.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("Shared post/reel renderer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FeatureMigrationCard(destination: BlinkDestination) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(BlinkPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${destination.title} desktop surface", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This route is reserved in the Windows shell. The Android feature will be migrated into shared logic/UI or a Windows-specific adapter so both platforms stay feature-compatible.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun BlinkRightPanel() {
    Column(
        modifier = Modifier.width(310.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Blinkng Desktop", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Designed for wide screens while keeping the same account, backend and feature set as the Android app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp,
        )
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Text("Desktop advantages", fontWeight = FontWeight.SemiBold)
        listOf(
            "Keyboard and mouse navigation",
            "Resizable multi-column layouts",
            "Desktop notifications and tray support",
            "Drag-and-drop file workflows",
            "Native Windows EXE/MSI distribution",
        ).forEach { item ->
            Text("• $item", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.painterResource
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.blinkng.shared.MenuSearch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.AdminControlCenterActivity
import com.example.ProfessionalCenterActivity
import com.example.auth.AccountSwitcherActivity
import com.example.call.CallHistoryActivity
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.supabase.AdminSupabaseService
import com.example.notification.NotificationAndCallSettingsActivity
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay

private object MenuMotion {
    val chevron = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    profile: UserProfile,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenPostItem: () -> Unit,
    onOpenBecomeSeller: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenActivity: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onShowToast: (String) -> Unit,
    showAdPrivacyOptions: Boolean,
    onAdPrivacyOptions: () -> Unit,
    onSimulateNotification: () -> Unit
) {
    val context = LocalContext.current
    val adminService = remember { AdminSupabaseService() }
    var adminModeAvailable by remember(profile.id) { mutableStateOf(false) }
    var expandedSections by rememberSaveable { mutableStateOf(setOf("Campus & tools")) }
    var menuQuery by rememberSaveable { mutableStateOf("") }
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    val settingsSearchQuery = if ("Settings and privacy".contains(menuQuery.trim(), ignoreCase = true)) "" else menuQuery
    val campusSearchQuery = if ("Campus & tools".contains(menuQuery.trim(), ignoreCase = true)) "" else menuQuery
    val accountSearchQuery = if ("Account and support".contains(menuQuery.trim(), ignoreCase = true)) "" else menuQuery
    val menuSearchIndex = buildList {
        addAll(listOf(
            "Profile view profile",
            "Activity mentions and notifications",
            "Leaderboard campus ranking and points",
            "Marketplace browse campus listings",
            "List an item create a marketplace listing",
            "Seller hub seller verification and tools",
            "Settings and privacy appearance light dark theme",
            "Calls & notification sounds ringtones message sounds vibration Android call alerts",
            "Call history missed incoming outgoing Blink calls",
            "Privacy & DM settings private account and messaging controls",
            "Safety & blocked accounts block unblock and report users",
            "Login & account security session and security controls",
            "Data & storage cache and account data",
            "Campus & tools",
            "Scheduled posts manage posts queued for later",
            "Study & group center group chats and study tools",
            "Campus verification manage your verification",
            "Orders & wishlist saved market items and order status",
            "Account and support",
            "Edit profile edit your campus profile",
            "Switch account use a saved account",
            "About Blink about this app",
            "Log out end this device session",
            "Delete account permanent account deletion"
        ))
        if (adminModeAvailable) add("Switch to Admin Account open Blink administration")
        if (showAdPrivacyOptions) add("Ad privacy choices manage consent choices used for advertising")
    }

    LaunchedEffect(profile.id) {
        adminModeAvailable = adminService.fetchCapability().getOrNull()?.isAdmin == true
    }

    fun toggle(title: String) {
        expandedSections = if (title in expandedSections) {
            expandedSections - title
        } else {
            expandedSections + title
        }
    }

    fun openProfessional(section: String) {
        onDismiss()
        context.startActivity(
            Intent(context, ProfessionalCenterActivity::class.java)
                .putExtra("section", section)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("app_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Menu", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("menu_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close menu")
                }
            }
            OutlinedTextField(
                value = menuQuery,
                onValueChange = { menuQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("menu_search"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search menu") },
                trailingIcon = {
                    if (menuQuery.isNotEmpty()) {
                        IconButton(onClick = { menuQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            if (MenuSearch.matches(menuQuery, "profile", profile.fullName, profile.username)) ProfileHeaderCard(profile) {
                onDismiss()
                onViewProfile()
            }

            if (adminModeAvailable) {
                SearchableMenuItemRow(menuQuery, 
                    Icons.Outlined.Security,
                    "Switch to Admin Account",
                    "Open Blink administration"
                ) {
                    onDismiss()
                    context.startActivity(Intent(context, AdminControlCenterActivity::class.java))
                }
            }

            SearchableMenuItemRow(menuQuery, Icons.Outlined.Notifications, "Activity", "Mentions and notifications") {
                onDismiss(); onOpenActivity()
            }
            SearchableMenuItemRow(menuQuery, Icons.Outlined.EmojiEvents, "Leaderboard", "Campus ranking and points") {
                onDismiss(); onOpenLeaderboard()
            }
            SearchableMenuItemRow(menuQuery, Icons.Outlined.Storefront, "Marketplace", "Browse campus listings") {
                onDismiss(); onOpenMarket()
            }
            SearchableMenuItemRow(menuQuery, Icons.Outlined.AddShoppingCart, "List an item", "Create a marketplace listing") {
                onDismiss(); onOpenPostItem()
            }
            SearchableMenuItemRow(menuQuery, Icons.Outlined.AccountBalanceWallet, "Seller hub", "Seller verification and tools") {
                onDismiss(); onOpenBecomeSeller()
            }

            MenuSection(
                query = menuQuery,
                searchTerms = "appearance dark light theme calls notifications sounds call history privacy blocked DM login account security data storage cache ad consent ringtones messages",
                title = "Settings and privacy",
                expanded = menuQuery.isNotBlank() || "Settings and privacy" in expandedSections,
                onToggle = { toggle("Settings and privacy") }
            ) {
                if (MenuSearch.matches(settingsSearchQuery, "appearance", "theme light dark")) {
                    ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)
                }
                SearchableMenuItemRow(settingsSearchQuery, 
                    Icons.Outlined.Notifications,
                    "Calls & notification sounds",
                    "Ringtones, message sounds, vibration and Android call alerts"
                ) {
                    onDismiss()
                    context.startActivity(Intent(context, NotificationAndCallSettingsActivity::class.java))
                }
                SearchableMenuItemRow(settingsSearchQuery, 
                    Icons.Outlined.Notifications,
                    "Call history",
                    "Missed, incoming and outgoing Blink calls"
                ) {
                    onDismiss()
                    context.startActivity(Intent(context, CallHistoryActivity::class.java))
                }
                if (showAdPrivacyOptions) {
                    SearchableMenuItemRow(settingsSearchQuery, 
                        Icons.Outlined.Security,
                        "Ad privacy choices",
                        "Manage consent choices used for advertising"
                    ) {
                        onDismiss()
                        onAdPrivacyOptions()
                    }
                }
                SearchableMenuItemRow(settingsSearchQuery, Icons.Outlined.Lock, "Privacy & DM settings", "Private account and messaging controls") {
                    openProfessional("privacy")
                }
                SearchableMenuItemRow(settingsSearchQuery, Icons.Outlined.Block, "Safety & blocked accounts", "Block, unblock and report users") {
                    openProfessional("safety")
                }
                SearchableMenuItemRow(settingsSearchQuery, Icons.Outlined.Security, "Login & account security", "Session and security controls") {
                    openProfessional("account")
                }
                SearchableMenuItemRow(settingsSearchQuery, Icons.Outlined.Storage, "Data & storage", "Cache and account data") {
                    openProfessional("account")
                }
            }

            MenuSection(
                query = menuQuery,
                searchTerms = "scheduled posts queued study group center campus verification orders wishlist saved items",
                title = "Campus & tools",
                expanded = menuQuery.isNotBlank() || "Campus & tools" in expandedSections,
                onToggle = { toggle("Campus & tools") }
            ) {
                SearchableMenuItemRow(campusSearchQuery, Icons.Outlined.Schedule, "Scheduled posts", "Manage posts queued for later") {
                    openProfessional("scheduled")
                }
                SearchableMenuItemRow(campusSearchQuery, Icons.Outlined.Groups, "Study & group center", "Group chats and study tools") {
                    openProfessional("groups")
                }
                SearchableMenuItemRow(campusSearchQuery, 
                    Icons.Outlined.Verified,
                    "Campus verification",
                    "Manage your verification",
                    trailingText = if (profile.verificationBadge != VerificationBadge.NONE) "Active" else null
                ) {
                    onDismiss(); onViewProfile()
                }
                SearchableMenuItemRow(campusSearchQuery, Icons.Outlined.ShoppingBag, "Orders & wishlist", "Saved market items and order status") {
                    openProfessional("market")
                }
            }

            MenuSection(
                query = menuQuery,
                searchTerms = "edit profile switch account about blink log out delete account end device session saved account",
                title = "Account and support",
                expanded = menuQuery.isNotBlank() || "Account and support" in expandedSections,
                onToggle = { toggle("Account and support") }
            ) {
                SearchableMenuItemRow(accountSearchQuery, Icons.Outlined.Edit, "Edit profile", "Edit your campus profile") {
                    onDismiss(); onEditProfile()
                }
                SearchableMenuItemRow(accountSearchQuery, Icons.Outlined.SwitchAccount, "Switch account", "Use a saved account") {
                    onDismiss()
                    context.startActivity(Intent(context, AccountSwitcherActivity::class.java))
                }
                SearchableMenuItemRow(accountSearchQuery, Icons.Outlined.Info, "About Blink", "About this app") {
                    showAboutDialog = true
                }
                SearchableMenuItemRow(accountSearchQuery, 
                    Icons.AutoMirrored.Filled.Logout,
                    "Log out",
                    "End this device session",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    showLogoutConfirmation = true
                }
                SearchableMenuItemRow(accountSearchQuery, 
                    Icons.Outlined.DeleteForever,
                    "Delete account",
                    "Permanent account deletion",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    openProfessional("account")
                }
            }
            if (menuQuery.isNotBlank() && menuSearchIndex.none { MenuSearch.matches(menuQuery, it) }) {
                Text(
                    "No matching menu items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).testTag("menu_no_results")
                )
            }
        }
    }
    if (showLogoutConfirmation) AlertDialog(
        onDismissRequest = { showLogoutConfirmation = false },
        title = { Text("Log out of BLINK?") },
        text = { Text("You can sign in again at any time.") },
        confirmButton = {
            TextButton(onClick = {
                showLogoutConfirmation = false
                onDismiss()
                onLogout()
            }) { Text("Log out") }
        },
        dismissButton = {
            TextButton(onClick = { showLogoutConfirmation = false }) { Text("Cancel") }
        }
    )
    if (showAboutDialog) AlertDialog(
        onDismissRequest = { showAboutDialog = false },
        title = { Text("About BLINK") },
        text = { Text("BLINK connects campus communities through posts, reels, games, messaging and marketplace.") },
        confirmButton = {
            TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
        }
    )
}

@Composable
private fun ProfileHeaderCard(profile: UserProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = profile.avatarUrl,
                error = painterResource(R.drawable.ic_default_profile),
                fallback = painterResource(R.drawable.ic_default_profile),
                contentDescription = "My profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
            if (profile.onlineNow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(Color(0xFF22C55E), CircleShape)
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${profile.username}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (profile.verificationBadge != VerificationBadge.NONE) {
            Icon(
                Icons.Outlined.Verified,
                contentDescription = "Verified account",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open profile",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuSection(
    query: String,
    searchTerms: String,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        if (expanded) 90f else 0f,
        MenuMotion.chevron,
        label = "menuChevron"
    )
    if (MenuSearch.matches(query, title, searchTerms)) {
        Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                modifier = Modifier.size(21.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
        ) {
            Column(content = content)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
        ) {
            Spacer(Modifier.size(1.dp))
        }
        }
    }
}

@Composable
private fun ThemeToggleRow(isDark: Boolean, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleTheme)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
            contentDescription = "Appearance",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            "Appearance",
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = isDark,
            onCheckedChange = { onToggleTheme() },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.onSurface, checkedThumbColor = MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
private fun SearchableMenuItemRow(
    query: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    iconColor: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    if (MenuSearch.matches(query, title, subtitle)) {
        MenuItemRow(icon, title, subtitle, trailingText, iconColor, titleColor, onClick)
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    iconColor: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (iconColor != Color.Unspecified) iconColor else if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingText != null) {
            Text(
                trailingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

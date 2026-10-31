package com.example.ui.screens

import com.example.ui.components.BlinkElasticSwipeDirection
import com.example.ui.components.blinkElasticSwipeAction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.components.blinkPressFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.performance.HighRefreshRateController
import com.blinkng.shared.BlinkPerformanceMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.BlinkMenuDestination
import com.example.R
import com.example.data.models.UserProfile
import com.example.update.WebsiteApkUpdateCoordinator
import com.example.BuildConfig
import kotlinx.coroutines.launch
import com.example.ui.components.FeedBottomBar
import com.example.viewmodel.MainTab
import java.text.NumberFormat

private val MenuPurple = Color(0xFF7839F4)
private val MenuOrange = Color(0xFFF5A623)
private val MenuGreen = Color(0xFF00B779)
private val MenuRed = Color(0xFFF43248)
private val MenuBlue = Color(0xFF1684EB)
private val MenuCharcoal = Color(0xFF4B5563)

/** Full-page, reference-inspired menu: every number is real account data, never screenshot demo data. */
@Composable
fun BlinkReferenceMenuScreen(
    profile: UserProfile,
    isDark: Boolean,
    hasUnreadNotifications: Boolean,
    appVersion: String,
    onViewProfile: () -> Unit,
    onLeaderboard: () -> Unit,
    onWallet: () -> Unit,
    onNotifications: () -> Unit,
    onSaved: () -> Unit,
    onVerification: () -> Unit,
    onSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onCallSettings: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenAdmin: () -> Unit,
    /** Passed from the app's authenticated capability check; avoids a second racy lookup. */
    canOpenAdmin: Boolean = false,
    onMessages: () -> Unit,
    onEditProfile: () -> Unit,
    onFindFriends: () -> Unit,
    onPostItem: () -> Unit,
    onSellerHub: () -> Unit,
    onScheduledPosts: () -> Unit,
    onGroups: () -> Unit,
    onOrdersAndWishlist: () -> Unit,
    onCallHistory: () -> Unit,
    showAdPrivacyOptions: Boolean,
    onAdPrivacyOptions: () -> Unit,
    onLogOut: () -> Unit,
    onSearch: () -> Unit,
    onGames: () -> Unit,
    onOpenUpdateWebsite: () -> Unit,
    onOpenHelpWebsite: () -> Unit,
    onHome: () -> Unit,
    onReels: () -> Unit,
    onMarket: () -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val background = if (isDark) Color(0xFF0B0B0B) else Color.White
    val foreground = if (isDark) Color.White else Color(0xFF111111)
    val muted = if (isDark) Color(0xFFAAAAAA) else Color(0xFF7F7F7F)
    val border = if (isDark) Color(0xFF333333) else Color(0xFFECECEC)
    val surface = if (isDark) Color(0xFF181818) else Color.White
    val context = LocalContext.current
    var performanceMode by remember(context) {
        mutableStateOf(HighRefreshRateController.mode(context))
    }
    val compact = LocalConfiguration.current.screenWidthDp < 350 ||
        LocalDensity.current.fontScale >= 1.5f
    var page by rememberSaveable(profile.id) { mutableStateOf("menu") }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var showReferGrow by rememberSaveable(profile.id) { mutableStateOf(false) }
    val adminModeAvailable = canOpenAdmin
    var updateMessage by rememberSaveable { mutableStateOf("Check for the latest verified BLINK release.") }
    var checkingUpdate by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()

    if (showReferGrow) {
        BlinkReferGrowDialog(username = profile.username, onDismiss = { showReferGrow = false })
    }

    BackHandler {
        if (showLogoutConfirm) showLogoutConfirm = false
        else if (page != "menu") page = "menu"
        else onDismiss()
    }

    Column(
        Modifier.fillMaxSize()
            .blinkElasticSwipeAction(
                direction = BlinkElasticSwipeDirection.RIGHT,
                onAction = {
                    if (page != "menu") page = "menu"
                    else if (!showLogoutConfirm) onDismiss()
                },
                enabled = !showLogoutConfirm
            )
            .background(background).statusBarsPadding().testTag("blink_reference_menu")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .padding(horizontal = 17.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(37.dp).clickable(onClick = onViewProfile)) {
                AsyncImage(
                    model = profile.avatarUrl,
                    placeholder = painterResource(R.drawable.ic_default_profile),
                    fallback = painterResource(R.drawable.ic_default_profile),
                    error = painterResource(R.drawable.ic_default_profile),
                    contentDescription = "Your profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
                if (profile.onlineNow) {
                    Box(Modifier.align(Alignment.BottomEnd).size(10.dp)
                        .background(Color(0xFF4CCB64), CircleShape)
                        .border(1.dp, background, CircleShape))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "BLINK", fontSize = if (compact) 19.sp else 21.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.8.sp,
                    color = foreground, maxLines = 1,
                )
            }
            HeaderAction(Icons.Rounded.Search, "Search", foreground, onSearch)
            HeaderAction(Icons.Rounded.GridView, "Open Items", foreground, onGames)
            Box {
                HeaderAction(Icons.Rounded.NotificationsNone, "Notifications", foreground, onNotifications)
                if (hasUnreadNotifications) {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 4.dp)
                        .size(8.dp).background(MenuRed, CircleShape))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (page == "menu") {
                Text(
                    "Menu",
                    fontSize = 31.sp, fontWeight = FontWeight.Black,
                    color = foreground,
                    modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                )
                Surface(
                    color = surface, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier.fillMaxWidth().testTag("menu_profile_card"),
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                fallback = painterResource(R.drawable.ic_default_profile),
                                error = painterResource(R.drawable.ic_default_profile),
                                contentDescription = "Your profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(if (compact) 52.dp else 60.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.fullName.ifBlank { profile.username.ifBlank { "BLINK student" } },
                                    fontSize = 16.sp, color = foreground,
                                    fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    profile.university.ifBlank { "@${profile.username}" },
                                    fontSize = 12.sp, color = muted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!compact) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isDark) Color(0xFF292929) else Color(0xFFF5F5F5),
                                    onClick = onViewProfile,
                                    modifier = Modifier.testTag("menu_view_profile")
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("View Profile", fontSize = 11.sp, color = foreground)
                                        Spacer(Modifier.width(3.dp))
                                        Icon(Icons.Rounded.ChevronRight, null, tint = foreground, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                        if (compact) {
                            TextButton(onClick = onViewProfile, modifier = Modifier.align(Alignment.End)) {
                                Text("View Profile  ›", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            MenuStat(NumberFormat.getIntegerInstance().format(profile.followerCount), "Followers", foreground, muted, Modifier.weight(1f))
                            MenuStat(NumberFormat.getIntegerInstance().format(profile.followingCount), "Following", foreground, muted, Modifier.weight(1f))
                            MenuStat(NumberFormat.getIntegerInstance().format(profile.points), "Points", foreground, muted, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                if (adminModeAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(surface)
                            .border(1.dp, border, RoundedCornerShape(15.dp))
                            .clickable(onClick = onOpenAdmin)
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("menu_switch_to_admin"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null,
                            tint = MenuPurple, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("BLINK Admin Dashboard", color = foreground,
                            modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open admin workspace",
                            tint = foreground)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                BlinkMenuDestination.entries.forEach { item ->
                    val icon: ImageVector
                    val tint: Color
                    when (item) {
                        BlinkMenuDestination.PROFILE -> { icon = Icons.Rounded.Person; tint = MenuPurple }
                        BlinkMenuDestination.REFER_GROW -> { icon = Icons.Rounded.GroupAdd; tint = MenuBlue }
                        BlinkMenuDestination.LEADERBOARD -> { icon = Icons.Rounded.EmojiEvents; tint = MenuOrange }
                        BlinkMenuDestination.WALLET -> { icon = Icons.Rounded.AccountBalanceWallet; tint = MenuGreen }
                        BlinkMenuDestination.NOTIFICATIONS -> { icon = Icons.Rounded.Notifications; tint = MenuRed }
                        BlinkMenuDestination.SAVED -> { icon = Icons.Rounded.Bookmark; tint = MenuBlue }
                        BlinkMenuDestination.VERIFICATION -> { icon = Icons.Rounded.VerifiedUser; tint = MenuGreen }
                        BlinkMenuDestination.UPDATES -> { icon = Icons.Rounded.SystemUpdate; tint = MenuOrange }
                        BlinkMenuDestination.SETTINGS -> { icon = Icons.Rounded.Settings; tint = MenuCharcoal }
                        BlinkMenuDestination.HELP -> { icon = Icons.Rounded.Help; tint = MenuBlue }
                        BlinkMenuDestination.LOGOUT -> { icon = Icons.Rounded.Logout; tint = MenuRed }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(surface)
                            .border(1.dp, border, RoundedCornerShape(15.dp))
                            .clickable {
                                when (item) {
                                    BlinkMenuDestination.PROFILE -> onViewProfile()
                                    BlinkMenuDestination.REFER_GROW -> showReferGrow = true
                                    BlinkMenuDestination.LEADERBOARD -> onLeaderboard()
                                    BlinkMenuDestination.WALLET -> onWallet()
                                    BlinkMenuDestination.NOTIFICATIONS -> onNotifications()
                                    BlinkMenuDestination.SAVED -> onSaved()
                                    BlinkMenuDestination.VERIFICATION -> onVerification()
                                    BlinkMenuDestination.UPDATES -> page = "updates"
                                    BlinkMenuDestination.SETTINGS -> page = "settings"
                                    BlinkMenuDestination.HELP -> page = "help"
                                    BlinkMenuDestination.LOGOUT -> showLogoutConfirm = true
                                }
                            }
                            .heightIn(min = if (compact) 54.dp else 51.dp)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("menu_${item.key}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(37.dp).background(tint.copy(alpha = if (isDark) .23f else .11f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(15.dp))
                        Text(
                            item.title, modifier = Modifier.weight(1f), fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, color = foreground, maxLines = 2
                        )
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${item.title}",
                            tint = foreground, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                TextButton(
                    onClick = { page = "menu" },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Icon(Icons.Rounded.ArrowBack, null); Text("  Back to Menu") }
                Text(
                    when (page) {
                        "updates" -> "Update Center"
                        "settings" -> "Settings"
                        else -> "Help & Support"
                    },
                    fontSize = 28.sp, fontWeight = FontWeight.Black,
                    color = foreground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                if (page == "updates") {
                    DetailCard("Your BLINK version", appVersion, surface, border, foreground, muted)
                    DetailCard("Stay up to date", "BLINK checks the published release metadata before offering an APK. Google Play installations also use the built-in Play updater.", surface, border, foreground, muted)
                    DetailCard("Update status", updateMessage, surface, border, foreground, muted)
                    Button(
                        enabled = !checkingUpdate,
                        onClick = {
                            checkingUpdate = true
                            updateScope.launch {
                                val latest = WebsiteApkUpdateCoordinator.checkForUpdate(BuildConfig.VERSION_CODE)
                                updateMessage = if (latest != null) {
                                    "A newer verified BLINK update is available on the official website."
                                } else {
                                    "No verified website update was found. If you are offline, check again when connected."
                                }
                                checkingUpdate = false
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text(if (checkingUpdate) "Checking…" else "Check for updates") }
                    OutlinedButton(onClick = onOpenUpdateWebsite, modifier = Modifier.fillMaxWidth()) {
                        Text("Open official download page")
                    }
                } else if (page == "settings") {
                    DetailCard("Appearance", "Switch between BLINK's light and dark themes.", surface, border, foreground, muted)
                    OutlinedButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isDark) "Switch to light theme" else "Switch to dark theme")
                    }
                    DetailCard("Performance", "Automatic lets Android choose the refresh rate. Balanced limits the request to 90Hz; Power Saving targets 60Hz and reduces decorative motion.", surface, border, foreground, muted)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlinkPerformanceMode.entries.forEach { option ->
                            FilterChip(
                                selected = performanceMode == option,
                                onClick = {
                                    HighRefreshRateController.setMode(context, option)
                                    performanceMode = option
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    DetailCard("Privacy & account", "Manage private messaging, safety, blocked users, account settings, and data controls.", surface, border, foreground, muted)
                    OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open privacy and security settings")
                    }
                    OutlinedButton(onClick = onCallSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Calls & notification sounds")
                    }
                    OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) {
                        Text("Switch account")
                    }
                    OutlinedButton(onClick = onCallHistory, modifier = Modifier.fillMaxWidth()) {
                        Text("Call history")
                    }
                    if (showAdPrivacyOptions) {
                        OutlinedButton(onClick = onAdPrivacyOptions, modifier = Modifier.fillMaxWidth()) {
                            Text("Ad privacy choices")
                        }
                    }
                    DetailCard("More BLINK tools", "Your existing campus and marketplace tools are still available here.", surface, border, foreground, muted)
                    val campusTools = listOf(
                        "Messages & inbox" to onMessages,
                        "Edit profile" to onEditProfile,
                        "Find Friends" to onFindFriends,
                        "List an item" to onPostItem,
                        "Seller hub" to onSellerHub,
                        "Scheduled posts" to onScheduledPosts,
                        "Study & group center" to onGroups,
                        "Orders & wishlist" to onOrdersAndWishlist
                    )
                    campusTools.forEach { (title, action) ->
                        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                            Text(title)
                        }
                    }
                } else {
                    DetailCard("Account and profile", "Open My Profile to update your account details. Use Settings for privacy and security controls.", surface, border, foreground, muted)
                    DetailCard("Notifications and coins", "Notification preferences live in Settings. Your Wallet & Coins page shows real BLINK Coins and reward history.", surface, border, foreground, muted)
                    DetailCard("Need more help?", "Open the official BLINK website for current support and contact information.", surface, border, foreground, muted)
                    OutlinedButton(onClick = onOpenHelpWebsite, modifier = Modifier.fillMaxWidth()) {
                        Text("Open official BLINK website")
                    }
                }
            }
        }

        FeedBottomBar(
            currentTab = MainTab.HOME,
            feedSubTab = 0,
            onHomeClick = onHome,
            onReelsClick = onReels,
            onMarketClick = onMarket,
            onConnectClick = onConnect,
            onMessageClick = onMessages,
            isDark = isDark,
            isMenuOpen = true,
            onMenuClick = { page = "menu" }
        )
    }
    if (showLogoutConfirm) AlertDialog(
        onDismissRequest = { showLogoutConfirm = false },
        title = { Text("Log out of BLINK?") },
        text = { Text("This will end your current session. You can sign in again later.") },
        confirmButton = {
            TextButton(onClick = { showLogoutConfirm = false; onLogOut() }) { Text("Log Out") }
        },
        dismissButton = {
            TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
        }
    )
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.size(39.dp).blinkPressFeedback(interaction)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
    }
}

@Composable
private fun MenuStat(value: String, label: String, foreground: Color, muted: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = foreground, maxLines = 1)
        Text(label, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailCard(
    title: String, description: String, surface: Color, border: Color, foreground: Color, muted: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = foreground)
            Text(description, fontSize = 14.sp, color = muted)
        }
    }
}

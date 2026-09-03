package com.example.ui.components

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
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Verified
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ProfessionalCenterActivity
import com.example.auth.AccountSwitcherActivity
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay

private object MenuMotion {
    val chevron = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
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
    onSimulateNotification: () -> Unit
) {
    val context = LocalContext.current

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
        modifier = Modifier.testTag("app_menu_sheet")
    ) {
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(25); contentVisible = true }
        var expandedSections by rememberSaveable {
            mutableStateOf(setOf("Profile", "Marketplace", "Privacy & Security", "Session"))
        }

        fun toggle(title: String) {
            expandedSections = if (title in expandedSections) expandedSections - title else expandedSections + title
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 34.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(260)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 7 }
            ) {
                ProfileHeaderCard(profile) {
                    onDismiss()
                    onViewProfile()
                }
            }

            MenuSection("Profile", expandedSections.contains("Profile"), { toggle("Profile") }) {
                MenuItemRow(Icons.Outlined.Person, "View profile", "Badges, skills, posts and campus identity") {
                    onDismiss(); onViewProfile()
                }
                MenuItemRow(Icons.Outlined.Edit, "Edit profile", "Academic details, bio, contact and links") {
                    onDismiss(); onEditProfile()
                }
                MenuItemRow(
                    Icons.Outlined.Verified,
                    "Campus verification",
                    if (profile.verificationBadge != VerificationBadge.NONE) "Verification is active" else "Manage verification from your profile",
                    trailingText = if (profile.verificationBadge != VerificationBadge.NONE) "Active" else null
                ) {
                    onDismiss(); onViewProfile()
                }
            }

            MenuSection("Marketplace", expandedSections.contains("Marketplace"), { toggle("Marketplace") }) {
                MenuItemRow(Icons.Outlined.Storefront, "Browse marketplace", "Campus listings from real Blink sellers") {
                    onDismiss(); onOpenMarket()
                }
                MenuItemRow(Icons.Outlined.AddShoppingCart, "List an item", "Create a marketplace listing") {
                    onDismiss(); onOpenPostItem()
                }
                MenuItemRow(Icons.Outlined.AccountBalanceWallet, "Seller hub", "Seller verification and listing tools") {
                    onDismiss(); onOpenBecomeSeller()
                }
                MenuItemRow(Icons.Outlined.ShoppingBag, "Orders & wishlist", "Buyer/seller status controls and saved items", trailingText = "Live") {
                    openProfessional("market")
                }
            }

            MenuSection("Campus & community", expandedSections.contains("Campus & community"), { toggle("Campus & community") }) {
                MenuItemRow(Icons.Outlined.Groups, "Study & group center", "Create secure group chats and coordinate study") {
                    openProfessional("groups")
                }
                MenuItemRow(Icons.Outlined.EmojiEvents, "Leaderboard", "Campus points, games and contributor rankings") {
                    onDismiss(); onOpenLeaderboard()
                }
                MenuItemRow(Icons.Outlined.Notifications, "Activity", "Mentions, interactions and live notifications") {
                    onDismiss(); onOpenActivity()
                }
            }

            MenuSection("Experience", expandedSections.contains("Experience"), { toggle("Experience") }) {
                ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)
            }

            MenuSection("Privacy & Security", expandedSections.contains("Privacy & Security"), { toggle("Privacy & Security") }) {
                MenuItemRow(Icons.Outlined.Lock, "Privacy & DM settings", "Private account, DM privacy, read receipts and presence", trailingText = "Live") {
                    openProfessional("privacy")
                }
                MenuItemRow(Icons.Outlined.Block, "Safety & blocked accounts", "Block, unblock and report users", trailingText = "Live") {
                    openProfessional("safety")
                }
                MenuItemRow(Icons.Outlined.Security, "Login & account security", "Encrypted session and account lifecycle controls") {
                    openProfessional("account")
                }
                MenuItemRow(Icons.Outlined.Storage, "Data & storage", "Clear cache and export your account data") {
                    openProfessional("account")
                }
                MenuItemRow(
                    Icons.Outlined.DeleteForever,
                    "Delete account",
                    "Permanent deletion with server-session revocation",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    openProfessional("account")
                }
            }

            MenuSection("About", expandedSections.contains("About"), { toggle("About") }) {
                MenuItemRow(Icons.Outlined.Info, "About Blink", "Student community platform • professional build") {
                    onShowToast("Blink • Student community, Connect Hub, Games and Marketplace")
                }
            }

            MenuSection("Session", expandedSections.contains("Session"), { toggle("Session") }) {
                MenuItemRow(Icons.Outlined.SwitchAccount, "Switch account", "Use one of your securely saved recent accounts") {
                    onDismiss()
                    context.startActivity(Intent(context, AccountSwitcherActivity::class.java))
                }
                MenuItemRow(
                    Icons.AutoMirrored.Filled.Logout,
                    "Log out",
                    "End this device session securely",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    onDismiss(); onLogout()
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(profile: UserProfile, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) .985f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "menuProfileScale"
    )
    val pulse = rememberInfiniteTransition(label = "menuOnlinePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Reverse),
        label = "menuOnlinePulseScale"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                pressed = true
                onClick()
            }
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = "My profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
                if (profile.onlineNow) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(13.dp)
                            .scale(pulseScale)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("@${profile.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOf(profile.academicLevel, profile.department, profile.university).filter { it.isNotBlank() }.joinToString(" • "),
                    fontSize = 9.5.sp,
                    color = BlinkPink,
                    maxLines = 1
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Open profile", modifier = Modifier.size(14.dp))
        }
    }
    LaunchedEffect(pressed) { if (pressed) { delay(130); pressed = false } }
}

@Composable
private fun MenuSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, MenuMotion.chevron, label = "menuChevron")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 5.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                modifier = Modifier.weight(1f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = .7.sp
            )
            Icon(Icons.Default.ChevronRight, contentDescription = if (expanded) "Collapse $title" else "Expand $title", modifier = Modifier.size(16.dp).rotate(rotation))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(230, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(190)) + fadeOut(tween(120))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

@Composable
private fun ThemeToggleRow(isDark: Boolean, onToggleTheme: () -> Unit) {
    val bg by animateColorAsState(
        if (isDark) Color(0xFF251E2D) else Color(0xFFF4E9FF),
        tween(220),
        label = "menuThemeBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleTheme)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isDark,
                transitionSpec = { scaleIn().togetherWith(fadeOut()) },
                label = "menuThemeIcon"
            ) { dark ->
                Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = "Appearance", tint = BlinkPink)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(if (isDark) "Dark mode" else "Light mode", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = isDark,
            onCheckedChange = { onToggleTheme() },
            colors = SwitchDefaults.colors(checkedTrackColor = BlinkPink)
        )
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    iconColor: Color = BlinkPink,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .985f else 1f, tween(100), label = "menuRowScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = iconColor.copy(alpha = .12f)) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.padding(9.dp).size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor
            )
            Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (trailingText != null) {
            Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = .12f)) {
                Text(trailingText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = BlinkPink, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LaunchedEffect(pressed) { if (pressed) { delay(120); pressed = false } }
}

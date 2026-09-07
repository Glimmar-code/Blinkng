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
    var expandedSections by rememberSaveable { mutableStateOf(setOf("Campus & tools")) }

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
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.testTag("app_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileHeaderCard(profile) {
                onDismiss()
                onViewProfile()
            }

            MenuItemRow(Icons.Outlined.Notifications, "Activity", "Mentions and notifications") {
                onDismiss(); onOpenActivity()
            }
            MenuItemRow(Icons.Outlined.EmojiEvents, "Leaderboard", "Campus ranking and points") {
                onDismiss(); onOpenLeaderboard()
            }
            MenuItemRow(Icons.Outlined.Storefront, "Marketplace", "Browse campus listings") {
                onDismiss(); onOpenMarket()
            }
            MenuItemRow(Icons.Outlined.AddShoppingCart, "List an item", "Create a marketplace listing") {
                onDismiss(); onOpenPostItem()
            }
            MenuItemRow(Icons.Outlined.AccountBalanceWallet, "Seller hub", "Seller verification and tools") {
                onDismiss(); onOpenBecomeSeller()
            }

            MenuSection(
                title = "Settings and privacy",
                expanded = "Settings and privacy" in expandedSections,
                onToggle = { toggle("Settings and privacy") }
            ) {
                ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)
                MenuItemRow(Icons.Outlined.Lock, "Privacy & DM settings", "Private account and messaging controls") {
                    openProfessional("privacy")
                }
                MenuItemRow(Icons.Outlined.Block, "Safety & blocked accounts", "Block, unblock and report users") {
                    openProfessional("safety")
                }
                MenuItemRow(Icons.Outlined.Security, "Login & account security", "Session and security controls") {
                    openProfessional("account")
                }
                MenuItemRow(Icons.Outlined.Storage, "Data & storage", "Cache and account data") {
                    openProfessional("account")
                }
            }

            MenuSection(
                title = "Campus & tools",
                expanded = "Campus & tools" in expandedSections,
                onToggle = { toggle("Campus & tools") }
            ) {
                MenuItemRow(Icons.Outlined.Groups, "Study & group center", "Group chats and study tools") {
                    openProfessional("groups")
                }
                MenuItemRow(
                    Icons.Outlined.Verified,
                    "Campus verification",
                    "Manage your verification",
                    trailingText = if (profile.verificationBadge != VerificationBadge.NONE) "Active" else null
                ) {
                    onDismiss(); onViewProfile()
                }
                MenuItemRow(Icons.Outlined.ShoppingBag, "Orders & wishlist", "Saved market items and order status") {
                    openProfessional("market")
                }
            }

            MenuSection(
                title = "Account and support",
                expanded = "Account and support" in expandedSections,
                onToggle = { toggle("Account and support") }
            ) {
                MenuItemRow(Icons.Outlined.Edit, "Edit profile", "Edit your campus profile") {
                    onDismiss(); onEditProfile()
                }
                MenuItemRow(Icons.Outlined.SwitchAccount, "Switch account", "Use a saved account") {
                    onDismiss()
                    context.startActivity(Intent(context, AccountSwitcherActivity::class.java))
                }
                MenuItemRow(Icons.Outlined.Info, "About Blink", "About this app") {
                    onShowToast("Blink • Student community, Connect Hub, Games and Marketplace")
                }
                MenuItemRow(
                    Icons.AutoMirrored.Filled.Logout,
                    "Log out",
                    "End this device session",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    onDismiss(); onLogout()
                }
                MenuItemRow(
                    Icons.Outlined.DeleteForever,
                    "Delete account",
                    "Permanent account deletion",
                    iconColor = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error
                ) {
                    openProfessional("account")
                }
            }
        }
    }
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "@${profile.username}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            tint = if (titleColor == MaterialTheme.colorScheme.error) titleColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor
        )
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

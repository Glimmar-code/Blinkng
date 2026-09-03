package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.AccountSwitcherActivity
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay

private object MenuMotion {
    val RowStagger = 28
    val ExpandSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val ChevronSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    profile: UserProfile,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenPostItem: () -> Unit,
    onOpenBecomeSeller: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenActivity: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = if (isDark) Color(0x40FFFFFF) else Color(0x30000000)) },
        modifier = Modifier.testTag("app_menu_sheet")
    ) {
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(30); contentVisible = true }
        var expandedSections by rememberSaveable {
            mutableStateOf(setOf("Profile & Campus Identity", "Aluta Campus Market", "Experience & Activity", "Session"))
        }
        fun toggleSection(title: String) { expandedSections = if (expandedSections.contains(title)) expandedSections - title else expandedSections + title }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedVisibility(visible = contentVisible, enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing), initialOffsetY = { -it / 6 })) {
                ProfileHeaderCard(profile = profile, onClick = { onDismiss(); onViewProfile() })
            }
            Spacer(modifier = Modifier.height(4.dp))
            AccordionSection("Profile & Campus Identity", expandedSections.contains("Profile & Campus Identity"), { toggleSection("Profile & Campus Identity") }, 40) {
                MenuItemRow(
                    Icons.Outlined.Person,
                    "View My Full Profile",
                    "Badges, skills, endorsements & portfolio",
                    onClick = { onDismiss(); onViewProfile() }
                )
                MenuItemRow(
                    Icons.Outlined.Edit,
                    "Edit Profile & Bio",
                    "Update contact, academic level & socials",
                    onClick = { onDismiss(); onEditProfile() }
                )
                MenuItemRow(
                    Icons.Outlined.Verified,
                    "Campus Verification",
                    if (profile.verificationBadge != VerificationBadge.NONE) "View verification status or upgrade" else "Apply for a verified badge",
                    if (profile.verificationBadge != VerificationBadge.NONE) "Verified" else "Apply",
                    onClick = { onDismiss(); onOpenVerification() }
                )
            }

            AccordionSection("Aluta Campus Market", expandedSections.contains("Aluta Campus Market"), { toggleSection("Aluta Campus Market") }) {
                MenuItemRow(
                    Icons.Outlined.Storefront,
                    "Browse Marketplace",
                    "Books, electronics, hostel gear & fashion",
                    onClick = { onDismiss(); onOpenMarket() }
                )
                MenuItemRow(
                    Icons.Outlined.AddShoppingCart,
                    "Post Item for Sale",
                    "List your item and publish it to the live market",
                    onClick = { onDismiss(); onOpenPostItem() }
                )
                MenuItemRow(
                    Icons.Outlined.AccountBalanceWallet,
                    "Seller Hub",
                    if (profile.isSellerActive) "Store active: ${profile.sellerStoreName}" else "Activate your seller profile",
                    if (profile.isSellerActive) "Active" else "Setup",
                    onClick = { onDismiss(); onOpenBecomeSeller() }
                )
            }

            AccordionSection("Experience & Activity", expandedSections.contains("Experience & Activity"), { toggleSection("Experience & Activity") }) {
                ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)
                MenuItemRow(
                    Icons.Outlined.Notifications,
                    "Campus Notifications",
                    "Likes, comments, replies and market activity",
                    onClick = { onDismiss(); onOpenActivity() }
                )
                MenuItemRow(
                    Icons.Outlined.EmojiEvents,
                    "Leaderboard & Streaks",
                    "Live campus rankings, points and streaks",
                    onClick = { onDismiss(); onOpenLeaderboard() }
                )
            }

            AccordionSection("Session", expandedSections.contains("Session"), { toggleSection("Session") }) {
                MenuItemRow(
                    Icons.Outlined.SwitchAccount,
                    "Switch Account",
                    "Recently logged in accounts",
                    onClick = {
                        onDismiss()
                        context.startActivity(Intent(context, AccountSwitcherActivity::class.java))
                    }
                )
                MenuItemRow(
                    Icons.AutoMirrored.Filled.Logout,
                    "Log Out",
                    "End your active session securely",
                    iconColor = Color(0xFFEF4444),
                    titleColor = Color(0xFFEF4444),
                    onClick = { onDismiss(); onLogout() }
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(profile: UserProfile, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "headerPressScale")
    var avatarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(90); avatarVisible = true }
    val avatarScale by animateFloatAsState(if (avatarVisible) 1f else 0.5f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "avatarSpring")
    val pulse = rememberInfiniteTransition(label = "onlinePulse")
    val pulseScale by pulse.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseScale")
    val pulseAlpha by pulse.animateFloat(0.55f, 0.15f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseAlpha")
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().scale(pressScale).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true; onClick() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(model = profile.avatarUrl, contentDescription = "My Avatar", contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).scale(avatarScale).clip(CircleShape))
                if (profile.onlineNow) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Box(modifier = Modifier.size(14.dp).scale(pulseScale).background(Color(0xFF22C55E).copy(alpha = pulseAlpha), CircleShape))
                        Box(modifier = Modifier.size(14.dp).background(Color(0xFF22C55E), CircleShape))
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    AnimatedVisibility(visible = profile.verificationBadge != VerificationBadge.NONE, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) { VerifiedMark(badge = profile.verificationBadge, size = 16.dp) }
                }
                Text("@${profile.username} • ${profile.faculty}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${profile.followerCount} followers • ${profile.university}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Go to Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
    LaunchedEffect(pressed) { if (pressed) { delay(140); pressed = false } }
}

@Composable
private fun AccordionSection(title: String, expanded: Boolean, onToggle: () -> Unit, revealDelayBase: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, MenuMotion.ChevronSpec, label = "chevronRotation")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), letterSpacing = 0.8.sp)
            Icon(Icons.Default.ChevronRight, contentDescription = if (expanded) "Collapse $title" else "Expand $title", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(16.dp).rotate(chevronRotation))
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(220)), exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(140))) {
            StaggeredReveal(active = expanded, delayBase = revealDelayBase) { Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() } }
        }
    }
}

@Composable
private fun StaggeredReveal(active: Boolean, delayBase: Int, content: @Composable () -> Unit) {
    var visible by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) { delay(delayBase.toLong()); visible = true } else visible = false }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)) + slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing), initialOffsetY = { it / 12 })) { content() }
}

@Composable
private fun ThemeToggleRow(isDark: Boolean, onToggleTheme: () -> Unit) {
    val iconBg by animateColorAsState(if (isDark) Color(0xFF2A2035) else Color(0xFFF3E8FF), tween(260), label = "themeIconBg")
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggleTheme() }.padding(vertical = 10.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = isDark, transitionSpec = { (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()).togetherWith(fadeOut(tween(120))) }, label = "themeIcon") { dark -> Icon(if (dark) Icons.Filled.DarkMode else Icons.Filled.LightMode, contentDescription = "Theme", tint = BlinkPink, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Appearance Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(if (isDark) "Dark Mode (Vibrant Cyber)" else "Light Mode (Clean Campus)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = isDark, onCheckedChange = { onToggleTheme() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BlinkPink))
    }
}

@Composable
private fun MenuItemRow(icon: ImageVector, title: String, subtitle: String, trailingText: String? = null, iconColor: Color = BlinkPink, titleColor: Color = Color.Unspecified, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val rowScale by animateFloatAsState(if (pressed) 0.985f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "rowPressScale")
    val rowAlpha by animateFloatAsState(if (pressed) 0.7f else 1f, tween(100), label = "rowPressAlpha")
    Row(modifier = Modifier.fillMaxWidth().scale(rowScale).clip(RoundedCornerShape(12.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true; onClick() }.padding(vertical = 10.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).graphicsLayer { alpha = rowAlpha }.background(iconColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(20.dp)) }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = rowAlpha }) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingText != null) {
            Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = 0.15f), modifier = Modifier.padding(start = 8.dp)) { Text(trailingText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlinkPink, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
        } else {
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
        }
    }
    LaunchedEffect(pressed) { if (pressed) { delay(130); pressed = false } }
}

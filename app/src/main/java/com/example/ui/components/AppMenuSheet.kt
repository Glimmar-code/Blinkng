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
    onOpenMarket: () -> Unit,
    onOpenPostItem: () -> Unit,
    onOpenBecomeSeller: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenActivity: () -> Unit,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onShowToast: (String) -> Unit,
    onSimulateNotification: () -> Unit,
    onOpenStudyGroups: () -> Unit = { onShowToast("Opening Study Groups…") },
    onOpenCourseMaterials: () -> Unit = { onShowToast("Opening Course Materials…") },
    onOpenTimetable: () -> Unit = { onShowToast("Syncing your timetable…") },
    onOpenAssignments: () -> Unit = { onShowToast("Opening Assignment Reminders…") },
    onOpenCampusEvents: () -> Unit = { onShowToast("Opening Campus Events…") },
    onOpenWallet: () -> Unit = { onShowToast("Opening Blink Wallet…") },
    onFundWallet: () -> Unit = { onShowToast("Opening top-up options…") },
    onWithdrawFunds: () -> Unit = { onShowToast("Opening withdrawal…") },
    onOpenTransactionHistory: () -> Unit = { onShowToast("Opening transaction history…") },
    onOpenReferralEarnings: () -> Unit = { onShowToast("Opening referral earnings…") },
    onOpenContentStudio: () -> Unit = { onShowToast("Opening Content Studio…") },
    onOpenPostInsights: () -> Unit = { onShowToast("Opening Post Insights…") },
    onOpenMonetization: () -> Unit = { onShowToast("Opening Monetization…") },
    onOpenSavedCollections: () -> Unit = { onShowToast("Opening Saved Collections…") },
    onOpenDrafts: () -> Unit = { onShowToast("Opening Drafts…") },
    onOpenPrivacySettings: () -> Unit = { onShowToast("Opening Privacy Settings…") },
    onOpenBlockedAccounts: () -> Unit = { onShowToast("Opening Blocked Accounts…") },
    onOpenLoginSecurity: () -> Unit = { onShowToast("Opening Login & Security…") },
    onOpenDataStorage: () -> Unit = { onShowToast("Opening Data & Storage…") },
    onReportProblem: () -> Unit = { onShowToast("Opening problem report form…") },
    onOpenHelpCenter: () -> Unit = { onShowToast("Opening Help Center…") },
    onContactSupport: () -> Unit = { onShowToast("Opening Support chat…") },
    onOpenCommunityGuidelines: () -> Unit = { onShowToast("Opening Community Guidelines…") },
    onSendFeedback: () -> Unit = { onShowToast("Opening Feedback form…") },
    onRateApp: () -> Unit = { onShowToast("Thanks! Opening app store…") },
    onOpenTerms: () -> Unit = { onShowToast("Opening Terms of Service…") },
    onOpenPrivacyPolicy: () -> Unit = { onShowToast("Opening Privacy Policy…") },
    onOpenAbout: () -> Unit = { onShowToast("Opening About Blink…") },
    onOpenLanguageSettings: () -> Unit = { onShowToast("Opening Language settings…") },
    onDeleteAccount: () -> Unit = { onShowToast("Opening account deletion flow…") }
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
            mutableStateOf(setOf("Profile & Campus Identity", "Aluta Campus Market", "Experience & Appearance", "Session"))
        }
        fun toggleSection(title: String) { expandedSections = if (expandedSections.contains(title)) expandedSections - title else expandedSections + title }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AnimatedVisibility(visible = contentVisible, enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(340, easing = FastOutSlowInEasing), initialOffsetY = { -it / 6 })) {
                ProfileHeaderCard(profile = profile, onClick = { onDismiss(); onViewProfile() })
            }
            Spacer(modifier = Modifier.height(4.dp))
            AccordionSection("Profile & Campus Identity", expandedSections.contains("Profile & Campus Identity"), { toggleSection("Profile & Campus Identity") }, 40) {
                MenuItemRow(Icons.Outlined.Person, "View My Full Profile", "Badges, skills, endorsements & portfolio", onClick = { onDismiss(); onViewProfile() })
                MenuItemRow(Icons.Outlined.Edit, "Edit Profile & Bio", "Update contact, academic level & socials", onClick = { onDismiss(); onEditProfile() })
                MenuItemRow(Icons.Outlined.Verified, "Campus Verification", if (profile.verificationBadge != VerificationBadge.NONE) "Verified Campus Member" else "Get Verified Badge", if (profile.verificationBadge != VerificationBadge.NONE) "Active" else "Apply", onClick = { onShowToast("Student Verification status: ACTIVE ✦") })
            }
            AccordionSection("Aluta Campus Market", expandedSections.contains("Aluta Campus Market"), { toggleSection("Aluta Campus Market") }) {
                MenuItemRow(Icons.Outlined.Storefront, "Browse Marketplace", "Books, electronics, hostel gear & fashion", onClick = { onDismiss(); onOpenMarket() })
                MenuItemRow(Icons.Outlined.AddShoppingCart, "Post Item for Sale", "List your gear on campus with direct WhatsApp", onClick = { onDismiss(); onOpenPostItem() })
                MenuItemRow(Icons.Outlined.AccountBalanceWallet, "Seller Hub & Paystack Escrow", if (profile.isSellerActive) "Store Active: ${profile.sellerStoreName}" else "Activate Merchant Account (₦2,500)", if (profile.isSellerActive) "Verified" else "Upgrade", onClick = { onDismiss(); onOpenBecomeSeller() })
            }
            AccordionSection("Academics & Study Tools", expandedSections.contains("Academics & Study Tools"), { toggleSection("Academics & Study Tools") }) {
                MenuItemRow(Icons.Outlined.Groups, "Study Groups", "Join or create course-based study groups", onClick = onOpenStudyGroups)
                MenuItemRow(Icons.Outlined.MenuBook, "Course Materials", "Shared notes, past questions & slides", onClick = onOpenCourseMaterials)
                MenuItemRow(Icons.Outlined.CalendarMonth, "Timetable Sync", "Sync your class schedule to your calendar", onClick = onOpenTimetable)
                MenuItemRow(Icons.Outlined.Assignment, "Assignment Reminders", "Never miss a submission deadline", onClick = onOpenAssignments)
                MenuItemRow(Icons.Outlined.Celebration, "Campus Events", "RSVP to workshops, socials & hackathons", onClick = onOpenCampusEvents)
            }
            AccordionSection("Blink Wallet & Finance", expandedSections.contains("Blink Wallet & Finance"), { toggleSection("Blink Wallet & Finance") }) {
                MenuItemRow(Icons.Outlined.AccountBalance, "Blink Wallet", "View your balance and recent activity", onClick = onOpenWallet)
                MenuItemRow(Icons.Outlined.AddCard, "Fund Wallet", "Top up via card, bank transfer or USSD", onClick = onFundWallet)
                MenuItemRow(Icons.Outlined.Payments, "Withdraw Funds", "Cash out to your linked bank account", onClick = onWithdrawFunds)
                MenuItemRow(Icons.Outlined.Receipt, "Transaction History", "Full record of purchases and payouts", onClick = onOpenTransactionHistory)
                MenuItemRow(Icons.Outlined.CardGiftcard, "Referral Earnings", "Track bonus points earned from invites", onClick = onOpenReferralEarnings)
            }
            AccordionSection("Creator & Content Tools", expandedSections.contains("Creator & Content Tools"), { toggleSection("Creator & Content Tools") }) {
                MenuItemRow(Icons.Outlined.Dashboard, "Content Studio", "Manage your posts, drafts and analytics", onClick = onOpenContentStudio)
                MenuItemRow(Icons.Outlined.BarChart, "Post Insights", "See views, engagement and reach", onClick = onOpenPostInsights)
                MenuItemRow(Icons.Outlined.MonetizationOn, "Monetization", "Apply for creator payouts", "New", onClick = onOpenMonetization)
                MenuItemRow(Icons.Outlined.Bookmarks, "Saved Collections", "Organize saved posts into folders", onClick = onOpenSavedCollections)
                MenuItemRow(Icons.Outlined.Drafts, "Drafts", "Resume posts you haven't published yet", onClick = onOpenDrafts)
            }
            AccordionSection("Experience & Appearance", expandedSections.contains("Experience & Appearance"), { toggleSection("Experience & Appearance") }) {
                ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)
                MenuItemRow(Icons.Outlined.Notifications, "Campus Notifications", "Mentions, likes, orders & announcements", onClick = { onDismiss(); onOpenActivity() })
                MenuItemRow(Icons.Outlined.Language, "Language", "Change your app display language", "English", onClick = onOpenLanguageSettings)
            }
            AccordionSection("Community & Safety", expandedSections.contains("Community & Safety"), { toggleSection("Community & Safety") }) {
                MenuItemRow(Icons.Outlined.EmojiEvents, "Leaderboard & Streaks", "Campus rankings and top contributor scores", onClick = { onDismiss(); onOpenLeaderboard() })
                MenuItemRow(Icons.Outlined.Shield, "Aluta Safety & Protection", "Campus trust guidelines & verified meetups", onClick = { onShowToast("Aluta Safety: Always meet in well-lit public campus locations.") })
                MenuItemRow(Icons.Outlined.Share, "Invite Classmates", "Earn 500 bonus rank points per student", onClick = { onShowToast("Invitation link copied to clipboard!") })
            }
            AccordionSection("Privacy & Security", expandedSections.contains("Privacy & Security"), { toggleSection("Privacy & Security") }) {
                MenuItemRow(Icons.Outlined.Lock, "Privacy Settings", "Control who sees your profile & posts", onClick = onOpenPrivacySettings)
                MenuItemRow(Icons.Outlined.Block, "Blocked Accounts", "Manage users you've blocked", onClick = onOpenBlockedAccounts)
                MenuItemRow(Icons.Outlined.Security, "Login & Security", "Password, two-factor auth & sessions", onClick = onOpenLoginSecurity)
                MenuItemRow(Icons.Outlined.Storage, "Data & Storage", "Manage cache, downloads and data usage", onClick = onOpenDataStorage)
                MenuItemRow(Icons.Outlined.ReportProblem, "Report a Problem", "Flag bugs or abuse directly to the team", onClick = onReportProblem)
            }
            AccordionSection("Help & Support", expandedSections.contains("Help & Support"), { toggleSection("Help & Support") }) {
                MenuItemRow(Icons.Outlined.HelpOutline, "Help Center", "FAQs and how-to guides", onClick = onOpenHelpCenter)
                MenuItemRow(Icons.Outlined.Chat, "Contact Support", "Chat with the Aluta support team", onClick = onContactSupport)
                MenuItemRow(Icons.Outlined.Gavel, "Community Guidelines", "Rules for a respectful campus", onClick = onOpenCommunityGuidelines)
                MenuItemRow(Icons.Outlined.Feedback, "Send Feedback", "Suggest features or improvements", onClick = onSendFeedback)
                MenuItemRow(Icons.Outlined.StarRate, "Rate Blink", "Leave a review on the app store", onClick = onRateApp)
            }
            AccordionSection("Legal & About", expandedSections.contains("Legal & About"), { toggleSection("Legal & About") }) {
                MenuItemRow(Icons.Outlined.Description, "Terms of Service", "The rules for using Blink", onClick = onOpenTerms)
                MenuItemRow(Icons.Outlined.PrivacyTip, "Privacy Policy", "How your data is collected and used", onClick = onOpenPrivacyPolicy)
                MenuItemRow(Icons.Outlined.Info, "About Blink", "Version, credits and the team", onClick = onOpenAbout)
                MenuItemRow(Icons.Outlined.DeleteForever, "Delete Account", "Permanently remove your account and data", iconColor = Color(0xFFEF4444), titleColor = Color(0xFFEF4444), onClick = onDeleteAccount)
            }
            AccordionSection("Session", expandedSections.contains("Session"), { toggleSection("Session") }) {
                MenuItemRow(Icons.Outlined.SwitchAccount, "Switch Account", "Recently logged in accounts", onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, AccountSwitcherActivity::class.java))
                })
                MenuItemRow(Icons.Outlined.NotificationsActive, "Test Real-Life Notification", "Disabled — real persisted notifications are used", iconColor = BlinkPink, onClick = { onShowToast("The fake notification test has been disabled. Real notifications are active.") })
                MenuItemRow(Icons.AutoMirrored.Filled.Logout, "Log Out", "End your active session securely", iconColor = Color(0xFFEF4444), titleColor = Color(0xFFEF4444), onClick = { onDismiss(); onLogout() })
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

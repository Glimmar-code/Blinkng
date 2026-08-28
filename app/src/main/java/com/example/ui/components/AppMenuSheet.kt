package com.example.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay

/* ============================================================================
 * APP MENU SHEET
 *
 * A full campus-app menu: identity, marketplace, academics, wallet, creator
 * tools, appearance, community, privacy, support, legal, and session — all
 * organized into collapsible, animated sections so the sheet stays scannable
 * even with 40+ destinations.
 *
 * Motion language:
 *  - Sections are accordions: chevron rotates, content expands/collapses
 *    with a spring, and rows inside stagger in with a short fade + rise.
 *  - The profile header springs in on open and gives tactile press feedback.
 *  - The online dot has a soft, looping pulse.
 *  - The theme switch cross-fades its icon and rotates it in.
 *  - Every row gives a quick scale/alpha "press" cue on tap.
 * ==========================================================================*/

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
    // Academics
    onOpenStudyGroups: () -> Unit = { onShowToast("Opening Study Groups…") },
    onOpenCourseMaterials: () -> Unit = { onShowToast("Opening Course Materials…") },
    onOpenTimetable: () -> Unit = { onShowToast("Syncing your timetable…") },
    onOpenAssignments: () -> Unit = { onShowToast("Opening Assignment Reminders…") },
    onOpenCampusEvents: () -> Unit = { onShowToast("Opening Campus Events…") },
    // Wallet & finance
    onOpenWallet: () -> Unit = { onShowToast("Opening Blink Wallet…") },
    onFundWallet: () -> Unit = { onShowToast("Opening top-up options…") },
    onWithdrawFunds: () -> Unit = { onShowToast("Opening withdrawal…") },
    onOpenTransactionHistory: () -> Unit = { onShowToast("Opening transaction history…") },
    onOpenReferralEarnings: () -> Unit = { onShowToast("Opening referral earnings…") },
    // Creator tools
    onOpenContentStudio: () -> Unit = { onShowToast("Opening Content Studio…") },
    onOpenPostInsights: () -> Unit = { onShowToast("Opening Post Insights…") },
    onOpenMonetization: () -> Unit = { onShowToast("Opening Monetization…") },
    onOpenSavedCollections: () -> Unit = { onShowToast("Opening Saved Collections…") },
    onOpenDrafts: () -> Unit = { onShowToast("Opening Drafts…") },
    // Privacy & security
    onOpenPrivacySettings: () -> Unit = { onShowToast("Opening Privacy Settings…") },
    onOpenBlockedAccounts: () -> Unit = { onShowToast("Opening Blocked Accounts…") },
    onOpenLoginSecurity: () -> Unit = { onShowToast("Opening Login & Security…") },
    onOpenDataStorage: () -> Unit = { onShowToast("Opening Data & Storage…") },
    onReportProblem: () -> Unit = { onShowToast("Opening problem report form…") },
    // Help & support
    onOpenHelpCenter: () -> Unit = { onShowToast("Opening Help Center…") },
    onContactSupport: () -> Unit = { onShowToast("Opening Support chat…") },
    onOpenCommunityGuidelines: () -> Unit = { onShowToast("Opening Community Guidelines…") },
    onSendFeedback: () -> Unit = { onShowToast("Opening Feedback form…") },
    onRateApp: () -> Unit = { onShowToast("Thanks! Opening app store…") },
    // Legal & about
    onOpenTerms: () -> Unit = { onShowToast("Opening Terms of Service…") },
    onOpenPrivacyPolicy: () -> Unit = { onShowToast("Opening Privacy Policy…") },
    onOpenAbout: () -> Unit = { onShowToast("Opening About Blink…") },
    onOpenLanguageSettings: () -> Unit = { onShowToast("Opening Language settings…") },
    onDeleteAccount: () -> Unit = { onShowToast("Opening account deletion flow…") }
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0x40FFFFFF) else Color(0x30000000)
            )
        },
        modifier = Modifier.testTag("app_menu_sheet")
    ) {
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(30)
            contentVisible = true
        }

        // Sections expanded by default — keep the sheet approachable at first glance.
        var expandedSections by rememberSaveable {
            mutableStateOf(
                setOf(
                    "Profile & Campus Identity",
                    "Aluta Campus Market",
                    "Experience & Appearance",
                    "Session"
                )
            )
        }

        fun toggleSection(title: String) {
            expandedSections = if (expandedSections.contains(title)) {
                expandedSections - title
            } else {
                expandedSections + title
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ============================================================
            // PROFILE HEADER CARD
            // ============================================================
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(300)) + slideInVertically(
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it / 6 }
                )
            ) {
                ProfileHeaderCard(
                    profile = profile,
                    onClick = {
                        onDismiss()
                        onViewProfile()
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ============================================================
            // SECTION 1 — PROFILE & IDENTITY
            // ============================================================
            AccordionSection(
                title = "Profile & Campus Identity",
                expanded = expandedSections.contains("Profile & Campus Identity"),
                onToggle = { toggleSection("Profile & Campus Identity") },
                revealDelayBase = 40
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Person,
                    title = "View My Full Profile",
                    subtitle = "Badges, skills, endorsements & portfolio",
                    onClick = {
                        onDismiss()
                        onViewProfile()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.Edit,
                    title = "Edit Profile & Bio",
                    subtitle = "Update contact, academic level & socials",
                    onClick = {
                        onDismiss()
                        onEditProfile()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.Verified,
                    title = "Campus Verification",
                    subtitle = if (profile.verificationBadge != VerificationBadge.NONE) "Verified Campus Member" else "Get Verified Badge",
                    trailingText = if (profile.verificationBadge != VerificationBadge.NONE) "Active" else "Apply",
                    onClick = { onShowToast("Student Verification status: ACTIVE ✦") }
                )
            }

            // ============================================================
            // SECTION 2 — MARKETPLACE
            // ============================================================
            AccordionSection(
                title = "Aluta Campus Market",
                expanded = expandedSections.contains("Aluta Campus Market"),
                onToggle = { toggleSection("Aluta Campus Market") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Storefront,
                    title = "Browse Marketplace",
                    subtitle = "Books, electronics, hostel gear & fashion",
                    onClick = {
                        onDismiss()
                        onOpenMarket()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.AddShoppingCart,
                    title = "Post Item for Sale",
                    subtitle = "List your gear on campus with direct WhatsApp",
                    onClick = {
                        onDismiss()
                        onOpenPostItem()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "Seller Hub & Paystack Escrow",
                    subtitle = if (profile.isSellerActive) "Store Active: ${profile.sellerStoreName}" else "Activate Merchant Account (₦2,500)",
                    trailingText = if (profile.isSellerActive) "Verified" else "Upgrade",
                    onClick = {
                        onDismiss()
                        onOpenBecomeSeller()
                    }
                )
            }

            // ============================================================
            // SECTION 3 — ACADEMICS & STUDY TOOLS (new)
            // ============================================================
            AccordionSection(
                title = "Academics & Study Tools",
                expanded = expandedSections.contains("Academics & Study Tools"),
                onToggle = { toggleSection("Academics & Study Tools") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Groups,
                    title = "Study Groups",
                    subtitle = "Join or create course-based study groups",
                    onClick = onOpenStudyGroups
                )
                MenuItemRow(
                    icon = Icons.Outlined.MenuBook,
                    title = "Course Materials",
                    subtitle = "Shared notes, past questions & slides",
                    onClick = onOpenCourseMaterials
                )
                MenuItemRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Timetable Sync",
                    subtitle = "Sync your class schedule to your calendar",
                    onClick = onOpenTimetable
                )
                MenuItemRow(
                    icon = Icons.Outlined.Assignment,
                    title = "Assignment Reminders",
                    subtitle = "Never miss a submission deadline",
                    onClick = onOpenAssignments
                )
                MenuItemRow(
                    icon = Icons.Outlined.Celebration,
                    title = "Campus Events",
                    subtitle = "RSVP to workshops, socials & hackathons",
                    onClick = onOpenCampusEvents
                )
            }

            // ============================================================
            // SECTION 4 — WALLET & FINANCE (new)
            // ============================================================
            AccordionSection(
                title = "Blink Wallet & Finance",
                expanded = expandedSections.contains("Blink Wallet & Finance"),
                onToggle = { toggleSection("Blink Wallet & Finance") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.AccountBalance,
                    title = "Blink Wallet",
                    subtitle = "View your balance and recent activity",
                    onClick = onOpenWallet
                )
                MenuItemRow(
                    icon = Icons.Outlined.AddCard,
                    title = "Fund Wallet",
                    subtitle = "Top up via card, bank transfer or USSD",
                    onClick = onFundWallet
                )
                MenuItemRow(
                    icon = Icons.Outlined.Payments,
                    title = "Withdraw Funds",
                    subtitle = "Cash out to your linked bank account",
                    onClick = onWithdrawFunds
                )
                MenuItemRow(
                    icon = Icons.Outlined.Receipt,
                    title = "Transaction History",
                    subtitle = "Full record of purchases and payouts",
                    onClick = onOpenTransactionHistory
                )
                MenuItemRow(
                    icon = Icons.Outlined.CardGiftcard,
                    title = "Referral Earnings",
                    subtitle = "Track bonus points earned from invites",
                    onClick = onOpenReferralEarnings
                )
            }

            // ============================================================
            // SECTION 5 — CREATOR & CONTENT TOOLS (new)
            // ============================================================
            AccordionSection(
                title = "Creator & Content Tools",
                expanded = expandedSections.contains("Creator & Content Tools"),
                onToggle = { toggleSection("Creator & Content Tools") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Dashboard,
                    title = "Content Studio",
                    subtitle = "Manage your posts, drafts and analytics",
                    onClick = onOpenContentStudio
                )
                MenuItemRow(
                    icon = Icons.Outlined.BarChart,
                    title = "Post Insights",
                    subtitle = "See views, engagement and reach",
                    onClick = onOpenPostInsights
                )
                MenuItemRow(
                    icon = Icons.Outlined.MonetizationOn,
                    title = "Monetization",
                    subtitle = "Apply for creator payouts",
                    trailingText = "New",
                    onClick = onOpenMonetization
                )
                MenuItemRow(
                    icon = Icons.Outlined.Bookmarks,
                    title = "Saved Collections",
                    subtitle = "Organize saved posts into folders",
                    onClick = onOpenSavedCollections
                )
                MenuItemRow(
                    icon = Icons.Outlined.Drafts,
                    title = "Drafts",
                    subtitle = "Resume posts you haven't published yet",
                    onClick = onOpenDrafts
                )
            }

            // ============================================================
            // SECTION 6 — EXPERIENCE & APPEARANCE
            // ============================================================
            AccordionSection(
                title = "Experience & Appearance",
                expanded = expandedSections.contains("Experience & Appearance"),
                onToggle = { toggleSection("Experience & Appearance") }
            ) {
                ThemeToggleRow(isDark = isDark, onToggleTheme = onToggleTheme)

                MenuItemRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Campus Notifications",
                    subtitle = "Mentions, likes, orders & announcements",
                    onClick = {
                        onDismiss()
                        onOpenActivity()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.Language,
                    title = "Language",
                    subtitle = "Change your app display language",
                    trailingText = "English",
                    onClick = onOpenLanguageSettings
                )
            }

            // ============================================================
            // SECTION 7 — COMMUNITY & SAFETY
            // ============================================================
            AccordionSection(
                title = "Community & Safety",
                expanded = expandedSections.contains("Community & Safety"),
                onToggle = { toggleSection("Community & Safety") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "Leaderboard & Streaks",
                    subtitle = "Campus rankings and top contributor scores",
                    onClick = {
                        onDismiss()
                        onOpenLeaderboard()
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.Shield,
                    title = "Aluta Safety & Protection",
                    subtitle = "Campus trust guidelines & verified meetups",
                    onClick = { onShowToast("Aluta Safety: Always meet in well-lit public campus locations.") }
                )
                MenuItemRow(
                    icon = Icons.Outlined.Share,
                    title = "Invite Classmates",
                    subtitle = "Earn 500 bonus rank points per student",
                    onClick = { onShowToast("Invitation link copied to clipboard!") }
                )
            }

            // ============================================================
            // SECTION 8 — PRIVACY & SECURITY (new)
            // ============================================================
            AccordionSection(
                title = "Privacy & Security",
                expanded = expandedSections.contains("Privacy & Security"),
                onToggle = { toggleSection("Privacy & Security") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy Settings",
                    subtitle = "Control who sees your profile & posts",
                    onClick = onOpenPrivacySettings
                )
                MenuItemRow(
                    icon = Icons.Outlined.Block,
                    title = "Blocked Accounts",
                    subtitle = "Manage users you've blocked",
                    onClick = onOpenBlockedAccounts
                )
                MenuItemRow(
                    icon = Icons.Outlined.Security,
                    title = "Login & Security",
                    subtitle = "Password, two-factor auth & sessions",
                    onClick = onOpenLoginSecurity
                )
                MenuItemRow(
                    icon = Icons.Outlined.Storage,
                    title = "Data & Storage",
                    subtitle = "Manage cache, downloads and data usage",
                    onClick = onOpenDataStorage
                )
                MenuItemRow(
                    icon = Icons.Outlined.ReportProblem,
                    title = "Report a Problem",
                    subtitle = "Flag bugs or abuse directly to the team",
                    onClick = onReportProblem
                )
            }

            // ============================================================
            // SECTION 9 — HELP & SUPPORT (new)
            // ============================================================
            AccordionSection(
                title = "Help & Support",
                expanded = expandedSections.contains("Help & Support"),
                onToggle = { toggleSection("Help & Support") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.HelpOutline,
                    title = "Help Center",
                    subtitle = "FAQs and how-to guides",
                    onClick = onOpenHelpCenter
                )
                MenuItemRow(
                    icon = Icons.Outlined.Chat,
                    title = "Contact Support",
                    subtitle = "Chat with the Aluta support team",
                    onClick = onContactSupport
                )
                MenuItemRow(
                    icon = Icons.Outlined.Gavel,
                    title = "Community Guidelines",
                    subtitle = "Rules for a respectful campus",
                    onClick = onOpenCommunityGuidelines
                )
                MenuItemRow(
                    icon = Icons.Outlined.Feedback,
                    title = "Send Feedback",
                    subtitle = "Suggest features or improvements",
                    onClick = onSendFeedback
                )
                MenuItemRow(
                    icon = Icons.Outlined.StarRate,
                    title = "Rate Blink",
                    subtitle = "Leave a review on the app store",
                    onClick = onRateApp
                )
            }

            // ============================================================
            // SECTION 10 — LEGAL & ABOUT (new)
            // ============================================================
            AccordionSection(
                title = "Legal & About",
                expanded = expandedSections.contains("Legal & About"),
                onToggle = { toggleSection("Legal & About") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.Description,
                    title = "Terms of Service",
                    subtitle = "The rules for using Blink",
                    onClick = onOpenTerms
                )
                MenuItemRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How your data is collected and used",
                    onClick = onOpenPrivacyPolicy
                )
                MenuItemRow(
                    icon = Icons.Outlined.Info,
                    title = "About Blink",
                    subtitle = "Version, credits and the team",
                    onClick = onOpenAbout
                )
                MenuItemRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = "Delete Account",
                    subtitle = "Permanently remove your account and data",
                    iconColor = Color(0xFFEF4444),
                    titleColor = Color(0xFFEF4444),
                    onClick = onDeleteAccount
                )
            }

            // ============================================================
            // SECTION 11 — SESSION
            // ============================================================
            AccordionSection(
                title = "Session",
                expanded = expandedSections.contains("Session"),
                onToggle = { toggleSection("Session") }
            ) {
                MenuItemRow(
                    icon = Icons.Outlined.SwitchAccount,
                    title = "Switch Account",
                    subtitle = "Login to another student profile",
                    onClick = {
                        onDismiss()
                        onShowToast("Account switch triggered")
                    }
                )
                MenuItemRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Test Real-Life Notification",
                    subtitle = "Simulate a push notification when offline",
                    iconColor = BlinkPink,
                    onClick = {
                        onDismiss()
                        onSimulateNotification()
                    }
                )
                MenuItemRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Log Out",
                    subtitle = "End your active session securely",
                    iconColor = Color(0xFFEF4444),
                    titleColor = Color(0xFFEF4444),
                    onClick = {
                        onDismiss()
                        onLogout()
                    }
                )
            }
        }
    }
}

// =====================================================================
// PROFILE HEADER CARD
// =====================================================================

@Composable
private fun ProfileHeaderCard(
    profile: UserProfile,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "headerPressScale"
    )

    var avatarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90)
        avatarVisible = true
    }
    val avatarScale by animateFloatAsState(
        targetValue = if (avatarVisible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "avatarSpring"
    )

    val pulse = rememberInfiniteTransition(label = "onlinePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isDarkSurface = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = isDarkSurface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = "My Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .scale(avatarScale)
                        .clip(CircleShape)
                )
                if (profile.onlineNow) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .background(Color(0xFF22C55E).copy(alpha = pulseAlpha), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AnimatedVisibility(
                        visible = profile.verificationBadge != VerificationBadge.NONE,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                    ) {
                        VerifiedMark(badge = profile.verificationBadge, size = 16.dp)
                    }
                }
                Text(
                    text = "@${profile.username} • ${profile.faculty}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${profile.followerCount} followers • ${profile.university}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            val arrowOffset by animateFloatAsState(
                targetValue = if (pressed) 3f else 0f,
                animationSpec = tween(120),
                label = "arrowNudge"
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Go to Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { translationX = arrowOffset }
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(140)
            pressed = false
        }
    }
}

// =====================================================================
// ACCORDION SECTION
// =====================================================================

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    revealDelayBase: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MenuMotion.ChevronSpec,
        label = "chevronRotation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle() }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.8.sp
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                fadeOut(tween(140))
        ) {
            StaggeredReveal(active = expanded, delayBase = revealDelayBase) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Fades the whole revealed block in once, giving the accordion a soft
 * "settle" instead of popping content in the instant it expands.
 */
@Composable
private fun StaggeredReveal(
    active: Boolean,
    delayBase: Int,
    content: @Composable () -> Unit
) {
    var visible by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayBase.toLong())
            visible = true
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 12 }
        )
    ) {
        content()
    }
}

// =====================================================================
// THEME TOGGLE ROW
// =====================================================================

@Composable
private fun ThemeToggleRow(
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val iconBg by animateColorAsState(
        targetValue = if (isDark) Color(0xFF2A2035) else Color(0xFFF3E8FF),
        animationSpec = tween(260),
        label = "themeIconBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleTheme() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isDark,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                        fadeIn() +
                        (
                            if (targetState) {
                                slideInVertically { it / 2 }
                            } else {
                                slideInVertically { -it / 2 }
                            }
                            ))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "themeIcon"
            ) { dark ->
                Icon(
                    imageVector = if (dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    contentDescription = "Theme",
                    tint = BlinkPink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Appearance Theme",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            AnimatedContent(
                targetState = if (isDark) "Dark Mode (Vibrant Cyber)" else "Light Mode (Clean Campus)",
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "themeSubtitle"
            ) { label ->
                Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Switch(
            checked = isDark,
            onCheckedChange = { onToggleTheme() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BlinkPink
            )
        )
    }
}

// =====================================================================
// MENU ITEM ROW
// =====================================================================

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
    val rowScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rowPressScale"
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "rowPressAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                pressed = true
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { alpha = rowAlpha }
                .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).graphicsLayer { alpha = rowAlpha }) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailingText != null) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = BlinkPink.copy(alpha = 0.15f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = trailingText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlinkPink,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(13.dp)
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(130)
            pressed = false
        }
    }
}
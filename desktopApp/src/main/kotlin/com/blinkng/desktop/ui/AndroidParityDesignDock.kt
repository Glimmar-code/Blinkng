package com.blinkng.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AndroidParityModule(
    val key: String,
    val title: String,
    val source: String,
    val route: String,
    val group: String,
    val designSummary: String,
)

private data class AndroidParityLayer(
    val key: String,
    val title: String,
    val description: String,
)

private data class AndroidParityCheckpoint(
    val module: AndroidParityModule,
    val layer: AndroidParityLayer,
)

private val androidParityModules = listOf(
    AndroidParityModule("feed", "Feed", "PremiumFeedScreen.kt", "home", "Social", "Stories, feed tabs, premium header actions, refresh/offline states, rich post cards and Create Post entry"),
    AndroidParityModule("reels", "Reels", "VideoReelsScreen.kt", "reels", "Social", "For You controls, progress, mute state, caption expansion, like/save/comment actions and playback states"),
    AndroidParityModule("search", "Search", "PremiumSearchPhase3Screen.kt", "search", "Discovery", "Voice, visual, near-me, saved/following scopes, entities, filters and result sheets"),
    AndroidParityModule("messages", "Messages", "PremiumMessagesScreen.kt", "messages", "Communication", "Matches, search, pinned/starred, reply/edit, attachments, emoji, appearance and call controls"),
    AndroidParityModule("activity", "Activity", "ActivityScreen.kt", "notifications", "Notifications", "Search, unread/category filters, section headers, caught-up divider, offline and skeleton states"),
    AndroidParityModule("marketplace", "Marketplace", "MarketScreen.kt", "marketplace", "Commerce", "Market search, featured listings, seller state, verified selling and product cards"),
    AndroidParityModule("connect", "Connect Hub", "ConnectHubPremiumPanel.kt", "connect", "Discovery", "Smart Match, compatibility, roommates, mentoring, reading mates, housing and challenges"),
    AndroidParityModule("games", "Games", "GameSection.kt", "games", "Games", "Challenges, coin rewards, accept/play states and campus game discovery"),
    AndroidParityModule("scheduled-posts", "Scheduled posts", "CreatePostSheet.kt", "home", "Creator", "Drafts, scheduled preview, publish timing, cancel/retry and background posting state"),
    AndroidParityModule("blink-ai", "Blink AI", "BlinkAiSheet.kt", "home", "AI", "History, attachments, voice, settings, context/privacy, stop/retry and response states"),
    AndroidParityModule("blink-store", "Blink Store", "BlinkStoreActivity.kt", "store", "Economy", "Premium catalog, ownership states, Vault actions, use/apply flows and VIP presentation"),
    AndroidParityModule("vault", "Vault / Inventory", "BlinkEconomyModels.kt", "store", "Economy", "Owned inventory, expiry, activation, apply/remove and content-target selection"),
    AndroidParityModule("vip", "Blink VIP", "BlinkVipMark.kt", "profile", "Economy", "VIP identity, premium marks, collection presentation and profile cosmetics"),
    AndroidParityModule("boosts", "Boosts", "BlinkEconomyService.kt", "store", "Creator", "Post/Reel boosts, strength, durations, target selection and active status"),
    AndroidParityModule("digital-gifts", "Digital gifts", "BlinkEconomyService.kt", "store", "Economy", "Recipient selection, optional message, gift status and premium delivery feedback"),
    AndroidParityModule("account-switcher", "Account switcher", "AccountSwitcherActivity.kt", "settings", "Account", "Saved accounts, current identity, secure switching and session state"),
    AndroidParityModule("google-signin", "Google sign-in", "AuthScreens.kt", "settings", "Account", "Premium sign-in button, loading state, fallback and account continuity"),
    AndroidParityModule("password-recovery", "Password recovery", "ResetPasswordScreen.kt", "settings", "Account", "Forgot/reset password, validation, strength and completion states"),
    AndroidParityModule("remembered-login", "Remembered login", "AuthScreens.kt", "settings", "Account", "Remembered credentials preference, secure continuity and sign-out state"),
    AndroidParityModule("session-refresh", "Session refresh", "SupabaseSessionRefresher.kt", "settings", "Account", "Restoring session, retry, expired-session messaging and recovery"),
    AndroidParityModule("call-launcher", "Voice/video calls", "PremiumMessagesScreen.kt", "messages", "Communication", "Voice/video entry points, active call controls, timer and speaker state"),
    AndroidParityModule("call-history", "Call history", "CallHistoryActivity.kt", "messages", "Communication", "Incoming/outgoing/missed rows, callback affordance and empty states"),
    AndroidParityModule("call-realtime", "Realtime calls", "IncomingCallBannerActivity.kt", "messages", "Communication", "Incoming-call banner, caller identity, answer/decline and reconnect state"),
    AndroidParityModule("call-sounds", "Call sound preferences", "NotificationAndCallSettingsActivity.kt", "settings", "Communication", "Voice/video tone cards, vibration, preview and system-settings bridges"),
    AndroidParityModule("incoming-call-banner", "Incoming call banner", "IncomingCallBannerActivity.kt", "messages", "Communication", "Prominent incoming-call surface with call type, caller and actions"),
    AndroidParityModule("media-cache", "Media cache", "BlinkMediaCache.kt", "settings", "Offline", "Cached-media status, storage feedback and safe clear actions"),
    AndroidParityModule("offline-content", "Offline content", "OfflineConnectionBanner.kt", "home", "Offline", "No-internet banner, back-online state, cached content and refresh feedback"),
    AndroidParityModule("offline-mutation-queue", "Offline queue", "OfflineMutationQueue.kt", "home", "Offline", "Queued posts/messages, retry feedback and connection-restored state"),
    AndroidParityModule("persistent-drafts", "Persistent drafts", "CreatePostSheet.kt", "home", "Offline", "Draft cards, resume/delete and durable composer state"),
    AndroidParityModule("post-comments", "Comments", "CommentSheet.kt", "home", "Social", "Replies, mentions, like/report actions, avatars and empty states"),
    AndroidParityModule("post-options", "Post options", "PostOptionsMenuSheet.kt", "home", "Social", "Save, repost, share, report, delete and owner/non-owner action states"),
    AndroidParityModule("create-post", "Create post", "CreatePostSheet.kt", "home", "Creator", "Text/color text, photos, video, polls, settings, drafts and scheduling"),
    AndroidParityModule("story-bar", "Stories", "StoryBar.kt", "home", "Social", "Unseen rings, add story, counts, loading and empty treatment"),
    AndroidParityModule("story-viewer", "Story viewer", "StoryViewerDialog.kt", "home", "Social", "Progress, author info, reply, like, close and multi-story navigation"),
    AndroidParityModule("create-story", "Create story", "CreateStoryScreen.kt", "home", "Creator", "Photo/video selection, preview, caption and 24-hour messaging"),
    AndroidParityModule("verification", "Verification", "GetVerifiedSheet.kt", "profile", "Identity", "Blue/Gold cards, benefits, requirements, follower progress and payment choice"),
    AndroidParityModule("profile-edit", "Edit profile", "EditProfileScreen.kt", "profile", "Identity", "Avatar/cover editing, identity, academics, social links and completion"),
    AndroidParityModule("profile-follow", "Follow / interact", "ProfileFollowInteractButton.kt", "profile", "Identity", "Follow/interact menu, gifts, challenges, mentoring, roommate and study requests"),
    AndroidParityModule("profile-analytics", "Follower analytics", "FollowerGrowthChart.kt", "profile", "Creator", "Follower chart, growth metrics, target projection and verification milestone"),
    AndroidParityModule("seller", "Seller tools", "BecomeSellerScreen.kt", "marketplace", "Commerce", "Merchant onboarding, contact/location, trust policy and seller-success state"),
    AndroidParityModule("study-circles", "Study Circles", "StudyCirclesPanel.kt", "connect", "Campus", "Search, create, join/request, member counts and private/open states"),
    AndroidParityModule("professional-center", "Professional Center", "ProfessionalCenterActivity.kt", "settings", "Account", "Privacy, safety, orders, groups, data export, cache and danger zone"),
    AndroidParityModule("professional-search", "Professional search", "ProfessionalSearchScreen.kt", "search", "Discovery", "Discover cards, live filters, pinned result, people/posts/reels/hashtags and recents"),
    AndroidParityModule("leaderboard", "Leaderboard", "LeaderboardScreen.kt", "leaderboard", "Games", "Top 10, scopes, search, podium, personal rank, streak/coins and share"),
    AndroidParityModule("product-detail", "Product details", "ProductDetailScreen.kt", "marketplace", "Commerce", "Seller identity, rating, DM, Buy Now, escrow and Paystack affordances"),
    AndroidParityModule("notification-settings", "Notification settings", "NotificationAndCallSettingsActivity.kt", "settings", "Notifications", "Category controls, permissions, sounds, vibration, lock-screen and call settings"),
    AndroidParityModule("instant-chat-notifications", "Chat notifications", "InstantChatNotification.kt", "messages", "Notifications", "Unread badges, delivery/read semantics and duplicate-alert protection"),
    AndroidParityModule("deep-links", "Deep links", "DeepLinkRouter.kt", "settings", "Platform", "Safe route handoff, shared-post/profile targets and unavailable-content feedback"),
    AndroidParityModule("premium-feed", "Premium feed chrome", "PremiumFeedChrome.kt", "home", "Social", "Brand block, profile avatar, radial actions, tabs and floating Create Post control"),
    AndroidParityModule("premium-messages", "Premium messaging", "PremiumMessagesScreen.kt", "messages", "Communication", "Master/detail layout, theme surface, glass buttons, rings and premium message chrome"),
)

private val androidParityLayers = listOf(
    AndroidParityLayer("route", "Route", "The Android design is reachable from the equivalent desktop destination."),
    AndroidParityLayer("responsive", "Responsive", "The surface uses desktop-width composition without stretching phone geometry."),
    AndroidParityLayer("keyboard", "Keyboard", "Controls remain keyboard/mouse accessible and use desktop-native focus behavior."),
    AndroidParityLayer("state", "State", "Loading, empty, selected, disabled and completion states are represented."),
    AndroidParityLayer("realtime", "Realtime", "Live data feedback and refresh affordances are visible where the Android UI exposes them."),
    AndroidParityLayer("offline", "Offline", "Offline, retry and restored-connection states have desktop equivalents."),
    AndroidParityLayer("accessibility", "Accessibility", "Labels, readable contrast, focus order and reduced-motion expectations are preserved."),
    AndroidParityLayer("feedback", "Feedback", "User actions expose progress, success, failure and retry feedback."),
    AndroidParityLayer("analytics", "Metrics", "Counts, ranks, views, growth or status metrics remain visible where Android shows them."),
    AndroidParityLayer("bridge", "Platform bridge", "Android-only intents are replaced by the closest safe Windows-native action."),
)

private val androidParityCheckpoints: List<AndroidParityCheckpoint> =
    androidParityModules.flatMap { module -> androidParityLayers.map { layer -> AndroidParityCheckpoint(module, layer) } }

internal const val ANDROID_UI_PARITY_CHECKPOINT_COUNT = 500

@Composable
fun AndroidParityDesignDock(
    route: String,
    onNavigate: (String) -> Unit,
) {
    val routeModules = remember(route) { androidParityModules.filter { it.route == route } }
    if (routeModules.isEmpty()) return

    var selectedModule by remember(route) { mutableStateOf<AndroidParityModule?>(null) }
    var showCoverage by remember(route) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Android UI parity", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(
                        "${routeModules.size} Android design module${if (routeModules.size == 1) "" else "s"} mapped here · $ANDROID_UI_PARITY_CHECKPOINT_COUNT total checkpoints",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = { showCoverage = true }) { Text("Coverage") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                routeModules.forEach { module ->
                    AssistChip(
                        onClick = { selectedModule = module },
                        label = { Text(module.title, maxLines = 1) },
                    )
                }
                Spacer(Modifier.width(4.dp))
                AssistChip(
                    onClick = {
                        val target = when (route) {
                            "home" -> "search"
                            "profile" -> "store"
                            "settings" -> "profile"
                            else -> route
                        }
                        onNavigate(target)
                    },
                    label = { Text("Open related") },
                )
            }
        }
    }

    selectedModule?.let { module ->
        AlertDialog(
            onDismissRequest = { selectedModule = null },
            title = { Text(module.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(module.designSummary)
                    HorizontalDivider()
                    Text("Android source: ${module.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Parity dimensions", fontWeight = FontWeight.Bold)
                    androidParityLayers.forEach { layer ->
                        Text("• ${layer.title}: ${layer.description}", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedModule = null }) { Text("Done") } },
        )
    }

    if (showCoverage) {
        val routeCheckpoints = remember(route) { androidParityCheckpoints.filter { it.module.route == route } }
        AlertDialog(
            onDismissRequest = { showCoverage = false },
            title = { Text("UI parity coverage") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "$ANDROID_UI_PARITY_CHECKPOINT_COUNT checkpoints = ${androidParityModules.size} Android modules × ${androidParityLayers.size} parity dimensions.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    items(routeCheckpoints, key = { "${it.module.key}:${it.layer.key}" }) { checkpoint ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("${checkpoint.module.title} · ${checkpoint.layer.title}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(checkpoint.layer.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCoverage = false }) { Text("Close") } },
        )
    }
}

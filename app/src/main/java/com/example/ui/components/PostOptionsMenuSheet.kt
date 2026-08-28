package com.example.ui.components
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.heightIn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOptionsMenuSheet(
    post: FeedPost,
    isAuthor: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReport: (reason: String) -> Unit,
    onMuteUser: (username: String) -> Unit
) {
    val clipboardManager: ClipboardManager =
        LocalClipboardManager.current

    var showReportDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showDeleteConfirmDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showMuteConfirmDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showHideConfirmDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showShareFeedback by rememberSaveable {
        mutableStateOf(false)
    }

    var showCopiedFeedback by rememberSaveable {
        mutableStateOf(false)
    }

    var showSaveFeedback by rememberSaveable {
        mutableStateOf(false)
    }

    var notificationsEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var postTranslationEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var compactPreview by rememberSaveable {
        mutableStateOf(false)
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    LaunchedEffect(showShareFeedback) {
        if (showShareFeedback) {
            delay(1100)
            showShareFeedback = false
        }
    }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(1100)
            showCopiedFeedback = false
        }
    }

    LaunchedEffect(showSaveFeedback) {
        if (showSaveFeedback) {
            delay(1100)
            showSaveFeedback = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(
                        top = 10.dp,
                        bottom = 4.dp
                    )
                    .width(42.dp)
                    .height(4.dp)
                    .clip(
                        RoundedCornerShape(100.dp)
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .outlineVariant
                    )
            )
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 6.dp
                )
        ) {

            // ========================================================
            // HEADER
            // ========================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 4.dp,
                        vertical = 7.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = "Post actions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                    )

                    Text(
                        text =
                            "@${post.author} • ${post.timeAgo}",
                        fontSize = 10.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .testTag(
                                "post_options_close"
                            )
                ) {

                    Icon(
                        Icons.Default.Close,
                        contentDescription =
                            "Close post actions"
                    )
                }
            }

            // ========================================================
            // POST SNAPSHOT
            // ========================================================

            PostActionPreviewCard(
                post = post,
                compact = compactPreview,
                onCompactToggle = {
                    compactPreview =
                        !compactPreview
                }
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            // ========================================================
            // FEEDBACK BANNERS
            // ========================================================

            AnimatedVisibility(
                visible =
                    showShareFeedback ||
                            showCopiedFeedback ||
                            showSaveFeedback,
                enter =
                    fadeIn() +
                            scaleIn(),
                exit =
                    fadeOut() +
                            scaleOut()
            ) {

                ActionFeedbackBanner(
                    text = when {
                        showShareFeedback ->
                            "Ready to share ✨"

                        showCopiedFeedback ->
                            "Copied to clipboard ✓"

                        showSaveFeedback ->
                            if (post.isBookmarked)
                                "Removed from Saved"
                            else
                                "Saved to your collection"

                        else ->
                            ""
                    }
                )
            }

            // ========================================================
            // QUICK ACTIONS
            // ========================================================

            Text(
                text = "Quick actions",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 5.dp
                    )
            )

            LazyRow(
                contentPadding =
                    PaddingValues(
                        horizontal = 2.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                item {

                    QuickActionButton(
                        icon =
                            if (post.isBookmarked)
                                Icons.Default.Bookmark
                            else
                                Icons.Outlined.BookmarkBorder,
                        title =
                            if (post.isBookmarked)
                                "Saved"
                            else
                                "Save",
                        tint =
                            if (post.isBookmarked)
                                BlinkPurple
                            else
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                        onClick = {
                            onToggleSave()
                            showSaveFeedback = true
                        },
                        testTag =
                            "quick_save_action"
                    )
                }

                item {

                    QuickActionButton(
                        icon = Icons.Default.Share,
                        title = "Share",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        onClick = {
                            showShareFeedback = true
                            onShare()
                        },
                        testTag =
                            "quick_share_action"
                    )
                }

                item {

                    QuickActionButton(
                        icon =
                            Icons.Default.ContentCopy,
                        title = "Copy",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        onClick = {

                            clipboardManager
                                .setText(
                                    AnnotatedString(
                                        post.text
                                    )
                                )

                            showCopiedFeedback = true
                        },
                        testTag =
                            "quick_copy_action"
                    )
                }

                item {

                    QuickActionButton(
                        icon =
                            Icons.Default.Link,
                        title = "Link",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurface,
                        onClick = {

                            clipboardManager
                                .setText(
                                    AnnotatedString(
                                        "https://blink.campus/post/${post.id}"
                                    )
                                )

                            showCopiedFeedback = true
                        },
                        testTag =
                            "quick_link_action"
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            HorizontalDivider(
                color =
                    MaterialTheme
                        .colorScheme
                        .outlineVariant
                        .copy(alpha = 0.5f)
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            // ========================================================
            // PERSONALIZATION
            // ========================================================

            Text(
                text = "Personalize your feed",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 5.dp
                    )
            )

            ActionToggleRow(
                icon =
                    Icons.Outlined.NotificationsNone,
                iconTint =
                    if (notificationsEnabled)
                        BlinkPink
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                title =
                    if (notificationsEnabled)
                        "Notifications enabled"
                    else
                        "Turn on notifications",
                subtitle =
                    "Get updates when people interact with this post",
                enabled =
                    notificationsEnabled,
                onClick = {
                    notificationsEnabled =
                        !notificationsEnabled
                }
            )

            ActionToggleRow(
                icon =
                    Icons.Default.Translate,
                iconTint =
                    if (postTranslationEnabled)
                        BlinkPurple
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                title =
                    if (postTranslationEnabled)
                        "Translation enabled"
                    else
                        "Translate post",
                subtitle =
                    "Show a translated version when available",
                enabled =
                    postTranslationEnabled,
                onClick = {
                    postTranslationEnabled =
                        !postTranslationEnabled
                }
            )

            if (!isAuthor) {

                ActionItemRow(
                    icon =
                        Icons.Default.VisibilityOff,
                    iconTint =
                        Color(0xFFFF9800),
                    title =
                        "Not interested",
                    subtitle =
                        "Show fewer posts like this",
                    onClick = {
                        showHideConfirmDialog = true
                    },
                    testTag =
                        "not_interested_action"
                )

                ActionItemRow(
                    icon =
                        Icons.Default.VolumeOff,
                    iconTint =
                        Color(0xFFFF9800),
                    title =
                        "Mute @${post.author}",
                    subtitle =
                        "Stop seeing posts and stories from this user",
                    onClick = {
                        showMuteConfirmDialog = true
                    },
                    testTag =
                        "mute_user_action"
                )
            }

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            HorizontalDivider(
                color =
                    MaterialTheme
                        .colorScheme
                        .outlineVariant
                        .copy(alpha = 0.5f)
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            // ========================================================
            // SECONDARY ACTIONS
            // ========================================================

            Text(
                text = "More",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 5.dp
                    )
            )

            ActionItemRow(
                icon =
                    Icons.Default.CopyAll,
                iconTint =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                title =
                    "Copy post text",
                subtitle =
                    "Copy the complete caption",
                onClick = {

                    clipboardManager
                        .setText(
                            AnnotatedString(
                                post.text
                            )
                        )

                    showCopiedFeedback = true
                },
                testTag =
                    "copy_post_text_action"
            )

            ActionItemRow(
                icon =
                    Icons.Default.Link,
                iconTint =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                title =
                    "Copy post link",
                subtitle =
                    "Copy a direct Blink link",
                onClick = {

                    clipboardManager
                        .setText(
                            AnnotatedString(
                                "https://blink.campus/post/${post.id}"
                            )
                        )

                    showCopiedFeedback = true
                },
                testTag =
                    "copy_post_link_action"
            )

            ActionItemRow(
                icon =
                    Icons.Default.Settings,
                iconTint =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                title =
                    "Post preferences",
                subtitle =
                    "Manage how this content appears to you",
                onClick = {},
                testTag =
                    "post_preferences_action"
            )

            // ========================================================
            // AUTHOR / MODERATION
            // ========================================================

            if (isAuthor) {

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                ActionItemRow(
                    icon =
                        Icons.Default.DeleteOutline,
                    iconTint =
                        Color(0xFFFF5252),
                    title =
                        "Delete post",
                    subtitle =
                        "Permanently remove your post",
                    onClick = {
                        showDeleteConfirmDialog = true
                    },
                    testTag =
                        "delete_post_action"
                )

            } else {

                ActionItemRow(
                    icon =
                        Icons.Default.Flag,
                    iconTint =
                        Color(0xFFFF5252),
                    title =
                        "Report post",
                    subtitle =
                        "Send this content to moderation",
                    onClick = {
                        showReportDialog = true
                    },
                    testTag =
                        "report_post_action"
                )
            }

            Spacer(
                modifier = Modifier.height(15.dp)
            )
        }
    }

    // ================================================================
    // DELETE DIALOG
    // ================================================================

    if (showDeleteConfirmDialog) {

        DeletePostDialog(
            onDismiss = {
                showDeleteConfirmDialog = false
            },
            onConfirm = {

                showDeleteConfirmDialog = false
                onDelete()
                onDismiss()
            }
        )
    }

    // ================================================================
    // MUTE DIALOG
    // ================================================================

    if (showMuteConfirmDialog) {

        MuteUserDialog(
            username = post.author,
            onDismiss = {
                showMuteConfirmDialog = false
            },
            onConfirm = {

                showMuteConfirmDialog = false
                onMuteUser(post.author)
                onDismiss()
            }
        )
    }

    // ================================================================
    // HIDE DIALOG
    // ================================================================

    if (showHideConfirmDialog) {

        NotInterestedDialog(
            onDismiss = {
                showHideConfirmDialog = false
            },
            onConfirm = {

                showHideConfirmDialog = false

                // Existing callback has no hide-specific action.
                // Dismiss the menu so the caller can decide how
                // to handle "not interested" externally.
                onDismiss()
            }
        )
    }

    // ================================================================
    // REPORT DIALOG
    // ================================================================

    if (showReportDialog) {

        ReportPostDialog(
            author = post.author,
            onDismiss = {
                showReportDialog = false
            },
            onSubmitReport = { reason ->

                showReportDialog = false
                onReport(reason)
                onDismiss()
            }
        )
    }
}

// ====================================================================
// POST PREVIEW
// ====================================================================

@Composable
private fun PostActionPreviewCard(
    post: FeedPost,
    compact: Boolean,
    onCompactToggle: () -> Unit
) {

    val hasMedia =
        post.images.isNotEmpty() ||
                post.videoUrl != null ||
                post.isReel

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    "post_action_preview"
                ),
        shape =
            RoundedCornerShape(19.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                        .copy(alpha = 0.42f)
            )
    ) {

        Row(
            modifier =
                Modifier.padding(11.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            if (
                post.authorAvatar.isNotBlank()
            ) {

                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription =
                        "${post.author} profile",
                    contentScale =
                        ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(45.dp)
                            .clip(
                                CircleShape
                            )
                )
            } else {

                Surface(
                    modifier =
                        Modifier.size(45.dp),
                    shape = CircleShape,
                    color =
                        BlinkPink.copy(
                            alpha = 0.12f
                        )
                ) {

                    Icon(
                        Icons.Default.Person,
                        contentDescription =
                            "Author",
                        tint = BlinkPink,
                        modifier =
                            Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.width(9.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            "@${post.author}",
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Surface(
                        shape =
                            RoundedCornerShape(
                                100.dp
                            ),
                        color =
                            if (hasMedia)
                                BlinkPurple.copy(
                                    alpha = 0.10f
                                )
                            else
                                BlinkPink.copy(
                                    alpha = 0.10f
                                )
                    ) {

                        Text(
                            text =
                                when {
                                    post.isReel ->
                                        "REEL"

                                    post.poll != null ->
                                        "POLL"

                                    post.images.size > 1 ->
                                        "PHOTOS"

                                    hasMedia ->
                                        "MEDIA"

                                    else ->
                                        "POST"
                                },
                            fontSize = 7.5.sp,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (post.isReel)
                                    BlinkPurple
                                else
                                    BlinkPink,
                            modifier =
                                Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 3.dp
                                )
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )

                Text(
                    text =
                        if (post.text.isBlank())
                            "No caption"
                        else
                            post.text,
                    fontSize =
                        if (compact)
                            9.5.sp
                        else
                            10.5.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    maxLines =
                        if (compact) 1 else 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        "${formatNumber(post.likes)} likes",
                        fontSize = 8.5.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        "•",
                        fontSize = 8.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        "${formatNumber(post.commentsCount)} comments",
                        fontSize = 8.5.sp,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onCompactToggle,
                modifier =
                    Modifier
                        .size(32.dp)
            ) {

                Icon(
                    imageVector =
                        if (compact)
                            Icons.Default.ExpandMore
                        else
                            Icons.Default.ExpandLess,
                    contentDescription =
                        "Toggle post preview",
                    modifier =
                        Modifier.size(17.dp)
                )
            }
        }
    }
}

// ====================================================================
// QUICK ACTION
// ====================================================================

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {

    var pressed by rememberSaveable {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(
        targetValue =
            if (pressed) 0.9f else 1f,
        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
            ),
        label = "quick_action_scale"
    )

    LaunchedEffect(pressed) {

        if (pressed) {
            delay(170)
            pressed = false
        }
    }

    Surface(
        modifier =
            Modifier
                .scale(scale)
                .testTag(testTag)
                .semantics {
                    contentDescription =
                        title
                    role =
                        Role.Button
                }
                .clickable {
                    pressed = true
                    onClick()
                },
        shape =
            RoundedCornerShape(15.dp),
        color =
            tint.copy(
                alpha = 0.08f
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 9.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                icon,
                contentDescription =
                    null,
                tint = tint,
                modifier =
                    Modifier.size(19.dp)
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                title,
                fontSize = 8.5.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

// ====================================================================
// ACTION ITEM
// ====================================================================

@Composable
private fun ActionItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        15.dp
                    )
                )
                .clickable {
                    onClick()
                }
                .testTag(testTag)
                .semantics {
                    contentDescription =
                        "$title. $subtitle"
                    role =
                        Role.Button
                }
                .padding(
                    vertical = 9.dp,
                    horizontal = 7.dp
                )
    ) {

        Surface(
            modifier =
                Modifier.size(41.dp),
            shape =
                CircleShape,
            color =
                iconTint.copy(
                    alpha = 0.10f
                )
        ) {

            Icon(
                imageVector = icon,
                contentDescription =
                    null,
                tint = iconTint,
                modifier =
                    Modifier.padding(
                        10.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(11.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface
            )

            Text(
                subtitle,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector =
                Icons.Default.ChevronRight,
            contentDescription = null,
            tint =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.size(17.dp)
        )
    }
}

// ====================================================================
// TOGGLE ROW
// ====================================================================

@Composable
private fun ActionToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        15.dp
                    )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    vertical = 9.dp,
                    horizontal = 7.dp
                )
    ) {

        Surface(
            modifier =
                Modifier.size(41.dp),
            shape =
                CircleShape,
            color =
                iconTint.copy(
                    alpha = 0.10f
                )
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier =
                    Modifier.padding(
                        10.dp
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.width(11.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                title,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                subtitle,
                fontSize = 9.5.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Surface(
            shape =
                RoundedCornerShape(
                    100.dp
                ),
            color =
                if (enabled)
                    BlinkPink
                else
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
        ) {

            Text(
                text =
                    if (enabled)
                        "ON"
                    else
                        "OFF",
                color =
                    if (enabled)
                        Color.White
                    else
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                fontSize = 8.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}

// ====================================================================
// FEEDBACK
// ====================================================================

@Composable
private fun ActionFeedbackBanner(
    text: String
) {

    val transition =
        rememberInfiniteTransition(
            label = "feedback_glow"
        )

    val alpha by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900),
                repeatMode =
                    RepeatMode.Reverse
            ),
        label = "feedback_alpha"
    )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    bottom = 8.dp
                ),
        shape =
            RoundedCornerShape(
                14.dp
            ),
        color =
            BlinkPink.copy(
                alpha = alpha
            ),
        border =
            BorderStroke(
                1.dp,
                BlinkPink.copy(
                    alpha = 0.28f
                )
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 11.dp,
                    vertical = 9.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = BlinkPink,
                modifier =
                    Modifier.size(
                        18.dp
                    )
            )

            Spacer(
                modifier =
                    Modifier.width(7.dp)
            )

            Text(
                text,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface,
                fontSize = 10.5.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

// ====================================================================
// DELETE DIALOG
// ====================================================================

@Composable
private fun DeletePostDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {

            Surface(
                shape = CircleShape,
                color =
                    Color(0xFFFF5252)
                        .copy(alpha = 0.10f)
            ) {

                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription =
                        "Delete",
                    tint =
                        Color(0xFFFF5252),
                    modifier =
                        Modifier.padding(
                            10.dp
                        )
                )
            }
        },
        title = {
            Text(
                "Delete this post?",
                fontWeight =
                    FontWeight.Bold
            )
        },
        text = {
            Text(
                "This will permanently remove your post. You won't be able to restore it from the app."
            )
        },
        confirmButton = {

            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Color(0xFFFF5252)
                        )
            ) {

                Text(
                    "Delete",
                    color = Color.White
                )
            }
        },
        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text("Cancel")
            }
        }
    )
}

// ====================================================================
// MUTE DIALOG
// ====================================================================

@Composable
private fun MuteUserDialog(
    username: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {

            Surface(
                shape = CircleShape,
                color =
                    Color(0xFFFF9800)
                        .copy(alpha = 0.10f)
            ) {

                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription =
                        "Mute",
                    tint =
                        Color(0xFFFF9800),
                    modifier =
                        Modifier.padding(
                            10.dp
                        )
                )
            }
        },
        title = {

            Text(
                "Mute @$username?",
                fontWeight =
                    FontWeight.Bold
            )
        },
        text = {

            Text(
                "You won't see this user's posts and stories in your feed. You can change this later in your preferences."
            )
        },
        confirmButton = {

            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                BlinkPink
                        )
            ) {

                Text(
                    "Mute user",
                    color = Color.White
                )
            }
        },
        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text("Cancel")
            }
        }
    )
}

// ====================================================================
// NOT INTERESTED
// ====================================================================

@Composable
private fun NotInterestedDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {

            Surface(
                shape = CircleShape,
                color =
                    Color(0xFFFF9800)
                        .copy(alpha = 0.10f)
            ) {

                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription =
                        "Not interested",
                    tint =
                        Color(0xFFFF9800),
                    modifier =
                        Modifier.padding(
                            10.dp
                        )
                )
            }
        },
        title = {

            Text(
                "Not interested?",
                fontWeight =
                    FontWeight.Bold
            )
        },
        text = {

            Text(
                "We'll reduce similar content in your feed."
            )
        },
        confirmButton = {

            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                BlinkPink
                        )
            ) {

                Text(
                    "Not interested",
                    color = Color.White
                )
            }
        },
        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text("Cancel")
            }
        }
    )
}

// ====================================================================
// REPORT DIALOG
// ====================================================================

@Composable
fun ReportPostDialog(
    author: String,
    onDismiss: () -> Unit,
    onSubmitReport: (String) -> Unit
) {

    val reportReasons =
        remember {

            listOf(
                "Spam or misleading campus ad",
                "Harassment, bullying or hate speech",
                "Academic dishonesty or exam malpractice",
                "Inappropriate or explicit content",
                "Scam / fake seller",
                "Impersonation",
                "Intellectual property issue",
                "Something else"
            )
        }

    var selectedReason by rememberSaveable {
        mutableStateOf(reportReasons[0])
    }

    var submitted by rememberSaveable {
        mutableStateOf(false)
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {

        Surface(
            shape =
                RoundedCornerShape(
                    26.dp
                ),
            color =
                MaterialTheme
                    .colorScheme
                    .surface,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme
                        .colorScheme
                        .outlineVariant
                )
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(21.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                AnimatedContent(
                    targetState = submitted,
                    label = "report_state"
                ) { sent ->

                    if (sent) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Surface(
                                shape = CircleShape,
                                color =
                                    Color(
                                        0xFF22C55E
                                    ).copy(
                                        alpha = 0.10f
                                    )
                            ) {

                                Icon(
                                    Icons.Default
                                        .CheckCircle,
                                    contentDescription =
                                        "Report submitted",
                                    tint =
                                        Color(
                                            0xFF22C55E
                                        ),
                                    modifier =
                                        Modifier
                                            .padding(
                                                15.dp
                                            )
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        13.dp
                                    )
                            )

                            Text(
                                "Report submitted",
                                fontSize = 19.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        5.dp
                                    )
                            )

                            Text(
                                "Thanks for helping keep Blink safe and useful.",
                                textAlign =
                                    TextAlign.Center,
                                fontSize = 11.sp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        17.dp
                                    )
                            )

                            Button(
                                onClick = onDismiss,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                colors =
                                    ButtonDefaults
                                        .buttonColors(
                                            containerColor =
                                                BlinkPink
                                        )
                            ) {

                                Text(
                                    "Done",
                                    color =
                                        Color.White
                                )
                            }
                        }

                    } else {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Surface(
                                shape = CircleShape,
                                color =
                                    Color(0xFFFF5252)
                                        .copy(
                                            alpha =
                                                0.10f
                                        )
                            ) {

                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription =
                                        "Report",
                                    tint =
                                        Color(
                                            0xFFFF5252
                                        ),
                                    modifier =
                                        Modifier
                                            .padding(
                                                13.dp
                                            )
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        12.dp
                                    )
                            )

                            Text(
                                "Report @$author",
                                fontWeight =
                                    FontWeight.Black,
                                fontSize = 19.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        5.dp
                                    )
                            )

                            Text(
                                "Choose the reason that best describes the problem.",
                                fontSize = 11.sp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,
                                textAlign =
                                    TextAlign.Center
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        15.dp
                                    )
                            )

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            max = 370.dp
                                        ),
                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        6.dp
                                    )
                            ) {

                                reportReasons.forEach {
                                    reason ->

                                    val selected =
                                        selectedReason ==
                                                reason

                                    Surface(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedReason =
                                                        reason
                                                },
                                        shape =
                                            RoundedCornerShape(
                                                12.dp
                                            ),
                                        color =
                                            if (
                                                selected
                                            )
                                                BlinkPink.copy(
                                                    alpha =
                                                        0.08f
                                                )
                                            else
                                                MaterialTheme
                                                    .colorScheme
                                                    .surfaceVariant
                                    ) {

                                        Row(
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 7.dp,
                                                    vertical = 5.dp
                                                ),
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            RadioButton(
                                                selected =
                                                    selected,
                                                onClick = {
                                                    selectedReason =
                                                        reason
                                                },
                                                colors =
                                                    RadioButtonDefaults
                                                        .colors(
                                                            selectedColor =
                                                                BlinkPink
                                                        )
                                            )

                                            Text(
                                                reason,
                                                fontSize =
                                                    10.5.sp,
                                                fontWeight =
                                                    if (
                                                        selected
                                                    )
                                                        FontWeight
                                                            .Bold
                                                    else
                                                        FontWeight
                                                            .Normal,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        15.dp
                                    )
                            )

                            Button(
                                onClick = {

                                    submitted = true
                                    onSubmitReport(
                                        selectedReason
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            48.dp
                                        ),
                                colors =
                                    ButtonDefaults
                                        .buttonColors(
                                            containerColor =
                                                BlinkPink
                                        ),
                                shape =
                                    RoundedCornerShape(
                                        100.dp
                                    )
                            ) {

                                Text(
                                    "Submit report",
                                    color =
                                        Color.White,
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick =
                                    onDismiss
                            ) {

                                Text(
                                    "Cancel",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
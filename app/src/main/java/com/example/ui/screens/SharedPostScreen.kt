package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.rememberPersistentTextState
import com.example.data.models.Comment
import com.example.data.models.FeedPost
import com.example.ui.components.CommentReportDialog
import com.example.ui.components.CommentRow
import com.example.ui.components.PostCard
import com.example.ui.theme.BlinkPink

/**
 * Single full-height post destination: post, replies and composer live on one scroll surface.
 * Deep links, Search and Home all use this destination, not a second comments sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPostScreen(
    post: FeedPost,
    currentUsername: String,
    currentUserId: String,
    isDark: Boolean,
    comments: List<Comment>,
    isCommentsLoading: Boolean,
    isPostingComment: Boolean,
    onBack: () -> Unit,
    onLike: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onOptions: () -> Unit,
    onDelete: () -> Unit,
    onProfileClick: (String) -> Unit,
    onVotePoll: (String, String) -> Unit,
    onSendComment: (String, String?) -> Unit,
    onToggleCommentLike: (String) -> Unit,
    onReportComment: (String, String) -> Unit
) {
    var textInput by rememberPersistentTextState(key = "comment_text", scope = post.id)
    var replyParentId by remember(post.id) { mutableStateOf<String?>(null) }
    var replyingTo by remember(post.id) { mutableStateOf<String?>(null) }
    var reportingId by remember(post.id) { mutableStateOf<String?>(null) }
    val inputFocus = remember(post.id) { FocusRequester() }

    fun submit() {
        val body = textInput.trim()
        if (body.isBlank() || isPostingComment) return
        onSendComment(body, replyParentId)
        textInput = ""
        replyParentId = null
        replyingTo = null
    }

    fun replyTo(parentId: String, username: String) {
        replyParentId = parentId
        replyingTo = username.trim().removePrefix("@")
        val mention = replyingTo.orEmpty()
        if (mention.isNotBlank() && !textInput.startsWith("@$mention ")) {
            textInput = "@$mention " + textInput
        }
        inputFocus.requestFocus()
    }

    reportingId?.let { id ->
        CommentReportDialog(
            onDismiss = { reportingId = null },
            onReasonSelected = { reason ->
                reportingId = null
                onReportComment(id, reason)
            }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            TopAppBar(
                title = { Text("Post", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("post_details_list"),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                item(key = "post:${post.id}") {
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = onLike,
                        onComment = { inputFocus.requestFocus() },
                        onBookmark = onBookmark,
                        onShare = onShare,
                        onOptionsClick = onOptions,
                        onProfileClick = onProfileClick,
                        isAuthor = post.author.equals(currentUsername, ignoreCase = true),
                        onDelete = onDelete,
                        onVotePoll = onVotePoll,
                        trackExposure = false,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Comments · ${comments.sumOf { 1 + it.replies.size }}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
                if (isCommentsLoading && comments.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                } else if (comments.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No comments yet. Start the conversation!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(comments, key = { "comment:${it.id}" }) { comment ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CommentRow(
                                username = comment.user,
                                displayName = comment.displayName,
                                avatar = comment.avatar,
                                text = comment.text,
                                time = comment.time,
                                likes = comment.likes,
                                isLiked = comment.isLiked,
                                verificationBadge = comment.verificationBadge,
                                isVip = comment.isVip,
                                premiumStyleId = comment.premiumStyleId,
                                avatarSize = 38.dp,
                                onProfileClick = onProfileClick,
                                onReply = { replyTo(comment.id, comment.user) },
                                onLike = { onToggleCommentLike(comment.id) },
                                canReport = currentUserId.isBlank() ||
                                    !comment.authorId.equals(currentUserId, ignoreCase = true),
                                onReport = { reportingId = comment.id }
                            )
                            comment.replies.forEach { reply ->
                                CommentRow(
                                    username = reply.user,
                                    displayName = reply.displayName,
                                    avatar = reply.avatar,
                                    text = reply.text,
                                    time = reply.time,
                                    likes = reply.likes,
                                    isLiked = reply.isLiked,
                                    verificationBadge = reply.verificationBadge,
                                    isVip = reply.isVip,
                                    premiumStyleId = reply.premiumStyleId,
                                    avatarSize = 30.dp,
                                    modifier = Modifier.padding(start = 48.dp),
                                    onProfileClick = onProfileClick,
                                    onReply = { replyTo(comment.id, reply.user) },
                                    onLike = { onToggleCommentLike(reply.id) },
                                    canReport = currentUserId.isBlank() ||
                                        !reply.authorId.equals(currentUserId, ignoreCase = true),
                                    onReport = { reportingId = reply.id }
                                )
                            }
                        }
                    }
                }
            }

            if (replyParentId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Replying to @${replyingTo.orEmpty()}",
                        modifier = Modifier.weight(1f),
                        color = BlinkPink,
                        fontSize = 12.sp,
                    )
                    IconButton(
                        onClick = { replyParentId = null; replyingTo = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { if (it.length <= 2_000) textInput = it },
                    placeholder = { Text(if (replyParentId == null) "Write a comment..." else "Write a reply...") },
                    modifier = Modifier.weight(1f).focusRequester(inputFocus).testTag("post_details_comment_input"),
                    enabled = !isPostingComment,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { submit() }, enabled = textInput.isNotBlank() && !isPostingComment) {
                    if (isPostingComment) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send comment", tint = BlinkPink)
                    }
                }
            }
        }
    }
}

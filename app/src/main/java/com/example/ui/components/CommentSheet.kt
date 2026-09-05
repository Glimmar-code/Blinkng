package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Comment
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkPink

private data class MentionToken(
    val start: Int,
    val query: String
)

private fun activeMentionToken(text: String): MentionToken? {
    val start = text.lastIndexOf('@')
    if (start < 0) return null
    if (start > 0 && !text[start - 1].isWhitespace()) return null

    val query = text.substring(start + 1)
    if (query.any { it.isWhitespace() }) return null
    if (query.any { !it.isLetterOrDigit() && it !in "_.-" }) return null
    return MentionToken(start = start, query = query)
}

private fun replaceMention(text: String, token: MentionToken, username: String): String {
    val cleanUsername = username.trim().removePrefix("@")
    return text.substring(0, token.start) + "@$cleanUsername "
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    comments: List<Comment>,
    isLoading: Boolean,
    isPosting: Boolean,
    currentUserId: String,
    mentionCandidates: List<UserProfile>,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSendComment: (text: String, parentCommentId: String?) -> Unit,
    onToggleCommentLike: (String) -> Unit,
    onReportComment: (commentId: String, reason: String) -> Unit,
    onProfileClick: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var replyParentId by remember { mutableStateOf<String?>(null) }
    var replyingToUsername by remember { mutableStateOf<String?>(null) }
    var reportingCommentId by remember { mutableStateOf<String?>(null) }

    val mentionToken = remember(textInput) { activeMentionToken(textInput) }
    val mentionSuggestions = remember(mentionToken, mentionCandidates) {
        val query = mentionToken?.query.orEmpty()
        if (mentionToken == null) {
            emptyList()
        } else {
            mentionCandidates
                .asSequence()
                .filter { it.username.isNotBlank() }
                .filter {
                    query.isBlank() ||
                        it.username.contains(query, ignoreCase = true) ||
                        it.fullName.contains(query, ignoreCase = true)
                }
                .sortedBy {
                    if (it.username.startsWith(query, ignoreCase = true)) 0 else 1
                }
                .distinctBy { it.username.trim().removePrefix("@").lowercase() }
                .take(5)
                .toList()
        }
    }

    fun startReply(parentId: String, username: String) {
        val cleanUsername = username.trim().removePrefix("@")
        replyParentId = parentId
        replyingToUsername = cleanUsername
        if (cleanUsername.isNotBlank() && !textInput.startsWith("@$cleanUsername ")) {
            textInput = "@$cleanUsername $textInput"
        }
    }

    fun cancelReply() {
        val username = replyingToUsername
        if (!username.isNullOrBlank()) {
            textInput = textInput.removePrefix("@$username ")
        }
        replyParentId = null
        replyingToUsername = null
    }

    fun submitComment() {
        val cleanText = textInput.trim()
        if (cleanText.isBlank() || isPosting) return
        onSendComment(cleanText, replyParentId)
        textInput = ""
        replyParentId = null
        replyingToUsername = null
    }

    reportingCommentId?.let { commentId ->
        CommentReportDialog(
            onDismiss = { reportingCommentId = null },
            onReasonSelected = { reason ->
                reportingCommentId = null
                onReportComment(commentId, reason)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(bottom = 16.dp)
        ) {
            val totalCommentCount = comments.sumOf { 1 + it.replies.size }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = BlinkPink.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = totalCommentCount.toString(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlinkPink,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = BlinkPink,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                comments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No comments yet 💭",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Be the first to start the campus conversation!",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                CommentRow(
                                    username = comment.user,
                                    displayName = comment.displayName,
                                    avatar = comment.avatar,
                                    text = comment.text,
                                    time = comment.time,
                                    likes = comment.likes,
                                    isLiked = comment.isLiked,
                                    verificationBadge = comment.verificationBadge,
                                    avatarSize = 38.dp,
                                    onProfileClick = onProfileClick,
                                    onReply = { startReply(comment.id, comment.user) },
                                    onLike = { onToggleCommentLike(comment.id) },
                                    canReport = currentUserId.isBlank() ||
                                        !comment.authorId.equals(currentUserId, ignoreCase = true),
                                    onReport = { reportingCommentId = comment.id }
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
                                        avatarSize = 30.dp,
                                        modifier = Modifier.padding(start = 48.dp, top = 12.dp),
                                        onProfileClick = onProfileClick,
                                        onReply = { startReply(comment.id, reply.user) },
                                        onLike = { onToggleCommentLike(reply.id) },
                                        canReport = currentUserId.isBlank() ||
                                            !reply.authorId.equals(currentUserId, ignoreCase = true),
                                        onReport = { reportingCommentId = reply.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (mentionSuggestions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        mentionSuggestions.forEach { profile ->
                            MentionSuggestionRow(
                                profile = profile,
                                onClick = {
                                    val token = mentionToken
                                    if (token != null) {
                                        textInput = replaceMention(textInput, token, profile.username)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (replyParentId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replying to @${replyingToUsername.orEmpty()}",
                            fontSize = 11.5.sp,
                            color = BlinkPink,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(17.dp)
                                .clickable { cancelReply() }
                        )
                    }
                }
            }

            Surface(
                color = if (isDark) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color(0xFFF1F3F5)
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { updated ->
                            if (updated.length <= 2_000) textInput = updated
                        },
                        placeholder = {
                            Text(
                                text = if (replyParentId != null) {
                                    "Write a reply..."
                                } else {
                                    "Add a campus comment..."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.5.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                            autoCorrectEnabled = true
                        ),
                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4,
                        enabled = !isPosting,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input")
                    )

                    IconButton(
                        onClick = { submitComment() },
                        enabled = textInput.isNotBlank() && !isPosting
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                color = BlinkPink,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send comment",
                                tint = if (textInput.isNotBlank()) {
                                    BlinkPink
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    username: String,
    displayName: String,
    avatar: String,
    text: String,
    time: String,
    likes: Int,
    isLiked: Boolean,
    verificationBadge: VerificationBadge,
    avatarSize: Dp,
    onProfileClick: (String) -> Unit,
    onReply: () -> Unit,
    onLike: () -> Unit,
    canReport: Boolean,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth()
    ) {
        CommentAvatar(
            avatarUrl = avatar,
            displayName = displayName,
            username = username,
            size = avatarSize,
            onClick = { if (username.isNotBlank()) onProfileClick(username) }
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayName.ifBlank { username.ifBlank { "Blink user" } },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = username.isNotBlank()) { onProfileClick(username) }
                )
                if (verificationBadge != VerificationBadge.NONE) {
                    VerifiedMark(badge = verificationBadge, size = 12.dp)
                }
                if (username.isNotBlank()) {
                    Text(
                        text = "@${username.removePrefix("@")}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = time,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(3.dp))

            HighlightedText(
                text = text,
                accentColor = BlinkPink,
                textColor = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "Reply",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlinkPink,
                modifier = Modifier.clickable(onClick = onReply)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (canReport) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Comment options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report comment") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onReport()
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            val heartScale by animateFloatAsState(
                targetValue = if (isLiked) 1.22f else 1f,
                animationSpec = spring(dampingRatio = 0.4f),
                label = "commentHeartScale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onLike)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (isLiked) "Unlike comment" else "Like comment",
                    tint = if (isLiked) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(17.dp)
                        .scale(heartScale)
                )
                if (likes > 0) {
                    Text(
                        text = likes.toString(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLiked) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(
    avatarUrl: String,
    displayName: String,
    username: String,
    size: Dp,
    onClick: () -> Unit
) {
    val initial = displayName
        .ifBlank { username }
        .trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "B"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BlinkPink.copy(alpha = 0.16f))
            .clickable(enabled = username.isNotBlank(), onClick = onClick)
    ) {
        Text(
            text = initial,
            color = BlinkPink,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MentionSuggestionRow(
    profile: UserProfile,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        CommentAvatar(
            avatarUrl = profile.avatarUrl,
            displayName = profile.fullName,
            username = profile.username,
            size = 30.dp,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = profile.fullName.ifBlank { profile.username },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.verificationBadge != VerificationBadge.NONE) {
                    VerifiedMark(profile.verificationBadge, size = 11.dp)
                }
            }
            Text(
                text = "@${profile.username.removePrefix("@")}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentReportDialog(
    onDismiss: () -> Unit,
    onReasonSelected: (String) -> Unit
) {
    val reasons = listOf(
        "Spam or scam",
        "Harassment or bullying",
        "Hate or abusive content",
        "False information",
        "Other harmful content"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report comment") },
        text = {
            Column {
                Text(
                    text = "Why are you reporting this comment?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                reasons.forEach { reason ->
                    TextButton(
                        onClick = { onReasonSelected(reason) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reason,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

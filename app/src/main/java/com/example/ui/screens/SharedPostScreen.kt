package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.models.FeedPost
import com.example.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPostScreen(
    post: FeedPost,
    currentUsername: String,
    isDark: Boolean,
    onBack: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onOptions: () -> Unit,
    onDelete: () -> Unit,
    onProfileClick: (String) -> Unit,
    onVotePoll: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (post.isReel) "Shared reel" else "Shared post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            LazyColumn(Modifier.fillMaxSize()) {
                item(key = post.id) {
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = onLike,
                        onComment = onComment,
                        onBookmark = onBookmark,
                        onShare = onShare,
                        onOptionsClick = onOptions,
                        onProfileClick = onProfileClick,
                        isAuthor = post.author.equals(currentUsername, ignoreCase = true),
                        onDelete = onDelete,
                        onVotePoll = onVotePoll
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

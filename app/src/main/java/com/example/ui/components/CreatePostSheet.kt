package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.models.FeedPost
import com.example.data.models.PostDraft
import com.example.data.models.PostPoll
import com.example.data.models.PollOption
import com.example.data.models.ScheduledPost
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkPink
import org.json.JSONArray
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostSheet(
    profile: UserProfile,
    savedDrafts: List<PostDraft> = emptyList(),
    scheduledPosts: List<ScheduledPost> = emptyList(),
    onDismiss: () -> Unit,
    onSubmitPost: (
        text: String, faculty: String, imageUri: String?, videoUri: String?, tags: List<String>, mentions: List<String>, poll: PostPoll?, isReel: Boolean, audience: String, category: String, location: String?, linkUrl: String?, allowComments: Boolean, hideLikes: Boolean, isPinned: Boolean, isDisappearing: Boolean, audioTitle: String?, altText: String?
    ) -> Unit,
    onSaveDraft: (PostDraft) -> Unit = {},
    onDeleteDraft: (String) -> Unit = {},
    onSchedulePost: (FeedPost, Long, String) -> Unit = { _, _, _ -> },
    isDark: Boolean
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var selectedImages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("post") }
    var pollQuestion by rememberSaveable { mutableStateOf("") }
    var pollOptions by rememberSaveable { mutableStateOf(listOf("", "")) }
    var audience by rememberSaveable { mutableStateOf("Everyone") }
    var category by rememberSaveable { mutableStateOf("Campus Life") }
    var allowComments by rememberSaveable { mutableStateOf(true) }
    var showPoll by rememberSaveable { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = uris.map(Uri::toString)
            selectedVideo = null
            mode = "post"
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedVideo = uri.toString()
            selectedImages = emptyList()
            mode = "reel"
        }
    }

    fun submit() {
        val cleanText = text.trim()
        val validPollOptions = pollOptions.map { it.trim() }.filter { it.isNotBlank() }
        val hasContent = cleanText.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null || (showPoll && pollQuestion.isNotBlank() && validPollOptions.size >= 2)
        if (!hasContent) {
            Toast.makeText(context, "Add text, image, video, or a poll first.", Toast.LENGTH_SHORT).show()
            return
        }
        val poll = if (showPoll && pollQuestion.isNotBlank() && validPollOptions.size >= 2) {
            PostPoll(pollQuestion.trim(), validPollOptions.map { PollOption(UUID.randomUUID().toString(), it) })
        } else null
        val imagePayload = if (selectedImages.isEmpty()) null else JSONArray(selectedImages).toString()
        onSubmitPost(cleanText, profile.faculty, imagePayload, selectedVideo, emptyList(), emptyList(), poll, selectedVideo != null, audience, category, null, null, allowComments, false, false, false, null, null)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = if (isDark) MaterialTheme.colorScheme.background else Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                Text("Create post", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Button(onClick = ::submit, enabled = text.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null || showPoll) { Text(if (mode == "reel") "Post Reel" else "Post") }
            }
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "post", onClick = { mode = "post" }, label = { Text("Post") })
                FilterChip(selected = mode == "reel", onClick = { mode = "reel" }, label = { Text("Reel") })
                FilterChip(selected = showPoll, onClick = { showPoll = !showPoll }, label = { Text("Poll") })
            }
            OutlinedTextField(value = text, onValueChange = { if (it.length <= 5000) text = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), minLines = 5, placeholder = { Text("What's happening on campus?") }, shape = RoundedCornerShape(18.dp))

            if (selectedImages.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(selectedImages) { index, uri ->
                        Box(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(uri, "Selected image ${index + 1}", ContentScale.Crop, Modifier.fillMaxSize())
                            IconButton(onClick = { selectedImages = selectedImages.toMutableList().also { it.removeAt(index) } }, Modifier.align(Alignment.TopEnd).size(30.dp)) { Surface(CircleShape, Color.Black.copy(alpha = .55f)) { Icon(Icons.Default.Close, null, tint = Color.White, Modifier.padding(6.dp)) } }
                        }
                    }
                }
            }

            selectedVideo?.let { VideoComposerPreview(it) { selectedVideo = null; mode = "post" } }

            if (showPoll) {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Poll", fontWeight = FontWeight.Bold)
                        OutlinedTextField(pollQuestion, { pollQuestion = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("Ask a question") }, singleLine = true)
                        pollOptions.forEachIndexed { index, value ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value, { v -> pollOptions = pollOptions.toMutableList().also { it[index] = v } }, Modifier.weight(1f), placeholder = { Text("Option ${index + 1}") }, singleLine = true)
                                if (pollOptions.size > 2) IconButton(onClick = { pollOptions = pollOptions.toMutableList().also { it.removeAt(index) } }) { Icon(Icons.Default.Delete, "Remove option") }
                            }
                        }
                        if (pollOptions.size < 4) TextButton(onClick = { pollOptions = pollOptions + "" }) { Text("+ Add option") }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                AssistChip(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, label = { Text("Images") }, leadingIcon = { Icon(Icons.Default.Image, null) })
                AssistChip(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, label = { Text("Video → Reel") }, leadingIcon = { Icon(Icons.Default.VideoLibrary, null) })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Allow comments", Modifier.weight(1f)); Switch(allowComments, { allowComments = it })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VideoComposerPreview(uri: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true; repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = player } }, update = { it.player = player }, Modifier.fillMaxSize())
        IconButton(onClick = onRemove, Modifier.align(Alignment.TopEnd).padding(6.dp)) { Surface(CircleShape, Color.Black.copy(alpha = .55f)) { Icon(Icons.Default.Close, "Remove video", tint = Color.White, Modifier.padding(7.dp)) } }
    }
}

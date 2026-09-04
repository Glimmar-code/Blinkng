package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.data.models.PollOption
import com.example.data.models.PostDraft
import com.example.data.models.PostPoll
import com.example.data.models.ScheduledPost
import com.example.data.models.UserProfile
import org.json.JSONArray
import java.util.UUID

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)
@Composable
fun CreatePostSheet(
    profile: UserProfile,
    savedDrafts: List<PostDraft> = emptyList(),
    scheduledPosts: List<ScheduledPost> = emptyList(),
    onDismiss: () -> Unit,
    onSubmitPost: (
        String,
        String,
        String?,
        String?,
        List<String>,
        List<String>,
        PostPoll?,
        Boolean,
        String,
        String,
        String?,
        String?,
        Boolean,
        Boolean,
        Boolean,
        Boolean,
        String?,
        String?
    ) -> Unit,
    onSaveDraft: (PostDraft) -> Unit = {},
    onDeleteDraft: (String) -> Unit = {},
    onSchedulePost: (FeedPost, Long, String) -> Unit = { _, _, _ -> },
    isDark: Boolean,
    isSubmitting: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
    var audienceMenuOpen by rememberSaveable { mutableStateOf(false) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val audiences = listOf("Everyone", "Campus", "Followers")
    val categories = listOf(
        "Campus Life",
        "Academic",
        "Events",
        "Sports",
        "Entertainment",
        "Marketplace"
    )

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = uris.map(Uri::toString)
            selectedVideo = null
            mode = "post"
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideo = uri.toString()
            selectedImages = emptyList()
            mode = "reel"
            showPoll = false
        }
    }

    fun imagePayload(images: List<String>): String? {
        return when (images.size) {
            0 -> null
            1 -> images.first()
            else -> JSONArray(images).toString()
        }
    }

    fun parseDraftImages(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val clean = raw.trim()
        if (!clean.startsWith("[")) return listOf(clean)
        return runCatching {
            val array = JSONArray(clean)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    val cleanText = text.trim()
    val validPollOptions = pollOptions.map(String::trim).filter(String::isNotBlank)
    val pollValid = showPoll && pollQuestion.isNotBlank() && validPollOptions.size >= 2
    val hasContent = cleanText.isNotBlank() ||
        selectedImages.isNotEmpty() ||
        selectedVideo != null ||
        pollValid
    val canSubmit = hasContent && !isSubmitting

    fun submit() {
        if (!canSubmit) {
            if (!isSubmitting) {
                Toast.makeText(
                    context,
                    if (showPoll && !pollValid) "Add a poll question and at least two options." else "Add something to your post first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val poll = if (pollValid) {
            PostPoll(
                question = pollQuestion.trim(),
                options = validPollOptions.map {
                    PollOption(UUID.randomUUID().toString(), it)
                }
            )
        } else null

        onSubmitPost(
            cleanText,
            profile.faculty,
            imagePayload(selectedImages),
            selectedVideo,
            emptyList(),
            emptyList(),
            poll,
            selectedVideo != null,
            audience,
            category,
            null,
            null,
            allowComments,
            false,
            false,
            false,
            null,
            null
        )
    }

    fun saveDraft() {
        if (!hasContent) {
            Toast.makeText(context, "Add something before saving a draft.", Toast.LENGTH_SHORT).show()
            return
        }

        onSaveDraft(
            PostDraft(
                text = text,
                faculty = profile.faculty,
                imageUri = imagePayload(selectedImages),
                videoUri = selectedVideo,
                isReel = selectedVideo != null,
                category = category,
                audience = audience
            )
        )
        Toast.makeText(context, "Draft saved.", Toast.LENGTH_SHORT).show()
    }

    val currentHasContent by rememberUpdatedState(hasContent)
    val currentIsSubmitting by rememberUpdatedState(isSubmitting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            when {
                targetValue != SheetValue.Hidden -> true
                currentIsSubmitting -> false
                currentHasContent -> {
                    showDiscardDialog = true
                    false
                }
                else -> true
            }
        }
    )

    fun requestDismiss() {
        if (isSubmitting) return
        if (hasContent) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            ComposerTopBar(
                isSubmitting = isSubmitting,
                canSubmit = canSubmit,
                isReel = selectedVideo != null,
                onDismiss = ::requestDismiss,
                onSubmit = ::submit
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(bottom = 18.dp)
            ) {
                AuthorComposerHeader(
                    profile = profile,
                    audience = audience,
                    audienceMenuOpen = audienceMenuOpen,
                    onAudienceMenuChanged = { audienceMenuOpen = it },
                    audiences = audiences,
                    onAudienceSelected = {
                        audience = it
                        audienceMenuOpen = false
                    }
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == "post" && !showPoll,
                        onClick = {
                            mode = "post"
                            showPoll = false
                        },
                        enabled = !isSubmitting,
                        label = { Text("Post") }
                    )
                    FilterChip(
                        selected = mode == "reel",
                        onClick = {
                            mode = "reel"
                            showPoll = false
                            if (selectedVideo == null) {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        },
                        enabled = !isSubmitting,
                        label = { Text("Reel") }
                    )
                    FilterChip(
                        selected = showPoll,
                        onClick = {
                            showPoll = !showPoll
                            if (showPoll) {
                                mode = "post"
                                selectedVideo = null
                            }
                        },
                        enabled = !isSubmitting,
                        label = { Text("Poll") }
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 5000) text = it
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    minLines = 5,
                    maxLines = 12,
                    placeholder = {
                        Text(
                            if (selectedVideo != null) "Write a caption for your reel..."
                            else "What's happening on campus?"
                        )
                    },
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                if (selectedImages.isNotEmpty()) {
                                    "${selectedImages.size}/10 photos selected"
                                } else {
                                    "Be clear, useful, and respectful."
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text("${text.length}/5000")
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                )

                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        itemsIndexed(
                            items = selectedImages,
                            key = { _, uri -> uri }
                        ) { index, uri ->
                            SelectedImagePreview(
                                uri = uri,
                                index = index,
                                onRemove = {
                                    selectedImages = selectedImages.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                }
                            )
                        }

                        if (selectedImages.size < 10) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clickable(enabled = !isSubmitting) {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Add more", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                selectedVideo?.let {
                    VideoComposerPreview(
                        uri = it,
                        onRemove = {
                            if (!isSubmitting) {
                                selectedVideo = null
                                mode = "post"
                            }
                        }
                    )
                }

                if (showPoll) {
                    PollComposer(
                        question = pollQuestion,
                        onQuestionChanged = { pollQuestion = it.take(240) },
                        options = pollOptions,
                        onOptionsChanged = { pollOptions = it },
                        enabled = !isSubmitting
                    )
                }

                AddToPostCard(
                    enabled = !isSubmitting,
                    onImages = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onVideo = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPoll = {
                        showPoll = !showPoll
                        if (showPoll) {
                            selectedVideo = null
                            mode = "post"
                        }
                    }
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Post settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Category",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box {
                                AssistChip(
                                    onClick = { categoryMenuOpen = true },
                                    enabled = !isSubmitting,
                                    label = { Text(category) }
                                )
                                DropdownMenu(
                                    expanded = categoryMenuOpen,
                                    onDismissRequest = { categoryMenuOpen = false }
                                ) {
                                    categories.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item) },
                                            onClick = {
                                                category = item
                                                categoryMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow comments", fontWeight = FontWeight.Medium)
                                Text(
                                    "Let people reply to this post.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = allowComments,
                                onCheckedChange = { allowComments = it },
                                enabled = !isSubmitting
                            )
                        }
                    }
                }

                if (savedDrafts.isNotEmpty()) {
                    Text(
                        "Recent drafts",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedDrafts.take(4), key = { it.id }) { draft ->
                            DraftCard(
                                draft = draft,
                                onLoad = {
                                    text = draft.text
                                    selectedImages = parseDraftImages(draft.imageUri)
                                    selectedVideo = draft.videoUri
                                    mode = if (draft.isReel || !draft.videoUri.isNullOrBlank()) "reel" else "post"
                                    audience = draft.audience
                                    category = draft.category
                                    showPoll = false
                                },
                                onDelete = { onDeleteDraft(draft.id) }
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = ::saveDraft,
                        enabled = hasContent && !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save draft")
                    }

                    Button(
                        onClick = ::submit,
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Publishing")
                        } else {
                            Text(if (selectedVideo != null) "Post reel" else "Publish")
                        }
                    }
                }

                if (scheduledPosts.isNotEmpty()) {
                    Text(
                        "${scheduledPosts.size} scheduled post${if (scheduledPosts.size == 1) "" else "s"}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard this post?") },
            text = {
                Text("Your post is still here. Keep editing, or discard everything you added.")
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun ComposerTopBar(
    isSubmitting: Boolean,
    canSubmit: Boolean,
    isReel: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDismiss,
            enabled = !isSubmitting
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }

        Column(Modifier.weight(1f)) {
            Text(
                if (isReel) "Create reel" else "Create post",
                fontWeight = FontWeight.Black,
                fontSize = 19.sp
            )
            Text(
                if (isReel) "Share a vertical video with campus" else "Share something with your community",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onSubmit,
            enabled = canSubmit
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Post")
            }
        }
    }
}

@Composable
private fun AuthorComposerHeader(
    profile: UserProfile,
    audience: String,
    audienceMenuOpen: Boolean,
    onAudienceMenuChanged: (Boolean) -> Unit,
    audiences: List<String>,
    onAudienceSelected: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            Text(
                profile.fullName.ifBlank { profile.username },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "@${profile.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            AssistChip(
                onClick = { onAudienceMenuChanged(true) },
                label = { Text(audience) },
                leadingIcon = {
                    Icon(
                        when (audience) {
                            "Followers" -> Icons.Default.People
                            "Campus" -> Icons.Default.Lock
                            else -> Icons.Default.Public
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            )

            DropdownMenu(
                expanded = audienceMenuOpen,
                onDismissRequest = { onAudienceMenuChanged(false) }
            ) {
                audiences.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = { onAudienceSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedImagePreview(
    uri: String,
    index: Int,
    onRemove: () -> Unit
) {
    Box(
        Modifier
            .size(116.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected image ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(30.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = .62f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove image",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun PollComposer(
    question: String,
    onQuestionChanged: (String) -> Unit,
    options: List<String>,
    onOptionsChanged: (List<String>) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Poll", fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChanged,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text("Ask a question") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            options.forEachIndexed { index, value ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            onOptionsChanged(
                                options.toMutableList().also {
                                    it[index] = newValue.take(120)
                                }
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Option ${index + 1}") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (options.size > 2) {
                        IconButton(
                            onClick = {
                                onOptionsChanged(
                                    options.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                )
                            },
                            enabled = enabled
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove option"
                            )
                        }
                    }
                }
            }

            if (options.size < 4) {
                TextButton(
                    onClick = { onOptionsChanged(options + "") },
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add option")
                }
            }
        }
    }
}

@Composable
private fun AddToPostCard(
    enabled: Boolean,
    onImages: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Add to your post",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onImages,
                    enabled = enabled,
                    label = { Text("Photos") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                AssistChip(
                    onClick = onVideo,
                    enabled = enabled,
                    label = { Text("Video") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                AssistChip(
                    onClick = onPoll,
                    enabled = enabled,
                    label = { Text("Poll") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Poll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: PostDraft,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onLoad),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft.text.ifBlank {
                        if (!draft.videoUri.isNullOrBlank()) "Video draft" else "Media draft"
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    draft.category,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete draft",
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoComposerPreview(
    uri: String,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(260.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = .62f)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove video",
                    tint = Color.White,
                    modifier = Modifier.padding(7.dp)
                )
            }
        }
    }
}

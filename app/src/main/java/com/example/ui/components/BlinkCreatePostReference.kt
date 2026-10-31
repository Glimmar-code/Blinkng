package com.example.ui.components

import com.blinkng.shared.BlinkPostReferenceStyle
import com.blinkng.shared.BlinkPostComposerPolicy
import com.example.data.media.hasGalleryPermission
import com.example.data.media.readAvailableGalleryPhotos
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.PostDraft
import org.json.JSONArray

/**
 * Reference-style first step for the existing BLINK composer. Only real saved drafts
 * are shown: the app cannot display device gallery thumbnails before permission.
 * All selections enter the existing posting/persistence pipeline.
 */
@Composable
internal fun BlinkCreatePostLanding(
    drafts: List<PostDraft>,
    onClose: () -> Unit,
    onText: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit,
    onClassic: () -> Unit,
    onCamera: () -> Unit,
    onDraft: (PostDraft) -> Unit,
    onRecentPhoto: (String) -> Unit = {},
    onRecentSeeAll: () -> Unit = onPhoto
) {
    val white = Color.White
    val context = LocalContext.current
    var showAllDrafts by remember { mutableStateOf(false) }
    var recentPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    // Only display real device photos when gallery access has already been granted.
    // Android Photo Picker remains the privacy-preserving fallback.
    LaunchedEffect(context) {
        recentPhotos = if (hasGalleryPermission(context)) {
            readAvailableGalleryPhotos(context).take(12).map { it.uri.toString() }
        } else emptyList()
    }
    Column(
        Modifier.fillMaxSize().background(Color(BlinkPostReferenceStyle.Canvas)).verticalScroll(rememberScrollState())
            .testTag("blink_create_post_landing")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = "Close create post", tint = white)
            }
            Text(
                "Create Post", color = white, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlinkPostChoice("Text", "Share your thoughts", Icons.Default.TextFields,
                Color(0xFF5148EA), onText, Modifier.weight(1f))
            BlinkPostChoice("Photo", "Share images", Icons.Default.PhotoLibrary,
                Color(BlinkPostReferenceStyle.Photo), onPhoto, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlinkPostChoice("Video", "Share videos", Icons.Default.Videocam,
                Color(BlinkPostReferenceStyle.Video), onVideo, Modifier.weight(1f))
            BlinkPostChoice("Poll", "Ask a question", Icons.Default.BarChart,
                Color(BlinkPostReferenceStyle.Poll), onPoll, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", color = white, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onRecentSeeAll, modifier = Modifier.testTag("blink_recent_see_all")) {
                Text("See all", color = Color(0xFF7D83F9), fontSize = 12.sp)
            }
        }
        val mediaDrafts = drafts.filter { !it.imageUri.isNullOrBlank() }
        if (recentPhotos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("blink_recent_device_photos")) {
                items(recentPhotos.take(8), key = { it }) { photoUri ->
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Select recent gallery photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 78.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRecentPhoto(photoUri) }
                    )
                }
            }
        } else if (mediaDrafts.isNotEmpty()) {
            // When full gallery access is unavailable, previews are restricted to
            // photos the user explicitly selected and saved as local drafts.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mediaDrafts.take(8), key = { it.id }) { draft ->
                    val raw = draft.imageUri.orEmpty()
                    val first = if (raw.startsWith("[")) {
                        runCatching { JSONArray(raw).optString(0) }.getOrDefault("")
                    } else raw
                    AsyncImage(
                        model = first,
                        contentDescription = "Resume recent photo draft",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 78.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDraft(draft) }
                    )
                }
            }
        }
        if (drafts.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF12151C),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3139))
            ) {
                Text(
                    "No drafts yet. Your selected photos and videos stay in the editor.",
                    color = Color(BlinkPostReferenceStyle.Muted), fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(if (showAllDrafts) drafts else drafts.take(8), key = { it.id }) { draft ->
                    val rawImage = draft.imageUri.orEmpty()
                    val previewImage = if (rawImage.startsWith("[")) {
                        runCatching { JSONArray(rawImage).optString(0) }.getOrDefault("")
                    } else rawImage
                    Surface(
                        modifier = Modifier.size(width = 92.dp, height = 100.dp)
                            .clickable { onDraft(draft) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1E26)
                    ) {
                        Box {
                            if (previewImage.isNotBlank()) {
                                AsyncImage(
                                    model = previewImage,
                                    contentDescription = "Open saved draft",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    if (draft.videoUri.isNullOrBlank()) Icons.Default.Description
                                    else Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFFAEB5C4),
                                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                                )
                            }
                            Text(
                                draft.text.ifBlank { "Draft" }, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp, color = white,
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Color(BlinkPostReferenceStyle.Canvas).copy(alpha = .72f)).padding(5.dp)
                            )
                        }
                    }
                }
            }
        }
        BlinkPostMenuRow(
            title = "Drafts", detail = when {
                drafts.isEmpty() -> "No saved drafts"
                drafts.size == 1 -> "Tap to resume your saved draft"
                else -> "${drafts.size} saved drafts · tap to view"
            },
            icon = Icons.Default.Save, onClick = {
                if (drafts.size > 1) showAllDrafts = !showAllDrafts
                else drafts.firstOrNull()?.let(onDraft)
            }, enabled = drafts.isNotEmpty()
        )
        BlinkPostMenuRow(
            title = "Camera", detail = "Take a photo or record a Reel",
            icon = Icons.Default.CameraAlt, onClick = onCamera
        )
        BlinkPostMenuRow(
            title = "Classic composer", detail = "Use BLINK's original posting screen",
            icon = Icons.Default.Description, onClick = onClassic
        )
        Text(
            "Posts can include text, photos or polls. Videos are published as Reels.",
            color = Color(0xFF9BA2B1), fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BlinkPostChoice(
    title: String,
    subtitle: String,
    image: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "createChoicePress"
    )
    Card(
        modifier = modifier.height(132.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag("blink_create_choice_" + title.lowercase()),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(BlinkPostReferenceStyle.Border)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161D))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val colors = BlinkPostReferenceStyle.choiceGradients[title]?.map { Color(it) }
                ?: listOf(accent, accent)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.verticalGradient(colors)), contentAlignment = Alignment.Center) {
                if (title == "Text") Text("Aa", color = Color.White, fontSize = 22.sp)
                else Icon(image, contentDescription = null,
                    tint = if (title == "Poll") Color(0xFF292B30) else Color.White,
                    modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Color(0xFFC2C5CE), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BlinkPostMenuRow(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF191D24),
        border = BorderStroke(1.dp, Color(0xFF31353D))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color(0xFFA4AAB6), fontSize = 11.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Final confirmation step, separate from the live editor. Publishing is routed
 * exclusively to the existing validated submit callback.
 */
@Composable
internal fun BlinkCreatePostPublishOptions(
    onBack: () -> Unit,
    onPost: () -> Unit,
    onSaveDraft: () -> Unit,
    onPreview: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareOtherApps: () -> Unit,
    onCopyCaption: () -> Unit,
    canCopyCaption: Boolean,
    isSubmitting: Boolean,
    canPublish: Boolean,
    uploadProgress: Int?,
    canSaveDraft: Boolean = true
) {
    Column(
        Modifier.fillMaxSize().background(Color(BlinkPostReferenceStyle.Canvas))
            .testTag("blink_create_post_publish_options")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(60.dp)) {
            IconButton(onClick = onBack, enabled = !isSubmitting,
                modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back to editor", tint = Color.White)
            }
            Text("Post Options", color = Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center))
            // Keep Preview accessible without adding a full-height extra card
            // that pushes the reference's sharing group below the fold.
            TextButton(
                onClick = onPreview,
                enabled = !isSubmitting,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .testTag("blink_post_options_preview")
            ) {
                Text("Preview", color = if (isSubmitting) Color(0xFF8D929A)
                    else Color(0xFFB9B6FF), fontSize = 12.sp)
            }
        }
        Button(
            onClick = onPost,
            enabled = canPublish && !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF315CFF), Color(0xFF8434E9)))),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                contentColor = Color.White, disabledContainerColor = Color(0xFF343544))
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isSubmitting) {
                    if (uploadProgress == null) "Publishing..."
                    else "Uploading " + uploadProgress.coerceIn(0, 100) + "%"
                } else "Post Now",
                fontWeight = FontWeight.Bold
            )
        }
        if (!canPublish && !isSubmitting) {
            Text("Review your post details before publishing.", color = Color(0xFFFFC7C7), fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onSaveDraft, enabled = !isSubmitting && canSaveDraft,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, Color(0xFF30333A)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF181B22), contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save as Draft")
        }
        Spacer(Modifier.height(4.dp))
        Text("Also share to", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        // The reference design presents these actions as one continuous
        // grouped card, rather than three disconnected floating surfaces.
        Surface(
            color = Color(0xFF1A1D24),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, Color(0xFF30343E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                BlinkPostShareRow(
                    title = "WhatsApp", icon = Icons.Default.Share,
                    accent = Color(0xFF1DBF69), enabled = !isSubmitting,
                    onClick = onShareWhatsApp
                )
                HorizontalDivider(color = Color(0xFF343944), thickness = 0.5.dp)
                BlinkPostShareRow(
                    title = "Copy caption", icon = Icons.Default.Link,
                    accent = Color(0xFF5B616D), enabled = !isSubmitting && canCopyCaption,
                    onClick = onCopyCaption
                )
                HorizontalDivider(color = Color(0xFF343944), thickness = 0.5.dp)
                BlinkPostShareRow(
                    title = "Other apps", icon = Icons.Default.Share,
                    accent = Color(0xFF5B616D), enabled = !isSubmitting,
                    onClick = onShareOtherApps
                )
            }
        }
        Text(
            "Before publication, sharing sends only your media and caption. It does not post to BLINK.",
            fontSize = 12.sp, color = Color(0xFFAAB1BE)
        )
    }
}

@Composable
private fun BlinkPostShareRow(
    title: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1A1D24), shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().height(55.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent, shape = RoundedCornerShape(9.dp)) {
                Icon(icon, contentDescription = null, tint = Color.White,
                    modifier = Modifier.padding(8.dp).size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = if (enabled) Color.White else Color(0xFF9399A7),
                fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (enabled) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null,
                    tint = Color(0xFFB5BAC7))
            } else {
                Text("After posting", color = Color(0xFF9399A7), fontSize = 11.sp)
            }
        }
    }
}

/** Compact hashtag card for the dark reference composer; counts are never fabricated. */
@Composable
internal fun BlinkReferenceTagCard(
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) Color(0xFF29234C) else Color(0xFF191C24),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF766EFF) else Color(0xFF30343D)
        )
    ) {
        Text(
            text = "#" + tag,
            color = if (enabled) Color.White else Color(0xFF999EAD),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Horizontal icon row from the reference composer. The existing Add to post
 * controls remain separately available, including link and location actions.
 */
@Composable
internal fun BlinkReferenceComposerToolbar(
    enabled: Boolean,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onPoll: () -> Unit,
    onEmoji: () -> Unit,
    onTag: () -> Unit,
    onMention: () -> Unit,
    onLocation: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Default.Image, "Add photo", onPhoto),
        Triple(Icons.Default.Videocam, "Add video", onVideo),
        Triple(Icons.Default.BarChart, "Create poll", onPoll),
        Triple(Icons.Default.InsertEmoticon, "Insert emoji", onEmoji),
        Triple(Icons.Default.Tag, "Add tag", onTag),
        Triple(Icons.Default.AlternateEmail, "Mention student", onMention),
        Triple(Icons.Default.LocationOn, "Add location", onLocation)
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .testTag("blink_reference_composer_actions")
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        actions.forEachIndexed { index, item ->
            IconButton(
                onClick = item.third,
                enabled = enabled,
                modifier = Modifier.size(43.dp).testTag("blink_compose_action_" + index)
            ) {
                Icon(
                    imageVector = item.first,
                    contentDescription = item.second,
                    tint = when (index) {
                        0 -> Color(0xFF477BFF)
                        1 -> Color(0xFFFD557A)
                        2 -> Color(0xFFF5C349)
                        else -> Color.White
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Distinct post-details stage modeled on the approved reference. Every control
 * updates existing persisted composer state; unsupported privacy modes are not offered.
 */
@Composable
internal fun BlinkReferencePostDetails(
    text: String,
    mediaUri: String?,
    tags: List<String>,
    audience: String,
    location: String,
    category: String,
    allowComments: Boolean,
    hideLikes: Boolean,
    temporary: Boolean,
    onBack: () -> Unit,
    onEditCaption: () -> Unit,
    onAudienceChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCommentsChange: (Boolean) -> Unit,
    onHideLikesChange: (Boolean) -> Unit,
    onTemporaryChange: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    var showAudienceChooser by remember { mutableStateOf(false) }
    if (showAudienceChooser) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAudienceChooser = false },
            title = { Text("Who can see this post?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Everyone", "Campus", "Followers").forEach { option ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onAudienceChange(option)
                                showAudienceChooser = false
                            }
                        ) {
                            Text(if (option == "Everyone") "Public" else option)
                        }
                    }
                    Text(
                        "Only me is unavailable until private-post security is verified.",
                        color = Color(0xFF9EA5B4), fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudienceChooser = false }) { Text("Close") }
            }
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(BlinkPostReferenceStyle.Canvas))
            .verticalScroll(rememberScrollState())
            .testTag("blink_post_details_stage")
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Back to composer", tint = Color.White)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Post Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            TextButton(onClick = onNext) {
                Text("Next", color = Color(0xFF8B81FF), fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            color = Color(0xFF13161C), shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEditCaption)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!mediaUri.isNullOrBlank()) {
                    AsyncImage(
                        model = mediaUri, contentDescription = "Selected post cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text.ifBlank { "Add a caption or description" },
                        color = Color.White, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, fontSize = 13.sp
                    )
                    if (tags.isNotEmpty()) {
                        Text(
                            tags.joinToString(" ") { "#" + it },
                            color = Color(0xFF6C92FF), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontSize = 12.sp
                        )
                    }
                    Text("Edit caption, tags and mentions", color = Color(0xFFAAB2BF), fontSize = 11.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
            }
        }
        BlinkPostMenuRow(
            title = "Tag people", detail = "Mention students in your caption",
            icon = Icons.Default.AlternateEmail, onClick = onEditCaption
        )
        Surface(
            color = Color(0xFF191D24), shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = location, onValueChange = { onLocationChange(it.take(120)) },
                label = { Text("Add location") },
                placeholder = { Text("Campus or place") },
                modifier = Modifier.fillMaxWidth().padding(7.dp),
                singleLine = true
            )
        }
        Surface(
            color = Color(0xFF191D24), shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { showAudienceChooser = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Who can see this post?", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Text(if (audience == "Everyone") "Public" else audience,
                    color = Color(0xFFC1C6D4), fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, "Choose post audience", tint = Color.White)
            }
        }
        Text("Post category", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            com.blinkng.shared.BlinkPostGrowthTips.feedCategories.forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { onCategoryChange(option) },
                    label = { Text(option) }
                )
            }
        }
        Surface(
            color = Color(0xFF171B22), shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                BlinkReferenceSettingRow("Allow comments", allowComments, onCommentsChange)
                HorizontalDivider(color = Color(0xFF343944))
                BlinkReferenceSettingRow("Hide like count", hideLikes, onHideLikesChange)
                HorizontalDivider(color = Color(0xFF343944))
                BlinkReferenceSettingRow("Temporary post", temporary, onTemporaryChange)
            }
        }
        Text(
            "Post visibility uses your existing BLINK audience rules. Other posting tools remain in the editor.",
            color = Color(0xFFADB3BF), fontSize = 11.sp
        )
        Button(
            onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2)),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Next: Post options", color = Color.White) }
    }
}

@Composable
private fun BlinkReferenceSettingRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Native device picker is followed by a fully interactive, reference-style
 * selection review. Grid items are real URIs; deleting/reordering takes effect
 * in the existing CreatePostSheet media payload.
 */
@Composable
internal fun BlinkReferenceMediaReview(
    images: List<String>,
    onBack: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onEdit: (String) -> Unit,
    onAddMore: () -> Unit,
    onPickVideos: () -> Unit,
    onNext: () -> Unit,
    onSelectPhoto: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    var library by remember { mutableStateOf<List<String>>(emptyList()) }
    var canBrowse by remember { mutableStateOf(hasGalleryPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canBrowse = hasGalleryPermission(context)
        permissionVersion++
    }
    fun requestLibrary() {
        permission.launch(when {
            android.os.Build.VERSION.SDK_INT >= 34 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            android.os.Build.VERSION.SDK_INT >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        })
    }
    LaunchedEffect(permissionVersion, canBrowse) {
        library = if (canBrowse) readAvailableGalleryPhotos(context).map { it.uri.toString() } else emptyList()
    }
    val visiblePhotos = (library + images).distinct()
    var previewIndex by remember { mutableIntStateOf(-1) }
    var selectedTab by remember { mutableStateOf("All") }
    if (previewIndex in images.indices) {
        Dialog(onDismissRequest = { previewIndex = -1 }) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF111319), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo " + (previewIndex + 1) + " of " + images.size,
                        color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { previewIndex = -1 }) {
                        Icon(Icons.Default.Close, "Close photo preview", tint = Color.White)
                    }
                }
                AsyncImage(
                    model = images[previewIndex], contentDescription = "Full selected photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        val uri = images[previewIndex]
                        previewIndex = -1
                        onEdit(uri)
                    }, modifier = Modifier.testTag("blink_gallery_edit_preview")) { Text("Edit") }
                    TextButton(onClick = {
                        val index = previewIndex
                        previewIndex = -1
                        onRemove(index)
                    }) { Text("Remove") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        onMove(previewIndex, previewIndex - 1)
                        previewIndex--
                    }, enabled = previewIndex > 0) { Text("Move earlier") }
                    TextButton(onClick = {
                        onMove(previewIndex, previewIndex + 1)
                        previewIndex++
                    }, enabled = previewIndex < images.lastIndex) { Text("Move later") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { previewIndex = (previewIndex - 1).coerceAtLeast(0) },
                        enabled = previewIndex > 0
                    ) { Icon(Icons.Default.ChevronLeft, null); Text("Previous") }
                    TextButton(
                        onClick = { previewIndex = (previewIndex + 1).coerceAtMost(images.lastIndex) },
                        enabled = previewIndex < images.lastIndex
                    ) { Text("Next"); Icon(Icons.Default.ChevronRight, null) }
                }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(BlinkPostReferenceStyle.Canvas))
            .testTag("blink_media_review")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("blink_media_review_back")
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back from selected photos", tint = Color.White)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Select Media", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            TextButton(onClick = onNext, enabled = images.isNotEmpty()) {
                Text("Next", color = Color(0xFF8F87FF), fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("All", "Photos", "Videos").forEach { tab ->
                Surface(
                    color = if (selectedTab == tab) Color.White else Color(0xFF1A1C23),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                        .clickable {
                            if (tab == "Videos") {
                                // Keep Android's system picker in charge of device-library access.
                                onPickVideos()
                            } else {
                                selectedTab = tab
                                // Tabs only filter or refresh permitted media.
                                // Asking for all-gallery access is a separate,
                                // explicit action in the empty-gallery state.
                                if (hasGalleryPermission(context)) {
                                    canBrowse = true
                                    permissionVersion++
                                }
                            }
                        }
                        .testTag("blink_media_tab_" + tab.lowercase())
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            tab,
                            color = if (selectedTab == tab) Color(BlinkPostReferenceStyle.Canvas) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (canBrowse) "Recent photos" else "Selected photos", color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(images.size.toString() + " / 10", color = Color(0xFFB9C0CE))
        }
        if (visiblePhotos.isEmpty()) {
            BlinkMediaEmptyGallery(
                canBrowse = canBrowse,
                onPick = onAddMore,
                onRequestLibrary = ::requestLibrary,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                itemsIndexed(visiblePhotos, key = { _, uri -> uri }) { _, uri ->
                    val index = images.indexOf(uri)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF20242B))
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = if (index >= 0) "Selected photo " + (index + 1) else "Available device photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().testTag(if (index >= 0) "blink_gallery_photo_" + index else "blink_gallery_available_" + uri)
                                .clickable {
                                    if (index >= 0) previewIndex = index
                                    else if (images.size < BlinkPostComposerPolicy.MAX_PHOTOS) onSelectPhoto(uri)
                                }
                        )
                        if (index >= 0) Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                            color = Color(0xFF5448F5), shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                (index + 1).toString(), color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                fontSize = 12.sp
                            )
                        }

                    }
                }
            }
            }
        Row(
            modifier = Modifier.fillMaxWidth().testTag("blink_gallery_selected_strip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f).height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(count = images.size, key = { index -> images[index] }) { index ->
                    val uri = images[index]
                    Box(Modifier.size(56.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Preview selected photo " + (index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { previewIndex = index }
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).size(20.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xFF292C35))
                                .clickable { onRemove(index) }
                                .testTag("blink_gallery_remove_selected_" + index),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove selected photo " + (index + 1),
                                tint = Color.White, modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (images.size < BlinkPostComposerPolicy.MAX_PHOTOS) {
                    item(key = "add-photo") {
                        Box(
                            Modifier.size(52.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF252A34))
                                .clickable(onClick = onAddMore)
                                .testTag("blink_gallery_add_more"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add more photos",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            Text(
                images.size.toString() + " selected",
                color = Color(0xFFD3D6DE), fontSize = 12.sp,
                modifier = Modifier.testTag("blink_gallery_selected_count")
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onNext,
                enabled = images.isNotEmpty(),
                modifier = Modifier.width(136.dp).height(44.dp)
                    .testTag("blink_gallery_next"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2))
            ) { Text("Next (" + images.size + ")", color = Color.White) }
        }
        Spacer(Modifier.height(3.dp))
    }
}

/**
 * Dedicated empty-gallery surface: Android Photo Picker works without broad
 * media permission. The second action is optional and only requests recent
 * thumbnail access when the user chooses to grant it.
 */
@Composable
internal fun BlinkMediaEmptyGallery(
    canBrowse: Boolean,
    onPick: () -> Unit,
    onRequestLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("blink_gallery_empty_state"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PhotoLibrary, contentDescription = null,
            tint = Color(0xFF9299AD), modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (canBrowse) "No photos found" else "Choose photos from your device",
            color = Color.White, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            if (canBrowse) "Choose photos with Android's photo picker."
            else "You can select photos without allowing access to your whole gallery.",
            color = Color(0xFFAFB5C4), fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPick,
            modifier = Modifier.testTag("blink_gallery_browse_photos"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2))
        ) { Text("Choose photos", color = Color.White) }
        if (!canBrowse) {
            TextButton(
                onClick = onRequestLibrary,
                modifier = Modifier.testTag("blink_gallery_allow_access")
            ) { Text("Show recent gallery photos", color = Color(0xFFABAAFF)) }
        }
    }
}

/** Interactive clip order screen before rendering one playable Reel. */
@Composable
internal fun BlinkReelClipReview(
    clips: List<String>,
    exporting: Boolean,
    progress: Int,
    error: String?,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(BlinkPostReferenceStyle.Canvas))
            .verticalScroll(rememberScrollState()).testTag("blink_reel_clip_review")
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !exporting) {
                Icon(Icons.Default.Close, contentDescription = "Close clip editor", tint = Color.White)
            }
            Text("Arrange Video Clips", color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${clips.size}/4", color = Color(0xFF9596FF))
        }
        Text("Select 2–4 videos. Arrange them and combine into a single Reel (maximum 3 minutes).",
            color = Color(0xFFB3BAC6), fontSize = 12.sp)
        clips.forEachIndexed { index, clip ->
            Surface(color = Color(0xFF1B1F28), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BlinkReferenceClipThumbnail(clip)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Clip ${index + 1}", color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(clip.substringAfterLast('/').take(24),
                            color = Color(0xFF9DA5B3), fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = !exporting && index > 0,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move clip earlier", tint = Color.White)
                    }
                    IconButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = !exporting && index < clips.lastIndex,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move clip later", tint = Color.White)
                    }
                    IconButton(onClick = { onRemove(index) }, enabled = !exporting) {
                        Icon(Icons.Default.Close, contentDescription = "Remove clip ${index + 1}",
                            tint = Color.White)
                    }
                }
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(),
            enabled = !exporting && clips.size < BlinkReelClipPolicy.MAX_CLIPS,
            onClick = onAdd) { Text("Add more clips") }
        if (exporting) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth()
            )
            Text("Combining: ${progress.coerceIn(0, 100)}%",
                color = Color.White, fontSize = 12.sp)
        }
        error?.let { Text(it, color = Color(0xFFFFA1A6), fontSize = 12.sp) }
        Button(modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !exporting && clips.size in 2..BlinkReelClipPolicy.MAX_CLIPS,
            onClick = onExport,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5148F2)),
            shape = RoundedCornerShape(13.dp)) {
            Text("Combine & Edit Reel", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Thumbnail frames come from the user's actual selected clip, never sample media. */
@Composable
private fun BlinkReferenceClipThumbnail(videoUri: String) {
    val context = LocalContext.current
    val frames by produceState(initialValue = emptyList<android.graphics.Bitmap>(), key1 = videoUri) {
        value = reelFilmstrip(context, android.net.Uri.parse(videoUri), count = 1)
    }
    Box(
        modifier = Modifier.size(width = 52.dp, height = 68.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF282D36)),
        contentAlignment = Alignment.Center
    ) {
        val frame = frames.firstOrNull()
        if (frame != null && !frame.isRecycled) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Selected video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.VideoLibrary,
                contentDescription = "Video preview unavailable",
                tint = Color(0xFFF06380),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

package com.example.ui.screens

import com.example.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.blinkng.shared.notification.BlinkStatusVideoTrimPolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.notification.BlinkStatusTextPolicy
import com.example.data.models.UserProfile
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple

/** Simple text-photo-video Status creator. Text drafts are device-local per user. */
@Composable
fun CreateStoryScreen(
    profile: UserProfile,
    isUploading: Boolean,
    onBack: () -> Unit,
    onPublish: (String, String, Boolean) -> Unit,
    onPublishText: (String, String) -> Unit
) {
    val context = LocalContext.current
    val stored = remember(profile.id) { BlinkStatusDraftStore.load(context, profile.id) }
    var caption by remember(profile.id) { mutableStateOf(stored.caption) }
    var statusText by remember(profile.id) { mutableStateOf(stored.text) }
    var background by remember(profile.id) { mutableStateOf(stored.background) }
    var textMode by remember(profile.id) { mutableStateOf(stored.textMode) }
    var selectedUri by remember(profile.id) { mutableStateOf<String?>(null) }
    var isVideo by remember(profile.id) { mutableStateOf(false) }
    var photoTurns by remember(profile.id) { mutableStateOf(0) }
    var cropPortrait by remember(profile.id) { mutableStateOf(false) }
    var editedPreviewUri by remember(profile.id) { mutableStateOf<String?>(null) }
    var isEditingPhoto by remember(profile.id) { mutableStateOf(false) }
    var photoEditError by remember(profile.id) { mutableStateOf<String?>(null) }
    var videoDurationMs by remember(profile.id) { mutableStateOf(0L) }
    var videoStartMs by remember(profile.id) { mutableStateOf(0L) }
    var videoEndMs by remember(profile.id) { mutableStateOf(0L) }
    var trimmedVideoUri by remember(profile.id) { mutableStateOf<String?>(null) }
    var isTrimmingVideo by remember(profile.id) { mutableStateOf(false) }
    var videoError by remember(profile.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedUri, isVideo) {
        videoDurationMs = 0
        videoStartMs = 0
        videoEndMs = 0
        trimmedVideoUri = null
        videoError = null
        if (selectedUri != null && isVideo) {
            try {
                val source = Uri.parse(selectedUri)
                val duration = withContext(Dispatchers.IO) {
                    BlinkStatusVideoTrimmer.durationMs(context, source)
                }
                BlinkStatusVideoTrimPolicy.window(duration, 0)
                videoDurationMs = duration
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                videoError = "This video couldn't be opened for safe trimming."
            }
        }
    }

    LaunchedEffect(selectedUri, isVideo, videoDurationMs, videoStartMs, videoEndMs) {
        trimmedVideoUri = null
        isTrimmingVideo = false
        if (selectedUri != null && isVideo && videoDurationMs > 0) {
            try {
                val window = BlinkStatusVideoTrimPolicy.window(
                    videoDurationMs, videoStartMs, videoEndMs
                )
                if (BlinkStatusVideoTrimPolicy.needsExport(videoDurationMs, window)) {
                    isTrimmingVideo = true
                    videoError = null
                    delay(400) // prevent transcoding/muxing for every drag event
                    trimmedVideoUri = BlinkStatusVideoTrimmer.export(
                        context, Uri.parse(selectedUri), window
                    ).toString()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                videoError = "Video trimming failed. Try another section or video."
            } finally {
                isTrimmingVideo = false
            }
        }
    }

    // Process off the main thread. A key change cancels stale previews.
    LaunchedEffect(selectedUri, isVideo, photoTurns, cropPortrait) {
        editedPreviewUri = null
        photoEditError = null
        if (isVideo || selectedUri == null ||
            (photoTurns == 0 && !cropPortrait)) {
            isEditingPhoto = false
        } else {
            isEditingPhoto = true
            try {
                editedPreviewUri = BlinkStatusPhotoEditor.edit(
                    context, Uri.parse(selectedUri!!), photoTurns, cropPortrait
                ).toString()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                photoEditError = "Photo editing failed. Reset edits or choose another photo."
            } finally {
                isEditingPhoto = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUri = it.toString(); textMode = false; isVideo = false
            photoTurns = 0; cropPortrait = false
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUri = it.toString(); textMode = false; isVideo = true
            photoTurns = 0; cropPortrait = false
        }
    }

    LaunchedEffect(profile.id, caption, statusText, background, textMode) {
        BlinkStatusDraftStore.save(
            context, profile.id,
            BlinkStatusDraftStore.Draft(statusText, caption, background, textMode)
        )
    }

    val validText = BlinkStatusTextPolicy.text(statusText)
    val canShare = when {
        textMode -> validText != null
        selectedUri == null -> false
        isVideo -> {
            if (videoDurationMs < BlinkStatusVideoTrimPolicy.MIN_CLIP_MS ||
                videoError != null || isTrimmingVideo) false
            else {
                val w = BlinkStatusVideoTrimPolicy.window(
                    videoDurationMs, videoStartMs, videoEndMs
                )
                !BlinkStatusVideoTrimPolicy.needsExport(videoDurationMs, w) ||
                    trimmedVideoUri != null
            }
        }
        photoTurns == 0 && !cropPortrait -> true
        else -> editedPreviewUri != null && !isEditingPhoto
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    BlinkPurple.copy(alpha = .10f),
                    BlinkPink.copy(alpha = .06f)
                )
            )
        )
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isUploading) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Add Status", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (textMode) validText?.let { onPublishText(it, background) }
                        else {
                            val toPublish = if (isVideo) {
                                val w = BlinkStatusVideoTrimPolicy.window(
                                    videoDurationMs, videoStartMs, videoEndMs
                                )
                                if (BlinkStatusVideoTrimPolicy.needsExport(videoDurationMs, w))
                                    trimmedVideoUri else selectedUri
                            } else if (photoTurns != 0 || cropPortrait) editedPreviewUri
                            else selectedUri
                            toPublish?.let { onPublish(it, caption.trim(), isVideo) }
                        }
                    },
                    enabled = canShare && !isUploading && !isEditingPhoto && !isTrimmingVideo,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    if (isUploading) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    ) else Text("Share")
                }
            }

            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = profile.avatarUrl,
                    error = painterResource(R.drawable.ic_default_profile),
                    fallback = painterResource(R.drawable.ic_default_profile),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Bold)
                    Text(
                        "@${profile.username} • disappears after 24h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    ) },
                    enabled = !isUploading, modifier = Modifier.weight(1f)
                ) { Text("Photo") }
                OutlinedButton(
                    onClick = { videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    ) },
                    enabled = !isUploading, modifier = Modifier.weight(1f)
                ) { Text("Video") }
                OutlinedButton(
                    onClick = { selectedUri = null; textMode = true },
                    enabled = !isUploading, modifier = Modifier.weight(1f)
                ) { Text("Text") }
            }
            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        textMode -> {
                            val safeColour = BlinkStatusTextPolicy.background(background)
                            Box(
                                Modifier.fillMaxSize().background(
                                    Color(android.graphics.Color.parseColor(safeColour))
                                ), contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = statusText,
                                    onValueChange = {
                                        statusText = it.take(BlinkStatusTextPolicy.MAX_LENGTH)
                                    },
                                    enabled = !isUploading,
                                    textStyle = TextStyle(
                                        fontSize = 25.sp, lineHeight = 31.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center, color = Color.White
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.Center) {
                                            if (statusText.isEmpty()) {
                                                Text(
                                                    "What's happening on campus?",
                                                    color = Color.White.copy(alpha = .70f),
                                                    textAlign = TextAlign.Center,
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                                Text(
                                    "${statusText.length}/${BlinkStatusTextPolicy.MAX_LENGTH}",
                                    color = Color.White.copy(alpha = .75f),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        selectedUri != null && !isVideo -> {
                            AsyncImage(
                                model = editedPreviewUri ?: selectedUri,
                                contentDescription = "Status photo preview",
                                contentScale = if (cropPortrait) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isEditingPhoto) {
                                CircularProgressIndicator()
                            }
                        }
                        selectedUri != null && isVideo -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary, contentDescription = null,
                                tint = BlinkPink, modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Video selected", fontWeight = FontWeight.Bold)
                            val durationLabel = if (videoDurationMs > 0) {
                                "${videoDurationMs / 1000} seconds"
                            } else "Loading video…"
                            Text(durationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isTrimmingVideo) {
                                Spacer(Modifier.height(12.dp))
                                CircularProgressIndicator()
                                Text("Preparing trimmed video", fontSize = 12.sp)
                            }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Create a new status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Choose Photo, Video or Text above",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (selectedUri != null && !textMode) {
                        TextButton(
                            onClick = { selectedUri = null },
                            enabled = !isUploading,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { Text("Change") }
                    }
                }
            }
            if (selectedUri != null && isVideo && !textMode) {
                if (videoDurationMs > 0) {
                    val window = BlinkStatusVideoTrimPolicy.window(
                        videoDurationMs, videoStartMs, videoEndMs
                    )
                    val startMax = (videoDurationMs -
                        BlinkStatusVideoTrimPolicy.MIN_CLIP_MS).toFloat()
                    val endMin = (window.startMs +
                        BlinkStatusVideoTrimPolicy.MIN_CLIP_MS).toFloat()
                    val endMax = minOf(videoDurationMs,
                        window.startMs + BlinkStatusVideoTrimPolicy.MAX_CLIP_MS).toFloat()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        Text("Trim video · max 30 seconds",
                            style = MaterialTheme.typography.labelMedium)
                        Text("Start: ${window.startMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = window.startMs.toFloat(),
                            onValueChange = { videoStartMs = it.toLong() },
                            valueRange = 0f..startMax,
                            enabled = !isUploading
                        )
                        Text("End: ${window.endMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = window.endMs.toFloat(),
                            onValueChange = { videoEndMs = it.toLong() },
                            valueRange = endMin..endMax,
                            enabled = !isUploading
                        )
                        Text(
                            "Clip duration: ${window.durationMs / 1000}s (keyframe-aligned)",
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(
                            onClick = { videoStartMs = 0; videoEndMs = 0 },
                            enabled = !isUploading
                        ) { Text("Reset trim") }
                    }
                }
                videoError?.let { message ->
                    Text(
                        message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            if (selectedUri != null && !isVideo && !textMode) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoTurns = (photoTurns + 1) % 4 },
                        enabled = !isUploading && !isEditingPhoto,
                        modifier = Modifier.weight(1f)
                    ) { Text("Rotate 90°") }
                    OutlinedButton(
                        onClick = { cropPortrait = !cropPortrait },
                        enabled = !isUploading && !isEditingPhoto,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (cropPortrait) "Fit photo" else "Crop 9:16") }
                    TextButton(
                        onClick = { photoTurns = 0; cropPortrait = false },
                        enabled = !isUploading && !isEditingPhoto
                    ) { Text("Reset") }
                }
                photoEditError?.let { message ->
                    Text(
                        message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            if (textMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BlinkStatusTextPolicy.backgrounds.forEach { choice ->
                        Box(
                            modifier = Modifier.size(if (background == choice) 39.dp else 33.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(choice)))
                                .clickable(enabled = !isUploading) { background = choice }
                        )
                    }
                }
                // Lightweight text stamps: inserted into the status text itself,
                // not a misleading visual-only sticker overlay.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("🔥", "❤️", "🎉", "😂", "⭐", "📚", "🎓", "🏆").forEach { stamp ->
                        TextButton(
                            onClick = {
                                val appended = statusText + stamp
                                if (appended.length <= BlinkStatusTextPolicy.MAX_LENGTH) {
                                    statusText = appended
                                }
                            },
                            enabled = !isUploading
                        ) {
                            Text(stamp, fontSize = 23.sp)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(
                        start = 16.dp, end = 16.dp, bottom = 12.dp
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Draft saved on this device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(
                        onClick = {
                            statusText = ""
                            caption = ""
                            background = BlinkStatusTextPolicy.backgrounds.first()
                            BlinkStatusDraftStore.clear(context, profile.id)
                        }, enabled = !isUploading
                    ) { Text("Discard draft") }
                }
            } else {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(500) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Add a caption…") },
                    enabled = !isUploading,
                    minLines = 2, maxLines = 4, shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

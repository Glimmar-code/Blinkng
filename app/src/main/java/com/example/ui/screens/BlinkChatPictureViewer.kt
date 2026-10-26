package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEvent
import com.blinkng.shared.BlinkChatMediaGesturePolicy
import com.blinkng.shared.BlinkChatMediaSwipe
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.models.ChatMessage
import com.example.data.repository.ProfileRepository
import com.example.data.local.BlinkStickerLibrary
import com.example.data.supabase.BlinkGroupAvatarClient
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen normal picture viewer: black letterboxed canvas, compact caption,
 * controls, and user-initiated download. View-once pictures NEVER use this screen.
 */
@Composable
internal fun BlinkChatPictureViewer(
    message: ChatMessage,
    senderName: String,
    onDismiss: () -> Unit,
    onShowInChat: () -> Unit,
    onForward: () -> Unit,
    onReply: () -> Unit,
    onStar: () -> Unit,
    onDeleteForMe: () -> Unit,
    onOpenAllMedia: () -> Unit,
    onReact: (String) -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    photoIndex: Int = 0,
    totalPhotos: Int = 0,
    groupConversationId: String? = null,
    onAskBlinkAi: (Uri) -> Unit = {},
) {
    if (message.imageViewOnce || message.deletedForEveryone ||
        message.attachedImageUrl.isNullOrBlank()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chromeVisible by remember(message.id) { mutableStateOf(true) }
    var captionExpanded by remember(message.id) { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var rotateDegrees by remember(message.id) { mutableIntStateOf(0) }
    var zoom by remember(message.id) { mutableFloatStateOf(1f) }
    var pan by remember(message.id) { mutableStateOf(Offset.Zero) }
    var horizontalDrag by remember(message.id) { mutableFloatStateOf(0f) }
    var verticalDrag by remember(message.id) { mutableFloatStateOf(0f) }
    var confirmation by remember(message.id) { mutableStateOf<String?>(null) }
    var editingPhoto by remember(message.id) { mutableStateOf<Uri?>(null) }
    var settingProfilePhoto by remember(message.id) { mutableStateOf(false) }
    var savingSticker by remember(message.id) { mutableStateOf(false) }
    var settingGroupIcon by remember(message.id) { mutableStateOf(false) }
    var downloadJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var progress by remember(message.id) { mutableIntStateOf(-1) }
    var downloadError by remember(message.id) { mutableStateOf<String?>(null) }
    var savedUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var pendingLocalAction by remember(message.id) { mutableStateOf<String?>(null) }
    var reactionMenu by remember { mutableStateOf(false) }
    val time = remember(message.rawTimestamp, message.timestamp) {
        runCatching {
            OffsetDateTime.parse(message.rawTimestamp).format(
                DateTimeFormatter.ofPattern("dd MMMM, h:mm a")
            )
        }.getOrDefault(message.timestamp)
    }

    // The original viewer only enabled actions after an unrelated manual gallery save.
    // A selected action now asks for that download once and resumes automatically.
    fun downloadToGallery() {
        if (downloadJob?.isActive == true) return
        downloadError = null
        progress = 0
        downloadJob = scope.launch {
            runCatching {
                saveChatPhotoToGallery(context, message.attachedImageUrl!!) {
                    progress = it
                }
            }.onSuccess { savedUri = it; progress = 100 }
                .onFailure {
                    downloadError = if (it is CancellationException) null
                        else "Download failed. Tap download to retry."
                    pendingLocalAction = null
                    progress = -1
                }
        }
    }
    fun requestLocalAction(action: String) {
        moreOpen = false
        pendingLocalAction = action
        if (savedUri == null && downloadJob?.isActive != true) {
            confirmation = "download_required"
        }
    }
    LaunchedEffect(savedUri, pendingLocalAction) {
        val local = savedUri ?: return@LaunchedEffect
        val action = pendingLocalAction ?: return@LaunchedEffect
        pendingLocalAction = null
        when (action) {
            "edit" -> editingPhoto = local
            "share", "external_search" -> {
                if (action == "external_search") {
                    confirmation = "external_search"
                } else runCatching {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(local) ?: "image/*"
                            putExtra(Intent.EXTRA_STREAM, local)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share picture"
                    ))
                }.onFailure { downloadError = "No compatible sharing app available." }
            }
            "gallery" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(local, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { downloadError = "No compatible gallery app available." }
            "sticker", "ai", "group", "profile" -> confirmation = action
            "wallpaper" -> runCatching {
                context.startActivity(
                    android.app.WallpaperManager.getInstance(context)
                        .getCropAndSetWallpaperIntent(local)
                )
            }.onFailure { downloadError = "Wallpaper setting unavailable on this device." }
        }
    }
    DisposableEffect(message.id) { onDispose { downloadJob?.cancel() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha =
            BlinkChatMediaGesturePolicy.backdropAlpha(verticalDrag, 1200f)))) {
            AsyncImage(
                model = message.attachedImageUrl,
                contentDescription = "Full-size shared photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotateDegrees.toFloat()
                        val dismissFraction = (kotlin.math.abs(verticalDrag) / 1600f).coerceIn(0f, .18f)
                        scaleX = zoom * (1f - dismissFraction)
                        scaleY = zoom * (1f - dismissFraction)
                        translationX = pan.x + horizontalDrag
                        translationY = pan.y + verticalDrag
                    }
                    .pointerInput(message.id, hasPrevious, hasNext) {
                        // One gesture owner prevents pan/zoom, horizontal paging, and
                        // vertical dismissal from fighting for pointer consumption.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var travelX = 0f
                            var travelY = 0f
                            var multiTouch = false
                            var previousPinchDistance = 0f
                            var previousCentroid: Offset? = null
                            do {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }
                                if (active.size >= 2) {
                                    multiTouch = true
                                    val centroid = (active[0].position + active[1].position) / 2f
                                    val distance = (active[0].position - active[1].position).getDistance()
                                    if (previousPinchDistance > 0f) {
                                        zoom = (zoom * distance / previousPinchDistance)
                                            .coerceIn(1f, 4f)
                                        pan = if (zoom > 1f) pan +
                                            (centroid - (previousCentroid ?: centroid))
                                            else Offset.Zero
                                    }
                                    previousPinchDistance = distance
                                    previousCentroid = centroid
                                    horizontalDrag = 0f
                                    verticalDrag = 0f
                                    active.forEach { it.consume() }
                                } else if (active.size == 1) {
                                    val change = active.first()
                                    val delta = change.position - change.previousPosition
                                    if (zoom > BlinkChatMediaGesturePolicy.ZOOM_LOCK_THRESHOLD) {
                                        pan += delta
                                    } else if (!multiTouch) {
                                        travelX += delta.x
                                        travelY += delta.y
                                        if (kotlin.math.abs(travelY) >
                                            kotlin.math.abs(travelX) * 1.25f) {
                                            verticalDrag = travelY
                                            horizontalDrag = 0f
                                        } else if (kotlin.math.abs(travelX) >
                                            kotlin.math.abs(travelY) * 1.25f) {
                                            horizontalDrag = travelX
                                            verticalDrag = 0f
                                        }
                                    }
                                    // Leave stationary taps to detectTapGestures below.
                                    if (delta.getDistance() > 0f) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                            if (!multiTouch) {
                                when (BlinkChatMediaGesturePolicy.action(
                                    travelX, travelY,
                                    BlinkChatMediaGesturePolicy.SWIPE_THRESHOLD_DP.dp.toPx(),
                                    zoom, hasPrevious, hasNext
                                )) {
                                    BlinkChatMediaSwipe.DISMISS -> onDismiss()
                                    BlinkChatMediaSwipe.NEXT -> onNext()
                                    BlinkChatMediaSwipe.PREVIOUS -> onPrevious()
                                    BlinkChatMediaSwipe.NONE -> Unit
                                }
                            }
                            horizontalDrag = 0f
                            verticalDrag = 0f
                            if (zoom <= BlinkChatMediaGesturePolicy.ZOOM_LOCK_THRESHOLD) {
                                zoom = 1f
                                pan = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onTap = { chromeVisible = !chromeVisible },
                            onDoubleTap = {
                                zoom = if (zoom > 1f) 1f else 2f
                                pan = Offset.Zero
                            }
                        )
                    }
            )
            if (chromeVisible) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Color(0xFF101317)).statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(senderName, fontSize = 16.sp, color = Color.White, maxLines = 1)
                        Text(
                            if (totalPhotos > 1 && photoIndex in 1..totalPhotos)
                                "$time  ·  $photoIndex of $totalPhotos"
                            else time,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = .75f),
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = {
                        if (downloadJob?.isActive == true) {
                            downloadJob?.cancel()
                        } else downloadToGallery()
                    }) {
                        Icon(
                            if (downloadJob?.isActive == true) Icons.Default.Close
                            else Icons.Default.Download,
                            contentDescription = if (downloadJob?.isActive == true)
                                "Cancel download" else "Download picture",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onForward) {
                        Icon(Icons.Default.Forward, "Forward picture", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Default.MoreVert, "More picture actions", tint = Color.White)
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("Edit") },
                                onClick = { requestLocalAction("edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem(text = { Text("Share") },
                                onClick = { requestLocalAction("share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) })
                            DropdownMenuItem(text = { Text(if (message.isStarred) "Unstar" else "Star") },
                                onClick = { moreOpen = false; onStar() },
                                leadingIcon = { Icon(Icons.Default.Star, null) })
                            DropdownMenuItem(text = { Text("Show in chat") },
                                onClick = { moreOpen = false; onShowInChat() },
                                leadingIcon = { Icon(Icons.Default.Chat, null) })
                            DropdownMenuItem(text = { Text("All media") },
                                onClick = { moreOpen = false; onOpenAllMedia() },
                                leadingIcon = { Icon(Icons.Default.GridView, null) })
                            DropdownMenuItem(text = { Text("Rotate") },
                                onClick = { moreOpen = false; rotateDegrees = (rotateDegrees + 90) % 360 },
                                leadingIcon = { Icon(Icons.Default.RotateRight, null) })
                            DropdownMenuItem(text = { Text("View in gallery") },
                                onClick = { requestLocalAction("gallery") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
                            DropdownMenuItem(text = { Text("Create reusable sticker") },
                                enabled = !savingSticker,
                                onClick = { requestLocalAction("sticker") },
                                leadingIcon = { Icon(Icons.Default.EmojiEmotions, null) })
                            DropdownMenuItem(text = { Text("Ask BLINK AI about this picture") },
                                onClick = { requestLocalAction("ai") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                            if (!groupConversationId.isNullOrBlank()) {
                                DropdownMenuItem(text = { Text("Set as group picture") },
                                    enabled = !settingGroupIcon,
                                    onClick = { requestLocalAction("group") },
                                    leadingIcon = { Icon(Icons.Default.Groups, null) })
                            }
                            DropdownMenuItem(text = { Text("Set as my profile picture") },
                                enabled = !settingProfilePhoto,
                                onClick = { requestLocalAction("profile") },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, null) })
                            DropdownMenuItem(text = { Text("Set as wallpaper") },
                                onClick = { requestLocalAction("wallpaper") },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null) })
                            DropdownMenuItem(text = { Text("Search with another app") },
                                onClick = { requestLocalAction("external_search") },
                                leadingIcon = { Icon(Icons.Default.Search, null) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete for me") },
                                onClick = { moreOpen = false; onDeleteForMe() },
                                leadingIcon = { Icon(Icons.Default.Delete, null) })
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0xFF0D1317))
                        .navigationBarsPadding()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
                ) {
                    if (progress in 0..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(), color = Color(0xFF41AFF7)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("Downloading · $progress% · Tap ✕ to cancel",
                            color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                    }
                    if (savedUri != null) {
                        Text("Saved to gallery", color = Color(0xFF62D9A3), fontSize = 11.sp)
                    }
                    downloadError?.let { Text(it, color = Color(0xFFFF8585), fontSize = 11.sp) }
                    val caption = message.text.takeUnless {
                        it.equals("Image", true) || it.equals("Photo", true)
                    }.orEmpty()
                    if (caption.isNotBlank()) {
                        Text(caption, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp,
                            maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis)
                        if (!captionExpanded && caption.length > 115) {
                            Text("Read more", fontWeight = FontWeight.Bold,
                                color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.clickable { captionExpanded = true })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF23313C)).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reply", color = Color.White.copy(alpha = .55f),
                            fontSize = 14.sp, modifier = Modifier.weight(1f).clickable(onClick = onReply)
                                .padding(10.dp))
                        Text("❤️", fontSize = 20.sp, modifier = Modifier.clickable { onReact("❤️") }
                            .padding(6.dp))
                        Text("🙏", fontSize = 20.sp, modifier = Modifier.clickable { onReact("🙏") }
                            .padding(6.dp))
                        Box {
                            IconButton(onClick = { reactionMenu = true }) {
                                Icon(Icons.Default.AddReaction, "Other reactions", tint = Color.White)
                            }
                            DropdownMenu(expanded = reactionMenu, onDismissRequest = { reactionMenu = false }) {
                                listOf("👍","😂","😮","🔥","💜","👏").forEach { emoji ->
                                    DropdownMenuItem(text = { Text(emoji) },
                                        onClick = { reactionMenu = false; onReact(emoji) })
                                }
                            }
                        }
                    }
                }
            }
            if (hasPrevious || hasNext) {
            Row(Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick=onPrevious,enabled=hasPrevious) {
                    Icon(Icons.Default.ChevronLeft,"Previous photo",
                        tint=if(hasPrevious) Color.White else Color.Transparent)
                }
                IconButton(onClick=onNext,enabled=hasNext) {
                    Icon(Icons.Default.ChevronRight,"Next photo",
                        tint=if(hasNext) Color.White else Color.Transparent)
                }
            }
            }
        }
    }
    editingPhoto?.let { saved ->
        BlinkUniversalPhotoEditor(
            originalUri=saved,
            onEdited={ result ->
                editingPhoto=null
                scope.launch {
                    runCatching { saveLocalChatPhotoToGallery(context,result) }
                        .onSuccess { savedUri=it; downloadError=null; confirmation="edit_saved" }
                        .onFailure { downloadError="Edited picture could not be saved to gallery." }
                }
            },
            onDismiss={editingPhoto=null}
        )
    }
    if (confirmation != null) {
        AlertDialog(
            onDismissRequest={
                if (confirmation == "download_required") pendingLocalAction = null
                confirmation = null
            },
            title={Text(when(confirmation) {
                "download_required" -> "Download photo to continue?"
                "external_search" -> "Share photo with another app?"
                "profile" -> "Use this picture as your BLINK avatar?"
                "group" -> "Set this group picture?"
                "sticker" -> "Save as reusable sticker?"
                "ai" -> "Send image to BLINK AI?"
                else -> "Edited copy created"
            })},
            text={Text(when(confirmation) {
                "download_required" ->
                    "BLINK needs a local copy to use this feature. Download and save the photo to your gallery, then the selected action will open automatically."
                "external_search" ->
                    "Another app may upload or analyze this image. Choose only an app you trust."
                "profile" ->
                    "Your profile picture will change for other students. The chat picture stays unchanged."
                "group" ->
                    "Only group owners and admins can change this picture. It will be visible to group members."
                "sticker" ->
                    "Save a reusable copy in this device's private sticker library. Do not save someone else's private photo without permission."
                "ai" ->
                    "BLINK AI may process the selected picture. It will be attached to a draft; nothing is sent until you press Send."
                else -> "Your original picture in BLINK has not been changed."
            })},
            confirmButton={TextButton(onClick={
                if (confirmation=="download_required") {
                    downloadToGallery()
                } else if (confirmation=="sticker") {
                    val image = savedUri
                    if (image != null) {
                        savingSticker=true
                        scope.launch {
                            val outcome=runCatching {
                                val id=SupabaseService().getCurrentUserId()
                                    ?: error("Sign in to save stickers.")
                                BlinkStickerLibrary.create(context,id,image)
                            }
                            downloadError=outcome.fold(
                                onSuccess={ "Sticker saved. Open Attachments → Stickers to reuse." },
                                onFailure={ it.message ?: "Couldn't save sticker." })
                            savingSticker=false
                        }
                    }
                } else if (confirmation=="ai") {
                    savedUri?.let(onAskBlinkAi)
                } else if (confirmation=="group") {
                    val image = savedUri
                    val group = groupConversationId
                    if (image != null && group != null) {
                        settingGroupIcon=true
                        scope.launch {
                            val result=runCatching {
                                val service=SupabaseService()
                                val id=service.getCurrentUserId()
                                    ?: error("Sign in to edit group pictures.")
                                val avatar=ProfileRepository().uploadAvatar(context,image,id)
                                    ?: error("Unable to upload group picture.")
                                check(BlinkGroupAvatarClient.setAvatar(group,avatar)) {
                                    "Only group owners or admins can change this picture."
                                }
                            }
                            downloadError=result.fold(
                                onSuccess={ "Group picture changed. Refresh the chat to see it." },
                                onFailure={ it.message ?: "Couldn't update group picture." }
                            )
                            settingGroupIcon=false
                        }
                    }
                } else if (confirmation=="profile") {
                    val picture = savedUri
                    if (picture != null) {
                        settingProfilePhoto=true
                        scope.launch {
                            val success=runCatching {
                                val repository=ProfileRepository()
                                val id=SupabaseService().getCurrentUserId()
                                    ?: error("Please sign in.")
                                val current=repository.fetchById(id)
                                    ?: error("Profile unavailable.")
                                val url=repository.uploadAvatar(context,picture,id)
                                    ?: error("Photo upload failed.")
                                check(repository.updateProfile(current.copy(avatarUrl=url))) {
                                    "Could not update your profile."
                                }
                            }.isSuccess
                            downloadError=if(success) "Your BLINK profile picture was updated."
                                else "Could not update profile picture. Try again."
                            settingProfilePhoto=false
                        }
                    }
                } else if (confirmation=="external_search") {
                    savedUri?.let { uri ->
                        runCatching {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type="image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM,uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Choose an image search app"
                            ))
                        }
                    }
                }
                confirmation=null
            }) { Text("Continue") }},
            dismissButton={TextButton(onClick={
                if (confirmation == "download_required") pendingLocalAction = null
                confirmation = null
            }){Text("Cancel")}}
        )
    }
}

/** Save a newly edited private copy without changing the original chat picture. */
private suspend fun saveLocalChatPhotoToGallery(context: Context, localUri: Uri): Uri =
    withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        require(localUri.scheme == "file")
        val file = java.io.File(localUri.path.orEmpty()).canonicalFile
        require(file.isFile && file.length() in 1..(30*1024*1024))
        require(file.parentFile == context.cacheDir.canonicalFile &&
            file.name.startsWith("blink-edited-"))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,
                "BLINK_edited_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BLINK")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val resolver = context.contentResolver
        val saved = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
            ?: throw IOException("Gallery unavailable")
        try {
            resolver.openOutputStream(saved)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IOException("Could not open gallery destination")
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0)
            resolver.update(saved,values,null,null)
            saved
        } catch (error: Throwable) {
            resolver.delete(saved,null,null)
            throw error
        }
    }

/** User-triggered MediaStore write; never changes the remote or original image. */
private suspend fun saveChatPhotoToGallery(
    context: Context, remoteUrl: String, onProgress: (Int) -> Unit
): Uri = withContext(Dispatchers.IO) {
    require(remoteUrl.startsWith("https://")) { "An HTTPS photo URL is required." }
    // Never create an orphan MediaStore item on legacy Android versions.
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Saving to gallery needs Android 10 or newer."
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BLINK_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BLINK")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Cannot create gallery image.")
    try {
        val req = Request.Builder().url(remoteUrl).get().build()
        OkHttpClient().newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image unavailable (${response.code})")
            val body = response.body ?: throw IOException("Image response was empty.")
            val total = body.contentLength()
            resolver.openOutputStream(uri)?.use { output ->
                body.byteStream().use { stream ->
                    val buffer = ByteArray(16_384)
                    var saved = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = stream.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        saved += n
                        if (total > 0) withContext(Dispatchers.Main) {
                            onProgress((saved * 100 / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            } ?: throw IOException("Gallery is unavailable.")
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

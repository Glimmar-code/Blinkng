package com.example.ui.screens

import com.example.performance.rememberBlinkReduceMotion
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import com.blinkng.shared.BlinkElasticGesturePhysics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.blinkng.shared.BlinkChatDownloadPolicy
import com.blinkng.shared.BlinkChatMediaGesturePolicy
import com.blinkng.shared.BlinkChatMediaSwipe
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.data.local.BlinkPrivateMediaVault
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
    galleryPhotos: List<ChatMessage> = emptyList(),
    onPickPhoto: (ChatMessage) -> Unit = {},
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
    var viewerHeightPx by remember { mutableFloatStateOf(1200f) }
    var draggingPicture by remember(message.id) { mutableStateOf(false) }
    val elasticActivation = with(LocalDensity.current) { 110.dp.toPx() }
    val quickFlickMin = with(LocalDensity.current) { 28.dp.toPx() }
    val quickFlickVelocity = with(LocalDensity.current) { 1100.dp.toPx() }
    val reducedMotion = rememberBlinkReduceMotion()
    val animatedVerticalDrag by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else BlinkElasticGesturePhysics.visualDistance(verticalDrag, elasticActivation),
        animationSpec = if (draggingPicture || reducedMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
        label = "chat_photo_vertical_drag"
    )
    val animatedHorizontalDrag by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else BlinkElasticGesturePhysics.visualDistance(horizontalDrag, elasticActivation),
        animationSpec = if (draggingPicture || reducedMotion) snap() else spring(stiffness = Spring.StiffnessMedium),
        label = "chat_photo_horizontal_drag"
    )
    var confirmation by remember(message.id) { mutableStateOf<String?>(null) }
    var editingPhoto by remember(message.id) { mutableStateOf<Uri?>(null) }
    var pendingEditedUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var stickerEditingPhoto by remember(message.id) { mutableStateOf<Uri?>(null) }
    var settingProfilePhoto by remember(message.id) { mutableStateOf(false) }
    var savingSticker by remember(message.id) { mutableStateOf(false) }
    var settingGroupIcon by remember(message.id) { mutableStateOf(false) }
    var vaultOpen by remember(message.id) { mutableStateOf(false) }
    var vaultBusy by remember(message.id) { mutableStateOf(false) }
    var downloadJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var progress by remember(message.id) { mutableIntStateOf(-1) }
    var downloadError by remember(message.id) { mutableStateOf<String?>(null) }
    // A private local copy is separate from an explicitly exported gallery photo.
    var savedUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var galleryUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var pendingLocalAction by remember(message.id) { mutableStateOf<String?>(null) }
    var reactionMenu by remember { mutableStateOf(false) }
    val time = remember(message.rawTimestamp, message.timestamp) {
        runCatching {
            OffsetDateTime.parse(message.rawTimestamp).format(
                DateTimeFormatter.ofPattern("dd MMMM, h:mm a")
            )
        }.getOrDefault(message.timestamp)
    }

    fun preparePrivateCopy() {
        if (downloadJob?.isActive == true) return
        downloadError = null
        progress = 0
        downloadJob = scope.launch {
            runCatching {
                preparePrivateChatPhoto(context, message.attachedImageUrl!!) { progress = it }
            }.onSuccess { savedUri = it; progress = 100 }
                .onFailure {
                    downloadError = if (it is CancellationException) null
                        else "Could not prepare the private picture. Try again."
                    pendingLocalAction = null
                    progress = -1
                }
        }
    }
    // Public gallery export must be an explicit action, not an editing prerequisite.
    fun downloadToGallery() {
        if (downloadJob?.isActive == true) return
        downloadError = null
        progress = 0
        downloadJob = scope.launch {
            runCatching {
                saveChatPhotoToGallery(context, message.attachedImageUrl!!) {
                    progress = it
                }
            }.onSuccess { galleryUri = it; progress = 100 }
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
        val ready = if (action == "gallery") galleryUri != null else savedUri != null
        if (!ready && downloadJob?.isActive != true) {
            confirmation = if (action == "gallery") "gallery_download" else "private_copy_required"
        }
    }
    LaunchedEffect(savedUri, galleryUri, pendingLocalAction) {
        val action = pendingLocalAction ?: return@LaunchedEffect
        val local = (if (action == "gallery") galleryUri else savedUri)
            ?: return@LaunchedEffect
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
            "sticker", "ai", "group", "profile", "vault" -> confirmation = action
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
        Box(Modifier.fillMaxSize()
            .onSizeChanged { viewerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .background(Color.Black.copy(alpha =
                BlinkChatMediaGesturePolicy.backdropAlpha(animatedVerticalDrag, viewerHeightPx)))) {
            AsyncImage(
                model = message.attachedImageUrl,
                contentDescription = "Full-size shared photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotateDegrees.toFloat()
                        val dismissFraction = (kotlin.math.abs(animatedVerticalDrag) /
                            viewerHeightPx).coerceIn(0f, .18f)
                        scaleX = zoom * (1f - dismissFraction)
                        scaleY = zoom * (1f - dismissFraction)
                        translationX = pan.x + animatedHorizontalDrag
                        translationY = pan.y + animatedVerticalDrag
                    }
                    .pointerInput(message.id, hasPrevious, hasNext) {
                        // One gesture owner prevents pan/zoom, horizontal paging, and
                        // vertical dismissal from fighting for pointer consumption.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var travelX = 0f
                            var travelY = 0f
                            val velocityTracker = VelocityTracker()
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
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    if (delta.getDistance() > 0f) draggingPicture = true
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
                                val direction = BlinkChatMediaGesturePolicy.action(
                                    travelX, travelY,
                                    BlinkChatMediaGesturePolicy.SWIPE_THRESHOLD_DP.dp.toPx(),
                                    zoom, hasPrevious, hasNext
                                )
                                val speed = velocityTracker.calculateVelocity()
                                val horizontalIntent = kotlin.math.abs(travelX) >
                                    kotlin.math.abs(travelY) * 1.25f
                                val verticalIntent = kotlin.math.abs(travelY) >
                                    kotlin.math.abs(travelX) * 1.25f
                                val nextByFlick = zoom <= 1.05f && horizontalIntent &&
                                    travelX < 0f && hasNext &&
                                    BlinkElasticGesturePhysics.shouldCommit(
                                        travelX, speed.x, elasticActivation,
                                        quickFlickMin, quickFlickVelocity
                                    )
                                val previousByFlick = zoom <= 1.05f && horizontalIntent &&
                                    travelX > 0f && hasPrevious &&
                                    BlinkElasticGesturePhysics.shouldCommit(
                                        travelX, speed.x, elasticActivation,
                                        quickFlickMin, quickFlickVelocity
                                    )
                                val dismissByFlick = zoom <= 1.05f && verticalIntent &&
                                    BlinkElasticGesturePhysics.shouldCommit(
                                        travelY, speed.y, elasticActivation,
                                        quickFlickMin, quickFlickVelocity
                                    )
                                when {
                                    direction == BlinkChatMediaSwipe.DISMISS || dismissByFlick -> onDismiss()
                                    direction == BlinkChatMediaSwipe.NEXT || nextByFlick -> onNext()
                                    direction == BlinkChatMediaSwipe.PREVIOUS || previousByFlick -> onPrevious()
                                }
                            }
                            draggingPicture = false
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
                        .background(Color(0xFF09131E)).statusBarsPadding()
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
                            DropdownMenuItem(text = { Text("Save in encrypted Media Vault") },
                                enabled = !vaultBusy,
                                onClick = { requestLocalAction("vault") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) })
                            DropdownMenuItem(text = { Text("Open my Media Vault") },
                                onClick = { moreOpen = false; vaultOpen = true },
                                leadingIcon = { Icon(Icons.Default.Folder, null) })
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
                        .background(Color(0xFF060D16))
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
                    if (galleryUri != null) {
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
                    if (galleryPhotos.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                                .testTag("chat_reference_image_filmstrip"),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            galleryPhotos.take(24).forEach { tile ->
                                val active = tile.id == message.id
                                AsyncImage(
                                    model = tile.imagePreviewUrl ?: tile.attachedImageUrl,
                                    contentDescription = if (active) "Current photo" else "Open photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 54.dp, height = 50.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(
                                            if (active) 2.dp else 1.dp,
                                            if (active) Color(0xFF1685FF)
                                            else Color(0xFF25384D),
                                            RoundedCornerShape(7.dp)
                                        )
                                        .clickable { onPickPhoto(tile) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF17283A)).padding(horizontal = 8.dp),
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
    if (vaultOpen) BlinkMediaVaultDialog(onDismiss = { vaultOpen = false })
    stickerEditingPhoto?.let { source ->
        BlinkUniversalPhotoEditor(
            originalUri = source,
            onEdited = { editedUri ->
                stickerEditingPhoto = null
                savingSticker = true
                scope.launch {
                    val outcome = runCatching {
                        val id = SupabaseService().getCurrentUserId()
                            ?: error("Sign in to save stickers.")
                        BlinkStickerLibrary.create(context, id, editedUri)
                    }
                    downloadError = outcome.fold(
                        onSuccess = { "Sticker saved in your private library." },
                        onFailure = { it.message ?: "Couldn't save sticker." }
                    )
                    savingSticker = false
                }
            },
            onDismiss = { stickerEditingPhoto = null }
        )
    }
    editingPhoto?.let { saved ->
        BlinkUniversalPhotoEditor(
            originalUri=saved,
            onEdited={ result ->
                editingPhoto=null
                // Editing a received photo does not imply consent to save it publicly.
                pendingEditedUri = result
                confirmation = "edit_save_required"
            },
            onDismiss={editingPhoto=null}
        )
    }
    if (confirmation != null) {
        AlertDialog(
            onDismissRequest={
                if (confirmation == "private_copy_required" || confirmation == "gallery_download") pendingLocalAction = null
                if (confirmation == "edit_save_required") pendingEditedUri = null
                confirmation = null
            },
            title={Text(when(confirmation) {
                "private_copy_required" -> "Prepare private picture?"
                "gallery_download" -> "Save picture to gallery?"
                "external_search" -> "Share photo with another app?"
                "profile" -> "Use this picture as your BLINK avatar?"
                "group" -> "Set this group picture?"
                "sticker" -> "Save as reusable sticker?"
                "vault" -> "Encrypt picture in Media Vault?"
                "ai" -> "Send image to BLINK AI?"
                "edit_save_required" -> "Save edited copy to gallery?"
                else -> "Picture action"
            })},
            text={Text(when(confirmation) {
                "private_copy_required" ->
                    "BLINK will prepare a temporary app-private copy for this action. It will not appear in your gallery unless you save it separately."
                "gallery_download" ->
                    "Save this picture to your device gallery? Other gallery apps may be able to see it."
                "external_search" ->
                    "Another app may upload or analyze this image. Choose only an app you trust."
                "profile" ->
                    "Your profile picture will change for other students. The chat picture stays unchanged."
                "group" ->
                    "Only group owners and admins can change this picture. It will be visible to group members."
                "sticker" ->
                    "Open the editor to crop, draw and add text before saving this private sticker. Do not reuse someone's private photo without permission."
                "vault" ->
                    "Save this received picture as an encrypted, device-only copy? Your vault is account-scoped, not backed up or synced, and may be lost if you uninstall BLINK."
                "ai" ->
                    "BLINK AI may process the selected picture. It will be attached to a draft; nothing is sent until you press Send."
                "edit_save_required" ->
                    "Your original message is unchanged. Save your edited picture to the device gallery, or discard this edit."
                else -> "Your original picture in BLINK has not been changed."
            })},
            confirmButton={TextButton(onClick={
                if (confirmation=="private_copy_required") {
                    preparePrivateCopy()
                } else if (confirmation=="gallery_download") {
                    downloadToGallery()
                } else if (confirmation=="vault") {
                    savedUri?.let { image ->
                        vaultBusy = true
                        scope.launch {
                            val result = runCatching {
                                BlinkPrivateMediaVault.add(context, image)
                            }
                            downloadError = result.fold(
                                onSuccess = { "Photo encrypted in your private Media Vault." },
                                onFailure = { it.message ?: "Unable to save to Media Vault." }
                            )
                            vaultBusy = false
                        }
                    }
                } else if (confirmation=="sticker") {
                    // Editing a private copy never alters the original received message.
                    // The user explicitly completes the editor before the sticker is saved.
                    stickerEditingPhoto = savedUri
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
                } else if (confirmation=="edit_save_required") {
                    pendingEditedUri?.let { edited ->
                        scope.launch {
                            runCatching { saveLocalChatPhotoToGallery(context, edited) }
                                .onSuccess {
                                    galleryUri = it
                                    downloadError = "Edited picture saved to gallery."
                                }
                                .onFailure {
                                    downloadError = "Could not save edited picture to gallery."
                                }
                        }
                    }
                    pendingEditedUri = null
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
                if (confirmation == "private_copy_required" || confirmation == "gallery_download") pendingLocalAction = null
                if (confirmation == "edit_save_required") pendingEditedUri = null
                confirmation = null
            }){Text(if (confirmation == "edit_save_required") "Discard" else "Cancel")}}
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

/**
 * Consent-triggered, app-private temporary image. The configured FileProvider only
 * grants access to chosen recipient apps; opening the editor never saves to gallery.
 */
private suspend fun preparePrivateChatPhoto(
    context: Context, remoteUrl: String, onProgress: (Int) -> Unit
): Uri = withContext(Dispatchers.IO) {
    require(BlinkChatDownloadPolicy.acceptsSource(remoteUrl)) {
        "A secure picture URL is required."
    }
    val privateDir = File(context.cacheDir, "blink-share").apply {
        check(isDirectory || mkdirs()) { "Cannot create a private picture cache." }
    }
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    privateDir.listFiles()?.forEach { old ->
        if (old.isFile && old.name.startsWith("blink-chat-") && old.lastModified() < cutoff) {
            old.delete()
        }
    }
    val local = File.createTempFile("blink-chat-", ".jpg", privateDir)
    try {
        val req = Request.Builder().url(remoteUrl).get().build()
        OkHttpClient().newCall(req).execute().use { response ->
            if (!response.isSuccessful || !response.request.url.isHttps) {
                throw IOException("Image unavailable or redirected insecurely (${response.code})")
            }
            val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                ?.lowercase()
            if (!BlinkChatDownloadPolicy.acceptsMime(mime)) throw IOException("Not an image.")
            val body = response.body ?: throw IOException("Image response was empty.")
            val maxBytes = BlinkChatDownloadPolicy.MAX_IMAGE_BYTES
            val total = body.contentLength()
            if (!BlinkChatDownloadPolicy.acceptsLength(total)) throw IOException("Image is too large.")
            var received = 0L
            local.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n == -1) break
                        received += n
                        if (received > maxBytes) throw IOException("Image is too large.")
                        output.write(buffer, 0, n)
                        if (total > 0) withContext(Dispatchers.Main) {
                            onProgress((received * 100 / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            if (received == 0L) throw IOException("Image response was empty.")
        }
        FileProvider.getUriForFile(context, "${context.packageName}.chatfiles", local)
    } catch (error: Throwable) {
        local.delete()
        throw error
    }
}

/** User-triggered MediaStore write; never changes the remote or original image. */
private suspend fun saveChatPhotoToGallery(
    context: Context, remoteUrl: String, onProgress: (Int) -> Unit
): Uri = withContext(Dispatchers.IO) {
    require(BlinkChatDownloadPolicy.acceptsSource(remoteUrl)) { "An HTTPS photo URL is required." }
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
            if (!response.isSuccessful || !response.request.url.isHttps) {
                throw IOException("Image unavailable or redirected insecurely (${response.code})")
            }
            val mime = response.header("Content-Type")
            if (!BlinkChatDownloadPolicy.acceptsMime(mime)) throw IOException("Not an image.")
            val body = response.body ?: throw IOException("Image response was empty.")
            val total = body.contentLength()
            val maxBytes = BlinkChatDownloadPolicy.MAX_IMAGE_BYTES
            if (!BlinkChatDownloadPolicy.acceptsLength(total)) throw IOException("Image is too large.")
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
                        if (saved > maxBytes) throw IOException("Image is too large.")
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

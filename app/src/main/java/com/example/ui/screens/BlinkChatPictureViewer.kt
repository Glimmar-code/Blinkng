package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
) {
    if (message.imageViewOnce || message.deletedForEveryone ||
        message.attachedImageUrl.isNullOrBlank()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chromeVisible by remember(message.id) { mutableStateOf(true) }
    var captionExpanded by remember(message.id) { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var rotateDegrees by remember(message.id) { mutableIntStateOf(0) }
    var downloadJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var progress by remember(message.id) { mutableIntStateOf(-1) }
    var downloadError by remember(message.id) { mutableStateOf<String?>(null) }
    var savedUri by remember(message.id) { mutableStateOf<Uri?>(null) }
    var reactionMenu by remember { mutableStateOf(false) }
    val time = remember(message.rawTimestamp, message.timestamp) {
        runCatching {
            OffsetDateTime.parse(message.rawTimestamp).format(
                DateTimeFormatter.ofPattern("dd MMMM, h:mm a")
            )
        }.getOrDefault(message.timestamp)
    }
    DisposableEffect(message.id) { onDispose { downloadJob?.cancel() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = message.attachedImageUrl,
                contentDescription = "Full-size shared photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { rotationZ = rotateDegrees.toFloat() }
                    .clickable { chromeVisible = !chromeVisible }
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
                        Text(time, fontSize = 11.sp, color = Color.White.copy(alpha = .75f))
                    }
                    IconButton(onClick = {
                        if (downloadJob?.isActive == true) {
                            downloadJob?.cancel()
                        } else {
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
                                        progress = -1
                                    }
                            }
                        }
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
                            DropdownMenuItem(text = { Text("Share") }, enabled = savedUri != null,
                                onClick = {
                                    moreOpen = false
                                    savedUri?.let { uri ->
                                        runCatching {
                                            context.startActivity(Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/jpeg"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }, "Share picture"
                                            ))
                                        }
                                    }
                                }, leadingIcon = { Icon(Icons.Default.Share, null) })
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
                            DropdownMenuItem(text = { Text("View in gallery") }, enabled = savedUri != null,
                                onClick = {
                                    moreOpen = false
                                    savedUri?.let { uri ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            })
                                        }
                                    }
                                }, leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) })
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
        }
    }
}

/** User-triggered MediaStore write; never changes the remote or original image. */
private suspend fun saveChatPhotoToGallery(
    context: Context, remoteUrl: String, onProgress: (Int) -> Unit
): Uri = withContext(Dispatchers.IO) {
    require(remoteUrl.startsWith("https://")) { "An HTTPS photo URL is required." }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BLINK_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BLINK")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    // On Android <29, prefer the system picker/share flow rather than broad storage writes.
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Saving to gallery needs Android 10 or newer."
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

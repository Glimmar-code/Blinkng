package com.example.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MessagePalette
import com.blinkng.shared.BlinkChatMediaSelectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

private data class BlinkGalleryPhoto(
    val uri: Uri, val album: String, val name: String
)

private fun hasGalleryPermission(context: Context): Boolean {
    fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

/**
 * Limited, local MediaStore discovery only after the user grants media access.
 * Android's privacy-scoped Photo Picker stays available without any permission.
 * Never reads media bytes or uploads pictures while browsing the sheet.
 */
private suspend fun readAvailableGalleryPhotos(context: Context): List<BlinkGalleryPhoto> =
    withContext(Dispatchers.IO) {
        if (!hasGalleryPermission(context)) return@withContext emptyList()
        val output = ArrayList<BlinkGalleryPhoto>(80)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val albumIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val fileNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext() && output.size < 80) {
                    val id = cursor.getLong(idIndex)
                    val album = if (albumIndex >= 0) cursor.getString(albumIndex)
                        ?: "Gallery" else "Gallery"
                    val name = if (fileNameIndex >= 0) cursor.getString(fileNameIndex)
                        .orEmpty() else ""
                    output.add(BlinkGalleryPhoto(ContentUris.withAppendedId(uri, id), album, name))
                }
            }
        }
        output
    }

/**
 * In-chat Telegram-reference-inspired attachment surface. The first tile launches
 * the camera; remaining items are selected from an authorized, recent photo list.
 * User-selected images are not transmitted until the subsequent review is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlinkChatMediaGallerySheet(
    palette: MessagePalette,
    permissionVersion: Int,
    onRequestPermission: () -> Unit,
    onSystemPhotoPicker: () -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onCamera: () -> Unit,
    onDocument: () -> Unit,
    onDocumentScan: () -> Unit,
    onAudio: () -> Unit,
    onVoiceNote: () -> Unit,
    onVideo: () -> Unit,
    onSticker: () -> Unit,
    onViewOnce: () -> Unit,
    onShareLocation: () -> Unit,
    onPickContact: () -> Unit,
    onShareArticle: () -> Unit,
    onCreatePoll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permitted by remember(permissionVersion) {
        mutableStateOf(hasGalleryPermission(context))
    }
    var images by remember(permissionVersion) { mutableStateOf<List<BlinkGalleryPhoto>>(emptyList()) }
    var album by remember { mutableStateOf("Recent") }
    var showAttachmentMenu by remember { mutableStateOf(true) }
    var gallerySearchOpen by remember { mutableStateOf(false) }
    var galleryQuery by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<Uri>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(permissionVersion, permitted) {
        if (permitted) images = readAvailableGalleryPhotos(context)
    }
    val albums = remember(images) { listOf("Recent") + images.map { it.album }.distinct().take(16) }
    val visible = remember(images, album, galleryQuery, newestFirst) {
        val inAlbum = if (album == "Recent") images else images.filter { it.album == album }
        val filtered = inAlbum.filter {
            galleryQuery.isBlank() || it.name.contains(galleryQuery, ignoreCase = true) ||
                it.album.contains(galleryQuery, ignoreCase = true)
        }
        if (newestFirst) filtered else filtered.asReversed()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (palette.isLight) Color(0xFFF5F5F8) else Color(0xFF101B2B),
        contentColor = palette.textPrimary,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(38.dp, 4.dp)
                .clip(CircleShape).background(Color.Gray.copy(alpha = .55f)))
        }
    ) {
        if (showAttachmentMenu) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Share with this chat",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = palette.textPrimary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close attachments",
                            tint = palette.textSecondary)
                    }
                }
                Text(
                    "Choose what to send",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                data class Shortcut(
                    val title: String,
                    val icon: androidx.compose.ui.graphics.vector.ImageVector,
                    val tint: Color,
                    val action: () -> Unit
                )
                val shortcuts = listOf(
                    Shortcut("Gallery", Icons.Default.PhotoLibrary,
                        Color(0xFF21BFA0), { showAttachmentMenu = false }),
                    Shortcut("Camera", Icons.Default.CameraAlt,
                        Color(0xFF288DFF), onCamera),
                    Shortcut("Video", Icons.Default.Videocam,
                        Color(0xFF7652F5), onVideo),
                    Shortcut("Audio", Icons.Default.MusicNote,
                        Color(0xFFE66ADE), onAudio),
                    Shortcut("Document", Icons.Default.Description,
                        Color(0xFF2C9DF3), onDocument),
                    Shortcut("Poll", Icons.Default.Poll,
                        Color(0xFFF3B914), onCreatePoll),
                    Shortcut("Location", Icons.Default.Place,
                        Color(0xFFEF695B), onShareLocation),
                    Shortcut("Contact", Icons.Default.Person,
                        Color(0xFF428AFF), onPickContact),
                    Shortcut("Sticker", Icons.Default.EmojiEmotions,
                        Color(0xFFED70BB), onSticker)
                )
                shortcuts.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { shortcut ->
                            Column(
                                Modifier.weight(1f)
                                    .clickable(onClickLabel = "Attach ${shortcut.title}",
                                        onClick = shortcut.action)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = shortcut.tint,
                                    modifier = Modifier.size(62.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            shortcut.icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(27.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    shortcut.title,
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = palette.border)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onVoiceNote) { Text("Voice note") }
                    OutlinedButton(onClick = onViewOnce) { Text("View once") }
                    OutlinedButton(onClick = onDocumentScan) { Text("Scan") }
                    OutlinedButton(onClick = onShareArticle) { Text("Article") }
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) { Text("Cancel", color = palette.textSecondary) }
            }
        } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showAttachmentMenu = true }) {
                Icon(Icons.Default.ArrowBack, "Back to attachment options",
                    tint = palette.textPrimary)
            }
            Text("Gallery", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            if (selected.isEmpty()) {
                IconButton(onClick = { gallerySearchOpen = !gallerySearchOpen }) {
                    Icon(Icons.Default.Search, "Search images by file name",
                        tint = palette.textPrimary)
                }
                IconButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.Default.Sort, "Reverse gallery order",
                        tint = palette.textPrimary)
                }
            }
            if (selected.isNotEmpty()) {
                TextButton(onClick = { selected.clear() }) { Text("Clear") }
                Button(
                    onClick = { onPhotosSelected(selected.toList()) },
                    enabled = BlinkChatMediaSelectionPolicy.canContinue(
                        selected.map { it.toString() }
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C9DF3))
                ) { Text("Next (${selected.size})", color = Color.White) }
            } else {
                TextButton(onClick = onSystemPhotoPicker) { Text("All photos") }
            }
        }
        if (selected.size > 1) {
            Text(
                "Send order — move pictures left or right",
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                selected.toList().forEachIndexed { index, uri ->
                    Column(
                        modifier = Modifier.width(94.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Photo ${index + 1} in sending order",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(76.dp, 62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                "${index + 1}",
                                color = Color.White,
                                modifier = Modifier.align(Alignment.TopStart)
                                    .background(Color(0xCC0579BD), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.Center) {
                            IconButton(
                                onClick = {
                                    val moved = BlinkChatMediaSelectionPolicy.move(
                                        selected.map(Uri::toString), index, index - 1
                                    )
                                    selected.clear()
                                    selected.addAll(moved.map(Uri::parse))
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, "Move photo earlier")
                            }
                            IconButton(
                                onClick = {
                                    val moved = BlinkChatMediaSelectionPolicy.move(
                                        selected.map(Uri::toString), index, index + 1
                                    )
                                    selected.clear()
                                    selected.addAll(moved.map(Uri::parse))
                                },
                                enabled = index < selected.lastIndex,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, "Move photo later")
                            }
                        }
                    }
                }
            }
        }
        if (gallerySearchOpen && permitted) {
            OutlinedTextField(
                value = galleryQuery,
                onValueChange = { galleryQuery = it.take(100) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                label = { Text("Search file names or albums") }
            )
        }
        if (permitted && albums.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                albums.forEach { name ->
                    FilterChip(
                        selected = album == name,
                        onClick = { album = name },
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }
        }
        if (!permitted) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Choose photos privately, or allow access to show recent pictures here.",
                    color = palette.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRequestPermission) { Text("Allow recent photos") }
                    Button(onClick = onSystemPhotoPicker) { Text("Choose photos") }
                }
            }
        }
        // Three columns, selection circles, and a camera shortcut mimic the screenshot,
        // but access is granted per Android's permission model.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().heightIn(min = 185.dp, max = 390.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "camera") {
                Box(
                    Modifier.aspectRatio(.79f).background(Color(0xFF29333D))
                        .clickable(onClick = onCamera),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, "Take a picture", tint = Color.White,
                        modifier = Modifier.size(30.dp))
                }
            }
            itemsIndexed(visible, key = { _, photo -> photo.uri.toString() }) { _, photo ->
                val position = selected.indexOf(photo.uri)
                Box(Modifier.aspectRatio(.79f)
                    .clickable {
                        val newIds = BlinkChatMediaSelectionPolicy.toggle(
                            selected.map { it.toString() }, photo.uri.toString()
                        )
                        selected.clear()
                        selected.addAll(newIds.map(Uri::parse))
                    }) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Select photo from ${photo.album}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(25.dp)
                            .border(2.dp, Color.White, CircleShape)
                            .clip(CircleShape)
                            .background(if (position >= 0) Color(0xFF2C9DF3)
                            else Color.Black.copy(alpha = .4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (position >= 0) Text("${position + 1}", color = Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            data class Destination(val title: String,
                val icon: androidx.compose.ui.graphics.vector.ImageVector, val click: () -> Unit)
            listOf(
                Destination("Gallery", Icons.Default.PhotoLibrary, onSystemPhotoPicker),
                Destination("File", Icons.Default.InsertDriveFile, onDocument),
                Destination("Scan", Icons.Default.Description, onDocumentScan),
                Destination("Camera", Icons.Default.CameraAlt, onCamera),
                Destination("Video", Icons.Default.Videocam, onVideo),
                Destination("Music", Icons.Default.MusicNote, onAudio),
                Destination("Sticker", Icons.Default.EmojiEmotions, onSticker),
                Destination("Location", Icons.Default.Place, onShareLocation),
                Destination("Contact", Icons.Default.Person, onPickContact),
                Destination("Article", Icons.Default.Article, onShareArticle),
                Destination("Poll", Icons.Default.Poll, onCreatePoll),
                Destination("View once", Icons.Default.VisibilityOff, onViewOnce)
            ).forEach { entry ->
                Column(
                    Modifier.widthIn(min = 60.dp).clickable(onClick = entry.click)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(entry.icon, entry.title, tint = Color(0xFF65B9FF),
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(entry.title, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

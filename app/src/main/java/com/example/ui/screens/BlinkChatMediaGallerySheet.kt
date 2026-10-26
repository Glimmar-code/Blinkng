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

private data class BlinkGalleryPhoto(val uri: Uri, val album: String)

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
        val projection = arrayOf(MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val albumIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext() && output.size < 80) {
                    val id = cursor.getLong(idIndex)
                    val album = if (albumIndex >= 0) cursor.getString(albumIndex)
                        ?: "Gallery" else "Gallery"
                    output.add(BlinkGalleryPhoto(ContentUris.withAppendedId(uri, id), album))
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
    onAudio: () -> Unit,
    onVideo: () -> Unit,
    onSticker: () -> Unit,
    onViewOnce: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permitted by remember(permissionVersion) {
        mutableStateOf(hasGalleryPermission(context))
    }
    var images by remember(permissionVersion) { mutableStateOf<List<BlinkGalleryPhoto>>(emptyList()) }
    var album by remember { mutableStateOf("Recent") }
    val selected = remember { mutableStateListOf<Uri>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(permissionVersion, permitted) {
        if (permitted) images = readAvailableGalleryPhotos(context)
    }
    val albums = remember(images) { listOf("Recent") + images.map { it.album }.distinct().take(16) }
    val visible = remember(images, album) {
        if (album == "Recent") images else images.filter { it.album == album }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (palette.isLight) Color(0xFFF5F5F8) else Color(0xFF171C24),
        contentColor = palette.textPrimary,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).size(38.dp, 4.dp)
                .clip(CircleShape).background(Color.Gray.copy(alpha = .55f)))
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gallery", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
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
                Destination("Camera", Icons.Default.CameraAlt, onCamera),
                Destination("Video", Icons.Default.Videocam, onVideo),
                Destination("Music", Icons.Default.MusicNote, onAudio),
                Destination("Sticker", Icons.Default.EmojiEmotions, onSticker),
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
        Spacer(Modifier.navigationBarsPadding())
    }
}

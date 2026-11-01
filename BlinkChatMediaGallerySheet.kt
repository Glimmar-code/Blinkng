package com.example.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ───────────────────────── Model + device loader ───────────────────────── */

data class GalleryItem(
    val id: String,
    val uri: String,                 // content:// uri string
    val isVideo: Boolean,
    val durationLabel: String? = null,
)

class DeviceGalleryState(
    val items: List<GalleryItem>,
    val hasAccess: Boolean,
    val requestAccess: () -> Unit,
)

/**
 * Loads recent photos + videos from MediaStore (newest first) and handles the runtime permission.
 * Manifest needs READ_MEDIA_IMAGES + READ_MEDIA_VIDEO (API 33+), READ_EXTERNAL_STORAGE (maxSdk 32),
 * and optionally READ_MEDIA_VISUAL_USER_SELECTED (API 34+).
 * If you already have BlinkDevicePhotoLibrary, feed its results into GalleryItem instead.
 */
@Composable
fun rememberDeviceGallery(limit: Int = 400): DeviceGalleryState {
    val context = LocalContext.current
    val base = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun check(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun access(): Boolean = base.all { check(it) } ||
        (Build.VERSION.SDK_INT >= 34 && check("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))

    var granted by remember { mutableStateOf(access()) }
    var items by remember { mutableStateOf(emptyList<GalleryItem>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = access()
    }
    LaunchedEffect(granted) {
        if (granted) items = withContext(Dispatchers.IO) { queryGallery(context, limit) }
    }
    return DeviceGalleryState(items, granted) {
        launcher.launch(
            if (Build.VERSION.SDK_INT >= 34) base + "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" else base
        )
    }
}

private fun queryGallery(context: Context, limit: Int): List<GalleryItem> {
    val out = ArrayList<GalleryItem>()
    val files = MediaStore.Files.getContentUri("external")
    val type = MediaStore.Files.FileColumns.MEDIA_TYPE
    val projection = buildList {
        add(MediaStore.Files.FileColumns._ID); add(type)
        if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.DURATION)
    }.toTypedArray()
    val selection = "$type=? OR $type=?"
    val args = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    runCatching {
        context.contentResolver.query(files, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
            ?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = c.getColumnIndexOrThrow(type)
                val durCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(idCol)
                    val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    out += GalleryItem(
                        id = id.toString(),
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        isVideo = video,
                        durationLabel = if (video && durCol >= 0) formatDuration(c.getLong(durCol)) else null,
                    )
                }
            }
    }
    return out
}

/* ───────────────────────── Sheet ───────────────────────── */

/**
 * Telegram-style gallery: camera tile first, 3-column grid, numbered multi-select (photos + videos),
 * selection order preserved, tap a tile with nothing selected to preview, Send button with count.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BlinkChatMediaGallerySheet(
    items: List<GalleryItem>,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onCamera: () -> Unit,
    onPreview: (GalleryItem) -> Unit,
    onSend: (List<GalleryItem>) -> Unit,
    onDismiss: () -> Unit,
    maxSelection: Int = 30,
) {
    val selected = remember { mutableStateListOf<String>() }          // ids, in tap order
    val byId = remember(items) { items.associateBy { it.id } }

    fun toggle(id: String) {
        if (id in selected) selected.remove(id)
        else if (selected.size < maxSelection) selected.add(id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0F11),
        contentColor = Color.White,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxHeight(0.94f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                    Text("Recent", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    AnimatedVisibility(selected.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Text("${selected.size} selected", color = BlinkChatColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (!hasAccess) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Allow access to your photos and videos to share them in chats.", color = BlinkChatColors.Muted, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRequestAccess, colors = ButtonDefaults.buttonColors(containerColor = BlinkChatColors.Accent)) {
                            Text("Allow access", color = Color.White)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = if (selected.isNotEmpty()) 96.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "camera") {
                            Box(
                                Modifier.aspectRatio(1f).background(BlinkChatColors.Panel).clickable(onClick = onCamera),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.CameraAlt, "Camera", tint = Color.White, modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text("Camera", color = Color(0xFFD0D0D4), fontSize = 13.sp)
                                }
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            val index = selected.indexOf(item.id)
                            GalleryCell(
                                item = item, number = if (index >= 0) index + 1 else null,
                                onCircle = { toggle(item.id) },
                                onTap = { if (selected.isNotEmpty()) toggle(item.id) else onPreview(item) },
                                onLongTap = { toggle(item.id) },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(Spring.DampingRatioLowBouncy)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xF2151518)).navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selected.size} of $maxSelection", color = Color(0xFFD0D0D4),
                        fontSize = 15.sp, modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier.clip(CircleShape).background(BlinkChatColors.Accent)
                            .clickable { onSend(selected.mapNotNull { byId[it] }) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Send", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryCell(
    item: GalleryItem, number: Int?,
    onCircle: () -> Unit, onTap: () -> Unit, onLongTap: () -> Unit,
) {
    val selected = number != null
    val scale by animateFloatAsState(if (selected) 0.88f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "cellScale")
    Box(
        Modifier.aspectRatio(1f).background(Color.Black)
            .combinedClickable(onClick = onTap, onLongClick = onLongTap)
    ) {
        BlinkMediaImage(item.uri, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale })
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                item.durationLabel?.let { Text(it, color = Color.White, fontSize = 11.sp) }
            }
        }
        if (selected) Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(34.dp).clickable(onClick = onCircle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (selected) BlinkChatColors.Accent else Color(0x44000000))
                    .border(1.8.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(selected, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = scaleOut()) {
                    Text("${number ?: ""}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

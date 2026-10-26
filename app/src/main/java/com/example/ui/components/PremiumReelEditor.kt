package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 9:16 reference-inspired BLINK Reel editor. All enabled editing controls affect
 * the actual resulting MP4 through ReelVideoEdits; failed export never replaces input.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PremiumReelEditor(
    uri: String,
    onRemove: () -> Unit,
    onEdited: (String) -> Unit,
    onExporting: (Boolean) -> Unit,
    onPendingEdits: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var trimStartMs by remember(uri) { mutableLongStateOf(0L) }
    var trimEndMs by remember(uri) { mutableLongStateOf(0L) }
    var playing by remember(uri) { mutableStateOf(true) }
    var muteOriginal by remember(uri) { mutableStateOf(false) }
    var grayscale by remember(uri) { mutableStateOf(false) }
    var rotation by remember(uri) { mutableFloatStateOf(0f) }
    var overlayText by remember(uri) { mutableStateOf("") }
    var sticker by remember(uri) { mutableStateOf("") }
    var musicUri by remember(uri) { mutableStateOf<String?>(null) }
    var isExporting by remember(uri) { mutableStateOf(false) }
    var exportError by remember(uri) { mutableStateOf<String?>(null) }
    var filmstrip by remember(uri) { mutableStateOf(emptyList<android.graphics.Bitmap>()) }
    var showAdvanced by remember(uri) { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        picked -> musicUri = picked?.toString()
    }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(uri) {
        filmstrip = reelFilmstrip(context, Uri.parse(uri))
    }
    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0L) {
                val freshDuration = player.duration
                if (durationMs == 0L) trimEndMs = freshDuration
                durationMs = freshDuration
            }
            positionMs = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            if (durationMs > 0L && trimEndMs > trimStartMs && positionMs >= trimEndMs) {
                player.seekTo(trimStartMs)
            }
            delay(160)
        }
    }
    fun display(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
    }
    val spec = ReelEditSpec(
        startMs = trimStartMs,
        endMs = trimEndMs.coerceAtMost(durationMs),
        muteOriginalAudio = muteOriginal,
        grayscale = grayscale,
        rotationDegrees = rotation,
        overlayText = overlayText.trim(),
        sticker = sticker,
        musicUri = musicUri
    )
    val hasPendingEdits = durationMs >= 500L && spec.hasChanges(durationMs)
    LaunchedEffect(hasPendingEdits) { onPendingEdits(hasPendingEdits) }
    val white = Color.White
    val surface = Color(0xFF1B1B20)
    val accent = Color(0xFFB976FC)
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).testTag("premium_reel_editor"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                .aspectRatio(9f / 16f).clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF121217)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            if (overlayText.isNotBlank() || sticker.isNotBlank()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sticker.isNotBlank()) Text(sticker, fontSize = 40.sp)
                    if (overlayText.isNotBlank())
                        Text(overlayText, fontWeight = FontWeight.Black, fontSize = 20.sp,
                            color = white, maxLines = 4)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    enabled = !isExporting,
                    onClick = { player.seekTo(trimStartMs) },
                    modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)
                ) { Icon(Icons.Default.Replay, "Replay clip", tint = white) }
                IconButton(
                    enabled = !isExporting,
                    onClick = onRemove,
                    modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)
                ) { Icon(Icons.Default.Close, "Remove reel", tint = white) }
            }
        }
        Surface(color = surface, shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 5_000L).coerceAtLeast(trimStartMs)) },
                        enabled = !isExporting) {
                        Icon(Icons.Default.Replay5, "Back five seconds", tint = white)
                    }
                    Text("${display(positionMs)} / ${display(durationMs)}",
                        color = white, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { player.seekTo((player.currentPosition + 5_000L).coerceAtMost(trimEndMs)) },
                        enabled = !isExporting) {
                        Icon(Icons.Default.Forward5, "Forward five seconds", tint = white)
                    }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() },
                        enabled = !isExporting) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play or pause reel", tint = white)
                    }
                }

                if (filmstrip.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))) {
                        filmstrip.forEach { thumbnail ->
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).height(55.dp)
                            )
                        }
                    }
                }
                Text("Trim start · ${display(trimStartMs)}", fontSize = 11.sp, color = white)
                Slider(value = if (durationMs > 0L) trimStartMs.toFloat() / durationMs else 0f,
                    enabled = !isExporting && durationMs > 500L,
                    onValueChange = {
                        val next = (it * durationMs).toLong()
                        trimStartMs = next.coerceAtMost((trimEndMs - 500L).coerceAtLeast(0L))
                        player.seekTo(trimStartMs)
                    }, modifier = Modifier.height(28.dp))
                Text("Trim end · ${display(trimEndMs)}", fontSize = 11.sp, color = white)
                Slider(value = if (durationMs > 0L) trimEndMs.toFloat() / durationMs else 1f,
                    enabled = !isExporting && durationMs > 500L,
                    onValueChange = {
                        trimEndMs = (it * durationMs).toLong()
                            .coerceIn((trimStartMs + 500L).coerceAtMost(durationMs), durationMs)
                        player.seekTo(trimEndMs - 100L)
                    }, modifier = Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = muteOriginal, onClick = {
                        muteOriginal = !muteOriginal
                        player.volume = if (muteOriginal) 0f else 1f
                    }, label = { Text("Mute", fontSize = 11.sp) }, enabled = !isExporting)
                    FilterChip(selected = grayscale, onClick = { grayscale = !grayscale },
                        label = { Text("B&W", fontSize = 11.sp) }, enabled = !isExporting)
                    FilterChip(selected = rotation != 0f, onClick = {
                        rotation = (rotation + 90f) % 360f
                    }, label = { Text("Rotate", fontSize = 11.sp) }, enabled = !isExporting)
                    TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !isExporting) {
                        Text(if (showAdvanced) "Less" else "More", fontSize = 11.sp, color = accent)
                    }
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it.take(120) },
                        label = { Text("Text on video") }, singleLine = true,
                        enabled = !isExporting, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = sticker,
                        onValueChange = { sticker = it.take(16) },
                        label = { Text("Sticker / emoji") }, singleLine = true,
                        enabled = !isExporting, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            enabled = !isExporting,
                            onClick = { audioPicker.launch("audio/*") }
                        ) { Text(if (musicUri == null) "Add music" else "Change music") }
                        if (musicUri != null) TextButton(onClick = { musicUri = null }) {
                            Text("Remove music")
                        }
                    }
                }
                if (grayscale || rotation != 0f) {
                    Text("Color and rotation are rendered into the exported MP4.",
                        color = Color.LightGray, fontSize = 10.sp)
                }
                exportError?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp)
                }
                if (isExporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Rendering the actual edited video…", color = white, fontSize = 11.sp)
                } else {
                    Button(
                        enabled = hasPendingEdits,
                        onClick = {
                            exportError = null
                            isExporting = true
                            onExporting(true)
                            player.pause()
                            scope.launch {
                                try {
                                    val result = exportEditedReel(context, uri, durationMs, spec)
                                    onEdited(result)
                                    Toast.makeText(context, "Reel edits applied to the video.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    exportError = e.message ?: "Unable to render video."
                                } finally {
                                    isExporting = false
                                    onExporting(false)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("apply_reel_video_edits")
                    ) { Text("Apply edits to video", fontWeight = FontWeight.Bold) }
                }
                Text(
                    "Edits are saved in a private MP4 before publishing. Unapplied changes won't be posted.",
                    fontSize = 10.sp, color = Color.White.copy(alpha = .65f)
                )
            }
        }
    }
}

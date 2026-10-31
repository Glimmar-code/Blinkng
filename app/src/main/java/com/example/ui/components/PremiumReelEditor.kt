package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import com.example.data.local.PersistentTextDraftStore
import org.json.JSONObject
import java.security.MessageDigest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
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
    draftScope: String,
    onRemove: () -> Unit,
    onEdited: (String) -> Unit,
    onExporting: (Boolean) -> Unit,
    onPendingEdits: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editKey = remember(uri) {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
        "reel_edit_" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    val saved = remember(uri, draftScope) {
        PersistentTextDraftStore.readValue(context, editKey, draftScope)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    }
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
    var trimStartMs by remember(uri, draftScope) { mutableLongStateOf(saved.optLong("trimStart", 0L)) }
    var trimEndMs by remember(uri, draftScope) { mutableLongStateOf(saved.optLong("trimEnd", 0L)) }
    var playing by remember(uri) { mutableStateOf(true) }
    var muteOriginal by remember(uri, draftScope) { mutableStateOf(saved.optBoolean("mute", false)) }
    var grayscale by remember(uri, draftScope) { mutableStateOf(saved.optBoolean("grayscale", false)) }
    var rotation by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("rotation", 0.0).toFloat()) }
    var overlayText by remember(uri, draftScope) { mutableStateOf(saved.optString("text", "")) }
    var sticker by remember(uri, draftScope) { mutableStateOf(saved.optString("sticker", "")) }
    var overlayStartMs by remember(uri, draftScope) { mutableLongStateOf(saved.optLong("overlayStart", 0L)) }
    var overlayEndMs by remember(uri, draftScope) { mutableLongStateOf(saved.optLong("overlayEnd", 0L)) }
    var overlayX by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("x", 0.0).toFloat()) }
    var overlayY by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("y", 0.0).toFloat()) }
    var overlayScale by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("scale", 1.0).toFloat()) }
    var previewWidth by remember(uri) { mutableIntStateOf(1) }
    var previewHeight by remember(uri) { mutableIntStateOf(1) }
    var musicUri by remember(uri, draftScope) { mutableStateOf<String?>(saved.optString("music").takeIf { it.isNotBlank() && it != "null" }) }
    var originalAudioVolume by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("originalVolume", 1.0).toFloat()) }
    var musicVolume by remember(uri, draftScope) { mutableFloatStateOf(saved.optDouble("musicVolume", 1.0).toFloat()) }
    var isExporting by remember(uri) { mutableStateOf(false) }
    var exportPercent by remember(uri) { mutableIntStateOf(0) }
    var exportError by remember(uri) { mutableStateOf<String?>(null) }
    var filmstrip by remember(uri) { mutableStateOf(emptyList<android.graphics.Bitmap>()) }
    var showAdvanced by remember(uri) { mutableStateOf(false) }
    var activeTool by remember(uri) { mutableStateOf("TRIM") }
    val musicPlayer = remember(musicUri) {
        musicUri?.let { selected ->
            runCatching {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(selected))
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false
                    prepare()
                }
            }.getOrNull()
        }
    }
    DisposableEffect(musicPlayer) {
        onDispose { musicPlayer?.release() }
    }
    LaunchedEffect(grayscale, rotation, player) {
        val effects = mutableListOf<androidx.media3.common.Effect>()
        if (grayscale) effects += RgbFilter.createGrayscaleFilter()
        if (rotation != 0f) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(rotation).build()
        }
        runCatching { player.setVideoEffects(effects) }
    }
    LaunchedEffect(musicPlayer, positionMs, playing, musicVolume, trimStartMs) {
        musicPlayer ?: return@LaunchedEffect
        val clipPosition = (positionMs - trimStartMs).coerceAtLeast(0L)
        if (kotlin.math.abs(musicPlayer.currentPosition - clipPosition) > 750L) {
            musicPlayer.seekTo(clipPosition)
        }
        musicPlayer.volume = musicVolume
        if (playing) musicPlayer.play() else musicPlayer.pause()
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            musicUri = picked.toString()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            onExporting(false)
            onPendingEdits(false)
        }
    }
    LaunchedEffect(uri) {
        filmstrip = reelFilmstrip(context, Uri.parse(uri))
    }
    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0L) {
                val freshDuration = player.duration
                if (durationMs == 0L && trimEndMs == 0L) trimEndMs = freshDuration
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
        musicUri = musicUri,
        originalAudioVolume = originalAudioVolume,
        musicVolume = musicVolume,
        overlayStartMs = overlayStartMs.coerceAtLeast(0L),
        overlayEndMs = if (overlayEndMs == 0L) Long.MAX_VALUE else overlayEndMs,
        overlayX = overlayX,
        overlayY = overlayY,
        overlayScale = overlayScale
    )
    val hasPendingEdits = durationMs >= 500L && spec.hasChanges(durationMs)
    LaunchedEffect(hasPendingEdits) { onPendingEdits(hasPendingEdits) }
    LaunchedEffect(spec, durationMs, draftScope) {
        if (durationMs < 500L) return@LaunchedEffect
        if (hasPendingEdits) {
            PersistentTextDraftStore.writeValue(
                context, editKey, draftScope,
                JSONObject().apply {
                    put("trimStart", trimStartMs)
                    put("trimEnd", trimEndMs)
                    put("mute", muteOriginal)
                    put("grayscale", grayscale)
                    put("rotation", rotation)
                    put("text", overlayText)
                    put("sticker", sticker)
                    put("overlayStart", overlayStartMs)
                    put("overlayEnd", overlayEndMs)
                    put("x", overlayX)
                    put("y", overlayY)
                    put("scale", overlayScale)
                    put("music", musicUri ?: JSONObject.NULL)
                    put("originalVolume", originalAudioVolume)
                    put("musicVolume", musicVolume)
                }.toString()
            )
        } else {
            PersistentTextDraftStore.clearValue(context, editKey, draftScope)
        }
    }
    val white = Color.White
    val surface = Color(0xFF1B1B20)
    val accent = Color.White
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black).testTag("premium_reel_editor"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Edit Video", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, modifier = Modifier.weight(1f)
            )
            Text(
                "TRIM  ·  MUSIC  ·  EFFECTS",
                color = Color(0xFFB5B4C7), fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                .aspectRatio(9f / 16f).heightIn(max = 420.dp)
                .clip(RoundedCornerShape(13.dp))
                .testTag("blink_video_editor_preview")
                .background(Color(0xFF121217))
                .onSizeChanged { previewWidth = it.width.coerceAtLeast(1);
                    previewHeight = it.height.coerceAtLeast(1) },
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
            if ((overlayText.isNotBlank() || sticker.isNotBlank()) &&
                spec.overlayVisibleAt(positionMs)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                (overlayX * previewWidth / 2f).roundToInt(),
                                (-overlayY * previewHeight / 2f).roundToInt()
                            )
                        }
                        .graphicsLayer(scaleX = overlayScale, scaleY = overlayScale)
                        .pointerInput(uri) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                overlayX = (overlayX + 2f * drag.x / previewWidth)
                                    .coerceIn(-0.85f, 0.85f)
                                overlayY = (overlayY - 2f * drag.y / previewHeight)
                                    .coerceIn(-0.85f, 0.85f)
                            }
                        }
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sticker.isNotBlank()) Text(sticker, fontSize = 40.sp)
                    if (overlayText.isNotBlank())
                        Text(overlayText, fontWeight = FontWeight.Black, fontSize = 20.sp,
                            color = white, maxLines = 4)
                }
            }
            // Reference-inspired right-hand tools. Every action operates on the
            // real editor state; tools requiring detail open the advanced panel.
            Column(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .padding(end = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = .73f))
                    .padding(vertical = 6.dp, horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val tools: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = listOf(
                    Triple(Icons.Default.MusicNote, "Music", {
                        activeTool = "MUSIC"
                        showAdvanced = true
                        if (musicUri == null) audioPicker.launch(arrayOf("audio/*"))
                    }),
                    Triple(Icons.Default.TextFields, "Text", {
                        activeTool = "TEXT"; showAdvanced = true
                    }),
                    Triple(Icons.Default.EmojiEmotions, "Stickers", {
                        activeTool = "STICKERS"; showAdvanced = true
                    }),
                    Triple(Icons.Default.FilterVintage, "Filters", {
                        activeTool = "FILTERS"; grayscale = !grayscale
                    }),
                    Triple(Icons.Default.AutoAwesome, "Effects", {
                        activeTool = "EFFECTS"; showAdvanced = true
                    }),
                    Triple(Icons.Default.Tune, "Adjust", {
                        activeTool = "ADJUST"; showAdvanced = true
                    }),
                    Triple(Icons.Default.ContentCut, "Trim", {
                        activeTool = "TRIM"; showAdvanced = false
                        player.seekTo(trimStartMs)
                    })
                )
                tools.forEach { (icon, label, action) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = action, enabled = !isExporting,
                            modifier = Modifier.size(36.dp)
                                .testTag("blink_video_tool_" + label.lowercase())) {
                            Icon(icon, contentDescription = label + " editing tool",
                                tint = if (activeTool == label.uppercase()) Color(0xFFA7A0FF)
                                    else Color.White,
                                modifier = Modifier.size(19.dp))
                        }
                        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
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
                    onClick = {
                        PersistentTextDraftStore.clearValue(context, editKey, draftScope)
                        onRemove()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = .45f), CircleShape)
                ) { Icon(Icons.Default.Close, "Remove reel", tint = white) }
            }
        }
        Surface(color = surface, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .testTag("blink_video_editor_timeline")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 5_000L).coerceAtLeast(trimStartMs)) },
                        enabled = !isExporting) {
                        Icon(Icons.Default.Replay5, "Back five seconds", tint = white)
                    }
                    Text("${display(positionMs)} / ${display(durationMs)}",
                        color = white, fontSize = 13.sp, modifier = Modifier.weight(1f),
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Trim video", fontSize = 13.sp, color = white, fontWeight = FontWeight.SemiBold)
                    Text("${display(trimStartMs)} – ${display(trimEndMs)}", fontSize = 12.sp, color = white)
                }
                // A two-handle timeline keeps the selected interval together.
                RangeSlider(
                    value = trimStartMs.toFloat()..trimEndMs.coerceAtLeast(trimStartMs).toFloat(),
                    valueRange = 0f..durationMs.coerceAtLeast(500L).toFloat(),
                    enabled = !isExporting && durationMs >= 500L,
                    onValueChange = { selection ->
                        val newStart = selection.start.toLong().coerceAtLeast(0L)
                            .coerceAtMost(durationMs - 500L)
                        val newEnd = selection.endInclusive.toLong()
                            .coerceIn(newStart + 500L, durationMs)
                        trimStartMs = newStart
                        trimEndMs = newEnd
                        player.seekTo(newStart)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .testTag("blink_video_trim_handles")
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = muteOriginal, onClick = {
                        muteOriginal = !muteOriginal
                        player.volume = if (muteOriginal) 0f else originalAudioVolume
                    }, label = { Text("Mute", fontSize = 11.sp) }, enabled = !isExporting)
                    FilterChip(selected = grayscale, onClick = { grayscale = !grayscale },
                        label = { Text("B&W", fontSize = 11.sp) }, enabled = !isExporting)
                    FilterChip(selected = rotation != 0f, onClick = {
                        rotation = (rotation + 90f) % 360f
                    }, label = { Text("Rotate", fontSize = 11.sp) }, enabled = !isExporting)
                    TextButton(
                        onClick = {
                            showAdvanced = !showAdvanced
                            if (showAdvanced && activeTool == "TRIM") activeTool = "ADJUST"
                        },
                        enabled = !isExporting
                    ) {
                        Text(if (showAdvanced) "Less" else "More", fontSize = 11.sp, color = accent)
                    }
                }
                if (showAdvanced) {
                    Text(activeTool.lowercase().replaceFirstChar { it.uppercase() } + " controls",
                        color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("blink_video_active_tool"))
                    if (activeTool == "TEXT") OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it.take(120) },
                        label = { Text("Text on video") }, singleLine = true,
                        enabled = !isExporting, modifier = Modifier.fillMaxWidth()
                            .testTag("blink_video_overlay_text"))
                    if (activeTool == "STICKERS") OutlinedTextField(
                        value = sticker,
                        onValueChange = { sticker = it.take(16) },
                        label = { Text("Sticker / emoji") }, singleLine = true,
                        enabled = !isExporting, modifier = Modifier.fillMaxWidth()
                            .testTag("blink_video_overlay_sticker"))
                    if ((activeTool == "TEXT" || activeTool == "STICKERS") &&
                        (overlayText.isNotBlank() || sticker.isNotBlank())) {
                        Text("Drag the words or sticker over the video to position them.",
                            color = Color.LightGray, fontSize = 12.sp)
                        Text("Overlay size: ${(overlayScale * 100).toInt()}%",
                            color = white, fontSize = 12.sp)
                        Slider(
                            value = overlayScale, onValueChange = { overlayScale = it },
                            valueRange = 0.5f..2f, enabled = !isExporting
                        )
                        Text("Overlay timing", fontWeight = FontWeight.Medium, color = white,
                            fontSize = 13.sp)
                        val overlayStart = overlayStartMs.coerceIn(0L, durationMs)
                        val overlayEnd = if (overlayEndMs == 0L) durationMs else
                            overlayEndMs.coerceIn(overlayStart, durationMs)
                        RangeSlider(
                            value = overlayStart.toFloat()..overlayEnd.toFloat(),
                            valueRange = 0f..durationMs.coerceAtLeast(500L).toFloat(),
                            enabled = !isExporting && durationMs >= 500L,
                            onValueChange = { range ->
                                overlayStartMs = range.start.toLong().coerceIn(0L, durationMs - 1L)
                                overlayEndMs = range.endInclusive.toLong()
                                    .coerceIn(overlayStartMs + 1L, durationMs)
                                player.seekTo(overlayStartMs)
                            }
                        )
                        Text("${display(overlayStart)} – ${display(overlayEnd)}",
                            fontSize = 12.sp, color = white)
                    }
                    if (activeTool == "EFFECTS") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { grayscale = !grayscale },
                                enabled = !isExporting) {
                                Text(if (grayscale) "Remove B&W" else "Black & white")
                            }
                            OutlinedButton(onClick = {
                                rotation = (rotation + 90f) % 360f
                            }, enabled = !isExporting) { Text("Rotate 90°") }
                        }
                    }
                    if (activeTool == "MUSIC" || activeTool == "ADJUST") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            enabled = !isExporting,
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            modifier = Modifier.testTag("blink_video_pick_music")
                        ) { Text(if (musicUri == null) "Add music" else "Change music") }
                        if (musicUri != null) TextButton(onClick = { musicUri = null }) {
                            Text("Remove music")
                        }
                    }
                    Text("Original audio ${(originalAudioVolume * 100).toInt()}%",
                        color = white, fontSize = 12.sp)
                    Slider(
                        value = originalAudioVolume,
                        onValueChange = {
                            originalAudioVolume = it
                            player.volume = if (muteOriginal) 0f else it
                        },
                        enabled = !isExporting && !muteOriginal
                    )
                    if (musicUri != null) {
                        Text("Added music ${(musicVolume * 100).toInt()}%",
                            color = white, fontSize = 12.sp)
                        Slider(
                            value = musicVolume, onValueChange = { musicVolume = it },
                            enabled = !isExporting
                        )
                        Text("Audio balance previews with your video and is applied on export.",
                            color = Color.LightGray, fontSize = 12.sp)
                    }
                    }
                }
                if (grayscale || rotation != 0f) {
                    Text("B&W and rotation preview the same effects applied on export.",
                        color = Color.LightGray, fontSize = 12.sp)
                }
                exportError?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp)
                }
                if (isExporting) {
                    LinearProgressIndicator(
                        progress = { exportPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Rendering ${exportPercent.coerceIn(0, 100)}% · Keep this screen open.",
                        color = white, fontSize = 12.sp)
                } else {
                    Button(
                        enabled = hasPendingEdits,
                        onClick = {
                            exportError = null
                            exportPercent = 0
                            isExporting = true
                            onExporting(true)
                            player.pause()
                            scope.launch {
                                try {
                                    val result = exportEditedReel(context, uri, durationMs, spec) {
                                        exportPercent = it
                                    }
                                    PersistentTextDraftStore.clearValue(context, editKey, draftScope)
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

package com.example.ui.screens

import com.example.ui.components.BlinkElasticSwipeDirection
import com.example.ui.components.blinkElasticSwipeAction

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkChatPhotoEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BLINK's shared non-destructive image composer. All supported image pickers
 * hand a selected Uri here; drawing, captions and stickers flatten only when Done
 * is tapped. No broad device gallery permission and no changes to originals.
 *
 * Android Photo Picker is deliberately retained for private scoped selection.
 */
/** Same preview transform for main canvas and selectable filter thumbnails. */
internal fun blinkPhotoColorPreviewFilter(
    exposure: Float, contrast: Float, saturation: Float
): ColorFilter {
    val inv = 1f - saturation
    val r = inv * .213f
    val g = inv * .715f
    val b = inv * .072f
    val offset = ((1f - contrast) * 128f + exposure * 75f) / 255f
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        contrast * (r + saturation), contrast * g, contrast * b, 0f, offset,
        contrast * r, contrast * (g + saturation), contrast * b, 0f, offset,
        contrast * r, contrast * g, contrast * (b + saturation), 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )))
}

private data class PhotoStudioHistory(
    val uri: Uri, val exposure: Float, val contrast: Float, val saturation: Float,
    val strokes: List<BlinkChatPhotoEditor.Stroke>,
    val labels: List<BlinkChatPhotoEditor.Label>
)

@Composable
fun BlinkUniversalPhotoEditor(
    originalUri: Uri,
    onEdited: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var workingUri by remember(originalUri) { mutableStateOf(originalUri) }
    val transientCopies = remember(originalUri) { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf("") }
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    val strokes = remember { mutableStateListOf<BlinkChatPhotoEditor.Stroke>() }
    val labels = remember { mutableStateListOf<BlinkChatPhotoEditor.Label>() }
    var activeStroke by remember { mutableStateOf<List<Pair<Float,Float>>>(emptyList()) }
    var brushColor by remember { mutableIntStateOf(AndroidColor.WHITE) }
    var brushWidth by remember { mutableFloatStateOf(.007f) }
    var labelSize by remember { mutableFloatStateOf(.07f) }
    var precisionCropRatio by remember { mutableStateOf<Float?>(null) }
    var precisionCropScale by remember { mutableFloatStateOf(1f) }
    var adjustingSlider by remember { mutableStateOf(false) }
    var straightenAngle by remember { mutableFloatStateOf(0f) }
    val undoHistory = remember(originalUri) { mutableStateListOf<PhotoStudioHistory>() }
    val redoHistory = remember(originalUri) { mutableStateListOf<PhotoStudioHistory>() }
    fun snapshot() = PhotoStudioHistory(
        workingUri, exposure, contrast, saturation, strokes.toList(), labels.toList()
    )
    fun restore(snapshot: PhotoStudioHistory) {
        workingUri = snapshot.uri
        exposure = snapshot.exposure
        contrast = snapshot.contrast
        saturation = snapshot.saturation
        strokes.clear(); strokes.addAll(snapshot.strokes)
        labels.clear(); labels.addAll(snapshot.labels)
        activeStroke = emptyList()
    }
    fun saveUndo() {
        undoHistory.add(snapshot())
        if (undoHistory.size > 40) undoHistory.removeAt(0)
        redoHistory.clear()
    }
    fun undo() {
        if (!busy && undoHistory.isNotEmpty()) {
            redoHistory.add(snapshot())
            restore(undoHistory.removeAt(undoHistory.lastIndex))
        }
    }
    fun redo() {
        if (!busy && redoHistory.isNotEmpty()) {
            undoHistory.add(snapshot())
            restore(redoHistory.removeAt(redoHistory.lastIndex))
        }
    }
    var newText by remember { mutableStateOf("") }
    var photoAspect by remember(workingUri) { mutableFloatStateOf(1f) }
    var imageSize by remember { mutableStateOf(IntSize(1,1)) }
    var showDiscard by remember { mutableStateOf(false) }

    fun clearTemporary(keep: Uri? = null) {
        transientCopies.forEach { candidate ->
            if (candidate != keep && candidate.scheme == "file") {
                val file = File(candidate.path.orEmpty())
                if (file.parentFile?.canonicalFile == context.cacheDir.canonicalFile &&
                    file.name.startsWith("blink-edited-")) runCatching { file.delete() }
            }
        }
    }
    fun close() {
        if (busy) return
        val dirty = transientCopies.isNotEmpty() || strokes.isNotEmpty() ||
            labels.isNotEmpty() || exposure != 0f || contrast != 1f ||
            saturation != 1f || straightenAngle != 0f ||
            precisionCropScale != 1f || precisionCropRatio != null ||
            newText.isNotBlank()
        if (dirty) showDiscard = true else onDismiss()
    }
    BackHandler(enabled = true) { close() }
    LaunchedEffect(workingUri) {
        photoAspect = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(workingUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                (opts.outWidth.toFloat()/opts.outHeight.coerceAtLeast(1)).coerceIn(.2f,5f)
            }.getOrDefault(1f)
        }
    }
    val palette = listOf(AndroidColor.WHITE,AndroidColor.YELLOW,AndroidColor.RED,
        AndroidColor.CYAN,AndroidColor.GREEN,AndroidColor.BLACK)
    val previewFilter = remember(exposure, contrast, saturation) {
        blinkPhotoColorPreviewFilter(exposure, contrast, saturation)
    }
    fun applyCrop(operation: BlinkChatPhotoEditor.Edit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { BlinkChatPhotoEditor.edit(context,workingUri,operation) }
                .onSuccess { uri ->
                    saveUndo()
                    transientCopies.add(uri); workingUri = uri
                    strokes.clear(); labels.clear(); activeStroke = emptyList()
                }
                .onFailure { Toast.makeText(context,it.message ?: "Cannot edit this image.",
                    Toast.LENGTH_SHORT).show() }
            busy=false
        }
    }
    fun applyPrecisionCrop() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                com.example.media.BlinkChatPhotoGeometry.edit(
                    context, workingUri, precisionCropRatio, straightenAngle,
                    precisionCropScale
                )
            }.onSuccess { result ->
                saveUndo()
                transientCopies.add(result)
                workingUri = result
                strokes.clear(); labels.clear(); activeStroke = emptyList()
                straightenAngle = 0f
                precisionCropScale = 1f
            }.onFailure {
                Toast.makeText(context, it.message ?: "Could not crop image.",
                    Toast.LENGTH_LONG).show()
            }
            busy = false
        }
    }
    fun commit() {
        if (busy) return
        busy=true
        scope.launch {
            runCatching {
                var result = workingUri
                if (exposure != 0f || contrast != 1f || saturation != 1f) {
                    result = BlinkChatPhotoEditor.adjust(context,result,exposure,contrast,saturation)
                    transientCopies.add(result)
                }
                if (strokes.isNotEmpty() || labels.isNotEmpty()) {
                    result = BlinkChatPhotoEditor.overlay(context,result,strokes.toList(),labels.toList())
                    transientCopies.add(result)
                }
                result
            }.onSuccess { result ->
                clearTemporary(keep = result)
                onEdited(result)
            }.onFailure { problem ->
                Toast.makeText(context,problem.message ?: "Unable to save photo.",
                    Toast.LENGTH_LONG).show()
            }
            busy=false
        }
    }
    val photoControls = listOf(
        "CROP" to Icons.Default.Crop,
        "FILTER" to Icons.Default.AutoAwesome,
        "ADJUST" to Icons.Default.Tune,
        "TEXT" to Icons.Default.TextFields,
        "STICKER" to Icons.Default.EmojiEmotions,
        "DRAW" to Icons.Default.Brush
    )

    Dialog(onDismissRequest = { close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        Column(
            Modifier.fillMaxSize()
                .testTag("blink_photo_editor_stage")
                .blinkElasticSwipeAction(
                    direction = BlinkElasticSwipeDirection.DOWN,
                    onAction = { if (!busy) close() },
                    enabled = !busy
                )
                .background(Color.Black).statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal=8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { close() },
                    enabled = !busy,
                    modifier = Modifier.testTag("blink_photo_editor_back")
                ) {
                    Icon(Icons.Default.ChevronLeft, "Back from photo editor", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text("Edit Photo", color=Color.White, fontWeight=FontWeight.Bold,
                    fontSize=16.sp)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { commit() },
                    enabled = !busy,
                    modifier = Modifier.testTag("blink_photo_editor_next")
                ) {
                    Text(if(busy) "Saving" else "Next",color=Color(0xFF8D85FF),
                        fontSize=13.sp,fontWeight=FontWeight.Bold)
                }
            }

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val width = minOf(maxWidth, maxHeight*photoAspect)
                val height = width/photoAspect
                Box(
                    Modifier.width(width).height(height).onSizeChanged { imageSize=it },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model=workingUri,contentDescription="Edited photo preview",
                        contentScale=ContentScale.Fit,colorFilter=previewFilter,
                        modifier=Modifier.fillMaxSize()
                    )
                    Canvas(
                        Modifier.fillMaxSize().then(
                            if(tool=="DRAW" && !busy) Modifier.pointerInput(tool,brushColor,brushWidth) {
                                detectDragGestures(
                                    onDragStart={ pos ->
                                        saveUndo()
                                        activeStroke = listOf(
                                            pos.x/size.width.coerceAtLeast(1),
                                            pos.y/size.height.coerceAtLeast(1))
                                            .let { listOf(it[0] to it[1]) }
                                    },
                                    onDrag={ change,_ ->
                                        change.consume()
                                        activeStroke=activeStroke+(
                                            change.position.x/size.width.coerceAtLeast(1) to
                                            change.position.y/size.height.coerceAtLeast(1))
                                    },
                                    onDragEnd={
                                        if(activeStroke.isNotEmpty()) strokes.add(
                                            BlinkChatPhotoEditor.Stroke(activeStroke,brushColor,brushWidth))
                                        activeStroke=emptyList()
                                    },
                                    onDragCancel={ activeStroke=emptyList() }
                                )
                            } else if ((tool=="TEXT" || tool=="STICKER") &&
                                labels.isNotEmpty() && !busy) {
                                Modifier.pointerInput(tool, labels.size) {
                                    var movingLabel = -1
                                    detectDragGestures(
                                        onDragStart = { position ->
                                            val radiusX = size.width * .25f
                                            val radiusY = size.height * .12f
                                            movingLabel = labels.indices.reversed().firstOrNull { i ->
                                                val label = labels[i]
                                                kotlin.math.abs(label.x * size.width - position.x) < radiusX &&
                                                    kotlin.math.abs(label.y * size.height - position.y) < radiusY
                                            } ?: -1
                                            if (movingLabel >= 0) saveUndo()
                                        },
                                        onDrag = { change, delta ->
                                            val i = movingLabel
                                            if (i in labels.indices) {
                                                val label = labels[i]
                                                labels[i] = label.copy(
                                                    x = (label.x + delta.x / size.width).coerceIn(.05f,.95f),
                                                    y = (label.y + delta.y / size.height).coerceIn(.1f,.96f)
                                                )
                                                change.consume()
                                            }
                                        },
                                        onDragEnd = { movingLabel = -1 },
                                        onDragCancel = { movingLabel = -1 }
                                    )
                                }
                            } else Modifier
                        )
                    ) {
                        val widthPx=size.width; val heightPx=size.height
                        fun plot(points:List<Pair<Float,Float>>, color:Int, width:Float) {
                            if(points.isEmpty()) return
                            val p=Path().apply {
                                moveTo(points.first().first*widthPx,points.first().second*heightPx)
                                points.drop(1).forEach { (x,y) ->
                                    lineTo(x*widthPx,y*heightPx)
                                }
                            }
                            drawPath(p,color=Color(color),style=ComposeStroke(
                                width=widthPx*width,cap=StrokeCap.Round))
                        }
                        strokes.forEach { plot(it.points,it.color,it.widthFraction) }
                        plot(activeStroke,brushColor,brushWidth)
                        labels.forEach { label ->
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    label.text,
                                    label.x*widthPx,label.y*heightPx,
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                        color=label.color
                                        textSize=widthPx*label.sizeFraction
                                        typeface=android.graphics.Typeface.DEFAULT_BOLD
                                        textAlign=android.graphics.Paint.Align.CENTER
                                        setShadowLayer(2f,1f,1f,AndroidColor.BLACK)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF151515)).padding(vertical=5.dp,horizontal=8.dp),
                horizontalArrangement=Arrangement.spacedBy(2.dp)
            ) {
                photoControls.forEach { (name,icon) ->
                    TextButton(onClick={ tool = if (tool == name) "" else name },enabled=!busy,
                        modifier=Modifier.weight(1f).heightIn(min=56.dp)
                            .testTag("blink_photo_tool_" + name.lowercase()),
                        contentPadding=PaddingValues(horizontal=1.dp,vertical=4.dp),
                        colors=ButtonDefaults.textButtonColors(
                            contentColor=if(tool==name) Color(0xFF8D85FF) else Color.LightGray)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Icon(icon,contentDescription=null,modifier=Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(when(name) { "DRAW" -> "Doodle"; "STICKER" -> "Stickers"; else -> name.lowercase().replaceFirstChar { it.uppercase() } },fontSize=10.sp,maxLines=1)
                        }
                    }
                }
            }
            if (tool.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(min=130.dp,max=260.dp)
                .background(Color(0xFF0F0F0F))
                .verticalScroll(rememberScrollState())
                .padding(horizontal=16.dp,vertical=8.dp)
                .testTag("blink_photo_tool_panel")) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { undo() },
                        enabled = !busy && undoHistory.isNotEmpty(),
                        modifier = Modifier.testTag("blink_photo_undo")
                    ) { Icon(Icons.Default.Undo, "Undo edit", tint = Color.White) }
                    IconButton(
                        onClick = { redo() },
                        enabled = !busy && redoHistory.isNotEmpty(),
                        modifier = Modifier.testTag("blink_photo_redo")
                    ) { Icon(Icons.Default.Redo, "Redo edit", tint = Color.White) }
                    TextButton(onClick = { tool = "" }, enabled = !busy) {
                        Text("Close tools", color = Color.White, fontSize = 11.sp)
                    }
                }
                when(tool) {
                    "FILTER" -> {
                        Text("FILTERS", color=Color.White, fontSize=12.sp)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                listOf(0f,1f,1f) to "Original",
                                listOf(.22f,1.08f,1.12f) to "Bright",
                                listOf(0f,1.3f,1.12f) to "Vivid",
                                listOf(.02f,1.12f,0f) to "B&W",
                                listOf(.08f,1.13f,.72f) to "Warm"
                            ).forEach { (values, title) ->
                                val chosen = exposure == values[0] &&
                                    contrast == values[1] && saturation == values[2]
                                Column(
                                    Modifier.width(76.dp)
                                        .testTag("blink_photo_filter_" + title.lowercase())
                                        .clip(RoundedCornerShape(9.dp))
                                        .border(
                                            if (chosen) 2.dp else 1.dp,
                                            if (chosen) Color(0xFF8D85FF) else Color(0xFF383D47),
                                            RoundedCornerShape(9.dp)
                                        )
                                        .clickable(enabled = !busy) {
                                            saveUndo()
                                            exposure = values[0]
                                            contrast = values[1]
                                            saturation = values[2]
                                        }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = workingUri,
                                        contentDescription = "$title photo filter preview",
                                        contentScale = ContentScale.Crop,
                                        colorFilter = blinkPhotoColorPreviewFilter(
                                            values[0], values[1], values[2]
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(title, color = Color.White, fontSize = 10.sp,
                                        maxLines = 1)
                                }
                            }
                        }
                        Text("Filters are applied to your actual photo when you tap Done.",
                            color=Color.LightGray, fontSize=11.sp)
                    }
                    "ADJUST" -> {
                        listOf("Enhance / Exposure","Contrast","Saturation").forEachIndexed { index,label ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(label,color=Color.White,fontSize=11.sp,
                                    modifier=Modifier.width(110.dp))
                                Slider(
                                    value=when(index){0->exposure;1->contrast;else->saturation},
                                    onValueChange = {
                                        if (!adjustingSlider) {
                                            saveUndo()
                                            adjustingSlider = true
                                        }
                                        when(index){0->exposure=it;1->contrast=it;else->saturation=it}
                                    },
                                    onValueChangeFinished = { adjustingSlider = false },
                                    valueRange=when(index){0->-1f..1f;1->.5f..1.5f;else->0f..2f},
                                    enabled=!busy,
                                    modifier=Modifier.weight(1f)
                                        .testTag("blink_photo_adjust_" + index)
                                )
                            }
                        }
                    }
                    "CROP" -> {
                        Text("CROP & ROTATE",color=Color.White,fontSize=12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "Square" to BlinkChatPhotoEditor.Edit.CENTER_SQUARE,
                                "16:9" to BlinkChatPhotoEditor.Edit.CENTER_LANDSCAPE,
                                "9:16" to BlinkChatPhotoEditor.Edit.CENTER_PORTRAIT,
                                "Rotate 90°" to BlinkChatPhotoEditor.Edit.ROTATE_RIGHT,
                                "Flip" to BlinkChatPhotoEditor.Edit.FLIP_HORIZONTAL
                            ).forEach { (name,edit) ->
                                OutlinedButton(onClick={applyCrop(edit)},enabled=!busy) {
                                    Text(name,color=Color.White,fontSize=11.sp)
                                }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val ratioOptions: List<Pair<String,Float?>> = listOf(
                                "Free" to null, "1:1" to 1f, "4:5" to .8f,
                                "9:16" to .5625f, "16:9" to (16f/9f)
                            )
                            ratioOptions.forEach { (name, ratio) ->
                                FilterChip(
                                    selected = precisionCropRatio == ratio,
                                    onClick = { precisionCropRatio = ratio },
                                    label = { Text(name) }
                                )
                            }
                        }
                        Text("Straighten ${"%.1f".format(straightenAngle)}°",
                            fontSize=11.sp, color=Color.White)
                        Slider(value=straightenAngle,
                            onValueChange={straightenAngle=it},
                            valueRange=-45f..45f,enabled=!busy)
                        Text("Crop zoom ${(precisionCropScale*100).toInt()}%",
                            fontSize=11.sp, color=Color.White)
                        Slider(value=precisionCropScale,
                            onValueChange={precisionCropScale=it},
                            valueRange=.45f..1f,enabled=!busy)
                        Button(onClick={applyPrecisionCrop()},enabled=!busy) {
                            Text("Apply precision crop")
                        }
                    }
                    "DRAW" -> {
                        Text("DRAW ON THE PICTURE",color=Color.White,fontSize=12.sp)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Brush size", color=Color.White, fontSize=11.sp,
                                modifier=Modifier.width(82.dp))
                            Slider(value=brushWidth, onValueChange={brushWidth=it},
                                valueRange=.002f.. .035f,modifier=Modifier.weight(1f))
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            palette.forEach { color ->
                                Box(Modifier.size(28.dp).clip(CircleShape)
                                    .background(Color(color))
                                    .border(if(brushColor==color) 3.dp else 1.dp,
                                        Color.LightGray,CircleShape)
                                    .clickable { brushColor=color })
                            }
                            TextButton(onClick={ undo() }, enabled=undoHistory.isNotEmpty()) { Text("Undo",color=Color.White) }
                        }
                        Text("Swipe your finger over the photo to draw.",
                            color=Color.Gray,fontSize=11.sp)
                    }
                    "STICKER" -> {
                        Text("ADD A STICKER",color=Color.White,fontSize=12.sp)
                        Slider(value=labelSize,onValueChange={labelSize=it},
                            valueRange=.025f.. .2f)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("❤️","🔥","✨","🎉","💜","😂","👍","⭐","🌈").forEach { emoji ->
                                Text(emoji,fontSize=27.sp,modifier=Modifier
                                    .clickable {
                                         saveUndo()
                                         labels.add(BlinkChatPhotoEditor.Label(emoji,sizeFraction=labelSize))
                                     }
                                    .padding(4.dp))
                            }
                        }
                        Text("Tap an emoji to place it in the centre; drag it to reposition.",color=Color.Gray,fontSize=11.sp)
                    }
                    "TEXT" -> {
                        Text("Text size", color=Color.White, fontSize=12.sp)
                        Slider(value=labelSize,onValueChange={labelSize=it},
                            valueRange=.025f.. .2f)
                        OutlinedTextField(
                            value=newText,onValueChange={newText=it.take(80)},
                            placeholder={Text("Write on the photo",color=Color.LightGray)},
                            singleLine=true,
                            modifier=Modifier.fillMaxWidth(),
                            colors=OutlinedTextFieldDefaults.colors(
                                focusedTextColor=Color.White,unfocusedTextColor=Color.White)
                        )
                        TextButton(onClick={
                            if(newText.isNotBlank()) {
                                 saveUndo()
                                 labels.add(BlinkChatPhotoEditor.Label(newText.trim(),
                                    color=brushColor,sizeFraction=labelSize))
                                newText=""
                            }
                        },enabled=newText.isNotBlank()) { Text("Add text",color=Color(0xFF46B7FF)) }
                    }
                }
            }
            }
        }
    }
    if(showDiscard) {
        AlertDialog(onDismissRequest={showDiscard=false},
            title={Text("Discard photo edits?")},
            text={Text("The original picture will stay unchanged.")},
            confirmButton={ TextButton(onClick={
                clearTemporary(); showDiscard=false; onDismiss()
            }) { Text("Discard") } },
            dismissButton={ TextButton(onClick={showDiscard=false}) { Text("Keep editing") } })
    }
}

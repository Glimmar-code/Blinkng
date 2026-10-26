package com.example.ui.screens

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
    var tool by remember { mutableStateOf("ADJUST") }
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    val strokes = remember { mutableStateListOf<BlinkChatPhotoEditor.Stroke>() }
    val labels = remember { mutableStateListOf<BlinkChatPhotoEditor.Label>() }
    var activeStroke by remember { mutableStateOf<List<Pair<Float,Float>>>(emptyList()) }
    var brushColor by remember { mutableIntStateOf(AndroidColor.WHITE) }
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
            labels.isNotEmpty() || exposure != 0f || contrast != 1f || saturation != 1f
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
    val previewFilter = remember(exposure,contrast,saturation) {
        val inv = 1f-saturation
        val r = inv*.213f; val g=inv*.715f; val b=inv*.072f
        val offset = ((1f-contrast)*128f+exposure*75f)/255f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            contrast*(r+saturation),contrast*g,contrast*b,0f,offset,
            contrast*r,contrast*(g+saturation),contrast*b,0f,offset,
            contrast*r,contrast*g,contrast*(b+saturation),0f,offset,
            0f,0f,0f,1f,0f
        )))
    }
    fun applyCrop(operation: BlinkChatPhotoEditor.Edit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { BlinkChatPhotoEditor.edit(context,workingUri,operation) }
                .onSuccess { uri ->
                    transientCopies.add(uri); workingUri = uri
                    strokes.clear(); labels.clear(); activeStroke = emptyList()
                }
                .onFailure { Toast.makeText(context,it.message ?: "Cannot edit this image.",
                    Toast.LENGTH_SHORT).show() }
            busy=false
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
        "ADJUST" to Icons.Default.Tune,
        "CROP" to Icons.Default.Crop,
        "DRAW" to Icons.Default.Brush,
        "STICKER" to Icons.Default.EmojiEmotions,
        "TEXT" to Icons.Default.TextFields
    )

    Dialog(onDismissRequest = { close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        )
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black)
            .statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal=8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick={close()},enabled=!busy) {
                    Icon(Icons.Default.Close,"Cancel edits",tint=Color.White)
                    Text("CANCEL",color=Color.White,fontSize=12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("BLINK PHOTO",color=Color.White,fontWeight=FontWeight.Bold,fontSize=15.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick={commit()},enabled=!busy) {
                    Text(if(busy) "SAVING" else "DONE",color=Color(0xFF38ADFF),
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
                            if(tool=="DRAW" && !busy) Modifier.pointerInput(tool,brushColor) {
                                detectDragGestures(
                                    onDragStart={ pos ->
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
                                            BlinkChatPhotoEditor.Stroke(activeStroke,brushColor))
                                        activeStroke=emptyList()
                                    },
                                    onDragCancel={ activeStroke=emptyList() }
                                )
                            } else Modifier
                        )
                    ) {
                        val widthPx=size.width; val heightPx=size.height
                        fun plot(points:List<Pair<Float,Float>>, color:Int) {
                            if(points.isEmpty()) return
                            val p=Path().apply {
                                moveTo(points.first().first*widthPx,points.first().second*heightPx)
                                points.drop(1).forEach { (x,y) ->
                                    lineTo(x*widthPx,y*heightPx)
                                }
                            }
                            drawPath(p,color=Color(color),style=ComposeStroke(
                                width=widthPx*.007f,cap=StrokeCap.Round))
                        }
                        strokes.forEach { plot(it.points,it.color) }
                        plot(activeStroke,brushColor)
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
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(Color(0xFF151515)).padding(vertical=5.dp,horizontal=8.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                photoControls.forEach { (name,icon) ->
                    TextButton(onClick={tool=name},enabled=!busy,
                        colors=ButtonDefaults.textButtonColors(
                            contentColor=if(tool==name) Color(0xFF46B7FF) else Color.LightGray)) {
                        Icon(icon,contentDescription=null,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(name,fontSize=11.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min=130.dp).background(Color(0xFF0F0F0F))
                .padding(horizontal=16.dp,vertical=8.dp)) {
                when(tool) {
                    "ADJUST" -> {
                        listOf("Enhance / Exposure","Contrast","Saturation").forEachIndexed { index,label ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(label,color=Color.White,fontSize=11.sp,
                                    modifier=Modifier.width(110.dp))
                                Slider(
                                    value=when(index){0->exposure;1->contrast;else->saturation},
                                    onValueChange={
                                        when(index){0->exposure=it;1->contrast=it;else->saturation=it}
                                    },
                                    valueRange=when(index){0->-1f..1f;1->.5f..1.5f;else->0f..2f},
                                    enabled=!busy,modifier=Modifier.weight(1f)
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
                    }
                    "DRAW" -> {
                        Text("DRAW ON THE PICTURE",color=Color.White,fontSize=12.sp)
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            palette.forEach { color ->
                                Box(Modifier.size(28.dp).clip(CircleShape)
                                    .background(Color(color))
                                    .border(if(brushColor==color) 3.dp else 1.dp,
                                        Color.LightGray,CircleShape)
                                    .clickable { brushColor=color })
                            }
                            TextButton(onClick={
                                if(strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                            }) { Text("Undo",color=Color.White) }
                        }
                        Text("Swipe your finger over the photo to draw.",
                            color=Color.Gray,fontSize=11.sp)
                    }
                    "STICKER" -> {
                        Text("ADD A STICKER",color=Color.White,fontSize=12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("❤️","🔥","✨","🎉","💜","😂","👍","⭐","🌈").forEach { emoji ->
                                Text(emoji,fontSize=27.sp,modifier=Modifier
                                    .clickable { labels.add(BlinkChatPhotoEditor.Label(emoji)) }
                                    .padding(4.dp))
                            }
                        }
                        Text("Tap an emoji to place it in the centre.",color=Color.Gray,fontSize=11.sp)
                    }
                    "TEXT" -> {
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
                                labels.add(BlinkChatPhotoEditor.Label(newText.trim(),
                                    color=brushColor))
                                newText=""
                            }
                        },enabled=newText.isNotBlank()) { Text("Add text",color=Color(0xFF46B7FF)) }
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

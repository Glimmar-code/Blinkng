package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam

/** The Windows counterpart of BLINK's non-destructive Android image editor.
 * All results are private temporary JPEG files until the user explicitly sends
 * or uploads the edited copy; never rewrite user-supplied source files.
 */
internal object DesktopPictureEditor {
    enum class Crop { SQUARE, WIDE, PORTRAIT, ROTATE, MIRROR }

    private fun decoded(file: File): BufferedImage {
        require(file.isFile && file.length() in 1..(30L*1024*1024)) {
            "Photo must be a readable file smaller than 30 MB."
        }
        require(file.extension.lowercase() in setOf("jpg","jpeg","png","webp")) {
            "Only still JPEG, PNG and supported WebP photos can be edited."
        }
        val stream = ImageIO.createImageInputStream(file)
            ?: throw IOException("Cannot open image.")
        return stream.use { src ->
            val readers = ImageIO.getImageReaders(src)
            require(readers.hasNext()) { "Unsupported still-photo format." }
            val reader = readers.next()
            try {
                reader.input=src
                val width=reader.getWidth(0)
                val height=reader.getHeight(0)
                require(width in 1..20000 && height in 1..20000)
                var sample=1
                while (maxOf(width,height)/sample>2048) sample*=2
                val param=reader.defaultReadParam.apply {
                    setSourceSubsampling(sample,sample,0,0)
                }
                reader.read(0,param) ?: throw IOException("Cannot decode image.")
            } finally { reader.dispose() }
        }
    }

    private fun save(image: BufferedImage): File {
        val file=File.createTempFile("blink-edited-desktop-", ".jpg")
        val output=BufferedImage(image.width,image.height,BufferedImage.TYPE_INT_RGB)
        val g=output.createGraphics()
        try {
            g.color=AwtColor.WHITE;g.fillRect(0,0,output.width,output.height)
            g.drawImage(image,0,0,null)
        } finally { g.dispose() }
        if (!ImageIO.write(output,"jpg",file) || file.length()<=0) {
            file.delete();throw IOException("Unable to save image.")
        }
        return file
    }

    fun crop(source: File,operation: Crop): File {
        val image=decoded(source)
        val (width,height)=image.width to image.height
        val output= when(operation) {
            Crop.ROTATE -> BufferedImage(height,width,BufferedImage.TYPE_INT_RGB).also { out ->
                val g=out.createGraphics()
                try {
                    g.translate(height,0);g.rotate(Math.PI/2)
                    g.drawImage(image,0,0,null)
                } finally {g.dispose()}
            }
            Crop.MIRROR -> BufferedImage(width,height,BufferedImage.TYPE_INT_RGB).also { out ->
                val g=out.createGraphics()
                try {g.drawImage(image,width,0,-width,height,null)}finally{g.dispose()}
            }
            else -> {
                val aspect=when(operation) {
                    Crop.SQUARE -> 1f
                    Crop.WIDE -> 16f/9f
                    else -> 9f/16f
                }
                val w=if(width.toFloat()/height>aspect)(height*aspect).toInt() else width
                val h=if(width.toFloat()/height>aspect)height else (width/aspect).toInt()
                image.getSubimage((width-w)/2,(height-h)/2,w.coerceAtLeast(1),h.coerceAtLeast(1))
            }
        }
        return save(output)
    }

    fun finish(
        source: File, exposure: Float, contrast: Float, saturation: Float,
        text: String, sticker: String
    ): File {
        val original=decoded(source)
        val output=BufferedImage(original.width,original.height,BufferedImage.TYPE_INT_RGB)
        val c=contrast.coerceIn(.5f,1.5f)
        val sat=saturation.coerceIn(0f,2f)
        val bright=exposure.coerceIn(-1f,1f)*75f
        for (y in 0 until original.height) for (x in 0 until original.width) {
            val argb=original.getRGB(x,y)
            var red=(argb ushr 16) and 255
            var green=(argb ushr 8) and 255
            var blue=argb and 255
            val gray=.213f*red+.715f*green+.072f*blue
            red=((gray+(red-gray)*sat-128f)*c+128f+bright).toInt().coerceIn(0,255)
            green=((gray+(green-gray)*sat-128f)*c+128f+bright).toInt().coerceIn(0,255)
            blue=((gray+(blue-gray)*sat-128f)*c+128f+bright).toInt().coerceIn(0,255)
            output.setRGB(x,y,(red shl 16) or (green shl 8) or blue)
        }
        val g=output.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color=AwtColor.WHITE
            g.font=Font(Font.SANS_SERIF,Font.BOLD,(output.width*.07f).toInt().coerceAtLeast(16))
            if(text.isNotBlank())g.drawString(text.take(80),output.width/8,output.height/2)
            if(sticker.isNotBlank())g.drawString(sticker,output.width/2,output.height/2)
        } finally {g.dispose()}
        return save(output)
    }
}

@Composable
internal fun DesktopUniversalPhotoEditor(
    sourceFile: File,
    onEdited: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember(sourceFile) {mutableStateOf(sourceFile)}
    val copies=remember(sourceFile) { mutableStateListOf<File>() }
    var exposure by remember {mutableFloatStateOf(0f)}
    var contrast by remember {mutableFloatStateOf(1f)}
    var saturation by remember {mutableFloatStateOf(1f)}
    var label by remember {mutableStateOf("")}
    var sticker by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var tab by remember {mutableStateOf("Adjust")}
    val scope=rememberCoroutineScope()
    fun cleanup(keep:File?=null) {
        copies.forEach { file ->
            if (file!=keep && file.name.startsWith("blink-edited-desktop-") &&
                file.parentFile == File(System.getProperty("java.io.tmpdir"))) {
                runCatching { file.delete() }
            }
        }
    }
    AlertDialog(
        onDismissRequest={if(!busy){cleanup();onDismiss()}},
        title={Text("BLINK Photo Editor")},
        text={
            Column(Modifier.widthIn(min=380.dp,max=700.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(270.dp).background(Color.Black),
                    contentAlignment=Alignment.Center) {
                    AsyncImage(model=working,contentDescription="Photo preview",
                        modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    listOf("Adjust","Crop","Text","Sticker").forEach { item ->
                        FilterChip(selected=tab==item,onClick={tab=item},
                            label={Text(item,fontSize=11.sp)})
                    }
                }
                when(tab) {
                    "Adjust"-> {
                        listOf("Exposure","Contrast","Saturation").forEachIndexed { i,title ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(title,modifier=Modifier.width(90.dp),fontSize=12.sp)
                                Slider(value=when(i){0->exposure;1->contrast;else->saturation},
                                    onValueChange={when(i){0->exposure=it;1->contrast=it;else->saturation=it}},
                                    valueRange=when(i){0->-1f..1f;1->.5f..1.5f;else->0f..2f},
                                    enabled=!busy,modifier=Modifier.weight(1f))
                            }
                        }
                    }
                    "Crop"-> {
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            DesktopPictureEditor.Crop.entries.forEach { operation ->
                                TextButton(enabled=!busy,onClick={
                                    busy=true
                                    scope.launch {
                                        runCatching {withContext(Dispatchers.IO) {
                                            DesktopPictureEditor.crop(working,operation)
                                        }}.onSuccess {file->copies.add(file);working=file}
                                            .onFailure {error=it.message}
                                        busy=false
                                    }
                                }) {Text(operation.name.lowercase().replaceFirstChar(Char::uppercase),fontSize=11.sp)}
                            }
                        }
                    }
                    "Text"-> OutlinedTextField(label,{label=it.take(80)},label={Text("Overlay text")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    "Sticker"-> Row {
                        listOf("❤️","⭐","🔥","🎉","💜").forEach {
                            TextButton(onClick={sticker=it}) {Text(it)}
                        }
                    }
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
            }
        },
        confirmButton={
            Button(enabled=!busy,onClick={
                busy=true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) {
                        DesktopPictureEditor.finish(working,exposure,contrast,saturation,label,sticker)
                    }}.onSuccess {result ->
                        cleanup()
                        onEdited(result)
                    }.onFailure {error=it.message}
                    busy=false
                }
            }) {Text(if(busy)"Saving..." else "Use edited photo")}
        },
        dismissButton={
            TextButton(enabled=!busy,onClick={cleanup();onDismiss()}) {Text("Cancel")}
        }
    )
}

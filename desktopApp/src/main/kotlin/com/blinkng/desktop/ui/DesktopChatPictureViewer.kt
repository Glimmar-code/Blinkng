package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.blinkng.desktop.data.DesktopMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.JFileChooser

/**
 * Desktop counterpart of the normal chat-image viewer. Never accepts a view-once
 * message, never auto-downloads to disk and never writes over a user file.
 */
@Composable
internal fun DesktopChatPictureViewer(
    message: DesktopMessage,
    senderLabel: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onStar: () -> Unit,
    onDeleteForMe: () -> Unit,
    onClose: () -> Unit,
    groupPhotoEnabled: Boolean = false,
    onSaveSticker: (File) -> Unit = {},
    onAskBlinkAi: (File) -> Unit = {},
    onSetGroupPicture: (File) -> Unit = {},
) {
    if(message.imageViewOnce || message.messageType!="image" ||
        message.mediaUrl.isNullOrBlank()) return
    val scope=rememberCoroutineScope()
    var busy by remember(message.id){mutableStateOf(false)}
    var downloadJob by remember(message.id){mutableStateOf<Job?>(null)}
    var progress by remember(message.id){mutableIntStateOf(-1)}
    var saved by remember(message.id){mutableStateOf<File?>(null)}
    var error by remember(message.id){mutableStateOf<String?>(null)}
    var editing by remember(message.id){mutableStateOf<File?>(null)}
    var privacyWarning by remember(message.id){mutableStateOf(false)}
    var confirmAction by remember(message.id){mutableStateOf<String?>(null)}
    DisposableEffect(message.id) {onDispose{downloadJob?.cancel()}}
    AlertDialog(
        onDismissRequest={if(!busy)onClose()},
        title={
            Column {
                Text(senderLabel,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(message.createdAt,fontSize=11.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text={
            Column(Modifier.widthIn(min=430.dp,max=780.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min=230.dp,max=530.dp)
                    .background(Color.Black),contentAlignment=Alignment.Center) {
                    AsyncImage(
                        model=message.mediaUrl,
                        contentDescription="Full-size shared picture",
                        contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxWidth().height(480.dp)
                    )
                }
                if(message.content.isNotBlank() &&
                    message.content !in setOf("Photo","Image")) {
                    Text(message.content,fontSize=13.sp,
                        maxLines=4,overflow=TextOverflow.Ellipsis,
                        modifier=Modifier.fillMaxWidth())
                }
                if(progress in 0..99) LinearProgressIndicator(
                    progress={progress/100f},modifier=Modifier.fillMaxWidth())
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onPrevious,enabled=hasPrevious){Text("◀ Previous")}
                    TextButton(onClick=onNext,enabled=hasNext){Text("Next ▶")}
                    TextButton(onClick=onReply){Text("Reply")}
                    TextButton(onClick=onForward){Text("Forward")}
                    TextButton(onClick=onStar){Text(if(message.isStarred)"Unstar" else "Star")}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton(enabled=!busy,onClick={
                        if(downloadJob?.isActive==true) {
                            downloadJob?.cancel()
                            return@OutlinedButton
                        }
                        val chooser=JFileChooser().apply {
                            dialogTitle="Save BLINK photo"
                            selectedFile=File("BLINK_${message.id.take(8)}.jpg")
                        }
                        if(chooser.showSaveDialog(null)!=JFileChooser.APPROVE_OPTION)
                            return@OutlinedButton
                        val output=chooser.selectedFile
                        if(output.exists()) {
                            error="Choose a new filename to avoid overwriting another picture."
                            return@OutlinedButton
                        }
                        busy=true
                        error=null
                        downloadJob=scope.launch {
                            runCatching {
                                downloadDesktopChatImage(message.mediaUrl!!,output) {progress=it}
                            }.onSuccess {saved=it;progress=100}
                                .onFailure {
                                    if(it !is CancellationException)
                                        error=it.message ?: "Download failed. Retry."
                                    progress=-1
                                }
                            busy=false
                        }
                    }) {Text(if(busy)"Downloading…" else "Download")}
                    TextButton(enabled=busy,onClick={downloadJob?.cancel()}){Text("Cancel")}
                    TextButton(enabled=saved!=null,onClick={editing=saved}){Text("Edit copy")}
                    TextButton(enabled=saved!=null,onClick={confirmAction="sticker"}){Text("Save sticker")}
                    TextButton(enabled=saved!=null,onClick={confirmAction="ai"}){Text("Ask BLINK AI")}
                    if(groupPhotoEnabled) TextButton(
                        enabled=saved!=null,
                        onClick={confirmAction="group"}
                    ){Text("Set group picture")}
                    TextButton(enabled=saved!=null,onClick={privacyWarning=true}){Text("Share externally")}
                    TextButton(onClick=onDeleteForMe){Text("Delete for me")}
                }
                saved?.let{Text("Saved to ${it.name}",fontSize=11.sp)}
            }
        },
        confirmButton={TextButton(onClick=onClose){Text("Back to chat")}}
    )
    editing?.let { picture ->
        DesktopUniversalPhotoEditor(
            sourceFile=picture,
            onEdited={result->
                saved=result
                editing=null
            },onDismiss={editing=null}
        )
    }
    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest={confirmAction=null},
            title={Text(when(action){
                "ai" -> "Send this image to BLINK AI?"
                "group" -> "Change the group's picture?"
                else -> "Keep this image as a reusable sticker?"
            })},
            text={Text(when(action){
                "ai" -> "The photo will be attached to an AI draft; it will not be sent until you submit a question."
                "group" -> "Only group owners or admins can change the picture. This change is visible to members."
                else -> "This creates a private copy saved on your computer. Get permission before saving another person's photo."
            })},
            confirmButton={TextButton(onClick={
                val file=saved
                confirmAction=null
                if(file!=null)when(action) {
                    "ai" -> onAskBlinkAi(file)
                    "group" -> onSetGroupPicture(file)
                    else -> onSaveSticker(file)
                }
            }){Text("Continue")}},
            dismissButton={TextButton(onClick={confirmAction=null}){Text("Cancel")}}
        )
    }
    if(privacyWarning) {
        AlertDialog(
            onDismissRequest={privacyWarning=false},
            title={Text("Share photo outside BLINK?")},
            text={Text("External apps may retain or upload the photo. Continue only if you agree.")},
            confirmButton={TextButton(onClick={
                privacyWarning=false
                val file=saved
                if(file!=null)runCatching {
                    java.awt.Desktop.getDesktop().open(file.parentFile)
                }.onFailure {error="Could not open the downloaded file location."}
            }){Text("Open folder")}},
            dismissButton={TextButton(onClick={privacyWarning=false}){Text("Cancel")}}
        )
    }
}

private suspend fun downloadDesktopChatImage(
    remote: String,
    destination: File,
    onProgress: (Int) -> Unit
): File = withContext(Dispatchers.IO) {
    val url=URI.create(remote)
    require(url.scheme=="https"){"HTTPS is required for private pictures."}
    val connection=url.toURL().openConnection() as HttpURLConnection
    connection.connectTimeout=12_000
    connection.readTimeout=15_000
    val temp=File(destination.parentFile,"${destination.name}.blink-partial")
    try {
        require(connection.responseCode in 200..299){"Picture download was denied."}
        val mime=connection.contentType.orEmpty()
        require(mime.startsWith("image/")){"Unexpected content type."}
        val length=connection.contentLengthLong
        require(length < 30L*1024*1024){"Photo is too large."}
        var nBytes=0L
        connection.inputStream.use {input->
            temp.outputStream().use { output->
                val chunk=ByteArray(16384)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(chunk)
                    if(n<0)break
                    nBytes+=n
                    require(nBytes<=30L*1024*1024){"Photo is too large."}
                    output.write(chunk,0,n)
                    if(length>0)withContext(Dispatchers.Main) {
                        onProgress((100*nBytes/length).toInt().coerceIn(0,99))
                    }
                }
            }
        }
        require(nBytes>0){"No picture data received."}
        require(temp.renameTo(destination)){"Cannot save selected picture."}
        destination
    } catch(e:Throwable) {
        temp.delete()
        throw e
    } finally {connection.disconnect()}
}

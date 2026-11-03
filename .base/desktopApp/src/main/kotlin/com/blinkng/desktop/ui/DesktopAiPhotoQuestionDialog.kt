package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.blinkng.desktop.data.DesktopBlinkAiService
import com.blinkng.desktop.data.DesktopSupabaseClient
import kotlinx.coroutines.launch
import java.io.File

/** Explicit image handoff to the existing authenticated multimodal BLINK AI.
 * No network action occurs until Send is pressed by the user.
 */
@Composable
internal fun DesktopAiPhotoQuestionDialog(
    file: File,
    client: DesktopSupabaseClient,
    onDismiss: () -> Unit
) {
    val service=remember(client){DesktopBlinkAiService(client)}
    val scope=rememberCoroutineScope()
    var prompt by remember(file){mutableStateOf("")}
    var sending by remember(file){mutableStateOf(false)}
    var answer by remember(file){mutableStateOf<String?>(null)}
    var error by remember(file){mutableStateOf<String?>(null)}
    DisposableEffect(file) { onDispose { service.cancelActiveRequest() } }
    AlertDialog(
        onDismissRequest={if(!sending)onDismiss()},
        title={Text("Ask BLINK AI about this image")},
        text={
            Column(Modifier.widthIn(min=380.dp,max=650.dp),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                AsyncImage(file,"Image attached to BLINK AI question",
                    contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(220.dp))
                Text("This picture stays on your computer until you choose Send.",
                    style=MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value=prompt,onValueChange={prompt=it.take(8000)},
                    label={Text("What would you like to know about this photo?")},
                    minLines=2,maxLines=5,
                    modifier=Modifier.fillMaxWidth(),
                    enabled=!sending
                )
                answer?.let {Text(it)}
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
        },
        confirmButton={
            Button(enabled=!sending,onClick={
                sending=true
                scope.launch {
                    runCatching {
                        service.ask(
                            message=prompt.ifBlank {"Describe this image."},
                            imageFiles=listOf(file),
                            usePersonalContext=false,
                            useWebSearch=false,
                            temporaryChat=true
                        )
                    }.onSuccess{answer=it.text;error=null}
                        .onFailure{error=it.message?:"BLINK AI couldn't analyze this image."}
                    sending=false
                }
            }){Text(if(sending)"Analyzing…" else "Send to BLINK AI")}
        },
        dismissButton={
            TextButton(enabled=!sending,onClick=onDismiss){Text("Close")}
        }
    )
}

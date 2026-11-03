package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.supabase.BlinkChatPhotoEditor
import kotlinx.coroutines.launch

/** Reusable edit-copy photo adjustment dialog; never overwrites a device original. */
@Composable
internal fun BlinkPictureAdjustDialog(
    uri: Uri,
    onEdited: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exposure by remember(uri) { mutableFloatStateOf(0f) }
    var contrast by remember(uri) { mutableFloatStateOf(1f) }
    var saturation by remember(uri) { mutableFloatStateOf(1f) }
    var busy by remember(uri) { mutableStateOf(false) }
    val previewFilter = remember(exposure,contrast,saturation) {
        // Combine saturation and affine RGB adjustment to preview exported changes.
        val s = saturation
        val inv = 1f - s
        val r = .213f * inv
        val g = .715f * inv
        val b = .072f * inv
        val offset = (1f - contrast) * 128f / 255f + exposure * 75f / 255f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            contrast*(r+s), contrast*g, contrast*b, 0f, offset,
            contrast*r, contrast*(g+s), contrast*b, 0f, offset,
            contrast*r, contrast*g, contrast*(b+s), 0f, offset,
            0f,0f,0f,1f,0f
        )))
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                    Spacer(Modifier.width(5.dp))
                    Text("CANCEL", color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text("EDIT PHOTO", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            BlinkChatPhotoEditor.adjust(context, uri, exposure, contrast, saturation)
                        }.onSuccess { onEdited(it) }
                            .onFailure {
                                Toast.makeText(context, it.message ?: "Photo edit failed.",
                                    Toast.LENGTH_SHORT).show()
                            }
                        busy = false
                    }
                }, enabled = !busy) { Text(if (busy) "SAVING" else "DONE", color = Color(0xFF27A6FF)) }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment=Alignment.Center) {
                AsyncImage(
                    model = uri, contentDescription = "Live adjusted photo preview",
                    contentScale = ContentScale.Fit, colorFilter = previewFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = Color(0xFF27A6FF))
                    Spacer(Modifier.width(8.dp))
                    Text("ADJUST", color=Color.White)
                }
                Text("Exposure", color = Color.White, fontSize = 12.sp)
                Slider(value = exposure, onValueChange = { exposure = it },
                    valueRange = -1f..1f, enabled=!busy)
                Text("Contrast", color = Color.White, fontSize = 12.sp)
                Slider(value = contrast, onValueChange = { contrast = it },
                    valueRange=.5f..1.5f, enabled=!busy)
                Text("Saturation", color = Color.White, fontSize = 12.sp)
                Slider(value = saturation, onValueChange = { saturation = it },
                    valueRange=0f..2f, enabled=!busy)
            }
        }
    }
}

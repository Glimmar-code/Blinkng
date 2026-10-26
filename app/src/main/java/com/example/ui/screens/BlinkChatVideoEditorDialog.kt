package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.PremiumReelEditor

/**
 * Reuses the real Media3 export pipeline already used by BLINK Reels for chat
 * video editing. The original picker URI is not changed; edited MP4s are private
 * exports and must pass the usual chat upload/size validation.
 */
@Composable
internal fun BlinkChatVideoEditorDialog(
    sourceUri: Uri,
    onSend: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var workingUri by remember(sourceUri) { mutableStateOf(sourceUri) }
    var exporting by remember(sourceUri) { mutableStateOf(false) }
    var hasUnappliedEdits by remember(workingUri) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF08090D))
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().height(55.dp)
                .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !exporting) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    Text("CANCEL", color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text("BLINK VIDEO STUDIO", color = Color.White)
                Spacer(Modifier.weight(1f))
            }
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                PremiumReelEditor(
                    uri = workingUri.toString(),
                    onRemove = { if (!exporting) onDismiss() },
                    onEdited = { edited -> workingUri = Uri.parse(edited) },
                    onExporting = { exporting = it },
                    onPendingEdits = { hasUnappliedEdits = it }
                )
            }
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasUnappliedEdits) "Apply your edits before sending."
                    else "Review the video before sending.",
                    color = Color.LightGray,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = !exporting && !hasUnappliedEdits,
                    onClick = { onSend(workingUri) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C9DF3))
                ) { Text("Send video") }
            }
        }
    }
}

package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blinkng.shared.ui.editor.rememberVideoEditorState
import com.blinkng.shared.ui.editor.EditorTopBar

@Composable
fun DesktopVideoEditorDialog(
    sourceUri: String,
    onDismiss: () -> Unit,
    onEdited: (String) -> Unit
) {
    val videoState = rememberVideoEditorState(sourceUri)

    Dialog(
        onDismissRequest = { if (!videoState.exporting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            Modifier
                .background(Color.Black)
                .padding(12.dp)
                .fillMaxSize()
        ) {
            // Top bar
            EditorTopBar(
                title = "Edit video",
                onClose = onDismiss,
                onConfirm = { onEdited(videoState.uri) },
                confirmEnabled = !videoState.exporting
            )
            Spacer(Modifier.height(8.dp))
            // Video preview placeholder
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray)
            ) {
                // In a real implementation, use a video preview component
                Text(
                    text = "Video preview: ${videoState.uri}",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
            // Bottom controls placeholder
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Trim logic */ },
                    enabled = !videoState.exporting
                ) { Text("Trim") }
                Button(
                    onClick = { /* Mute logic */ },
                    enabled = !videoState.exporting
                ) { Text("Mute") }
                Button(
                    onClick = { /* Speed logic */ },
                    enabled = !videoState.exporting
                ) { Text("Speed") }
                Button(
                    onClick = { /* Rotate logic */ },
                    enabled = !videoState.exporting
                ) { Text("Rotate") }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Text overlay */ },
                    enabled = !videoState.exporting
                ) { Text("Text") }
                Button(
                    onClick = { /* Sticker overlay */ },
                    enabled = !videoState.exporting
                ) { Text("Sticker") }
                Button(
                    onClick = { /* Draw */ },
                    enabled = !videoState.exporting
                ) { Text("Draw") }
            }
            Spacer(Modifier.height(8.dp))
            // Export status
            Text(
                if (videoState.exporting) "Exporting…" else "Ready to send",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("Cancel") }
                Button(
                    onClick = { onEdited(videoState.uri) },
                    enabled = !videoState.exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) { Text("Send") }
            }
        }
    }
}

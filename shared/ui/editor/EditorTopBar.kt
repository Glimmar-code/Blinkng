package com.blinkng.shared.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Re‑usable top bar used by both photo and video editors.
 * It shows a close button on the left, a title in the centre, and a confirm/check
 * button on the right.
 */
@Composable
fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close editor")
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onConfirm, enabled = confirmEnabled) {
                Icon(Icons.Default.Check, contentDescription = "Apply changes")
            }
        }
    }
}

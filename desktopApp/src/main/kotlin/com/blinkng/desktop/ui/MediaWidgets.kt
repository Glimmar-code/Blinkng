package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun BlinkRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ImageNotSupported, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onLoading = { },
        onError = { },
    )
}

@Composable
fun BlinkAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = 40.dp,
) {
    if (!avatarUrl.isNullOrBlank()) {
        BlinkRemoteImage(
            url = avatarUrl,
            contentDescription = "$name profile photo",
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "B",
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

fun chooseImageFile(title: String = "Choose image"): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name ->
            name.lowercase().endsWith(".jpg") ||
                name.lowercase().endsWith(".jpeg") ||
                name.lowercase().endsWith(".png") ||
                name.lowercase().endsWith(".webp") ||
                name.lowercase().endsWith(".gif")
        }
        isVisible = true
    }
    val selected = dialog.file ?: return null
    return File(dialog.directory, selected)
}

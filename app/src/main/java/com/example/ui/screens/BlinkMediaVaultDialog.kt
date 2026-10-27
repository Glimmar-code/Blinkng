package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.BlinkPrivateMediaVault
import kotlinx.coroutines.launch

/**
 * In-app only; decrypted previews are short-lived, app-private files. No gallery
 * export, public URLs or automatic cloud sync. The vault is tied to the account.
 */
@Composable
internal fun BlinkMediaVaultDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var preview by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun dismiss() {
        preview?.let { uri -> scope.launch {
            BlinkPrivateMediaVault.clearTemporaryPreview(context, uri)
        } }
        onDismiss()
    }
    LaunchedEffect(Unit) {
        runCatching { BlinkPrivateMediaVault.items(context) }
            .onSuccess { ids = it }
            .onFailure { error = it.message ?: "Could not open Media Vault." }
        loading = false
    }
    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF080B12))
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::dismiss) {
                    Icon(Icons.Default.ArrowBack, "Close vault", tint = Color.White)
                }
                Text("MEDIA VAULT", color = Color.White,
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Lock, "Locally encrypted",
                    tint = Color(0xFF81C9FF))
            }
            Text(
                "Private, encrypted on this device. Items are not synced or backed up; uninstalling BLINK can permanently erase them.",
                color = Color.LightGray, modifier = Modifier.padding(15.dp)
            )
            error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(12.dp)) }
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else if (preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = "Decrypted photo visible only within Media Vault",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = {
                        val old = preview
                        preview = null
                        selected = null
                        if (old != null) scope.launch {
                            BlinkPrivateMediaVault.clearTemporaryPreview(context, old)
                        }
                    }) { Text("Back to vault") }
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, null)
                        Text("Delete encrypted copy")
                    }
                }
            } else if (ids.isEmpty()) {
                Text("No saved pictures yet.", color = Color.LightGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ids) { index, id ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1D2634),
                            modifier = Modifier.fillMaxWidth().clickable {
                                loading = true
                                error = null
                                scope.launch {
                                    runCatching { BlinkPrivateMediaVault.open(context, id) }
                                        .onSuccess { preview = it; selected = id }
                                        .onFailure { error = it.message ?: "Cannot decrypt picture." }
                                    loading = false
                                }
                            }
                        ) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = Color(0xFF81C9FF))
                                Spacer(Modifier.width(12.dp))
                                Text("Encrypted picture ${index + 1}", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete encrypted picture?") },
            text = { Text("This removes only the vault copy. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = selected ?: return@TextButton
                    val oldPreview = preview
                    scope.launch {
                        runCatching { BlinkPrivateMediaVault.remove(context, id) }
                            .onSuccess { removed ->
                                if (removed) {
                                    oldPreview?.let {
                                        BlinkPrivateMediaVault.clearTemporaryPreview(context, it)
                                    }
                                    ids = ids.filterNot { it == id }
                                    preview = null
                                    selected = null
                                }
                            }
                            .onFailure { error = it.message ?: "Deletion failed." }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

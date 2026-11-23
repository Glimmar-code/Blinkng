package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastAuditEntry
import com.example.R
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkBroadcastAuditLogScreen(
    viewModel: BlinkBroadcastViewModel,
    onBack: () -> Unit,
) {
    var records by remember { mutableStateOf<List<BroadcastAuditEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("") }
    fun load() {
        loading = true
        viewModel.getAuditLog { result ->
            result.onSuccess { records = it; error = null }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    val filtered = remember(records, filter) { records.filter { filter.isBlank() || it.action.equals(filter, true) } }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.broadcast_audit_log)) },
            navigationIcon = { IconButton(onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
            } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to R.string.broadcast_all, "Created" to R.string.broadcast_audit_created,
                    "Edited" to R.string.broadcast_audit_edited, "Sent" to R.string.broadcast_audit_sent,
                    "Ended" to R.string.broadcast_audit_ended).forEach { (key, label) ->
                    FilterChip(filter == key, { filter = key }, label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            when {
                loading -> BlinkBroadcastSkeleton()
                error != null -> BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_error),
                    error.orEmpty(), stringResource(R.string.broadcast_retry), ::load)
                filtered.isEmpty() -> BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_empty_title),
                    stringResource(R.string.broadcast_empty_body))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(filtered, key = { it.id }) { entry ->
                        BlinkBroadcastSurfaceCard {
                            Text(entry.broadcastTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.broadcast_audit_action) + " · " + entry.action)
                            Text(stringResource(R.string.broadcast_audit_actor) + " · " + entry.actor)
                            Text(stringResource(R.string.broadcast_audit_when) + " · " + entry.at.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastInboxItem
import com.blinkng.shared.broadcast.BroadcastType
import com.example.viewmodel.BlinkBroadcastUiState
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkAnnouncementInboxScreen(
    state: BlinkBroadcastUiState,
    viewModel: BlinkBroadcastViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit = {},
) {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.broadcast_all),
        stringResource(R.string.broadcast_tab_announcements),
        stringResource(R.string.broadcast_tab_campaigns),
        stringResource(R.string.broadcast_tab_offers),
    )
    val inbox = (state as? BlinkBroadcastUiState.Success)?.inbox.orEmpty()
    val filtered = remember(inbox, selected) {
        inbox.filter { selected == 0 || it.broadcast.type.ordinal == selected - 1 }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.broadcast_inbox)) },
            navigationIcon = {
                IconButton(onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
                }
            },
            actions = {
                IconButton(onClick = onSettings, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.broadcast_settings))
                }
                TextButton(onClick = viewModel::markAllRead, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.MarkEmailRead, contentDescription = null)
                    Text(stringResource(R.string.broadcast_mark_all_read))
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 14.dp) {
                tabs.forEachIndexed { index, title -> Tab(
                    selected = selected == index, onClick = { selected = index },
                    modifier = Modifier.heightIn(min = 48.dp), text = { Text(title) },
                ) }
            }
            when (state) {
                BlinkBroadcastUiState.Loading -> repeat(3) { BlinkBroadcastSkeleton(Modifier.padding(12.dp)) }
                is BlinkBroadcastUiState.Error -> BlinkBroadcastEmptyOrError(
                    stringResource(R.string.broadcast_error),
                    if (state.offline) stringResource(R.string.broadcast_offline) else state.message,
                    stringResource(R.string.broadcast_retry), viewModel::refresh,
                )
                else -> if (filtered.isEmpty()) {
                    BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_empty_title),
                        stringResource(R.string.broadcast_empty_body))
                } else LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.broadcast.id }) { item ->
                        BroadcastInboxRow(item, onDismiss = { viewModel.dismiss(item.broadcast.id) },
                            onClick = {
                                viewModel.markRead(item.broadcast.id)
                                onOpen(item.broadcast.deepLink)
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastInboxRow(
    item: BroadcastInboxItem,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    var drag by remember(item.broadcast.id) { mutableFloatStateOf(0f) }
    Surface(
        Modifier.fillMaxWidth().pointerInput(item.broadcast.id) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount -> drag += amount },
                onDragEnd = { if (kotlin.math.abs(drag) > 90f) onDismiss(); drag = 0f },
                onDragCancel = { drag = 0f },
            )
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!item.read) Badge(Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f).padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BlinkBroadcastTypeBadge(item.broadcast.type)
                Text(item.broadcast.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(item.broadcast.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.broadcast_close))
            }
        }
    }
}

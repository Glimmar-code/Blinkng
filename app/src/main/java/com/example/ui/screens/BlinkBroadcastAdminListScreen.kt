package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.*
import com.example.R
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkBroadcastAdminListScreen(
    viewModel: BlinkBroadcastViewModel,
    onCreate: () -> Unit,
    onEdit: (BroadcastDraft) -> Unit = {},
    onAnalytics: (String) -> Unit,
    onAudit: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<BroadcastStatus?>(null) }
    var type by remember { mutableStateOf<BroadcastType?>(null) }
    var broadcasts by remember { mutableStateOf<List<Broadcast>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<Broadcast, Boolean>?>(null) }
    var loading by remember { mutableStateOf(true) }
    fun reload() {
        loading = true
        viewModel.getAdminBroadcasts { result ->
            result.onSuccess { broadcasts = it; error = null }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    val shown = remember(broadcasts, search, status, type) {
        broadcasts.filter {
            (status == null || it.status == status) &&
                (type == null || it.type == type) &&
                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.body.contains(search, ignoreCase = true))
        }.sortedByDescending { it.priority }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.broadcast_admin_list)) },
                actions = {
                    IconButton(onClick = { shown.firstOrNull()?.let { onAnalytics(it.id) } }) {
                        Icon(Icons.Default.Analytics, contentDescription = stringResource(R.string.broadcast_analytics))
                    }
                    IconButton(onClick = onAudit) { Icon(Icons.Default.History, contentDescription = stringResource(R.string.broadcast_audit_log)) }
                },
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(
            onClick = { viewModel.resetDraft(); onCreate() },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.broadcast_new)) },
        ) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.broadcast_search)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
             Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(type == null, { type = null },
                    label = { Text(stringResource(R.string.broadcast_admin_all_types)) }, modifier = Modifier.heightIn(min = 48.dp))
                BroadcastType.entries.forEach { kind ->
                    val label = when (kind) {
                        BroadcastType.ANNOUNCEMENT -> R.string.broadcast_type_announcement
                        BroadcastType.CAMPAIGN -> R.string.broadcast_campaign
                        BroadcastType.PROMO -> R.string.broadcast_type_promo
                    }
                    FilterChip(type == kind, { type = if (type == kind) null else kind },
                        label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(status == null, { status = null },
                    label = { Text(stringResource(R.string.broadcast_filter_all)) }, modifier = Modifier.heightIn(min = 48.dp))
                BroadcastStatus.entries.forEach { item ->
                    val label = stringResource(item.labelRes())
                    FilterChip(status == item, { status = if (status == item) null else item },
                        label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (loading) BlinkBroadcastSkeleton()
            error?.let {
                BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_error_admin_load), it,
                    stringResource(R.string.broadcast_retry_admin), ::reload)
            }
            if (!loading && error == null && shown.isEmpty()) BlinkBroadcastEmptyOrError(
                stringResource(R.string.broadcast_no_results), stringResource(R.string.broadcast_empty_admin))
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { broadcast ->
                    val duplicateTitle = stringResource(R.string.broadcast_duplicate_title_format, broadcast.title)
                    AdminBroadcastCard(
                        broadcast = broadcast,
                        onEdit = { onEdit(broadcast.toDraft()) },
                        onDuplicate = { viewModel.createBroadcast(
                            broadcast.toDraft().copy(
                                id = null,
                                title = duplicateTitle.take(60),
                            )
                        ) { reload() } },
                        onEnd = { confirm = broadcast to false },
                        onDelete = { confirm = broadcast to true },
                        onAnalytics = { onAnalytics(broadcast.id) },
                    )
                }
            }
        }
    }
    confirm?.let { (broadcast, deleting) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.broadcast_confirmation)) },
            text = { Text(stringResource(if (deleting) R.string.broadcast_confirm_delete else R.string.broadcast_confirm_end)) },
            confirmButton = { TextButton(onClick = {
                if (deleting) viewModel.deleteDraft(broadcast.id) else viewModel.endBroadcast(broadcast.id)
                confirm = null
                reload()
            }) { Text(stringResource(if (deleting) R.string.broadcast_delete else R.string.broadcast_end_now)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.broadcast_cancel)) } },
        )
    }
}

@Composable
private fun AdminBroadcastCard(
    broadcast: Broadcast,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
    onAnalytics: () -> Unit,
) {
    BlinkBroadcastSurfaceCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            BlinkBroadcastTypeBadge(broadcast.type)
            Spacer(Modifier.weight(1f))
            Text(stringResource(broadcast.status.labelRes()), style = MaterialTheme.typography.labelMedium)
        }
        Text(broadcast.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.broadcast_audience_format,
            stringResource(broadcast.audience.kind.labelRes())), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.broadcast_dates_format, broadcast.startsAt.toString(), broadcast.endsAt.toString()),
            style = MaterialTheme.typography.bodySmall, maxLines = 2)
        if (broadcast.isPreview) BlinkBroadcastPreviewStamp()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.broadcast_edit)) }
            IconButton(onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.broadcast_duplicate)) }
            IconButton(onAnalytics) { Icon(Icons.Default.Analytics, contentDescription = stringResource(R.string.broadcast_analytics)) }
            if (broadcast.status != BroadcastStatus.ENDED) IconButton(onEnd) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.broadcast_end_now))
            }
            if (broadcast.status == BroadcastStatus.DRAFT) IconButton(onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.broadcast_delete_draft))
            }
        }
    }
}

internal fun Broadcast.toDraft() = BroadcastDraft(
    id = id, type = type, title = title, body = body, imageUrl = imageUrl, ctaLabel = ctaLabel,
    deepLink = deepLink, isBlocking = isBlocking, rules = rules, audience = audience, channels = channels,
    startsAt = startsAt, endsAt = endsAt, priority = priority, dismissible = dismissible,
)

internal fun BroadcastStatus.labelRes() = when (this) {
    BroadcastStatus.DRAFT -> R.string.broadcast_draft
    BroadcastStatus.SCHEDULED -> R.string.broadcast_scheduled
    BroadcastStatus.LIVE -> R.string.broadcast_live
    BroadcastStatus.ENDED -> R.string.broadcast_ended
}

private fun BroadcastAudienceKind.labelRes() = when (this) {
    BroadcastAudienceKind.EVERYONE -> R.string.broadcast_audience_everyone
    BroadcastAudienceKind.CAMPUS -> R.string.broadcast_audience_campus
    BroadcastAudienceKind.NEW_USERS -> R.string.broadcast_audience_new_users
    BroadcastAudienceKind.INACTIVE_7_DAYS -> R.string.broadcast_audience_inactive_7
    BroadcastAudienceKind.INACTIVE_30_DAYS -> R.string.broadcast_audience_inactive_30
    BroadcastAudienceKind.SELLERS -> R.string.broadcast_audience_sellers
    BroadcastAudienceKind.VERIFIED -> R.string.broadcast_audience_verified
    BroadcastAudienceKind.APP_VERSION -> R.string.broadcast_audience_app_version
}

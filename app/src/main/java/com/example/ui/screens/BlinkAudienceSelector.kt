package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastAudienceEstimate
import com.blinkng.shared.broadcast.BroadcastAudienceFilter
import com.blinkng.shared.broadcast.BroadcastAudienceKind
import com.example.R
import com.example.viewmodel.BlinkBroadcastViewModel

@Composable
fun BlinkAudienceSelector(
    value: BroadcastAudienceFilter,
    viewModel: BlinkBroadcastViewModel,
    onChange: (BroadcastAudienceFilter) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val campuses = stringArrayResource(R.array.broadcast_demo_campuses).toList()
    var query by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf<BroadcastAudienceEstimate?>(null) }
    LaunchedEffect(value) {
        viewModel.estimateAudience(value) { result -> estimate = result.getOrNull() }
    }
    val options = listOf(
        BroadcastAudienceKind.EVERYONE to R.string.broadcast_audience_everyone,
        BroadcastAudienceKind.CAMPUS to R.string.broadcast_audience_campus,
        BroadcastAudienceKind.NEW_USERS to R.string.broadcast_audience_new_users,
        BroadcastAudienceKind.INACTIVE_7_DAYS to R.string.broadcast_audience_inactive_7,
        BroadcastAudienceKind.INACTIVE_30_DAYS to R.string.broadcast_audience_inactive_30,
        BroadcastAudienceKind.SELLERS to R.string.broadcast_audience_sellers,
        BroadcastAudienceKind.VERIFIED to R.string.broadcast_audience_verified,
        BroadcastAudienceKind.APP_VERSION to R.string.broadcast_audience_app_version,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (kind, label) ->
                    FilterChip(
                        selected = value.kind == kind,
                        onClick = {
                            onChange(value.copy(kind = kind))
                            estimate = null
                        },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (value.kind == BroadcastAudienceKind.CAMPUS) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text(stringResource(R.string.broadcast_audience_search_campuses)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            LazyColumn(Modifier.heightIn(max = 190.dp)) {
                items(campuses.filter { it.contains(query, ignoreCase = true) }, key = { it }) { campus ->
                    val checked = campus in value.campuses
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Checkbox(checked, onCheckedChange = {
                            onChange(value.copy(campuses = if (it) value.campuses + campus else value.campuses - campus))
                        })
                        Text(campus, Modifier.padding(top = 12.dp))
                    }
                }
            }
            Text(stringResource(R.string.broadcast_campus_count, value.campuses.size),
                style = MaterialTheme.typography.labelMedium)
        }
        if (value.kind == BroadcastAudienceKind.APP_VERSION) {
            OutlinedTextField(value = value.appVersion, onValueChange = { onChange(value.copy(appVersion = it.take(32))) },
                label = { Text(stringResource(R.string.broadcast_audience_app_version)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        estimate?.let {
            Text(stringResource(R.string.broadcast_estimated_reach, it.reach),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

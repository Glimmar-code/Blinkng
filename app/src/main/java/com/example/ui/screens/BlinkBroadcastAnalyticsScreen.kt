package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastAnalytics
import com.example.R
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkBroadcastAnalyticsScreen(
    viewModel: BlinkBroadcastViewModel,
    broadcastId: String,
    onBack: () -> Unit,
) {
    var analytics by remember(broadcastId) { mutableStateOf<BroadcastAnalytics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    fun load() {
        loading = true
        viewModel.getAnalytics(broadcastId) { result ->
            result.onSuccess { analytics = it; error = null }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(broadcastId) { load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.broadcast_analytics)) },
            navigationIcon = { IconButton(onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
            } })
    }) { padding ->
        when {
            loading -> BlinkBroadcastSkeleton(Modifier.padding(padding).padding(16.dp))
            error != null -> BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_error), error.orEmpty(),
                stringResource(R.string.broadcast_retry), ::load)
            analytics == null -> BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_empty_title),
                stringResource(R.string.broadcast_no_chart_data))
            else -> AnalyticsContent(analytics!!, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AnalyticsContent(value: BroadcastAnalytics, modifier: Modifier = Modifier) {
    val ctr = if (value.impressions == 0L) 0.0 else value.clicks * 100.0 / value.impressions
    val dismissRate = if (value.reach == 0L) 0.0 else value.dismissals * 100.0 / value.reach
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(stringResource(R.string.broadcast_reach), value.reach.toString(), Modifier.weight(1f))
            MetricTile(stringResource(R.string.broadcast_impressions), value.impressions.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(stringResource(R.string.broadcast_ctr), "%.1f%%".format(ctr), Modifier.weight(1f))
            MetricTile(stringResource(R.string.broadcast_conversions), value.conversions.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(stringResource(R.string.broadcast_dismiss_rate), "%.1f%%".format(dismissRate), Modifier.weight(1f))
            MetricTile(stringResource(R.string.broadcast_claims_budget),
                "${value.claims} / ${value.budgetUsed} · ${value.budgetTotal}", Modifier.weight(1f))
        }
        Text(stringResource(R.string.broadcast_graph_reach), style = MaterialTheme.typography.titleMedium)
        if (value.series.isEmpty()) Text(stringResource(R.string.broadcast_no_chart_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val description = stringResource(R.string.broadcast_last_seven_days)
            Canvas(Modifier.fillMaxWidth().height(150.dp)
                .semantics { contentDescription = description }) {
                val max = (value.series.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
                val gap = if (value.series.size > 1) size.width / (value.series.size - 1) else 0f
                val path = Path()
                value.series.forEachIndexed { index, point ->
                    val x = gap * index
                    val y = size.height - (point.toFloat() / max * (size.height - 12.dp.toPx()))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(BlinkBroadcastColors.Blue, 4.dp.toPx(), Offset(x, y))
                }
                drawPath(path, BlinkBroadcastColors.Blue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(stringResource(R.string.broadcast_audience_breakdown), style = MaterialTheme.typography.titleMedium)
        value.audienceBreakdown.forEach { (label, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text(count.toString())
                }
                val fraction = if (value.reach <= 0) 0f else (count.toFloat() / value.reach).coerceIn(0f, 1f)
                LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    BlinkBroadcastSurfaceCard(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

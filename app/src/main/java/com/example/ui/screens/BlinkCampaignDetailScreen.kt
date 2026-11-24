package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.Broadcast
import com.example.viewmodel.BlinkBroadcastUiState
import com.example.viewmodel.BlinkBroadcastViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkCampaignDetailScreen(
    campaignId: String,
    state: BlinkBroadcastUiState,
    viewModel: BlinkBroadcastViewModel,
    onBack: () -> Unit,
    onShare: (Broadcast) -> Unit = {},
) {
    val campaign = (state as? BlinkBroadcastUiState.Success)?.broadcasts?.firstOrNull { it.id == campaignId }
    val detail by viewModel.campaignDetails.collectAsState()
    LaunchedEffect(campaignId) { viewModel.loadCampaign(campaignId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(campaign?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
                    }
                },
                actions = {
                    if (campaign != null) IconButton(onClick = { onShare(campaign) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.broadcast_share))
                    }
                },
            )
        },
        bottomBar = {
            if (campaign != null) Surface(tonalElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp)) {
                    BlinkBroadcastPrimaryButton(
                        stringResource(when {
                            campaign.completed -> R.string.broadcast_reward_claimed
                            campaign.joined -> R.string.broadcast_view_progress
                            else -> R.string.broadcast_join
                        }),
                        Modifier.fillMaxWidth(),
                        enabled = !campaign.completed,
                    ) { if (campaign.joined) viewModel.loadCampaign(campaignId) else viewModel.joinCampaign(campaignId) }
                }
            }
        },
    ) { padding ->
        when {
            state is BlinkBroadcastUiState.Loading -> BlinkBroadcastSkeleton(Modifier.padding(padding).padding(16.dp))
            campaign == null -> BlinkBroadcastEmptyOrError(
                stringResource(R.string.broadcast_error),
                (state as? BlinkBroadcastUiState.Error)?.message ?: stringResource(R.string.broadcast_offline),
                stringResource(R.string.broadcast_retry), viewModel::refresh,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "campaign_hero") {
                    BlinkCampaignHeroCard(campaign, { viewModel.joinCampaign(campaignId) }, { viewModel.loadCampaign(campaignId) })
                }
                item(key = "campaign_progress") {
                    BlinkBroadcastSurfaceCard {
                        if (campaign.completed) BlinkCampaignConfetti()
                        Text(stringResource(R.string.broadcast_my_progress), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        BlinkBroadcastProgress(detail.progress, campaign.target ?: campaign.rules.target ?: 1)
                        if (campaign.isPreview) BlinkBroadcastPreviewStamp()
                    }
                }
                item(key = "campaign_rules") {
                    BlinkBroadcastSurfaceCard {
                        Text(stringResource(R.string.broadcast_rules), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(campaign.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "campaign_rewards") {
                    BlinkBroadcastSurfaceCard {
                        Text(stringResource(R.string.broadcast_rewards), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        campaign.rules.rewardTiers.forEach { tier ->
                            val unlocked = detail.progress >= tier.threshold
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = stringResource(
                                        if (unlocked) R.string.broadcast_reward_unlocked else R.string.broadcast_reward_locked),
                                    tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.broadcast_progress_format,
                                    tier.threshold, campaign.target ?: tier.threshold) + " · " + tier.reward)
                            }
                        }
                        if (campaign.completed) Text(stringResource(R.string.broadcast_campaign_completed),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                item(key = "campaign_leaderboard_title") {
                    Text(stringResource(R.string.broadcast_leaderboard), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
                if (detail.loading) item(key = "campaign_leaderboard_loading") { BlinkBroadcastSkeleton() }
                detail.error?.let { error ->
                    item(key = "campaign_leaderboard_error") {
                        BlinkBroadcastEmptyOrError(stringResource(R.string.broadcast_error), error,
                            stringResource(R.string.broadcast_retry)) { viewModel.loadCampaign(campaignId) }
                    }
                }
                itemsIndexed(detail.leaderboard, key = { _, row -> "${row.rank}:${row.name}" }) { _, row ->
                    ListItem(
                        headlineContent = { Text(row.name, fontWeight = if (row.isCurrentUser) FontWeight.Bold else FontWeight.Normal) },
                        supportingContent = { Text(stringResource(R.string.broadcast_points_format, row.score)) },
                        leadingContent = { Text("${row.rank}", fontWeight = FontWeight.Bold) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlinkCampaignConfetti() {
    val transition = rememberInfiniteTransition(label = "campaignConfetti")
    val phase by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1600),
            androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "confettiPhase",
    )
    val colors = listOf(
        BlinkBroadcastColors.Blue, BlinkBroadcastColors.Violet, BlinkBroadcastColors.Gold,
        BlinkBroadcastColors.Mint, BlinkBroadcastColors.Coral,
    )
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(38.dp)) {
        repeat(24) { index ->
            val fraction = (phase + index * .137f) % 1f
            val x = size.width * ((index * 37 % 100) / 100f)
            val y = size.height * fraction
            drawCircle(colors[index % colors.size], radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

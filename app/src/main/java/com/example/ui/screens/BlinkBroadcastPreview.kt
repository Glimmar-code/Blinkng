package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.Broadcast
import com.blinkng.shared.broadcast.BroadcastComposition
import com.blinkng.shared.broadcast.BroadcastDraft
import com.blinkng.shared.broadcast.BroadcastType
import com.example.viewmodel.BlinkBroadcastViewModel

@Composable
fun BlinkBroadcastPreview(
    draft: BroadcastDraft,
    viewModel: BlinkBroadcastViewModel,
    broadcastId: String? = draft.id,
) {
    var dark by remember { mutableStateOf(true) }
    var large by remember { mutableStateOf(false) }
    val colorScheme = if (dark) darkColorScheme() else lightColorScheme()
    val preview = Broadcast(
        id = broadcastId ?: "broadcast-preview",
        type = draft.type,
        title = draft.title.ifBlank { stringResource(R.string.broadcast_title_label) },
        body = draft.body.ifBlank { stringResource(R.string.broadcast_body_label) },
        imageUrl = draft.imageUrl,
        ctaLabel = draft.ctaLabel,
        deepLink = draft.deepLink,
        priority = draft.priority,
        startsAt = draft.startsAt,
        endsAt = draft.endsAt,
        isBlocking = draft.isBlocking,
        target = draft.rules.target,
        rewardText = draft.rules.rewardTiers.firstOrNull()?.reward.orEmpty(),
        rules = draft.rules,
        audience = draft.audience,
        channels = draft.channels,
        isPreview = true,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(dark, { dark = true }, label = { Text(stringResource(R.string.broadcast_dark_mode)) },
                modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(!dark, { dark = false }, label = { Text(stringResource(R.string.broadcast_light_mode)) },
                modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(!large, { large = false }, label = { Text(stringResource(R.string.broadcast_phone_small)) },
                modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(large, { large = true }, label = { Text(stringResource(R.string.broadcast_phone_large)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        MaterialTheme(colorScheme = colorScheme) {
            Surface(Modifier.fillMaxWidth(if (large) 1f else .82f), shape = MaterialTheme.shapes.extraLarge,
                color = colorScheme.background, tonalElevation = 3.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (preview.isBlocking) Text(stringResource(R.string.broadcast_blocking_title),
                        style = MaterialTheme.typography.titleMedium)
                    when (preview.type) {
                        BroadcastType.ANNOUNCEMENT -> BlinkFeedBanner(listOf(preview), viewModel, {})
                        BroadcastType.CAMPAIGN -> BlinkCampaignHeroCard(preview, {}, {})
                        BroadcastType.PROMO -> BlinkPromoCard(preview, viewModel)
                    }
                    BlinkBroadcastPreviewStamp()
                }
            }
        }
        if (!broadcastId.isNullOrBlank()) {
            BlinkBroadcastPrimaryButton(stringResource(R.string.broadcast_send_test), Modifier.fillMaxWidth()) {
                viewModel.sendTest(broadcastId) {}
            }
        } else {
            Text(stringResource(R.string.broadcast_test_not_saved),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

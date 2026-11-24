package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.Broadcast
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun BlinkCampaignHeroCard(
    campaign: Broadcast,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BlinkBroadcastColors.HeroGradient)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlinkBroadcastTypeBadge(campaign.type)
            Spacer(Modifier.weight(1f))
            BlinkBroadcastCountdown(campaign.endsAt)
        }
        BlinkBroadcastImage(campaign.imageUrl)
        Text(campaign.title, style = MaterialTheme.typography.headlineSmall,
            color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black)
        Text(campaign.body, style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f))
        if ((campaign.target ?: campaign.rules.target ?: 0) > 0) {
            BlinkBroadcastProgress(campaign.progress, campaign.target ?: campaign.rules.target ?: 0)
        }
        if (campaign.rewardText.isNotBlank()) {
            Text(campaign.rewardText, color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        if (campaign.isPreview) BlinkBroadcastPreviewStamp()
        BlinkBroadcastPrimaryButton(
            stringResource(if (campaign.joined) R.string.broadcast_view_progress else R.string.broadcast_join),
            Modifier.align(Alignment.End),
        ) { if (campaign.joined) onOpen() else onJoin() }
    }
}

@Composable
fun BlinkInlineCampaignCard(
    campaign: Broadcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember(campaign.id) { mutableStateOf(Instant.now()) }
    LaunchedEffect(campaign.id) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }
    if (campaign.completed || !campaign.endsAt.isAfter(now) || campaign.startsAt.isAfter(now)) return
    Surface(
        modifier.fillMaxWidth().heightIn(min = 70.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlinkBroadcastTypeBadge(campaign.type)
            Column(Modifier.weight(1f)) {
                Text(campaign.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(campaign.rewardText.ifBlank { campaign.body }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            BlinkBroadcastCountdown(campaign.endsAt)
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

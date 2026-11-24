package com.example.ui.screens

import com.example.R
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.Broadcast
import com.blinkng.shared.broadcast.BroadcastClaimState
import com.example.viewmodel.BlinkBroadcastViewModel

@Composable
fun BlinkPromoCard(
    promo: Broadcast,
    viewModel: BlinkBroadcastViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var success by remember(promo.id) { mutableStateOf(false) }
    var claimedCoins by remember(promo.id) { mutableIntStateOf(0) }
    var now by remember(promo.id) { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(promo.id) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = java.time.Instant.now()
        }
    }
    val displayClaimState = if (!promo.endsAt.isAfter(now)) BroadcastClaimState.EXPIRED else promo.claimState
    val animatedCoins by animateIntAsState(claimedCoins, tween(900), label = "promoCoins")
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .background(BlinkBroadcastColors.PromoGradient).animateContentSize().padding(if (compact) 13.dp else 17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlinkBroadcastTypeBadge(promo.type)
            Spacer(Modifier.weight(1f))
            BlinkBroadcastCountdown(promo.endsAt)
        }
        BlinkBroadcastImage(promo.imageUrl)
        Text(promo.title, color = androidx.compose.ui.graphics.Color.White,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold)
        Text(promo.rewardText.ifBlank { promo.body }, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f),
            style = MaterialTheme.typography.bodyMedium)
        if (promo.isPreview) BlinkBroadcastPreviewStamp()
        val label = when (displayClaimState) {
            BroadcastClaimState.AVAILABLE -> stringResource(R.string.broadcast_claim)
            BroadcastClaimState.CLAIMING -> stringResource(R.string.broadcast_claiming)
            BroadcastClaimState.CLAIMED -> stringResource(R.string.broadcast_claimed)
            BroadcastClaimState.LIMIT_REACHED -> stringResource(R.string.broadcast_limit_reached)
            BroadcastClaimState.EXPIRED -> stringResource(R.string.broadcast_expired)
            BroadcastClaimState.SOLD_OUT -> stringResource(R.string.broadcast_sold_out)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayClaimState == BroadcastClaimState.CLAIMED) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BlinkBroadcastColors.Mint)
            }
            Spacer(Modifier.weight(1f))
            BlinkBroadcastPrimaryButton(label, enabled = displayClaimState == BroadcastClaimState.AVAILABLE) {
                viewModel.claimPromo(promo.id) { result ->
                    if (result == BroadcastClaimState.CLAIMED) {
                        success = true
                        claimedCoins = promo.rules.rewardAmount ?: 0
                    }
                }
            }
        }
        if (claimedCoins > 0) Text(stringResource(R.string.broadcast_bonus_coins, animatedCoins),
            color = BlinkBroadcastColors.Gold, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
    }
    if (success) AlertDialog(
        onDismissRequest = { success = false },
        title = { Text(stringResource(R.string.broadcast_claim_success_title)) },
        text = { Text(promo.rewardText) },
        confirmButton = { TextButton(onClick = { success = false }) { Text(stringResource(R.string.broadcast_claimed)) } },
    )
}

@Composable
fun BlinkPromoStrip(
    promos: List<Broadcast>,
    placement: String,
    viewModel: BlinkBroadcastViewModel,
    onOpenPromo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches = remember(promos, placement) {
        val now = java.time.Instant.now()
        promos.filter { it.type == com.blinkng.shared.broadcast.BroadcastType.PROMO &&
            it.status == com.blinkng.shared.broadcast.BroadcastStatus.LIVE &&
            !it.startsAt.isAfter(now) && it.endsAt.isAfter(now) &&
            (placement in it.placement || placement in it.rules.placement) }
    }
    if (matches.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        matches.forEach { promo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = BlinkBroadcastColors.Mint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(promo.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(promo.rewardText, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onOpenPromo(promo.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.broadcast_open))
                }
            }
        }
    }
}

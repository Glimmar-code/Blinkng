package com.example.ui.screens

import com.example.R
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import com.blinkng.shared.broadcast.Broadcast
import com.blinkng.shared.broadcast.BroadcastType
import com.example.viewmodel.BlinkBroadcastUiState
import com.example.viewmodel.BlinkBroadcastViewModel

@Composable
fun BlinkFeedBanner(
    broadcasts: List<Broadcast>,
    viewModel: BlinkBroadcastViewModel,
    onOpenDeepLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(broadcasts) {
        broadcasts.filter { it.type == BroadcastType.ANNOUNCEMENT && !it.isDismissed }
            .sortedByDescending { it.priority }
    }
    if (items.isEmpty()) return
    val pager = rememberPagerState(pageCount = { items.size })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        HorizontalPager(state = pager, key = { items[it].id }) { page ->
            val item = items[page]
            var visible by remember(item.id) { mutableStateOf(true) }
            LaunchedEffect(item.id, item.isPreview) {
                if (!item.isPreview) viewModel.trackImpression(item.id)
            }
            AnimatedVisibility(
                visible = visible,
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                BlinkBroadcastSurfaceCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BlinkBroadcastTypeBadge(item.type, official = true)
                            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                        if (item.dismissible) {
                            IconButton(
                                onClick = {
                                    visible = false
                                    viewModel.dismiss(item.id)
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.broadcast_close))
                            }
                        }
                    }
                    Text(item.body, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BlinkBroadcastImage(item.imageUrl)
                    if (item.isPreview) BlinkBroadcastPreviewStamp()
                    if (item.ctaLabel.isNotBlank()) {
                        BlinkBroadcastPrimaryButton(item.ctaLabel) {
                            viewModel.trackClick(item.id)
                            onOpenDeepLink(item.deepLink)
                        }
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { index ->
                    Box(
                        Modifier.size(if (pager.currentPage == index) 8.dp else 6.dp)
                            .background(
                                if (pager.currentPage == index) BlinkBroadcastColors.Blue
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = .24f),
                                CircleShape,
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun BlinkBroadcastFeedHeader(
    state: BlinkBroadcastUiState,
    viewModel: BlinkBroadcastViewModel,
    isAdmin: Boolean,
    onInbox: () -> Unit,
    onAdmin: () -> Unit,
    onDeepLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = (state as? BlinkBroadcastUiState.Success)?.broadcasts.orEmpty()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }
    val liveItems = items.filter { it.status == BroadcastStatus.LIVE &&
        !it.startsAt.isAfter(now) && it.endsAt.isAfter(now) }
    val blocking = liveItems.firstOrNull { it.isBlocking }
    val unread = (state as? BlinkBroadcastUiState.Success)?.inbox?.count { !it.read } ?: 0
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.broadcast_inbox), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onInbox, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (unread > 0) "$unread" else stringResource(R.string.broadcast_all))
            }
            if (isAdmin) TextButton(onClick = onAdmin, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.broadcast_admin))
            }
        }
        when (state) {
            BlinkBroadcastUiState.Loading -> BlinkBroadcastSkeleton()
            is BlinkBroadcastUiState.Error -> BlinkBroadcastEmptyOrError(
                stringResource(R.string.broadcast_error),
                if (state.offline) stringResource(R.string.broadcast_offline) else state.message.ifBlank { stringResource(R.string.broadcast_error) },
                stringResource(R.string.broadcast_retry), viewModel::refresh,
            )
            BlinkBroadcastUiState.Empty -> BlinkBroadcastEmptyOrError(
                stringResource(R.string.broadcast_empty_title), stringResource(R.string.broadcast_empty_body))
            is BlinkBroadcastUiState.Success -> {
                val banners = liveItems.filter { it.type == BroadcastType.ANNOUNCEMENT }
                if (banners.isNotEmpty()) BlinkFeedBanner(banners, viewModel, onDeepLink)
                liveItems.firstOrNull { it.type == BroadcastType.CAMPAIGN && !it.completed }?.let { campaign ->
                    BlinkCampaignHeroCard(campaign, onJoin = { viewModel.joinCampaign(campaign.id) },
                        onOpen = { onDeepLink("blink://campaign/${campaign.id}") })
                }
            }
        }
    }
    blocking?.let { required ->
        BlinkBlockingDialog(
            broadcast = required,
            onUpdate = {
                val marketUri = required.deepLink.takeIf {
                    it.startsWith("market://") || it.startsWith("https://play.google.com")
                }?.let(Uri::parse) ?: Uri.parse("market://details?id=${context.packageName}")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, marketUri)) }
                    .onFailure {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                    }
            },
            onRetry = viewModel::refresh,
        )
    }
}

package com.example.ui.screens

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blinkng.shared.broadcast.BlinkBroadcastRoutes
import com.blinkng.shared.broadcast.BroadcastType
import com.example.viewmodel.BlinkBroadcastUiState
import com.example.viewmodel.BlinkBroadcastViewModel

object BlinkDeepLinkHandler {
    val supportedLinks = mapOf(
        "blink://spin" to "spin",
        "blink://market" to "market",
        "blink://contest" to "contest",
        "blink://campaign/{id}" to "broadcasts/campaign/{id}",
        "blink://promo/{id}" to "broadcasts/promo/{id}",
        "blink://wallet" to "wallet",
        "blink://profile/{id}" to "profile/{id}",
        "unknown" to "home",
    )

    fun route(raw: String?): String = BlinkBroadcastRoutes.resolveDeepLink(raw)

    fun handle(raw: String?, navigate: (String) -> Unit) {
        navigate(route(raw))
    }
}

@Composable
fun BlinkBroadcastNavigationHost(
    route: String?,
    viewModel: BlinkBroadcastViewModel,
    isAdmin: Boolean,
    onNavigate: (String?) -> Unit,
    onExternalRoute: (String) -> Unit = {},
) {
    if (route == null) return
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { onNavigate(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val id = route.substringAfterLast('/').takeIf { route.contains('/') }
                ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrDefault(it) }
            when {
                route == BlinkBroadcastRoutes.INBOX -> BlinkAnnouncementInboxScreen(
                    state, viewModel, { onNavigate(null) },
                    { deep -> onNavigate(BlinkDeepLinkHandler.route(deep)) },
                    onSettings = { onNavigate(BlinkBroadcastRoutes.SETTINGS) },
                )
                route == BlinkBroadcastRoutes.SETTINGS -> BlinkNotificationSettingsScreen(state, viewModel) { onNavigate(null) }
                route == BlinkBroadcastRoutes.ADMIN && isAdmin -> BlinkBroadcastAdminListScreen(
                    viewModel = viewModel, onCreate = { viewModel.resetDraft(); onNavigate(BlinkBroadcastRoutes.CREATE) },
                    onEdit = { viewModel.saveDraft(it); onNavigate(BlinkBroadcastRoutes.CREATE) },
                    onAnalytics = { onNavigate(BlinkBroadcastRoutes.analytics(it)) },
                    onAudit = { onNavigate(BlinkBroadcastRoutes.AUDIT) },
                )
                route == BlinkBroadcastRoutes.ADMIN && !isAdmin -> BlinkBroadcastEmptyOrError(
                    stringResource(R.string.broadcast_admin), stringResource(R.string.broadcast_admin_only))
                route == BlinkBroadcastRoutes.CREATE && isAdmin -> BlinkCreateBroadcastScreen(
                    viewModel = viewModel, onBack = { onNavigate(BlinkBroadcastRoutes.ADMIN) },
                    onSaved = { onNavigate(BlinkBroadcastRoutes.ADMIN) },
                )
                route.startsWith("broadcasts/analytics/") && isAdmin -> BlinkBroadcastAnalyticsScreen(
                    viewModel = viewModel, broadcastId = id.orEmpty(), onBack = { onNavigate(BlinkBroadcastRoutes.ADMIN) },
                )
                route == BlinkBroadcastRoutes.AUDIT && isAdmin -> BlinkBroadcastAuditLogScreen(
                    viewModel = viewModel, onBack = { onNavigate(BlinkBroadcastRoutes.ADMIN) },
                )
                route.startsWith("broadcasts/campaign/") -> BlinkCampaignDetailScreen(
                    id.orEmpty(), state, viewModel, { onNavigate(null) },
                    onShare = { campaign ->
                        val text = campaign.deepLink
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.broadcast_campaign_share_title))
                        }
                        context.startActivity(Intent.createChooser(send,
                            context.getString(R.string.broadcast_campaign_share_title)))
                    },
                )
                route.startsWith("broadcasts/promo/") -> {
                    val promo = (state as? BlinkBroadcastUiState.Success)?.broadcasts
                        ?.firstOrNull { it.id == id && it.type == BroadcastType.PROMO }
                    if (promo == null) BlinkBroadcastEmptyOrError(
                        stringResource(R.string.broadcast_error), stringResource(R.string.broadcast_open_link_error),
                        stringResource(R.string.broadcast_retry), viewModel::refresh,
                    ) else Column(Modifier.fillMaxSize().padding(20.dp)) {
                        IconButton(onClick = { onNavigate(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.broadcast_back))
                        }
                        BlinkPromoCard(promo, viewModel)
                    }
                }
                else -> {
                    LaunchedEffect(route) { onNavigate(null); onExternalRoute(route) }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.shared.BlinkDailyMission
import com.blinkng.shared.BlinkEconomyPolicy
import com.blinkng.shared.BlinkEarningHubPolicy
import com.example.data.supabase.BlinkEconomyService
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat

internal data class WalletLedgerRow(
    val id: String,
    val description: String,
    val amount: Long,
    val createdAt: String
)

internal fun decodeWalletLedger(payload: JSONObject): List<WalletLedgerRow> {
    val array = payload.optJSONArray("transactions") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val id = row.optString("id").trim()
            if (id.isBlank() || id == "null") continue
            val name = row.optString("item_name").trim()
            add(WalletLedgerRow(
                id = id,
                description = name.takeIf { it.isNotBlank() && it != "null" }
                    ?: BlinkEarningHubPolicy.readableTransaction(row.optString("kind")),
                amount = row.optLong("amount", 0L),
                createdAt = row.optString("created_at").take(16).replace('T', ' ')
            ))
        }
    }
}

private val WalletBlack = Color(0xFF141619)
private val WalletGreen = Color(0xFF13B889)
private val WalletMuted = Color(0xFF90959D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkWalletScreen(
    accountId: String,
    lastKnownBalance: Long,
    dailyStreak: Int,
    adsToday: Int,
    policy: BlinkEconomyPolicy,
    missions: List<BlinkDailyMission>,
    loadingMissions: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClaimMission: (String) -> Unit,
    onWatchOptionalAd: () -> Unit,
    onOpenVerification: () -> Unit,
    onOpenStore: () -> Unit,
    isVerified: Boolean = false,
    onVerifyCoins: () -> Unit = {},
    onBuyPack: (com.blinkng.shared.BlinkCoinPack) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val service = remember(accountId) { BlinkEconomyService() }
    var balance by remember(accountId) { mutableStateOf<Long?>(null) }
    var history by remember(accountId) { mutableStateOf<List<WalletLedgerRow>>(emptyList()) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var tab by remember(accountId) { mutableIntStateOf(0) }
    var historyFilter by remember(accountId) { mutableIntStateOf(0) }
    val foreground = MaterialTheme.colorScheme.onSurface
    val background = if (isDark) Color(0xFF0D0F12) else Color(0xFFF9FAFC)
    val surface = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
    val amountFormatter = remember { NumberFormat.getIntegerInstance() }
    val compact = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.5f

    suspend fun refresh() {
        if (accountId.isBlank()) return
        loading = true
        error = null
        service.state().onSuccess { response ->
            balance = if (response.has("balance") && !response.isNull("balance")) {
                response.optLong("balance").coerceAtLeast(0L)
            } else null
            history = decodeWalletLedger(response)
            if (balance == null) error = "Your balance could not be confirmed. Please retry."
        }.onFailure {
            balance = null
            history = emptyList()
            error = "Couldn't reach your BLINK wallet. Check your internet and retry."
        }
        loading = false
    }

    // Reload the authoritative ledger when an ad, daily mission, or payment updates the account.
    LaunchedEffect(accountId, adsToday, lastKnownBalance, missions.map { it.key to it.claimed }) {
        if (accountId.isNotBlank()) {
            onRefresh()
            refresh()
        } else {
            loading = false
            error = "Sign in to access your personal wallet."
        }
    }

    BlinkWalletPremiumContent(
        accountId = accountId,
        balance = balance,
        history = history,
        loading = loading,
        error = error,
        adsToday = adsToday,
        isVerified = isVerified,
        policy = policy,
        missions = missions,
        loadingMissions = loadingMissions,
        onBack = onBack,
        onRetry = {
            onRefresh()
            scope.launch { refresh() }
        },
        onWatchAd = onWatchOptionalAd,
        onClaimMission = onClaimMission,
        onVerifyCoins = onVerifyCoins,
        onOpenVerification = onOpenVerification,
        onBuyPack = onBuyPack
    )
}

@Composable
private fun WalletSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WalletPanel(
    color: Color, border: Color, content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = color, border = BorderStroke(1.dp, border), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun WalletInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
    value: String, foreground: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = WalletGreen, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), color = foreground,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = foreground, maxLines = 2)
    }
}

@Composable
private fun WalletEmpty(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WalletTransaction(tx: WalletLedgerRow, surface: Color, border: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = surface, border = BorderStroke(1.dp, border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (tx.amount >= 0) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                null, tint = if (tx.amount >= 0) WalletGreen else WalletMuted
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.description, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(tx.createdAt.takeIf { it.isNotBlank() }?.plus(" UTC") ?: "Date unavailable",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text(BlinkEarningHubPolicy.coinChangeLabel(tx.amount),
                fontWeight = FontWeight.Bold,
                color = if (tx.amount > 0) WalletGreen else MaterialTheme.colorScheme.onSurface)
        }
    }
}

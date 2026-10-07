package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blinkng.shared.BlinkBoostAudienceScope
import com.blinkng.shared.BlinkBoostObjective
import com.blinkng.shared.BlinkBoostTargetType
import com.example.data.models.FeedPost
import com.example.data.models.MarketItem
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.data.models.kNigerianUniversitiesList
import com.example.data.network.NetworkMonitor
import com.example.data.repository.FollowStateStore
import com.example.data.supabase.BlinkEconomyService
import com.example.data.supabase.BlinkWalletStore
import com.example.ui.components.PostCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private enum class BoostGrowthColumn { BOOST, EARN }

private data class BoostDraftSnapshot(
    val targetType: BlinkBoostTargetType = BlinkBoostTargetType.POST,
    val targetId: String = "",
    val boostPower: Int = 25,
    val objective: BlinkBoostObjective = BlinkBoostObjective.VIEWS,
    val audience: BlinkBoostAudienceScope = BlinkBoostAudienceScope.MY_UNIVERSITY,
    val durationDays: Int = 3,
    val selectedUniversity: String = "",
)

private object BoostDraftStore {
    var value = BoostDraftSnapshot()
}

private data class MissionItem(
    val campaignId: String,
    val targetType: String,
    val targetId: String,
    val ownerId: String,
    val ownerUsername: String,
    val ownerName: String,
    val actions: List<MissionAction>,
    val post: FeedPost? = null,
    val listing: MarketItem? = null,
)

private data class MissionAction(
    val key: String,
    val label: String,
    val points: Int,
)

@Composable
fun BlinkBoostGrowthRoute(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    marketItems: List<MarketItem>,
    myProfile: UserProfile,
    isDark: Boolean,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onListingClick: (MarketItem) -> Unit,
    onGetCoins: () -> Unit = {},
    onCreateContent: () -> Unit = {},
    onClose: () -> Unit,
) {
    var selectedColumn by remember { mutableStateOf(BoostGrowthColumn.BOOST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Growth", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Promote content or earn Rank Points from promoted content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Boost")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(16.dp))
                .padding(4.dp),
        ) {
            GrowthColumnTab(
                selected = selectedColumn == BoostGrowthColumn.BOOST,
                icon = Icons.Default.Campaign,
                label = "Boost",
                onClick = { selectedColumn = BoostGrowthColumn.BOOST },
                modifier = Modifier.weight(1f),
            )
            GrowthColumnTab(
                selected = selectedColumn == BoostGrowthColumn.EARN,
                icon = Icons.Default.EmojiEvents,
                label = "Earn Rank Points",
                onClick = { selectedColumn = BoostGrowthColumn.EARN },
                modifier = Modifier.weight(1f),
            )
        }

        when (selectedColumn) {
            BoostGrowthColumn.BOOST -> BoostCampaignColumn(
                posts = posts,
                reels = reels,
                marketItems = marketItems,
                myProfile = myProfile,
                isDark = isDark,
                onGetCoins = onGetCoins,
                onCreateContent = onCreateContent,
            )
            BoostGrowthColumn.EARN -> EarnRankPointsColumn(
                isDark = isDark,
                onLikePost = onLikePost,
                onCommentPost = onCommentPost,
                onBookmarkPost = onBookmarkPost,
                onProfileClick = onProfileClick,
                onListingClick = onListingClick,
            )
        }
    }
}

@Composable
private fun GrowthColumnTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(7.dp))
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostCampaignColumn(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    marketItems: List<MarketItem>,
    myProfile: UserProfile,
    isDark: Boolean,
    onGetCoins: () -> Unit,
    onCreateContent: () -> Unit,
) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    val context = LocalContext.current
    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = networkMonitor.isCurrentlyOnline())
    val liveWalletBalance by BlinkWalletStore.balance.collectAsState()
    val draft = remember { BoostDraftStore.value }

    val me = myProfile.username.trim().removePrefix("@")
    val ownPosts = remember(posts, me) {
        posts.filter {
            !it.isReel && it.authorUsername.trim().removePrefix("@").equals(me, ignoreCase = true)
        }.distinctBy { it.id }
    }
    val ownReels = remember(reels, posts, me) {
        (reels + posts).filter {
            it.isReel && it.authorUsername.trim().removePrefix("@").equals(me, ignoreCase = true)
        }.distinctBy { it.id }
    }
    val ownListings = remember(marketItems, me) {
        marketItems.filter { it.sellerUsername.trim().removePrefix("@").equals(me, ignoreCase = true) }
    }

    var targetType by remember { mutableStateOf(draft.targetType) }
    var targetId by remember { mutableStateOf(draft.targetId) }
    var boostPower by remember { mutableIntStateOf(draft.boostPower) }
    var objective by remember { mutableStateOf(draft.objective) }
    var audience by remember { mutableStateOf(draft.audience) }
    var durationDays by remember { mutableIntStateOf(draft.durationDays) }
    var selectedUniversity by remember { mutableStateOf(draft.selectedUniversity) }
    var universityMenuOpen by remember { mutableStateOf(false) }
    var lastTargetType by remember { mutableStateOf(targetType) }

    var state by remember { mutableStateOf<JSONObject?>(null) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var quoteLoading by remember { mutableStateOf(false) }
    var stateLoading by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var budgetWorking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmStart by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var pendingRequestId by remember { mutableStateOf<String?>(null) }
    var pendingRequestFingerprint by remember { mutableStateOf<String?>(null) }
    var historyFilter by remember { mutableStateOf("ALL") }
    var historyQuery by remember { mutableStateOf("") }

    suspend fun reloadState() {
        stateLoading = true
        service.boostGrowthState()
            .onSuccess {
                state = it
                it.takeIf { payload -> payload.has("balance") }?.optLong("balance")?.let(BlinkWalletStore::publish)
                error = null
            }
            .onFailure { error = boostUserMessage(it, "Boost is temporarily unavailable. Please try again.") }
        stateLoading = false
    }

    LaunchedEffect(refreshNonce) { reloadState() }

    LaunchedEffect(targetType) {
        if (targetType != lastTargetType) {
            targetId = when (targetType) {
                BlinkBoostTargetType.PROFILE -> myProfile.id
                else -> ""
            }
            objective = defaultObjectiveFor(targetType)
            pendingRequestId = null
            quote = null
            error = null
        }
        lastTargetType = targetType
    }

    LaunchedEffect(targetType, targetId, boostPower, objective, audience, durationDays, selectedUniversity) {
        BoostDraftStore.value = BoostDraftSnapshot(
            targetType = targetType,
            targetId = targetId,
            boostPower = boostPower,
            objective = objective,
            audience = audience,
            durationDays = durationDays,
            selectedUniversity = selectedUniversity,
        )
    }

    val targetUniversity = when (audience) {
        BlinkBoostAudienceScope.SELECTED_UNIVERSITY -> selectedUniversity.takeIf(String::isNotBlank)
        else -> null
    }
    val targetReady = targetId.isNotBlank() &&
        (audience != BlinkBoostAudienceScope.SELECTED_UNIVERSITY || selectedUniversity.isNotBlank())

    LaunchedEffect(targetType, targetId, boostPower, objective, audience, durationDays, selectedUniversity) {
        quote = null
        if (!targetReady || !isOnline) return@LaunchedEffect
        delay(260)
        quoteLoading = true
        service.quoteBoostCampaign(
            targetType = targetType.name,
            targetId = targetId,
            boostPower = boostPower,
            objective = objective.name,
            audienceScope = audience.name,
            durationDays = durationDays,
            targetUniversity = targetUniversity,
        ).onSuccess {
            quote = it
            it.takeIf { payload -> payload.has("balance") }?.optLong("balance")?.let(BlinkWalletStore::publish)
            error = null
        }.onFailure {
            error = boostUserMessage(it, "Unable to calculate this boost right now. Please try again.")
        }
        quoteLoading = false
    }

    fun applyBudgetPreset(budget: Long) {
        if (!targetReady || !isOnline || budgetWorking) return
        scope.launch {
            budgetWorking = true
            service.recommendBoostPower(
                targetType = targetType.name,
                targetId = targetId,
                budget = budget,
                objective = objective.name,
                audienceScope = audience.name,
                durationDays = durationDays,
                targetUniversity = targetUniversity,
            ).onSuccess { recommendation ->
                val recommended = recommendation.optInt("recommended_power", 0)
                if (recommended > 0) {
                    boostPower = recommended
                    val cost = recommendation.optLong("coin_cost", 0L)
                    message = "Budget preset applied: $recommended% power" +
                        if (cost > 0) " • about ${formatter.format(cost)} coins" else ""
                    error = null
                } else {
                    error = "That budget is below the minimum for this campaign setup."
                }
            }.onFailure {
                error = boostUserMessage(it, "Unable to apply that budget right now.")
            }
            budgetWorking = false
        }
    }

    val stateBalance = state?.takeIf { it.has("balance") }?.optLong("balance")
    val balance = liveWalletBalance ?: stateBalance
    val quoteCost = quote?.optLong("coin_cost", 0L) ?: 0L
    val campaigns = state?.optJSONArray("campaigns").objectList()
    val activeCampaigns = campaigns.filter { it.optString("status") == "ACTIVE" }
    val historyCampaigns = campaigns.filter { it.optString("status") != "ACTIVE" }
        .filter { historyFilter == "ALL" || it.optString("status").equals(historyFilter, ignoreCase = true) }
        .filter {
            val q = historyQuery.trim()
            q.isBlank() ||
                it.optString("target_type").contains(q, ignoreCase = true) ||
                it.optString("objective").contains(q, ignoreCase = true) ||
                it.optString("status").contains(q, ignoreCase = true)
        }
    val receipts = state?.optJSONArray("receipts").objectList()
    val analytics = state?.optJSONObject("analytics")

    PullToRefreshBox(
        isRefreshing = stateLoading,
        onRefresh = { if (isOnline) refreshNonce++ },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BalanceCard(balance = balance, formatter = formatter) }

        if (stateLoading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        analytics?.let { value ->
            item { BoostAnalyticsCard(value, formatter) }
        }

        if (!isOnline) {
            item {
                StatusCard(
                    "You're offline. Campaign history remains visible, but spending is disabled until you reconnect.",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        error?.let { value ->
            item {
                StatusCard(value, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
            }
        }
        message?.let { value ->
            item {
                StatusCard(value, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
            }
        }

        if (error != null && state == null) {
            item {
                OutlinedButton(
                    onClick = { refreshNonce++ },
                    enabled = !stateLoading && isOnline,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Retry Boost")
                }
            }
        }

        item {
            SectionTitle(
                "1. What do you want to boost?",
                "Choose your content. Listing is the name used for Market posts.",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BlinkBoostTargetType.values().toList()) { type ->
                    FilterChip(
                        selected = targetType == type,
                        onClick = { targetType = type },
                        label = { Text(targetTypeLabel(type)) },
                        leadingIcon = { Icon(targetTypeIcon(type), null, modifier = Modifier.size(17.dp)) },
                    )
                }
            }
        }

        when (targetType) {
            BlinkBoostTargetType.POST -> {
                if (ownPosts.isEmpty()) {
                    item { EmptyTargetCard("You do not have a boostable post yet.", "Create a post", onCreateContent) }
                } else {
                    items(ownPosts.take(12), key = { "boost-post-" + it.id }) { post ->
                        SelectablePostTarget(
                            post = post,
                            selected = targetId == post.id,
                            isDark = isDark,
                            label = "Boost this post",
                            onSelect = { targetId = post.id },
                        )
                    }
                }
            }
            BlinkBoostTargetType.REEL -> {
                if (ownReels.isEmpty()) {
                    item { EmptyTargetCard("You do not have a boostable Reel yet.", "Create content", onCreateContent) }
                } else {
                    items(ownReels.take(12), key = { "boost-reel-" + it.id }) { reel ->
                        SelectablePostTarget(
                            post = reel,
                            selected = targetId == reel.id,
                            isDark = isDark,
                            label = "Boost this Reel",
                            onSelect = { targetId = reel.id },
                        )
                    }
                }
            }
            BlinkBoostTargetType.PROFILE -> {
                item {
                    ProfileTargetCard(
                        profile = myProfile,
                        selected = targetId == myProfile.id && myProfile.id.isNotBlank(),
                        onSelect = { targetId = myProfile.id },
                    )
                }
            }
            BlinkBoostTargetType.LISTING -> {
                if (ownListings.isEmpty()) {
                    item { EmptyTargetCard("You do not have an active Listing to boost.") }
                } else {
                    items(ownListings.take(12), key = { "boost-listing-" + it.id }) { listing ->
                        SelectableListingTarget(
                            listing = listing,
                            selected = targetId == listing.id,
                            isDark = isDark,
                            onSelect = { targetId = listing.id },
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("2. Audience", "Choose your campus or expand across BLINK.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BlinkBoostAudienceScope.values().toList()) { item ->
                    FilterChip(
                        selected = audience == item,
                        onClick = { audience = item },
                        label = { Text(audienceLabel(item)) },
                        leadingIcon = {
                            Icon(
                                when (item) {
                                    BlinkBoostAudienceScope.MY_UNIVERSITY -> Icons.Default.School
                                    BlinkBoostAudienceScope.SELECTED_UNIVERSITY -> Icons.Default.Groups
                                    BlinkBoostAudienceScope.ALL_CAMPUSES -> Icons.Default.Public
                                },
                                null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                }
            }
            if (audience == BlinkBoostAudienceScope.SELECTED_UNIVERSITY) {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { universityMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedUniversity.ifBlank { "Choose university" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = universityMenuOpen,
                        onDismissRequest = { universityMenuOpen = false },
                    ) {
                        kNigerianUniversitiesList.forEach { university ->
                            DropdownMenuItem(
                                text = { Text(university) },
                                onClick = {
                                    selectedUniversity = university
                                    universityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                "3. Goal",
                "Choose views, likes, comments, saves, any engagement, visits or followers. Results are estimates.",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(objectivesFor(targetType)) { item ->
                    FilterChip(
                        selected = objective == item,
                        onClick = { objective = item },
                        label = { Text(objectiveLabel(item)) },
                    )
                }
            }
        }

        item {
            SectionTitle("4. Boost power", "Higher power increases delivery speed and estimated reach.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = boostPower.toFloat(),
                    onValueChange = {
                        boostPower = it.toInt().coerceIn(1, 100)
                        pendingRequestId = null
                    },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$boostPower%",
                    modifier = Modifier.padding(start = 12.dp),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "Quick max budget",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(100L, 250L, 500L, 1_000L)) { budget ->
                    AssistChip(
                        onClick = { applyBudgetPreset(budget) },
                        enabled = targetReady && isOnline && !budgetWorking,
                        label = { Text(formatter.format(budget) + " coins") },
                    )
                }
            }
        }

        item {
            SectionTitle("5. Duration", "Longer campaigns receive the built-in duration discount.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(1, 3, 7, 14, 30)) { days ->
                    FilterChip(
                        selected = durationDays == days,
                        onClick = { durationDays = days },
                        label = { Text(if (days == 1) "1 day" else days.toString() + " days") },
                    )
                }
            }
        }

        item {
            CampaignQuoteCard(
                quote = quote,
                quoteLoading = quoteLoading,
                balance = balance,
                formatter = formatter,
            )
        }

        item {
            Button(
                onClick = { confirmStart = true },
                enabled = !working && !quoteLoading && isOnline && quote != null && quoteCost > 0 && (balance ?: -1L) >= quoteCost,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    when {
                        !isOnline -> "Connect to start Boost"
                        quote == null -> "Choose a boost target"
                        balance == null -> "Balance unavailable"
                        balance < quoteCost -> "Not enough Blink Coins"
                        else -> "Start Boost • " + formatter.format(quoteCost) + " coins"
                    },
                )
            }
        }

        if (quote != null && balance != null && balance < quoteCost) {
            item {
                OutlinedButton(
                    onClick = onGetCoins,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Get Blink Coins")
                }
            }
        }

        if (activeCampaigns.isNotEmpty()) {
            item { HorizontalDivider() }
            item { SectionTitle("Active boosts", "Campaigns currently delivering as Promoted content.") }
            items(activeCampaigns, key = { "campaign-" + it.optString("id") }) { campaign ->
                ActiveCampaignCard(
                    campaign = campaign,
                    formatter = formatter,
                    working = working,
                    onCancel = {
                        val id = campaign.optString("id")
                        if (id.isNotBlank()) {
                            scope.launch {
                                working = true
                                service.cancelBoostCampaign(id)
                                    .onSuccess {
                                        val refund = it.optLong("refunded", 0L)
                                        it.takeIf { payload -> payload.has("balance") }?.optLong("balance")?.let(BlinkWalletStore::publish)
                                        message = if (refund > 0) {
                                            "Boost cancelled. " + formatter.format(refund) + " unused coins were returned."
                                        } else {
                                            "Boost cancelled."
                                        }
                                        reloadState()
                                    }
                                    .onFailure { error = boostUserMessage(it, "Unable to cancel this boost right now.") }
                                working = false
                            }
                        }
                    },
                )
            }
        }

        if (campaigns.isNotEmpty()) {
            item { HorizontalDivider() }
            item { SectionTitle("Campaign history", "Search completed and cancelled Boosts, spend, reach and refunds.") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL", "ENDED", "CANCELLED")) { status ->
                        FilterChip(
                            selected = historyFilter == status,
                            onClick = { historyFilter = status },
                            label = {
                                Text(
                                    when (status) {
                                        "ENDED" -> "Completed"
                                        "CANCELLED" -> "Cancelled"
                                        else -> "All"
                                    }
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it.take(60) },
                    label = { Text("Search history") },
                    placeholder = { Text("Post, Reel, objective or status") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (historyCampaigns.isEmpty()) {
                item { EmptyTargetCard("No campaigns match this history filter.") }
            } else {
                items(historyCampaigns, key = { "history-" + it.optString("id") }) { campaign ->
                    CampaignHistoryCard(campaign, formatter)
                }
            }
        }

        if (receipts.isNotEmpty()) {
            item { HorizontalDivider() }
            item { SectionTitle("Growth receipts", "Every Boost reserve/refund and Drop reserve/reward/refund remains auditable.") }
            items(receipts.take(12), key = { "receipt-" + it.optString("id") }) { receipt ->
                GrowthReceiptCard(receipt, formatter)
            }
        }
    }
    }

    if (confirmStart) {
        val low = quote?.optLong("estimated_reach_low", 0L) ?: 0L
        val high = quote?.optLong("estimated_reach_high", 0L) ?: 0L
        AlertDialog(
            onDismissRequest = { if (!working) confirmStart = false },
            title = { Text("Start this Boost?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(targetTypeLabel(targetType) + " • " + boostPower + "% power")
                    Text(objectiveLabel(objective) + " • " + audienceLabel(audience))
                    Text(durationDays.toString() + " day" + if (durationDays == 1) "" else "s")
                    Text("Estimated reach: " + formatter.format(low) + "–" + formatter.format(high))
                    Text("Reserved budget: " + formatter.format(quoteCost) + " Blink Coins", fontWeight = FontWeight.Black)
                    balance?.let {
                        Text("Balance after reserve: " + formatter.format(it - quoteCost) + " Blink Coins")
                    }
                    Text(
                        "Reach and engagement are estimates, not guaranteed results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !working && isOnline && quoteCost > 0 && (balance ?: -1L) >= quoteCost,
                    onClick = {
                        scope.launch {
                            working = true
                            val fingerprint = com.blinkng.shared.BlinkBoostCampaignRequest(
                                targetType.name, targetId, boostPower, objective.name,
                                audience.name, durationDays, targetUniversity
                            ).fingerprint()
                            if (pendingRequestId == null || pendingRequestFingerprint != fingerprint) {
                                pendingRequestId = UUID.randomUUID().toString()
                                pendingRequestFingerprint = fingerprint
                            }
                            service.createBoostCampaign(
                                targetType = targetType.name,
                                targetId = targetId,
                                boostPower = boostPower,
                                objective = objective.name,
                                audienceScope = audience.name,
                                durationDays = durationDays,
                                targetUniversity = targetUniversity,
                                requestId = pendingRequestId.orEmpty(),
                            ).onSuccess {
                                confirmStart = false
                                pendingRequestId = null
                                pendingRequestFingerprint = null
                                val charged = it.optLong("charged", quoteCost)
                                it.takeIf { payload -> payload.has("balance") }?.optLong("balance")?.let(BlinkWalletStore::publish)
                                message = "Boost started. " + formatter.format(charged) + " coins were reserved for campaign delivery."
                                reloadState()
                            }.onFailure {
                                error = boostUserMessage(it, "Unable to start this boost right now. Please try again.")
                            }
                            working = false
                        }
                    },
                ) { Text("Confirm Boost") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }, enabled = !working) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EarnRankPointsColumn(
    isDark: Boolean,
    onLikePost: (String) -> Unit,
    onCommentPost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onListingClick: (MarketItem) -> Unit,
) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    val followingIds by FollowStateStore.followingIds.collectAsState()

    var payload by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var commentMission by remember { mutableStateOf<MissionItem?>(null) }
    var commentText by remember { mutableStateOf("") }
    var actionBusy by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        service.boostMissions()
            .onSuccess { payload = it; error = null }
            .onFailure { error = it.message ?: "Unable to load Rank Point opportunities." }
        loading = false
    }

    fun completeMission(
        mission: MissionItem,
        action: String,
        comment: String? = null,
        afterSuccess: () -> Unit = {},
    ) {
        if (actionBusy != null) return
        actionBusy = mission.campaignId + ":" + action
        scope.launch {
            service.completeBoostMissionAction(
                campaignId = mission.campaignId,
                action = action,
                commentText = comment,
            ).onSuccess {
                afterSuccess()
                if (action == "follow") FollowStateStore.refresh()
                error = null
                refreshNonce++
            }.onFailure {
                error = it.message ?: "This Rank Point action could not be completed."
            }
            actionBusy = null
        }
    }

    LaunchedEffect(refreshNonce) {
        if (refreshNonce == 0) FollowStateStore.refresh()
        reload()
    }

    val missions = payload?.optJSONArray("items").objectList().mapNotNull(::parseMissionItem)
    val offered = payload?.optInt("offered_points", 0) ?: 0
    val cap = payload?.optInt("suggested_points_cap", 20) ?: 20
    val earnedToday = payload?.optInt("earned_from_missions_today", 0) ?: 0
    val remainingToday = payload?.optInt(
        "remaining_mission_points_today",
        (cap - earnedToday).coerceAtLeast(0),
    ) ?: (cap - earnedToday).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Earn Rank Points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { refreshNonce++ }, enabled = actionBusy == null) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh opportunities")
                        }
                    }
                    Text(
                        "Complete genuine actions on promoted content. Each content/action pair can reward you only once, even if you undo and repeat it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Today: $earnedToday / $cap RP · Available now: up to $offered RP",
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = if (cap <= 0) 0f else (earnedToday.toFloat() / cap).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (remainingToday <= 0) {
                        Text(
                            "Daily Boost Mission limit reached. Normal BLINK activity can still follow its regular Rank Point rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        error?.let { value ->
            item {
                StatusCard(value, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
            }
        }

        if (!loading && missions.isEmpty() && error == null) {
            item {
                EmptyTargetCard(
                    if (remainingToday <= 0) {
                        "You reached today's Boost Mission Rank Point limit."
                    } else {
                        "No new promoted Rank Point opportunities are available right now. Completed actions are never recycled for extra points."
                    },
                )
            }
        }

        items(missions, key = { "mission-" + it.campaignId + "-" + it.targetId }) { mission ->
            LaunchedEffect(mission.campaignId) {
                service.recordBoostDelivery(mission.campaignId, "IMPRESSION", "MISSIONS")
            }

            var localPost by remember(mission.campaignId) {
                mutableStateOf(mission.post?.copy(isSponsored = true, adLabel = "Promoted"))
            }
            val available = mission.actions.map { it.key }.toSet()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PromotedHeader(mission)

                localPost?.let { post ->
                    PostCard(
                        post = post,
                        isDark = isDark,
                        onLike = {
                            if ("like" in available) {
                                completeMission(mission, "like") {
                                    localPost = post.copy(isLiked = true, likes = post.likes + if (post.isLiked) 0 else 1)
                                }
                            }
                        },
                        onComment = {
                            if ("comment" in available) {
                                commentText = ""
                                commentMission = mission
                            }
                        },
                        onBookmark = {
                            if ("save" in available) {
                                completeMission(mission, "save") {
                                    localPost = post.copy(isBookmarked = true)
                                }
                            }
                        },
                        onRepost = {},
                        onShare = {},
                        onOptionsClick = {},
                        onProfileClick = onProfileClick,
                        authorName = mission.ownerName,
                        authorUsername = mission.ownerUsername,
                        authorProfileId = mission.ownerId,
                        isFollowingAuthor = mission.ownerId in followingIds,
                        onFollowAuthor = {
                            if ("follow" in available) completeMission(mission, "follow")
                        },
                        onUnfollowAuthor = {},
                        trackExposure = false,
                    )
                }

                mission.listing?.let { listing ->
                    ProductCard(
                        item = listing,
                        onClick = {
                            if ("listing_open" in available) {
                                completeMission(mission, "listing_open") {
                                    onListingClick(listing)
                                }
                            } else {
                                onListingClick(listing)
                            }
                        },
                        isDark = isDark,
                    )
                }

                if (mission.post == null && mission.listing == null) {
                    PromotedProfileCard(
                        mission = mission,
                        isFollowing = mission.ownerId in followingIds,
                        onOpenProfile = {
                            scope.launch {
                                service.recordBoostDelivery(mission.campaignId, "PROFILE_OPEN", "MISSIONS")
                            }
                            onProfileClick(mission.ownerUsername)
                        },
                        onFollow = {
                            if ("follow" in available) completeMission(mission, "follow")
                        },
                    )
                }

                MissionActionBar(
                    actions = mission.actions,
                    onAction = { action ->
                        when (action.key) {
                            "view" -> completeMission(mission, "view")
                            "like" -> completeMission(mission, "like") {
                                localPost = localPost?.let { it.copy(isLiked = true, likes = it.likes + if (it.isLiked) 0 else 1) }
                            }
                            "comment" -> {
                                commentText = ""
                                commentMission = mission
                            }
                            "save" -> completeMission(mission, "save") {
                                localPost = localPost?.copy(isBookmarked = true)
                            }
                            "follow" -> completeMission(mission, "follow")
                            "listing_open" -> completeMission(mission, "listing_open") {
                                mission.listing?.let(onListingClick)
                            }
                        }
                    },
                )

                if (actionBusy?.startsWith(mission.campaignId + ":") == true) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    commentMission?.let { mission ->
        AlertDialog(
            onDismissRequest = {
                if (actionBusy == null) {
                    commentMission = null
                    commentText = ""
                }
            },
            title = { Text("Comment for +2 RP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Write a genuine comment. A second comment on the same promoted post will not earn another Rank Point reward.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it.take(2000) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("Your comment") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = commentText.trim()
                        if (clean.isBlank()) return@Button
                        completeMission(mission, "comment", clean) {
                            commentMission = null
                            commentText = ""
                        }
                    },
                    enabled = commentText.trim().isNotEmpty() && actionBusy == null,
                ) {
                    Text("Post comment")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        commentMission = null
                        commentText = ""
                    },
                    enabled = actionBusy == null,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MissionActionBar(
    actions: List<MissionAction>,
    onAction: (MissionAction) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(actions, key = { it.key }) { action ->
            val icon = when (action.key) {
                "view" -> Icons.Default.Visibility
                "like" -> Icons.Default.FavoriteBorder
                "comment" -> Icons.Default.ChatBubbleOutline
                "save" -> Icons.Default.Bookmark
                "follow" -> Icons.Default.PersonAdd
                "listing_open" -> Icons.Default.Storefront
                else -> Icons.Default.EmojiEvents
            }
            AssistChip(
                onClick = { if (action.key != "view") onAction(action) },
                label = { Text(action.label + " +" + action.points + " RP") },
                leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun PromotedHeader(mission: MissionItem) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text("Promoted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            "Up to +" + mission.actions.sumOf { it.points } + " RP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PromotedProfileCard(
    mission: MissionItem,
    isFollowing: Boolean,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mission.ownerName.ifBlank { mission.ownerUsername }, fontWeight = FontWeight.Black)
                Text("@" + mission.ownerUsername, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenProfile) { Text("View") }
            Spacer(Modifier.size(6.dp))
            Button(onClick = onFollow, enabled = !isFollowing) {
                Text(if (isFollowing) "Following" else "Follow")
            }
        }
    }
}

@Composable
private fun SelectablePostTarget(
    post: FeedPost,
    selected: Boolean,
    isDark: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            PostCard(
                post = post,
                isDark = isDark,
                onLike = {},
                onComment = {},
                onBookmark = {},
                onRepost = {},
                onShare = {},
                onOptionsClick = {},
                onProfileClick = {},
                trackExposure = false,
            )
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (selected) "Selected" else label)
            }
        }
    }
}

@Composable
private fun SelectableListingTarget(
    listing: MarketItem,
    selected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(8.dp)) {
            ProductCard(item = listing, onClick = onSelect, isDark = isDark)
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (selected) "Selected" else "Boost this Listing")
            }
        }
    }
}

@Composable
private fun ProfileTargetCard(
    profile: UserProfile,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(42.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Black)
                Text("@" + profile.username.trim().removePrefix("@"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                profile.university.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(onClick = onSelect, enabled = profile.id.isNotBlank()) {
                Text(if (selected) "Selected" else "Select")
            }
        }
    }
}

@Composable
private fun CampaignQuoteCard(
    quote: JSONObject?,
    quoteLoading: Boolean,
    balance: Long?,
    formatter: NumberFormat,
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("Campaign estimate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            when {
                quoteLoading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Calculating from server pricing…", style = MaterialTheme.typography.bodySmall)
                }
                quote == null -> Text(
                    "Select content and audience to see the server-calculated price.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val cost = quote.optLong("coin_cost", 0L)
                    val low = quote.optLong("estimated_reach_low", 0L)
                    val high = quote.optLong("estimated_reach_high", 0L)
                    val audienceCount = quote.optLong("audience_user_count", 0L)
                    val dailyLow = quote.optLong("estimated_daily_reach_low", 0L)
                    val dailyHigh = quote.optLong("estimated_daily_reach_high", 0L)
                    val remainingAfter = quote.optLong(
                        "remaining_balance_after_reserve",
                        balance?.minus(cost) ?: Long.MIN_VALUE,
                    )
                    Text(
                        formatter.format(cost) + " Blink Coins",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text("Estimated reach: " + formatter.format(low) + "–" + formatter.format(high))
                    if (dailyHigh > 0) {
                        Text("Expected daily delivery: " + formatter.format(dailyLow) + "–" + formatter.format(dailyHigh))
                    }
                    if (audienceCount > 0) {
                        Text("Audience preview: " + formatter.format(audienceCount) + " eligible BLINK users")
                    }
                    Text(
                        balance?.let { "Balance: " + formatter.format(it) + " coins" }
                            ?: "Balance unavailable",
                        color = if (balance == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (remainingAfter != Long.MIN_VALUE) {
                        Text(
                            "After reserve: " + formatter.format(remainingAfter.coerceAtLeast(0L)) + " coins",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Estimates are not guarantees. Paid delivery is marked Promoted and stays separate from organic Trending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCampaignCard(
    campaign: JSONObject,
    formatter: NumberFormat,
    working: Boolean,
    onCancel: () -> Unit,
) {
    val target = campaign.optString("target_type", "BOOST")
    val power = campaign.optInt("boost_power", 0)
    val budget = campaign.optLong("coin_budget", 0L)
    val spent = campaign.optLong("coin_spent", 0L)
    val refunded = campaign.optLong("coin_refunded", 0L)
    val remaining = (budget - spent - refunded).coerceAtLeast(0L)
    val impressions = campaign.optLong("promoted_impressions", 0L)
    val opens = campaign.optLong("promoted_opens", 0L)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text(
                    target.lowercase().replaceFirstChar { it.uppercase() } + " • " + power + "%",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(formatter.format(budget) + " coins", style = MaterialTheme.typography.labelMedium)
            }
            val progress = if (budget <= 0L) 0f else (spent.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
            val conversion = if (impressions <= 0L) 0.0 else opens.toDouble() * 100.0 / impressions.toDouble()
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(
                ((progress * 100).toInt()).toString() + "% delivered • " +
                    formatter.format(impressions) + " impressions • " +
                    formatter.format(opens) + " opens",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Open rate: " + String.format(Locale.US, "%.1f%%", conversion) +
                    " • Spent " + formatter.format(spent) +
                    " • Reserved " + formatter.format(remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            campaign.optString("ends_at").takeIf { it.isNotBlank() }?.let {
                Text(
                    "Ends " + shortGrowthDate(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onCancel, enabled = !working) {
                Text("Cancel & refund unused budget")
            }
        }
    }
}

@Composable
private fun BoostAnalyticsCard(
    analytics: JSONObject,
    formatter: NumberFormat,
) {
    val campaigns = analytics.optInt("campaigns", 0)
    val spent = analytics.optLong("spent", 0L)
    val refunded = analytics.optLong("refunded", 0L)
    val impressions = analytics.optLong("impressions", 0L)
    val opens = analytics.optLong("opens", 0L)
    val conversion = analytics.optDouble("conversion_rate", 0.0)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Boost performance", fontWeight = FontWeight.Black)
            Text(
                formatter.format(campaigns) + " campaigns • " +
                    formatter.format(impressions) + " impressions • " +
                    formatter.format(opens) + " opens",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Spent " + formatter.format(spent) + " • Refunded " + formatter.format(refunded) +
                    " • Open rate " + String.format(Locale.US, "%.1f%%", conversion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CampaignHistoryCard(
    campaign: JSONObject,
    formatter: NumberFormat,
) {
    val status = campaign.optString("status", "ENDED")
    val target = campaign.optString("target_type", "BOOST")
    val objective = campaign.optString("objective", "")
    val budget = campaign.optLong("coin_budget", 0L)
    val spent = campaign.optLong("coin_spent", 0L)
    val refunded = campaign.optLong("coin_refunded", 0L)
    val impressions = campaign.optLong("promoted_impressions", 0L)
    val opens = campaign.optLong("promoted_opens", 0L)
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    target.lowercase().replaceFirstChar { it.uppercase() } +
                        if (objective.isBlank()) "" else " • " + objective.lowercase().replace('_',' '),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (status == "ENDED") "Completed" else status.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
            Text(
                "Budget " + formatter.format(budget) +
                    " • Spent " + formatter.format(spent) +
                    " • Refunded " + formatter.format(refunded),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                formatter.format(impressions) + " impressions • " + formatter.format(opens) + " opens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            campaign.optString("created_at").takeIf { it.isNotBlank() }?.let {
                Text("Started " + shortGrowthDate(it), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GrowthReceiptCard(
    receipt: JSONObject,
    formatter: NumberFormat,
) {
    val amount = receipt.optLong("amount", 0L)
    val kind = receipt.optString("kind").replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(receipt.optString("item_name").ifBlank { kind }, fontWeight = FontWeight.SemiBold)
                Text(
                    kind + " • " + shortGrowthDate(receipt.optString("created_at")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (amount > 0) "+" else "") + formatter.format(amount) + " coins",
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Balance " + formatter.format(receipt.optLong("balance_after", 0L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Long?,
    formatter: NumberFormat,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text("Boost balance", style = MaterialTheme.typography.labelMedium)
                Text(
                    balance?.let { formatter.format(it) + " Blink Coins" } ?: "Balance unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

private fun shortGrowthDate(raw: String): String =
    raw.replace('T', ' ').replace("Z", "").take(16).ifBlank { "—" }

private fun boostUserMessage(error: Throwable, fallback: String): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("INSUFFICIENT_BLINK_COINS", ignoreCase = true) -> "You don't have enough Blink Coins."
        raw.contains("schema cache", ignoreCase = true) ||
            raw.contains("Could not find the function", ignoreCase = true) ->
            "Boost is updating. Please try again shortly."
        raw.contains("session has expired", ignoreCase = true) -> "Your Blink session expired. Please sign in again."
        else -> fallback
    }
}

@Composable
private fun SectionTitle(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTargetCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StatusCard(
    message: String,
    background: Color,
    foreground: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(13.dp), color = foreground)
    }
}

private fun defaultObjectiveFor(type: BlinkBoostTargetType): BlinkBoostObjective = when (type) {
    BlinkBoostTargetType.POST -> BlinkBoostObjective.VIEWS
    BlinkBoostTargetType.REEL -> BlinkBoostObjective.VIEWS
    BlinkBoostTargetType.PROFILE -> BlinkBoostObjective.PROFILE_VISITS
    BlinkBoostTargetType.LISTING -> BlinkBoostObjective.BUYER_INTEREST
}

private fun objectivesFor(type: BlinkBoostTargetType): List<BlinkBoostObjective> = when (type) {
    BlinkBoostTargetType.POST, BlinkBoostTargetType.REEL -> listOf(
        BlinkBoostObjective.VIEWS,
        BlinkBoostObjective.LIKES,
        BlinkBoostObjective.COMMENTS,
        BlinkBoostObjective.SAVES,
        BlinkBoostObjective.ENGAGEMENT,
        BlinkBoostObjective.PROFILE_VISITS,
        BlinkBoostObjective.FOLLOWERS,
    )
    BlinkBoostTargetType.PROFILE -> listOf(
        BlinkBoostObjective.REACH,
        BlinkBoostObjective.PROFILE_VISITS,
        BlinkBoostObjective.FOLLOWERS,
    )
    BlinkBoostTargetType.LISTING -> listOf(
        BlinkBoostObjective.REACH,
        BlinkBoostObjective.BUYER_INTEREST,
        BlinkBoostObjective.PROFILE_VISITS,
    )
}

private fun targetTypeLabel(type: BlinkBoostTargetType): String = when (type) {
    BlinkBoostTargetType.POST -> "Post"
    BlinkBoostTargetType.REEL -> "Reel"
    BlinkBoostTargetType.PROFILE -> "Profile"
    BlinkBoostTargetType.LISTING -> "Listing"
}

private fun targetTypeIcon(type: BlinkBoostTargetType): ImageVector = when (type) {
    BlinkBoostTargetType.POST -> Icons.Default.Article
    BlinkBoostTargetType.REEL -> Icons.Default.Movie
    BlinkBoostTargetType.PROFILE -> Icons.Default.Person
    BlinkBoostTargetType.LISTING -> Icons.Default.Storefront
}

private fun objectiveLabel(objective: BlinkBoostObjective): String = when (objective) {
    BlinkBoostObjective.REACH -> "Reach"
    BlinkBoostObjective.VIEWS -> "Views"
    BlinkBoostObjective.LIKES -> "Likes"
    BlinkBoostObjective.COMMENTS -> "Comments"
    BlinkBoostObjective.SAVES -> "Saves"
    BlinkBoostObjective.ENGAGEMENT -> "Any engagement"
    BlinkBoostObjective.PROFILE_VISITS -> "Profile visits"
    BlinkBoostObjective.FOLLOWERS -> "Followers"
    BlinkBoostObjective.BUYER_INTEREST -> "Buyer interest"
}

private fun audienceLabel(scope: BlinkBoostAudienceScope): String = when (scope) {
    BlinkBoostAudienceScope.MY_UNIVERSITY -> "My university"
    BlinkBoostAudienceScope.SELECTED_UNIVERSITY -> "Choose campus"
    BlinkBoostAudienceScope.ALL_CAMPUSES -> "All campuses"
}

private fun parseMissionItem(raw: JSONObject): MissionItem? {
    val campaignId = raw.optString("campaign_id")
    val targetId = raw.optString("target_id")
    if (campaignId.isBlank() || targetId.isBlank()) return null

    val owner = raw.optJSONObject("owner") ?: JSONObject()
    val ownerId = owner.optString("id")
    val ownerUsername = owner.optString("username")
    val ownerName = owner.optString("full_name").ifBlank { ownerUsername }
    val actions = raw.optJSONArray("actions").objectList().mapNotNull { action ->
        val key = action.optString("key")
        if (key.isBlank()) {
            null
        } else {
            MissionAction(
                key = key,
                label = action.optString("label", key),
                points = action.optInt("points", 0),
            )
        }
    }

    return MissionItem(
        campaignId = campaignId,
        targetType = raw.optString("target_type"),
        targetId = targetId,
        ownerId = ownerId,
        ownerUsername = ownerUsername,
        ownerName = ownerName,
        actions = actions,
        post = raw.optJSONObject("post")?.let { parsePromotedPost(it, owner) },
        listing = raw.optJSONObject("listing")?.let(::parsePromotedListing),
    )
}

private fun parsePromotedPost(
    raw: JSONObject,
    owner: JSONObject,
): FeedPost {
    val images = buildList {
        raw.optJSONArray("images").stringList().filter(String::isNotBlank).forEach(::add)
        raw.optString("image_url")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let(::add)
    }.distinct()

    val badge = runCatching {
        VerificationBadge.valueOf(owner.optString("verification_badge", "NONE").uppercase())
    }.getOrDefault(VerificationBadge.NONE)

    return FeedPost(
        id = raw.optString("id"),
        author = owner.optString("full_name").ifBlank { owner.optString("username") },
        authorAvatar = owner.optString("avatar_url"),
        facultyTag = raw.optString("faculty"),
        isVerified = badge != VerificationBadge.NONE,
        verificationBadge = badge,
        timeAgo = "Promoted",
        text = raw.optString("text").ifBlank { raw.optString("caption") },
        images = images,
        likes = raw.optInt("like_count", 0),
        commentsCount = raw.optInt("comment_count", 0),
        sharesCount = raw.optInt("share_count", 0),
        repostsCount = raw.optInt("repost_count", 0),
        viewsCount = raw.optInt("view_count", 0),
        isReel = raw.optBoolean("is_reel", false),
        videoUrl = raw.optString("video_url").takeIf { it.isNotBlank() && it != "null" },
        audience = raw.optString("audience", "Everyone"),
        category = raw.optString("category", "Campus Life"),
        location = raw.optString("location").takeIf(String::isNotBlank),
        linkUrl = raw.optString("link_url").takeIf(String::isNotBlank),
        allowComments = raw.optBoolean("allow_comments", true),
        hideLikes = raw.optBoolean("hide_likes", false),
        isPinned = raw.optBoolean("is_pinned", false),
        isDisappearing = raw.optBoolean("is_disappearing", false),
        audioTitle = raw.optString("audio_title").takeIf(String::isNotBlank),
        altText = raw.optString("alt_text").takeIf(String::isNotBlank),
        isSponsored = true,
        adLabel = "Promoted",
        createdAt = raw.optString("created_at"),
        authorUsername = owner.optString("username"),
    )
}

private fun parsePromotedListing(raw: JSONObject): MarketItem {
    val images = buildList {
        raw.optJSONArray("image_urls").stringList().filter(String::isNotBlank).forEach(::add)
        raw.optString("image_url")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let(::add)
    }.distinct()

    return MarketItem(
        id = raw.optString("id"),
        title = raw.optString("title"),
        price = raw.optLong("price", 0L),
        images = images,
        sellerUsername = raw.optString("seller_username"),
        sellerAvatar = raw.optString("seller_avatar"),
        sellerName = raw.optString("seller_name"),
        sellerPhone = raw.optString("seller_phone"),
        sellerWhatsapp = raw.optString("seller_whatsapp"),
        sellerIsVerified = raw.optBoolean("seller_is_verified", false),
        sellerRating = raw.optDouble("seller_rating", 0.0),
        sellerReviewCount = raw.optInt("seller_review_count", 0),
        university = raw.optString("university"),
        location = raw.optString("location"),
        category = raw.optString("category"),
        condition = raw.optString("condition", "Used"),
        description = raw.optString("description"),
        postedTime = "Promoted",
        isFeatured = true,
        isSold = raw.optBoolean("is_sold", false),
    )
}

private fun JSONArray?.objectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

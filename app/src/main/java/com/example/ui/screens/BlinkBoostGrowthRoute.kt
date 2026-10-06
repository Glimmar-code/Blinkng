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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.repository.FollowStateStore
import com.example.data.supabase.BlinkEconomyService
import com.example.ui.components.PostCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private enum class BoostGrowthColumn { BOOST, EARN }

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

@Composable
private fun BoostCampaignColumn(
    posts: List<FeedPost>,
    reels: List<FeedPost>,
    marketItems: List<MarketItem>,
    myProfile: UserProfile,
    isDark: Boolean,
) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

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

    var targetType by remember { mutableStateOf(BlinkBoostTargetType.POST) }
    var targetId by remember { mutableStateOf("") }
    var boostPower by remember { mutableIntStateOf(25) }
    var objective by remember { mutableStateOf(BlinkBoostObjective.VIEWS) }
    var audience by remember { mutableStateOf(BlinkBoostAudienceScope.MY_UNIVERSITY) }
    var durationDays by remember { mutableIntStateOf(3) }
    var selectedUniversity by remember { mutableStateOf("") }
    var universityMenuOpen by remember { mutableStateOf(false) }

    var state by remember { mutableStateOf<JSONObject?>(null) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var quoteLoading by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmStart by remember { mutableStateOf(false) }

    suspend fun reloadState() {
        service.boostGrowthState()
            .onSuccess { state = it; error = null }
            .onFailure { error = it.message ?: "Unable to load Boost." }
    }

    LaunchedEffect(Unit) { reloadState() }

    LaunchedEffect(targetType) {
        targetId = when (targetType) {
            BlinkBoostTargetType.PROFILE -> myProfile.id
            else -> ""
        }
        objective = defaultObjectiveFor(targetType)
        quote = null
        error = null
    }

    val targetUniversity = when (audience) {
        BlinkBoostAudienceScope.SELECTED_UNIVERSITY -> selectedUniversity.takeIf(String::isNotBlank)
        else -> null
    }
    val targetReady = targetId.isNotBlank() &&
        (audience != BlinkBoostAudienceScope.SELECTED_UNIVERSITY || selectedUniversity.isNotBlank())

    LaunchedEffect(targetType, targetId, boostPower, objective, audience, durationDays, selectedUniversity) {
        quote = null
        if (!targetReady) return@LaunchedEffect
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
            error = null
        }.onFailure {
            error = it.message ?: "Unable to calculate this boost."
        }
        quoteLoading = false
    }

    val balance = state?.optLong("balance", 0L) ?: 0L
    val quoteCost = quote?.optLong("coin_cost", 0L) ?: 0L
    val activeCampaigns = state?.optJSONArray("campaigns").objectList()
        .filter { it.optString("status") == "ACTIVE" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BalanceCard(balance = balance, formatter = formatter) }

        error?.let { value ->
            item {
                StatusCard(value, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        message?.let { value ->
            item {
                StatusCard(value, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
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
                    item { EmptyTargetCard("You do not have a boostable post yet.") }
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
                    item { EmptyTargetCard("You do not have a boostable Reel yet.") }
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
            SectionTitle("2. Boost power", "Higher power increases delivery speed and estimated reach.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = boostPower.toFloat(),
                    onValueChange = { boostPower = it.toInt().coerceIn(1, 100) },
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
            SectionTitle("4. Audience", "Choose your campus or expand across BLINK.")
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
                enabled = !working && !quoteLoading && quote != null && quoteCost > 0 && balance >= quoteCost,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    when {
                        quote == null -> "Choose a boost target"
                        balance < quoteCost -> "Not enough Blink Coins"
                        else -> "Start Boost • " + formatter.format(quoteCost) + " coins"
                    },
                )
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
                                        message = if (refund > 0) {
                                            "Boost cancelled. " + formatter.format(refund) + " unused coins were returned."
                                        } else {
                                            "Boost cancelled."
                                        }
                                        reloadState()
                                    }
                                    .onFailure { error = it.message ?: "Unable to cancel this boost." }
                                working = false
                            }
                        }
                    },
                )
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
                    Text("Cost: " + formatter.format(quoteCost) + " Blink Coins", fontWeight = FontWeight.Black)
                    Text(
                        "Reach and engagement are estimates, not guaranteed results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !working && quoteCost > 0 && balance >= quoteCost,
                    onClick = {
                        scope.launch {
                            working = true
                            service.createBoostCampaign(
                                targetType = targetType.name,
                                targetId = targetId,
                                boostPower = boostPower,
                                objective = objective.name,
                                audienceScope = audience.name,
                                durationDays = durationDays,
                                targetUniversity = targetUniversity,
                            ).onSuccess {
                                confirmStart = false
                                val charged = it.optLong("charged", quoteCost)
                                message = "Boost started. " + formatter.format(charged) + " coins were charged for campaign delivery."
                                reloadState()
                            }.onFailure {
                                error = it.message ?: "Unable to start this boost."
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

    suspend fun reload() {
        loading = true
        service.boostMissions()
            .onSuccess { payload = it; error = null }
            .onFailure { error = it.message ?: "Unable to load Rank Point opportunities." }
        loading = false
    }

    fun refreshSoon() {
        scope.launch {
            delay(650)
            refreshNonce++
        }
    }

    LaunchedEffect(refreshNonce) {
        if (refreshNonce == 0) FollowStateStore.refresh()
        reload()
    }

    val missions = payload?.optJSONArray("items").objectList().mapNotNull(::parseMissionItem)
    val offered = payload?.optInt("offered_points", 0) ?: 0
    val cap = payload?.optInt("suggested_points_cap", 20) ?: 20

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
                        IconButton(onClick = { refreshNonce++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh opportunities")
                        }
                    }
                    Text(
                        "Complete genuine actions on promoted content. BLINK uses the normal Rank Points ledger, so each action can pay only once.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Suggested opportunities: " + offered + " / " + cap + " RP",
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = if (cap <= 0) 0f else (offered.toFloat() / cap).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                StatusCard(value, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (!loading && missions.isEmpty() && error == null) {
            item {
                EmptyTargetCard(
                    "No new promoted Rank Point opportunities are available right now. Completed actions are never recycled for extra points.",
                )
            }
        }

        items(missions, key = { "mission-" + it.campaignId + "-" + it.targetId }) { mission ->
            LaunchedEffect(mission.campaignId) {
                service.recordBoostDelivery(mission.campaignId, "IMPRESSION", "MISSIONS")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PromotedHeader(mission)

                mission.post?.let { initialPost ->
                    var localPost by remember(mission.campaignId) {
                        mutableStateOf(initialPost.copy(isSponsored = true, adLabel = "Promoted"))
                    }
                    val available = mission.actions.map { it.key }.toSet()

                    PostCard(
                        post = localPost,
                        isDark = isDark,
                        onLike = {
                            if ("like" in available) {
                                localPost = localPost.copy(isLiked = true, likes = localPost.likes + 1)
                                onLikePost(localPost.id)
                                refreshSoon()
                            }
                        },
                        onComment = {
                            if ("comment" in available) onCommentPost(localPost.id)
                        },
                        onBookmark = {
                            if ("save" in available) {
                                localPost = localPost.copy(isBookmarked = true)
                                onBookmarkPost(localPost.id)
                                refreshSoon()
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
                        onFollowAuthor = { profileId ->
                            scope.launch {
                                if (FollowStateStore.setFollowing(profileId, true)) {
                                    delay(300)
                                    refreshNonce++
                                }
                            }
                        },
                        onUnfollowAuthor = {},
                        trackExposure = true,
                    )
                }

                mission.listing?.let { listing ->
                    ProductCard(
                        item = listing,
                        onClick = {
                            scope.launch {
                                service.recordBoostDelivery(mission.campaignId, "LISTING_OPEN", "MISSIONS")
                                refreshNonce++
                            }
                            onListingClick(listing)
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
                            scope.launch {
                                if (FollowStateStore.setFollowing(mission.ownerId, true)) {
                                    delay(250)
                                    refreshNonce++
                                }
                            }
                        },
                    )
                }

                MissionActionBar(
                    actions = mission.actions,
                    onAction = { action ->
                        when (action.key) {
                            "like" -> mission.post?.let {
                                onLikePost(it.id)
                                refreshSoon()
                            }
                            "comment" -> mission.post?.let { onCommentPost(it.id) }
                            "save" -> mission.post?.let {
                                onBookmarkPost(it.id)
                                refreshSoon()
                            }
                            "follow" -> scope.launch {
                                if (FollowStateStore.setFollowing(mission.ownerId, true)) {
                                    delay(300)
                                    refreshNonce++
                                }
                            }
                            "listing_open" -> mission.listing?.let { listing ->
                                scope.launch {
                                    service.recordBoostDelivery(mission.campaignId, "LISTING_OPEN", "MISSIONS")
                                    refreshNonce++
                                }
                                onListingClick(listing)
                            }
                        }
                    },
                )
            }
        }
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
    balance: Long,
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
                    Text(
                        formatter.format(cost) + " Blink Coins",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text("Estimated reach: " + formatter.format(low) + "–" + formatter.format(high))
                    Text("Balance: " + formatter.format(balance) + " coins")
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
            Text(
                impressions.toString() + " promoted impressions • " + opens + " opens",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onCancel, enabled = !working) {
                Text("Cancel & refund unused time")
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Long,
    formatter: NumberFormat,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
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
                    formatter.format(balance) + " Blink Coins",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
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
private fun EmptyTargetCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

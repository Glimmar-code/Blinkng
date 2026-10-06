package com.blinkng.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private enum class DesktopBoostColumn { BOOST, EARN }

private data class DesktopBoostTarget(
    val id: String,
    val type: String,
    val label: String,
)

@Composable
fun DesktopBoostGrowthScreen(state: DesktopAppState) {
    var column by remember { mutableStateOf(DesktopBoostColumn.BOOST) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Boost & Earn Rank Points", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "Promote posts, Reels, your profile or a Listing — or earn Rank Points from genuine promoted-content actions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DesktopGrowthTab(
                selected = column == DesktopBoostColumn.BOOST,
                label = "Boost",
                icon = Icons.Rounded.Campaign,
                onClick = { column = DesktopBoostColumn.BOOST },
                modifier = Modifier.weight(1f),
            )
            DesktopGrowthTab(
                selected = column == DesktopBoostColumn.EARN,
                label = "Earn Rank Points",
                icon = Icons.Rounded.EmojiEvents,
                onClick = { column = DesktopBoostColumn.EARN },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()

        when (column) {
            DesktopBoostColumn.BOOST -> DesktopBoostCampaignColumn(state)
            DesktopBoostColumn.EARN -> DesktopEarnRankPointsColumn(state)
        }
    }
}

@Composable
private fun DesktopGrowthTab(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DesktopBoostCampaignColumn(state: DesktopAppState) {
    val rpc = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    val formatter = remember { NumberFormat.getNumberInstance(Locale.US) }

    var growthState by remember { mutableStateOf<JSONObject?>(null) }
    var boostable by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var targetType by remember { mutableStateOf("POST") }
    var targetId by remember { mutableStateOf("") }
    var power by remember { mutableFloatStateOf(25f) }
    var objective by remember { mutableStateOf("VIEWS") }
    var audience by remember { mutableStateOf("MY_UNIVERSITY") }
    var duration by remember { mutableIntStateOf(3) }
    var selectedUniversity by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun reload() {
        loading = true
        runCatching {
            growthState = rpc.getBoostGrowthState()
            boostable = rpc.getBoostableContent()
        }.onSuccess {
            error = null
        }.onFailure {
            error = it.message ?: "Unable to load Boost."
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LaunchedEffect(targetType) {
        targetId = if (targetType == "PROFILE") state.profile?.id.orEmpty() else ""
        objective = when (targetType) {
            "PROFILE" -> "PROFILE_VISITS"
            "LISTING" -> "BUYER_INTEREST"
            else -> "VIEWS"
        }
        quote = null
    }

    val postTargets = boostable?.optJSONArray("posts").objects().mapNotNull { row ->
        val type = row.optString("type").uppercase()
        if (type !in setOf("POST", "REEL")) return@mapNotNull null
        DesktopBoostTarget(
            id = row.optString("id"),
            type = type,
            label = row.optString("text").ifBlank { if (type == "REEL") "Reel" else "Post" },
        )
    }
    val listingTargets = boostable?.optJSONArray("market").objects().map {
        DesktopBoostTarget(
            id = it.optString("id"),
            type = "LISTING",
            label = it.optString("text").ifBlank { "Listing" },
        )
    }
    val targets = when (targetType) {
        "POST" -> postTargets.filter { it.type == "POST" }
        "REEL" -> postTargets.filter { it.type == "REEL" }
        "LISTING" -> listingTargets
        "PROFILE" -> listOfNotNull(
            state.profile?.let {
                DesktopBoostTarget(it.id, "PROFILE", "@" + it.username + " · " + it.fullName)
            },
        )
        else -> emptyList()
    }

    val objectives = when (targetType) {
        "POST", "REEL" -> listOf("VIEWS", "LIKES", "COMMENTS", "SAVES", "ENGAGEMENT", "PROFILE_VISITS", "FOLLOWERS")
        "PROFILE" -> listOf("REACH", "PROFILE_VISITS", "FOLLOWERS")
        "LISTING" -> listOf("REACH", "BUYER_INTEREST", "PROFILE_VISITS")
        else -> listOf("REACH")
    }

    val balance = growthState?.optLong("balance", state.profile?.coinBalance ?: 0L)
        ?: (state.profile?.coinBalance ?: 0L)
    val campaigns = growthState?.optJSONArray("campaigns").objects()
    val quoteCost = quote?.optLong("coin_cost", 0L) ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Campaign, null)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blink Coin balance", fontWeight = FontWeight.Bold)
                        Text(formatter.format(balance) + " coins", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    IconButton(onClick = { scope.launch { reload() } }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Boost")
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

        error?.let { value -> item { DesktopBoostStatus(value, true) } }
        message?.let { value -> item { DesktopBoostStatus(value, false) } }

        item {
            DesktopBoostSection("1. Choose what to boost", "Listing is the BLINK name for a Market post.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("POST", "REEL", "PROFILE", "LISTING")) { type ->
                    FilterChip(
                        selected = targetType == type,
                        onClick = { targetType = type },
                        label = { Text(type.lowercase().replaceFirstChar(Char::uppercase)) },
                        leadingIcon = {
                            Icon(
                                when (type) {
                                    "POST" -> Icons.Rounded.Article
                                    "REEL" -> Icons.Rounded.Movie
                                    "PROFILE" -> Icons.Rounded.Person
                                    else -> Icons.Rounded.Storefront
                                },
                                null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                }
            }
        }

        if (!loading) {
            if (targets.isEmpty()) {
                item { DesktopBoostStatus("No boostable " + targetType.lowercase() + " is available yet.", false) }
            } else {
                items(targets.take(30), key = { "boost-target-" + it.type + "-" + it.id }) { target ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            targetId = target.id
                            quote = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (targetId == target.id) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                        border = BorderStroke(
                            1.dp,
                            if (targetId == target.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                target.label,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (targetId == target.id) "Selected" else "Select",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        item {
            DesktopBoostSection("2. Boost power", "1–100% controls campaign delivery strength and price.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = power,
                    onValueChange = {
                        power = it.coerceIn(1f, 100f)
                        quote = null
                    },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(power.toInt().toString() + "%", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }

        item {
            DesktopBoostSection("3. Goal", "BLINK optimizes delivery; it never guarantees fake likes, comments or followers.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(objectives) { item ->
                    FilterChip(
                        selected = objective == item,
                        onClick = { objective = item; quote = null },
                        label = { Text(item.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }

        item {
            DesktopBoostSection("4. Audience", "Target your university, another university, or all campuses.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("MY_UNIVERSITY", "SELECTED_UNIVERSITY", "ALL_CAMPUSES")) { item ->
                    FilterChip(
                        selected = audience == item,
                        onClick = { audience = item; quote = null },
                        label = {
                            Text(
                                when (item) {
                                    "MY_UNIVERSITY" -> "My university"
                                    "SELECTED_UNIVERSITY" -> "Another university"
                                    else -> "All campuses"
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (item) {
                                    "MY_UNIVERSITY" -> Icons.Rounded.School
                                    "SELECTED_UNIVERSITY" -> Icons.Rounded.Groups
                                    else -> Icons.Rounded.Public
                                },
                                null,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                }
            }
            if (audience == "SELECTED_UNIVERSITY") {
                OutlinedTextField(
                    value = selectedUniversity,
                    onValueChange = { selectedUniversity = it; quote = null },
                    label = { Text("University") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        item {
            DesktopBoostSection("5. Duration", "Longer campaigns receive the configured duration discount.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(1, 3, 7, 14, 30)) { days ->
                    FilterChip(
                        selected = duration == days,
                        onClick = { duration = days; quote = null },
                        label = { Text(days.toString() + if (days == 1) " day" else " days") },
                    )
                }
            }
        }

        item {
            val university = when (audience) {
                "SELECTED_UNIVERSITY" -> selectedUniversity.trim().takeIf(String::isNotBlank)
                else -> null
            }
            val ready = targetId.isNotBlank() &&
                (audience != "SELECTED_UNIVERSITY" || !university.isNullOrBlank())

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            working = true
                            runCatching {
                                rpc.quoteBoostCampaign(
                                    targetType = targetType,
                                    targetId = targetId,
                                    boostPower = power.toInt(),
                                    objective = objective,
                                    audienceScope = audience,
                                    durationDays = duration,
                                    targetUniversity = university,
                                )
                            }.onSuccess {
                                quote = it
                                error = null
                            }.onFailure {
                                error = it.message ?: "Unable to calculate this Boost."
                            }
                            working = false
                        }
                    },
                    enabled = ready && !working,
                ) { Text("Preview cost") }

                Button(
                    onClick = {
                        scope.launch {
                            working = true
                            runCatching {
                                rpc.createBoostCampaign(
                                    targetType = targetType,
                                    targetId = targetId,
                                    boostPower = power.toInt(),
                                    objective = objective,
                                    audienceScope = audience,
                                    durationDays = duration,
                                    targetUniversity = university,
                                )
                            }.onSuccess {
                                message = "Boost campaign started."
                                quote = null
                                reload()
                            }.onFailure {
                                error = it.message ?: "Unable to start this Boost."
                            }
                            working = false
                        }
                    },
                    enabled = ready && !working && quote != null && quoteCost > 0 && balance >= quoteCost,
                ) {
                    Text(
                        if (quoteCost > 0) "Start Boost · " + formatter.format(quoteCost) + " coins"
                        else "Start Boost",
                    )
                }
            }

            quote?.let { result ->
                val low = result.optLong("estimated_reach_low", 0L)
                val high = result.optLong("estimated_reach_high", 0L)
                Text(
                    "Estimated reach: " + formatter.format(low) + "–" + formatter.format(high) + " · Estimate only, not guaranteed.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        if (campaigns.isNotEmpty()) {
            item { DesktopBoostSection("Campaigns", "Active and past Boost campaigns.") }
            items(campaigns, key = { it.optString("id") }) { campaign ->
                val active = campaign.optString("status") == "ACTIVE"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                campaign.optString("target_type") + " · " + campaign.optString("objective"),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Black,
                            )
                            Text(campaign.optString("status"), fontSize = 11.sp)
                        }
                        Text(
                            campaign.optInt("boost_power").toString() + "% power · " +
                                campaign.optInt("duration_days").toString() + " days · " +
                                formatter.format(campaign.optLong("coin_budget")) + " coins",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        if (active) {
                            OutlinedButton(
                                onClick = {
                                    val id = campaign.optString("id")
                                    scope.launch {
                                        working = true
                                        runCatching { rpc.cancelBoostCampaign(id) }
                                            .onSuccess {
                                                message = "Boost cancelled. Unused time was refunded by the server."
                                                reload()
                                            }
                                            .onFailure { error = it.message ?: "Unable to cancel Boost." }
                                        working = false
                                    }
                                },
                                enabled = !working,
                            ) { Text("End campaign") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopEarnRankPointsColumn(state: DesktopAppState) {
    val rpc = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()

    var payload by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableIntStateOf(0) }

    suspend fun reload() {
        loading = true
        runCatching { rpc.getBoostMissions(20) }
            .onSuccess { payload = it; error = null }
            .onFailure { error = it.message ?: "Unable to load Rank Point opportunities." }
        loading = false
    }

    LaunchedEffect(nonce) { reload() }

    val cap = payload?.optInt("suggested_points_cap", 20) ?: 20
    val earned = payload?.optInt("earned_from_missions_today", 0) ?: 0
    val remaining = payload?.optInt("remaining_mission_points_today", (cap - earned).coerceAtLeast(0))
        ?: (cap - earned).coerceAtLeast(0)
    val offered = payload?.optInt("offered_points", 0) ?: 0
    val missions = payload?.optJSONArray("items").objects()

    fun complete(campaignId: String, action: String, comment: String? = null) {
        if (busyKey != null) return
        busyKey = campaignId + ":" + action
        scope.launch {
            runCatching { rpc.completeBoostMissionAction(campaignId, action, comment) }
                .onSuccess { result ->
                    val awarded = result.optInt("points_awarded", 0)
                    message = if (awarded > 0) {
                        "+" + awarded + if (awarded == 1) " Rank Point earned." else " Rank Points earned."
                    } else {
                        "Action completed."
                    }
                    error = null
                    nonce++
                    state.refreshProfile()
                }
                .onFailure { error = it.message ?: "This Rank Point action could not be completed." }
            busyKey = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null)
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Earn Rank Points", fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(
                                "Genuine promoted-content actions only. Undoing and repeating an action never pays twice.",
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { nonce++ }, enabled = busyKey == null) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                    Text(
                        "Today: " + earned + " / " + cap + " RP · Available now: up to " + offered + " RP",
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = if (cap <= 0) 0f else (earned.toFloat() / cap).coerceIn(0f, 1f),
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
        error?.let { value -> item { DesktopBoostStatus(value, true) } }
        message?.let { value -> item { DesktopBoostStatus(value, false) } }

        if (!loading && missions.isEmpty() && error == null) {
            item {
                DesktopBoostStatus(
                    if (remaining <= 0) "You reached today's Boost Mission Rank Point limit."
                    else "No new promoted Rank Point opportunities are available right now.",
                    false,
                )
            }
        }

        items(missions, key = { "desktop-mission-" + it.optString("campaign_id") + "-" + it.optString("target_id") }) { mission ->
            val campaignId = mission.optString("campaign_id")
            val targetType = mission.optString("target_type")
            val owner = mission.optJSONObject("owner") ?: JSONObject()
            val ownerName = owner.optString("full_name").ifBlank { owner.optString("username") }
            val actions = mission.optJSONArray("actions").objects()
            val post = mission.optJSONObject("post")
            val listing = mission.optJSONObject("listing")
            val targetLabel = when {
                post != null -> post.optString("text").ifBlank { post.optString("caption") }.ifBlank { targetType.lowercase() }
                listing != null -> listing.optString("title").ifBlank { "Listing" }
                else -> "@" + owner.optString("username")
            }
            var comment by remember(campaignId) { mutableStateOf("") }

            LaunchedEffect(campaignId) {
                runCatching { rpc.recordBoostDelivery(campaignId, "IMPRESSION", "MISSIONS") }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Campaign, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Promoted", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Up to +" + actions.sumOf { it.optInt("points", 0) } + " RP",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                    }

                    Text(ownerName, fontWeight = FontWeight.Black)
                    Text(
                        targetLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(actions, key = { it.optString("key") }) { action ->
                            val key = action.optString("key")
                            val points = action.optInt("points", 0)
                            AssistChip(
                                onClick = {
                                    if (key in setOf("view", "like", "save", "follow", "listing_open")) {
                                        complete(campaignId, key)
                                    }
                                },
                                enabled = busyKey == null && key != "comment",
                                label = { Text(action.optString("label", key) + " · +" + points + " RP") },
                                leadingIcon = {
                                    Icon(
                                        when (key) {
                                            "view" -> Icons.Rounded.Visibility
                                            "like" -> Icons.Rounded.FavoriteBorder
                                            "comment" -> Icons.Rounded.ChatBubbleOutline
                                            "save" -> Icons.Rounded.Bookmark
                                            "follow" -> Icons.Rounded.PersonAdd
                                            "listing_open" -> Icons.Rounded.Storefront
                                            else -> Icons.Rounded.EmojiEvents
                                        },
                                        null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

                    if (actions.any { it.optString("key") == "comment" }) {
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it.take(2000) },
                            label = { Text("Genuine comment · +2 RP") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                        )
                        Button(
                            onClick = {
                                val clean = comment.trim()
                                if (clean.isNotEmpty()) complete(campaignId, "comment", clean)
                            },
                            enabled = comment.trim().isNotEmpty() && busyKey == null,
                        ) { Text("Post comment") }
                    }

                    if (busyKey?.startsWith(campaignId + ":") == true) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    if (targetType == "PROFILE") {
                        OutlinedButton(onClick = { state.selectedRoute = "profile" }) {
                            Text("Open profile")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopBoostSection(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun DesktopBoostStatus(text: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.BlinkDropAction
import com.blinkng.shared.BlinkDropAudienceScope
import com.blinkng.shared.BlinkDropTargetType
import com.blinkng.shared.BlinkDropsPolicy
import com.example.data.supabase.BlinkDropsService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private enum class DropsColumn { GIVEAWAYS, TOP_GIVERS }

private data class DropTarget(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
)

private data class DropItem(
    val id: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorName: String,
    val rewardPerUser: Int,
    val winnerCount: Int,
    val claimedCount: Int,
    val totalCoins: Long,
    val action: String,
    val targetType: String,
    val targetTitle: String,
    val audienceScope: String,
    val targetUniversity: String,
    val status: String,
    val endsAt: String,
    val eligible: Boolean,
)

private data class TopGiver(
    val creatorId: String,
    val username: String,
    val name: String,
    val coinsGiven: Long,
    val dropsCompleted: Int,
    val peopleRewarded: Int,
    val following: Boolean,
)

@Composable
fun BlinkDropsRoute(
    onClose: () -> Unit,
    initialDropId: String? = null,
) {
    val service = remember { BlinkDropsService() }
    val scope = rememberCoroutineScope()
    val formatter = remember { NumberFormat.getIntegerInstance(Locale.US) }

    var selectedColumn by remember { mutableStateOf(DropsColumn.GIVEAWAYS) }
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showOrganizer by remember { mutableStateOf(false) }
    var commentDrop by remember { mutableStateOf<DropItem?>(null) }
    var busyDropId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        refreshNonce += 1
    }

    LaunchedEffect(refreshNonce) {
        loading = true
        service.state(30)
            .onSuccess {
                state = it
                error = null
            }
            .onFailure {
                error = it.message ?: "Unable to load BLINK Drops."
            }
        loading = false
    }

    val balance = state?.optLong("balance", 0L) ?: 0L
    val activeDrops = state?.optJSONArray("active_drops").toDropItems()
    val myDrops = state?.optJSONArray("my_drops").toDropItems()
    val topGivers = state?.optJSONArray("top_givers").toTopGivers()
    val targets = state?.optJSONObject("targets")
    val postTargets = targets?.optJSONArray("posts").toTargets()
    val listingTargets = targets?.optJSONArray("listings").toTargets()

    fun complete(drop: DropItem, comment: String? = null) {
        if (busyDropId != null) return
        busyDropId = drop.id
        scope.launch {
            service.completeAction(drop.id, comment)
                .onSuccess { result ->
                    val reward = result.optLong("reward", drop.rewardPerUser.toLong())
                    message = "You earned ${formatter.format(reward)} BLINK Coins."
                    error = null
                    commentDrop = null
                    refresh()
                }
                .onFailure {
                    error = it.message ?: "Unable to complete this Drop."
                }
            busyDropId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "BLINK Drops",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Reward followers with BLINK Coins. No random draws or paid entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Drops")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Drops")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                    RoundedCornerShape(16.dp)
                )
                .padding(4.dp),
        ) {
            DropsTab(
                selected = selectedColumn == DropsColumn.GIVEAWAYS,
                icon = Icons.Default.CardGiftcard,
                label = "Giveaways",
                onClick = { selectedColumn = DropsColumn.GIVEAWAYS },
                modifier = Modifier.weight(1f),
            )
            DropsTab(
                selected = selectedColumn == DropsColumn.TOP_GIVERS,
                icon = Icons.Default.EmojiEvents,
                label = "Top Givers",
                onClick = { selectedColumn = DropsColumn.TOP_GIVERS },
                modifier = Modifier.weight(1f),
            )
        }

        if (loading && state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        when (selectedColumn) {
            DropsColumn.GIVEAWAYS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    BalanceAndCreateCard(
                        balance = balance,
                        formatter = formatter,
                        onCreate = { showOrganizer = true }
                    )
                }

                error?.let { value ->
                    item { StatusCard(value, isError = true) }
                }
                message?.let { value ->
                    item { StatusCard(value, isError = false) }
                }

                item {
                    SectionHeader(
                        "Active for you",
                        "These rewards were reserved for eligible followers when each Drop started."
                    )
                }

                if (activeDrops.isEmpty()) {
                    item {
                        EmptyCard(
                            "No active Drops are available for you right now. Follow creators you like so you can be included in their next Drop."
                        )
                    }
                } else {
                    items(activeDrops, key = { "active-drop-${it.id}" }) { drop ->
                        DropCard(
                            drop = drop,
                            formatter = formatter,
                            busy = busyDropId == drop.id,
                            highlighted = initialDropId == drop.id,
                            onComplete = {
                                if (drop.action == "COMMENT") commentDrop = drop
                                else complete(drop)
                            },
                            onCancel = null,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(
                        "Your Drops",
                        "Reserved coins stay locked until claimed, cancelled or expired."
                    )
                }

                if (myDrops.isEmpty()) {
                    item { EmptyCard("You have not organized a BLINK Drop yet.") }
                } else {
                    items(myDrops, key = { "my-drop-${it.id}" }) { drop ->
                        DropCard(
                            drop = drop,
                            formatter = formatter,
                            busy = busyDropId == drop.id,
                            highlighted = false,
                            onComplete = null,
                            onCancel = if (drop.status == "ACTIVE") {
                                {
                                    busyDropId = drop.id
                                    scope.launch {
                                        service.cancelDrop(drop.id)
                                            .onSuccess {
                                                message = "Drop cancelled. Unclaimed reserved coins were returned."
                                                error = null
                                                refresh()
                                            }
                                            .onFailure {
                                                error = it.message ?: "Unable to cancel this Drop."
                                            }
                                        busyDropId = null
                                    }
                                }
                            } else null,
                        )
                    }
                }
            }

            DropsColumn.TOP_GIVERS -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionHeader(
                        "Top Givers",
                        "Ranked by BLINK Coins successfully distributed, not by how many Drops were created."
                    )
                }
                if (topGivers.isEmpty()) {
                    item { EmptyCard("Top Givers will appear after completed Drops.") }
                } else {
                    items(topGivers, key = { "giver-${it.creatorId}" }) { giver ->
                        TopGiverCard(
                            giver = giver,
                            formatter = formatter,
                            onFollow = if (giver.following) null else {
                                {
                                    scope.launch {
                                        service.followCreator(giver.creatorId)
                                            .onSuccess {
                                                message = "You will be eligible for this creator's future Drops."
                                                refresh()
                                            }
                                            .onFailure {
                                                error = it.message ?: "Unable to follow this creator."
                                            }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showOrganizer) {
        OrganizeDropDialog(
            balance = balance,
            posts = postTargets,
            listings = listingTargets,
            onDismiss = { showOrganizer = false },
            onCreate = { target, action, reward, winners, audience, duration ->
                scope.launch {
                    service.createDrop(
                        targetType = target.type,
                        targetId = target.id,
                        action = action,
                        rewardPerUser = reward,
                        winnerCount = winners,
                        audienceScope = audience,
                        durationHours = duration,
                    ).onSuccess { result ->
                        val notified = result.optInt("eligible_followers", 0)
                        message = "Drop started. $notified eligible follower${if (notified == 1) "" else "s"} notified."
                        error = null
                        showOrganizer = false
                        refresh()
                    }.onFailure {
                        error = it.message ?: "Unable to create this Drop."
                    }
                }
            }
        )
    }

    commentDrop?.let { drop ->
        CommentDropDialog(
            reward = drop.rewardPerUser,
            onDismiss = { commentDrop = null },
            onSubmit = { complete(drop, it) }
        )
    }
}

@Composable
private fun DropsTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun BalanceAndCreateCard(
    balance: Long,
    formatter: NumberFormat,
    onCreate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Available balance", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${formatter.format(balance)} BLINK Coins",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Creating a Drop reserves the full reward budget immediately.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Organize")
            }
        }
    }
}

@Composable
private fun DropCard(
    drop: DropItem,
    formatter: NumberFormat,
    busy: Boolean,
    highlighted: Boolean,
    onComplete: (() -> Unit)?,
    onCancel: (() -> Unit)?,
) {
    val remaining = (drop.winnerCount - drop.claimedCount).coerceAtLeast(0)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 3.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        drop.creatorName.ifBlank { "@${drop.creatorUsername}" },
                        fontWeight = FontWeight.Bold,
                    )
                    if (drop.creatorUsername.isNotBlank()) {
                        Text(
                            "@${drop.creatorUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = { Text(drop.status.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }

            Text(
                "${formatter.format(drop.rewardPerUser)} coins each · ${drop.winnerCount} people",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "${actionLabel(drop.action)} · ${drop.targetTitle.ifBlank { targetLabel(drop.targetType) }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (drop.audienceScope == "MY_CAMPUS") {
                    "Campus only · ${drop.targetUniversity}"
                } else {
                    "All campuses"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LinearProgressIndicator(
                progress = if (drop.winnerCount <= 0) 0f else {
                    (drop.claimedCount.toFloat() / drop.winnerCount.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$remaining reward${if (remaining == 1) "" else "s"} remaining",
                style = MaterialTheme.typography.labelMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onComplete?.let {
                    Button(onClick = it, enabled = !busy && drop.status == "ACTIVE" && remaining > 0) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(actionIcon(drop.action), null, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(5.dp))
                        Text("Complete & earn")
                    }
                }
                onCancel?.let {
                    OutlinedButton(onClick = it, enabled = !busy) {
                        Text("Cancel Drop")
                    }
                }
            }
        }
    }
}

@Composable
private fun TopGiverCard(
    giver: TopGiver,
    formatter: NumberFormat,
    onFollow: (() -> Unit)?,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EmojiEvents, null)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(giver.name.ifBlank { "@${giver.username}" }, fontWeight = FontWeight.Bold)
                Text(
                    "${formatter.format(giver.coinsGiven)} coins given · ${giver.peopleRewarded} people · ${giver.dropsCompleted} Drops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onFollow != null) {
                OutlinedButton(onClick = onFollow) { Text("Follow") }
            } else {
                Text("Following", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OrganizeDropDialog(
    balance: Long,
    posts: List<DropTarget>,
    listings: List<DropTarget>,
    onDismiss: () -> Unit,
    onCreate: (
        target: DropTarget,
        action: String,
        reward: Int,
        winners: Int,
        audience: String,
        durationHours: Int,
    ) -> Unit,
) {
    var targetType by remember { mutableStateOf(BlinkDropTargetType.POST) }
    var selectedTarget by remember { mutableStateOf<DropTarget?>(null) }
    var action by remember { mutableStateOf(BlinkDropAction.LIKE.name) }
    var rewardText by remember { mutableStateOf("100") }
    var winnerText by remember { mutableStateOf("1") }
    var audience by remember { mutableStateOf(BlinkDropAudienceScope.ALL_CAMPUSES.name) }
    var duration by remember { mutableIntStateOf(24) }

    val availableTargets = when (targetType) {
        BlinkDropTargetType.POST -> posts.filter { it.type == "POST" }
        BlinkDropTargetType.REEL -> posts.filter { it.type == "REEL" }
        BlinkDropTargetType.LISTING -> listings
    }
    val actions = BlinkDropsPolicy.actionsFor(targetType)
    val reward = rewardText.toIntOrNull() ?: 0
    val winners = winnerText.toIntOrNull() ?: 0
    val total = reward.toLong() * winners.toLong()
    val valid = selectedTarget != null &&
        BlinkDropsPolicy.isRewardValid(reward) &&
        BlinkDropsPolicy.isWinnerCountValid(winners) &&
        total <= BlinkDropsPolicy.MAX_TOTAL_BUDGET &&
        total <= balance &&
        action in actions.map { it.name }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Organize a Drop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(
                                "Only followers who are eligible when you publish are included.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                item {
                    Text("1. Choose content", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        BlinkDropTargetType.entries.forEach { type ->
                            FilterChip(
                                selected = targetType == type,
                                onClick = {
                                    targetType = type
                                    selectedTarget = null
                                    action = BlinkDropsPolicy.actionsFor(type).first().name
                                },
                                label = { Text(targetLabel(type.name)) },
                            )
                        }
                    }
                }

                if (availableTargets.isEmpty()) {
                    item { EmptyCard("You do not have an eligible ${targetLabel(targetType.name).lowercase()} yet.") }
                } else {
                    items(availableTargets.take(20), key = { "drop-target-${it.id}" }) { target ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTarget = target },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedTarget?.id == target.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                                }
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(target.title.ifBlank { targetLabel(target.type) }, fontWeight = FontWeight.SemiBold)
                                if (target.subtitle.isNotBlank()) {
                                    Text(
                                        target.subtitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text("2. Required action", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        actions.forEach { value ->
                            FilterChip(
                                selected = action == value.name,
                                onClick = { action = value.name },
                                label = { Text(actionLabel(value.name)) },
                                leadingIcon = { Icon(actionIcon(value.name), null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                item {
                    Text("3. Reward", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rewardText,
                            onValueChange = { rewardText = it.filter(Char::isDigit).take(6) },
                            label = { Text("Coins per person") },
                            supportingText = { Text("Minimum 100 · multiples of 100") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = winnerText,
                            onValueChange = { winnerText = it.filter(Char::isDigit).take(3) },
                            label = { Text("People") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Total reserved: ${NumberFormat.getIntegerInstance(Locale.US).format(total)} coins · Balance: ${NumberFormat.getIntegerInstance(Locale.US).format(balance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (total > balance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Text("4. Audience", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = audience == BlinkDropAudienceScope.ALL_CAMPUSES.name,
                            onClick = { audience = BlinkDropAudienceScope.ALL_CAMPUSES.name },
                            label = { Text("All campuses") },
                            leadingIcon = { Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = audience == BlinkDropAudienceScope.MY_CAMPUS.name,
                            onClick = { audience = BlinkDropAudienceScope.MY_CAMPUS.name },
                            label = { Text("My campus") },
                            leadingIcon = { Icon(Icons.Default.School, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                item {
                    Text("5. Duration", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 6, 12, 24, 72, 168).forEach { hours ->
                            FilterChip(
                                selected = duration == hours,
                                onClick = { duration = hours },
                                label = {
                                    Text(
                                        when (hours) {
                                            24 -> "1d"
                                            72 -> "3d"
                                            168 -> "7d"
                                            else -> "${hours}h"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                item {
                    Divider()
                    Button(
                        onClick = {
                            val target = selectedTarget ?: return@Button
                            onCreate(target, action, reward, winners, audience, duration)
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reserve coins & publish Drop")
                    }
                    if (!valid) {
                        Text(
                            when {
                                selectedTarget == null -> "Choose content first."
                                !BlinkDropsPolicy.isRewardValid(reward) -> "Reward must be 100, 200, 300… coins per person."
                                !BlinkDropsPolicy.isWinnerCountValid(winners) -> "Choose between 1 and ${BlinkDropsPolicy.MAX_WINNERS} recipients."
                                total > BlinkDropsPolicy.MAX_TOTAL_BUDGET -> "This Drop is above the supported total budget."
                                total > balance -> "You do not have enough BLINK Coins."
                                else -> "Check the Drop details."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentDropDialog(
    reward: Int,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comment to earn $reward coins") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                label = { Text("Your comment") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Comment & claim")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
        )
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun actionLabel(value: String): String = when (value.uppercase()) {
    "LIKE" -> "Like post"
    "COMMENT" -> "Comment"
    "REPOST" -> "Repost"
    "SAVE_LISTING" -> "Save listing"
    else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun targetLabel(value: String): String = when (value.uppercase()) {
    "POST" -> "Post"
    "REEL" -> "Reel"
    "LISTING" -> "Listing"
    else -> value
}

private fun actionIcon(value: String): ImageVector = when (value.uppercase()) {
    "LIKE" -> Icons.Default.Favorite
    "COMMENT" -> Icons.Default.ChatBubble
    "REPOST" -> Icons.Default.Repeat
    "SAVE_LISTING" -> Icons.Default.Bookmark
    else -> Icons.Default.CardGiftcard
}

private fun JSONArray?.toTargets(): List<DropTarget> {
    if (this == null) return emptyList()
    return buildList {
        repeat(length()) { index ->
            optJSONObject(index)?.let { item ->
                add(
                    DropTarget(
                        id = item.optString("id"),
                        type = item.optString("type"),
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                    )
                )
            }
        }
    }
}

private fun JSONArray?.toDropItems(): List<DropItem> {
    if (this == null) return emptyList()
    return buildList {
        repeat(length()) { index ->
            optJSONObject(index)?.let { item ->
                add(
                    DropItem(
                        id = item.optString("id"),
                        creatorId = item.optString("creator_id"),
                        creatorUsername = item.optString("creator_username"),
                        creatorName = item.optString("creator_name"),
                        rewardPerUser = item.optInt("reward_per_user"),
                        winnerCount = item.optInt("winner_count"),
                        claimedCount = item.optInt("claimed_count"),
                        totalCoins = item.optLong("total_coins"),
                        action = item.optString("action"),
                        targetType = item.optString("target_type"),
                        targetTitle = item.optString("target_title"),
                        audienceScope = item.optString("audience_scope"),
                        targetUniversity = item.optString("target_university"),
                        status = item.optString("status"),
                        endsAt = item.optString("ends_at"),
                        eligible = item.optBoolean("eligible", false),
                    )
                )
            }
        }
    }
}

private fun JSONArray?.toTopGivers(): List<TopGiver> {
    if (this == null) return emptyList()
    return buildList {
        repeat(length()) { index ->
            optJSONObject(index)?.let { item ->
                add(
                    TopGiver(
                        creatorId = item.optString("creator_id"),
                        username = item.optString("username"),
                        name = item.optString("name"),
                        coinsGiven = item.optLong("coins_given"),
                        dropsCompleted = item.optInt("drops_completed"),
                        peopleRewarded = item.optInt("people_rewarded"),
                        following = item.optBoolean("is_following"),
                    )
                )
            }
        }
    }
}

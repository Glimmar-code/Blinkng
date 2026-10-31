package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.shared.BlinkDropRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
fun DesktopDropsScreen(state: DesktopAppState, initialDropId: String? = null) {
    val rpc = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var availablePage by remember { mutableStateOf<List<JSONObject>?>(null) }
    var minePage by remember { mutableStateOf<List<JSONObject>?>(null) }
    var availableMore by remember { mutableStateOf(false) }
    var mineMore by remember { mutableStateOf(false) }
    var pageBusy by remember { mutableStateOf(false) }
    var pendingCancel by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var selectedPage by remember { mutableIntStateOf(0) }
    var selectedGiveaway by remember { mutableStateOf<JSONObject?>(null) }
    var selectedTarget by remember { mutableStateOf<JSONObject?>(null) }
    var action by remember { mutableStateOf("LIKE") }
    var reward by remember { mutableStateOf("100") }
    var recipients by remember { mutableStateOf("1") }
    var audience by remember { mutableStateOf("MY_CAMPUS") }
    var hours by remember { mutableIntStateOf(24) }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var quotedRequest by remember { mutableStateOf<BlinkDropRequest?>(null) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    var pendingFingerprint by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<BlinkDropRequest?>(null) }
    var comments by remember { mutableStateOf<Map<String,String>>(emptyMap()) }

    suspend fun reload() {
        runCatching { rpc.getDropsState() }.onSuccess { data = it; error = null }
            .onFailure { error = it.message ?: "Unable to load BLINK Giveaway." }
        runCatching { rpc.getDropsPage("AVAILABLE",0) }.onSuccess {
            availablePage = it.optJSONArray("items").dropRows()
            availableMore = it.optBoolean("has_more")
        }
        runCatching { rpc.getDropsPage("MINE",0) }.onSuccess {
            minePage = it.optJSONArray("items").dropRows()
            mineMore = it.optBoolean("has_more")
        }
        loading = false
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { action(); reload() }.onFailure { error = it.message }
            busy = false
        }
    }
    LaunchedEffect(rpc) { while (isActive) { reload(); delay(30_000) } }
    LaunchedEffect(initialDropId) {
        val id = initialDropId?.takeIf(String::isNotBlank)
        if (id == null) {
            selectedGiveaway = null
        } else {
            runCatching { rpc.getDropById(id) }
                .onSuccess { root -> selectedGiveaway = root.optJSONObject("item") }
                .onFailure { error = it.message ?: "Couldn't open this Giveaway." }
            state.selectedGiveawayId = null
        }
    }
    val input = BlinkDropRequest(selectedTarget?.optString("type").orEmpty(), selectedTarget?.optString("id").orEmpty(),
        action, reward.toIntOrNull() ?: 0, recipients.toIntOrNull() ?: 0, audience, hours)
    val targets = data?.optJSONObject("targets")
    val choices = targets?.optJSONArray("posts").dropRows() + targets?.optJSONArray("listings").dropRows()
    val balance = data?.optLong("balance")

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BLINK GIVEAWAY", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = { perform { reload() } }) { Text("Refresh") }
            }
            Text(if (loading) "Loading Giveaways…" else balance?.let { "$it Blink Coins available" } ?: "Balance unavailable")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TabRow(selectedTabIndex = selectedPage) {
                Tab(selected = selectedPage == 0, onClick = { selectedPage = 0 }, text = { Text("Giveaways") })
                Tab(selected = selectedPage == 1, onClick = { selectedPage = 1 }, text = { Text("Most Givers") })
            }
        }
        if (selectedPage == 0 && selectedGiveaway != null) item {
            val selected = selectedGiveaway!!
            val isLive = selected.optString("status").equals("ACTIVE", ignoreCase = true)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isLive) "Selected BLINK Giveaway · LIVE" else "You missed this BLINK Giveaway",
                        fontWeight = FontWeight.Bold)
                    Text(selected.optString("target_title"), fontWeight = FontWeight.SemiBold)
                    Text("${selected.optLong("total_coins")} BLINK Coins · ${selected.optInt("reward_per_user")} each · ${selected.optInt("winner_count")} winners")
                    Text(if (isLive) "Open the active Giveaway below to complete its required action."
                        else "Follow the creator to be notified about the next Giveaway.")
                    if (!selected.optBoolean("is_following") && selected.optString("creator_id").isNotBlank()) {
                        TextButton(enabled = !busy, onClick = {
                            perform { rpc.followDropCreator(selected.optString("creator_id")) }
                            selected.put("is_following", true)
                        }) { Text("Follow creator") }
                    }
                }
            }
        }
        if (selectedPage == 0) item {
            Text("Create a Giveaway", fontWeight = FontWeight.Bold)
            Text("Anyone in your selected audience can join. Followers are notified when it starts.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(choices, key = { it.optString("id") }) { target ->
                    FilterChip(selected = selectedTarget?.optString("id") == target.optString("id"), enabled = !busy,
                        onClick = { selectedTarget = target; action = if (target.optString("type") == "LISTING") "SAVE_LISTING" else "LIKE" },
                        label = { Text(target.optString("title").ifBlank { target.optString("type") }.take(48)) })
                }
            }
            if (!loading && choices.isEmpty()) Text("Publish a post, Reel or listing to create a Giveaway.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (input.targetType == "LISTING") listOf("SAVE_LISTING") else listOf("LIKE","COMMENT","REPOST")).forEach { option ->
                    FilterChip(selected = action == option, enabled = !busy, onClick = { action = option }, label = { Text(option.replace('_',' ')) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = reward, onValueChange = { reward = it.filter(Char::isDigit).take(6) }, enabled = !busy,
                    label = { Text("Coins per winner (multiples of 100)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = recipients, onValueChange = { recipients = it.filter(Char::isDigit).take(3) }, enabled = !busy,
                    label = { Text("Recipients (1–500)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MY_CAMPUS","ALL_CAMPUSES").forEach { option ->
                    FilterChip(selected = audience == option, enabled = !busy, onClick = { audience = option }, label = { Text(option.replace('_',' ')) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(1,6,12,24,72,168)) { value ->
                    FilterChip(selected = hours == value, enabled = !busy, onClick = { hours = value }, label = { Text("$value hours") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = input.isValid() && !busy, onClick = {
                    val request = input
                    perform { quote = rpc.quoteDrop(request); quotedRequest = request }
                }) { Text("Preview eligibility and budget") }
                Button(enabled = input.isValid() && quotedRequest == input && quote != null && !busy &&
                    (quote?.optInt("maximum_possible_recipients", 0) ?: 0) > 0 &&
                    quote?.optLong("total_budget",Long.MAX_VALUE)?.let { it <= (balance ?: -1) } == true,
                    onClick = { confirm = input }) { Text("Publish Giveaway") }
            }
            if (quotedRequest == input) quote?.let {
                Text("${it.optLong("total_budget")} BLINK Coins committed · ${it.optInt("eligible_followers")} followers notified · " +
                    "${it.optInt("maximum_possible_recipients")} possible winners · " +
                    "${it.optLong("remaining_balance_after_reserve")} coins remaining")
                if (it.optInt("eligible_followers") < 1)
                    Text("No followers will be notified, but users in your selected audience can still discover and join.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val analytics = data?.optJSONObject("analytics")
        if (selectedPage == 0 && analytics != null) item {
            Text("Your results", fontWeight = FontWeight.Bold)
            Text("${analytics.optLong("distributed")} coins rewarded · ${analytics.optInt("recipients")} recipients · ${analytics.optLong("refunded")} coins refunded")
        }
        if (selectedPage == 0) item { Text("Active Giveaways", fontWeight = FontWeight.Bold) }
        val available = availablePage ?: data?.optJSONArray("active_drops").dropRows()
        if (selectedPage == 0 && !loading && available.isEmpty()) item { Text("No active Giveaways right now.") }
        if (selectedPage == 0) items(available, key = { "available-" + it.optString("id") }) { drop ->
            val id = drop.optString("id")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(drop.optString("target_title"), fontWeight = FontWeight.Bold)
                    Text("${drop.optInt("reward_per_user")} coins · ${drop.optString("action")} · ${drop.optInt("claimed_count")}/${drop.optInt("winner_count")} claimed")
                    if (drop.optString("action") == "COMMENT") OutlinedTextField(value = comments[id].orEmpty(), onValueChange = {
                        comments = comments + (id to it)
                    }, label = { Text("Your comment") }, enabled = !busy)
                    Button(enabled = !busy && (drop.optString("action") != "COMMENT" || comments[id].orEmpty().isNotBlank()),
                        onClick = { val comment = comments[id]; perform { rpc.completeDropAction(id, comment) } }) { Text("Complete action and claim") }
                }
            }
        }
        if (selectedPage == 0 && availableMore) item {
            OutlinedButton(enabled = !pageBusy, onClick = {
                pageBusy = true
                scope.launch {
                    runCatching { rpc.getDropsPage("AVAILABLE",available.size) }
                        .onSuccess {
                            availablePage = (available + it.optJSONArray("items").dropRows()).distinctBy { row -> row.optString("id") }
                            availableMore = it.optBoolean("has_more")
                        }.onFailure { error = it.message ?: "Could not load more Giveaways." }
                    pageBusy = false
                }
            }) { Text(if (pageBusy) "Loading…" else "Load more active Giveaways") }
        }
        if (selectedPage == 0) item { Text("Your Giveaways", fontWeight = FontWeight.Bold) }
        val mine = minePage ?: data?.optJSONArray("my_drops").dropRows()
        if (selectedPage == 0) items(mine, key = { "mine-" + it.optString("id") }) { drop ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(drop.optString("target_title"), fontWeight = FontWeight.Bold)
                    Text("${drop.optString("status")} · ${drop.optInt("claimed_count")}/${drop.optInt("winner_count")} claimed")
                    Text("${drop.optLong("coin_distributed")} coins rewarded · ${drop.optLong("coin_refunded")} refunded")
                    if (drop.optString("status") == "ACTIVE") TextButton(enabled = !busy,
                        onClick = { pendingCancel = drop.optString("id") }) { Text("End Giveaway (no refund)") }
                }
            }
        }
        if (selectedPage == 0 && mineMore) item {
            OutlinedButton(enabled = !pageBusy, onClick = {
                pageBusy = true
                scope.launch {
                    runCatching { rpc.getDropsPage("MINE",mine.size) }
                        .onSuccess {
                            minePage = (mine + it.optJSONArray("items").dropRows()).distinctBy { row -> row.optString("id") }
                            mineMore = it.optBoolean("has_more")
                        }.onFailure { error = it.message ?: "Could not load more history." }
                    pageBusy = false
                }
            }) { Text(if (pageBusy) "Loading…" else "Load more Giveaway history") }
        }
        val givers = data?.optJSONArray("top_givers").dropRows().take(20)
        if (selectedPage == 1) {
            item {
                Text("Most Givers · Top 20", fontWeight = FontWeight.Bold)
                Text("Ranked by total BLINK Coins committed to published Giveaways.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (givers.isEmpty()) item { Text("No published Giveaways yet. Create the first one.") }
            itemsIndexed(givers, key = { _, giver -> "giver-" + giver.optString("creator_id") }) { index, giver ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("#${index + 1}  @${giver.optString("username")} · ${giver.optLong("coins_spent")} BLINK Coins spent · ${giver.optInt("giveaways_created")} Giveaways",
                        modifier = Modifier.weight(1f))
                    if (!giver.optBoolean("is_following") && giver.optString("creator_id") != state.session?.userId)
                        TextButton(enabled = !busy, onClick = { perform { rpc.followDropCreator(giver.optString("creator_id")) } }) { Text("Follow") }
                }
            }
        }
        val receipts = data?.optJSONArray("receipts").dropRows()
        if (selectedPage == 0 && receipts.isNotEmpty()) item { Text("Giveaway receipts", fontWeight = FontWeight.Bold) }
        if (selectedPage == 0) items(receipts, key = { "receipt-" + it.optString("id") }) { receipt ->
            Text("${receipt.optString("item_name")} · ${receipt.optLong("amount")} coins · Balance ${receipt.optLong("balance_after")}")
        }
    }
    pendingCancel?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingCancel = null },
            title = { Text("End this Giveaway?") },
            text = { Text("This stops new entries. The amount committed at publication is non-refundable.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    perform { rpc.cancelDrop(id); pendingCancel = null }
                }) { Text("Confirm cancellation") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingCancel = null }) { Text("Keep Giveaway") }
            },
        )
    }
    confirm?.let { request ->
        AlertDialog(onDismissRequest = { if (!busy) confirm = null }, title = { Text("Publish this Giveaway?") },
            text = { Text("${request.reward.toLong()*request.recipients} BLINK Coins committed for ${request.recipients} winners. This amount is non-refundable after publication.") },
            confirmButton = { Button(enabled = !busy, onClick = {
                val fingerprint = request.fingerprint()
                if (pendingId == null || pendingFingerprint != fingerprint) { pendingId = UUID.randomUUID().toString(); pendingFingerprint = fingerprint }
                val requestId = pendingId.orEmpty()
                perform { rpc.createDrop(request, requestId); pendingId = null; confirm = null; quote = null }
            }) { Text("Confirm and publish") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirm = null }) { Text("Cancel") } })
    }
}

private fun JSONArray?.dropRows(): List<JSONObject> = if (this == null) emptyList()
    else (0 until length()).mapNotNull { optJSONObject(it) }

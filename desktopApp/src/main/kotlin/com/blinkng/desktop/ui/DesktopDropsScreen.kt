package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
fun DesktopDropsScreen(state: DesktopAppState) {
    val rpc = remember(state.client) { DesktopRpcActions(state.client) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
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
            .onFailure { error = it.message ?: "Unable to load Drops." }
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
    val input = BlinkDropRequest(selectedTarget?.optString("type").orEmpty(), selectedTarget?.optString("id").orEmpty(),
        action, reward.toIntOrNull() ?: 0, recipients.toIntOrNull() ?: 0, audience, hours)
    val targets = data?.optJSONObject("targets")
    val choices = targets?.optJSONArray("posts").dropRows() + targets?.optJSONArray("listings").dropRows()
    val balance = data?.optLong("balance")

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BLINK Drops", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = { perform { reload() } }) { Text("Refresh") }
            }
            Text(if (loading) "Loading Drops…" else balance?.let { "$it Blink Coins available" } ?: "Balance unavailable")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text("Create a Drop", fontWeight = FontWeight.Bold)
            Text("Reward eligible followers for an action on your post, Reel or listing.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(choices, key = { it.optString("id") }) { target ->
                    FilterChip(selected = selectedTarget?.optString("id") == target.optString("id"), enabled = !busy,
                        onClick = { selectedTarget = target; action = if (target.optString("type") == "LISTING") "SAVE_LISTING" else "LIKE" },
                        label = { Text(target.optString("title").ifBlank { target.optString("type") }.take(48)) })
                }
            }
            if (!loading && choices.isEmpty()) Text("Publish a post, Reel or listing to create a Drop.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (input.targetType == "LISTING") listOf("SAVE_LISTING") else listOf("LIKE","COMMENT","REPOST")).forEach { option ->
                    FilterChip(selected = action == option, enabled = !busy, onClick = { action = option }, label = { Text(option.replace('_',' ')) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = reward, onValueChange = { reward = it.filter(Char::isDigit).take(6) }, enabled = !busy,
                    label = { Text("Coins per follower (multiples of 100)") }, singleLine = true, modifier = Modifier.weight(1f))
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
                    quote?.optLong("total_budget",Long.MAX_VALUE)?.let { it <= (balance ?: -1) } == true,
                    onClick = { confirm = input }) { Text("Create Drop") }
            }
            if (quotedRequest == input) quote?.let {
                Text("${it.optLong("total_budget")} coins reserved · ${it.optInt("eligible_followers")} eligible followers · " +
                    "${it.optLong("remaining_balance_after_reserve")} coins after reserve")
            }
        }
        val analytics = data?.optJSONObject("analytics")
        if (analytics != null) item {
            Text("Your results", fontWeight = FontWeight.Bold)
            Text("${analytics.optLong("distributed")} coins rewarded · ${analytics.optInt("recipients")} recipients · ${analytics.optLong("refunded")} coins refunded")
        }
        item { Text("Available Drops", fontWeight = FontWeight.Bold) }
        val available = data?.optJSONArray("active_drops").dropRows()
        if (!loading && available.isEmpty()) item { Text("No eligible Drops available right now.") }
        items(available, key = { "available-" + it.optString("id") }) { drop ->
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
        item { Text("Your Drop history", fontWeight = FontWeight.Bold) }
        items(data?.optJSONArray("my_drops").dropRows(), key = { "mine-" + it.optString("id") }) { drop ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(drop.optString("target_title"), fontWeight = FontWeight.Bold)
                    Text("${drop.optString("status")} · ${drop.optInt("claimed_count")}/${drop.optInt("winner_count")} claimed")
                    Text("${drop.optLong("coin_distributed")} coins rewarded · ${drop.optLong("coin_refunded")} refunded")
                    if (drop.optString("status") == "ACTIVE") TextButton(enabled = !busy,
                        onClick = { perform { rpc.cancelDrop(drop.optString("id")) } }) { Text("End Drop and refund unused coins") }
                }
            }
        }
        val givers = data?.optJSONArray("top_givers").dropRows()
        if (givers.isNotEmpty()) item { Text("Top givers", fontWeight = FontWeight.Bold) }
        items(givers, key = { "giver-" + it.optString("creator_id") }) { giver ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("@${giver.optString("username")} · ${giver.optLong("coins_given")} coins given", modifier = Modifier.weight(1f))
                if (!giver.optBoolean("is_following") && giver.optString("creator_id") != state.session?.userId)
                    TextButton(enabled = !busy, onClick = { perform { rpc.followDropCreator(giver.optString("creator_id")) } }) { Text("Follow") }
            }
        }
        val receipts = data?.optJSONArray("receipts").dropRows()
        if (receipts.isNotEmpty()) item { Text("Growth receipts", fontWeight = FontWeight.Bold) }
        items(receipts, key = { "receipt-" + it.optString("id") }) { receipt ->
            Text("${receipt.optString("item_name")} · ${receipt.optLong("amount")} coins · Balance ${receipt.optLong("balance_after")}")
        }
    }
    confirm?.let { request ->
        AlertDialog(onDismissRequest = { if (!busy) confirm = null }, title = { Text("Reserve coins for this Drop?") },
            text = { Text("${request.reward.toLong()*request.recipients} coins for ${request.recipients} followers. Unused coins are refunded when the Drop ends.") },
            confirmButton = { Button(enabled = !busy, onClick = {
                val fingerprint = request.fingerprint()
                if (pendingId == null || pendingFingerprint != fingerprint) { pendingId = UUID.randomUUID().toString(); pendingFingerprint = fingerprint }
                val requestId = pendingId.orEmpty()
                perform { rpc.createDrop(request, requestId); pendingId = null; confirm = null; quote = null }
            }) { Text("Reserve and start") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirm = null }) { Text("Cancel") } })
    }
}

private fun JSONArray?.dropRows(): List<JSONObject> = if (this == null) emptyList()
    else (0 until length()).mapNotNull { optJSONObject(it) }

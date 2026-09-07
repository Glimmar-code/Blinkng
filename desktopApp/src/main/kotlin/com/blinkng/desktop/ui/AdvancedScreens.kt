package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopInventoryItem
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.desktop.data.DesktopStoreItem
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun StoreProScreen(state: DesktopAppState) {
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    var catalog by remember { mutableStateOf<List<DesktopStoreItem>>(emptyList()) }
    var inventory by remember { mutableStateOf<List<DesktopInventoryItem>>(emptyList()) }
    var balance by remember { mutableStateOf(0L) }
    var serverState by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        val store = runCatching { state.client.fetchStore() }.getOrDefault(emptyList<DesktopStoreItem>() to emptyList())
        catalog = store.first
        inventory = store.second
        balance = runCatching { state.client.fetchCoinBalance() }.getOrDefault(0L)
        serverState = runCatching { actions.getStoreState() }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenHeader("Blink Store", "$balance Blink Coins available")
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Refresh") }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        serverState?.let { stateJson ->
            item {
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("VIP & wallet", fontWeight = FontWeight.Bold)
                        stateJson.optJSONObject("vip")?.let { vip ->
                            Text(if (vip.optBoolean("active")) "VIP active" else "VIP inactive")
                            vip.optString("expires_at").takeIf(String::isNotBlank)?.let { Text("Expires: $it", fontSize = 12.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { actions.renewVip() }
                                        .onSuccess { message = it.optString("message").ifBlank { "VIP renewed." }; error = null; reload() }
                                        .onFailure { error = it.message }
                                }
                            }) { Text("Renew VIP") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { actions.setVipAutoRenew(true) }
                                        .onSuccess { message = "VIP auto-renew enabled."; error = null; reload() }
                                        .onFailure { error = it.message }
                                }
                            }) { Text("Auto-renew on") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { actions.setVipAutoRenew(false) }
                                        .onSuccess { message = "VIP auto-renew disabled."; error = null; reload() }
                                        .onFailure { error = it.message }
                                }
                            }) { Text("Auto-renew off") }
                        }
                    }
                }
            }
        }

        item { Text("Store", fontWeight = FontWeight.Black, fontSize = 19.sp) }
        if (loading) item { Text("Loading store…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(catalog, key = { "pro-store-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val detail = buildList {
                            add(item.category)
                            item.durationSeconds?.let { add("${it / 86_400} days") }
                            if (item.boostMultipliers.isNotEmpty()) add("boost ${item.boostMultipliers.joinToString("/")}x")
                            if (item.vipOnly) add("VIP")
                        }.joinToString(" • ")
                        if (detail.isNotBlank()) Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${item.price} coins", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val multiplier = item.boostMultipliers.firstOrNull()
                                runCatching { actions.purchaseStoreItem(item.id, 1, multiplier) }
                                    .onSuccess { result ->
                                        message = result.optString("message").ifBlank { "${item.name} purchased." }
                                        error = null
                                        reload()
                                    }
                                    .onFailure { error = it.message }
                            }
                        },
                        enabled = balance >= item.price,
                    ) { Text("Buy") }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("My purchases", fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }
        if (!loading && inventory.isEmpty()) item { Text("No purchased items yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(inventory, key = { "pro-inventory-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(15.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(item.catalogId, fontWeight = FontWeight.SemiBold)
                        Text("${item.status} • qty ${item.quantity}", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        item.activatedAt?.let { Text("Activated $it", fontSize = 11.sp) }
                        item.expiresAt?.let { Text("Expires $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminProScreen(state: DesktopAppState) {
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var sections by remember { mutableStateOf(JSONArray()) }
    var query by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val capability = state.adminCapability

    suspend fun loadDashboard() {
        if (!capability.allowed) return
        loading = true
        runCatching { actions.adminDashboard() }
            .onSuccess { stats = it.raw; sections = it.sections; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(capability.allowed) { loadDashboard() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Admin", "Server-authorized Blinkng administration") }
        if (!capability.allowed) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "This account does not have server-authorized admin access.",
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Access verified", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        Text("Role: ${capability.role ?: "admin"}${if (capability.isOwner) " • Owner" else ""}")
                    }
                    OutlinedButton(onClick = { scope.launch { loadDashboard() } }) { Text("Refresh") }
                }
            }
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (loading) item { Text("Loading admin dashboard…") }

        stats?.let { snapshot ->
            item {
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dashboard", fontWeight = FontWeight.Black, fontSize = 19.sp)
                        compactJsonRows(snapshot).forEach { (key, value) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(key.replace('_', ' ').replaceFirstChar(Char::uppercase), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (sections.length() > 0) {
            item { Text("Admin sections", fontWeight = FontWeight.Black, fontSize = 19.sp) }
            items((0 until sections.length()).toList(), key = { "admin-section-$it" }) { index ->
                val section = sections.optJSONObject(index)
                Surface(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(section?.optString("title")?.ifBlank { section.optString("name") } ?: "Admin section", fontWeight = FontWeight.SemiBold)
                        section?.optString("description")?.takeIf(String::isNotBlank)?.let {
                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Text("Global admin search", fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(Modifier.padding(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("User ID, name, username, post link or reference") },
                )
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { actions.adminGlobalSearch(query) }
                                .onSuccess { searchResult = it; error = null }
                                .onFailure { error = it.message }
                        }
                    },
                    enabled = query.isNotBlank(),
                ) { Text("Search") }
            }
        }

        searchResult?.let { result ->
            item {
                Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Search results", fontWeight = FontWeight.Bold)
                        Text(result.toString(2).take(12_000), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun compactJsonRows(json: JSONObject): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val keys = json.keys()
    while (keys.hasNext() && result.size < 18) {
        val key = keys.next()
        val value = json.opt(key)
        if (value is Number || value is Boolean || value is String) {
            result += key to value.toString()
        }
    }
    return result
}

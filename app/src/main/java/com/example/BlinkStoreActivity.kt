package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AccountSwitcherActivity
import com.example.data.models.BlinkInventoryStatus
import com.example.data.models.BlinkStoreCatalog
import com.example.data.models.BlinkStoreItem
import com.example.data.models.BlinkStoreItemType
import com.example.data.models.BlinkStoreTarget
import com.example.data.supabase.BlinkEconomyService
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class BlinkStoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlinkTheme {
                BlinkStoreRoute(onClose = { closeStore() })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun closeStore() {
        finish()
        overridePendingTransition(R.anim.blink_stay, R.anim.blink_slide_out_right)
    }
}

private enum class BlinkStoreTab(val label: String) {
    STORE("Store"), VAULT("Vault"), VIP("VIP"), HISTORY("History"), MORE("More")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlinkStoreRoute(onClose: () -> Unit) {
    val service = remember { BlinkEconomyService() }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(JSONObject()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(BlinkStoreTab.STORE) }
    var purchaseItem by remember { mutableStateOf<BlinkStoreItem?>(null) }
    var activateRow by remember { mutableStateOf<JSONObject?>(null) }
    var targets by remember { mutableStateOf(JSONObject()) }

    suspend fun refresh() {
        loading = true
        service.state()
            .onSuccess { snapshot = it }
            .onFailure { message = it.message ?: "Unable to load Blink Store." }
        loading = false
    }

    fun runAction(successMessage: String, block: suspend () -> Result<JSONObject>) {
        scope.launch {
            working = true
            block().onSuccess {
                message = successMessage
                refresh()
            }.onFailure { message = it.message ?: "That action could not be completed." }
            working = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(snapshot.optJSONObject("vip")?.optBoolean("active") == true) {
        while (true) {
            delay(60_000)
            if (!working) service.state().onSuccess { snapshot = it }
        }
    }

    val balance = snapshot.optLong("balance", 0L)
    val inventory = snapshot.optJSONArray("inventory").objects()
    val vip = snapshot.optJSONObject("vip") ?: JSONObject()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blink Store", fontWeight = FontWeight.Bold)
                        Text("🪙 $balance Blink Coins", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp)
        ) {
                    BlinkStoreTab.entries.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f).clickable { tab = item }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = tabIcon(item),
                                contentDescription = item.label,
                                tint = if (tab == item) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(item.label, fontSize = 10.sp, color = if (tab == item) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && snapshot.length() == 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> when (tab) {
                    BlinkStoreTab.STORE -> StoreTab(snapshot, inventory, vip, onBuy = { purchaseItem = it })
                    BlinkStoreTab.VAULT -> VaultTab(
                        inventory = inventory,
                        snapshot = snapshot,
                        onUse = { row ->
                            val item = BlinkStoreCatalog.items.firstOrNull { it.id == row.optString("catalog_id") }
                            if (item == null) return@VaultTab
                            if (item.type == BlinkStoreItemType.PERMANENT) {
                                val equipped = snapshot.optJSONArray("equipped").objects().any { it.optString("catalog_id") == item.id }
                                runAction(if (equipped) "Item removed." else "Item applied.") {
                                    service.equip(row.optString("id"), item.id, !equipped)
                                }
                            } else if (item.type == BlinkStoreItemType.CONTENT_SPECIFIC || item.id == "digital_gift" || item.id == "story_highlight") {
                                scope.launch {
                                    working = true
                                    service.boostableContent().onSuccess { targets = it }.onFailure { message = it.message }
                                    working = false
                                    activateRow = row
                                }
                            } else {
                                runAction("${item.name} activated.") { service.activate(row.optString("id")) }
                            }
                        }
                    )
                    BlinkStoreTab.VIP -> VipTab(
                        vip = vip,
                        balance = balance,
                        working = working,
                        onClaim = { benefit -> runAction("VIP benefit added to your Vault.") { service.claimVip(benefit) } },
                        onRenew = { runAction("Blink VIP extended by 10 days.") { service.renewVip() } },
                        onAutoRenew = { enabled -> runAction(if (enabled) "VIP auto-renew enabled." else "VIP auto-renew disabled.") { service.setAutoRenew(enabled) } },
                        onGift = { username -> runAction("Blink VIP gift sent to @$username.") { service.giftVip(username) } },
                        onBuyVip = { purchaseItem = BlinkStoreCatalog.items.first { it.id == "blink_vip_10d" } }
                    )
                    BlinkStoreTab.HISTORY -> HistoryTab(snapshot.optJSONArray("transactions").objects())
                    BlinkStoreTab.MORE -> MoreTab(onClose)
                }
            }

            if (working) {
                Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .18f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    purchaseItem?.let { item ->
        PurchaseDialog(
            item = item,
            balance = balance,
            vipActive = vip.optBoolean("active", false),
            onDismiss = { purchaseItem = null },
            onConfirm = { quantity, multiplier ->
                purchaseItem = null
                runAction("${item.name} added to your Vault.") { service.purchase(item.id, quantity, multiplier) }
            }
        )
    }

    activateRow?.let { row ->
        val item = BlinkStoreCatalog.items.firstOrNull { it.id == row.optString("catalog_id") }
        if (item != null) {
            TargetDialog(
                item = item,
                targets = targets,
                onDismiss = { activateRow = null },
                onSelect = { targetId ->
                    activateRow = null
                    runAction("${item.name} activated.") { service.activate(row.optString("id"), targetId) }
                },
                onDigitalGift = { username, giftMessage ->
                    activateRow = null
                    runAction("Digital gift sent to @${username.trim().removePrefix("@")}.") {
                        service.sendDigitalGift(row.optString("id"), username, giftMessage)
                    }
                }
            )
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { Button(onClick = { message = null }) { Text("OK") } },
            title = { Text("Blink Store") },
            text = { Text(text) }
        )
    }
}

@Composable
private fun StoreTab(
    snapshot: JSONObject,
    inventory: List<JSONObject>,
    vip: JSONObject,
    onBuy: (BlinkStoreItem) -> Unit
) {
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + BlinkStoreCatalog.items.map { it.category }.distinct()
    val items = BlinkStoreCatalog.items.filter { category == "All" || it.category == category }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Spend your Blink Coins", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Purchases go to your Vault first. Timers start only when you activate them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vip.optBoolean("active", false)) {
                    Spacer(Modifier.height(10.dp))
                    AssistChip(onClick = {}, label = { Text("👑 VIP active • 10% Store discount") })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                items(categories) { name ->
                    FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                }
            }
        }
        items(items, key = { it.id }) { item ->
            val owned = item.type == BlinkStoreItemType.PERMANENT && inventory.any { it.optString("catalog_id") == item.id && it.optString("status") == "PERMANENT" }
            val vipLocked = item.vipOnly && !vip.optBoolean("active", false)
            StoreItemCard(item, owned, vipLocked, vip.optBoolean("active", false), onBuy)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StoreItemCard(item: BlinkStoreItem, owned: Boolean, vipLocked: Boolean, vipActive: Boolean, onBuy: (BlinkStoreItem) -> Unit) {
    val displayPrice = if (vipActive) max(0, (item.price * .9).toInt()) else item.price
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = BlinkPink.copy(alpha = .12f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(storeIcon(item), null, tint = BlinkPink) }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (item.vipOnly) Text("👑", fontSize = 15.sp)
                }
                Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🪙 $displayPrice", fontWeight = FontWeight.Bold)
                    Text(itemTypeLabel(item), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.boostMultipliers.isNotEmpty()) Text(item.boostMultipliers.joinToString("/") { "${it}×" }, fontSize = 11.sp, color = BlinkPink)
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { onBuy(item) }, enabled = !owned && !vipLocked) {
                Text(if (owned) "Owned" else if (vipLocked) "VIP" else "Buy")
            }
        }
    }
}

@Composable
private fun VaultTab(inventory: List<JSONObject>, snapshot: JSONObject, onUse: (JSONObject) -> Unit) {
    var filter by remember { mutableStateOf("AVAILABLE") }
    val filters = listOf("AVAILABLE", "ACTIVE", "PERMANENT", "USED", "EXPIRED")
    val rows = inventory.filter { it.optString("status") == filter }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Blink Vault", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Everything you buy is stored here until you use, activate or apply it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filters) { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name.lowercase().replaceFirstChar(Char::uppercase)) }) }
            }
        }
        if (filter == "ACTIVE") {
            val boosts = snapshot.optJSONArray("active_boosts").objects()
            if (boosts.isNotEmpty()) {
                item { Text("Active boosts", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(boosts, key = { it.optString("id") }) { BoostCard(it) }
            }
        }
        if (rows.isEmpty()) {
            item { Text("Nothing here yet.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(rows, key = { it.optString("id") }) { row ->
                val item = BlinkStoreCatalog.items.firstOrNull { it.id == row.optString("catalog_id") }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item?.let(::storeIcon) ?: Icons.Filled.Apps, null, tint = BlinkPink, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item?.name ?: row.optString("name"), fontWeight = FontWeight.Bold)
                            Text("${row.optString("status").lowercase().replaceFirstChar(Char::uppercase)} • Qty ${row.optInt("quantity", 1)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            row.stringOrNull("expires_at")?.let { Text("Expires: ${shortDate(it)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (row.has("boost_multiplier") && !row.isNull("boost_multiplier")) Text("${row.optInt("boost_multiplier")}× boost", color = BlinkPink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        if (row.optString("status") in listOf("AVAILABLE", "PERMANENT")) {
                            Button(onClick = { onUse(row) }) { Text(if (row.optString("status") == "PERMANENT") "Apply" else "Use") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BoostCard(row: JSONObject) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), border = BorderStroke(1.dp, BlinkPink.copy(alpha = .35f))) {
        Column(Modifier.padding(14.dp)) {
            Text("${row.optInt("multiplier", 1)}× ${row.optString("content_type").lowercase().replaceFirstChar(Char::uppercase)} Boost", fontWeight = FontWeight.Bold)
            Text("Ends ${shortDate(row.optString("ends_at"))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Extra impressions", row.optLong("extra_impressions"))
                Metric("Profile visits", row.optLong("profile_visits"))
                Metric("Followers", row.optLong("followers_attributed"))
            }
            Text("Boost changes distribution opportunity only. It never creates fake likes, views, comments or followers.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VipTab(
    vip: JSONObject,
    balance: Long,
    working: Boolean,
    onClaim: (String) -> Unit,
    onRenew: () -> Unit,
    onAutoRenew: (Boolean) -> Unit,
    onGift: (String) -> Unit,
    onBuyVip: () -> Unit
) {
    var giftUser by remember { mutableStateOf("") }
    val active = vip.optBoolean("active", false)
    val perks = remember { vipPerks }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = BlinkPink.copy(alpha = .10f)),
                border = BorderStroke(1.dp, BlinkPink.copy(alpha = .35f))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("👑 Blink VIP — 10 Days", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    if (active) {
                        Text("Active • ${durationText(vip.optLong("remaining_seconds", 0))} remaining", fontWeight = FontWeight.SemiBold, color = BlinkPink)
                        vip.stringOrNull("expires_at")?.let { Text("Expires ${shortDate(it)}", fontSize = 12.sp) }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-renew", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Switch(checked = vip.optBoolean("auto_renew", false), onCheckedChange = onAutoRenew, enabled = !working)
                        }
                        Button(onClick = onRenew, enabled = !working) { Text("Renew +10 days") }
                    } else {
                        Text("VIP starts only when you activate it from your Vault. Auto-renew is OFF by default.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onBuyVip, enabled = balance >= 350 && !working) { Text("Buy for 🪙 350") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Passes completed: ${vip.optInt("completed_passes", 0)} • Cumulative VIP days: ${vip.optInt("cumulative_vip_days", 0)}", fontSize = 12.sp)
                }
            }
        }
        if (active) {
            item { Text("Claimable benefits", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            item { VipClaimRow("Daily VIP coin bonus", if (vip.optBoolean("daily_coin_bonus_claimed")) 0 else 1, "daily_coin_bonus", onClaim) }
            item { VipClaimRow("2× Post Boost", vip.optInt("post_boosts_2x"), "post_boost_2x", onClaim) }
            item { VipClaimRow("2× Reel Boost", vip.optInt("reel_boosts_2x"), "reel_boost_2x", onClaim) }
            item { VipClaimRow("Profile Spotlight", vip.optInt("profile_spotlights"), "profile_spotlight", onClaim) }
            item { VipClaimRow("Post Spotlight", vip.optInt("post_spotlights"), "post_spotlight", onClaim) }
            item { VipClaimRow("Reel Spotlight", vip.optInt("reel_spotlights"), "reel_spotlight", onClaim) }
            item { VipClaimRow("Marketplace Highlight", vip.optInt("marketplace_highlights"), "marketplace_highlight", onClaim) }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gift Blink VIP", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("The recipient receives a 10-day pass in their Vault and chooses when to activate it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = giftUser, onValueChange = { giftUser = it }, label = { Text("Blink username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onGift(giftUser) }, enabled = giftUser.isNotBlank() && !working) { Text("Gift VIP") }
                }
            }
        }
        item { Text("VIP benefits", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(perks) { perk ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.CheckCircle, null, tint = BlinkPink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(perk, fontSize = 13.sp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VipClaimRow(title: String, remaining: Int, key: String, onClaim: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("$remaining left", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            Button(onClick = { onClaim(key) }, enabled = remaining > 0) { Text("Claim") }
        }
    }
}

@Composable
private fun HistoryTab(rows: List<JSONObject>) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Purchase History", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your Blink Coin receipts, rewards and VIP renewals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(rows, key = { it.optString("id") }) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, tint = BlinkPink)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.optString("item_name"), fontWeight = FontWeight.SemiBold)
                    Text("${row.optString("kind").replace('_', ' ')} • ${shortDate(row.optString("created_at"))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val amount = row.optLong("amount")
                Text(if (amount > 0) "+🪙 $amount" else "-🪙 ${-amount}", fontWeight = FontWeight.Bold, color = if (amount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MoreTab(onClose: () -> Unit) {
    val context = LocalContext.current
    fun professional(section: String) {
        context.startActivity(Intent(context, ProfessionalCenterActivity::class.java).putExtra("section", section))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("More", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your existing Blink tools and settings remain available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { MoreRoute("Back to Blink", Icons.Filled.Apps) { onClose() } }
        item { MoreRoute("Scheduled posts", Icons.Outlined.EmojiEvents) { professional("scheduled") } }
        item { MoreRoute("Privacy & DM settings", Icons.Filled.Person) { professional("privacy") } }
        item { MoreRoute("Safety & blocked accounts", Icons.Filled.Notifications) { professional("safety") } }
        item { MoreRoute("Login & account security", Icons.Outlined.Verified) { professional("account") } }
        item { MoreRoute("Marketplace tools", Icons.Filled.Storefront) { professional("market") } }
        item { MoreRoute("Study & group center", Icons.Filled.ChatBubble) { professional("groups") } }
        item { MoreRoute("Switch account", Icons.Filled.Person) { context.startActivity(Intent(context, AccountSwitcherActivity::class.java)) } }
        item { MoreRoute("Admin control center", Icons.Outlined.Verified) { context.startActivity(Intent(context, AdminControlCenterActivity::class.java)) } }
    }
}

@Composable
private fun MoreRoute(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BlinkPink)
        Spacer(Modifier.size(14.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PurchaseDialog(item: BlinkStoreItem, balance: Long, vipActive: Boolean, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var quantity by remember { mutableIntStateOf(1) }
    var multiplier by remember { mutableIntStateOf(item.boostMultipliers.firstOrNull() ?: 1) }
    val unit = BlinkStoreCatalog.priceFor(item, multiplier).let { if (vipActive) max(0, (it * .9).toInt()) else it }
    val total = unit * quantity
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.description)
                Text("Type: ${itemTypeLabel(item)}")
                item.durationSeconds?.let { Text("Duration: ${durationText(it)} after activation") }
                if (item.target != BlinkStoreTarget.NONE) Text("Applies to: ${item.target.name.lowercase().replaceFirstChar(Char::uppercase)}")
                if (item.boostMultipliers.isNotEmpty()) {
                    Text("Boost strength", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.boostMultipliers.forEach { value -> FilterChip(selected = multiplier == value, onClick = { multiplier = value }, label = { Text("${value}×") }) }
                    }
                }
                if (item.stackable && item.type != BlinkStoreItemType.PERMANENT) {
                    Text("Quantity", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { quantity = max(1, quantity - 1) }) { Text("−") }
                        Text(quantity.toString(), fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { quantity = (quantity + 1).coerceAtMost(20) }) { Text("+") }
                    }
                }
                Text("🪙 $total", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (balance < total) Text("Need ${total - balance} more Blink Coins", color = MaterialTheme.colorScheme.error)
                Text("This purchase will be stored in Blink Vault. It will not activate automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(quantity, multiplier) }, enabled = balance >= total) { Text("Buy") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TargetDialog(
    item: BlinkStoreItem,
    targets: JSONObject,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDigitalGift: (String, String) -> Unit
) {
    var recipientUsername by remember(item.id) { mutableStateOf("") }
    var giftMessage by remember(item.id) { mutableStateOf("") }
    val key = when (item.target) {
        BlinkStoreTarget.POST, BlinkStoreTarget.REEL -> "posts"
        BlinkStoreTarget.MARKETPLACE -> "market"
        BlinkStoreTarget.COMMENT -> "comments"
        else -> ""
    }
    val rows = targets.optJSONArray(key).objects().filter {
        when (item.target) {
            BlinkStoreTarget.POST -> it.optString("type") == "POST"
            BlinkStoreTarget.REEL -> it.optString("type") == "REEL"
            else -> true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ${item.name}") },
        text = {
            if (item.id == "digital_gift") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send this Vault gift directly to another Blink account.")
                    OutlinedTextField(
                        value = recipientUsername,
                        onValueChange = { recipientUsername = it.take(64) },
                        singleLine = true,
                        label = { Text("Recipient username") },
                        placeholder = { Text("@username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = giftMessage,
                        onValueChange = { giftMessage = it.take(200) },
                        label = { Text("Message (optional)") },
                        supportingText = { Text("${giftMessage.length}/200") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "The gift is consumed only after the server validates the recipient and records the transfer.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (rows.isEmpty()) {
                Text("No eligible ${item.target.name.lowercase()} found yet.")
            } else {
                LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.optString("id") }) { row ->
                        Card(Modifier.fillMaxWidth().clickable { onSelect(row.optString("id")) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.optString("type").lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold)
                                Text(row.optString("text").ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (item.id == "digital_gift") {
                Button(
                    onClick = { onDigitalGift(recipientUsername, giftMessage) },
                    enabled = recipientUsername.trim().removePrefix("@").isNotBlank()
                ) { Text("Send Gift") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (item.id == "digital_gift") OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun tabIcon(tab: BlinkStoreTab): ImageVector = when (tab) {
    BlinkStoreTab.STORE -> Icons.Filled.Storefront
    BlinkStoreTab.VAULT -> Icons.Outlined.Inventory2
    BlinkStoreTab.VIP -> Icons.Outlined.Verified
    BlinkStoreTab.HISTORY -> Icons.Outlined.AccountBalanceWallet
    BlinkStoreTab.MORE -> Icons.Filled.MoreHoriz
}

private fun storeIcon(item: BlinkStoreItem): ImageVector = when (item.category) {
    "Boosts" -> Icons.Filled.Star
    "Profile" -> Icons.Filled.Person
    "Chat" -> Icons.Filled.ChatBubble
    "Marketplace" -> Icons.Outlined.ShoppingBag
    "Gifts" -> Icons.Outlined.Redeem
    "VIP" -> Icons.Outlined.Verified
    "Social" -> Icons.Filled.Favorite
    "Analytics" -> Icons.Filled.Notifications
    else -> Icons.Filled.Apps
}

private fun itemTypeLabel(item: BlinkStoreItem): String = when (item.type) {
    BlinkStoreItemType.CONSUMABLE -> "One-time use"
    BlinkStoreItemType.TIMED -> "Timed • manual activation"
    BlinkStoreItemType.PERMANENT -> "Permanent unlock"
    BlinkStoreItemType.CONTENT_SPECIFIC -> "Use on one ${item.target.name.lowercase()}"
    BlinkStoreItemType.PASS -> "10-day pass"
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun durationText(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${minutes}m")
    }.trim()
}

private fun shortDate(raw: String): String = raw.replace('T', ' ').take(16).ifBlank { "—" }

private val vipPerks = listOf(
    "VIP badge across profile, comments, replies, reels, search, notifications, leaderboard, marketplace and chat surfaces that consume VIP status.",
    "Special VIP interaction notifications when a VIP likes, comments, replies, follows, mentions or reposts.",
    "VIP notification priority styling without hiding normal notifications.",
    "Two claimable 2× Post Boosts per pass.",
    "Two claimable 2× Reel Boosts per pass.",
    "One Profile Spotlight per pass.",
    "One Post Spotlight per pass.",
    "One Reel Spotlight per pass.",
    "One Marketplace Highlight per pass.",
    "VIP profile frame and animated profile ring entitlement.",
    "VIP username effect, comment badge, reply styling and mention styling.",
    "Exclusive VIP reactions, stickers, chat themes and profile themes.",
    "Custom profile background and profile entrance animation entitlement.",
    "Follower celebration animation and VIP notification sound entitlement.",
    "VIP-only digital gift styling and gift animation entitlement.",
    "Daily VIP Blink Coin bonus.",
    "10% Blink Store discount while VIP is active.",
    "10% eligible activity coin bonus entitlement.",
    "Early access to new Store items, reactions and themes.",
    "Access to limited-time VIP drops.",
    "Extra pinned-post and pinned-reel capacity entitlement.",
    "Extra saved drafts, profile links, customization slots and featured media entitlement.",
    "Profile visitor insights during the active VIP period.",
    "Content performance summary, best post/reel indicators and follower-growth summary entitlement.",
    "VIP analytics card entitlement.",
    "Enhanced marketplace seller card and VIP seller badge entitlement.",
    "VIP badge in Connect and discovery surfaces.",
    "VIP discovery styling without guaranteed ranking or fake engagement.",
    "VIP-created room badge entitlement for room/live features.",
    "VIP chat accent and group/admin badge styling entitlement.",
    "Profile music/theme slot entitlement.",
    "Collectible badge for completed VIP passes.",
    "VIP streak and cumulative VIP history.",
    "Gift a 10-day VIP pass using Blink Coins.",
    "Receive gifted VIP in Vault and choose when to activate it.",
    "24-hour VIP expiry reminder.",
    "Live VIP time-remaining card.",
    "VIP benefit tracker and unused boost counters.",
    "Manual claim for included boosts/rewards so benefits are not wasted.",
    "VIP Store category and VIP-exclusive item access.",
    "VIP-exclusive pricing via active-pass discount.",
    "Birthday/profile celebration effect entitlement.",
    "VIP leaderboard decoration without altering earned rank.",
    "VIP challenge reward and cosmetic activity-reward entitlement.",
    "Milestone badge entitlement based on cumulative VIP days.",
    "VIP profile-card sharing and QR/profile-link design entitlement.",
    "Exclusive app accent selection entitlement.",
    "Full Blink Coin purchase receipts and history.",
    "Renew from the VIP screen with one tap.",
    "Optional auto-renew; OFF by default and disabled automatically if balance is insufficient."
)

package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AccountSwitcherActivity
import com.example.data.models.BlinkStoreCatalog
import com.example.data.models.BlinkStoreItem
import com.example.data.models.BlinkStoreItemType
import com.example.data.models.BlinkStoreTarget
import com.example.data.supabase.BlinkEconomyService
import com.example.ui.components.invalidateBlinkPublicPremiumIdentityCache
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
                invalidateBlinkPublicPremiumIdentityCache()
                message = successMessage
                refresh()
            }.onFailure {
                message = it.message ?: "That Blink Store action could not be completed."
            }
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
    val equippedIds = snapshot.optJSONArray("equipped").objects().map { it.optString("catalog_id") }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blink Store", fontWeight = FontWeight.Black)
                        Text(
                            "🪙 $balance Blink Coins • Premium lives on your identity",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            Surface(shadowElevation = 10.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 6.dp)
                ) {
                    BlinkStoreTab.entries.forEach { item ->
                        val selected = tab == item
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "storeTabScale"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tab = item }
                                .padding(vertical = 4.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = tabIcon(item),
                                contentDescription = item.label,
                                tint = if (selected) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                item.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                color = if (selected) BlinkPink else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && snapshot.length() == 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> AnimatedContent(
                    targetState = tab,
                    transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = .985f)) togetherWith fadeOut(tween(120)) },
                    label = "blinkStoreTabContent"
                ) { selectedTab ->
                    when (selectedTab) {
                        BlinkStoreTab.STORE -> StoreTab(
                            inventory = inventory,
                            vip = vip,
                            equippedIds = equippedIds,
                            onBuy = { purchaseItem = it }
                        )
                        BlinkStoreTab.VAULT -> VaultTab(
                            inventory = inventory,
                            snapshot = snapshot,
                            equippedIds = equippedIds,
                            onUse = { row ->
                                val item = BlinkStoreCatalog.items.firstOrNull { it.id == row.optString("catalog_id") }
                                    ?: return@VaultTab
                                val experience = item.premiumExperience()
                                if (item.type == BlinkStoreItemType.PERMANENT) {
                                    val equipped = item.id in equippedIds
                                    runAction(
                                        if (equipped) "${item.name} removed from your active premium look."
                                        else "${item.name} applied. ${experience.visibleAt} can now show the effect."
                                    ) {
                                        service.equip(row.optString("id"), equipSlot(item), !equipped)
                                    }
                                } else if (
                                    item.type == BlinkStoreItemType.CONTENT_SPECIFIC ||
                                    item.id == "digital_gift" ||
                                    item.id == "story_highlight"
                                ) {
                                    scope.launch {
                                        working = true
                                        service.boostableContent()
                                            .onSuccess { targets = it }
                                            .onFailure { message = it.message ?: "Unable to load eligible content." }
                                        working = false
                                        activateRow = row
                                    }
                                } else {
                                    runAction(
                                        "${item.name} is live. ${experience.visibleAt} now receives its premium effect."
                                    ) { service.activate(row.optString("id")) }
                                }
                            }
                        )
                        BlinkStoreTab.VIP -> VipTab(
                            vip = vip,
                            balance = balance,
                            working = working,
                            onClaim = { benefit -> runAction("VIP benefit added to your Vault.") { service.claimVip(benefit) } },
                            onRenew = { runAction("Blink VIP extended by 10 days.") { service.renewVip() } },
                            onAutoRenew = { enabled ->
                                runAction(if (enabled) "VIP auto-renew enabled." else "VIP auto-renew disabled.") {
                                    service.setAutoRenew(enabled)
                                }
                            },
                            onGift = { username -> runAction("Blink VIP gift sent to @$username.") { service.giftVip(username) } },
                            onBuyVip = { purchaseItem = BlinkStoreCatalog.items.first { it.id == "blink_vip_10d" } }
                        )
                        BlinkStoreTab.HISTORY -> HistoryTab(snapshot.optJSONArray("transactions").objects())
                        BlinkStoreTab.MORE -> MoreTab(onClose)
                    }
                }
            }

            AnimatedVisibility(
                visible = working,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .22f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 12.dp,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            CircularProgressIndicator(Modifier.padding(18.dp), color = BlinkPink)
                        }
                    }
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
                runAction("${item.name} purchased. Open Vault when you are ready to use or apply it.") {
                    service.purchase(item.id, quantity, multiplier)
                }
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
                    runAction("${item.name} is now active on the selected ${item.target.name.lowercase()}.") {
                        service.activate(row.optString("id"), targetId)
                    }
                },
                onDigitalGift = { username, giftMessage ->
                    activateRow = null
                    runAction("Premium digital gift sent to @${username.trim().removePrefix("@")}.") {
                        service.sendDigitalGift(row.optString("id"), username, giftMessage)
                    }
                }
            )
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { Button(onClick = { message = null }) { Text("Done") } },
            title = { Text("Blink Store", fontWeight = FontWeight.Black) },
            text = { Text(text) }
        )
    }
}

@Composable
private fun StoreTab(
    inventory: List<JSONObject>,
    vip: JSONObject,
    equippedIds: Set<String>,
    onBuy: (BlinkStoreItem) -> Unit
) {
    var category by remember { mutableStateOf("All") }
    val categories = remember { listOf("All") + BlinkStoreCatalog.items.map { it.category }.distinct() }
    val items = BlinkStoreCatalog.items.filter { category == "All" || it.category == category }
    val vipActive = vip.optBoolean("active", false)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PremiumStoreHero(vipActive = vipActive, itemCount = BlinkStoreCatalog.items.size)
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(categories) { name ->
                    FilterChip(
                        selected = category == name,
                        onClick = { category = name },
                        label = { Text(name) }
                    )
                }
            }
        }
        items(items, key = { it.id }) { item ->
            val owned = item.type == BlinkStoreItemType.PERMANENT &&
                inventory.any { it.optString("catalog_id") == item.id && it.optString("status") == "PERMANENT" }
            val active = inventory.any { it.optString("catalog_id") == item.id && it.optString("status") == "ACTIVE" }
            val equipped = item.id in equippedIds
            val vipLocked = item.vipOnly && !vipActive
            StoreItemCard(
                item = item,
                owned = owned,
                active = active,
                equipped = equipped,
                vipLocked = vipLocked,
                vipActive = vipActive,
                onBuy = onBuy
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun PremiumStoreHero(vipActive: Boolean, itemCount: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else .94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "blinkStoreHeroScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFDB2777))
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f)) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Make premium visible", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text(
                        "$itemCount real Store experiences • buy → Vault → use/apply",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Public cosmetics now follow your Blink identity, so supported profile/feed name surfaces can show what you actually equipped or activated.",
                color = Color.White.copy(alpha = .92f),
                fontSize = 12.sp
            )
            if (vipActive) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(100.dp), color = Color.White.copy(alpha = .16f)) {
                    Text(
                        "👑 VIP ACTIVE • 10% Store discount",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreItemCard(
    item: BlinkStoreItem,
    owned: Boolean,
    active: Boolean,
    equipped: Boolean,
    vipLocked: Boolean,
    vipActive: Boolean,
    onBuy: (BlinkStoreItem) -> Unit
) {
    val experience = item.premiumExperience()
    val displayPrice = if (vipActive) max(0, (BlinkStoreCatalog.priceFor(item) * .9).toInt()) else BlinkStoreCatalog.priceFor(item)
    val accent = premiumAccent(experience)
    var visible by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else .97f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "storeItemReveal"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, accent.copy(alpha = .32f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = .12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(storeIcon(item), null, tint = accent, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        if (item.vipOnly) Text("👑", fontSize = 15.sp)
                    }
                    Text(
                        experience.benefit,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PremiumPill(
                    if (experience.publiclyVisible) "PUBLIC • ${experience.publicLabel}" else "PRIVATE • ${experience.publicLabel}",
                    accent
                )
                PremiumPill(itemTypeLabel(item), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Seen/used on: ${experience.visibleAt}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Text(
                experience.activationHint,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🪙 $displayPrice", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (item.boostMultipliers.isNotEmpty()) {
                        Text(
                            item.boostMultipliers.joinToString(" • ") { "${it}×" } + " strengths",
                            fontSize = 10.sp,
                            color = BlinkPink
                        )
                    }
                }
                when {
                    equipped -> PremiumPill("APPLIED", Color(0xFF16A34A))
                    active -> PremiumPill("LIVE", Color(0xFF16A34A))
                    owned -> PremiumPill("IN VAULT", accent)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onBuy(item) }, enabled = !owned && !vipLocked) {
                    Text(if (owned) "Owned" else if (vipLocked) "VIP" else "Buy")
                }
            }
        }
    }
}

@Composable
private fun PremiumPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .25f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun VaultTab(
    inventory: List<JSONObject>,
    snapshot: JSONObject,
    equippedIds: Set<String>,
    onUse: (JSONObject) -> Unit
) {
    var filter by remember { mutableStateOf("AVAILABLE") }
    val filters = listOf("AVAILABLE", "ACTIVE", "PERMANENT", "USED", "EXPIRED")
    val rows = inventory.filter { it.optString("status") == filter }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Blink Vault", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    "Your premium locker. Nothing timed starts until you press Use; permanent cosmetics stay yours and can be applied again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(filters) { name ->
                    FilterChip(
                        selected = filter == name,
                        onClick = { filter = name },
                        label = { Text(name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
        }

        if (filter == "ACTIVE") {
            val boosts = snapshot.optJSONArray("active_boosts").objects()
            if (boosts.isNotEmpty()) {
                item {
                    Text(
                        "Live boost performance",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                items(boosts, key = { it.optString("id") }) { BoostCard(it) }
            }
        }

        if (rows.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inventory2, null, tint = BlinkPink, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing here yet", fontWeight = FontWeight.Bold)
                        Text(
                            "Buy an item from Store and it will appear in Vault.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(rows, key = { it.optString("id") }) { row ->
                val item = BlinkStoreCatalog.items.firstOrNull { it.id == row.optString("catalog_id") }
                val experience = item?.premiumExperience()
                val accent = experience?.let(::premiumAccent) ?: BlinkPink
                val equipped = item?.id in equippedIds

                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, accent.copy(alpha = .25f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(item?.let(::storeIcon) ?: Icons.Filled.Apps, null, tint = accent, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item?.name ?: row.optString("name"), fontWeight = FontWeight.Black)
                                Text(
                                    "${row.optString("status").lowercase().replaceFirstChar(Char::uppercase)} • Qty ${row.optInt("quantity", 1)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                row.stringOrNull("expires_at")?.let {
                                    Text("Expires ${shortDate(it)}", fontSize = 11.sp, color = accent, fontWeight = FontWeight.SemiBold)
                                }
                                if (row.has("boost_multiplier") && !row.isNull("boost_multiplier")) {
                                    Text("${row.optInt("boost_multiplier")}× boost strength", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            if (equipped) PremiumPill("APPLIED", Color(0xFF16A34A))
                        }

                        if (experience != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(experience.benefit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (experience.publiclyVisible) "Visible effect: ${experience.visibleAt}" else "Personal feature: ${experience.visibleAt}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }

                        if (row.optString("status") in listOf("AVAILABLE", "PERMANENT")) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { onUse(row) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    when {
                                        row.optString("status") != "PERMANENT" -> "Use now"
                                        equipped -> "Remove from premium look"
                                        else -> "Apply to my premium look"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun BoostCard(row: JSONObject) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        border = BorderStroke(1.dp, BlinkPink.copy(alpha = .35f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${row.optInt("multiplier", 1)}× ${row.optString("content_type").lowercase().replaceFirstChar(Char::uppercase)} Boost",
                fontWeight = FontWeight.Black
            )
            Text("Live until ${shortDate(row.optString("ends_at"))}", fontSize = 11.sp, color = BlinkPink)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Extra impressions", row.optLong("extra_impressions"))
                Metric("Profile visits", row.optLong("profile_visits"))
                Metric("Followers", row.optLong("followers_attributed"))
            }
            Text(
                "Boost improves eligible distribution opportunity only. It never manufactures likes, comments, views or followers.",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Black)
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

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED), Color(0xFFF59E0B))))
                        .padding(19.dp)
                ) {
                    Text("👑 Blink VIP — 10 Days", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (active) {
                        Text(
                            "LIVE • ${durationText(vip.optLong("remaining_seconds", 0))} remaining",
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        vip.stringOrNull("expires_at")?.let {
                            Text("Expires ${shortDate(it)}", fontSize = 11.sp, color = Color.White.copy(alpha = .82f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-renew", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                            Switch(checked = vip.optBoolean("auto_renew", false), onCheckedChange = onAutoRenew, enabled = !working)
                        }
                        Button(onClick = onRenew, enabled = !working) { Text("Renew +10 days") }
                    } else {
                        Text(
                            "Buy into Vault first. Your 10-day timer starts only when you activate the pass.",
                            color = Color.White.copy(alpha = .86f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onBuyVip, enabled = balance >= 350 && !working) { Text("Buy for 🪙 350") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Passes completed ${vip.optInt("completed_passes", 0)} • Cumulative VIP ${vip.optInt("cumulative_vip_days", 0)} days",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (active) {
            item { Text("Claimable VIP value", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.Black) }
            item { VipClaimRow("Daily VIP coin bonus", if (vip.optBoolean("daily_coin_bonus_claimed")) 0 else 1, "daily_coin_bonus", onClaim) }
            item { VipClaimRow("2× Post Boost", vip.optInt("post_boosts_2x"), "post_boost_2x", onClaim) }
            item { VipClaimRow("2× Reel Boost", vip.optInt("reel_boosts_2x"), "reel_boost_2x", onClaim) }
            item { VipClaimRow("Profile Spotlight", vip.optInt("profile_spotlights"), "profile_spotlight", onClaim) }
            item { VipClaimRow("Post Spotlight", vip.optInt("post_spotlights"), "post_spotlight", onClaim) }
            item { VipClaimRow("Reel Spotlight", vip.optInt("reel_spotlights"), "reel_spotlight", onClaim) }
            item { VipClaimRow("Marketplace Highlight", vip.optInt("marketplace_highlights"), "marketplace_highlight", onClaim) }
        }

        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gift Blink VIP", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        "The recipient gets a 10-day pass in Vault and chooses when to activate it.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = giftUser,
                        onValueChange = { giftUser = it.take(64) },
                        label = { Text("Blink username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onGift(giftUser.trim().removePrefix("@")) },
                        enabled = giftUser.trim().removePrefix("@").isNotBlank() && !working
                    ) { Text("Gift VIP") }
                }
            }
        }

        item { Text("VIP benefits", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.Black) }
        items(vipPerks) { perk ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.CheckCircle, null, tint = BlinkPink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(perk, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun VipClaimRow(title: String, remaining: Int, key: String, onClaim: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("$remaining left", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onClaim(key) }, enabled = remaining > 0) { Text("Claim") }
        }
    }
}

@Composable
private fun HistoryTab(rows: List<JSONObject>) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Purchase History", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Real Blink Coin receipts, rewards and renewals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(rows, key = { it.optString("id") }) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, tint = BlinkPink)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.optString("item_name"), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${row.optString("kind").replace('_', ' ')} • ${shortDate(row.optString("created_at"))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val amount = row.optLong("amount")
                Text(
                    if (amount > 0) "+🪙 $amount" else "-🪙 ${-amount}",
                    fontWeight = FontWeight.Black,
                    color = if (amount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                )
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
                Text("More", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Blink tools and settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = BlinkPink)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Icon(Icons.Filled.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PurchaseDialog(
    item: BlinkStoreItem,
    balance: Long,
    vipActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    var multiplier by remember { mutableIntStateOf(item.boostMultipliers.firstOrNull() ?: 1) }
    val experience = item.premiumExperience()
    val unit = BlinkStoreCatalog.priceFor(item, multiplier).let { if (vipActive) max(0, (it * .9).toInt()) else it }
    val total = unit * quantity
    val accent = premiumAccent(experience)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accent.copy(alpha = .10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .25f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("What you get", fontWeight = FontWeight.Black, color = accent)
                        Text(experience.benefit, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (experience.publiclyVisible) "People can see/use it on: ${experience.visibleAt}" else "Personal use: ${experience.visibleAt}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(experience.activationHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Type: ${itemTypeLabel(item)}", fontSize = 11.sp)
                item.durationSeconds?.let { Text("Duration after activation: ${durationText(it)}", fontSize = 11.sp) }

                if (item.boostMultipliers.isNotEmpty()) {
                    Text("Choose strength", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.boostMultipliers.forEach { value ->
                            FilterChip(selected = multiplier == value, onClick = { multiplier = value }, label = { Text("${value}×") })
                        }
                    }
                }
                if (item.stackable && item.type != BlinkStoreItemType.PERMANENT) {
                    Text("Quantity", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { quantity = max(1, quantity - 1) }) { Text("−") }
                        Text(quantity.toString(), fontWeight = FontWeight.Black)
                        OutlinedButton(onClick = { quantity = (quantity + 1).coerceAtMost(20) }) { Text("+") }
                    }
                }
                Text("🪙 $total", fontSize = 20.sp, fontWeight = FontWeight.Black)
                if (balance < total) {
                    Text("Need ${total - balance} more Blink Coins", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Text(
                    "Purchase goes to Vault first. Timed items and passes never start automatically.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val experience = item.premiumExperience()
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
        title = { Text("Use ${item.name}", fontWeight = FontWeight.Black) },
        text = {
            if (item.id == "digital_gift") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(experience.benefit, fontSize = 12.sp)
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
                }
            } else if (rows.isEmpty()) {
                Column {
                    Text("No eligible ${item.target.name.lowercase()} found yet.")
                    Spacer(Modifier.height(6.dp))
                    Text(experience.activationHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    Text("Choose exactly where the premium effect should appear.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rows, key = { it.optString("id") }) { row ->
                            Card(
                                Modifier.fillMaxWidth().clickable { onSelect(row.optString("id")) },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(row.optString("type").lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Black)
                                    Text(
                                        row.optString("text").ifBlank { "Untitled" },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }
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

private fun equipSlot(item: BlinkStoreItem): String = when (item.id) {
    "profile_ring", "animated_profile_ring", "premium_profile_frame" -> "profile_frame"
    "profile_background", "profile_theme_bundle" -> "profile_theme"
    "username_font", "animated_name" -> "name_style"
    "custom_profile_badge", "creator_badge" -> "profile_badge"
    "chat_bubble_theme", "special_dm_theme" -> "chat_theme"
    "reaction_pack", "emoji_pack", "sticker_pack" -> "social_pack_${item.id}"
    else -> item.id
}

private fun premiumAccent(experience: BlinkStoreExperience): Color = when (experience.visibility) {
    BlinkExperienceVisibility.PUBLIC_IDENTITY -> Color(0xFF7C3AED)
    BlinkExperienceVisibility.PUBLIC_CONTENT -> Color(0xFFDB2777)
    BlinkExperienceVisibility.SHARED_SOCIAL -> Color(0xFF2563EB)
    BlinkExperienceVisibility.DISTRIBUTION -> Color(0xFFF59E0B)
    BlinkExperienceVisibility.PRIVATE_UTILITY -> Color(0xFF64748B)
    BlinkExperienceVisibility.RECIPIENT_VISIBLE -> Color(0xFF0EA5E9)
    BlinkExperienceVisibility.BUNDLE -> Color(0xFF8B5CF6)
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
    BlinkStoreItemType.TIMED -> "Timed • manual start"
    BlinkStoreItemType.PERMANENT -> "Permanent unlock"
    BlinkStoreItemType.CONTENT_SPECIFIC -> "Choose one ${item.target.name.lowercase()}"
    BlinkStoreItemType.PASS -> "10-day pass"
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

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
    "VIP badge on public identity surfaces that consume VIP status.",
    "10% Blink Store discount while VIP is active.",
    "Two claimable 2× Post Boosts per pass.",
    "Two claimable 2× Reel Boosts per pass.",
    "Profile, Post, Reel and Marketplace spotlight credits.",
    "Daily VIP Blink Coin bonus.",
    "VIP interaction-notification styling and priority treatment.",
    "VIP profile frame, ring, username and theme entitlements.",
    "Exclusive reactions, stickers, chat themes and profile themes.",
    "Profile visitor insights during the active VIP period.",
    "VIP analytics and content-performance summary entitlements.",
    "Enhanced Marketplace seller identity treatment.",
    "VIP styling in Connect/discovery surfaces without fake ranking.",
    "Cumulative VIP history, streak and completed-pass badges.",
    "Gift a 10-day VIP pass; recipient chooses when it starts.",
    "Optional auto-renew, OFF by default."
)

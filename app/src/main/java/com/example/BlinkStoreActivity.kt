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
import androidx.compose.material.icons.filled.Search
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
import com.example.ui.components.BlinkMark
import com.example.ui.components.BlinkStoreLivePreview
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
    STORE("Store"), VAULT("Collection"), VIP("VIP"), HISTORY("History"), MORE("More")
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
    var previewItem by remember { mutableStateOf<BlinkStoreItem?>(null) }
    var purchaseItem by remember { mutableStateOf<BlinkStoreItem?>(null) }
    var giftItem by remember { mutableStateOf<BlinkStoreItem?>(null) }
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
    val xpLevel = snapshot.optInt("xp_level", 1).coerceAtLeast(1)
    val inventory = snapshot.optJSONArray("inventory").objects()
    val serverCatalog = snapshot.optJSONArray("catalog").objects().mapNotNull(::parseStoreCatalogItem)
    val catalogItems = if (serverCatalog.isNotEmpty()) serverCatalog else BlinkStoreCatalog.items
    val vip = snapshot.optJSONObject("vip") ?: JSONObject()
    val equippedIds = snapshot.optJSONArray("equipped").objects().map { it.optString("catalog_id") }.toSet()
    val wishlistIds = snapshot.optJSONArray("wishlist")?.let { array ->
        buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.orEmpty()
    val savedLooks = snapshot.optJSONArray("saved_looks").objects()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlinkMark(size = 32.dp, showText = false)
                        Spacer(Modifier.width(10.dp))
                        Column {
                        Text("Blink Store", fontWeight = FontWeight.Black)
                        Text(
                            "🪙 $balance Blink Coins • Premium lives on your identity",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
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
                            catalog = catalogItems,
                            inventory = inventory,
                            xpLevel = xpLevel,
                            vip = vip,
                            equippedIds = equippedIds,
                            wishlistIds = wishlistIds,
                            onPreview = { previewItem = it },
                            onWishlist = { item, enabled ->
                                runAction(if (enabled) "${item.name} saved to Wishlist." else "${item.name} removed from Wishlist.") {
                                    service.setWishlist(item.id, enabled)
                                }
                            },
                        )
                        BlinkStoreTab.VAULT -> VaultTab(
                            inventory = inventory,
                            snapshot = snapshot,
                            equippedIds = equippedIds,
                            onPreview = { previewItem = it },
                            onSaveLook = {
                                runAction("Saved Look ${savedLooks.size + 1}.") {
                                    service.saveCurrentLook("Look ${savedLooks.size + 1}")
                                }
                            },
                            onApplyLook = { row ->
                                runAction("${row.optString("name", "Saved look")} applied.") {
                                    service.applySavedLook(row.optString("id"))
                                }
                            },
                            onDeleteLook = { row ->
                                runAction("${row.optString("name", "Saved look")} deleted.") {
                                    service.deleteSavedLook(row.optString("id"))
                                }
                            },
                            onUse = { row ->
                                val item = catalogItems.firstOrNull { it.id == row.optString("catalog_id") }
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
                            vipPrice = catalogItems.firstOrNull { it.id == "blink_vip_10d" }?.price ?: 1_200,
                            working = working,
                            onClaim = { benefit -> runAction("VIP benefit added to your Collection.") { service.claimVip(benefit) } },
                            onRenew = { runAction("Blink VIP extended by 30 days.") { service.renewVip() } },
                            onAutoRenew = { enabled ->
                                runAction(if (enabled) "VIP auto-renew enabled." else "VIP auto-renew disabled.") {
                                    service.setAutoRenew(enabled)
                                }
                            },
                            onGift = { username -> runAction("Blink VIP gift sent to @$username.") { service.giftVip(username) } },
                            onBuyVip = { previewItem = catalogItems.first { it.id == "blink_vip_10d" } }
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

    previewItem?.let { item ->
        val owned = item.type == BlinkStoreItemType.PERMANENT &&
            inventory.any { it.optString("catalog_id") == item.id && it.optString("status") == "PERMANENT" }
        ProductPreviewDialog(
            item = item,
            balance = balance,
            vipActive = vip.optBoolean("active", false),
            owned = owned,
            xpLevel = xpLevel,
            onDismiss = { previewItem = null },
            onBuy = {
                previewItem = null
                purchaseItem = item
            },
            onGift = if (item.canGiftCosmetic() && item.isAvailableNow()) {
                {
                    previewItem = null
                    giftItem = item
                }
            } else null,
            onClaimLevelReward = item.unlockLevel?.let { requiredLevel ->
                {
                    if (xpLevel >= requiredLevel && !owned) {
                        previewItem = null
                        runAction("${item.name} claimed from your Level $requiredLevel reward.") {
                            service.claimLevelCosmetic(item.id)
                        }
                    }
                }
            },
        )
    }

    purchaseItem?.let { item ->
        PurchaseDialog(
            item = item,
            balance = balance,
            vipActive = vip.optBoolean("active", false),
            onDismiss = { purchaseItem = null },
            onConfirm = { quantity, multiplier ->
                purchaseItem = null
                runAction("${item.name} purchased. Open My Collection when you are ready to use or apply it.") {
                    service.purchase(item.id, quantity, multiplier)
                }
            }
        )
    }

    giftItem?.let { item ->
        CosmeticGiftDialog(
            item = item,
            balance = balance,
            vipActive = vip.optBoolean("active", false),
            onDismiss = { giftItem = null },
            onConfirm = { username ->
                giftItem = null
                val recipient = username.trim().removePrefix("@")
                runAction("${item.name} sent to @$recipient.") {
                    service.giftStoreItem(item.id, recipient)
                }
            }
        )
    }

    activateRow?.let { row ->
        val item = catalogItems.firstOrNull { it.id == row.optString("catalog_id") }
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
    catalog: List<BlinkStoreItem>,
    inventory: List<JSONObject>,
    xpLevel: Int,
    vip: JSONObject,
    equippedIds: Set<String>,
    wishlistIds: Set<String>,
    onPreview: (BlinkStoreItem) -> Unit,
    onWishlist: (BlinkStoreItem, Boolean) -> Unit
) {
    var category by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    val categories = remember(catalog) { listOf("All") + catalog.map { it.category }.distinct() }
    val items = catalog.filter { item ->
        (category == "All" || item.category == category) &&
            (query.isBlank() || listOf(item.name, item.description, item.category)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val vipActive = vip.optBoolean("active", false)
    val ownedIds = inventory.map { it.optString("catalog_id") }.filter(String::isNotBlank).toSet()
    val ownedCategories = catalog
        .filter { it.id in ownedIds || it.id in equippedIds }
        .map { it.category }
        .toSet()
    val recommendations = catalog
        .asSequence()
        .filter { it.id !in ownedIds }
        .filter { !it.vipOnly || vipActive }
        .sortedWith(
            compareByDescending<BlinkStoreItem> { it.category in ownedCategories }
                .thenByDescending { it.id in wishlistIds }
                .thenBy { it.price }
        )
        .take(6)
        .toList()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PremiumStoreHero(vipActive = vipActive, itemCount = catalog.size)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search profile, comments, chat, effects…") },
            )
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
        if (query.isBlank() && category == "All" && recommendations.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Recommended for your look", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "Based only on Store items you own or equipped.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    items(recommendations, key = { "recommended-${it.id}" }) { recommended ->
                        AssistChip(
                            onClick = { onPreview(recommended) },
                            label = { Text(recommended.name, maxLines = 1) },
                            leadingIcon = {
                                Icon(storeIcon(recommended), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
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
                wishlisted = item.id in wishlistIds,
                xpLevel = xpLevel,
                onPreview = onPreview,
                onWishlist = onWishlist
            )
        }
        if (items.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No Store items found", fontWeight = FontWeight.Black)
                    Text("Try another name or category.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                        "$itemCount real Store experiences • preview → buy → Collection → use/apply",
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
    wishlisted: Boolean,
    xpLevel: Int,
    onPreview: (BlinkStoreItem) -> Unit,
    onWishlist: (BlinkStoreItem, Boolean) -> Unit
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
            .clickable { onPreview(item) }
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
                if (item.rarity != "STANDARD") {
                    PremiumPill(item.rarity, accent)
                }
            }
            item.unlockLevel?.let { required ->
                Spacer(Modifier.height(6.dp))
                Text(
                    if (xpLevel >= required) "✓ Level $required reward unlocked — claim it free in Preview"
                    else "Earn it free at BLINK Level $required • your level: $xpLevel",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (xpLevel >= required) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.availabilityLabel()?.let { availability ->
                Spacer(Modifier.height(4.dp))
                Text(
                    availability,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (item.isAvailableNow()) Color(0xFF16A34A) else BlinkPink
                )
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
                    owned -> PremiumPill("IN COLLECTION", accent)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onWishlist(item, !wishlisted) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (wishlisted) "♥ Saved" else "♡ Wishlist")
                }
                Button(
                    onClick = { onPreview(item) },
                    enabled = !vipLocked,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            vipLocked -> "VIP required"
                            owned -> "Preview owned"
                            else -> "Preview & buy"
                        }
                    )
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
    onPreview: (BlinkStoreItem) -> Unit,
    onSaveLook: () -> Unit,
    onApplyLook: (JSONObject) -> Unit,
    onDeleteLook: (JSONObject) -> Unit,
    onUse: (JSONObject) -> Unit
) {
    var filter by remember { mutableStateOf("AVAILABLE") }
    val filters = listOf("AVAILABLE", "ACTIVE", "PERMANENT", "USED", "EXPIRED")
    val rows = inventory.filter { it.optString("status") == filter }
    val savedLooks = snapshot.optJSONArray("saved_looks").objects()
    val wishlistCount = snapshot.optJSONArray("wishlist")?.length() ?: 0

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("My Collection", fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    "Equip, remove and preview everything you own. Timed items only start when you press Use.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveLook, enabled = equippedIds.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Save current look")
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = BlinkPink.copy(alpha = .10f),
                        border = BorderStroke(1.dp, BlinkPink.copy(alpha = .25f))
                    ) {
                        Text(
                            "♡ $wishlistCount Wishlist",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            color = BlinkPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
        if (savedLooks.isNotEmpty()) {
            item {
                Text(
                    "Saved Looks",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            items(savedLooks, key = { it.optString("id") }) { look ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(look.optString("name", "Saved look"), fontWeight = FontWeight.Black)
                            Text(
                                "${look.optInt("item_count", 0)} equipped cosmetics",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { onDeleteLook(look) }) { Text("Delete") }
                        Button(onClick = { onApplyLook(look) }) { Text("Apply") }
                    }
                }
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
                            "Buy an item from Store and it will appear in My Collection.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(rows, key = { it.optString("id") }) { row ->
                val item = catalogItems.firstOrNull { it.id == row.optString("catalog_id") }
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

                        if (item != null) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { onPreview(item) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Preview")
                            }
                        }

                        if (row.optString("status") in listOf("AVAILABLE", "PERMANENT")) {
                            Spacer(Modifier.height(8.dp))
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
    vipPrice: Int,
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
                    Text("👑 Blink VIP — 30 Days", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
                        Button(onClick = onRenew, enabled = !working) { Text("Renew +30 days") }
                    } else {
                        Text(
                            "Buy into My Collection first. Your 30-day timer starts only when you activate the pass.",
                            color = Color.White.copy(alpha = .86f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onBuyVip, enabled = balance >= vipPrice && !working) { Text("Preview • 🪙 $vipPrice") }
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
                        "The recipient gets a 30-day pass in My Collection and chooses when to activate it.",
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
                Text(
                    "Receipts, price, duration, expiry and current activation status in one place.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (rows.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Store activity yet", fontWeight = FontWeight.Black)
                    Text(
                        "Purchases, gifts, rewards and renewals will appear here.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(rows, key = { it.optString("id") }) { row ->
            val amount = row.optLong("amount")
            val durationSeconds = row.optLong("catalog_duration_seconds", 0L)
            val status = row.optString("current_inventory_status")
            val expiresAt = row.optString("current_inventory_expires_at")
            val activatedAt = row.optString("current_inventory_activated_at")
            val itemType = row.optString("catalog_item_type")
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = BlinkPink)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.optString("item_name").ifBlank { row.optString("kind").replace('_', ' ') },
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "${row.optString("kind").replace('_', ' ')} • ${shortDate(row.optString("created_at"))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (amount > 0) "+🪙 $amount" else "-🪙 ${-amount}",
                            fontWeight = FontWeight.Black,
                            color = if (amount > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (row.has("catalog_id") && !row.isNull("catalog_id")) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (itemType.isNotBlank()) {
                                PremiumPill(itemType.replace('_', ' '), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (status.isNotBlank()) {
                                PremiumPill(
                                    status.replace('_', ' '),
                                    if (status == "ACTIVE" || status == "PERMANENT") Color(0xFF16A34A) else BlinkPink
                                )
                            }
                        }
                        if (durationSeconds > 0L) {
                            Text(
                                "Duration: ${durationText(durationSeconds)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (itemType == "PERMANENT") {
                            Text(
                                "Duration: Permanent",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activatedAt.isNotBlank()) {
                            Text(
                                "Activated: ${shortDate(activatedAt)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expiresAt.isNotBlank()) {
                            Text(
                                "Expires: ${shortDate(expiresAt)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
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
private fun ProductPreviewDialog(
    item: BlinkStoreItem,
    balance: Long,
    vipActive: Boolean,
    owned: Boolean,
    xpLevel: Int,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    onGift: (() -> Unit)? = null,
    onClaimLevelReward: (() -> Unit)? = null,
) {
    var showEffect by remember(item.id) { mutableStateOf(true) }
    val experience = item.premiumExperience()
    val displayPrice = if (vipActive) {
        max(0, (BlinkStoreCatalog.priceFor(item) * .9).toInt())
    } else {
        BlinkStoreCatalog.priceFor(item)
    }
    val vipLocked = item.vipOnly && !vipActive
    val availableNow = item.isAvailableNow()
    val levelRequirement = item.unlockLevel
    val canClaimLevelReward = !owned && levelRequirement != null && xpLevel >= levelRequirement

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(item.name, fontWeight = FontWeight.Black)
                Text(
                    "Live try-on • what people will actually see",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showEffect,
                        onClick = { showEffect = false },
                        label = { Text("Before") },
                    )
                    FilterChip(
                        selected = showEffect,
                        onClick = { showEffect = true },
                        label = { Text("With effect") },
                    )
                }
                AnimatedContent(
                    targetState = showEffect,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "storeBeforeAfterPreview",
                ) { enabled ->
                    if (enabled) {
                        BlinkStoreLivePreview(catalogId = item.id, itemName = item.name)
                    } else {
                        StandardStorePreview(item)
                    }
                }
                Text(experience.benefit, fontSize = 12.sp)
                Text(
                    "Visible on: ${experience.visibleAt}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = premiumAccent(experience),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PremiumPill(itemTypeLabel(item), MaterialTheme.colorScheme.onSurfaceVariant)
                    PremiumPill(if (experience.publiclyVisible) "PUBLIC EFFECT" else "PERSONAL EFFECT", premiumAccent(experience))
                }
                Text(experience.activationHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.availabilityLabel()?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (availableNow) Color(0xFF16A34A) else BlinkPink
                    )
                }
                levelRequirement?.let { required ->
                    Text(
                        if (canClaimLevelReward) "Level $required reached — you can claim this permanent cosmetic free."
                        else "Free Level reward at Level $required • your level: $xpLevel. You can still buy it normally when available.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canClaimLevelReward) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (balance < displayPrice && !owned && availableNow) {
                    Text(
                        "You need ${displayPrice - balance} more Blink Coins.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onBuy,
                enabled = !owned && !vipLocked && availableNow && balance >= displayPrice,
            ) {
                Text(
                    when {
                        owned -> "Owned"
                        vipLocked -> "VIP required"
                        !availableNow -> item.availabilityLabel() ?: "Unavailable"
                        else -> "Buy • 🪙 $displayPrice"
                    }
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canClaimLevelReward && onClaimLevelReward != null) {
                    Button(onClick = onClaimLevelReward) {
                        Text("Claim free")
                    }
                }
                if (onGift != null) {
                    OutlinedButton(onClick = onGift) {
                        Icon(Icons.Outlined.Redeem, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Gift")
                    }
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun CosmeticGiftDialog(
    item: BlinkStoreItem,
    balance: Long,
    vipActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var username by remember(item.id) { mutableStateOf("") }
    val price = BlinkStoreCatalog.priceFor(item).let { if (vipActive) max(0, (it * .9).toInt()) else it }
    val recipient = username.trim().removePrefix("@")
    val availableNow = item.isAvailableNow()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gift ${item.name}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlinkStoreLivePreview(catalogId = item.id, itemName = item.name)
                Text(
                    "The recipient gets this item in My Collection. Timed cosmetics do not start until they activate them.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(64) },
                    label = { Text("Recipient username") },
                    placeholder = { Text("@username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("🪙 $price", fontSize = 18.sp, fontWeight = FontWeight.Black)
                if (balance < price) {
                    Text("Need ${price - balance} more Blink Coins", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(recipient) },
                enabled = recipient.isNotBlank() && balance >= price && availableNow,
            ) { Text("Send Gift") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StandardStorePreview(item: BlinkStoreItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(188.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    storeIcon(item),
                    contentDescription = null,
                    modifier = Modifier.padding(15.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Standard appearance", fontWeight = FontWeight.Black)
            Text(
                "No premium surface, motion or identity accent",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
                    "Purchase goes to My Collection first. Timed items and passes never start automatically.",
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

private fun parseStoreCatalogItem(row: JSONObject): BlinkStoreItem? {
    val id = row.optString("id").trim()
    if (id.isBlank()) return null

    val fallback = BlinkStoreCatalog.items.firstOrNull { it.id == id }
    val itemType = runCatching {
        BlinkStoreItemType.valueOf(row.optString("item_type", fallback?.type?.name ?: "CONSUMABLE").uppercase())
    }.getOrElse { fallback?.type ?: BlinkStoreItemType.CONSUMABLE }
    val target = runCatching {
        BlinkStoreTarget.valueOf(row.optString("target_type", fallback?.target?.name ?: "NONE").uppercase())
    }.getOrElse { fallback?.target ?: BlinkStoreTarget.NONE }

    val multipliersJson = row.optJSONArray("boost_multipliers")
    val multipliers = if (multipliersJson != null) {
        buildList {
            for (index in 0 until multipliersJson.length()) {
                multipliersJson.optInt(index, 0).takeIf { it > 0 }?.let(::add)
            }
        }
    } else fallback?.boostMultipliers.orEmpty()

    return BlinkStoreItem(
        id = id,
        name = row.optString("name").ifBlank { fallback?.name ?: id.replace('_', ' ').replaceFirstChar(Char::uppercase) },
        description = row.optString("description").ifBlank { fallback?.description.orEmpty() },
        iconKey = row.optString("icon_key").ifBlank { fallback?.iconKey ?: "auto_awesome" },
        category = row.optString("category").ifBlank { fallback?.category ?: "Premium" },
        price = row.optInt("price", fallback?.price ?: 0).coerceAtLeast(0),
        type = itemType,
        target = target,
        durationSeconds = if (row.isNull("duration_seconds")) fallback?.durationSeconds else row.optLong("duration_seconds").takeIf { it > 0L },
        stackable = if (row.has("stackable")) row.optBoolean("stackable") else fallback?.stackable ?: false,
        vipOnly = if (row.has("vip_only")) row.optBoolean("vip_only") else fallback?.vipOnly ?: false,
        boostMultipliers = multipliers,
        collectionId = row.stringOrNull("collection_id") ?: fallback?.collectionId,
        rarity = row.optString("rarity", fallback?.rarity ?: "STANDARD").ifBlank { "STANDARD" },
        unlockLevel = if (row.isNull("unlock_level")) fallback?.unlockLevel else row.optInt("unlock_level").takeIf { it > 0 },
        availableFrom = row.stringOrNull("available_from") ?: fallback?.availableFrom,
        availableUntil = row.stringOrNull("available_until") ?: fallback?.availableUntil,
    )
}

private fun BlinkStoreItem.isAvailableNow(now: java.time.Instant = java.time.Instant.now()): Boolean {
    val starts = availableFrom?.let { raw ->
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull()
    }
    val ends = availableUntil?.let { raw ->
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull()
    }
    return (starts == null || !now.isBefore(starts)) && (ends == null || now.isBefore(ends))
}

private fun BlinkStoreItem.availabilityLabel(now: java.time.Instant = java.time.Instant.now()): String? {
    val starts = availableFrom?.let { raw ->
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull()
    }
    val ends = availableUntil?.let { raw ->
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { java.time.Instant.parse(raw) }
            .getOrNull()
    }
    return when {
        starts != null && now.isBefore(starts) -> "Limited drop • available from ${availableFrom.orEmpty().take(10)}"
        ends != null && !now.isBefore(ends) -> "Limited drop ended ${availableUntil.orEmpty().take(10)}"
        ends != null -> "Limited drop • available until ${availableUntil.orEmpty().take(10)}"
        else -> null
    }
}

private fun BlinkStoreItem.canGiftCosmetic(): Boolean =
    !vipOnly &&
        type in setOf(BlinkStoreItemType.PERMANENT, BlinkStoreItemType.TIMED) &&
        category !in setOf("Boosts", "Analytics", "Marketplace") &&
        id !in setOf(
            "profile_spotlight_1h", "profile_spotlight_24h",
            "post_spotlight_6h", "post_spotlight_24h",
            "reel_spotlight_6h", "reel_spotlight_24h",
            "discovery_boost_7d", "profile_discovery_boost",
            "birthday_profile_theme",
        )

private fun equipSlot(item: BlinkStoreItem): String = when (item.id) {
    "profile_ring", "animated_profile_ring", "premium_profile_frame",
    "campus_signature_frame", "level_10_neon_frame" -> "profile_frame"
    "profile_background", "profile_theme_bundle", "campus_signature_theme",
    "christmas_2026_profile_theme" -> "profile_theme"
    "username_font", "animated_name", "campus_signature_nameplate",
    "level_25_signature_nameplate", "christmas_2026_nameplate" -> "name_style"
    "level_50_legend_aura" -> "profile_aura"
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
    "Campus" -> Icons.Filled.Person
    "Earned" -> Icons.Outlined.EmojiEvents
    "Seasonal" -> Icons.Outlined.Redeem
    else -> Icons.Filled.Apps
}

private fun itemTypeLabel(item: BlinkStoreItem): String = when (item.type) {
    BlinkStoreItemType.CONSUMABLE -> "One-time use"
    BlinkStoreItemType.TIMED -> "Timed • manual start"
    BlinkStoreItemType.PERMANENT -> "Permanent unlock"
    BlinkStoreItemType.CONTENT_SPECIFIC -> "Choose one ${item.target.name.lowercase()}"
    BlinkStoreItemType.PASS -> "30-day pass"
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
    "Gift a 30-day VIP pass; recipient chooses when it starts.",
    "Optional auto-renew, OFF by default."
)

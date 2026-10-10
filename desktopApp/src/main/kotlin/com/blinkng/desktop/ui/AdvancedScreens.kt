package com.blinkng.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blinkng.desktop.DesktopAppState
import com.blinkng.desktop.data.DesktopInventoryItem
import com.blinkng.desktop.data.DesktopRpcActions
import com.blinkng.desktop.data.DesktopStoreItem
import com.blinkng.shared.BlinkStoreProductGroup
import com.blinkng.shared.BlinkStoreProductGroups
import com.blinkng.shared.BlinkStoreJourneys
import com.blinkng.shared.BlinkCoinCheckoutOffer
import com.blinkng.shared.BlinkCoinCheckoutPolicy
import java.awt.Desktop
import java.net.URI
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
    var economyStatus by remember { mutableStateOf(JSONObject()) }
    var coinPackToConfirm by remember { mutableStateOf<BlinkCoinCheckoutOffer?>(null) }
    var pendingCoinOrder by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUse by remember { mutableStateOf<DesktopInventoryItem?>(null) }
    var selectedGroup by remember { mutableStateOf<BlinkStoreProductGroup?>(null) }
    var previewItem by remember { mutableStateOf<DesktopStoreItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("For You") }
    var selectedJourney by remember { mutableStateOf<String?>(null) }
    var targetState by remember { mutableStateOf(JSONObject()) }
    var heroShown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        val store = runCatching { state.client.fetchStore() }.getOrDefault(emptyList<DesktopStoreItem>() to emptyList())
        catalog = store.first
        inventory = store.second
        balance = runCatching { state.client.fetchCoinBalance() }.getOrDefault(0L)
        serverState = runCatching { actions.getStoreState() }.getOrNull()
        // Disable cash checkout on errors; no client-priced fallback.
        economyStatus = runCatching { actions.getEconomyStatus() }.getOrDefault(JSONObject())
        loading = false
    }

    fun runAction(success: String, action: suspend () -> JSONObject) {
        scope.launch {
            working = true
            runCatching { action() }
                .onSuccess {
                    message = success
                    error = null
                    reload()
                }
                .onFailure { error = it.message ?: "Blink Store action failed." }
            working = false
        }
    }

    fun purchase(item: DesktopStoreItem) {
        scope.launch {
            working = true
            val multiplier = item.boostMultipliers.firstOrNull()
            runCatching { actions.purchaseStoreItem(item.id, 1, multiplier) }
                .onSuccess {
                    message = "${item.name} purchased. It is now in your Vault."
                    error = null
                    previewItem = null
                    reload()
                }
                .onFailure { error = it.message }
            working = false
        }
    }

    val coinOffers = economyStatus.optJSONArray("coin_packs")?.let { data ->
        (0 until data.length()).mapNotNull { index ->
            data.optJSONObject(index)?.let { row ->
                BlinkCoinCheckoutPolicy.validOffer(
                    row.optString("id"), row.optInt("price_ngn"), row.optInt("coins")
                )
            }
        }
    }.orEmpty()
    val coinCheckoutEnabled = economyStatus.optBoolean("cash_checkout_enabled", false)

    fun initializeCheckout(offer: BlinkCoinCheckoutOffer) {
        coinPackToConfirm = null
        if (!BlinkCoinCheckoutPolicy.canCheckout(coinCheckoutEnabled, offer)) return
        scope.launch {
            working = true
            runCatching {
                val response = actions.initializePaystackCoinCheckout(offer.id)
                val url = response.optString("authorization_url")
                val orderId = response.optString("order_id")
                require(BlinkCoinCheckoutPolicy.trustedHostedCheckoutUrl(url) && orderId.isNotBlank()) {
                    "Checkout returned an invalid destination."
                }
                require(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    "This device cannot launch secure checkout."
                }
                Desktop.getDesktop().browse(URI(url))
                pendingCoinOrder = orderId
            }.onSuccess {
                message = "After payment, return and select Check payment. Only confirmed payments credit coins."
                error = null
            }.onFailure { error = it.message ?: "Coin checkout failed." }
            working = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
        heroShown = true
    }

    val heroScale by animateFloatAsState(
        targetValue = if (heroShown) 1f else .94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "desktopStoreHeroScale",
    )
    val catalogById = remember(catalog) { catalog.associateBy { it.id } }
    val equippedIds = serverState
        ?.optJSONArray("equipped")
        .objectList()
        .map { it.optString("catalog_id") }
        .filter(String::isNotBlank)
        .toSet()
    val vipActive = serverState?.optJSONObject("vip")?.optBoolean("active", false) ?: false
    val filteredGroups = remember(catalog, searchQuery, selectedCategory, selectedJourney) {
        val journeyGroupIds = BlinkStoreJourneys.availableGroups(selectedJourney).map { it.id }.toSet()
        val query = searchQuery.trim()
        BlinkStoreProductGroups.groupsForCategory(selectedCategory)
            .filter { it.id in journeyGroupIds }
            .filter { group ->
            query.isBlank() ||
                group.title.contains(query, true) ||
                group.description.contains(query, true) ||
                group.category.contains(query, true) ||
                group.itemIds.mapNotNull(catalogById::get).any { item ->
                    item.name.contains(query, true) ||
                        item.description.contains(query, true) ||
                        item.category.contains(query, true)
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = heroScale; scaleY = heroScale },
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3B0764), Color(0xFF6D28D9), Color(0xFFDB2777)),
                            ),
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f)) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp).size(25.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Blink Store", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            Text("$balance Blink Coins • Buy → Vault → Use / Apply", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        }
                        if (working) CircularProgressIndicator(modifier = Modifier.size(25.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                    Text(
                        "Browse premium collections with visual previews, then choose the exact existing Store variant you want. Purchases still go to Vault before use or activation.",
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PremiumStatusPill("${BlinkStoreProductGroups.all.size} COLLECTIONS", Color.White)
                        if (equippedIds.isNotEmpty()) PremiumStatusPill("${equippedIds.size} APPLIED", Color.White)
                    }
                }
            }
        }

        message?.let {
            item {
                Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFF16A34A).copy(alpha = .12f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF16A34A))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color(0xFF16A34A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        serverState?.let {
            item {
                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Premium identity", fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !working) { Text("Refresh") }
                        }
                        Text("Owned cosmetics stay in your Collection and can be previewed before they are applied.")
                        if (equippedIds.isNotEmpty()) {
                            Text("Applied public/premium cosmetics", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                equippedIds.take(4).forEach { id ->
                                    val storeItem = catalogById[id]
                                    val exp = storeItem?.premiumExperience()
                                    PremiumStatusPill(exp?.label ?: id.replace('_', ' ').uppercase().take(12), exp?.let(::desktopPremiumAccent) ?: MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Explore by goal", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(
                    "Only existing Store products are listed. Explore and preview without pressure to buy.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedJourney == null,
                            onClick = { selectedJourney = null; selectedCategory = "For You" },
                            label = { Text("All collections") },
                        )
                    }
                    items(BlinkStoreJourneys.live, key = { it.id }) { journey ->
                        FilterChip(
                            selected = selectedJourney == journey.id,
                            onClick = {
                                selectedJourney = if (selectedJourney == journey.id) null else journey.id
                                selectedCategory = "For You"
                            },
                            label = { Text(journey.title) },
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Build your BLINK Identity", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(
                    "Mix and match individually priced pieces. Each purchase remains optional.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(BlinkStoreJourneys.looks, key = { it.id }) { look ->
                        Surface(
                            modifier = Modifier.width(245.dp).clickable {
                                BlinkStoreProductGroups.byId(look.entryGroupId)?.let { selectedGroup = it }
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(look.title, fontWeight = FontWeight.Bold)
                                Text(
                                    look.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    minLines = 3,
                                )
                                Text("Explore pieces →", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blink Coins", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !working) { Text("Refresh balance") }
                }
                Text(
                    if (coinCheckoutEnabled) "Optional fixed-price packs. Only the backend can confirm and credit payments."
                    else "Coin packs are shown for reference. Secure checkout is not enabled yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(coinOffers, key = { it.id }) { offer ->
                        Surface(
                            modifier = Modifier.width(170.dp),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${offer.coins} coins", fontWeight = FontWeight.Bold)
                                Text("₦${offer.priceNgn}", fontSize = 14.sp)
                                if (offer.bonusCoins > 0) Text("${offer.bonusCoins} extra coins included", fontSize = 11.sp)
                                Button(
                                    onClick = { coinPackToConfirm = offer },
                                    enabled = !working && BlinkCoinCheckoutPolicy.canCheckout(coinCheckoutEnabled, offer),
                                ) { Text(if (coinCheckoutEnabled) "Buy" else "Unavailable") }
                            }
                        }
                    }
                }
                pendingCoinOrder?.let { id ->
                    OutlinedButton(
                        enabled = !working,
                        onClick = {
                            scope.launch {
                                working = true
                                runCatching { actions.verifyPaystackCoinCheckout(id) }
                                    .onSuccess {
                                        pendingCoinOrder = null
                                        message = "Payment verified and coins credited by the server."
                                        error = null
                                        reload()
                                    }
                                    .onFailure { error = it.message ?: "Payment is not verified yet." }
                                working = false
                            }
                        },
                    ) { Text("Check payment") }
                }
            }
        }

        item { Text("Store collections", fontWeight = FontWeight.Black, fontSize = 21.sp) }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, null) },
                label = { Text("Search themes, effects, boosts and gifts") },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BlinkStoreProductGroups.categories) { name ->
                    FilterChip(
                        selected = selectedCategory == name,
                        onClick = { selectedCategory = name },
                        label = { Text(name) },
                    )
                }
            }
        }
        if (loading) item { Text("Loading Store…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!loading && filteredGroups.isEmpty()) item {
            Text("No Store collections match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(filteredGroups.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { group ->
                    Box(Modifier.weight(1f)) {
                        DesktopStoreGroupCard(
                            group = group,
                            catalog = catalog,
                            inventory = inventory,
                            equippedIds = equippedIds,
                            vipActive = vipActive,
                            onOpen = { selectedGroup = group },
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        item {
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Blink Vault", fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("Timed items wait here until Use. Permanent cosmetics can be applied or removed without losing ownership.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!loading && inventory.isEmpty()) item { Text("No purchased items yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(inventory, key = { "pro-inventory-${it.id}" }) { item ->
            val storeItem = catalogById[item.catalogId]
            val experience = storeItem?.premiumExperience()
            val accent = experience?.let(::desktopPremiumAccent) ?: MaterialTheme.colorScheme.primary
            val equipped = item.catalogId in equippedIds

            Surface(
                shape = RoundedCornerShape(19.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = .24f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(storeItem?.name ?: item.catalogId.replace('_', ' ').replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Black)
                            Text("${item.status.lowercase().replaceFirstChar(Char::uppercase)} • qty ${item.quantity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (equipped) PremiumStatusPill("APPLIED", Color(0xFF16A34A))
                        else if (item.status.equals("ACTIVE", true)) PremiumStatusPill("LIVE", Color(0xFF16A34A))
                    }
                    experience?.let {
                        Text(it.benefit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (it.publicFacing) "Visible effect: ${it.visibleAt}" else "Personal feature: ${it.visibleAt}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    item.activatedAt?.let { Text("Activated ${shortDesktopDate(it)}", fontSize = 11.sp) }
                    item.expiresAt?.let { Text("Expires ${shortDesktopDate(it)}", fontSize = 11.sp, color = accent, fontWeight = FontWeight.SemiBold) }
                    item.boostMultiplier?.let { Text("${it}× boost strength", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }

                    if (item.status.equals("AVAILABLE", true) || item.status.equals("PERMANENT", true)) {
                        Button(
                            enabled = !working,
                            onClick = {
                                val selected = storeItem ?: return@Button
                                when {
                                    item.status.equals("PERMANENT", true) -> {
                                        runAction(
                                            if (equipped) "${selected.name} removed from your active premium look."
                                            else "${selected.name} applied. ${experience?.visibleAt ?: "Blink"} can now show the effect.",
                                        ) { actions.equipStoreItem(item.id, desktopEquipSlot(item.catalogId), !equipped) }
                                    }
                                    item.catalogId == "digital_gift" || selected.itemType.equals("CONTENT_SPECIFIC", true) || !selected.targetType.equals("NONE", true) -> {
                                        scope.launch {
                                            working = true
                                            runCatching { actions.getBoostableContent() }
                                                .onSuccess { targetState = it; pendingUse = item; error = null }
                                                .onFailure { error = it.message }
                                            working = false
                                        }
                                    }
                                    else -> runAction("${selected.name} is now live. ${experience?.visibleAt ?: "Blink"} receives its premium effect.") {
                                        actions.activateStoreItem(item.id)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    item.status.equals("PERMANENT", true) && equipped -> "Remove from premium look"
                                    item.status.equals("PERMANENT", true) -> "Apply to my premium look"
                                    else -> "Use now"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    coinPackToConfirm?.let { offer ->
        DesktopCoinPackConfirmation(
            offer = offer,
            onClose = { coinPackToConfirm = null },
            onConfirm = { initializeCheckout(offer) },
        )
    }

    selectedGroup?.let { group ->
        DesktopStoreGroupDialog(
            group = group,
            catalog = catalog,
            inventory = inventory,
            equippedIds = equippedIds,
            balance = balance,
            vipActive = vipActive,
            working = working,
            onDismiss = { selectedGroup = null },
            onPreview = { item ->
                selectedGroup = null
                previewItem = item
            },
            onBuy = { item ->
                selectedGroup = null
                purchase(item)
            },
        )
    }

    pendingUse?.let { inventoryItem ->
        val storeItem = catalogById[inventoryItem.catalogId]
        if (storeItem != null) {
            DesktopStoreUseDialog(
                inventoryItem = inventoryItem,
                storeItem = storeItem,
                targetState = targetState,
                onDismiss = { pendingUse = null },
                onActivate = { targetId ->
                    pendingUse = null
                    runAction("${storeItem.name} is active on the selected ${storeItem.targetType.lowercase()}.") {
                        actions.activateStoreItem(inventoryItem.id, targetId)
                    }
                },
                onGift = { username, giftMessage ->
                    pendingUse = null
                    runAction("Premium digital gift sent to @${username.trim().removePrefix("@")}.") {
                        actions.sendDigitalGift(inventoryItem.id, username, giftMessage)
                    }
                },
            )
        }
    }

    previewItem?.let { item ->
        val ownedPermanent = item.itemType.equals("PERMANENT", true) &&
            inventory.any { it.catalogId == item.id && it.status.equals("PERMANENT", true) }
        val vipLocked = item.vipOnly && !vipActive
        val displayPrice = if (vipActive) (item.price * 90) / 100 else item.price
        DesktopStorePreviewDialog(
            item = item,
            balance = balance,
            vipActive = vipActive,
            owned = ownedPermanent,
            working = working,
            onDismiss = { previewItem = null },
            onBuy = { purchase(item) },
            buyEnabled = !working && !ownedPermanent && !vipLocked && balance >= displayPrice,
        )
    }
}

@Composable
private fun DesktopCoinPackConfirmation(
    offer: BlinkCoinCheckoutOffer,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Buy Blink Coins", fontWeight = FontWeight.Black) },
        text = {
            Text(
                "Purchase ${offer.coins} coins for ₦${offer.priceNgn} through secure provider-hosted checkout. Coins are credited only after server payment verification. This purchase is optional."
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Continue securely") } },
        dismissButton = { OutlinedButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun DesktopStoreGroupCard(
    group: BlinkStoreProductGroup,
    catalog: List<DesktopStoreItem>,
    inventory: List<DesktopInventoryItem>,
    equippedIds: Set<String>,
    vipActive: Boolean,
    onOpen: () -> Unit,
) {
    val catalogById = catalog.associateBy { it.id }
    val primary = catalogById[group.primaryItemId] ?: return
    val variants = group.itemIds.mapNotNull(catalogById::get)
    val experience = primary.premiumExperience()
    val accent = desktopPremiumAccent(experience)
    val applied = variants.any { it.id in equippedIds }
    val active = variants.any { variant ->
        inventory.any { it.catalogId == variant.id && it.status.equals("ACTIVE", true) }
    }
    val ownedCount = variants.count { variant ->
        inventory.any {
            it.catalogId == variant.id &&
                it.status.uppercase() in setOf("PERMANENT", "AVAILABLE", "ACTIVE")
        }
    }
    val displayPrice = if (vipActive) (primary.price * 90) / 100 else primary.price

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = .32f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DesktopStoreLivePreview(
                catalogId = primary.id,
                itemName = group.title,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp).padding(bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 15.sp)
                    if (group.id == "blink_vip") Text("👑", fontSize = 15.sp)
                }
                Text(
                    group.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (variants.size > 1) "From $displayPrice coins" else "$displayPrice coins",
                        modifier = Modifier.weight(1f),
                        color = accent,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${variants.size} ${if (variants.size == 1) "option" else "options"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (applied || active || ownedCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            applied -> PremiumStatusPill("APPLIED", Color(0xFF16A34A))
                            active -> PremiumStatusPill("LIVE", Color(0xFF16A34A))
                        }
                        if (ownedCount > 0) PremiumStatusPill("$ownedCount OWNED", accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopStoreGroupDialog(
    group: BlinkStoreProductGroup,
    catalog: List<DesktopStoreItem>,
    inventory: List<DesktopInventoryItem>,
    equippedIds: Set<String>,
    balance: Long,
    vipActive: Boolean,
    working: Boolean,
    onDismiss: () -> Unit,
    onPreview: (DesktopStoreItem) -> Unit,
    onBuy: (DesktopStoreItem) -> Unit,
) {
    val catalogById = catalog.associateBy { it.id }
    val variants = group.itemIds.mapNotNull(catalogById::get)
    val primary = catalogById[group.primaryItemId] ?: variants.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.title, fontWeight = FontWeight.Black)
                Text(
                    "${variants.size} ${if (variants.size == 1) "option" else "options"} • choose a variant",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                primary?.let { preview ->
                    item {
                        DesktopStoreLivePreview(
                            catalogId = preview.id,
                            itemName = group.title,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                    }
                }
                item {
                    Text(group.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(variants, key = { it.id }) { item ->
                    val experience = item.premiumExperience()
                    val accent = desktopPremiumAccent(experience)
                    val ownedPermanent = item.itemType.equals("PERMANENT", true) &&
                        inventory.any { it.catalogId == item.id && it.status.equals("PERMANENT", true) }
                    val active = inventory.any { it.catalogId == item.id && it.status.equals("ACTIVE", true) }
                    val equipped = item.id in equippedIds
                    val vipLocked = item.vipOnly && !vipActive
                    val displayPrice = if (vipActive) (item.price * 90) / 100 else item.price

                    Surface(
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
                        tonalElevation = 1.dp,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Black)
                                    Text(
                                        experience.benefit,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when {
                                    equipped -> PremiumStatusPill("APPLIED", Color(0xFF16A34A))
                                    active -> PremiumStatusPill("LIVE", Color(0xFF16A34A))
                                    ownedPermanent -> PremiumStatusPill("OWNED", accent)
                                    item.vipOnly -> PremiumStatusPill("VIP", Color(0xFFF59E0B))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("$displayPrice coins", modifier = Modifier.weight(1f), color = accent, fontWeight = FontWeight.Black)
                                OutlinedButton(onClick = { onPreview(item) }) { Text("Preview") }
                                Button(
                                    onClick = { onBuy(item) },
                                    enabled = !working && !ownedPermanent && !vipLocked && balance >= displayPrice,
                                ) {
                                    Text(
                                        when {
                                            ownedPermanent -> "Owned"
                                            vipLocked -> "VIP required"
                                            else -> "Buy"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DesktopStorePreviewDialog(
    item: DesktopStoreItem,
    balance: Long,
    vipActive: Boolean,
    owned: Boolean,
    working: Boolean,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    buyEnabled: Boolean,
) {
    val experience = item.premiumExperience()
    val accent = desktopPremiumAccent(experience)
    val displayPrice = if (vipActive) (item.price * 90) / 100 else item.price
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, fontWeight = FontWeight.Black)
                Text(experience.label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("BEFORE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                    Text("Standard Blink surface", Modifier.fillMaxWidth().padding(13.dp), fontSize = 12.sp)
                }
                Text("WITH EFFECT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = accent)
                DesktopStoreLivePreview(item.id, item.name)
                Text(experience.benefit, fontSize = 12.sp)
                Text("Visible on: ${experience.visibleAt}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(experience.activationHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$displayPrice Blink Coins", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Balance: $balance", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (vipActive) Text("VIP price • 10% off the ${item.price}-coin standard price", fontSize = 9.sp, color = accent)
                    }
                    PremiumStatusPill(
                        if (experience.publicFacing) "PUBLIC" else "PRIVATE",
                        accent,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onBuy, enabled = buyEnabled) {
                Text(
                    when {
                        owned -> "Owned"
                        item.vipOnly && !vipActive -> "VIP required"
                        balance < displayPrice -> "Not enough coins"
                        working -> "Working…"
                        else -> "Buy"
                    },
                )
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PremiumStatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .28f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun DesktopStoreUseDialog(
    inventoryItem: DesktopInventoryItem,
    storeItem: DesktopStoreItem,
    targetState: JSONObject,
    onDismiss: () -> Unit,
    onActivate: (String) -> Unit,
    onGift: (String, String) -> Unit,
) {
    var recipient by remember(inventoryItem.id) { mutableStateOf("") }
    var giftMessage by remember(inventoryItem.id) { mutableStateOf("") }
    val experience = storeItem.premiumExperience()
    val key = when (storeItem.targetType.uppercase()) {
        "POST", "REEL" -> "posts"
        "MARKETPLACE" -> "market"
        "COMMENT" -> "comments"
        else -> ""
    }
    val targets = targetState.optJSONArray(key)
        .objectList()
        .filter {
            when (storeItem.targetType.uppercase()) {
                "POST" -> it.optString("type").equals("POST", true)
                "REEL" -> it.optString("type").equals("REEL", true)
                else -> true
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use ${storeItem.name}", fontWeight = FontWeight.Black) },
        text = {
            if (storeItem.id == "digital_gift") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(experience.benefit, fontSize = 12.sp)
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Recipient username") },
                        placeholder = { Text("@username") },
                    )
                    OutlinedTextField(
                        value = giftMessage,
                        onValueChange = { giftMessage = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Message (optional)") },
                        supportingText = { Text("${giftMessage.length}/200") },
                        maxLines = 4,
                    )
                }
            } else if (targets.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No eligible ${storeItem.targetType.lowercase()} is available yet.")
                    Text(experience.activationHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose exactly where this premium effect should appear.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(targets.take(30), key = { it.optString("id") }) { row ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onActivate(row.optString("id")) },
                                shape = RoundedCornerShape(15.dp),
                                tonalElevation = 1.dp,
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(row.optString("type").ifBlank { storeItem.targetType }.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Black)
                                    Text(
                                        row.optString("text").ifBlank { "Untitled" },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (storeItem.id == "digital_gift") {
                Button(
                    onClick = { onGift(recipient, giftMessage) },
                    enabled = recipient.trim().removePrefix("@").isNotBlank(),
                ) { Text("Send gift") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        dismissButton = {
            if (storeItem.id == "digital_gift") OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun JSONArray?.objectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList { for (index in 0 until length()) optJSONObject(index)?.let(::add) }
}

private fun shortDesktopDate(raw: String): String = raw.replace('T', ' ').take(16).ifBlank { "—" }

@Composable
fun AdminProScreen(state: DesktopAppState) {
    val actions = remember(state.client) { DesktopRpcActions(state.client) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var sections by remember { mutableStateOf(JSONArray()) }
    var userQuery by remember { mutableStateOf("") }
    var userMatches by remember { mutableStateOf(JSONArray()) }
    var userSearchLoading by remember { mutableStateOf(false) }
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
            Text("Find user", fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(
                "Use this whenever an admin action needs a person. Search by name or @username instead of copying database IDs.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = { userQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Name or @username") },
                )
                Button(
                    onClick = {
                        scope.launch {
                            userSearchLoading = true
                            runCatching { actions.adminGlobalSearch(userQuery) }
                                .onSuccess {
                                    userMatches = it.optJSONArray("users") ?: JSONArray()
                                    error = null
                                }
                                .onFailure { error = it.message }
                            userSearchLoading = false
                        }
                    },
                    enabled = userQuery.isNotBlank() && !userSearchLoading,
                ) {
                    if (userSearchLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Search users")
                    }
                }
            }
        }

        if (userMatches.length() > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("User results", fontWeight = FontWeight.Bold)
                    repeat(minOf(userMatches.length(), 8)) { index ->
                        val user = userMatches.optJSONObject(index) ?: return@repeat
                        val username = user.optString("username")
                        val fullName = user.optString("full_name").ifBlank { username }
                        val university = user.optString("university")
                        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(fullName, fontWeight = FontWeight.Bold)
                                Text(
                                    buildList {
                                        if (username.isNotBlank()) add("@$username")
                                        if (university.isNotBlank()) add(university)
                                    }.joinToString(" • "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

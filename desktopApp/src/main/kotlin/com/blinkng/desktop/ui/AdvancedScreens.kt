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
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUse by remember { mutableStateOf<DesktopInventoryItem?>(null) }
    var previewItem by remember { mutableStateOf<DesktopStoreItem?>(null) }
    var giftStoreItem by remember { mutableStateOf<DesktopStoreItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
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
                    message = "${item.name} purchased. It is now in My Collection."
                    error = null
                    previewItem = null
                    reload()
                }
                .onFailure { error = it.message }
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
    val vip = serverState?.optJSONObject("vip")
    val vipActive = vip?.optBoolean("active", false) == true
    val xpLevel = serverState?.optInt("xp_level", 1)?.coerceAtLeast(1) ?: 1
    val ownedIds = inventory.map(DesktopInventoryItem::catalogId).toSet()
    val ownedCategories = catalog.filter { it.id in ownedIds || it.id in equippedIds }.map(DesktopStoreItem::category).toSet()
    val wishlistIds = serverState?.optJSONArray("wishlist")?.let { array ->
        buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.orEmpty()
    val savedLooks = serverState?.optJSONArray("saved_looks").objectList()
    val transactions = serverState?.optJSONArray("transactions").objectList()
    val filteredCatalog = remember(catalog, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) catalog else catalog.filter {
            it.name.contains(query, true) ||
                it.description.contains(query, true) ||
                it.category.contains(query, true)
        }
    }
    val recommendations = remember(catalog, inventory, equippedIds, wishlistIds, vipActive) {
        catalog.asSequence()
            .filter { it.id !in ownedIds }
            .filter { !it.vipOnly || vipActive }
            .sortedWith(
                compareByDescending<DesktopStoreItem> { it.category in ownedCategories }
                    .thenByDescending { it.id in wishlistIds }
                    .thenBy { it.price }
            )
            .take(6)
            .toList()
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
                            Text("$balance Blink Coins • Preview → Buy → Collection → Use / Apply", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                        }
                        if (working) CircularProgressIndicator(modifier = Modifier.size(25.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                    Text(
                        "Premium should be visible. Public cosmetics now tell you exactly where they appear, while timed items only start when you activate them.",
                        color = Color.White.copy(alpha = .92f),
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PremiumStatusPill(if (vipActive) "VIP ACTIVE" else "VIP READY", Color.White)
                        PremiumStatusPill("${catalog.size} STORE ITEMS", Color.White)
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

        serverState?.let { stateJson ->
            item {
                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VIP & premium identity", fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !working) { Text("Refresh") }
                        }
                        stateJson.optJSONObject("vip")?.let { vipJson ->
                            Text(if (vipJson.optBoolean("active")) "VIP is live on your public identity." else "VIP is inactive. Buy it into My Collection and activate when ready.")
                            vipJson.optString("expires_at").takeIf(String::isNotBlank)?.let { Text("VIP expires: ${shortDesktopDate(it)}", fontSize = 12.sp) }
                        }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { runAction("VIP extended by 30 days.") { actions.renewVip() } }, enabled = !working) { Text("Renew VIP") }
                            OutlinedButton(onClick = { runAction("VIP auto-renew enabled.") { actions.setVipAutoRenew(true) } }, enabled = !working) { Text("Auto-renew on") }
                            OutlinedButton(onClick = { runAction("VIP auto-renew disabled.") { actions.setVipAutoRenew(false) } }, enabled = !working) { Text("Auto-renew off") }
                        }
                    }
                }
            }
        }

        item { Text("Store", fontWeight = FontWeight.Black, fontSize = 21.sp) }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, null) },
                label = { Text("Search effects, themes and boosts") },
            )
        }
        if (loading) item { Text("Loading Store…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!loading && filteredCatalog.isEmpty()) item {
            Text("No Store items match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (searchQuery.isBlank() && recommendations.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recommended for your look", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Based only on Store items you own or equipped.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recommendations.take(4).forEach { recommended ->
                            OutlinedButton(onClick = { previewItem = recommended }) {
                                Text(recommended.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        items(filteredCatalog, key = { "pro-store-${it.id}" }) { item ->
            val experience = item.premiumExperience()
            val accent = desktopPremiumAccent(experience)
            val ownedPermanent = item.itemType.equals("PERMANENT", true) && inventory.any { it.catalogId == item.id && it.status.equals("PERMANENT", true) }
            val active = inventory.any { it.catalogId == item.id && it.status.equals("ACTIVE", true) }
            val equipped = item.id in equippedIds
            val vipLocked = item.vipOnly && !vipActive
            val displayPrice = if (vipActive) (item.price * 90) / 100 else item.price

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { previewItem = item },
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, accent.copy(alpha = .34f)),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = .12f)) {
                            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(25.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(experience.benefit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (equipped) PremiumStatusPill("APPLIED", Color(0xFF16A34A))
                        else if (active) PremiumStatusPill("LIVE", Color(0xFF16A34A))
                        else if (ownedPermanent) PremiumStatusPill("IN COLLECTION", accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PremiumStatusPill(
                            if (experience.publicFacing) "PUBLIC • ${experience.label}" else "PRIVATE • ${experience.label}",
                            accent,
                        )
                        PremiumStatusPill(item.itemType.replace('_', ' '), MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.rarity != "STANDARD") PremiumStatusPill(item.rarity, accent)
                    }
                    item.unlockLevel?.let { required ->
                        Text(
                            if (xpLevel >= required) "✓ Level $required reward unlocked — claim it free in Preview"
                            else "Earn it free at BLINK Level $required • your level: $xpLevel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (xpLevel >= required) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.availabilityLabel()?.let {
                        Text(
                            it,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (item.isAvailableNow()) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text("Seen / used on: ${experience.visibleAt}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(experience.activationHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$displayPrice coins", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            if (vipActive) Text("VIP price • 10% off the ${item.price}-coin standard price", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.boostMultipliers.isNotEmpty()) Text("Strength: ${item.boostMultipliers.joinToString(" • ") { "${it}×" }}", fontSize = 10.sp, color = accent)
                        }
                        OutlinedButton(
                            onClick = {
                                runAction(
                                    if (item.id in wishlistIds) "${item.name} removed from Wishlist."
                                    else "${item.name} saved to Wishlist."
                                ) { actions.setWishlistItem(item.id, item.id !in wishlistIds) }
                            },
                            enabled = !working,
                        ) { Text(if (item.id in wishlistIds) "♥ Saved" else "♡ Wishlist") }
                        Button(
                            onClick = { previewItem = item },
                            enabled = !working && !vipLocked,
                        ) {
                            Text(
                                when {
                                    vipLocked -> "VIP required"
                                    ownedPermanent -> "Preview owned"
                                    else -> "Preview & buy"
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Collection", fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("Preview, equip and combine owned cosmetics. Timed items wait until Use.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        runAction("Current premium look saved.") {
                            actions.saveCurrentLook("Look ${savedLooks.size + 1}")
                        }
                    },
                    enabled = !working && equippedIds.isNotEmpty() && savedLooks.size < 8,
                ) { Text("Save look") }
            }
        }
        if (savedLooks.isNotEmpty()) {
            item { Text("Saved Looks", fontWeight = FontWeight.Black, fontSize = 18.sp) }
            items(savedLooks, key = { "saved-look-${it.optString("id")}" }) { look ->
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(look.optString("name", "Saved look"), fontWeight = FontWeight.Bold)
                            Text("${look.optInt("item_count", 0)} cosmetics", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { runAction("Saved look deleted.") { actions.deleteSavedLook(look.optString("id")) } },
                            enabled = !working,
                        ) { Text("Delete") }
                        Button(
                            onClick = { runAction("${look.optString("name", "Saved look")} applied.") { actions.applySavedLook(look.optString("id")) } },
                            enabled = !working,
                        ) { Text("Apply") }
                    }
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

                    if (storeItem != null) {
                        OutlinedButton(
                            onClick = { previewItem = storeItem },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Preview") }
                    }

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
        item {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Purchase History", fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(
                    "Receipts, duration, expiry and current Store status.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (transactions.isEmpty()) {
            item { Text("No Store activity yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(transactions, key = { "store-history-${it.optString("id")}" }) { row ->
                val amount = row.optLong("amount")
                val duration = row.optLong("catalog_duration_seconds", 0L)
                val itemType = row.optString("catalog_item_type")
                val status = row.optString("current_inventory_status")
                val activatedAt = row.optString("current_inventory_activated_at")
                val expiresAt = row.optString("current_inventory_expires_at")
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    row.optString("item_name").ifBlank { row.optString("kind").replace('_', ' ') },
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${row.optString("kind").replace('_', ' ')} • ${shortDesktopDate(row.optString("created_at"))}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (amount >= 0) "+$amount coins" else "${-amount} coins",
                                fontWeight = FontWeight.Black,
                                color = if (amount >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (row.has("catalog_id") && !row.isNull("catalog_id")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (itemType.isNotBlank()) PremiumStatusPill(itemType.replace('_', ' '), MaterialTheme.colorScheme.onSurfaceVariant)
                                if (status.isNotBlank()) PremiumStatusPill(
                                    status.replace('_', ' '),
                                    if (status == "ACTIVE" || status == "PERMANENT") Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                                )
                            }
                            when {
                                duration > 0L -> Text("Duration: ${desktopStoreDurationText(duration)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                itemType == "PERMANENT" -> Text("Duration: Permanent", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            activatedAt.takeIf(String::isNotBlank)?.let { Text("Activated: ${shortDesktopDate(it)}", fontSize = 10.sp) }
                            expiresAt.takeIf(String::isNotBlank)?.let { Text("Expires: ${shortDesktopDate(it)}", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }

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
            xpLevel = xpLevel,
            onDismiss = { previewItem = null },
            onBuy = { purchase(item) },
            buyEnabled = !working && !ownedPermanent && !vipLocked && item.isAvailableNow() && balance >= displayPrice,
            onGift = if (item.canGiftCosmetic() && item.isAvailableNow()) {
                {
                    previewItem = null
                    giftStoreItem = item
                }
            } else null,
            onClaimLevelReward = item.unlockLevel?.let { required ->
                {
                    if (xpLevel >= required && !ownedPermanent) {
                        previewItem = null
                        runAction("${item.name} claimed from your Level $required reward.") {
                            actions.claimLevelCosmetic(item.id)
                        }
                    }
                }
            },
        )
    }

    giftStoreItem?.let { item ->
        DesktopCosmeticGiftDialog(
            item = item,
            balance = balance,
            vipActive = vipActive,
            working = working,
            onDismiss = { giftStoreItem = null },
            onConfirm = { username ->
                giftStoreItem = null
                val recipient = username.trim().removePrefix("@")
                runAction("${item.name} sent to @$recipient.") {
                    actions.giftStoreItem(item.id, recipient)
                }
            },
        )
    }
}

@Composable
private fun DesktopStorePreviewDialog(
    item: DesktopStoreItem,
    balance: Long,
    vipActive: Boolean,
    owned: Boolean,
    working: Boolean,
    xpLevel: Int,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    buyEnabled: Boolean,
    onGift: (() -> Unit)? = null,
    onClaimLevelReward: (() -> Unit)? = null,
) {
    val experience = item.premiumExperience()
    val accent = desktopPremiumAccent(experience)
    val displayPrice = if (vipActive) (item.price * 90) / 100 else item.price
    val availableNow = item.isAvailableNow()
    val levelRequirement = item.unlockLevel
    val canClaimLevelReward = !owned && levelRequirement != null && xpLevel >= levelRequirement
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
                item.availabilityLabel()?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (availableNow) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                    )
                }
                levelRequirement?.let { required ->
                    Text(
                        if (canClaimLevelReward) "Level $required reached — claim this permanent cosmetic free."
                        else "Free at BLINK Level $required • your level: $xpLevel. You can still buy it when available.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canClaimLevelReward) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        !availableNow -> item.availabilityLabel() ?: "Unavailable"
                        balance < displayPrice -> "Not enough coins"
                        working -> "Working…"
                        else -> "Buy"
                    },
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canClaimLevelReward && onClaimLevelReward != null) {
                    Button(onClick = onClaimLevelReward, enabled = !working) { Text("Claim free") }
                }
                if (onGift != null) {
                    OutlinedButton(onClick = onGift, enabled = !working) { Text("Gift") }
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun DesktopCosmeticGiftDialog(
    item: DesktopStoreItem,
    balance: Long,
    vipActive: Boolean,
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var username by remember(item.id) { mutableStateOf("") }
    val recipient = username.trim().removePrefix("@")
    val price = if (vipActive) (item.price * 90) / 100 else item.price
    val availableNow = item.isAvailableNow()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gift ${item.name}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DesktopStoreLivePreview(item.id, item.name)
                Text(
                    "The recipient gets this item in My Collection. Timed cosmetics remain inactive until they choose to use them.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(64) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Recipient username") },
                    placeholder = { Text("@username") },
                )
                Text("$price Blink Coins", fontWeight = FontWeight.Black, fontSize = 18.sp)
                if (balance < price) {
                    Text("Need ${price - balance} more Blink Coins", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(recipient) },
                enabled = !working && recipient.isNotBlank() && balance >= price && availableNow,
            ) { Text("Send Gift") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun DesktopStoreItem.isAvailableNow(now: java.time.Instant = java.time.Instant.now()): Boolean {
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

private fun DesktopStoreItem.availabilityLabel(now: java.time.Instant = java.time.Instant.now()): String? {
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

private fun desktopStoreDurationText(seconds: Long): String = when {
    seconds >= 86_400L && seconds % 86_400L == 0L -> {
        val days = seconds / 86_400L
        if (days == 1L) "1 day" else "$days days"
    }
    seconds >= 3_600L && seconds % 3_600L == 0L -> {
        val hours = seconds / 3_600L
        if (hours == 1L) "1 hour" else "$hours hours"
    }
    else -> "$seconds seconds"
}

private fun DesktopStoreItem.canGiftCosmetic(): Boolean =
    !vipOnly &&
        itemType.uppercase() in setOf("PERMANENT", "TIMED") &&
        category !in setOf("Boosts", "Analytics", "Marketplace") &&
        id !in setOf(
            "profile_spotlight_1h", "profile_spotlight_24h",
            "post_spotlight_6h", "post_spotlight_24h",
            "reel_spotlight_6h", "reel_spotlight_24h",
            "discovery_boost_7d", "profile_discovery_boost",
            "birthday_profile_theme",
        )

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

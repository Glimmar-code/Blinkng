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
                            Text(if (vipJson.optBoolean("active")) "VIP is live on your public identity." else "VIP is inactive. Buy it into Vault and activate when ready.")
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
                            OutlinedButton(onClick = { runAction("VIP extended by 10 days.") { actions.renewVip() } }, enabled = !working) { Text("Renew VIP") }
                            OutlinedButton(onClick = { runAction("VIP auto-renew enabled.") { actions.setVipAutoRenew(true) } }, enabled = !working) { Text("Auto-renew on") }
                            OutlinedButton(onClick = { runAction("VIP auto-renew disabled.") { actions.setVipAutoRenew(false) } }, enabled = !working) { Text("Auto-renew off") }
                        }
                    }
                }
            }
        }

        item { Text("Store", fontWeight = FontWeight.Black, fontSize = 21.sp) }
        if (loading) item { Text("Loading Store…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(catalog, key = { "pro-store-${it.id}" }) { item ->
            val experience = item.premiumExperience()
            val accent = desktopPremiumAccent(experience)
            val ownedPermanent = item.itemType.equals("PERMANENT", true) && inventory.any { it.catalogId == item.id && it.status.equals("PERMANENT", true) }
            val active = inventory.any { it.catalogId == item.id && it.status.equals("ACTIVE", true) }
            val equipped = item.id in equippedIds
            val vipLocked = item.vipOnly && !vipActive

            Surface(
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
                        else if (ownedPermanent) PremiumStatusPill("IN VAULT", accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PremiumStatusPill(
                            if (experience.publicFacing) "PUBLIC • ${experience.label}" else "PRIVATE • ${experience.label}",
                            accent,
                        )
                        PremiumStatusPill(item.itemType.replace('_', ' '), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Seen / used on: ${experience.visibleAt}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(experience.activationHint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.price} coins", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            if (vipActive) Text("VIP Store discount is applied by the server.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.boostMultipliers.isNotEmpty()) Text("Strength: ${item.boostMultipliers.joinToString(" • ") { "${it}×" }}", fontSize = 10.sp, color = accent)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    working = true
                                    val multiplier = item.boostMultipliers.firstOrNull()
                                    runCatching { actions.purchaseStoreItem(item.id, 1, multiplier) }
                                        .onSuccess {
                                            message = "${item.name} purchased. It is now in your Vault."
                                            error = null
                                            reload()
                                        }
                                        .onFailure { error = it.message }
                                    working = false
                                }
                            },
                            enabled = !working && !ownedPermanent && !vipLocked && balance >= item.price,
                        ) { Text(if (ownedPermanent) "Owned" else if (vipLocked) "VIP" else "Buy") }
                    }
                }
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

package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.AppSettings
import com.example.data.models.MarketplaceOrder
import com.example.data.models.UserProfile
import com.example.data.repository.ProfessionalRepository
import com.example.data.supabase.SupabaseService
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkPurple
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class ProfessionalCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        val initialSection = intent.getStringExtra("section") ?: "privacy"
        setContent {
            BlinkTheme(darkTheme = true) {
                ProfessionalCenterScreen(
                    initialSection = initialSection,
                    onBack = ::finish
                )
            }
        }
    }
}

private enum class ProfessionalSection(val key: String, val label: String) {
    PRIVACY("privacy", "Privacy"),
    SAFETY("safety", "Safety"),
    MARKET("market", "Orders"),
    GROUPS("groups", "Groups"),
    ACCOUNT("account", "Account")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfessionalCenterScreen(
    initialSection: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfessionalRepository() }
    val service = remember { SupabaseService() }

    var section by rememberSaveable {
        mutableStateOf(
            ProfessionalSection.entries.firstOrNull { it.key == initialSection }
                ?: ProfessionalSection.PRIVACY
        )
    }
    var settings by remember { mutableStateOf(AppSettings()) }
    var blocked by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var orders by remember { mutableStateOf<List<MarketplaceOrder>>(emptyList()) }
    var wishlistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var actionBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        error = null
        runCatching {
            val newSettings = repository.fetchSettings()
            val newBlocked = repository.fetchBlockedProfiles()
            val newOrders = repository.fetchMarketplaceOrders()
            val newWishlist = repository.fetchWishlistIds()
            settings = newSettings
            blocked = newBlocked
            orders = newOrders
            wishlistIds = newWishlist
        }.onFailure { error = it.message ?: "Couldn't load Professional Center." }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Professional Center", fontWeight = FontWeight.Black)
                        Text(
                            "Privacy • safety • orders • account",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = .92f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfessionalSection.entries.forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) },
                        leadingIcon = if (section == item) {
                            { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            AnimatedVisibility(visible = loading) {
                Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            error?.let {
                Surface(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp
                    )
                }
            }

            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    (slideInHorizontally { it / 5 } + fadeIn() + scaleIn(initialScale = .98f)) togetherWith
                        (slideOutHorizontally { -it / 7 } + fadeOut() + scaleOut(targetScale = .98f))
                },
                label = "professionalCenterSection"
            ) { selected ->
                when (selected) {
                    ProfessionalSection.PRIVACY -> PrivacySection(
                        settings = settings,
                        busy = actionBusy,
                        onChange = { updated ->
                            settings = updated
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.updateSettings(updated) }
                                    .onFailure { Toast.makeText(context, it.message ?: "Settings update failed", Toast.LENGTH_SHORT).show() }
                                actionBusy = false
                            }
                        }
                    )

                    ProfessionalSection.SAFETY -> SafetySection(
                        blocked = blocked,
                        busy = actionBusy,
                        onBlock = { username ->
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.blockUser(username) }
                                    .onSuccess { reload() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Block failed", Toast.LENGTH_SHORT).show() }
                                actionBusy = false
                            }
                        },
                        onReport = { username, reason ->
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.reportUser(username, reason) }
                                    .onSuccess { Toast.makeText(context, "Report submitted for review.", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Report failed", Toast.LENGTH_SHORT).show() }
                                actionBusy = false
                            }
                        },
                        onUnblock = { userId ->
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.unblockUser(userId) }
                                    .onSuccess { reload() }
                                actionBusy = false
                            }
                        }
                    )

                    ProfessionalSection.MARKET -> MarketOrdersSection(
                        orders = orders,
                        currentUserId = service.getCurrentUserId().orEmpty(),
                        wishlistCount = wishlistIds.size,
                        busy = actionBusy,
                        onTransition = { orderId, status ->
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.updateOrderStatus(orderId, status) }
                                    .onSuccess { reload() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Order update failed", Toast.LENGTH_SHORT).show() }
                                actionBusy = false
                            }
                        }
                    )

                    ProfessionalSection.GROUPS -> GroupsSection(
                        busy = actionBusy,
                        onCreate = { title, usernames ->
                            scope.launch {
                                actionBusy = true
                                runCatching {
                                    val ids = withContext(Dispatchers.IO) {
                                        usernames.map { username ->
                                            service.fetchProfileByUsername(username.removePrefix("@"))?.id
                                                ?: error("@$username was not found")
                                        }
                                    }
                                    repository.createGroup(title, ids).getOrThrow()
                                }.onSuccess {
                                    Toast.makeText(context, "Group created. It will appear in Messages.", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "Couldn't create group", Toast.LENGTH_LONG).show()
                                }
                                actionBusy = false
                            }
                        }
                    )

                    ProfessionalSection.ACCOUNT -> AccountSection(
                        username = service.getCurrentUsername().orEmpty(),
                        busy = actionBusy,
                        onClearCache = {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Temporary cache cleared.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                actionBusy = true
                                runCatching {
                                    val json = repository.exportAccountData()
                                    withContext(Dispatchers.IO) {
                                        val target = File(
                                            context.getExternalFilesDir(null) ?: context.filesDir,
                                            "blink-account-export-${Instant.now().epochSecond}.json"
                                        )
                                        target.writeText(json)
                                        target.absolutePath
                                    }
                                }.onSuccess { path ->
                                    Toast.makeText(context, "Export saved securely to $path", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_SHORT).show()
                                }
                                actionBusy = false
                            }
                        },
                        onDelete = { confirmation ->
                            scope.launch {
                                actionBusy = true
                                runCatching { repository.deleteAccount(confirmation) }
                                    .onSuccess {
                                        context.getSharedPreferences("blink_user_session", MODE_PRIVATE).edit().clear().apply()
                                        context.getSharedPreferences("blink_auth_prefs", MODE_PRIVATE).edit().clear().apply()
                                        Toast.makeText(context, "Account deleted.", Toast.LENGTH_LONG).show()
                                        onBack()
                                    }
                                    .onFailure {
                                        Toast.makeText(context, it.message ?: "Account deletion failed", Toast.LENGTH_LONG).show()
                                    }
                                actionBusy = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    settings: AppSettings,
    busy: Boolean,
    onChange: (AppSettings) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            PremiumHeader(
                icon = Icons.Default.Lock,
                title = "Privacy & experience",
                subtitle = "These controls are enforced by your live Supabase account settings."
            )
        }
        item {
            SettingsCard("Who can message you") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("everyone", "following", "nobody").forEach { value ->
                        FilterChip(
                            selected = settings.dmPrivacy == value,
                            onClick = { if (!busy) onChange(settings.copy(dmPrivacy = value)) },
                            label = { Text(value.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }
        item { ToggleSetting("Private account", "Restrict profile visibility for non-approved people.", settings.privateAccount, busy) { onChange(settings.copy(privateAccount = it)) } }
        item { ToggleSetting("Online status", "Show when you're active in Blink.", settings.showOnlineStatus, busy) { onChange(settings.copy(showOnlineStatus = it)) } }
        item { ToggleSetting("Read receipts", "Allow chats to show when messages are read.", settings.readReceipts, busy) { onChange(settings.copy(readReceipts = it)) } }
        item { ToggleSetting("Autoplay videos", "Automatically start reels while browsing.", settings.autoplayVideos, busy) { onChange(settings.copy(autoplayVideos = it)) } }
        item { ToggleSetting("Data saver", "Reduce media preloading on mobile data.", settings.dataSaver, busy) { onChange(settings.copy(dataSaver = it)) } }
        item { ToggleSetting("Reduce motion", "Use gentler transitions and fewer looping animations.", settings.reduceMotion, busy) { onChange(settings.copy(reduceMotion = it)) } }
    }
}

@Composable
private fun SafetySection(
    blocked: List<UserProfile>,
    busy: Boolean,
    onBlock: (String) -> Unit,
    onReport: (String, String) -> Unit,
    onUnblock: (String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { PremiumHeader(Icons.Default.Security, "Safety controls", "Blocks remove follows/connections and prevent direct messages.") }
        item {
            SettingsCard("Block or report") {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(40) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text("Report reason (optional unless reporting)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onBlock(username.trim()) },
                        enabled = !busy && username.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Block")
                    }
                    Button(
                        onClick = { onReport(username.trim(), reason.trim()) },
                        enabled = !busy && username.isNotBlank() && reason.trim().length >= 3,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Report")
                    }
                }
            }
        }
        item {
            Text("Blocked accounts (${blocked.size})", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        if (blocked.isEmpty()) {
            item { EmptyProfessionalState("No blocked accounts", "People you block will appear here.") }
        } else {
            items(blocked, key = { it.id.ifBlank { it.username } }) { profile ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .12f)) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.padding(9.dp), tint = BlinkPink)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.fullName.ifBlank { profile.username }, fontWeight = FontWeight.Bold)
                            Text("@${profile.username}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onUnblock(profile.id) }, enabled = !busy) { Text("Unblock") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketOrdersSection(
    orders: List<MarketplaceOrder>,
    currentUserId: String,
    wishlistCount: Int,
    busy: Boolean,
    onTransition: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { PremiumHeader(Icons.Default.ShoppingBag, "Marketplace orders", "$wishlistCount saved marketplace item${if (wishlistCount == 1) "" else "s"}.") }
        if (orders.isEmpty()) {
            item { EmptyProfessionalState("No orders yet", "Orders you buy or sell will appear here with live status controls.") }
        } else {
            items(orders, key = { it.id }) { order ->
                val isSeller = order.sellerId == currentUserId
                SettingsCard(if (isSeller) "Sale order" else "Purchase order") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${order.currency} ${"%,.2f".format(order.totalPrice)}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("Qty ${order.quantity} • ${order.status.uppercase()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(order.id.take(12), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = BlinkPink.copy(alpha = .12f)) {
                            Text(order.status, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val actions = when {
                        isSeller && order.status == "pending" -> listOf("accepted", "declined")
                        isSeller && order.status == "accepted" -> listOf("completed", "cancelled")
                        !isSeller && order.status == "pending" -> listOf("cancelled")
                        !isSeller && order.status == "accepted" -> listOf("completed")
                        else -> emptyList()
                    }
                    if (actions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.forEach { action ->
                                OutlinedButton(
                                    onClick = { onTransition(order.id, action) },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text(action.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsSection(
    busy: Boolean,
    onCreate: (String, List<String>) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var users by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { PremiumHeader(Icons.Default.Groups, "Group chats", "Create a private group with 3–50 Blink members. Blocks are respected server-side.") }
        item {
            SettingsCard("New group") {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    users,
                    { users = it.take(500) },
                    label = { Text("Member usernames") },
                    supportingText = { Text("Comma-separated, e.g. ada, chidi, zara") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val parsed = users.split(',').map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }.distinct()
                Button(
                    onClick = { onCreate(title.trim(), parsed) },
                    enabled = !busy && title.trim().length >= 2 && parsed.size >= 2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text("Create secure group")
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    username: String,
    busy: Boolean,
    onClearCache: () -> Unit,
    onExport: () -> Unit,
    onDelete: (String) -> Unit
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { PremiumHeader(Icons.Default.Security, "Account & security", "Sessions are encrypted locally. Account deletion revokes server sessions before removing the user.") }
        item {
            SettingsCard("Data & storage") {
                ProfessionalAction(Icons.Default.Storage, "Clear temporary cache", "Removes disposable image/network cache, not your account.", onClearCache)
                ProfessionalAction(Icons.Default.CloudDownload, "Export my data", "Creates a JSON export of your profile, posts, messages and marketplace records.", onExport)
            }
        }
        item {
            SettingsCard("Danger zone") {
                ProfessionalAction(
                    Icons.Default.DeleteForever,
                    "Delete account",
                    "Permanent. You must type your username to confirm.",
                    { showDelete = true },
                    dangerous = true
                )
            }
        }
    }

    if (showDelete) {
        var confirmation by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Permanently delete account?", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("This removes your account and revokes active server sessions. Type @$username to confirm.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        confirmation,
                        { confirmation = it.take(60) },
                        label = { Text("Username") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDelete = false; onDelete(confirmation.trim().removePrefix("@")) },
                    enabled = !busy && confirmation.trim().removePrefix("@").equals(username, true)
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PremiumHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f)),
        border = BorderStroke(1.dp, BlinkPurple.copy(alpha = .28f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .13f)) {
                Icon(icon, contentDescription = null, tint = BlinkPink, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    busy: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Text(subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked, enabled = !busy)
        }
    }
}

@Composable
private fun ProfessionalAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    dangerous: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (dangerous) MaterialTheme.colorScheme.error else BlinkPink)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyProfessionalState(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

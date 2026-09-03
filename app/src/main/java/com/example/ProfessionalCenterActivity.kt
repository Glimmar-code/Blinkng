package com.example

import android.content.Context
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.vector.ImageVector
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
        val section = intent.getStringExtra("section") ?: "privacy"
        setContent {
            BlinkTheme(darkTheme = true) {
                ProfessionalCenterScreen(initialSection = section, onBack = ::finish)
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
private fun ProfessionalCenterScreen(initialSection: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProfessionalRepository() }
    val service = remember { SupabaseService() }

    var section by rememberSaveable {
        mutableStateOf(ProfessionalSection.entries.firstOrNull { it.key == initialSection } ?: ProfessionalSection.PRIVACY)
    }
    var settings by remember { mutableStateOf(AppSettings()) }
    var blocked by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var orders by remember { mutableStateOf<List<MarketplaceOrder>>(emptyList()) }
    var wishlistCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        errorText = null
        runCatching {
            settings = repository.fetchSettings()
            blocked = repository.fetchBlockedProfiles()
            orders = repository.fetchMarketplaceOrders()
            wishlistCount = repository.fetchWishlistIds().size
        }.onFailure { errorText = it.message ?: "Couldn't load Professional Center." }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Professional Center", fontWeight = FontWeight.Black)
                        Text("Privacy • safety • marketplace • account", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                    )
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
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

            AnimatedVisibility(loading) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            errorText?.let {
                Surface(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp)
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
                    ProfessionalSection.PRIVACY -> PrivacySection(settings, busy) { updated ->
                        settings = updated
                        scope.launch {
                            busy = true
                            runCatching { repository.updateSettings(updated) }
                                .onFailure { Toast.makeText(context, it.message ?: "Settings update failed", Toast.LENGTH_SHORT).show() }
                            busy = false
                        }
                    }

                    ProfessionalSection.SAFETY -> SafetySection(
                        blocked = blocked,
                        busy = busy,
                        onBlock = { username ->
                            scope.launch {
                                busy = true
                                runCatching { repository.blockUser(username) }
                                    .onSuccess { reload() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Block failed", Toast.LENGTH_SHORT).show() }
                                busy = false
                            }
                        },
                        onReport = { username, reason ->
                            scope.launch {
                                busy = true
                                runCatching { repository.reportUser(username, reason) }
                                    .onSuccess { Toast.makeText(context, "Report submitted.", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Report failed", Toast.LENGTH_SHORT).show() }
                                busy = false
                            }
                        },
                        onUnblock = { userId ->
                            scope.launch {
                                busy = true
                                runCatching { repository.unblockUser(userId) }.onSuccess { reload() }
                                busy = false
                            }
                        }
                    )

                    ProfessionalSection.MARKET -> OrdersSection(
                        orders = orders,
                        currentUserId = service.getCurrentUserId().orEmpty(),
                        wishlistCount = wishlistCount,
                        busy = busy,
                        onStatus = { id, status ->
                            scope.launch {
                                busy = true
                                runCatching { repository.updateOrderStatus(id, status) }
                                    .onSuccess { reload() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Order update failed", Toast.LENGTH_SHORT).show() }
                                busy = false
                            }
                        }
                    )

                    ProfessionalSection.GROUPS -> GroupsSection(busy) { title, usernames ->
                        scope.launch {
                            busy = true
                            runCatching {
                                val ids = withContext(Dispatchers.IO) {
                                    usernames.map { username ->
                                        service.fetchProfileByUsername(username.removePrefix("@"))?.id
                                            ?: error("@$username was not found")
                                    }
                                }
                                repository.createGroup(title, ids).getOrThrow()
                            }.onSuccess {
                                Toast.makeText(context, "Group created. Refresh Messages to see it.", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "Couldn't create group", Toast.LENGTH_LONG).show()
                            }
                            busy = false
                        }
                    }

                    ProfessionalSection.ACCOUNT -> AccountSection(
                        username = service.getCurrentUsername().orEmpty(),
                        busy = busy,
                        onClearCache = {
                            scope.launch(Dispatchers.IO) {
                                context.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Temporary cache cleared.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                busy = true
                                runCatching {
                                    val json = repository.exportAccountData()
                                    withContext(Dispatchers.IO) {
                                        val file = File(
                                            context.getExternalFilesDir(null) ?: context.filesDir,
                                            "blink-account-export-${Instant.now().epochSecond}.json"
                                        )
                                        file.writeText(json)
                                        file.absolutePath
                                    }
                                }.onSuccess { path ->
                                    Toast.makeText(context, "Export saved to $path", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_SHORT).show()
                                }
                                busy = false
                            }
                        },
                        onDelete = { confirmation ->
                            scope.launch {
                                busy = true
                                runCatching { repository.deleteAccount(confirmation) }
                                    .onSuccess {
                                        context.getSharedPreferences("blink_user_session", Context.MODE_PRIVATE).edit().clear().apply()
                                        context.getSharedPreferences("blink_auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                        Toast.makeText(context, "Account deleted.", Toast.LENGTH_LONG).show()
                                        onBack()
                                    }
                                    .onFailure { Toast.makeText(context, it.message ?: "Deletion failed", Toast.LENGTH_LONG).show() }
                                busy = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(settings: AppSettings, busy: Boolean, onChange: (AppSettings) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Header(Icons.Default.Lock, "Privacy & experience", "These controls are saved to your live Blink account.") }
        item {
            Panel("Who can message you") {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("everyone", "following", "nobody").forEach { value ->
                        FilterChip(
                            selected = settings.dmPrivacy == value,
                            onClick = { if (!busy) onChange(settings.copy(dmPrivacy = value)) },
                            label = { Text(value.replaceFirstChar(Char::uppercase)) }
                        )
                    }
                }
            }
        }
        item { ToggleRow("Private account", "Restrict profile visibility.", settings.privateAccount, busy) { onChange(settings.copy(privateAccount = it)) } }
        item { ToggleRow("Online status", "Show when you're active.", settings.showOnlineStatus, busy) { onChange(settings.copy(showOnlineStatus = it)) } }
        item { ToggleRow("Read receipts", "Show when chats are read.", settings.readReceipts, busy) { onChange(settings.copy(readReceipts = it)) } }
        item { ToggleRow("Autoplay videos", "Automatically play reels.", settings.autoplayVideos, busy) { onChange(settings.copy(autoplayVideos = it)) } }
        item { ToggleRow("Data saver", "Reduce media preloading.", settings.dataSaver, busy) { onChange(settings.copy(dataSaver = it)) } }
        item { ToggleRow("Reduce motion", "Use gentler animations.", settings.reduceMotion, busy) { onChange(settings.copy(reduceMotion = it)) } }
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
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Header(Icons.Default.Security, "Safety", "Blocks remove follows/connections and prevent DMs.") }
        item {
            Panel("Block or report") {
                OutlinedTextField(username, { username = it.take(40) }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("Report reason") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onBlock(username.trim()) }, enabled = !busy && username.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Block")
                    }
                    Button(onClick = { onReport(username.trim(), reason.trim()) }, enabled = !busy && username.isNotBlank() && reason.trim().length >= 3, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Report")
                    }
                }
            }
        }
        item { Text("Blocked accounts (${blocked.size})", fontWeight = FontWeight.Black) }
        if (blocked.isEmpty()) item { EmptyState("No blocked accounts", "Blocked people will appear here.") }
        items(blocked, key = { it.id.ifBlank { it.username } }) { profile ->
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BlinkPink, modifier = Modifier.size(36.dp))
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

@Composable
private fun OrdersSection(
    orders: List<MarketplaceOrder>,
    currentUserId: String,
    wishlistCount: Int,
    busy: Boolean,
    onStatus: (String, String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Header(Icons.Default.ShoppingBag, "Marketplace orders", "$wishlistCount saved item${if (wishlistCount == 1) "" else "s"}.") }
        if (orders.isEmpty()) item { EmptyState("No orders yet", "Buyer and seller orders will appear here.") }
        items(orders, key = { it.id }) { order ->
            val seller = order.sellerId == currentUserId
            Panel(if (seller) "Sale order" else "Purchase order") {
                Text("${order.currency} ${"%,.2f".format(order.totalPrice)}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Qty ${order.quantity} • ${order.status.uppercase()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val actions = when {
                    seller && order.status == "pending" -> listOf("accepted", "declined")
                    seller && order.status == "accepted" -> listOf("completed", "cancelled")
                    !seller && order.status == "pending" -> listOf("cancelled")
                    !seller && order.status == "accepted" -> listOf("completed")
                    else -> emptyList()
                }
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { action ->
                            OutlinedButton(onClick = { onStatus(order.id, action) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text(action.replaceFirstChar(Char::uppercase), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsSection(busy: Boolean, onCreate: (String, List<String>) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var users by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Header(Icons.Default.Groups, "Group chats", "Create a secure group with 3–50 Blink members.") }
        item {
            Panel("New group") {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(users, { users = it.take(500) }, label = { Text("Member usernames") }, supportingText = { Text("Comma-separated usernames") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val parsed = users.split(',').map { it.trim().removePrefix("@") }.filter(String::isNotBlank).distinct()
                Button(onClick = { onCreate(title.trim(), parsed) }, enabled = !busy && title.trim().length >= 2 && parsed.size >= 2, modifier = Modifier.fillMaxWidth()) {
                    if (busy) { CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp); Spacer(Modifier.width(7.dp)) }
                    Text("Create group")
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
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Header(Icons.Default.Security, "Account & security", "Account deletion revokes server sessions first.") }
        item {
            Panel("Data & storage") {
                ActionRow(Icons.Default.Storage, "Clear temporary cache", "Keep account data; remove disposable cache.", onClearCache)
                ActionRow(Icons.Default.CloudDownload, "Export my data", "Export profile, posts, messages and marketplace records.", onExport)
            }
        }
        item {
            Panel("Danger zone") {
                ActionRow(Icons.Default.DeleteForever, "Delete account", "Permanent deletion with username confirmation.", { deleteDialog = true }, dangerous = true)
            }
        }
    }

    if (deleteDialog) {
        var confirmation by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete account permanently?", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Type @$username to confirm.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(confirmation, { confirmation = it.take(60) }, label = { Text("Username") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = { deleteDialog = false; onDelete(confirmation.trim().removePrefix("@")) },
                    enabled = !busy && confirmation.trim().removePrefix("@").equals(username, true)
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Header(icon: ImageVector, title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, BlinkPurple.copy(alpha = .28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .16f))
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = BlinkPink.copy(alpha = .13f)) {
                Icon(icon, contentDescription = null, tint = BlinkPink, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, busy: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked, enabled = !busy)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, dangerous: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
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
private fun EmptyState(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, contentDescription = null)
            Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

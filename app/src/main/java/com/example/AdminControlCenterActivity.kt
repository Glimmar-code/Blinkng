package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.*
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdminControlCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseService.initialize(applicationContext)
        setContent {
            BlinkTheme(darkTheme = true) {
                AdminControlCenter { finish() }
            }
        }
    }
}

private enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    MESSAGE("Message"),
    POSTS("Posts"),
    FEATURES("200 Features")
}

@Composable
private fun AdminControlCenter(onExit: () -> Unit) {
    val service = remember { AdminSupabaseService() }
    var capability by remember { mutableStateOf<AdminCapability?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = service.fetchCapability()
        delay(650)
        result.onSuccess { capability = it }
            .onFailure { error = it.message ?: "Unable to verify admin access." }
    }
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(650)
            onExit()
        }
    }

    when {
        leaving -> SwitchingScreen("Switching to personal account")
        capability == null && error == null -> SwitchingScreen("Switching to admin account")
        error != null -> AccessError(error!!, onExit)
        capability?.isAdmin != true -> AccessError("This account does not have active admin access.", onExit)
        else -> AdminDashboard(capability!!, service) { leaving = true }
    }
}

@Composable
private fun SwitchingScreen(text: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccessError(text: String, onExit: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blink Admin", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.LightGray)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onExit) { Text("Back to personal account") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDashboard(
    capability: AdminCapability,
    service: AdminSupabaseService,
    onExit: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    var status by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onExit)

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blink Admin", fontWeight = FontWeight.Black)
                        Text(
                            if (capability.isOwner) "Overall owner • permanent" else "Admin • temporary",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onExit) { Text("Switch to Personal Account") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminTab.entries.forEach {
                    FilterChip(
                        selected = tab == it,
                        onClick = { tab = it },
                        label = { Text(it.label) }
                    )
                }
            }
            status?.let {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { status = null }) { Text("Dismiss") }
                    }
                }
            }
            when (tab) {
                AdminTab.OVERVIEW -> Overview(service, capability) { status = it }
                AdminTab.USERS -> Users(service, capability) { status = it }
                AdminTab.MESSAGE -> MessageCenter(service) { status = it }
                AdminTab.POSTS -> PostTools(service) { status = it }
                AdminTab.FEATURES -> FeatureCenter(service) { status = it }
            }
        }
    }
}

@Composable
private fun Overview(
    service: AdminSupabaseService,
    capability: AdminCapability,
    status: (String) -> Unit
) {
    var stats by remember { mutableStateOf<AdminDashboardStats?>(null) }
    LaunchedEffect(Unit) {
        service.fetchStats()
            .onSuccess { stats = it }
            .onFailure { status(it.message ?: "Could not load admin stats.") }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Control Center", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                if (capability.isOwner)
                    "Blink is the immutable overall owner. Owner access cannot expire or be removed."
                else "Your admin role is backend-enforced and expires automatically.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
        stats?.let { s ->
            item { StatCard("Users", s.users) }
            item { StatCard("Verified", s.verified) }
            item { StatCard("Active admins", s.activeAdmins) }
            item { StatCard("Live posts", s.posts) }
            item { StatCard("Blink owner posts", s.ownerPosts) }
        } ?: item { CircularProgressIndicator(Modifier.size(28.dp)) }
        item {
            Text(
                "Blink-owner posts receive global first-feed priority. Sponsored admin posts stay inside normal discovery ranking.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), color = BlinkPink, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Users(
    service: AdminSupabaseService,
    capability: AdminCapability,
    status: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var users by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            service.searchUsers(query)
                .onSuccess { users = it }
                .onFailure { status(it.message ?: "Could not load users.") }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Search users") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::load, enabled = !loading) { Text("Search") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.id }) { user ->
                UserAdminCard(user, capability, service, status, ::load)
            }
        }
    }
}

@Composable
private fun UserAdminCard(
    user: AdminUserSummary,
    capability: AdminCapability,
    service: AdminSupabaseService,
    status: (String) -> Unit,
    refresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var coins by remember(user.id) { mutableStateOf("100") }
    var hours by remember(user.id) { mutableStateOf("720") }
    var busy by remember(user.id) { mutableStateOf(false) }

    fun duration() = hours.toIntOrNull()?.coerceIn(1, 8760) ?: 720
    fun act(call: suspend () -> Result<*>, success: String) {
        if (busy) return
        scope.launch {
            busy = true
            call().onSuccess { status(success); refresh() }
                .onFailure { status(it.message ?: "Admin action failed.") }
            busy = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName.ifBlank { user.username }, fontWeight = FontWeight.Black)
                    Text("@${user.username} • ${user.university.ifBlank { "No university" }}", fontSize = 11.sp)
                }
                Text(
                    when (user.adminRole) {
                        "owner" -> "OWNER"
                        "admin" -> "ADMIN"
                        else -> user.verificationBadge.uppercase()
                    },
                    color = BlinkPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Coins: ${user.coins}", fontSize = 12.sp, color = Color.Gray)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    coins,
                    { coins = it.filter { c -> c.isDigit() }.take(7) },
                    Modifier.weight(1f),
                    label = { Text("Coins") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !busy,
                    onClick = {
                        val amount = coins.toLongOrNull()
                        if (amount == null) status("Enter a valid coin amount.")
                        else act({ service.grantCoins(user.id, amount) }, "Coins granted to @${user.username}.")
                    }
                ) { Text("Give") }
            }
            OutlinedTextField(
                hours,
                { hours = it.filter { c -> c.isDigit() }.take(4) },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Duration hours (max 8760)") },
                singleLine = true
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { act({ service.setVerification(user.id, "BLUE", duration()) }, "Blue tick set.") }
                ) { Text("Blue tick") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { act({ service.setVerification(user.id, "GOLD", duration()) }, "Gold tick set.") }
                ) { Text("Gold tick") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { act({ service.setVerification(user.id, "NONE", 1) }, "Verification removed.") }
                ) { Text("Remove tick") }
            }
            if (capability.isOwner) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (user.adminRole == "owner") {
                        Text("Overall owner cannot be removed.", color = Color.Gray, fontSize = 11.sp)
                    } else {
                        Button(
                            enabled = !busy,
                            onClick = { act({ service.grantAdmin(user.id, duration()) }, "Admin access granted.") }
                        ) { Text("Make admin") }
                        OutlinedButton(
                            enabled = !busy && user.adminRole == "admin",
                            onClick = { act({ service.revokeAdmin(user.id) }, "Admin access removed.") }
                        ) { Text("Remove admin") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCenter(service: AdminSupabaseService, status: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var audience by rememberSaveable { mutableStateOf("everyone") }
    var verification by rememberSaveable { mutableStateOf("all") }
    var message by rememberSaveable { mutableStateOf("") }
    var universities by remember { mutableStateOf<List<String>>(emptyList()) }
    var university by rememberSaveable { mutableStateOf("All universities") }
    var universityMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<AdminUserSummary?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        service.fetchUniversities().onSuccess { universities = it }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Send from Blink", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Messages appear in the recipient notification/activity panel from Blink.", color = Color.LightGray, fontSize = 12.sp)
        }
        item {
            Text("Audience", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("everyone" to "Everyone", "university" to "University", "user" to "Specific user").forEach { (k, v) ->
                    FilterChip(selected = audience == k, onClick = { audience = k }, label = { Text(v) })
                }
            }
        }
        if (audience == "university") {
            item {
                Box {
                    Button(onClick = { universityMenu = true }) { Text(university) }
                    DropdownMenu(universityMenu, { universityMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All universities") },
                            onClick = { university = "All universities"; universityMenu = false }
                        )
                        universities.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { university = name; universityMenu = false }
                            )
                        }
                    }
                }
            }
        }
        if (audience == "user") {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query,
                        { query = it; selected = null },
                        Modifier.weight(1f),
                        label = { Text("Find user") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch {
                            service.searchUsers(query, 8)
                                .onSuccess { matches = it }
                                .onFailure { status(it.message ?: "User search failed.") }
                        }
                    }) { Text("Find") }
                }
                selected?.let { Text("Selected: @${it.username}", color = BlinkPink, fontWeight = FontWeight.Bold) }
                matches.take(5).forEach { u ->
                    TextButton(onClick = { selected = u; matches = emptyList() }) {
                        Text("@${u.username} • ${u.fullName}")
                    }
                }
            }
        }
        item {
            Text("Verification filter", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Everybody", "blue" to "Blue tick", "gold" to "Gold tick").forEach { (k, v) ->
                    FilterChip(selected = verification == k, onClick = { verification = k }, label = { Text(v) })
                }
            }
        }
        item {
            OutlinedTextField(
                message,
                { message = it.take(2000) },
                Modifier.fillMaxWidth(),
                label = { Text("Message") },
                minLines = 5
            )
        }
        item {
            Button(
                enabled = !busy && message.isNotBlank() && (audience != "user" || selected != null),
                onClick = {
                    scope.launch {
                        busy = true
                        service.sendAnnouncement(
                            message,
                            if (audience == "user") selected?.id else null,
                            if (audience == "university") university else null,
                            verification
                        ).onSuccess { count ->
                            status("Blink message delivered to $count user${if (count == 1) "" else "s"}.")
                            message = ""
                        }.onFailure { status(it.message ?: "Message failed.") }
                        busy = false
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Send Blink message")
            }
        }
    }
}

@Composable
private fun PostTools(service: AdminSupabaseService, status: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var postId by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("12") }
    var busy by remember { mutableStateOf(false) }

    fun action(name: String) {
        if (postId.isBlank()) {
            status("Enter a post ID first.")
            return
        }
        scope.launch {
            busy = true
            service.postAction(postId, name, weight.toDoubleOrNull() ?: 12.0)
                .onSuccess { status("Post action completed: $name.") }
                .onFailure { status(it.message ?: "Post moderation failed.") }
            busy = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Post moderation", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                "Hide, restore, pin, or promote posts. Sponsored promotion stays inside the normal discovery ranking.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
        item {
            OutlinedTextField(postId, { postId = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Post UUID") }, singleLine = true)
        }
        item {
            OutlinedTextField(
                weight,
                { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                Modifier.fillMaxWidth(),
                label = { Text("Promotion weight") },
                singleLine = true
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { action("hide") }) { Text("Hide") }
                OutlinedButton(enabled = !busy, onClick = { action("restore") }) { Text("Restore") }
                OutlinedButton(enabled = !busy, onClick = { action("pin") }) { Text("Pin") }
                OutlinedButton(enabled = !busy, onClick = { action("unpin") }) { Text("Unpin") }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { action("promote") }) { Text("Promote Sponsored") }
                OutlinedButton(enabled = !busy, onClick = { action("unpromote") }) { Text("Stop promotion") }
            }
        }
    }
}

@Composable
private fun FeatureCenter(service: AdminSupabaseService, status: (String) -> Unit) {
    var allFeatures by remember { mutableStateOf<List<AdminFeature>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var selected by remember { mutableStateOf<AdminFeature?>(null) }

    LaunchedEffect(Unit) {
        service.fetchFeatures()
            .onSuccess { allFeatures = it }
            .onFailure { status(it.message ?: "Could not load the 200 admin features.") }
        loading = false
    }

    val visible = remember(allFeatures, query, category) {
        allFeatures.filter { feature ->
            (category == "all" || feature.category == category) &&
                (query.isBlank() || feature.title.contains(query, ignoreCase = true) || feature.featureId.toString() == query.trim())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("200 Admin Features", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                "Every item is loaded from Supabase and runs a backend-enforced admin function. Owner-only controls are hidden from ordinary admins.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search feature name or number") },
                singleLine = true
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all", "users", "admins", "coins", "verification", "content", "messages", "analytics", "system").forEach { key ->
                    FilterChip(
                        selected = category == key,
                        onClick = { category = key },
                        label = { Text(if (key == "all") "All" else key.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Text(
                "${visible.size} available • ${allFeatures.size} loaded",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.featureId }) { feature ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "#${feature.featureId}  ${feature.title}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    buildString {
                                        append(feature.category.uppercase())
                                        if (feature.ownerOnly) append(" • OWNER ONLY")
                                    },
                                    color = if (feature.ownerOnly) BlinkPink else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { selected = feature }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }

    selected?.let { feature ->
        FeatureExecuteDialog(
            feature = feature,
            service = service,
            onDismiss = { selected = null },
            status = status
        )
    }
}

@Composable
private fun FeatureExecuteDialog(
    feature: AdminFeature,
    service: AdminSupabaseService,
    onDismiss: () -> Unit,
    status: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var targetId by remember(feature.featureId) { mutableStateOf("") }
    var text by remember(feature.featureId) { mutableStateOf("") }
    var amount by remember(feature.featureId) { mutableStateOf("") }
    var duration by remember(feature.featureId) { mutableStateOf("") }
    var extraJson by remember(feature.featureId) { mutableStateOf("{}") }
    var result by remember(feature.featureId) { mutableStateOf("") }
    var busy by remember(feature.featureId) { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("#${feature.featureId}", color = BlinkPink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(feature.title, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text(
                    "${feature.category.uppercase()}${if (feature.ownerOnly) " • OWNER ONLY" else ""}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    "Fill only the inputs this feature needs. The backend validates permissions, UUIDs, limits, and required values.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    targetId,
                    { targetId = it.trim() },
                    Modifier.fillMaxWidth(),
                    label = { Text("Target UUID (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Text / query / message (optional)") },
                    minLines = 2,
                    maxLines = 5
                )
                OutlinedTextField(
                    amount,
                    { value -> amount = value.filter { it.isDigit() || it == '-' }.take(12) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Amount / numeric value (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    duration,
                    { value -> duration = value.filter { it.isDigit() }.take(5) },
                    Modifier.fillMaxWidth(),
                    label = { Text("Duration hours (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    extraJson,
                    { extraJson = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Extra JSON") },
                    supportingText = { Text("Example: {\"reason\":\"Policy violation\"}") },
                    minLines = 3,
                    maxLines = 8
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                result = ""
                                service.executeFeature(
                                    featureId = feature.featureId,
                                    targetId = targetId.ifBlank { null },
                                    text = text.ifBlank { null },
                                    amount = amount.toLongOrNull(),
                                    durationHours = duration.toIntOrNull(),
                                    extraJson = extraJson
                                ).onSuccess {
                                    result = it
                                    status("Feature #${feature.featureId} completed.")
                                }.onFailure {
                                    result = it.message ?: "Admin feature failed."
                                }
                                busy = false
                            }
                        }
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Run feature")
                    }
                    OutlinedButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
                }

                if (result.isNotBlank()) {
                    HorizontalDivider()
                    Text("Backend result", fontWeight = FontWeight.Bold)
                    Text(result, fontSize = 11.sp)
                }
            }
        }
    }
}

package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.*
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class AdminV2Section(
    val key: String,
    val label: String,
    val modules: Set<String>
)

internal val adminV2Sections = listOf(
    AdminV2Section("dashboard", "1. Dashboard", emptySet()),
    AdminV2Section("users", "2. Users", setOf("users")),
    AdminV2Section("account_safety", "3. Account Safety", setOf("account_safety")),
    AdminV2Section("sessions_devices", "4. Sessions & Devices", setOf("sessions_devices")),
    AdminV2Section("admin_roles", "5. Admin Roles", setOf("admin_roles")),
    AdminV2Section("permissions", "6. Permissions", setOf("permissions")),
    AdminV2Section("coins_wallet", "7. Coins & Wallet", setOf("coins_wallet")),
    AdminV2Section("verification", "8. Verification", setOf("verification")),
    AdminV2Section("posts", "9. Posts", setOf("posts")),
    AdminV2Section("reels", "10. Reels", setOf("reels")),
    AdminV2Section("comments", "11. Comments", setOf("comments")),
    AdminV2Section("reports_flags", "12. Reports & Flags", setOf("reports_flags")),
    AdminV2Section("moderation_queue", "13. Moderation Queue", setOf("moderation_queue")),
    AdminV2Section("messages", "14. Messages", setOf("messages")),
    AdminV2Section("notifications", "15. Notifications", setOf("notifications")),
    AdminV2Section("universities", "16. Universities", setOf("universities")),
    AdminV2Section("faculties_departments", "17. Faculties & Departments", setOf("users", "universities")),
    AdminV2Section("categories_interests", "18. Categories & Interests", setOf("posts", "analytics")),
    AdminV2Section("hashtags", "19. Hashtags", setOf("posts")),
    AdminV2Section("ads_monetization", "20. Ads & Monetization", setOf("ads_monetization")),
    AdminV2Section("scheduled_content", "21. Scheduled Content", setOf("scheduled_content")),
    AdminV2Section("analytics", "22. Analytics", setOf("analytics")),
    AdminV2Section("growth", "23. Growth", setOf("growth")),
    AdminV2Section("engagement", "24. Engagement", setOf("engagement")),
    AdminV2Section("trust_safety", "25. Trust & Safety", setOf("trust_safety")),
    AdminV2Section("device_management", "26. Device Management", setOf("sessions_devices", "account_safety")),
    AdminV2Section("api_webhooks", "27. API & Webhooks", setOf("performance_health", "system_security")),
    AdminV2Section("audit_logs", "28. Audit Logs", setOf("audit_logs")),
    AdminV2Section("backup_restore", "29. Backup & Restore", setOf("system_security", "audit_logs")),
    AdminV2Section("feature_flags", "30. Feature Flags", setOf("feature_flags")),
    AdminV2Section("system_security", "31. System & Security", setOf("system_security")),
    AdminV2Section("support_helpdesk", "32. Support & Helpdesk", setOf("support_helpdesk")),
    AdminV2Section("marketplace", "33. Marketplace", setOf("account_safety", "system_security")),
    AdminV2Section("search_discovery", "34. Search & Discovery", setOf("users", "posts", "reels")),
    AdminV2Section("performance_health", "35. Performance & Health", setOf("performance_health")),
    AdminV2Section("advanced_tools", "36. Advanced Tools", setOf("system_security", "feature_flags", "performance_health"))
)

@Composable
fun AdminControlCenterV2(onExit: () -> Unit) {
    val service = remember { AdminProSupabaseService() }
    var capability by remember { mutableStateOf<AdminCapability?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        service.fetchCapability()
            .onSuccess { capability = it }
            .onFailure { error = it.message ?: "Unable to verify admin access." }
    }
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(400)
            onExit()
        }
    }

    when {
        leaving -> AdminV2Loading("Switching to personal account")
        capability == null && error == null -> AdminV2Loading("Opening Blink Admin")
        error != null -> AdminV2AccessError(error!!, onExit)
        capability?.isAdmin != true -> AdminV2AccessError("This account does not have active admin access.", onExit)
        else -> AdminV2Dashboard(capability!!, service) { leaving = true }
    }
}

@Composable
private fun AdminV2Loading(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(30.dp), color = BlinkPink, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.White)
        }
    }
}

@Composable
private fun AdminV2AccessError(text: String, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blink Admin", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.LightGray)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExit) { Text("Back") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminV2Dashboard(
    capability: AdminCapability,
    service: AdminProSupabaseService,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSection by rememberSaveable { mutableStateOf("dashboard") }
    var allFeatures by remember { mutableStateOf<List<ProAdminFeature>>(emptyList()) }
    var loadingFeatures by remember { mutableStateOf(true) }
    var tableQuery by rememberSaveable { mutableStateOf("") }
    var selectedFeature by remember { mutableStateOf<ProAdminFeature?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var globalQuery by rememberSaveable { mutableStateOf("") }
    var globalResults by remember { mutableStateOf<ProAdminSearchBundle?>(null) }
    var globalLoading by remember { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    LaunchedEffect(Unit) {
        service.fetchFeatures()
            .onSuccess { allFeatures = it }
            .onFailure { status = it.message ?: "Could not load admin capabilities." }
        loadingFeatures = false
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blink Admin", fontWeight = FontWeight.Black)
                        Text(
                            if (capability.isOwner) "Overall owner • permanent" else "Admin • permission scoped",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) { Text("History") }
                    TextButton(onClick = onExit) { Text("Personal") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            status?.let { message ->
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, Modifier.weight(1f), fontSize = 10.sp)
                        TextButton(onClick = { status = null }) { Text("Dismiss") }
                    }
                }
            }

            Row(Modifier.fillMaxSize()) {
                AdminV2Sidebar(
                    selectedKey = selectedSection,
                    onSelect = { selectedSection = it; tableQuery = "" },
                    modifier = Modifier.width(138.dp).fillMaxHeight()
                )
                VerticalDivider(color = Color.DarkGray.copy(alpha = .45f))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AdminV2GlobalSearch(
                        query = globalQuery,
                        loading = globalLoading,
                        results = globalResults,
                        onQueryChange = { globalQuery = it },
                        onSearch = {
                            if (globalQuery.isBlank()) {
                                globalResults = null
                            } else {
                                scope.launch {
                                    globalLoading = true
                                    service.globalSearch(globalQuery)
                                        .onSuccess { globalResults = it }
                                        .onFailure { status = it.message ?: "Search failed." }
                                    globalLoading = false
                                }
                            }
                        },
                        onClear = { globalQuery = ""; globalResults = null }
                    )

                    if (selectedSection == "dashboard") {
                        AdminV2Overview(service, capability, allFeatures.size) { status = it }
                    } else {
                        val section = adminV2Sections.first { it.key == selectedSection }
                        val visible = allFeatures.filter { f ->
                            f.module in section.modules &&
                                (tableQuery.isBlank() || f.title.contains(tableQuery, true) ||
                                    f.routeKey.contains(tableQuery, true) || f.featureId.toString() == tableQuery.trim())
                        }
                        AdminV2FeatureTable(
                            section = section,
                            features = visible,
                            totalLoaded = allFeatures.size,
                            loading = loadingFeatures,
                            query = tableQuery,
                            onQueryChange = { tableQuery = it },
                            onOpen = { selectedFeature = it }
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        AdminV2HistoryDialog(
            service = service,
            onDismiss = { showHistory = false },
            onStatus = { status = it }
        )
    }

    selectedFeature?.let { feature ->
        AdminFeatureDialogV2(
            feature = feature,
            service = service,
            onDismiss = { selectedFeature = null },
            onStatus = { status = it }
        )
    }
}

@Composable
private fun AdminV2Sidebar(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier, color = Color(0xFF080A11)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(adminV2Sections, key = { it.key }) { section ->
                val selected = section.key == selectedKey
                Surface(
                    Modifier.fillMaxWidth().clickable { onSelect(section.key) },
                    color = if (selected) BlinkPink.copy(alpha = .20f) else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        section.label,
                        color = if (selected) Color.White else Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminV2GlobalSearch(
    query: String,
    loading: Boolean,
    results: ProAdminSearchBundle?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                onQueryChange,
                Modifier.weight(1f),
                label = { Text("Search users, posts, reels, universities or paste a link") },
                singleLine = true
            )
            Spacer(Modifier.width(5.dp))
            Button(onClick = onSearch, enabled = !loading) { Text(if (loading) "…" else "Search", fontSize = 9.sp) }
            if (results != null) TextButton(onClick = onClear) { Text("Clear", fontSize = 9.sp) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        results?.let { AdminV2SearchPreview(it) }
    }
}

@Composable
private fun AdminV2SearchPreview(results: ProAdminSearchBundle) {
    Card(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (results.users.isEmpty() && results.posts.isEmpty() && results.universities.isEmpty()) {
                Text("No match found.", fontSize = 10.sp)
            }
            results.users.take(3).forEach { u ->
                Text("User • @${u.username} • ${u.fullName} • ${u.university}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
            results.posts.take(3).forEach { p ->
                Text("${if (p.isReel) "Reel" else "Post"} • @${p.username} • ${p.caption.ifBlank { p.text }.take(70)}", fontSize = 9.sp)
            }
            results.universities.take(4).forEach { u -> Text("University • $u", fontSize = 9.sp) }
        }
    }
}

@Composable
private fun AdminV2Overview(
    service: AdminProSupabaseService,
    capability: AdminCapability,
    featureCount: Int,
    onStatus: (String) -> Unit
) {
    var stats by remember { mutableStateOf<AdminDashboardStats?>(null) }
    var recentHistory by remember { mutableStateOf<List<ProAdminHistoryItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        service.fetchStats().onSuccess { stats = it }.onFailure { onStatus(it.message ?: "Stats failed.") }
        service.fetchHistory(8).onSuccess { recentHistory = it }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Control Center", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                if (capability.isOwner) "700 routed admin capabilities • 200 existing + 500 new • searchable targets • reversible history" else "Only backend-authorized tools are shown for your role.",
                color = Color.LightGray,
                fontSize = 10.sp
            )
        }
        item { AdminV2Metric("Available capabilities", featureCount) }
        stats?.let { s ->
            item { AdminV2Metric("Users", s.users) }
            item { AdminV2Metric("Verified", s.verified) }
            item { AdminV2Metric("Active admins", s.activeAdmins) }
            item { AdminV2Metric("Live posts", s.posts) }
        } ?: item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("Recent admin history", color = Color.White, fontWeight = FontWeight.Black) }
        items(recentHistory, key = { it.id }) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(9.dp)) {
                    Text(h.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text("@${h.actorUsername} • ${h.createdAt}", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminV2Metric(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), color = BlinkPink, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AdminV2FeatureTable(
    section: AdminV2Section,
    features: List<ProAdminFeature>,
    totalLoaded: Int,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (ProAdminFeature) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text(section.label.substringAfter(". "), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("${features.size} tools in this table • $totalLoaded loaded", color = Color.Gray, fontSize = 9.sp)
            OutlinedTextField(
                query,
                onQueryChange,
                Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("Search this table") },
                singleLine = true
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(features, key = { "${section.key}:${it.featureId}" }) { feature ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${feature.featureId} ${feature.title}", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Text(
                                "${feature.routeKey}${if (feature.ownerOnly) " • OWNER" else ""}${if (feature.reversible) " • REVERSIBLE" else ""}",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = { onOpen(feature) }) { Text("Open", fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminV2HistoryDialog(
    service: AdminProSupabaseService,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var historyItems by remember { mutableStateOf<List<ProAdminHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyActionId by remember { mutableStateOf<String?>(null) }

    fun reloadHistory() {
        scope.launch {
            loading = true
            service.fetchHistory(150)
                .onSuccess { historyItems = it }
                .onFailure { onStatus(it.message ?: "Could not load history.") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reloadHistory() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(.94f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin History", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("Global history remains here while you switch tables.", color = Color.Gray, fontSize = 9.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(historyItems, key = { it.id }) { h ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(h.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text(
                                            buildString {
                                                append("@${h.actorUsername}")
                                                h.targetUsername?.let { append(" → @$it") }
                                                append(" • ${h.createdAt}")
                                            },
                                            color = Color.Gray,
                                            fontSize = 8.sp
                                        )
                                    }
                                    if (h.reversed) {
                                        Text("REVERSED", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                    } else if (h.canRevert) {
                                        val restoreLabel = h.action.contains("hide") || h.action.contains("ban") || h.action.contains("suspend") || h.action.contains("disable") || h.action.contains("delete")
                                        OutlinedButton(
                                            enabled = busyActionId == null,
                                            onClick = {
                                                scope.launch {
                                                    busyActionId = h.id
                                                    service.revertAction(h.id, "Reversed from Blink Admin History")
                                                        .onSuccess { onStatus("Admin action reversed."); reloadHistory() }
                                                        .onFailure { onStatus(it.message ?: "Could not reverse action.") }
                                                    busyActionId = null
                                                }
                                            }
                                        ) { Text(if (restoreLabel) "Restore" else "Revoke", fontSize = 8.sp) }
                                    }
                                }
                                if (h.details.isNotBlank()) {
                                    Text(h.details.take(220), color = Color.Gray, fontSize = 8.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
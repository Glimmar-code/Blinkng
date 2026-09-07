package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.NigerianUniversities
import com.example.data.supabase.*
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AdminSection(
    val key: String,
    val label: String,
    val modules: Set<String> = setOf(key)
)

private val adminSections = listOf(
    AdminSection("dashboard", "1. Dashboard", emptySet()),
    AdminSection("users", "2. Users"),
    AdminSection("account_safety", "3. Account Safety"),
    AdminSection("sessions_devices", "4. Sessions & Devices"),
    AdminSection("admin_roles", "5. Admin Roles"),
    AdminSection("permissions", "6. Permissions"),
    AdminSection("coins_wallet", "7. Coins & Wallet"),
    AdminSection("verification", "8. Verification"),
    AdminSection("posts", "9. Posts"),
    AdminSection("reels", "10. Reels"),
    AdminSection("comments", "11. Comments"),
    AdminSection("reports_flags", "12. Reports & Flags"),
    AdminSection("moderation_queue", "13. Moderation Queue"),
    AdminSection("messages", "14. Messages"),
    AdminSection("notifications", "15. Notifications"),
    AdminSection("universities", "16. Universities"),
    AdminSection("faculties_departments", "17. Faculties & Departments", setOf("users", "universities")),
    AdminSection("categories_interests", "18. Categories & Interests", setOf("posts", "analytics")),
    AdminSection("hashtags", "19. Hashtags", setOf("posts")),
    AdminSection("ads_monetization", "20. Ads & Monetization"),
    AdminSection("scheduled_content", "21. Scheduled Content"),
    AdminSection("analytics", "22. Analytics"),
    AdminSection("growth", "23. Growth"),
    AdminSection("engagement", "24. Engagement"),
    AdminSection("trust_safety", "25. Trust & Safety"),
    AdminSection("device_management", "26. Device Management", setOf("sessions_devices", "account_safety")),
    AdminSection("api_webhooks", "27. API & Webhooks", setOf("performance_health", "system_security")),
    AdminSection("audit_logs", "28. Audit Logs"),
    AdminSection("backup_restore", "29. Backup & Restore", setOf("system_security", "audit_logs")),
    AdminSection("feature_flags", "30. Feature Flags"),
    AdminSection("system_security", "31. System & Security"),
    AdminSection("support_helpdesk", "32. Support & Helpdesk"),
    AdminSection("marketplace", "33. Marketplace", setOf("account_safety", "system_security")),
    AdminSection("search_discovery", "34. Search & Discovery", setOf("users", "posts", "reels")),
    AdminSection("performance_health", "35. Performance & Health"),
    AdminSection("advanced_tools", "36. Advanced Tools", setOf("system_security", "feature_flags", "performance_health"))
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
            delay(450)
            onExit()
        }
    }

    when {
        leaving -> AdminSwitchingScreen("Switching to personal account")
        capability == null && error == null -> AdminSwitchingScreen("Opening Blink Admin")
        error != null -> AdminAccessError(error!!, onExit)
        capability?.isAdmin != true -> AdminAccessError("This account does not have active admin access.", onExit)
        else -> AdminProDashboard(capability!!, service) { leaving = true }
    }
}

@Composable
private fun AdminSwitchingScreen(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(30.dp), color = BlinkPink, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AdminAccessError(text: String, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blink Admin", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.LightGray)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onExit) { Text("Back to personal account") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminProDashboard(
    capability: AdminCapability,
    service: AdminProSupabaseService,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var sectionKey by rememberSaveable { mutableStateOf("dashboard") }
    var features by remember { mutableStateOf<List<ProAdminFeature>>(emptyList()) }
    var featureLoading by remember { mutableStateOf(true) }
    var featureQuery by rememberSaveable { mutableStateOf("") }
    var selectedFeature by remember { mutableStateOf<ProAdminFeature?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var globalQuery by rememberSaveable { mutableStateOf("") }
    var globalBundle by remember { mutableStateOf<ProAdminSearchBundle?>(null) }
    var globalLoading by remember { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    LaunchedEffect(Unit) {
        service.fetchFeatures()
            .onSuccess { features = it }
            .onFailure { status = it.message ?: "Could not load admin features." }
        featureLoading = false
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blink Admin", fontWeight = FontWeight.Black)
                        Text(
                            if (capability.isOwner) "Overall owner • permanent" else "Admin • backend enforced",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) { Text("History") }
                    TextButton(onClick = onExit) { Text("Personal") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Color.Black)
        ) {
            status?.let {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), fontSize = 11.sp)
                        TextButton(onClick = { status = null }) { Text("Dismiss") }
                    }
                }
            }

            Row(Modifier.fillMaxSize()) {
                AdminSidebar(
                    selected = sectionKey,
                    onSelect = { sectionKey = it; featureQuery = "" },
                    modifier = Modifier.width(142.dp).fillMaxHeight()
                )
                VerticalDivider(color = Color.DarkGray.copy(alpha = .45f))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    GlobalAdminSearch(
                        query = globalQuery,
                        loading = globalLoading,
                        bundle = globalBundle,
                        onQuery = { globalQuery = it },
                        onSearch = {
                            if (globalQuery.isBlank()) {
                                globalBundle = null
                            } else {
                                scope.launch {
                                    globalLoading = true
                                    service.globalSearch(globalQuery)
                                        .onSuccess { globalBundle = it }
                                        .onFailure { status = it.message ?: "Search failed." }
                                    globalLoading = false
                                }
                            }
                        },
                        onClear = { globalQuery = ""; globalBundle = null }
                    )

                    if (sectionKey == "dashboard") {
                        DashboardPanel(service, features.size, capability, status = { status = it })
                    } else {
                        val section = adminSections.firstOrNull { it.key == sectionKey }
                        val visible = remember(features, sectionKey, featureQuery) {
                            val moduleSet = section?.modules.orEmpty()
                            features.filter { f ->
                                (moduleSet.isEmpty() || f.module in moduleSet) &&
                                    (featureQuery.isBlank() ||
                                        f.title.contains(featureQuery, true) ||
                                        f.routeKey.contains(featureQuery, true) ||
                                        f.featureId.toString() == featureQuery.trim())
                            }
                        }
                        FeatureModulePanel(
                            section = section ?: adminSections[1],
                            features = visible,
                            totalLoaded = features.size,
                            loading = featureLoading,
                            query = featureQuery,
                            onQuery = { featureQuery = it },
                            onOpen = { selectedFeature = it }
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        AdminHistoryDialog(
            service = service,
            onDismiss = { showHistory = false },
            onStatus = { status = it }
        )
    }

    selectedFeature?.let { feature ->
        ProFeatureDialog(
            feature = feature,
            service = service,
            onDismiss = { selectedFeature = null },
            onStatus = { status = it }
        )
    }
}

@Composable
private fun AdminSidebar(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0xFF080A11)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(adminSections, key = { it.key }) { section ->
                val active = selected == section.key
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(section.key) },
                    color = if (active) BlinkPink.copy(alpha = .20f) else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        section.label,
                        color = if (active) Color.White else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalAdminSearch(
    query: String,
    loading: Boolean,
    bundle: ProAdminSearchBundle?,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                label = { Text("Search users, posts, reels, universities, links…") },
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = onSearch, enabled = !loading) { Text(if (loading) "…" else "Search") }
            if (bundle != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        bundle?.let { SearchBundlePreview(it) }
    }
}

@Composable
private fun SearchBundlePreview(bundle: ProAdminSearchBundle) {
    Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bundle.users.isEmpty() && bundle.posts.isEmpty() && bundle.universities.isEmpty()) {
                Text("No matching users, posts or universities.", fontSize = 11.sp)
            }
            bundle.users.take(3).forEach { user ->
                Text(
                    "User  @${user.username} • ${user.fullName} • ${user.university}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            bundle.posts.take(3).forEach { post ->
                Text(
                    "${if (post.isReel) "Reel" else "Post"}  @${post.username} • ${post.caption.ifBlank { post.text }.take(70)}",
                    fontSize = 11.sp
                )
            }
            bundle.universities.take(4).forEach { Text("University  $it", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun DashboardPanel(
    service: AdminProSupabaseService,
    featureCount: Int,
    capability: AdminCapability,
    status: (String) -> Unit
) {
    var stats by remember { mutableStateOf<AdminDashboardStats?>(null) }
    var history by remember { mutableStateOf<List<ProAdminHistoryItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        service.fetchStats().onSuccess { stats = it }.onFailure { status(it.message ?: "Stats failed.") }
        service.fetchHistory(8).onSuccess { history = it }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("Control Center", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                if (capability.isOwner) "Owner controls, searchable entities, reversible history and 700 routed capabilities." else "Your available tools are filtered by your backend permissions.",
                color = Color.LightGray,
                fontSize = 11.sp
            )
        }
        item { MetricCard("Admin capabilities", featureCount) }
        stats?.let { s ->
            item { MetricCard("Users", s.users) }
            item { MetricCard("Verified", s.verified) }
            item { MetricCard("Active admins", s.activeAdmins) }
            item { MetricCard("Live posts", s.posts) }
        } ?: item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Text("Recent admin history", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        items(history, key = { it.id }) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(h.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("@${h.actorUsername} • ${h.createdAt}", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), color = BlinkPink, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeatureModulePanel(
    section: AdminSection,
    features: List<ProAdminFeature>,
    totalLoaded: Int,
    loading: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (ProAdminFeature) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(section.label.substringAfter(". "), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("${features.size} tools in this table • $totalLoaded admin capabilities loaded", color = Color.Gray, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search this admin table") },
                singleLine = true
            )
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(features, key = { "${section.key}:${it.featureId}" }) { feature ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${feature.featureId}  ${feature.title}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "${feature.routeKey} • ${feature.category}${if (feature.ownerOnly) " • OWNER" else ""}${if (feature.reversible) " • REVERSIBLE" else ""}",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { onOpen(feature) }) { Text("Open", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminHistoryDialog(
    service: AdminProSupabaseService,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ProAdminHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            service.fetchHistory(150)
                .onSuccess { items = it }
                .onFailure { onStatus(it.message ?: "Could not load history.") }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(.94f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin History", fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text("History stays available no matter which table you open.", color = Color.Gray, fontSize = 10.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(items, key = { it.id }) { h ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(h.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(
                                            buildString {
                                                append("@${h.actorUsername}")
                                                h.targetUsername?.let { append(" → @$it") }
                                                append(" • ${h.createdAt}")
                                            },
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                    if (h.reversed) {
                                        Text("REVERSED", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                    } else if (h.canRevert) {
                                        val destructiveWasRemoval = h.action.contains("hide") || h.action.contains("ban") || h.action.contains("suspend") || h.action.contains("delete") || h.action.contains("disable")
                                        OutlinedButton(
                                            enabled = busyId == null,
                                            onClick = {
                                                scope.launch {
                                                    busyId = h.id
                                                    service.revertAction(h.id, "Reversed from Blink Admin History")
                                                        .onSuccess { onStatus("Admin action reversed safely."); reload() }
                                                        .onFailure { onStatus(it.message ?: "Could not reverse this action.") }
                                                    busyId = null
                                                }
                                            }
                                        ) { Text(if (destructiveWasRemoval) "Restore" else "Revoke", fontSize = 9.sp) }
                                    }
                                }
                                if (h.details.isNotBlank()) {
                                    Text(h.details.take(220), color = Color.Gray, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureDialog(
    feature: ProAdminFeature,
    service: AdminProSupabaseService,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val kind = feature.inputKind
    var entityRef by remember(feature.featureId) { mutableStateOf("") }
    var text by remember(feature.featureId) { mutableStateOf("") }
    var amount by remember(feature.featureId) { mutableStateOf("") }
    var durationDays by remember(feature.featureId) { mutableIntStateOf(7) }
    var reason by remember(feature.featureId) { mutableStateOf("") }
    var enabled by remember(feature.featureId) { mutableStateOf(true) }
    var badge by remember(feature.featureId) { mutableStateOf("BLUE") }
    var selectedUsers by remember(feature.featureId) { mutableStateOf<List<ProAdminUser>>(emptyList()) }
    var selectedUser by remember(feature.featureId) { mutableStateOf<ProAdminUser?>(null) }
    var selectedPost by remember(feature.featureId) { mutableStateOf<ProAdminPost?>(null) }
    var selectedUniversity by remember(feature.featureId) { mutableStateOf("") }
    var selectedUniversities by remember(feature.featureId) { mutableStateOf<List<String>>(emptyList()) }
    var permissions by remember(feature.featureId) { mutableStateOf(setOf("users", "content")) }
    var configKey by remember(feature.featureId) { mutableStateOf("posting_enabled") }
    var extraA by remember(feature.featureId) { mutableStateOf("") }
    var extraB by remember(feature.featureId) { mutableStateOf("") }
    var assignee by remember(feature.featureId) { mutableStateOf<ProAdminUser?>(null) }
    var result by remember(feature.featureId) { mutableStateOf("") }
    var busy by remember(feature.featureId) { mutableStateOf(false) }

    fun buildOptions(): JSONObject {
        val options = JSONObject()
        if (reason.isNotBlank()) options.put("reason", reason.trim())
        if (kind.contains("toggle") || feature.featureId in listOf(75, 197, 198)) options.put("enabled", enabled)
        if (kind.contains("badge")) options.put("badge", badge)
        if (kind.contains("permissions") || kind == "role_template") options.put("permissions", JSONArray(permissions.toList()))
        if (kind.contains("universities")) options.put("universities", JSONArray(selectedUniversities.ifEmpty { listOfNotNull(selectedUniversity.takeIf { it.isNotBlank() }) }))
        if (kind.contains("bulk_users")) options.put("user_ids", JSONArray(selectedUsers.map { it.id }))
        if (kind == "date_range") {
            if (extraA.isNotBlank()) options.put("from", extraA.trim())
            if (extraB.isNotBlank()) options.put("to", extraB.trim())
        }
        if (kind == "report_assignee" && assignee != null) options.put("admin_id", assignee!!.id)
        if (kind == "campaign_action") {
            if (extraA.isNotBlank()) options.put("label", extraA.trim())
            if (extraB.isNotBlank()) options.put("url", extraB.trim())
        }
        if (kind == "campaign_link" && extraA.isNotBlank()) options.put("link_ref", extraA.trim())
        if (kind == "bonus_config" || kind == "event_config") {
            if (extraA.isNotBlank()) options.put("name", extraA.trim())
            if (extraB.isNotBlank()) options.put("value", extraB.trim())
        }
        return options
    }

    fun resolvedEntityRef(): String? = when {
        feature.targetType == "user" -> selectedUser?.id ?: entityRef.takeIf { it.isNotBlank() }
        feature.targetType == "post" || feature.targetType == "reel" -> selectedPost?.id ?: entityRef.takeIf { it.isNotBlank() }
        else -> entityRef.takeIf { it.isNotBlank() }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(.94f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${feature.featureId}", color = BlinkPink, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(feature.title, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Text(feature.routeKey, color = Color.Gray, fontSize = 9.sp)
                    }
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
                }
                if (feature.description.isNotBlank()) Text(feature.description, color = Color.Gray, fontSize = 10.sp)

                if (kind.contains("user") && !kind.contains("bulk_users")) {
                    UserPicker(service, selectedUser, onSelected = { selectedUser = it })
                }
                if (kind.contains("bulk_users")) {
                    MultiUserPicker(service, selectedUsers, onSelected = { selectedUsers = it })
                }
                if (feature.targetType == "post" || feature.targetType == "reel" || kind.startsWith("post")) {
                    PostPicker(service, selectedPost, onSelected = { selectedPost = it }, reelsOnly = feature.targetType == "reel")
                }
                if (feature.targetType in setOf("comment", "report", "campaign", "verification_request")) {
                    OutlinedTextField(
                        entityRef,
                        { entityRef = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Find ${feature.targetType.replace('_', ' ')} or paste its link/reference") },
                        singleLine = true
                    )
                }
                if (kind.contains("university") && !kind.contains("universities")) {
                    UniversityPicker(service, selectedUniversity, onSelected = { selectedUniversity = it })
                }
                if (kind.contains("universities")) {
                    MultiUniversityPicker(service, selectedUniversities, onSelected = { selectedUniversities = it })
                }
                if (kind == "query") {
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Search query") }, singleLine = true)
                }
                if (kind.contains("message") || kind.contains("note") || kind == "campaign_text" || kind == "version") {
                    OutlinedTextField(
                        text,
                        { text = it.take(2000) },
                        Modifier.fillMaxWidth(),
                        label = { Text(if (kind == "version") "Required app version" else if (kind.contains("note")) "Internal note" else "Message / text") },
                        minLines = if (kind == "version") 1 else 3
                    )
                }
                if (kind.contains("session")) {
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Session reference") }, singleLine = true)
                }
                if (kind.contains("amount")) {
                    OutlinedTextField(
                        amount,
                        { amount = it.filter { c -> c.isDigit() || c == '-' }.take(12) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        singleLine = true
                    )
                }
                if (kind.contains("duration") || kind == "scheduled_message") {
                    Text("Duration", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 7, 30, 90, 365).forEach { days ->
                            FilterChip(selected = durationDays == days, onClick = { durationDays = days }, label = { Text("$days day${if (days == 1) "" else "s"}") })
                        }
                    }
                }
                if (kind.contains("reason")) {
                    OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Reason / internal note") }, minLines = 2)
                }
                if (kind.contains("badge")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BLUE", "GOLD").forEach { b -> FilterChip(selected = badge == b, onClick = { badge = b }, label = { Text(b) }) }
                    }
                }
                if (kind.contains("permissions") || kind == "role_template") {
                    Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    listOf("users", "content", "messages", "coins", "verification", "analytics", "admins").forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = p in permissions, onCheckedChange = { checked -> permissions = if (checked) permissions + p else permissions - p })
                            Text(p.replaceFirstChar { it.uppercase() }, fontSize = 11.sp)
                        }
                    }
                }
                if (kind == "role_template") {
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Role name") }, singleLine = true)
                }
                if (kind == "toggle") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                if (kind == "feature_toggle") {
                    Text("Feature switch", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("posting_enabled", "reels_enabled", "comments_enabled", "messaging_enabled", "verification_enabled", "coin_rewards_enabled", "ads_enabled").forEach { key ->
                            FilterChip(selected = configKey == key, onClick = { configKey = key }, label = { Text(key.removeSuffix("_enabled").replace('_', ' ')) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                if (kind == "emergency") {
                    Text("Emergency mode", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = text != "restore", onClick = { text = "lockdown" }, label = { Text("Lockdown") })
                        FilterChip(selected = text == "restore", onClick = { text = "restore" }, label = { Text("Restore services") })
                    }
                }
                if (kind == "date_range") {
                    OutlinedTextField(extraA, { extraA = it }, Modifier.fillMaxWidth(), label = { Text("From date/time") }, singleLine = true)
                    OutlinedTextField(extraB, { extraB = it }, Modifier.fillMaxWidth(), label = { Text("To date/time") }, singleLine = true)
                }
                if (kind == "report_assignee") {
                    Text("Assign to admin", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    UserPicker(service, assignee, onSelected = { assignee = it })
                }
                if (kind == "campaign_action") {
                    OutlinedTextField(extraA, { extraA = it }, Modifier.fillMaxWidth(), label = { Text("Button label") }, singleLine = true)
                    OutlinedTextField(extraB, { extraB = it }, Modifier.fillMaxWidth(), label = { Text("Button URL") }, singleLine = true)
                }
                if (kind == "campaign_link") {
                    OutlinedTextField(extraA, { extraA = it }, Modifier.fillMaxWidth(), label = { Text("Paste destination link or reference") }, singleLine = true)
                }
                if (kind == "bonus_config" || kind == "event_config") {
                    OutlinedTextField(extraA, { extraA = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(extraB, { extraB = it }, Modifier.fillMaxWidth(), label = { Text("Value / description") }, singleLine = true)
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            result = ""
                            val payloadText = when (kind) {
                                "feature_toggle" -> configKey
                                "emergency" -> text.ifBlank { "lockdown" }
                                else -> text.ifBlank { null }
                            }
                            val ref = when {
                                kind.contains("university") && !kind.contains("universities") -> null
                                else -> resolvedEntityRef()
                            }
                            val options = buildOptions()
                            if (kind.contains("university") && selectedUniversity.isNotBlank()) {
                                if (kind.startsWith("post_university")) {
                                    // Existing feature #132 expects the university in p_text.
                                } else if (kind.startsWith("university_")) {
                                    // Existing university actions also expect p_text.
                                }
                            }
                            val finalText = when {
                                kind.startsWith("university_") || kind == "university" || kind == "post_university_reason" -> selectedUniversity.ifBlank { payloadText }
                                else -> payloadText
                            }
                            service.executeFeature(
                                featureId = feature.featureId,
                                entityRef = ref,
                                text = finalText,
                                amount = amount.toLongOrNull(),
                                durationHours = if (kind.contains("duration") || kind == "scheduled_message") durationDays * 24 else null,
                                options = options
                            ).onSuccess {
                                result = it
                                onStatus("${feature.title} completed.")
                            }.onFailure {
                                result = it.message ?: "Admin action failed."
                            }
                            busy = false
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (kind == "insight" || kind == "none") "Run" else "Apply")
                }

                if (result.isNotBlank()) {
                    HorizontalDivider()
                    Text("Result", fontWeight = FontWeight.Bold)
                    Text(result, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun UserPicker(
    service: AdminProSupabaseService,
    selected: ProAdminUser?,
    onSelected: (ProAdminUser) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProAdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Find user", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Name, @username, email, user ID or profile link") },
                singleLine = true
            )
            Spacer(Modifier.width(5.dp))
            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        service.searchUsers(query, 8).onSuccess { results = it }
                        loading = false
                    }
                }
            ) { Text("Find") }
        }
        selected?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("Selected: @${it.username}", fontWeight = FontWeight.Bold, color = BlinkPink, fontSize = 11.sp)
                    Text("${it.fullName} • ${it.university}", fontSize = 10.sp)
                }
            }
        }
        results.take(6).forEach { u ->
            TextButton(onClick = { onSelected(u); results = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                Text("@${u.username} • ${u.fullName} • ${u.university}", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MultiUserPicker(
    service: AdminProSupabaseService,
    selected: List<ProAdminUser>,
    onSelected: (List<ProAdminUser>) -> Unit
) {
    var latest by remember { mutableStateOf<ProAdminUser?>(null) }
    UserPicker(service, latest, onSelected = { user ->
        latest = user
        if (selected.none { it.id == user.id }) onSelected(selected + user)
    })
    if (selected.isNotEmpty()) {
        Text("Selected users (${selected.size})", fontWeight = FontWeight.Bold, fontSize = 10.sp)
        selected.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${u.username}", Modifier.weight(1f), fontSize = 10.sp)
                TextButton(onClick = { onSelected(selected.filterNot { it.id == u.id }) }) { Text("Remove", fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun PostPicker(
    service: AdminProSupabaseService,
    selected: ProAdminPost?,
    onSelected: (ProAdminPost) -> Unit,
    reelsOnly: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProAdminPost>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (reelsOnly) "Find reel" else "Find post", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Paste link, ID, @username, caption or keyword") },
                singleLine = true
            )
            Spacer(Modifier.width(5.dp))
            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        service.searchPosts(query, 10).onSuccess { all -> results = if (reelsOnly) all.filter { it.isReel } else all }
                        loading = false
                    }
                }
            ) { Text("Find") }
        }
        selected?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("Selected ${if (it.isReel) "reel" else "post"} by @${it.username}", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(it.caption.ifBlank { it.text }.take(120), fontSize = 10.sp)
                    Text("${it.likes} likes • ${it.comments} comments • ${it.views} views", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }
        results.take(6).forEach { p ->
            TextButton(onClick = { onSelected(p); results = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                Text("@${p.username} • ${p.caption.ifBlank { p.text }.take(75)}", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun UniversityPicker(
    service: AdminProSupabaseService,
    selected: String,
    onSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { service.searchUniversities().onSuccess { remote = it } }
    val options = remember(query, remote) {
        (NigerianUniversities.all + remote).distinct().filter { query.isBlank() || it.contains(query, true) }.take(12)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("University", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search university") }, singleLine = true)
        if (selected.isNotBlank()) Text("Selected: $selected", color = BlinkPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        options.forEach { u -> TextButton(onClick = { onSelected(u); query = u }, modifier = Modifier.fillMaxWidth()) { Text(u, fontSize = 10.sp) } }
    }
}

@Composable
private fun MultiUniversityPicker(
    service: AdminProSupabaseService,
    selected: List<String>,
    onSelected: (List<String>) -> Unit
) {
    var latest by remember { mutableStateOf("") }
    UniversityPicker(service, latest, onSelected = { university ->
        latest = university
        if (university !in selected) onSelected(selected + university)
    })
    if (selected.isNotEmpty()) {
        Text("Selected universities (${selected.size})", fontWeight = FontWeight.Bold, fontSize = 10.sp)
        selected.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u, Modifier.weight(1f), fontSize = 10.sp)
                TextButton(onClick = { onSelected(selected - u) }) { Text("Remove", fontSize = 9.sp) }
            }
        }
    }
}
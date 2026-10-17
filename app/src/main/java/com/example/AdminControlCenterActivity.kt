package com.example

import com.blinkng.shared.BlinkDesignTokens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.NigerianUniversities
import com.example.data.supabase.*
import com.example.ui.components.BlinkAdminBugInbox
import com.example.ui.components.BlinkMark
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.BlinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

@Composable
private fun AdminControlCenter(onExit: () -> Unit) {
    val service = remember { AdminSupabaseService() }
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
            delay(350)
            onExit()
        }
    }

    when {
        leaving -> LoadingAdminScreen("Switching to personal account")
        capability == null && error == null -> LoadingAdminScreen("Opening Blink Admin")
        error != null -> AdminAccessError(error.orEmpty(), onExit)
        capability?.isAdmin != true -> AdminAccessError("This account does not have active admin access.", onExit)
        else -> capability?.let { adminCapability ->
            AdminDashboardV3(adminCapability, service) { leaving = true }
        } ?: LoadingAdminScreen("Opening Blink Admin")
    }
}

@Composable
private fun LoadingAdminScreen(text: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(30.dp), color = BlinkPink, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AdminAccessError(text: String, onExit: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blink Admin", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.LightGray)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onExit) { Text("Back to personal account") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDashboardV3(
    capability: AdminCapability,
    service: AdminSupabaseService,
    onExit: () -> Unit
) {
    var sections by remember { mutableStateOf<List<AdminSection>>(emptyList()) }
    var selectedKey by rememberSaveable { mutableStateOf("dashboard") }
    var status by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showBugReports by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    BackHandler(onBack = onExit)

    LaunchedEffect(Unit) {
        loading = true
        service.fetchSectionsV3()
            .onSuccess { sections = it }
            .onFailure { status = it.message ?: "Could not load admin sections." }
        loading = false
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlinkMark(size = 30.dp, showText = false)
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("Blink Admin", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text(
                                if (capability.isOwner) "Owner workspace" else "Admin workspace",
                                fontSize = 15.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showGlobalSearch = true }) {
                        Text("Search", fontSize = 16.sp)
                    }
                    Box {
                        IconButton(onClick = { showMoreActions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More admin actions")
                        }
                        DropdownMenu(
                            expanded = showMoreActions,
                            onDismissRequest = { showMoreActions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bug reports") },
                                onClick = { showMoreActions = false; showBugReports = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Action history") },
                                onClick = { showMoreActions = false; showHistory = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Back to BLINK") },
                                onClick = { showMoreActions = false; onExit() }
                            )
                        }
                    }
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
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, Modifier.weight(1f), fontSize = 15.sp)
                        TextButton(onClick = { status = null }) { Text("Dismiss", fontSize = 15.sp) }
                    }
                }
            }

            if (loading && sections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlinkPink)
                }
            } else {
                val enabledSections = sections.filter { it.showInSidebar && it.enabled }.sortedBy { it.sortOrder }
                val activeGroup = adminNavigationGroup(selectedKey)
                val groups = listOf(
                    "Overview", "People & access", "Content", "Safety", "Messaging",
                    "Coins & commerce", "Campus & connect", "System & insights"
                ).filter { group -> enabledSections.any { adminNavigationGroup(it.key) == group } || group == "Overview" }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = activeGroup == group,
                                onClick = {
                                    selectedKey = if (group == "Overview") "dashboard"
                                    else enabledSections.firstOrNull { adminNavigationGroup(it.key) == group }?.key ?: "dashboard"
                                },
                                label = { Text(group, fontSize = 15.sp) }
                            )
                        }
                    }
                    val groupSections = enabledSections.filter { adminNavigationGroup(it.key) == activeGroup }
                    if (groupSections.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            groupSections.forEach { section ->
                                FilterChip(
                                    selected = selectedKey == section.key,
                                    onClick = { selectedKey = section.key },
                                    label = {
                                        Text(
                                            if (section.featureCount > 0) "${section.title} (${section.featureCount})"
                                            else section.title,
                                            fontSize = 15.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        val selected = sections.firstOrNull { it.key == selectedKey }
                        if (selectedKey == "dashboard") {
                            AdminOverviewV3(service, capability, sections) { status = it }
                        } else if (selected != null) {
                            AdminSectionScreen(
                                section = selected,
                                capability = capability,
                                service = service,
                                status = { status = it },
                                openHistory = { showHistory = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBugReports) {
        BlinkAdminBugInbox(onDismiss = { showBugReports = false })
    }

    if (showHistory) {
        AdminHistoryDialog(
            service = service,
            onDismiss = { showHistory = false },
            status = { status = it }
        )
    }

    if (showGlobalSearch) {
        AdminGlobalSearchDialog(
            service = service,
            onDismiss = { showGlobalSearch = false },
            status = { status = it }
        )
    }
}

// Group by task rather than squeezing dozens of admin sections into a 122dp sidebar.
internal fun adminNavigationGroup(key: String): String = when (key) {
    "dashboard" -> "Overview"
    "users", "accounts", "profiles", "sessions_devices", "universities",
    "verification", "admins", "roles_permissions" -> "People & access"
    "posts", "reels", "stories", "comments", "scheduled_content", "engagement" -> "Content"
    "reports", "moderation_queue", "support_appeals", "safety_restrictions" -> "Safety"
    "messages", "conversations", "notifications", "announcements" -> "Messaging"
    "coins_balances", "points_rewards", "transactions", "marketplace", "orders_sellers" -> "Coins & commerce"
    "connect_hub", "housing", "mentorship", "reading_mate", "study_circles",
    "games", "leaderboards" -> "Campus & connect"
    else -> "System & insights"
}

@Composable
private fun AdminOverviewV3(
    service: AdminSupabaseService,
    capability: AdminCapability,
    sections: List<AdminSection>,
    status: (String) -> Unit
) {
    var stats by remember { mutableStateOf<AdminDashboardStats?>(null) }
    var notificationHealth by remember { mutableStateOf<AdminNotificationDeliveryHealth?>(null) }

    LaunchedEffect(Unit) {
        service.fetchStats()
            .onSuccess { stats = it }
            .onFailure { status(it.message ?: "Could not load admin stats.") }
        service.fetchNotificationDeliveryHealth()
            .onSuccess { notificationHealth = it }
            .onFailure { status(it.message ?: "Could not load notification delivery health.") }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Control Center", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(
                "Manage people, content, campus activity and BLINK operations in one place.",
                color = Color.LightGray,
                fontSize = 15.sp
            )
        }
        stats?.let { s ->
            item { AdminStatCard("Users", s.users) }
            item { AdminStatCard("Verified", s.verified) }
            item { AdminStatCard("Active admins", s.activeAdmins) }
            item { AdminStatCard("Live posts", s.posts) }
            item { AdminStatCard("Blink owner posts", s.ownerPosts) }
        } ?: item { CircularProgressIndicator(Modifier.size(28.dp), color = BlinkPink) }
        item {
            notificationHealth?.let { health ->
                AdminNotificationHealthCard(health)
            } ?: Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = BlinkPink, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking notification delivery health…", fontSize = 15.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Text("How targeting works", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Users are selected by name, @username, email or user ID. Posts and reels can be found by pasting a Blink link or searching their content. Universities use the full searchable NigerianUniversities catalogue plus live profile values.",
                        fontSize = 15.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Text("History stays global", fontWeight = FontWeight.Black)
                    Text(
                        "History is in the top bar, not inside a section. Reversible actions can be revoked from the audit timeline without deleting their original record.",
                        fontSize = 15.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminNotificationHealthCard(health: AdminNotificationDeliveryHealth) {
    val totalFailed = health.socialFailed + health.messageFailed
    val totalSent = health.socialSent + health.messageSent
    val totalSending = health.socialSending + health.messageSending
    val totalRetried = health.socialRetried + health.messageRetried
    val healthy = health.failuresLast24h == 0

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notification delivery health", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        if (healthy) "Healthy • no recorded failures in the last 24 hours"
                        else health.failuresLast24h.toString() + " failed dispatches in the last 24 hours",
                        fontSize = 15.sp,
                        color = if (healthy) Color(BlinkDesignTokens.Semantic.Success) else BlinkPink
                    )
                }
                Text(
                    if (healthy) "HEALTHY" else "CHECK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = if (healthy) Color(BlinkDesignTokens.Semantic.Success) else BlinkPink
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Active push devices: " + health.activeDevices +
                    " • users with push: " + health.usersWithPush,
                fontSize = 15.sp,
                color = Color.LightGray
            )
            Text(
                "Sent: " + totalSent +
                    " • failed: " + totalFailed +
                    " • sending: " + totalSending +
                    " • retried: " + totalRetried,
                fontSize = 15.sp,
                color = Color.LightGray
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Social " + health.socialSent + " sent / " + health.socialFailed + " failed" +
                    "   •   Messages " + health.messageSent + " sent / " + health.messageFailed + " failed",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                "Aggregates only. FCM tokens, message content and provider error payloads are never shown here.",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(value.toString(), color = BlinkPink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun AdminSectionScreen(
    section: AdminSection,
    capability: AdminCapability,
    service: AdminSupabaseService,
    status: (String) -> Unit,
    openHistory: () -> Unit
) {
    var query by rememberSaveable(section.key) { mutableStateOf("") }
    var toolFilter by rememberSaveable(section.key) { mutableStateOf("All") }
    var features by remember(section.key) { mutableStateOf<List<AdminFeatureV3>>(emptyList()) }
    var loading by remember(section.key) { mutableStateOf(true) }
    var selectedFeature by remember { mutableStateOf<AdminFeatureV3?>(null) }
    var resultByFeature by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            service.fetchFeaturesV3(section.key, query)
                .onSuccess { features = it }
                .onFailure { status(it.message ?: "Could not load ${section.title}.") }
            loading = false
        }
    }

    LaunchedEffect(section.key) { load() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(section.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(section.description, color = Color.Gray, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search ${section.title} tools", fontSize = 15.sp) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = ::load,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Find tools", fontSize = 16.sp) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("All", "Insights", "Actions", "High risk", "Owner only").forEach { filter ->
                FilterChip(
                    selected = toolFilter == filter,
                    onClick = { toolFilter = filter },
                    label = { Text(filter, fontSize = 15.sp) }
                )
            }
        }
        val shownFeatures = features.filter { feature ->
            when (toolFilter) {
                "Insights" -> feature.inputKind == "insight"
                "Actions" -> feature.inputKind != "insight"
                "High risk" -> feature.riskLevel in setOf("high", "critical")
                "Owner only" -> feature.ownerOnly
                else -> true
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BlinkPink)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shownFeatures, key = { it.featureId }) { feature ->
                AdminFeatureCard(
                    feature = feature,
                    capability = capability,
                    result = resultByFeature[feature.featureId],
                    onRunInsight = {
                        scope.launch {
                            service.executeFeatureV3(feature.featureId)
                                .onSuccess {
                                    resultByFeature = resultByFeature + (feature.featureId to friendlyResult(it))
                                }
                                .onFailure { status(it.message ?: "Admin tool failed.") }
                        }
                    },
                    onOpen = { selectedFeature = feature },
                    openHistory = openHistory
                )
            }
            if (!loading && shownFeatures.isEmpty()) {
                item {
                    Text("No tools match this search.", color = Color.Gray, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }

    selectedFeature?.let { feature ->
        FeatureActionDialog(
            feature = feature,
            capability = capability,
            service = service,
            onDismiss = { selectedFeature = null },
            onCompleted = { text ->
                resultByFeature = resultByFeature + (feature.featureId to text)
                status("${feature.title}: completed.")
            }
        )
    }
}

@Composable
private fun AdminFeatureCard(
    feature: AdminFeatureV3,
    capability: AdminCapability,
    result: String?,
    onRunInsight: () -> Unit,
    onOpen: () -> Unit,
    openHistory: () -> Unit
) {
    val blocked = feature.ownerOnly && !capability.isOwner
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(feature.title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(feature.description, fontSize = 15.sp, color = Color.Gray)
                }
                Text("#${feature.featureId}", color = BlinkPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append(feature.riskLevel.uppercase())
                    if (feature.reversible) append(" • REVERSIBLE")
                    if (feature.ownerOnly) append(" • OWNER ONLY")
                },
                fontSize = 14.sp,
                color = if (feature.riskLevel == "critical" || feature.riskLevel == "high") MaterialTheme.colorScheme.error else Color.Gray
            )
            if (result != null) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(result, Modifier.fillMaxWidth().padding(8.dp), fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    enabled = !blocked,
                    onClick = if (feature.inputKind == "insight") onRunInsight else onOpen,
                    modifier = Modifier.heightIn(min = 48.dp).weight(1f)
                ) {
                    Text(if (feature.inputKind == "insight") "Run insight" else "Open tool", fontSize = 15.sp)
                }
                if (feature.reversible) {
                    TextButton(onClick = openHistory, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("History", fontSize = 15.sp)
                    }
                }
            }
            if (blocked) {
                Text("This tool requires overall-owner access.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FeatureActionDialog(
    feature: AdminFeatureV3,
    capability: AdminCapability,
    service: AdminSupabaseService,
    onDismiss: () -> Unit,
    onCompleted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val kind = feature.inputKind.lowercase()
    val targetType = feature.targetType.lowercase()

    var userQuery by remember { mutableStateOf("") }
    var userResults by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var showUserPicker by remember { mutableStateOf(false) }
    var userPickerError by remember { mutableStateOf<String?>(null) }
    var selectedUsers by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var postQuery by remember { mutableStateOf("") }
    var postResults by remember { mutableStateOf<List<AdminPostSummary>>(emptyList()) }
    var selectedPost by remember { mutableStateOf<AdminPostSummary?>(null) }
    var genericRef by remember { mutableStateOf("") }
    var universityQuery by remember { mutableStateOf("") }
    var serverUniversities by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedUniversities by remember { mutableStateOf<List<String>>(emptyList()) }
    var textInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("24") }
    var reasonInput by remember { mutableStateOf("") }
    var badge by remember { mutableStateOf("BLUE") }
    var enabled by remember { mutableStateOf(true) }
    var permissionsInput by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf("") }
    var fromValue by remember { mutableStateOf("") }
    var toValue by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var linkRef by remember { mutableStateOf("") }
    var confirmText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var targetBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isBulkUsers = kind.contains("bulk_users")
    val needsPrimaryUser = targetType == "user" ||
        isBulkUsers ||
        kind == "user" ||
        kind.startsWith("user_")
    val needsAssigneeUser = kind == "report_assignee"
    val needsProfileLinkUser = feature.featureId == 177
    val needsUserPicker = needsPrimaryUser || needsAssigneeUser || needsProfileLinkUser
    val needsPost = targetType == "post" || targetType == "reel"
    val needsGenericTarget = targetType in setOf("comment", "report", "campaign", "verification_request")
    val needsUniversity = kind.contains("university") || kind.contains("universities")
    val needsAmount = kind.contains("amount") || feature.featureId in setOf(96, 98)
    val needsDuration = kind.contains("duration") || feature.featureId == 98
    val needsReason = kind.contains("reason") || feature.riskLevel in setOf("high", "critical")
    val needsBadge = kind.contains("badge")
    val needsPermissions = kind.contains("permissions") || kind == "role_template"
    val needsToggle = kind == "toggle" || kind == "feature_toggle"
    val needsDateRange = kind == "date_range"
    val needsScheduledAt = kind == "scheduled_message"
    val needsText = kind in setOf(
        "query", "message", "user_message", "user_message_reason", "bulk_users_message",
        "university_message", "universities_message", "scheduled_message", "campaign_message",
        "user_note", "report_note", "role_template", "bonus_config", "event_config",
        "version", "emergency", "feature_toggle"
    )

    val mergedUniversities = remember(universityQuery, serverUniversities) {
        val local = NigerianUniversities.all
        (local + serverUniversities)
            .distinct()
            .filter { universityQuery.isBlank() || it.contains(universityQuery, ignoreCase = true) }
            .take(20)
    }

    LaunchedEffect(needsUniversity) {
        if (needsUniversity) {
            service.searchUniversitiesV3("", 300).onSuccess { serverUniversities = it }
        }
    }

    // Load suggested accounts on open; debounce typing so admin search is responsive.
    LaunchedEffect(showUserPicker, userQuery) {
        if (!showUserPicker) return@LaunchedEffect
        delay(if (userQuery.isBlank()) 0L else 280L)
        targetBusy = true
        userPickerError = null
        service.searchUsersV2(userQuery.trim(), 30)
            .onSuccess { userResults = it }
            .onFailure {
                userResults = emptyList()
                userPickerError = it.message ?: "Could not find users."
            }
        targetBusy = false
    }

    fun searchPosts() {
        scope.launch {
            targetBusy = true
            service.searchPostsV2(postQuery, 20)
                .onSuccess {
                    postResults = if (targetType == "reel") it.filter { p -> p.isReel } else it
                }
                .onFailure { error = it.message }
            targetBusy = false
        }
    }

    fun runFeature() {
        if (feature.ownerOnly && !capability.isOwner) {
            error = "Overall-owner access is required."
            return
        }
        if (needsUserPicker && selectedUsers.isEmpty() && kind != "user_optional") {
            error = when {
                needsAssigneeUser -> "Select the admin/user to assign."
                needsProfileLinkUser -> "Select the profile to link."
                else -> "Select at least one user."
            }
            return
        }
        if (needsPost && selectedPost == null) {
            error = "Select a post or reel."
            return
        }
        if (needsGenericTarget && genericRef.isBlank()) {
            error = "Enter or paste the ${targetType.replace('_', ' ')} reference."
            return
        }
        if (needsUniversity && selectedUniversities.isEmpty()) {
            error = "Select a university."
            return
        }
        if (needsReason && reasonInput.isBlank()) {
            error = "Add a reason for this admin action."
            return
        }
        if (feature.confirmationKind == "typed" && confirmText.trim() != "CONFIRM") {
            error = "Type CONFIRM to run this high-risk action."
            return
        }

        val amount = if (needsAmount) amountInput.toLongOrNull() else null
        if (needsAmount && amount == null) {
            error = "Enter a valid numeric amount."
            return
        }
        val duration = if (needsDuration) durationInput.toIntOrNull() else null
        if (needsDuration && duration == null) {
            error = "Enter a valid duration in hours."
            return
        }

        val options = JSONObject()
        if (reasonInput.isNotBlank()) options.put("reason", reasonInput.trim())
        if (needsBadge) options.put("badge", badge)
        if (needsUniversity && selectedUniversities.isNotEmpty()) {
            options.put("university", selectedUniversities.first())
            options.put("universities", JSONArray(selectedUniversities))
        }
        if (isBulkUsers) options.put("user_ids", JSONArray(selectedUsers.map { it.id }))
        if (needsPermissions) {
            val values = permissionsInput.split(',').map { it.trim() }.filter { it.isNotBlank() }
            options.put("permissions", JSONArray(values))
        }
        if (needsToggle) options.put("enabled", enabled)
        if (needsDateRange) {
            options.put("from", fromValue.trim())
            options.put("to", toValue.trim())
        }
        if (needsScheduledAt) options.put("scheduled_at", scheduledAt.trim())
        when (feature.featureId) {
            153 -> options.put("admin_id", selectedUsers.firstOrNull()?.id ?: JSONObject.NULL)
            173 -> options.put("title", textInput.trim())
            174 -> options.put("image_url", textInput.trim())
            175 -> {
                options.put("action_label", actionLabel.trim())
                options.put("action_url", actionUrl.trim())
            }
            176 -> {
                options.put("link_type", "post")
                options.put("link_id", linkRef.trim())
            }
            177 -> {
                options.put("link_type", "profile")
                options.put("link_id", selectedUsers.firstOrNull()?.id ?: JSONObject.NULL)
            }
            178 -> {
                options.put("link_type", "marketplace")
                options.put("link_id", linkRef.trim())
            }
        }

        val entityRef = when {
            needsPost -> selectedPost?.id
            needsPrimaryUser -> selectedUsers.firstOrNull()?.id
            needsGenericTarget -> genericRef.trim()
            else -> null
        }

        busy = true
        error = null
        scope.launch {
            service.executeFeatureV3(
                featureId = feature.featureId,
                entityRef = entityRef,
                text = textInput.takeIf { it.isNotBlank() },
                amount = amount,
                durationHours = duration,
                options = options
            ).onSuccess {
                busy = false
                onCompleted(friendlyResult(it))
                onDismiss()
            }.onFailure {
                busy = false
                error = it.message ?: "Admin action failed."
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(10.dp).heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(feature.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(feature.description, color = Color.LightGray, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))

                if (needsUserPicker) {
                    Text(
                        when {
                            needsAssigneeUser -> "Assigned account"
                            needsProfileLinkUser -> "Linked profile"
                            isBulkUsers -> "Recipients"
                            else -> "User"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            userQuery = ""
                            showUserPicker = true
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Text(
                            when {
                                selectedUsers.isEmpty() -> "Choose user — search by name or @username"
                                isBulkUsers -> "${selectedUsers.size} users selected — change"
                                else -> "@${selectedUsers.first().username} — change"
                            },
                            fontSize = 16.sp
                        )
                    }
                    selectedUsers.forEach { user ->
                        SelectedUserRow(user) {
                            selectedUsers = selectedUsers.filterNot { it.id == user.id }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (needsPost) {
                    Text("Select ${if (targetType == "reel") "reel" else "post"}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        postQuery,
                        { postQuery = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Paste Blink link, post ID, @username or search text") },
                        singleLine = true
                    )
                    Button(onClick = ::searchPosts, enabled = !targetBusy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                        Text("Find content")
                    }
                    selectedPost?.let { AdminPostSearchRow(it, true) { selectedPost = null } }
                    postResults.take(8).forEach { post ->
                        AdminPostSearchRow(post, selectedPost?.id == post.id) { selectedPost = post }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (needsGenericTarget) {
                    OutlinedTextField(
                        genericRef,
                        { genericRef = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Search or paste ${targetType.replace('_', ' ')} reference") },
                        singleLine = true
                    )
                    Text("Blink resolves the matching record securely on the backend.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (needsUniversity) {
                    Text("University", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        universityQuery,
                        { universityQuery = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Search university") },
                        singleLine = true
                    )
                    mergedUniversities.forEach { name ->
                        val chosen = selectedUniversities.contains(name)
                        Surface(
                            Modifier.fillMaxWidth().padding(top = 3.dp).clickable {
                                selectedUniversities = if (kind.contains("universities")) {
                                    if (chosen) selectedUniversities - name else selectedUniversities + name
                                } else listOf(name)
                            },
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(name, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (needsText) {
                    val label = when (kind) {
                        "query" -> "Search query"
                        "user_note", "report_note" -> "Internal note"
                        "role_template" -> "Role template name"
                        "version" -> "Required app version"
                        "feature_toggle" -> "Feature key"
                        "bonus_config" -> "Bonus name / configuration"
                        "event_config" -> "Event name / configuration"
                        "emergency" -> "Emergency command"
                        else -> "Message / value"
                    }
                    OutlinedTextField(
                        textInput,
                        { textInput = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(label) },
                        minLines = if (kind.contains("message") || kind.contains("note")) 3 else 1
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (needsAmount) {
                    OutlinedTextField(
                        amountInput,
                        { amountInput = it.filter { c -> c.isDigit() || c == '-' }.take(12) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Amount / numeric value") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (needsDuration) {
                    OutlinedTextField(
                        durationInput,
                        { durationInput = it.filter(Char::isDigit).take(5) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Duration in hours") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (needsBadge) {
                    Text("Verification badge", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("BLUE", "GOLD", "NONE").forEach { option ->
                            FilterChip(
                                selected = badge == option,
                                onClick = { badge = option },
                                label = { Text(option, fontSize = 14.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (needsPermissions) {
                    OutlinedTextField(
                        permissionsInput,
                        { permissionsInput = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Permissions (comma separated)") }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (needsToggle) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (needsDateRange) {
                    OutlinedTextField(fromValue, { fromValue = it }, Modifier.fillMaxWidth(), label = { Text("From (ISO date/time)") })
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(toValue, { toValue = it }, Modifier.fillMaxWidth(), label = { Text("To (ISO date/time)") })
                    Spacer(Modifier.height(8.dp))
                }

                if (needsScheduledAt) {
                    OutlinedTextField(
                        scheduledAt,
                        { scheduledAt = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Schedule time (ISO date/time)") }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (feature.featureId == 175) {
                    OutlinedTextField(actionLabel, { actionLabel = it }, Modifier.fillMaxWidth(), label = { Text("Action button label") })
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(actionUrl, { actionUrl = it }, Modifier.fillMaxWidth(), label = { Text("Action URL") })
                    Spacer(Modifier.height(8.dp))
                }

                if (feature.featureId == 176 || feature.featureId == 178) {
                    OutlinedTextField(
                        linkRef,
                        { linkRef = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Linked Blink reference / link") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (needsReason) {
                    OutlinedTextField(
                        reasonInput,
                        { reasonInput = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Reason / audit note") },
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (feature.confirmationKind == "typed") {
                    Text("High-risk action", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                    Text("Type CONFIRM to continue.", color = Color.LightGray, fontSize = 15.sp)
                    OutlinedTextField(confirmText, { confirmText = it }, Modifier.fillMaxWidth(), label = { Text("CONFIRM") })
                    Spacer(Modifier.height(8.dp))
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::runFeature, enabled = !busy && !targetBusy,
                        modifier = Modifier.heightIn(min = 48.dp).weight(1f)) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Run tool")
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp).weight(1f)) { Text("Close") }
                }
            }
        }
    }

    if (showUserPicker) {
        Dialog(
            onDismissRequest = { if (!busy) showUserPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                Modifier.fillMaxWidth().padding(8.dp).heightIn(max = 670.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Select Blink user", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        if (isBulkUsers) "Search and select multiple accounts."
                        else "Select one account. The user ID is filled automatically.",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userQuery,
                        onValueChange = { userQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name, @username or email") },
                        singleLine = true
                    )
                    if (targetBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    userPickerError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                    }
                    Text(
                        if (userQuery.isBlank()) "Suggested users" else "Search results",
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(userResults, key = { it.id }) { user ->
                            UserSearchRow(
                                user = user,
                                selected = selectedUsers.any { it.id == user.id },
                                onClick = {
                                    selectedUsers = if (isBulkUsers && !needsAssigneeUser && !needsProfileLinkUser) {
                                        if (selectedUsers.any { it.id == user.id }) {
                                            selectedUsers.filterNot { it.id == user.id }
                                        } else selectedUsers + user
                                    } else listOf(user)
                                    if (!isBulkUsers || needsAssigneeUser || needsProfileLinkUser) {
                                        showUserPicker = false
                                    }
                                }
                            )
                        }
                        if (!targetBusy && userPickerError == null && userResults.isEmpty()) {
                            item {
                                Text(
                                    if (userQuery.isBlank()) "No suggested users. Search by username."
                                    else "No users match this search.",
                                    Modifier.padding(12.dp),
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBulkUsers) {
                            Text("${selectedUsers.size} selected", fontSize = 15.sp)
                            Spacer(Modifier.width(12.dp))
                        }
                        TextButton(onClick = { showUserPicker = false }) {
                            Text(if (isBulkUsers) "Done" else "Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearchRow(
    user: AdminUserSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.fullName.ifBlank { user.username }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("@${user.username} • ${user.university.ifBlank { "No university" }}", fontSize = 14.sp, color = Color.Gray)
                if (user.email.isNotBlank()) Text(user.email, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (selected) "Selected" else "Select", color = BlinkPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectedUserRow(user: AdminUserSummary, remove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Selected: @${user.username}", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = remove) { Text("Remove", fontSize = 14.sp) }
    }
}

@Composable
private fun AdminPostSearchRow(
    post: AdminPostSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("@${post.username} • ${if (post.isReel) "Reel" else "Post"}", fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text((post.caption.ifBlank { post.text }).ifBlank { "Media post" }, maxLines = 2, fontSize = 15.sp)
            Text(
                "${post.viewCount} views • ${post.likeCount} likes • ${post.commentCount} comments • ${post.shareCount} shares",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(if (selected) "Selected" else "Tap to select", color = BlinkPink, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AdminHistoryDialog(
    service: AdminSupabaseService,
    onDismiss: () -> Unit,
    status: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AdminHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var revertingId by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            service.fetchHistoryV2(150, 0)
                .onSuccess { items = it }
                .onFailure { status(it.message ?: "Could not load admin history.") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize().padding(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin History", fontWeight = FontWeight.Black, fontSize = 23.sp)
                        Text("Every action stays auditable. Revoke creates a linked reversal instead of erasing history.", fontSize = 14.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BlinkPink)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(11.dp)) {
                                Text(humanizeAction(item.action), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text(
                                    buildString {
                                        append("@${item.actorUsername}")
                                        item.targetUsername?.takeIf { it.isNotBlank() }?.let { append(" → @$it") }
                                        if (item.targetPostId != null) append(" → post")
                                    },
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                item.createdAt?.let { Text(it, fontSize = 14.sp, color = Color.Gray) }
                                val detail = summarizeAuditDetails(item.details)
                                if (detail.isNotBlank()) {
                                    Text(detail, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                                }
                                Spacer(Modifier.height(5.dp))
                                when {
                                    item.reversed -> Text("REVOKED", color = BlinkPink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    item.canRevert -> Button(
                                        onClick = {
                                            revertingId = item.id
                                            scope.launch {
                                                service.revertActionV2(item.id, "Revoked from Blink Admin history")
                                                    .onSuccess {
                                                        status("Admin action revoked successfully.")
                                                        load()
                                                    }
                                                    .onFailure { status(it.message ?: "Could not revoke action.") }
                                                revertingId = null
                                            }
                                        },
                                        enabled = revertingId == null
                                    ) {
                                        if (revertingId == item.id) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                        else Text("Revoke", fontSize = 14.sp)
                                    }
                                    else -> Text("Not reversible", color = Color.Gray, fontSize = 14.sp)
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
private fun AdminGlobalSearchDialog(
    service: AdminSupabaseService,
    onDismiss: () -> Unit,
    status: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<AdminUserSummary>>(emptyList()) }
    var posts by remember { mutableStateOf<List<AdminPostSummary>>(emptyList()) }
    var universities by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            loading = true
            service.searchUsersV2(query, 8).onSuccess { users = it }.onFailure { status(it.message ?: "User search failed.") }
            service.searchPostsV2(query, 8).onSuccess { posts = it }.onFailure { status(it.message ?: "Post search failed.") }
            service.searchUniversitiesV3(query, 20).onSuccess { universities = it }.onFailure { status(it.message ?: "University search failed.") }
            loading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize().padding(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Global Search", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("Search users, paste post links, or search universities without hunting for database IDs.", color = Color.Gray, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name, username, email, post or university") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = ::search,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Search") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 5.dp), color = BlinkPink)
                LazyColumn(
                    Modifier.fillMaxSize().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (users.isNotEmpty()) {
                        item { Text("Users", color = BlinkPink, fontWeight = FontWeight.Black) }
                        items(users, key = { "user-${it.id}" }) { UserSearchRow(it, false) {} }
                    }
                    if (posts.isNotEmpty()) {
                        item { Text("Posts & Reels", color = BlinkPink, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp)) }
                        items(posts, key = { "post-${it.id}" }) { AdminPostSearchRow(it, false) {} }
                    }
                    if (universities.isNotEmpty()) {
                        item { Text("Universities", color = BlinkPink, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp)) }
                        items(universities, key = { "uni-$it" }) { name ->
                            Card(Modifier.fillMaxWidth()) { Text(name, Modifier.padding(10.dp), fontSize = 15.sp) }
                        }
                    }
                }
            }
        }
    }
}

private fun humanizeAction(action: String): String =
    action.replace('_', ' ').trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun summarizeAuditDetails(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val json = JSONObject(raw)
        buildList {
            json.opt("amount")?.takeUnless { it == JSONObject.NULL }?.let { add("Amount: $it") }
            json.optString("badge").takeIf { it.isNotBlank() }?.let { add("Badge: $it") }
            json.optString("reason").takeIf { it.isNotBlank() }?.let { add("Reason: $it") }
            json.opt("duration_hours")?.takeUnless { it == JSONObject.NULL }?.let { add("Duration: $it hours") }
            json.optString("text").takeIf { it.isNotBlank() && it.length < 120 }?.let { add(it) }
        }.joinToString(" • ")
    }.getOrDefault("")
}

private fun friendlyResult(json: JSONObject): String {
    val result = json.opt("result")
    if (result is JSONObject && result.has("value")) {
        return "Result: ${result.opt("value")}"
    }
    if (result is JSONArray) {
        if (result.length() == 0) return "No matching records."
        val lines = buildList {
            val limit = minOf(result.length(), 5)
            for (i in 0 until limit) {
                val item = result.optJSONObject(i)
                if (item != null) {
                    val username = item.optString("username")
                    val fullName = item.optString("full_name")
                    val text = item.optString("text").ifBlank { item.optString("caption") }
                    add(
                        when {
                            username.isNotBlank() -> listOf(fullName, "@$username").filter { it.isNotBlank() }.joinToString(" • ")
                            text.isNotBlank() -> text.take(100)
                            else -> "Record ${i + 1}"
                        }
                    )
                }
            }
        }
        return "${result.length()} record(s)" + if (lines.isNotEmpty()) "\n" + lines.joinToString("\n") else ""
    }
    if (result is JSONObject) {
        val lines = mutableListOf<String>()
        val keys = result.keys()
        while (keys.hasNext() && lines.size < 10) {
            val key = keys.next()
            val value = result.opt(key)
            if (value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) {
                lines += "${key.replace('_', ' ')}: $value"
            }
        }
        if (lines.isNotEmpty()) return lines.joinToString("\n")
    }
    return if (json.optBoolean("ok", false)) "Completed successfully." else "Request completed."
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.NigerianUniversities
import com.example.data.supabase.*
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
        error != null -> AdminAccessError(error!!, onExit)
        capability?.isAdmin != true -> AdminAccessError("This account does not have active admin access.", onExit)
        else -> AdminDashboardV3(capability!!, service) { leaving = true }
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
                    Column {
                        Text("Blink Admin", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            if (capability.isOwner) "Overall owner • permanent" else "${capability.role} • backend protected",
                            fontSize = 9.sp,
                            color = Color.LightGray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showGlobalSearch = true }) {
                        Text("Search", fontSize = 10.sp)
                    }
                    TextButton(onClick = { showHistory = true }) {
                        Text("History", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onExit) {
                        Text("Personal", fontSize = 10.sp)
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
                        Text(it, Modifier.weight(1f), fontSize = 11.sp)
                        TextButton(onClick = { status = null }) { Text("Dismiss", fontSize = 10.sp) }
                    }
                }
            }

            if (loading && sections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlinkPink)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    AdminSidebar(
                        sections = sections.filter { it.showInSidebar },
                        selectedKey = selectedKey,
                        onSelect = { selectedKey = it }
                    )
                    Divider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = Color(0xFF242424)
                    )
                    Box(Modifier.weight(1f).fillMaxHeight()) {
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

@Composable
private fun AdminSidebar(
    sections: List<AdminSection>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    LazyColumn(
        Modifier.width(122.dp).fillMaxHeight().background(Color(0xFF090909)),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(sections, key = { it.key }) { section ->
            val selected = section.key == selectedKey
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
                    .clickable { onSelect(section.key) },
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        section.title,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.LightGray,
                        maxLines = 2
                    )
                    if (section.featureCount > 0) {
                        Text(
                            "${section.featureCount} tools",
                            fontSize = 8.sp,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOverviewV3(
    service: AdminSupabaseService,
    capability: AdminCapability,
    sections: List<AdminSection>,
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
            Text("Control Center", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Text(
                "700 backend-routed admin tools • ${sections.count { it.showInSidebar }} vertical sections • searchable resources • reversible audit history",
                color = Color.LightGray,
                fontSize = 11.sp
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Text("How targeting works", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Users are selected by name, @username, email or user ID. Posts and reels can be found by pasting a Blink link or searching their content. Universities use the full searchable NigerianUniversities catalogue plus live profile values.",
                        fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(value.toString(), color = BlinkPink, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            Text(section.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(section.description, color = Color.Gray, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search ${section.title} tools", fontSize = 10.sp) },
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = ::load, enabled = !loading) { Text("Find", fontSize = 10.sp) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = BlinkPink)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(features, key = { it.featureId }) { feature ->
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
            if (!loading && features.isEmpty()) {
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
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(feature.title, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(feature.description, fontSize = 10.sp, color = Color.Gray)
                }
                Text("#${feature.featureId}", color = BlinkPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append(feature.riskLevel.uppercase())
                    if (feature.reversible) append(" • REVERSIBLE")
                    if (feature.ownerOnly) append(" • OWNER ONLY")
                },
                fontSize = 8.sp,
                color = if (feature.riskLevel == "critical" || feature.riskLevel == "high") MaterialTheme.colorScheme.error else Color.Gray
            )
            if (result != null) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(result, Modifier.fillMaxWidth().padding(8.dp), fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    enabled = !blocked,
                    onClick = if (feature.inputKind == "insight") onRunInsight else onOpen
                ) {
                    Text(if (feature.inputKind == "insight") "Run insight" else "Open tool", fontSize = 10.sp)
                }
                if (feature.reversible) {
                    TextButton(onClick = openHistory) { Text("History", fontSize = 10.sp) }
                }
            }
            if (blocked) {
                Text("This tool requires overall-owner access.", color = MaterialTheme.colorScheme.error, fontSize = 9.sp)
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
    val needsUser = targetType == "user" || isBulkUsers
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
        "version", "emergency", "feature_toggle", "report_assignee"
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

    fun searchUsers() {
        scope.launch {
            targetBusy = true
            service.searchUsersV2(userQuery, 20)
                .onSuccess { userResults = it }
                .onFailure { error = it.message }
            targetBusy = false
        }
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
        if (needsUser && selectedUsers.isEmpty() && kind != "user_optional") {
            error = "Select at least one user."
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
            153 -> options.put("admin_id", textInput.trim())
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
                options.put("link_id", linkRef.trim())
            }
            178 -> {
                options.put("link_type", "marketplace")
                options.put("link_id", linkRef.trim())
            }
        }

        val entityRef = when {
            needsPost -> selectedPost?.id
            needsUser -> selectedUsers.firstOrNull()?.id
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
            Modifier.fillMaxWidth().padding(18.dp).heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.large,
            color = Color(0xFF171521)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState())) {
                Text(feature.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(feature.description, color = Color.LightGray, fontSize = 10.sp)
                Text(feature.routeKey, color = BlinkPink, fontSize = 8.sp)
                Spacer(Modifier.height(12.dp))

                if (needsUser) {
                    Text("Select user", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        userQuery,
                        { userQuery = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Search name, @username, email or user ID") },
                        singleLine = true
                    )
                    Button(onClick = ::searchUsers, enabled = !targetBusy, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Search users")
                    }
                    selectedUsers.forEach { user ->
                        SelectedUserRow(user) {
                            selectedUsers = selectedUsers.filterNot { it.id == user.id }
                        }
                    }
                    userResults.take(8).forEach { user ->
                        UserSearchRow(
                            user = user,
                            selected = selectedUsers.any { it.id == user.id },
                            onClick = {
                                selectedUsers = if (isBulkUsers) {
                                    if (selectedUsers.any { it.id == user.id }) selectedUsers.filterNot { it.id == user.id }
                                    else selectedUsers + user
                                } else listOf(user)
                            }
                        )
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
                    Button(onClick = ::searchPosts, enabled = !targetBusy, modifier = Modifier.padding(top = 6.dp)) {
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
                    Text("Blink resolves the matching record securely on the backend.", color = Color.Gray, fontSize = 9.sp)
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
                            Text(name, Modifier.padding(8.dp), fontSize = 10.sp)
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
                        "report_assignee" -> "Admin user ID"
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
                                label = { Text(option, fontSize = 9.sp) }
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

                if (feature.featureId in 176..178) {
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
                    Text("Type CONFIRM to continue.", color = Color.LightGray, fontSize = 10.sp)
                    OutlinedTextField(confirmText, { confirmText = it }, Modifier.fillMaxWidth(), label = { Text("CONFIRM") })
                    Spacer(Modifier.height(8.dp))
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::runFeature, enabled = !busy && !targetBusy) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Run tool")
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
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
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.fullName.ifBlank { user.username }, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("@${user.username} • ${user.university.ifBlank { "No university" }}", fontSize = 9.sp, color = Color.Gray)
                if (user.email.isNotBlank()) Text(user.email, fontSize = 8.sp, color = Color.Gray)
            }
            Text(if (selected) "Selected" else "Select", color = BlinkPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectedUserRow(user: AdminUserSummary, remove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Selected: @${user.username}", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = remove) { Text("Remove", fontSize = 9.sp) }
    }
}

@Composable
private fun AdminPostSearchRow(
    post: AdminPostSummary,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("@${post.username} • ${if (post.isReel) "Reel" else "Post"}", fontWeight = FontWeight.Black, fontSize = 10.sp)
            Text((post.caption.ifBlank { post.text }).ifBlank { "Media post" }, maxLines = 2, fontSize = 10.sp)
            Text(
                "${post.viewCount} views • ${post.likeCount} likes • ${post.commentCount} comments • ${post.shareCount} shares",
                fontSize = 8.sp,
                color = Color.Gray
            )
            Text(if (selected) "Selected" else "Tap to select", color = BlinkPink, fontSize = 8.sp)
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
            color = Color(0xFF101014),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Admin History", fontWeight = FontWeight.Black, fontSize = 21.sp)
                        Text("Every action stays auditable. Revoke creates a linked reversal instead of erasing history.", fontSize = 9.sp, color = Color.Gray)
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
                                Text(humanizeAction(item.action), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Text(
                                    buildString {
                                        append("@${item.actorUsername}")
                                        item.targetUsername?.takeIf { it.isNotBlank() }?.let { append(" → @$it") }
                                        if (item.targetPostId != null) append(" → post")
                                    },
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                                item.createdAt?.let { Text(it, fontSize = 8.sp, color = Color.Gray) }
                                val detail = summarizeAuditDetails(item.details)
                                if (detail.isNotBlank()) {
                                    Text(detail, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                                }
                                Spacer(Modifier.height(5.dp))
                                when {
                                    item.reversed -> Text("REVOKED", color = BlinkPink, fontWeight = FontWeight.Black, fontSize = 9.sp)
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
                                        else Text("Revoke", fontSize = 9.sp)
                                    }
                                    else -> Text("Not reversible", color = Color.Gray, fontSize = 8.sp)
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
            color = Color(0xFF101014),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Global Search", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("Search users, paste post links, or search universities without hunting for database IDs.", color = Color.Gray, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.weight(1f),
                        label = { Text("Name, @username, email, user ID, post link or university") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = ::search, enabled = !loading) { Text("Search") }
                }
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
                            Card(Modifier.fillMaxWidth()) { Text(name, Modifier.padding(10.dp), fontSize = 10.sp) }
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

package com.example

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.NigerianUniversities
import com.example.data.supabase.AdminProSupabaseService
import com.example.data.supabase.ProAdminFeature
import com.example.data.supabase.ProAdminPost
import com.example.data.supabase.ProAdminUser
import com.example.ui.theme.BlinkPink
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AdminFeatureDialogV2(
    feature: ProAdminFeature,
    service: AdminProSupabaseService,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val kind = feature.inputKind
    var entityRef by remember(feature.featureId) { mutableStateOf("") }
    var mainText by remember(feature.featureId) { mutableStateOf("") }
    var amount by remember(feature.featureId) { mutableStateOf("") }
    var durationDays by remember(feature.featureId) { mutableStateOf(7) }
    var reason by remember(feature.featureId) { mutableStateOf("") }
    var enabled by remember(feature.featureId) { mutableStateOf(true) }
    var badge by remember(feature.featureId) { mutableStateOf("BLUE") }
    var selectedUser by remember(feature.featureId) { mutableStateOf<ProAdminUser?>(null) }
    var selectedUsers by remember(feature.featureId) { mutableStateOf<List<ProAdminUser>>(emptyList()) }
    var selectedPost by remember(feature.featureId) { mutableStateOf<ProAdminPost?>(null) }
    var university by remember(feature.featureId) { mutableStateOf("") }
    var universities by remember(feature.featureId) { mutableStateOf<List<String>>(emptyList()) }
    var permissions by remember(feature.featureId) { mutableStateOf(setOf("users", "content")) }
    var featureToggleKey by remember(feature.featureId) { mutableStateOf("posting_enabled") }
    var extraA by remember(feature.featureId) { mutableStateOf("") }
    var extraB by remember(feature.featureId) { mutableStateOf("") }
    var assignee by remember(feature.featureId) { mutableStateOf<ProAdminUser?>(null) }
    var result by remember(feature.featureId) { mutableStateOf("") }
    var busy by remember(feature.featureId) { mutableStateOf(false) }

    fun options(): JSONObject {
        val out = JSONObject()
        if (reason.isNotBlank()) out.put("reason", reason.trim())
        if (kind == "toggle" || kind == "feature_toggle") out.put("enabled", enabled)
        if (kind.contains("badge")) out.put("badge", badge)
        if (kind.contains("permissions") || kind == "role_template") {
            out.put("permissions", JSONArray(permissions.toList()))
        }
        if (kind.contains("universities")) {
            out.put("universities", JSONArray(universities))
        }
        if (kind.contains("bulk_users")) {
            out.put("user_ids", JSONArray(selectedUsers.map { it.id }))
        }
        if (feature.featureId == 163 && university.isNotBlank()) {
            out.put("university", university)
        }
        if (kind == "date_range") {
            if (extraA.isNotBlank()) out.put("from", extraA.trim())
            if (extraB.isNotBlank()) out.put("to", extraB.trim())
        }
        if (kind == "report_assignee" && assignee != null) {
            out.put("admin_id", assignee!!.id)
        }
        if (kind == "campaign_action") {
            if (extraA.isNotBlank()) out.put("label", extraA.trim())
            if (extraB.isNotBlank()) out.put("url", extraB.trim())
        }
        if (kind == "campaign_link" && extraA.isNotBlank()) {
            out.put("link_ref", extraA.trim())
        }
        if (kind == "bonus_config" || kind == "event_config") {
            if (extraA.isNotBlank()) out.put("name", extraA.trim())
            if (extraB.isNotBlank()) out.put("value", extraB.trim())
        }
        return out
    }

    fun entityReference(): String? = when {
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
                Modifier.fillMaxSize().padding(13.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${feature.featureId}", color = BlinkPink, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(feature.title, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(feature.routeKey, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                    }
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") }
                }
                if (feature.description.isNotBlank()) {
                    Text(feature.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                }

                if (kind.contains("bulk_users")) {
                    AdminMultiUserPicker(service, selectedUsers) { selectedUsers = it }
                } else if (kind.contains("user") || feature.targetType == "user") {
                    AdminUserPicker(service, selectedUser) { selectedUser = it }
                }

                if (feature.targetType == "post" || feature.targetType == "reel" || kind.startsWith("post")) {
                    AdminPostPicker(service, selectedPost, feature.targetType == "reel") { selectedPost = it }
                }

                if (feature.targetType in setOf("comment", "report", "campaign", "verification_request")) {
                    OutlinedTextField(
                        value = entityRef,
                        onValueChange = { entityRef = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find ${feature.targetType.replace('_', ' ')} or paste link/reference") },
                        singleLine = true
                    )
                }

                if (kind.contains("universities")) {
                    AdminMultiUniversityPicker(service, universities) { universities = it }
                } else if (kind.contains("university")) {
                    AdminUniversityPicker(service, university) { university = it }
                }

                if (kind == "query") {
                    OutlinedTextField(mainText, { mainText = it }, Modifier.fillMaxWidth(), label = { Text("Search query") }, singleLine = true)
                }

                if (kind.contains("message") || kind.contains("note") || kind == "campaign_text" || kind == "version") {
                    OutlinedTextField(
                        mainText,
                        { mainText = it.take(2000) },
                        Modifier.fillMaxWidth(),
                        label = { Text(if (kind == "version") "Required app version" else if (kind.contains("note")) "Internal note" else "Message / text") },
                        minLines = if (kind == "version") 1 else 3
                    )
                }

                if (kind.contains("session")) {
                    OutlinedTextField(mainText, { mainText = it }, Modifier.fillMaxWidth(), label = { Text("Session reference") }, singleLine = true)
                }

                if (kind.contains("amount")) {
                    OutlinedTextField(
                        amount,
                        { value -> amount = value.filter { it.isDigit() || it == '-' }.take(12) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Amount") },
                        singleLine = true
                    )
                }

                if (kind.contains("duration") || kind == "scheduled_message") {
                    Text("Duration", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1, 7, 30, 90, 365).forEach { days ->
                            FilterChip(
                                selected = durationDays == days,
                                onClick = { durationDays = days },
                                label = { Text("$days day${if (days == 1) "" else "s"}", fontSize = 9.sp) }
                            )
                        }
                    }
                }

                if (kind.contains("reason")) {
                    OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Reason / internal note") }, minLines = 2)
                }

                if (kind.contains("badge")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("BLUE", "GOLD").forEach { value ->
                            FilterChip(selected = badge == value, onClick = { badge = value }, label = { Text(value) })
                        }
                    }
                }

                if (kind.contains("permissions") || kind == "role_template") {
                    Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    listOf("users", "content", "messages", "coins", "verification", "analytics", "admins").forEach { permission ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = permission in permissions,
                                onCheckedChange = { checked -> permissions = if (checked) permissions + permission else permissions - permission }
                            )
                            Text(permission.replaceFirstChar { it.uppercase() }, fontSize = 10.sp)
                        }
                    }
                }

                if (kind == "role_template") {
                    OutlinedTextField(mainText, { mainText = it }, Modifier.fillMaxWidth(), label = { Text("Role name") }, singleLine = true)
                }

                if (kind == "toggle") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }

                if (kind == "feature_toggle") {
                    Text("Feature switch", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            "registrations_enabled", "posting_enabled", "reels_enabled", "comments_enabled",
                            "messaging_enabled", "marketplace_enabled", "verification_enabled", "coin_rewards_enabled", "ads_enabled"
                        ).forEach { key ->
                            FilterChip(
                                selected = featureToggleKey == key,
                                onClick = { featureToggleKey = key },
                                label = { Text(key.removeSuffix("_enabled").replace('_', ' '), fontSize = 8.sp) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }

                if (kind == "emergency") {
                    Text("Emergency control", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FilterChip(selected = mainText != "restore", onClick = { mainText = "lockdown" }, label = { Text("Lockdown") })
                        FilterChip(selected = mainText == "restore", onClick = { mainText = "restore" }, label = { Text("Restore services") })
                    }
                }

                if (kind == "date_range") {
                    OutlinedTextField(extraA, { extraA = it }, Modifier.fillMaxWidth(), label = { Text("From date/time") }, singleLine = true)
                    OutlinedTextField(extraB, { extraB = it }, Modifier.fillMaxWidth(), label = { Text("To date/time") }, singleLine = true)
                }

                if (kind == "report_assignee") {
                    Text("Assign to admin", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    AdminUserPicker(service, assignee) { assignee = it }
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
                            val textPayload = when {
                                kind == "feature_toggle" -> featureToggleKey
                                kind == "emergency" -> mainText.ifBlank { "lockdown" }
                                feature.featureId in setOf(83, 97, 124, 132, 152) -> university.ifBlank { mainText }
                                else -> mainText.ifBlank { null }
                            }
                            service.executeFeature(
                                featureId = feature.featureId,
                                entityRef = entityReference(),
                                text = textPayload,
                                amount = amount.toLongOrNull(),
                                durationHours = if (kind.contains("duration") || kind == "scheduled_message") durationDays * 24 else null,
                                options = options()
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
                    Text(result, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminUserPicker(
    service: AdminProSupabaseService,
    selected: ProAdminUser?,
    onSelected: (ProAdminUser) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<ProAdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Find user", fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Name, @username, email, user ID or profile link") },
                singleLine = true
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        service.searchUsers(query, 8).onSuccess { matches = it }
                        loading = false
                    }
                }
            ) { Text("Find", fontSize = 9.sp) }
        }
        selected?.let { user ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(7.dp)) {
                    Text("Selected: @${user.username}", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text("${user.fullName} • ${user.university}", fontSize = 9.sp)
                }
            }
        }
        matches.take(6).forEach { user ->
            TextButton(onClick = { onSelected(user); matches = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                Text("@${user.username} • ${user.fullName} • ${user.university}", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AdminMultiUserPicker(
    service: AdminProSupabaseService,
    selected: List<ProAdminUser>,
    onSelected: (List<ProAdminUser>) -> Unit
) {
    var latest by remember { mutableStateOf<ProAdminUser?>(null) }
    AdminUserPicker(service, latest) { user ->
        latest = user
        if (selected.none { it.id == user.id }) onSelected(selected + user)
    }
    if (selected.isNotEmpty()) {
        Text("Selected users (${selected.size})", fontWeight = FontWeight.Bold, fontSize = 9.sp)
        selected.forEach { user ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${user.username}", Modifier.weight(1f), fontSize = 9.sp)
                TextButton(onClick = { onSelected(selected.filterNot { it.id == user.id }) }) { Text("Remove", fontSize = 8.sp) }
            }
        }
    }
}

@Composable
private fun AdminPostPicker(
    service: AdminProSupabaseService,
    selected: ProAdminPost?,
    reelsOnly: Boolean,
    onSelected: (ProAdminPost) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<ProAdminPost>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (reelsOnly) "Find reel" else "Find post", fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                label = { Text("Paste post link, ID, @username, caption or keyword") },
                singleLine = true
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        service.searchPosts(query, 10).onSuccess { all -> matches = if (reelsOnly) all.filter { it.isReel } else all }
                        loading = false
                    }
                }
            ) { Text("Find", fontSize = 9.sp) }
        }
        selected?.let { post ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(7.dp)) {
                    Text("Selected ${if (post.isReel) "reel" else "post"} • @${post.username}", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(post.caption.ifBlank { post.text }.take(120), fontSize = 9.sp)
                    Text("${post.likes} likes • ${post.comments} comments • ${post.views} views", fontSize = 8.sp)
                }
            }
        }
        matches.take(6).forEach { post ->
            TextButton(onClick = { onSelected(post); matches = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                Text("@${post.username} • ${post.caption.ifBlank { post.text }.take(70)}", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AdminUniversityPicker(
    service: AdminProSupabaseService,
    selected: String,
    onSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { service.searchUniversities().onSuccess { remote = it } }
    val options = remember(query, remote) {
        (NigerianUniversities.all + remote)
            .distinct()
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .take(10)
    }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("University", fontWeight = FontWeight.Bold, fontSize = 10.sp)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search university") }, singleLine = true)
        if (selected.isNotBlank()) Text("Selected: $selected", color = BlinkPink, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        options.forEach { value ->
            TextButton(onClick = { onSelected(value); query = value }, modifier = Modifier.fillMaxWidth()) {
                Text(value, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AdminMultiUniversityPicker(
    service: AdminProSupabaseService,
    selected: List<String>,
    onSelected: (List<String>) -> Unit
) {
    var latest by remember { mutableStateOf("") }
    AdminUniversityPicker(service, latest) { value ->
        latest = value
        if (value !in selected) onSelected(selected + value)
    }
    if (selected.isNotEmpty()) {
        Text("Selected universities (${selected.size})", fontWeight = FontWeight.Bold, fontSize = 9.sp)
        selected.forEach { value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), fontSize = 9.sp)
                TextButton(onClick = { onSelected(selected - value) }) { Text("Remove", fontSize = 8.sp) }
            }
        }
    }
}
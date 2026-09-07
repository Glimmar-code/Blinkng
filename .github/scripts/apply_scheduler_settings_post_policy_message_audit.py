from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f'Expected block not found in {path}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# ScheduledPost model: retain server status/error so the management page can
# show pending, failed, published and cancelled records.
# ---------------------------------------------------------------------------
replace_once(
    'app/src/main/java/com/example/data/models/PostModel.kt',
    '''data class ScheduledPost(\n    val id: String = "sched_${System.currentTimeMillis()}",\n    val post: FeedPost,\n    val scheduledTimeMillis: Long,\n    val scheduledTimeFormatted: String\n)''',
    '''data class ScheduledPost(\n    val id: String = "sched_${System.currentTimeMillis()}",\n    val post: FeedPost,\n    val scheduledTimeMillis: Long,\n    val scheduledTimeFormatted: String,\n    val status: String = "pending",\n    val errorMessage: String? = null\n)'''
)

# ---------------------------------------------------------------------------
# ScheduledPostRepository: persist colored-text style and expose history.
# ---------------------------------------------------------------------------
repo = 'app/src/main/java/com/example/data/repository/ScheduledPostRepository.kt'
replace_once(
    repo,
    '''            put("audio_title", post.audioTitle ?: JSONObject.NULL)\n            put("alt_text", post.altText ?: JSONObject.NULL)\n            post.poll?.let { poll ->''',
    '''            put("audio_title", post.audioTitle ?: JSONObject.NULL)\n            put("alt_text", post.altText ?: JSONObject.NULL)\n            put("text_style", post.textStyle ?: JSONObject.NULL)\n            post.poll?.let { poll ->'''
)

text = read(repo)
start = text.index('    suspend fun fetchMine(): List<ScheduledPost> = withContext(Dispatchers.IO) {')
end = text.index('    suspend fun cancel(id: String): Boolean =', start)
new_fetch = '''    suspend fun fetchMine(includeHistory: Boolean = false): List<ScheduledPost> = withContext(Dispatchers.IO) {\n        val token = SupabaseService.accessToken() ?: return@withContext emptyList()\n        val statusClause = if (includeHistory) "" else "&status=in.(pending,failed)"\n        val request = Request.Builder()\n            .url(\n                "${SupabaseConfig.url.trimEnd('/')}/rest/v1/scheduled_feed_posts" +\n                    "?select=id,payload,scheduled_for,status,error_message${statusClause}&order=scheduled_for.desc&limit=100"\n            )\n            .addHeader("apikey", SupabaseConfig.anonKey)\n            .addHeader("Authorization", "Bearer $token")\n            .get()\n            .build()\n        client.newCall(request).execute().use { response ->\n            val raw = response.body?.string().orEmpty()\n            if (!response.isSuccessful || raw.isBlank()) return@withContext emptyList()\n            val array = JSONArray(raw)\n            buildList {\n                for (i in 0 until array.length()) {\n                    val row = array.getJSONObject(i)\n                    val scheduledIso = row.optString("scheduled_for")\n                    val millis = runCatching { Instant.parse(scheduledIso).toEpochMilli() }.getOrDefault(0L)\n                    add(\n                        ScheduledPost(\n                            id = row.optString("id"),\n                            post = parsePayload(row.optJSONObject("payload") ?: JSONObject()),\n                            scheduledTimeMillis = millis,\n                            scheduledTimeFormatted = formatTime(millis),\n                            status = row.optString("status", "pending"),\n                            errorMessage = row.optString("error_message").takeIf { it.isNotBlank() && it != "null" }\n                        )\n                    )\n                }\n            }\n        }\n    }\n\n'''
write(repo, text[:start] + new_fetch + text[end:])
replace_once(
    repo,
    '''            audioTitle = payload.optString("audio_title").takeIf { it.isNotBlank() && it != "null" },\n            altText = payload.optString("alt_text").takeIf { it.isNotBlank() && it != "null" }\n        )''',
    '''            audioTitle = payload.optString("audio_title").takeIf { it.isNotBlank() && it != "null" },\n            altText = payload.optString("alt_text").takeIf { it.isNotBlank() && it != "null" },\n            textStyle = payload.optString("text_style").takeIf { it.isNotBlank() && it != "null" }\n        )'''
)

# ---------------------------------------------------------------------------
# BlinkViewModel: replace device-only scheduled posts with the real server
# scheduler, including immediate upload of content:// media before scheduling.
# ---------------------------------------------------------------------------
vm = 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
replace_once(
    vm,
    '''    private val connectHubRepository = ConnectHubRepository(supabaseService)\n    val realtimeManager = SupabaseRealtimeManager.getInstance()''',
    '''    private val connectHubRepository = ConnectHubRepository(supabaseService)\n    private val scheduledPostRepository = ScheduledPostRepository()\n    val realtimeManager = SupabaseRealtimeManager.getInstance()'''
)
replace_once(
    vm,
    '''                    val storiesRequest = async {\n                        runCatching { supabaseService.fetchStories() }\n                            .onFailure { Log.e(TAG, "Stories fetch failed", it) }\n                    }\n                    val activitiesRequest = async {''',
    '''                    val storiesRequest = async {\n                        runCatching { supabaseService.fetchStories() }\n                            .onFailure { Log.e(TAG, "Stories fetch failed", it) }\n                    }\n                    val scheduledPostsRequest = async {\n                        runCatching { scheduledPostRepository.fetchMine() }\n                            .onFailure { Log.e(TAG, "Scheduled posts fetch failed", it) }\n                    }\n                    val activitiesRequest = async {'''
)
replace_once(
    vm,
    '''                    val connectHub = connectHubRequest.await()\n                        .getOrDefault(before.connectHub)\n\n                    val cloudStories = storiesRequest.await()''',
    '''                    val connectHub = connectHubRequest.await()\n                        .getOrDefault(before.connectHub)\n\n                    val scheduledPosts = scheduledPostsRequest.await()\n                        .getOrDefault(before.scheduledPosts)\n\n                    val cloudStories = storiesRequest.await()'''
)
replace_once(
    vm,
    '''                        connectHub = connectHub,\n                        isConnectHubLoading = false,\n                        stories = mergedStories,''',
    '''                        connectHub = connectHub,\n                        scheduledPosts = scheduledPosts,\n                        isConnectHubLoading = false,\n                        stories = mergedStories,'''
)

text = read(vm)
start = text.index('    fun schedulePost(post: FeedPost, timeMillis: Long, timeFormatted: String)')
end = text.index('    fun addPost(', start)
new_schedule_block = '''    fun refreshScheduledPosts() {\n        if (!_uiState.value.isOnline) return\n        viewModelScope.launch(Dispatchers.IO) {\n            try {\n                val remote = scheduledPostRepository.fetchMine()\n                withContext(Dispatchers.Main) {\n                    _uiState.value = _uiState.value.copy(scheduledPosts = remote)\n                }\n            } catch (e: Exception) {\n                Log.w(TAG, "Scheduled posts refresh failed", e)\n            }\n        }\n    }\n\n    fun schedulePost(post: FeedPost, timeMillis: Long, timeFormatted: String) {\n        if (_uiState.value.isCreatingPost) return\n        if (timeMillis < System.currentTimeMillis() + 60_000L) {\n            showToast("Choose a time at least one minute from now.")\n            return\n        }\n\n        val profile = _uiState.value.myProfile\n        val userId = supabaseService.getCurrentUserId()\n            ?: profile.id.takeIf { it.isNotBlank() }\n        if (userId.isNullOrBlank()) {\n            showToast("Please sign in again before scheduling a post.")\n            return\n        }\n\n        _uiState.value = _uiState.value.copy(isCreatingPost = true)\n        viewModelScope.launch(Dispatchers.IO) {\n            try {\n                val uploadedImages = mutableListOf<String>()\n                for (input in post.images) {\n                    val uploaded = if (input.startsWith("content://")) {\n                        uploadPostUri(userId, input, false)\n                    } else input\n                    if (uploaded.isNullOrBlank()) {\n                        throw IllegalStateException("One of the selected images could not be uploaded.")\n                    }\n                    uploadedImages += uploaded\n                }\n\n                val uploadedVideo = post.videoUrl?.let { input ->\n                    if (input.startsWith("content://")) uploadPostUri(userId, input, true) else input\n                }\n                if (!post.videoUrl.isNullOrBlank() && uploadedVideo.isNullOrBlank()) {\n                    throw IllegalStateException("The selected video could not be uploaded.")\n                }\n\n                val remotePost = post.copy(\n                    author = profile.username,\n                    authorAvatar = profile.avatarUrl,\n                    images = uploadedImages,\n                    videoUrl = uploadedVideo,\n                    isReel = post.isReel || !uploadedVideo.isNullOrBlank()\n                )\n                val scheduleId = scheduledPostRepository.schedule(remotePost, timeMillis)\n                if (scheduleId.isBlank()) throw IllegalStateException("Supabase did not save the schedule.")\n                val latest = scheduledPostRepository.fetchMine()\n\n                withContext(Dispatchers.Main) {\n                    _uiState.value = _uiState.value.copy(\n                        scheduledPosts = latest,\n                        isCreatePostOpen = false,\n                        isCreatingPost = false\n                    )\n                    showToast("Post scheduled for $timeFormatted")\n                }\n            } catch (e: Exception) {\n                Log.e(TAG, "Schedule post failed", e)\n                withContext(Dispatchers.Main) {\n                    showToast(e.message ?: "Couldn't schedule the post.")\n                }\n            } finally {\n                withContext(Dispatchers.Main) {\n                    _uiState.value = _uiState.value.copy(isCreatingPost = false)\n                }\n            }\n        }\n    }\n\n    fun deleteScheduledPost(id: String) {\n        viewModelScope.launch(Dispatchers.IO) {\n            try {\n                scheduledPostRepository.cancel(id)\n                val latest = scheduledPostRepository.fetchMine()\n                withContext(Dispatchers.Main) {\n                    _uiState.value = _uiState.value.copy(scheduledPosts = latest)\n                    showToast("Scheduled post cancelled.")\n                }\n            } catch (e: Exception) {\n                Log.e(TAG, "Cancel scheduled post failed", e)\n                withContext(Dispatchers.Main) { showToast(e.message ?: "Couldn't cancel scheduled post.") }\n            }\n        }\n    }\n\n    fun publishScheduledPostNow(id: String) {\n        viewModelScope.launch(Dispatchers.IO) {\n            try {\n                scheduledPostRepository.publishNow(id)\n                val latest = scheduledPostRepository.fetchMine()\n                withContext(Dispatchers.Main) {\n                    _uiState.value = _uiState.value.copy(scheduledPosts = latest)\n                    showToast("Scheduled post published.")\n                    fetchSupabaseData()\n                }\n            } catch (e: Exception) {\n                Log.e(TAG, "Publish scheduled post failed", e)\n                withContext(Dispatchers.Main) { showToast(e.message ?: "Couldn't publish scheduled post.") }\n            }\n        }\n    }\n\n'''
write(vm, text[:start] + new_schedule_block + text[end:])

# ---------------------------------------------------------------------------
# Create Post: expose quick scheduling choices and build a real pending FeedPost.
# ---------------------------------------------------------------------------
composer = 'app/src/main/java/com/example/ui/components/CreatePostSheet.kt'
replace_once(
    composer,
    'import androidx.compose.material.icons.filled.Save\nimport androidx.compose.material.icons.filled.VideoLibrary',
    'import androidx.compose.material.icons.filled.Save\nimport androidx.compose.material.icons.filled.Schedule\nimport androidx.compose.material.icons.filled.VideoLibrary'
)
replace_once(
    composer,
    'import org.json.JSONArray\nimport java.util.UUID',
    'import org.json.JSONArray\nimport java.time.Instant\nimport java.time.ZoneId\nimport java.time.format.DateTimeFormatter\nimport java.util.UUID'
)
replace_once(
    composer,
    '    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }\n    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }',
    '    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }\n    var showScheduleDialog by rememberSaveable { mutableStateOf(false) }\n    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }'
)
replace_once(
    composer,
    '''    val canSubmit = hasContent && !isSubmitting\n\n    fun submit() {''',
    '''    val canSubmit = hasContent && !isSubmitting\n\n    fun scheduledPreviewPost(): FeedPost {\n        val poll = if (pollValid) {\n            PostPoll(\n                question = pollQuestion.trim(),\n                options = validPollOptions.map { PollOption(UUID.randomUUID().toString(), it) }\n            )\n        } else null\n        val style = selectedTextStyle.takeIf {\n            textPresentation == "color" &&\n                cleanText.isNotBlank() &&\n                selectedImages.isEmpty() &&\n                selectedVideo == null &&\n                !pollValid\n        }\n        return FeedPost(\n            id = "scheduled_preview_${System.currentTimeMillis()}",\n            author = profile.username,\n            authorAvatar = profile.avatarUrl,\n            facultyTag = profile.faculty,\n            timeAgo = "Scheduled",\n            text = cleanText,\n            images = selectedImages,\n            likes = 0,\n            commentsCount = 0,\n            sharesCount = 0,\n            isReel = selectedVideo != null,\n            videoUrl = selectedVideo,\n            poll = poll,\n            audience = audience,\n            category = category,\n            allowComments = allowComments,\n            textStyle = style\n        )\n    }\n\n    fun scheduleAfter(delayMillis: Long) {\n        if (!canSubmit) return\n        val timeMillis = System.currentTimeMillis() + delayMillis\n        val formatted = Instant.ofEpochMilli(timeMillis)\n            .atZone(ZoneId.systemDefault())\n            .format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"))\n        showScheduleDialog = false\n        onSchedulePost(scheduledPreviewPost(), timeMillis, formatted)\n    }\n\n    fun submit() {'''
)
replace_once(
    composer,
    '''                Row(\n                    Modifier\n                        .fillMaxWidth()\n                        .padding(horizontal = 16.dp, vertical = 14.dp),''',
    '''                OutlinedButton(\n                    onClick = { showScheduleDialog = true },\n                    enabled = canSubmit,\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(horizontal = 16.dp, vertical = 4.dp)\n                ) {\n                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(7.dp))\n                    Text("Schedule for later")\n                }\n\n                Row(\n                    Modifier\n                        .fillMaxWidth()\n                        .padding(horizontal = 16.dp, vertical = 14.dp),'''
)
replace_once(
    composer,
    '''    if (showDiscardDialog) {\n        AlertDialog(''',
    '''    if (showScheduleDialog) {\n        AlertDialog(\n            onDismissRequest = { showScheduleDialog = false },\n            title = { Text("Schedule this post") },\n            text = {\n                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Text(\n                        "The media is uploaded now and Supabase publishes the post automatically at the selected time.",\n                        color = MaterialTheme.colorScheme.onSurfaceVariant\n                    )\n                    listOf(\n                        "In 30 minutes" to 30L * 60_000L,\n                        "In 1 hour" to 60L * 60_000L,\n                        "In 5 hours" to 5L * 60L * 60_000L,\n                        "In 1 day" to 24L * 60L * 60_000L\n                    ).forEach { (label, delayMillis) ->\n                        OutlinedButton(\n                            onClick = { scheduleAfter(delayMillis) },\n                            enabled = !isSubmitting,\n                            modifier = Modifier.fillMaxWidth()\n                        ) { Text(label) }\n                    }\n                }\n            },\n            confirmButton = {\n                TextButton(onClick = { showScheduleDialog = false }) { Text("Close") }\n            }\n        )\n    }\n\n    if (showDiscardDialog) {\n        AlertDialog('''
)

# ---------------------------------------------------------------------------
# Menu: give scheduled posts a first-class management entry.
# ---------------------------------------------------------------------------
menu = 'app/src/main/java/com/example/ui/components/AppMenuSheet.kt'
replace_once(
    menu,
    'import androidx.compose.material.icons.outlined.Security\nimport androidx.compose.material.icons.outlined.ShoppingBag',
    'import androidx.compose.material.icons.outlined.Security\nimport androidx.compose.material.icons.outlined.Schedule\nimport androidx.compose.material.icons.outlined.ShoppingBag'
)
replace_once(
    menu,
    '''            ) {\n                MenuItemRow(Icons.Outlined.Groups, "Study & group center", "Group chats and study tools") {''',
    '''            ) {\n                MenuItemRow(Icons.Outlined.Schedule, "Scheduled posts", "Manage posts queued for later") {\n                    openProfessional("scheduled")\n                }\n                MenuItemRow(Icons.Outlined.Groups, "Study & group center", "Group chats and study tools") {'''
)

# ---------------------------------------------------------------------------
# Professional Center: add scheduled-post page and surface notification prefs.
# ---------------------------------------------------------------------------
prof = 'app/src/main/java/com/example/ProfessionalCenterActivity.kt'
replace_once(
    prof,
    'import com.example.data.models.MarketplaceOrder\nimport com.example.data.models.UserProfile',
    'import com.example.data.models.MarketplaceOrder\nimport com.example.data.models.ScheduledPost\nimport com.example.data.models.UserProfile'
)
replace_once(
    prof,
    'import com.example.data.repository.ProfessionalRepository\nimport com.example.data.supabase.SupabaseService',
    'import com.example.data.repository.ProfessionalRepository\nimport com.example.data.repository.ScheduledPostRepository\nimport com.example.data.supabase.SupabaseService'
)
replace_once(
    prof,
    '''private enum class ProfessionalSection(val key: String, val label: String) {\n    PRIVACY("privacy", "Privacy"),\n    SAFETY("safety", "Safety"),''',
    '''private enum class ProfessionalSection(val key: String, val label: String) {\n    PRIVACY("privacy", "Privacy"),\n    SCHEDULED("scheduled", "Scheduled"),\n    SAFETY("safety", "Safety"),'''
)
replace_once(
    prof,
    '''    val repository = remember { ProfessionalRepository() }\n    val service = remember { SupabaseService() }''',
    '''    val repository = remember { ProfessionalRepository() }\n    val scheduledRepository = remember { ScheduledPostRepository() }\n    val service = remember { SupabaseService() }'''
)
replace_once(
    prof,
    '''    var orders by remember { mutableStateOf<List<MarketplaceOrder>>(emptyList()) }\n    var wishlistCount by remember { mutableStateOf(0) }''',
    '''    var orders by remember { mutableStateOf<List<MarketplaceOrder>>(emptyList()) }\n    var scheduledPosts by remember { mutableStateOf<List<ScheduledPost>>(emptyList()) }\n    var wishlistCount by remember { mutableStateOf(0) }'''
)
replace_once(
    prof,
    '''            orders = repository.fetchMarketplaceOrders()\n            wishlistCount = repository.fetchWishlistIds().size''',
    '''            orders = repository.fetchMarketplaceOrders()\n            scheduledPosts = scheduledRepository.fetchMine(includeHistory = true)\n            wishlistCount = repository.fetchWishlistIds().size'''
)
replace_once(
    prof,
    '''                    ProfessionalSection.SAFETY -> SafetySection(''',
    '''                    ProfessionalSection.SCHEDULED -> ScheduledPostsSection(\n                        posts = scheduledPosts,\n                        busy = busy,\n                        onPublishNow = { id ->\n                            scope.launch {\n                                busy = true\n                                runCatching { scheduledRepository.publishNow(id) }\n                                    .onSuccess {\n                                        Toast.makeText(context, "Scheduled post published.", Toast.LENGTH_SHORT).show()\n                                        reload()\n                                    }\n                                    .onFailure { Toast.makeText(context, it.message ?: "Publish failed", Toast.LENGTH_SHORT).show() }\n                                busy = false\n                            }\n                        },\n                        onCancel = { id ->\n                            scope.launch {\n                                busy = true\n                                runCatching { scheduledRepository.cancel(id) }\n                                    .onSuccess {\n                                        Toast.makeText(context, "Scheduled post cancelled.", Toast.LENGTH_SHORT).show()\n                                        reload()\n                                    }\n                                    .onFailure { Toast.makeText(context, it.message ?: "Cancel failed", Toast.LENGTH_SHORT).show() }\n                                busy = false\n                            }\n                        }\n                    )\n\n                    ProfessionalSection.SAFETY -> SafetySection('''
)
replace_once(
    prof,
    '''        item { ToggleRow("Private account", "Restrict profile visibility.", settings.privateAccount, busy) { onChange(settings.copy(privateAccount = it)) } }''',
    '''        item { ToggleRow("Push notifications", "Receive activity and unread-message alerts.", settings.pushNotifications, busy) { onChange(settings.copy(pushNotifications = it)) } }\n        item { ToggleRow("Email notifications", "Allow account and activity emails.", settings.emailNotifications, busy) { onChange(settings.copy(emailNotifications = it)) } }\n        item { ToggleRow("Private account", "Restrict profile visibility.", settings.privateAccount, busy) { onChange(settings.copy(privateAccount = it)) } }'''
)
marker = '@Composable\nprivate fun SafetySection('
text = read(prof)
idx = text.index(marker)
scheduled_ui = '''@Composable\nprivate fun ScheduledPostsSection(\n    posts: List<ScheduledPost>,\n    busy: Boolean,\n    onPublishNow: (String) -> Unit,\n    onCancel: (String) -> Unit\n) {\n    LazyColumn(\n        Modifier.fillMaxSize(),\n        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 6.dp, 14.dp, 28.dp),\n        verticalArrangement = Arrangement.spacedBy(9.dp)\n    ) {\n        item { Header(Icons.Default.Inventory2, "Scheduled posts", "Supabase keeps these schedules even when the app is closed or reinstalled.") }\n        if (posts.isEmpty()) {\n            item { EmptyState("Nothing scheduled", "Create a post and choose Schedule for later.") }\n        }\n        items(posts, key = { it.id }) { scheduled ->\n            Card(\n                modifier = Modifier.fillMaxWidth(),\n                shape = RoundedCornerShape(18.dp),\n                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))\n            ) {\n                Column(Modifier.padding(14.dp)) {\n                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                        Text(\n                            scheduled.status.replaceFirstChar(Char::uppercase),\n                            modifier = Modifier.weight(1f),\n                            fontWeight = FontWeight.Black,\n                            color = if (scheduled.status == "failed") MaterialTheme.colorScheme.error else BlinkPink\n                        )\n                        Text(scheduled.scheduledTimeFormatted, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                    Spacer(Modifier.height(7.dp))\n                    Text(\n                        scheduled.post.text.ifBlank { if (!scheduled.post.videoUrl.isNullOrBlank()) "Reel" else "Media post" },\n                        maxLines = 3,\n                        overflow = TextOverflow.Ellipsis,\n                        fontWeight = FontWeight.SemiBold\n                    )\n                    scheduled.errorMessage?.let {\n                        Spacer(Modifier.height(6.dp))\n                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)\n                    }\n                    if (scheduled.status == "pending" || scheduled.status == "failed") {\n                        Spacer(Modifier.height(10.dp))\n                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                            Button(\n                                onClick = { onPublishNow(scheduled.id) },\n                                enabled = !busy,\n                                modifier = Modifier.weight(1f)\n                            ) { Text(if (scheduled.status == "failed") "Retry now" else "Publish now") }\n                            OutlinedButton(\n                                onClick = { onCancel(scheduled.id) },\n                                enabled = !busy && scheduled.status == "pending",\n                                modifier = Modifier.weight(1f)\n                            ) { Text("Cancel") }\n                        }\n                    }\n                }\n            }\n        }\n    }\n}\n\n'''
write(prof, text[:idx] + scheduled_ui + text[idx:])

# ---------------------------------------------------------------------------
# Password security: require a stronger password in UI and in the repository
# call path. Supabase's leaked-password project toggle is separate platform
# configuration, but the app itself no longer accepts six-character passwords.
# ---------------------------------------------------------------------------
auth_ui = 'app/src/main/java/com/example/ui/screens/AuthScreens.kt'
replace_once(
    auth_ui,
    '''// SIGN UP\n// ================================================================\n\n@OptIn(ExperimentalMaterial3Api::class)''',
    '''// SIGN UP\n// ================================================================\n\nprivate fun isStrongBlinkPassword(password: String): Boolean =\n    password.length >= 8 &&\n        password.any(Char::isLowerCase) &&\n        password.any(Char::isUpperCase) &&\n        password.any(Char::isDigit) &&\n        password.any { !it.isLetterOrDigit() && !it.isWhitespace() }\n\n@OptIn(ExperimentalMaterial3Api::class)'''
)
replace_once(auth_ui, '                password.length >= 6 &&', '                isStrongBlinkPassword(password) &&')
replace_once(
    auth_ui,
    '''                            password.length < 6 ->\n                                validationError =\n                                    "Your password should contain at least 6 characters."''',
    '''                            !isStrongBlinkPassword(password) ->\n                                validationError =\n                                    "Use at least 8 characters with uppercase, lowercase, a number, and a symbol."'''
)

service = 'app/src/main/java/com/example/data/supabase/SupabaseService.kt'
replace_once(
    service,
    '''                if (cleanEmail.isBlank()) {\n                    return@withContext Result.failure(Exception("Email address is required."))\n                }\n                val cleanUsername = username.trim().lowercase(Locale.US).replace("@", "").replace(" ", "_")''',
    '''                if (cleanEmail.isBlank()) {\n                    return@withContext Result.failure(Exception("Email address is required."))\n                }\n                val strongPassword = password.length >= 8 &&\n                    password.any(Char::isLowerCase) &&\n                    password.any(Char::isUpperCase) &&\n                    password.any(Char::isDigit) &&\n                    password.any { !it.isLetterOrDigit() && !it.isWhitespace() }\n                if (!strongPassword) {\n                    return@withContext Result.failure(\n                        Exception("Use at least 8 characters with uppercase, lowercase, a number, and a symbol.")\n                    )\n                }\n                val cleanUsername = username.trim().lowercase(Locale.US).replace("@", "").replace(" ", "_")'''
)

print('Applied scheduler, Professional Center, post reliability, message and password hardening patches.')

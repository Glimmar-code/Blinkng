from pathlib import Path
import re

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding='utf-8')


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise SystemExit(f'Expected pattern not found in {path}: {old[:120]!r}')
    write(path, text.replace(old, new, 1))


def replace_all_required(path: str, old: str, new: str, minimum: int = 1) -> None:
    text = read(path)
    count = text.count(old)
    if count < minimum:
        raise SystemExit(f'Expected >= {minimum} matches in {path}, found {count}: {old[:120]!r}')
    write(path, text.replace(old, new))


# Gradle public share URL + manifest host.
replace_once(
    'app/build.gradle.kts',
    '    versionName = "1.0"\n',
    '    versionName = "1.0"\n'
    '    manifestPlaceholders["shareHost"] = "my-app.com"\n'
    '    buildConfigField("String", "SHARE_BASE_URL", "\\\"https://my-app.com\\\"")\n'
)

# Android App Links.
replace_once(
    'app/src/main/AndroidManifest.xml',
    '<activity android:name=".MainActivity" android:exported="true" android:label="@string/app_name" android:theme="@style/Theme.MyApplication">',
    '<activity android:name=".MainActivity" android:exported="true" android:label="@string/app_name" android:launchMode="singleTop" android:theme="@style/Theme.MyApplication">'
)
replace_once(
    'app/src/main/AndroidManifest.xml',
    '''            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n''',
    '''            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n\n            <intent-filter android:autoVerify="true">\n                <action android:name="android.intent.action.VIEW" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <category android:name="android.intent.category.BROWSABLE" />\n\n                <data android:scheme="https" />\n                <data android:host="${shareHost}" />\n                <data android:pathPrefix="/profile/" />\n                <data android:pathPrefix="/post/" />\n                <data android:pathPrefix="/reel/" />\n            </intent-filter>\n'''
)

# SupabaseService: exact content lookup and better timestamps.
replace_once(
    'app/src/main/java/com/example/data/supabase/SupabaseService.kt',
    'import com.example.data.models.IdentityAvailability\n',
    'import com.example.data.models.IdentityAvailability\nimport com.example.util.TimeFormatters\n'
)
replace_once(
    'app/src/main/java/com/example/data/supabase/SupabaseService.kt',
    '''    suspend fun fetchFeedPosts(): List<FeedPost> =\n        fetchFeedPage(limit = 40, feedType = "all")\n\n    suspend fun fetchFeedPage(\n''',
    '''    suspend fun fetchFeedPosts(): List<FeedPost> =\n        fetchFeedPage(limit = 40, feedType = "all")\n\n    suspend fun fetchFeedPostById(postId: String): FeedPost? = withContext(Dispatchers.IO) {\n        val cleanId = postId.trim()\n        if (!isValidUuid(cleanId)) return@withContext null\n\n        if (!isAuthenticated() && !refreshToken().isNullOrBlank()) {\n            refreshSession()\n        }\n        val uid = getCurrentUserId() ?: return@withContext null\n\n        val source = executeRequest(\n            newRequestBuilder(\n                "/rest/v1/feed_posts?id=eq.${encodeValue(cleanId)}&is_active=eq.true&select=*&limit=1",\n                authenticated = true\n            ).get().build()\n        ).use { response ->\n            val raw = response.body?.string().orEmpty()\n            if (!response.isSuccessful || raw.isBlank() || raw == "[]") return@withContext null\n            JSONArray(raw).optJSONObject(0) ?: return@withContext null\n        }\n\n        val profile = fetchProfileById(source.cleanString("user_id")) ?: return@withContext null\n        val mapped = JSONObject(source.toString()).apply {\n            put("author", profile.username)\n            put("author_avatar", profile.avatarUrl)\n            put("username", profile.username)\n            put("is_verified", profile.verificationBadge != VerificationBadge.NONE)\n            put("verification_badge", profile.verificationBadge.name)\n        }\n\n        val liked = executeRequest(\n            newRequestBuilder(\n                "/rest/v1/post_likes?post_id=eq.${encodeValue(cleanId)}&user_id=eq.${encodeValue(uid)}&select=post_id&limit=1",\n                true\n            ).get().build()\n        ).use { response ->\n            val raw = response.body?.string().orEmpty()\n            response.isSuccessful && raw.isNotBlank() && raw != "[]"\n        }\n\n        val bookmarked = executeRequest(\n            newRequestBuilder(\n                "/rest/v1/post_bookmarks?post_id=eq.${encodeValue(cleanId)}&user_id=eq.${encodeValue(uid)}&select=post_id&limit=1",\n                true\n            ).get().build()\n        ).use { response ->\n            val raw = response.body?.string().orEmpty()\n            response.isSuccessful && raw.isNotBlank() && raw != "[]"\n        }\n\n        parseFeedPost(mapped).copy(isLiked = liked, isBookmarked = bookmarked)\n    }\n\n    suspend fun fetchFeedPage(\n'''
)
replace_once(
    'app/src/main/java/com/example/data/supabase/SupabaseService.kt',
    '            timeAgo = obj.cleanString("time_ago", "Recently"),',
    '            timeAgo = obj.cleanString("created_at").takeIf { it.isNotBlank() }?.let(TimeFormatters::relativeOrDate)\n                ?: obj.cleanString("time_ago", "Recently"),'
)
text = read('app/src/main/java/com/example/data/supabase/SupabaseService.kt')
pattern = re.compile(r'''    private fun formatTimeAgo\(\n        dateString: String\n    \): String \{.*?\n    \}\n\n    // COMMENTS''', re.S)
text, count = pattern.subn(
    '''    private fun formatTimeAgo(dateString: String): String =\n        TimeFormatters.relativeOrDate(dateString)\n\n    // COMMENTS''',
    text,
    count=1
)
if count != 1:
    raise SystemExit(f'Could not replace SupabaseService.formatTimeAgo (count={count})')
write('app/src/main/java/com/example/data/supabase/SupabaseService.kt', text)

# Repository exact post/reel fetch.
replace_once(
    'app/src/main/java/com/example/data/repository/PostRepository.kt',
    '    suspend fun fetchFeedPage(\n',
    '''    suspend fun fetchPostById(postId: String): FeedPost? = withContext(Dispatchers.IO) {\n        supabaseService.fetchFeedPostById(postId)\n    }\n\n    suspend fun fetchFeedPage(\n'''
)

# Last-seen uses same minute/hour/day/date formatter.
replace_once(
    'app/src/main/java/com/example/data/repository/ChatRepository.kt',
    'import com.example.data.supabase.SupabaseService\n',
    'import com.example.data.supabase.SupabaseService\nimport com.example.util.TimeFormatters\n'
)
replace_once(
    'app/src/main/java/com/example/data/repository/ChatRepository.kt',
    '                            lastSeen = o.optString("partner_last_seen").ifBlank { "Last seen recently" },',
    '''                            lastSeen = o.optString("partner_last_seen")\n                                .takeIf { it.isNotBlank() && !it.equals("null", true) }\n                                ?.let(TimeFormatters::relativeOrDate)\n                                ?: "recently",'''
)

# ViewModel pending deep-link routing.
replace_once(
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt',
    'import com.example.notification.BlinkNotificationHelper\n',
    'import com.example.notification.BlinkNotificationHelper\nimport com.example.sharing.AppDeepLink\nimport com.example.sharing.ShareContentType\n'
)
replace_once(
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt',
    '    val activePostOptionsPost: FeedPost? = null,\n',
    '    val activePostOptionsPost: FeedPost? = null,\n    val deepLinkedPost: FeedPost? = null,\n'
)
replace_once(
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt',
    '    private var discoverSearchJob: Job? = null\n',
    '    private var discoverSearchJob: Job? = null\n    private var pendingDeepLink: AppDeepLink? = null\n'
)
replace_once(
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt',
    '        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }\n',
    '''        viewModelScope.launch { realtimeManager.events.collect { handleRealtimeEvent(it) } }\n        viewModelScope.launch {\n            _uiState.collectLatest { state ->\n                val link = pendingDeepLink\n                if (link != null && state.destination == AppDestination.MAIN && state.myProfile.id.isNotBlank()) {\n                    pendingDeepLink = null\n                    routeDeepLink(link)\n                }\n            }\n        }\n'''
)
replace_once(
    'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt',
    '    private fun observeAuthState() {\n',
    '''    fun handleDeepLink(link: AppDeepLink) {\n        pendingDeepLink = link\n        val state = _uiState.value\n        if (state.destination == AppDestination.MAIN && state.myProfile.id.isNotBlank()) {\n            pendingDeepLink = null\n            routeDeepLink(link)\n        }\n    }\n\n    private fun routeDeepLink(link: AppDeepLink) {\n        viewModelScope.launch {\n            when (link.type) {\n                ShareContentType.PROFILE -> {\n                    val profile = runCatching { supabaseService.fetchProfileById(link.id) }.getOrNull()\n                    if (profile == null) {\n                        showToast("This profile is unavailable.")\n                    } else {\n                        val state = _uiState.value\n                        _uiState.value = state.copy(\n                            selectedTab = MainTab.HOME,\n                            viewingProfile = profile,\n                            deepLinkedPost = null,\n                            activePostOptionsPost = null,\n                            activeCommentsPostId = null\n                        )\n                        persistProfile(profile)\n                    }\n                }\n\n                ShareContentType.POST, ShareContentType.REEL -> {\n                    val post = runCatching { postRepository.fetchPostById(link.id) }.getOrNull()\n                    val expectsReel = link.type == ShareContentType.REEL\n                    if (post == null || post.isReel != expectsReel) {\n                        showToast(if (expectsReel) "This reel is unavailable." else "This post is unavailable.")\n                        return@launch\n                    }\n\n                    val state = _uiState.value\n                    if (expectsReel) {\n                        _uiState.value = state.copy(\n                            selectedTab = MainTab.HOME,\n                            feedSubTab = 1,\n                            reels = listOf(post) + state.reels.filterNot { it.id == post.id },\n                            viewingProfile = null,\n                            deepLinkedPost = null,\n                            activePostOptionsPost = null,\n                            activeCommentsPostId = null\n                        )\n                    } else {\n                        _uiState.value = state.copy(\n                            selectedTab = MainTab.HOME,\n                            feedSubTab = 0,\n                            posts = listOf(post) + state.posts.filterNot { it.id == post.id },\n                            viewingProfile = null,\n                            deepLinkedPost = post,\n                            activePostOptionsPost = null,\n                            activeCommentsPostId = null\n                        )\n                    }\n                    persistCurrentFeed()\n                }\n            }\n        }\n    }\n\n    fun closeDeepLinkedPost() {\n        _uiState.value = _uiState.value.copy(deepLinkedPost = null)\n    }\n\n    private fun observeAuthState() {\n'''
)

# MainActivity catches both cold/warm App Links and uses the native share sheet.
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    'import com.example.notification.BlinkFirebaseMessagingService\n',
    'import com.example.notification.BlinkFirebaseMessagingService\nimport com.example.sharing.DeepLinkRouter\nimport com.example.sharing.ShareContentType\nimport com.example.sharing.ShareLinkManager\n'
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '''        setIntent(intent)\n        handleNotificationIntent(intent)\n    }\n\n    private fun handleNotificationIntent(intent: Intent?) {\n''',
    '''        setIntent(intent)\n        handleIncomingIntent(intent)\n    }\n\n    private fun handleIncomingIntent(intent: Intent?) {\n        handleNotificationIntent(intent)\n        DeepLinkRouter.parse(intent?.data)?.let { deepLink ->\n            viewModel.handleDeepLink(deepLink)\n            intent?.data = null\n        }\n    }\n\n    private fun handleNotificationIntent(intent: Intent?) {\n'''
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '        handleNotificationIntent(intent)\n    }\n}\n\n@Composable\nfun MainAppContent(',
    '        handleIncomingIntent(intent)\n    }\n}\n\n@Composable\nfun MainAppContent('
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '''    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }\n    var homeReselectSignal by rememberSaveable { mutableIntStateOf(0) }\n\n''',
    '''    var isBottomBarVisibleByScroll by rememberSaveable { mutableStateOf(true) }\n    var homeReselectSignal by rememberSaveable { mutableIntStateOf(0) }\n    val context = androidx.compose.ui.platform.LocalContext.current\n\n    fun sharePostOrReel(postId: String) {\n        val post = (uiState.posts + uiState.reels).firstOrNull { it.id == postId }\n        if (post == null) {\n            viewModel.showToast("This content is unavailable.")\n            return\n        }\n        viewModel.sharePost(postId)\n        ShareLinkManager.share(\n            context = context,\n            type = if (post.isReel) ShareContentType.REEL else ShareContentType.POST,\n            id = post.id,\n            title = if (post.isReel) "Share reel" else "Share post",\n            message = post.text.take(180),\n            previewImageUrl = post.images.firstOrNull() ?: post.authorAvatar.takeIf { it.isNotBlank() }\n        )\n    }\n\n'''
)
replace_all_required(
    'app/src/main/java/com/example/MainActivity.kt',
    'onSharePost = { viewModel.sharePost(it) },',
    'onSharePost = { sharePostOrReel(it) },',
    minimum=3
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '                uiState.showSellerCongratulationsDialog\n',
    '                uiState.showSellerCongratulationsDialog ||\n                uiState.deepLinkedPost != null\n'
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '''        when {\n            uiState.activePostOptionsPost != null -> viewModel.openPostOptions(null)\n''',
    '''        when {\n            uiState.deepLinkedPost != null -> viewModel.closeDeepLinkedPost()\n            uiState.activePostOptionsPost != null -> viewModel.openPostOptions(null)\n'''
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '''                uiState.activeCommentsPostId == null &&\n                !uiState.isMenuOpen &&\n''',
    '''                uiState.activeCommentsPostId == null &&\n                uiState.deepLinkedPost == null &&\n                !uiState.isMenuOpen &&\n'''
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '        // Sub-screen Overlays: Product Detail\n',
    '''        AnimatedVisibility(\n            visible = uiState.deepLinkedPost != null,\n            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),\n            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()\n        ) {\n            uiState.deepLinkedPost?.let { post ->\n                SharedPostScreen(\n                    post = post,\n                    currentUsername = uiState.myProfile.username,\n                    isDark = uiState.isDarkMode,\n                    onBack = { viewModel.closeDeepLinkedPost() },\n                    onLike = { viewModel.togglePostLike(post.id) },\n                    onComment = { viewModel.openCommentsForPost(post.id) },\n                    onBookmark = { viewModel.toggleBookmark(post.id) },\n                    onShare = { sharePostOrReel(post.id) },\n                    onOptions = { viewModel.openPostOptions(post) },\n                    onDelete = { viewModel.deletePost(post.id) },\n                    onProfileClick = { viewModel.openProfile(it) },\n                    onVotePoll = { postId, optionId -> viewModel.votePoll(postId, optionId) }\n                )\n            }\n        }\n\n        // Sub-screen Overlays: Product Detail\n'''
)
replace_once(
    'app/src/main/java/com/example/MainActivity.kt',
    '                onShare = { viewModel.sharePost(post.id) },',
    '                onShare = { sharePostOrReel(post.id) },'
)

# Profile native sharing/copy + avatar preview.
replace_once(
    'app/src/main/java/com/example/ui/screens/ProfileScreen.kt',
    'import com.example.ui.theme.*\n',
    'import com.example.ui.theme.*\nimport com.example.sharing.ShareContentType\nimport com.example.sharing.ShareLinkManager\n'
)
replace_once(
    'app/src/main/java/com/example/ui/screens/ProfileScreen.kt',
    '''        ProfileShareSheet(\n            username = profile.username,\n            fullName = profile.fullName,\n            onDismiss = { showShareSheet = false },\n            onCopy = {\n                clipboard.setText(AnnotatedString("https://blink.app/@${profile.username}"))\n                Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()\n                showShareSheet = false\n            }\n        )\n''',
    '''        val shareProfileId = profile.id.ifBlank { profile.username }\n        ProfileShareSheet(\n            username = profile.username,\n            fullName = profile.fullName,\n            avatarUrl = profile.avatarUrl,\n            onDismiss = { showShareSheet = false },\n            onShare = {\n                ShareLinkManager.share(\n                    context = context,\n                    type = ShareContentType.PROFILE,\n                    id = shareProfileId,\n                    title = "Share ${profile.fullName}",\n                    message = "View @${profile.username} on Blink",\n                    previewImageUrl = profile.avatarUrl\n                )\n                showShareSheet = false\n            },\n            onCopy = {\n                ShareLinkManager.copyLink(\n                    context = context,\n                    type = ShareContentType.PROFILE,\n                    id = shareProfileId,\n                    toastMessage = "Profile link copied"\n                )\n                showShareSheet = false\n            }\n        )\n'''
)
replace_once(
    'app/src/main/java/com/example/ui/screens/ProfileScreen.kt',
    '''private fun ProfileShareSheet(\n    username: String,\n    fullName: String,\n    onDismiss: () -> Unit,\n    onCopy: () -> Unit\n) {\n''',
    '''private fun ProfileShareSheet(\n    username: String,\n    fullName: String,\n    avatarUrl: String,\n    onDismiss: () -> Unit,\n    onShare: () -> Unit,\n    onCopy: () -> Unit\n) {\n'''
)
replace_once(
    'app/src/main/java/com/example/ui/screens/ProfileScreen.kt',
    '''            Text("@$username", fontSize = 11.sp, color = BlinkPink)\n\n            Spacer(modifier = Modifier.height(15.dp))\n\n            FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {\n''',
    '''            Text("@$username", fontSize = 11.sp, color = BlinkPink)\n\n            Spacer(modifier = Modifier.height(15.dp))\n\n            if (avatarUrl.isNotBlank()) {\n                AsyncImage(\n                    model = avatarUrl,\n                    contentDescription = "$fullName profile preview",\n                    contentScale = ContentScale.Crop,\n                    modifier = Modifier\n                        .size(72.dp)\n                        .clip(CircleShape)\n                        .align(Alignment.CenterHorizontally)\n                )\n                Spacer(modifier = Modifier.height(12.dp))\n            }\n\n            Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {\n                Icon(Icons.Default.Share, contentDescription = null)\n                Spacer(modifier = Modifier.width(7.dp))\n                Text("Share via…")\n            }\n\n            Spacer(modifier = Modifier.height(8.dp))\n\n            FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {\n'''
)

# Post/reel link actions now use the canonical helper and Android Toast.
replace_once(
    'app/src/main/java/com/example/ui/components/PostOptionsMenuSheet.kt',
    'import androidx.compose.ui.platform.LocalClipboardManager\n',
    'import androidx.compose.ui.platform.LocalClipboardManager\nimport androidx.compose.ui.platform.LocalContext\n'
)
replace_once(
    'app/src/main/java/com/example/ui/components/PostOptionsMenuSheet.kt',
    'import com.example.ui.theme.BlinkPurple\n',
    'import com.example.ui.theme.BlinkPurple\nimport com.example.sharing.ShareContentType\nimport com.example.sharing.ShareLinkManager\n'
)
replace_once(
    'app/src/main/java/com/example/ui/components/PostOptionsMenuSheet.kt',
    '''    val clipboardManager: ClipboardManager =\n        LocalClipboardManager.current\n\n''',
    '''    val clipboardManager: ClipboardManager =\n        LocalClipboardManager.current\n    val context = LocalContext.current\n\n'''
)
path = 'app/src/main/java/com/example/ui/components/PostOptionsMenuSheet.kt'
text = read(path)
old = '''clipboardManager\n                                .setText(\n                                    AnnotatedString(\n                                        "https://blink.campus/post/${post.id}"\n                                    )\n                                )'''
count = text.count(old)
if count < 2:
    raise SystemExit(f'Expected two hardcoded post-link blocks, found {count}')
text = text.replace(
    old,
    '''ShareLinkManager.copyLink(\n                                context = context,\n                                type = if (post.isReel) ShareContentType.REEL else ShareContentType.POST,\n                                id = post.id,\n                                toastMessage = if (post.isReel) "Reel link copied" else "Post link copied"\n                            )'''
)
write(path, text)

print('Share/deep-link/time patch applied successfully.')

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")

def replace_once(rel, old, new, label):
    text = read(rel)
    if new and new in text:
        print(f"[ok] {label}: already applied")
        return
    if old not in text:
        raise RuntimeError(f"{label}: expected source block not found in {rel}")
    write(rel, text.replace(old, new, 1))
    print(f"[patched] {label}")

def replace_simple(rel, old, new, label, count=1):
    text = read(rel)
    if old not in text:
        if new in text:
            print(f"[ok] {label}: already applied")
            return
        raise RuntimeError(f"{label}: expected text not found in {rel}")
    write(rel, text.replace(old, new, count))
    print(f"[patched] {label}")

# ---------------------------------------------------------------------
# 1) Post delete belongs only in the creator's three-dot action sheet.
# ---------------------------------------------------------------------
replace_once(
    "app/src/main/java/com/example/ui/components/PostCard.kt",
'''                // Delete is never exposed on somebody else's post.
                if (isAuthor) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete your post",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
''',
"",
"remove inline post delete button"
)

replace_simple(
    "app/src/main/java/com/example/ui/components/PostOptionsMenuSheet.kt",
    '"Delete this post?"',
    '"Are you sure you want to delete this post?"',
    "make post delete confirmation explicit"
)

# ---------------------------------------------------------------------
# 2) Make the inbox total clearly mean people/conversations.
# ---------------------------------------------------------------------
replace_simple(
    "app/src/main/java/com/example/ui/screens/MessagesScreen.kt",
    '"$totalChats chats"',
    '"$totalChats people"',
    "show total people in inbox header"
)
replace_simple(
    "app/src/main/java/com/example/ui/screens/MessagesScreen.kt",
    'label = "Chats"',
    'label = "People"',
    "label inbox total as people"
)

# ---------------------------------------------------------------------
# 3) Preserve message history across refresh/realtime and local sends.
# ---------------------------------------------------------------------
vm = "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"

persist_block = '''    private fun persistConversations() {
        val snapshot = _uiState.value.conversations
        viewModelScope.launch(Dispatchers.IO) {
            cacheWriteMutex.withLock {
                runCatching { offlineContentStore.replaceConversations(snapshot, _uiState.value.myProfile.username) }
                    .onFailure { Log.w(TAG, "Unable to persist conversations", it) }
            }
        }
    }
'''

merge_helper = persist_block + '''
    /**
     * Server conversation summaries intentionally do not contain message pages.
     * Never replace the local list outright: doing so erases visible chat history
     * and optimistic/offline messages whenever a realtime summary event arrives.
     */
    private fun mergeConversationSummaries(
        summaries: List<ChatConversation>,
        local: List<ChatConversation>
    ): List<ChatConversation> {
        val server = summaries
            .distinctBy { it.id.ifBlank { it.partnerUsername.lowercase() } }
            .map { summary ->
                val cached = local.firstOrNull {
                    it.id == summary.id || it.partnerUsername.equals(summary.partnerUsername, true)
                }
                summary.copy(messages = cached?.messages?.toMutableList() ?: mutableListOf())
            }

        val localOnly = local.filter { cached ->
            server.none {
                it.id == cached.id || it.partnerUsername.equals(cached.partnerUsername, true)
            }
        }

        // Local-only entries are pending/new chats and should stay visible at the top
        // until Supabase returns their real conversation id.
        return localOnly + server
    }
'''
replace_once(vm, persist_block, merge_helper, "add safe conversation summary merger")

replace_once(
    vm,
'''                    val conversationsResult = conversationsRequest.await()
                    val conversationSummaries = conversationsResult.getOrDefault(before.conversations)
                    val conversations = conversationSummaries.map { summary ->
                        val cached = before.conversations.firstOrNull {
                            it.id == summary.id || it.partnerUsername.equals(summary.partnerUsername, true)
                        }
                        summary.copy(messages = cached?.messages ?: mutableListOf())
                    }
''',
'''                    val conversationsResult = conversationsRequest.await()
                    val conversationSummaries = conversationsResult.getOrDefault(before.conversations)
                    val conversations = mergeConversationSummaries(
                        summaries = conversationSummaries,
                        local = before.conversations
                    )
''',
"preserve history during full Supabase refresh"
)

replace_once(
    vm,
'''                    val merged = if (older) page + conversation.messages else page + conversation.messages.filter {
                        it.status != MessageStatus.SENT || it.id.startsWith("temp_")
                    }
                    conversation.copy(messages = merged.distinctBy { it.id }.sortedBy { it.rawTimestamp }.toMutableList())
''',
'''                    // A just-sent message can briefly be absent from the first server page.
                    // Merge instead of replacing so entering the chat never makes it disappear.
                    val merged = page + conversation.messages
                    conversation.copy(
                        messages = merged
                            .distinctBy { it.id }
                            .sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                            .toMutableList()
                    )
''',
"merge message pages without dropping sent messages"
)

replace_simple(
    vm,
    'else conversations.add(0, ChatConversation("conv_$partnerUsername", partnerUsername,',
    'else conversations.add(0, ChatConversation("local_${UUID.randomUUID()}", partnerUsername,',
    "use local-only id for unsynced outgoing conversation"
)

replace_once(
    vm,
'''    private suspend fun reconcileConversationSummary(partnerUsername: String) {
        val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
            .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            ?: return
        withContext(Dispatchers.Main) {
            val latest = _uiState.value
            val index = latest.conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
            if (index < 0) return@withContext
            val local = latest.conversations[index]
            val merged = server.copy(messages = local.messages)
            val conversations = latest.conversations.toMutableList().apply { this[index] = merged }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()
        }
    }
''',
'''    private suspend fun reconcileConversationSummary(partnerUsername: String) {
        val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
            .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            ?: return
        withContext(Dispatchers.Main) {
            val latest = _uiState.value
            val index = latest.conversations.indexOfFirst {
                it.partnerUsername.equals(partnerUsername, true)
            }
            val conversations = latest.conversations.toMutableList()
            if (index >= 0) {
                val local = conversations[index]
                conversations[index] = server.copy(messages = local.messages.toMutableList())
            } else {
                conversations.add(0, server)
            }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()
        }
    }
''',
"upgrade pending chats to real server summaries"
)

replace_once(
    vm,
'''            is RealtimeEvent.ConversationEvent -> viewModelScope.launch { _uiState.value = _uiState.value.copy(conversations = chatRepository.fetchConversations()) }
''',
'''            is RealtimeEvent.ConversationEvent -> viewModelScope.launch {
                val latest = _uiState.value
                runCatching { chatRepository.fetchConversations() }
                    .onSuccess { summaries ->
                        _uiState.value = latest.copy(
                            conversations = mergeConversationSummaries(
                                summaries = summaries,
                                local = latest.conversations
                            )
                        )
                        persistConversations()
                    }
                    .onFailure { Log.w(TAG, "Conversation summary refresh failed", it) }
            }
''',
"preserve chat history on realtime conversation events"
)

# ---------------------------------------------------------------------
# 4) Feed chrome: staged collapse, 0.8s idle reveal, refresh feedback,
#    and a FAB that sits above bottom nav and fades during upward scroll.
# ---------------------------------------------------------------------
feed = "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
text = read(feed)

animation_imports = '''import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
'''
if "import androidx.compose.animation.AnimatedVisibility" not in text:
    text = text.replace("package com.example.ui.screens\n\n", "package com.example.ui.screens\n\n" + animation_imports, 1)

runtime_imports = '''import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
'''
if "import androidx.compose.runtime.mutableIntStateOf" not in text:
    text = text.replace("import androidx.compose.runtime.rememberUpdatedState\n", "import androidx.compose.runtime.rememberUpdatedState\n" + runtime_imports, 1)

if "import kotlinx.coroutines.delay" not in text:
    text = text.replace("import com.example.ui.theme.BlinkPink\n", "import com.example.ui.theme.BlinkPink\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.collectLatest\n", 1)

write(feed, text)

replace_once(
    feed,
'''    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }

    val nestedScrollConnection = remember(selectedTopTab) {
''',
'''    val postIds = remember(posts) { posts.mapTo(linkedSetOf()) { it.id } }
    // 0 = full header + tabs, 1 = compact utility header, 2 = hidden while scrolling.
    var chromeStage by remember { mutableIntStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(800)
                if (!listState.isScrollInProgress) {
                    // After 0.8s idle, restore the compact menu/home/notification/profile row.
                    if (chromeStage == 2) chromeStage = 1
                    fabVisible = true
                }
            }
        }
    }

    val nestedScrollConnection = remember(selectedTopTab) {
''',
"add staged feed chrome state and idle reveal"
)

replace_once(
    feed,
'''        object : NestedScrollConnection {
            private var lastVisible = true
            private var accumulatedScroll = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

                // Do not animate the bottom bar for every tiny finger movement. Accumulate
                // intentional movement and only change visibility after a meaningful swipe.
                if ((accumulatedScroll > 0f && available.y < 0f) ||
                    (accumulatedScroll < 0f && available.y > 0f)
                ) {
                    accumulatedScroll = 0f
                }

                accumulatedScroll = (accumulatedScroll + available.y).coerceIn(-160f, 160f)
                val shouldBeVisible = when {
                    accumulatedScroll <= -56f -> false
                    accumulatedScroll >= 56f -> true
                    else -> null
                }

                if (shouldBeVisible != null) {
                    accumulatedScroll = 0f
                    if (shouldBeVisible != lastVisible) {
                        lastVisible = shouldBeVisible
                        bottomBarVisibility(shouldBeVisible)
                    }
                }
                return Offset.Zero
            }
        }
''',
'''        object : NestedScrollConnection {
            private var lastVisible = true
            private var accumulatedScroll = 0f
            private var chromeScroll = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero

                if (available.y < 0f) {
                    // Upward swipe: fold Home/Reel/Connect/Game first, then fade utilities.
                    chromeScroll += -available.y
                    fabVisible = false
                    when {
                        chromeStage == 0 && chromeScroll >= 44f -> {
                            chromeStage = 1
                            chromeScroll = 0f
                        }
                        chromeStage == 1 && chromeScroll >= 72f -> {
                            chromeStage = 2
                            chromeScroll = 0f
                        }
                    }
                } else {
                    // Downward navigation restores the full chrome immediately.
                    chromeScroll = 0f
                    chromeStage = 0
                    fabVisible = true
                }

                // Do not animate the bottom bar for every tiny finger movement. Accumulate
                // intentional movement and only change visibility after a meaningful swipe.
                if ((accumulatedScroll > 0f && available.y < 0f) ||
                    (accumulatedScroll < 0f && available.y > 0f)
                ) {
                    accumulatedScroll = 0f
                }

                accumulatedScroll = (accumulatedScroll + available.y).coerceIn(-160f, 160f)
                val shouldBeVisible = when {
                    accumulatedScroll <= -56f -> false
                    accumulatedScroll >= 56f -> true
                    else -> null
                }

                if (shouldBeVisible != null) {
                    accumulatedScroll = 0f
                    if (shouldBeVisible != lastVisible) {
                        lastVisible = shouldBeVisible
                        bottomBarVisibility(shouldBeVisible)
                    }
                }
                return Offset.Zero
            }
        }
''',
"stage feed navigation while scrolling"
)

replace_once(
    feed,
'''    LaunchedEffect(selectedTopTab) {
        bottomBarVisibility(true)
    }
''',
'''    LaunchedEffect(selectedTopTab) {
        bottomBarVisibility(true)
        chromeStage = 0
        fabVisible = true
    }
''',
"reset feed chrome on tab change"
)

replace_once(
    feed,
'''                        item {
                            HomeHeader(
                                userAvatar = userAvatar,
                                onMenuClick = onOpenMenu,
                                onNotificationClick = onOpenActivity,
                                onProfileClick = { onProfileClick("you") }
                            )
                        }

                    item {
                        TopNavigation(
                            selected = selectedTopTab,
                            onHome = { navigate(0) },
                            onReel = { navigate(1) },
                            onConnect = { navigate(2) },
                            onGame = { navigate(3) }
                        )
                    }
''',
'''                        stickyHeader(key = "home_feed_chrome") {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                tonalElevation = if (chromeStage < 2) 2.dp else 0.dp
                            ) {
                                Column {
                                    AnimatedVisibility(
                                        visible = chromeStage < 2,
                                        enter = fadeIn(tween(180)) + expandVertically(),
                                        exit = fadeOut(tween(140)) + shrinkVertically()
                                    ) {
                                        HomeHeader(
                                            userAvatar = userAvatar,
                                            onMenuClick = onOpenMenu,
                                            onNotificationClick = onOpenActivity,
                                            onProfileClick = { onProfileClick("you") }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = chromeStage == 0,
                                        enter = fadeIn(tween(180)) + expandVertically(),
                                        exit = fadeOut(tween(140)) + shrinkVertically()
                                    ) {
                                        TopNavigation(
                                            selected = selectedTopTab,
                                            onHome = { navigate(0) },
                                            onReel = { navigate(1) },
                                            onConnect = { navigate(2) },
                                            onGame = { navigate(3) }
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isRefreshing,
                                        enter = fadeIn(tween(150)) + expandVertically(),
                                        exit = fadeOut(tween(150)) + shrinkVertically()
                                    ) {
                                        FeedRefreshingBanner()
                                    }
                                }
                            }
                        }
''',
"make home chrome sticky and progressively collapsible"
)

replace_once(
    feed,
'''                FloatingActionButton(
                    onClick = onOpenCreatePost,
                    containerColor = if (isDark) BlinkCream else BlinkBlack,
                    contentColor = if (isDark) BlinkBlack else BlinkCream,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 16.dp)
                        .testTag("create_post_fab")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create post",
                        modifier = Modifier.size(27.dp)
                    )
                }
''',
'''                AnimatedVisibility(
                    visible = fabVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 92.dp)
                ) {
                    FloatingActionButton(
                        onClick = onOpenCreatePost,
                        containerColor = if (isDark) BlinkCream else BlinkBlack,
                        contentColor = if (isDark) BlinkBlack else BlinkCream,
                        modifier = Modifier.testTag("create_post_fab")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create post",
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
''',
"move create-post FAB above bottom nav and fade it on scroll"
)

banner_marker = '''@Composable
private fun FeedConnectionNotice(onRetry: () -> Unit) {
'''
banner = '''@Composable
private fun FeedRefreshingBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Refreshing feed…",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

''' + banner_marker

replace_once(
    feed,
    banner_marker,
    banner,
    "add persistent feed refreshing feedback"
)

print("All requested source patches applied.")

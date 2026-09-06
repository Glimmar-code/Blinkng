from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_exact_count(text: str, old: str, new: str, expected: int, label: str) -> str:
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} matches, found {count}")
    return text.replace(old, new)


def write_changed(path: Path, text: str) -> None:
    original = path.read_text()
    if original == text:
        raise SystemExit(f"{path}: patch produced no changes")
    path.write_text(text)
    print(f"patched {path.relative_to(ROOT)}")


# ---------------------------------------------------------------------------
# ViewModel: expose a real conversation-list loading state, refresh an empty
# message list whenever Messages is opened online, and always clear the flag.
# ---------------------------------------------------------------------------
vm_path = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
vm = vm_path.read_text()

vm = replace_once(
    vm,
    '''    val conversations: List<ChatConversation> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
''',
    '''    val conversations: List<ChatConversation> = emptyList(),
    val isConversationsLoading: Boolean = false,
    val activities: List<ActivityItem> = emptyList(),
''',
    "conversation loading state"
)

vm = replace_once(
    vm,
    '''                _uiState.value = before.copy(
                    isFeedLoading = !hadFeed && !showRefreshIndicator,
                    isRefreshingContent = showRefreshIndicator,
                    isSyncingContent = true,
                    feedErrorMessage = null
                )
''',
    '''                _uiState.value = before.copy(
                    isFeedLoading = !hadFeed && !showRefreshIndicator,
                    isRefreshingContent = showRefreshIndicator,
                    isSyncingContent = true,
                    isConversationsLoading = before.conversations.isEmpty(),
                    feedErrorMessage = null
                )
''',
    "start conversation loading"
)

vm = replace_once(
    vm,
    '''                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        isRefreshingContent = false,
                        isSyncingContent = false
                    )
''',
    '''                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        isRefreshingContent = false,
                        isSyncingContent = false,
                        isConversationsLoading = false
                    )
''',
    "finish conversation loading"
)

vm = replace_once(
    vm,
    '''            isConversationFullScreen = false
        )
        persistUiPreferences()
    }
    fun setTab(tab: MainTab) = selectTab(tab)
''',
    '''            isConversationFullScreen = false,
            isConversationsLoading = if (
                tab == MainTab.MESSAGES &&
                _uiState.value.conversations.isEmpty() &&
                _uiState.value.isOnline
            ) true else _uiState.value.isConversationsLoading
        )
        persistUiPreferences()
        if (tab == MainTab.MESSAGES && _uiState.value.conversations.isEmpty() && _uiState.value.isOnline) {
            fetchSupabaseData()
        }
    }
    fun setTab(tab: MainTab) = selectTab(tab)
''',
    "refresh empty messages tab"
)

write_changed(vm_path, vm)


# ---------------------------------------------------------------------------
# Premium messages UI: show skeleton cards while the server/cache is resolving,
# and always sort conversations by latest server/realtime activity.
# ---------------------------------------------------------------------------
ui_path = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
ui = ui_path.read_text()

ui = replace_once(
    ui,
    '''    interactionActions: ChatInteractionActions = ChatInteractionActions(),
    isConnected: Boolean = true,
    isDark: Boolean = false
) {
''',
    '''    interactionActions: ChatInteractionActions = ChatInteractionActions(),
    isConnected: Boolean = true,
    isLoading: Boolean = false,
    isDark: Boolean = false
) {
''',
    "premium messages loading parameter"
)

ui = replace_exact_count(
    ui,
    '''                            onOpenAppearance = { showAppearanceSheet = true },
                            isConnected = isConnected
''',
    '''                            onOpenAppearance = { showAppearanceSheet = true },
                            isConnected = isConnected,
                            isLoading = isLoading
''',
    1,
    "master-detail background loading"
)
ui = replace_exact_count(
    ui,
    '''                    onOpenAppearance = { showAppearanceSheet = true },
                    isConnected = isConnected
''',
    '''                    onOpenAppearance = { showAppearanceSheet = true },
                    isConnected = isConnected,
                    isLoading = isLoading
''',
    1,
    "messages home loading"
)

ui = replace_once(
    ui,
    '''    onAddStoryClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    isConnected: Boolean
) {
    var query by remember { mutableStateOf("") }
    val filteredConversations = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter {
''',
    '''    onAddStoryClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    isConnected: Boolean,
    isLoading: Boolean
) {
    var query by remember { mutableStateOf("") }
    val sortedConversations = remember(conversations) {
        conversations.sortedWith(
            compareByDescending<ChatConversation> { it.lastMessageRawTime }
                .thenByDescending { it.id }
        )
    }
    val filteredConversations = remember(sortedConversations, query) {
        if (query.isBlank()) sortedConversations
        else sortedConversations.filter {
''',
    "sort conversation home list"
)

ui = replace_once(
    ui,
    '''                ConversationList(
                    conversations = filteredConversations,
                    palette = palette,
                    onOpenConversation = onOpenConversation,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.fillMaxSize()
                )
''',
    '''                ConversationList(
                    conversations = filteredConversations,
                    palette = palette,
                    onOpenConversation = onOpenConversation,
                    onProfileClick = onProfileClick,
                    isLoading = isLoading && conversations.isEmpty() && query.isBlank(),
                    modifier = Modifier.fillMaxSize()
                )
''',
    "conversation list loading handoff"
)

skeleton = '''@Composable
private fun PremiumConversationListSkeleton(
    palette: MessagePalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(7) {
            Surface(
                color = palette.glassElevated.copy(alpha = if (palette.isLight) .70f else .48f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, palette.border.copy(alpha = .70f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(palette.glass.copy(alpha = .88f))
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth(.46f).height(13.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.glass.copy(alpha = .90f))
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier.fillMaxWidth(.78f).height(10.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.glass.copy(alpha = .66f))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(38.dp).height(9.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.glass.copy(alpha = .62f))
                    )
                }
            }
        }
    }
}

'''
anchor = '''@Composable
private fun ConversationList(
'''
if anchor not in ui:
    raise SystemExit("conversation skeleton anchor not found")
if "private fun PremiumConversationListSkeleton(" in ui:
    raise SystemExit("conversation skeleton already exists")
ui = ui.replace(anchor, skeleton + anchor, 1)

ui = replace_once(
    ui,
    '''private fun ConversationList(
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conversations.isEmpty()) {
''',
    '''private fun ConversationList(
    conversations: List<ChatConversation>,
    palette: MessagePalette,
    onOpenConversation: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        PremiumConversationListSkeleton(palette = palette, modifier = modifier)
        return
    }
    if (conversations.isEmpty()) {
''',
    "render skeleton before empty state"
)

write_changed(ui_path, ui)


# ---------------------------------------------------------------------------
# MainActivity: feed the real ViewModel loading state to the messages surface.
# ---------------------------------------------------------------------------
main_path = ROOT / "app/src/main/java/com/example/MainActivity.kt"
main = main_path.read_text()
main = replace_once(
    main,
    '''                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline
                    )
''',
    '''                        isDark = uiState.isDarkMode,
                        isConnected = uiState.isOnline,
                        isLoading = uiState.isConversationsLoading
                    )
''',
    "main messages loading wiring"
)
write_changed(main_path, main)

print("messages fresh-install/realtime patch applied")

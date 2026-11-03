from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def write_changed(path: Path, text: str) -> None:
    original = path.read_text()
    if original == text:
        raise SystemExit(f"{path}: patch produced no changes")
    path.write_text(text)
    print(f"patched {path.relative_to(ROOT)}")


# ---------------------------------------------------------------------------
# BlinkViewModel: never roll the whole chat list backwards and never merge a
# realtime summary into a stale pre-request state snapshot.
# ---------------------------------------------------------------------------
vm_path = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
vm = vm_path.read_text()

old_delete_for_me = '''    fun deleteChatMessageForMe(partnerUsername: String, message: ChatMessage) {
        val state = _uiState.value
        val before = state.conversations
        val updated = before.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(messages = conversation.messages.filterNot { it.id == message.id }.toMutableList())
        }
        _uiState.value = state.copy(conversations = updated)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            if (message.id.startsWith("temp_")) {
                offlineContentStore.deleteOutbox(message.id)
                pendingReplyTargets.remove(message.id)
                return@launch
            }
            if (!chatRepository.hideMessageForMe(message.id)) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(conversations = before)
                    persistConversations()
                    showToast("Couldn't delete message for you.")
                }
            }
        }
    }
'''
new_delete_for_me = '''    fun deleteChatMessageForMe(partnerUsername: String, message: ChatMessage) {
        val state = _uiState.value
        val updated = state.conversations.map { conversation ->
            if (!conversation.partnerUsername.equals(partnerUsername, true)) conversation
            else conversation.copy(messages = conversation.messages.filterNot { it.id == message.id }.toMutableList())
        }
        _uiState.value = state.copy(conversations = updated)
        persistConversations()
        viewModelScope.launch(Dispatchers.IO) {
            if (message.id.startsWith("temp_")) {
                offlineContentStore.deleteOutbox(message.id)
                pendingReplyTargets.remove(message.id)
                return@launch
            }
            if (!chatRepository.hideMessageForMe(message.id)) {
                withContext(Dispatchers.Main) {
                    // Restore only the affected message into the newest state. Never restore
                    // an old whole-list snapshot because messages may have arrived meanwhile.
                    val latest = _uiState.value
                    val restored = latest.conversations.map { conversation ->
                        if (!conversation.partnerUsername.equals(partnerUsername, true) ||
                            conversation.messages.any { it.id == message.id }
                        ) {
                            conversation
                        } else {
                            val messages = (conversation.messages + message)
                                .distinctBy { it.id }
                                .sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }
                                .toMutableList()
                            conversation.copy(messages = messages)
                        }
                    }
                    _uiState.value = latest.copy(conversations = restored)
                    persistConversations()
                    showToast("Couldn't delete message for you.")
                }
            }
        }
    }
'''
vm = replace_once(vm, old_delete_for_me, new_delete_for_me, "targeted delete-for-me rollback")

old_clear = '''    fun clearConversationForMe(conversation: ChatConversation) {
        val state = _uiState.value
        val before = state.conversations
        _uiState.value = state.copy(
            conversations = before.filterNot { it.id == conversation.id },
            activeConversationPartner = if (state.activeConversationPartner.equals(conversation.partnerUsername, true)) null else state.activeConversationPartner,
            isConversationFullScreen = false
        )
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.clearConversationForMe(conversation.id)) {
                _uiState.value = _uiState.value.copy(conversations = before)
                persistConversations()
                showToast("Couldn't delete chat.")
            }
        }
    }
'''
new_clear = '''    fun clearConversationForMe(conversation: ChatConversation) {
        val state = _uiState.value
        val originalIndex = state.conversations.indexOfFirst { it.id == conversation.id }
        _uiState.value = state.copy(
            conversations = state.conversations.filterNot { it.id == conversation.id },
            activeConversationPartner = if (state.activeConversationPartner.equals(conversation.partnerUsername, true)) null else state.activeConversationPartner,
            isConversationFullScreen = false
        )
        persistConversations()
        if (conversation.id.startsWith("local_")) return
        viewModelScope.launch {
            if (!chatRepository.clearConversationForMe(conversation.id)) {
                // Roll back only this conversation into the newest state. This preserves any
                // conversations/messages that appeared while the server request was running.
                val latest = _uiState.value
                if (latest.conversations.none { it.id == conversation.id }) {
                    val restored = latest.conversations.toMutableList()
                    restored.add(originalIndex.coerceIn(0, restored.size), conversation)
                    _uiState.value = latest.copy(conversations = restored)
                    persistConversations()
                }
                showToast("Couldn't clear chat.")
            }
        }
    }
'''
vm = replace_once(vm, old_clear, new_clear, "targeted clear-chat rollback")

old_realtime_summary = '''            is RealtimeEvent.ConversationEvent -> viewModelScope.launch {
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
'''
new_realtime_summary = '''            is RealtimeEvent.ConversationEvent -> viewModelScope.launch {
                runCatching { chatRepository.fetchConversations() }
                    .onSuccess { summaries ->
                        // Read state after the network call completes. A message may have been
                        // sent/received while this request was in flight, so a pre-request
                        // snapshot must never be written back over newer chat state.
                        val latest = _uiState.value
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
'''
vm = replace_once(vm, old_realtime_summary, new_realtime_summary, "fresh-state realtime summary merge")

old_restore_state = '''        _uiState.value = _uiState.value.copy(
            myProfile = UserProfile(
                fullName = savedName,
                username = savedUsername,
                email = ContactField(savedEmail, true),
                faculty = savedFaculty,
                university = savedUniversity,
                avatarUrl = savedAvatar,
                coverPhotoUrl = savedCover,
                verificationBadge = badge,
                isSellerActive = prefs.getBoolean(KEY_SELLER_ACTIVE, false)
            ),
            destination = AppDestination.MAIN
        )
'''
new_restore_state = '''        val previousUsername = _uiState.value.myProfile.username
        val accountChanged = previousUsername.isNotBlank() && !previousUsername.equals(savedUsername, true)
        offlineContentStore.setActiveOwner(savedUsername)
        _uiState.value = _uiState.value.copy(
            myProfile = UserProfile(
                fullName = savedName,
                username = savedUsername,
                email = ContactField(savedEmail, true),
                faculty = savedFaculty,
                university = savedUniversity,
                avatarUrl = savedAvatar,
                coverPhotoUrl = savedCover,
                verificationBadge = badge,
                isSellerActive = prefs.getBoolean(KEY_SELLER_ACTIVE, false)
            ),
            // Never carry account A's in-memory chats into account B. The account-scoped
            // Room/snapshot cache below restores B's own conversations immediately.
            conversations = if (accountChanged) emptyList() else _uiState.value.conversations,
            destination = AppDestination.MAIN
        )
'''
vm = replace_once(vm, old_restore_state, new_restore_state, "account-aware local session restore")

old_save_local = '''    private fun saveLocalProfile(profile: UserProfile) {
        prefs.edit()
'''
new_save_local = '''    private fun saveLocalProfile(profile: UserProfile) {
        offlineContentStore.setActiveOwner(profile.username)
        prefs.edit()
'''
vm = replace_once(vm, old_save_local, new_save_local, "activate account chat cache on profile save")
write_changed(vm_path, vm)


# ---------------------------------------------------------------------------
# PremiumMessagesScreen: keep the last real conversation while transient state
# refreshes happen, reset hidden filters when the screen is recreated, and ask
# before clearing the whole chat.
# ---------------------------------------------------------------------------
ui_path = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"
ui = ui_path.read_text()

old_active = '''    val activeConversation = activePartner?.let { partner ->
        conversations.firstOrNull { it.partnerUsername.equals(partner, ignoreCase = true) }
            ?: ChatConversation(
                id = "local_$partner",
                partnerUsername = partner,
                partnerName = partner.replace("_", " ").replace(".", " "),
                partnerAvatar = ""
            )
    }
'''
new_active = '''    val liveActiveConversation = activePartner?.let { partner ->
        conversations.firstOrNull { it.partnerUsername.equals(partner, ignoreCase = true) }
    }
    var retainedActiveConversation by remember(activePartner) {
        mutableStateOf<ChatConversation?>(liveActiveConversation)
    }
    LaunchedEffect(liveActiveConversation) {
        if (liveActiveConversation != null) retainedActiveConversation = liveActiveConversation
    }
    val activeConversation = activePartner?.let { partner ->
        liveActiveConversation
            ?: retainedActiveConversation
            ?: ChatConversation(
                id = "local_$partner",
                partnerUsername = partner,
                partnerName = partner.replace("_", " ").replace(".", " "),
                partnerAvatar = ""
            )
    }
'''
ui = replace_once(ui, old_active, new_active, "retain active conversation")
ui = replace_once(
    ui,
    '    var query by rememberSaveable { mutableStateOf("") }\n',
    '    var query by remember { mutableStateOf("") }\n',
    "reset conversation-list search when screen is recreated"
)
ui = replace_once(
    ui,
    '    var searchQuery by rememberSaveable(conversation.partnerUsername) { mutableStateOf("") }\n',
    '    var searchQuery by remember(conversation.partnerUsername) { mutableStateOf("") }\n',
    "reset chat search when detail is recreated"
)
ui = replace_once(
    ui,
    '    var showOverflow by remember(conversation.partnerUsername) { mutableStateOf(false) }\n',
    '    var showOverflow by remember(conversation.partnerUsername) { mutableStateOf(false) }\n    var confirmClearChat by remember(conversation.partnerUsername) { mutableStateOf(false) }\n',
    "clear-chat confirmation state"
)
old_clear_action = '''            onDelete = {
                showOverflow = false
                interactionActions.onClearConversation(conversation)
            },
'''
new_clear_action = '''            onDelete = {
                showOverflow = false
                confirmClearChat = true
            },
'''
ui = replace_once(ui, old_clear_action, new_clear_action, "route clear chat through confirmation")

attachment_anchor = '''    if (showAttachmentSheet) {
        AttachmentSheet(
'''
confirm_block = '''    if (confirmClearChat) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("Clear this chat?", color = palette.textPrimary) },
            text = {
                Text(
                    "This hides the current history only for your account. New messages will make the conversation appear again.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmClearChat = false
                        interactionActions.onClearConversation(conversation)
                    }
                ) { Text("Clear chat", color = palette.danger) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClearChat = false }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
'''
ui = replace_once(ui, attachment_anchor, confirm_block, "clear-chat confirmation dialog")
write_changed(ui_path, ui)


# ---------------------------------------------------------------------------
# SupabaseRealtimeManager: queue all realtime events in order instead of using
# lossy tryEmit calls with a small buffer.
# ---------------------------------------------------------------------------
rt_path = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt"
rt = rt_path.read_text()
rt = replace_once(
    rt,
    'import kotlinx.coroutines.Job\n',
    'import kotlinx.coroutines.Job\nimport kotlinx.coroutines.channels.Channel\n',
    "realtime channel import"
)
old_flow = '''    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
'''
new_flow = '''    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 128)
    private val eventQueue = Channel<RealtimeEvent>(Channel.UNLIMITED)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    init {
        // WebSocket callbacks must never lose a chat event just because collectors are busy.
        // A single queue consumer also preserves server event ordering.
        scope.launch {
            for (event in eventQueue) _events.emit(event)
        }
    }

    private fun publishEvent(event: RealtimeEvent) {
        if (eventQueue.trySend(event).isFailure) {
            Log.w(TAG, "Realtime event queue rejected ${event::class.simpleName}")
        }
    }
'''
rt = replace_once(rt, old_flow, new_flow, "lossless realtime event queue")
if '_events.tryEmit(' not in rt:
    raise SystemExit("realtime publish replacement: no tryEmit calls found")
rt = rt.replace('_events.tryEmit(', 'publishEvent(')
write_changed(rt_path, rt)


# ---------------------------------------------------------------------------
# Room cache: tag all conversation/message/outbox rows with their account.
# IDs are globally unique already, so adding owner_username preserves existing
# primary keys while preventing another signed-in account from observing rows.
# ---------------------------------------------------------------------------
cache_path = ROOT / "app/src/main/java/com/example/data/local/CachedContent.kt"
cache = cache_path.read_text()

cache = replace_once(
    cache,
'''@Entity(
    tableName = "cached_conversations",
    indices = [Index(value = ["partner_username"]), Index(value = ["display_order"])]
)
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "partner_username") val partnerUsername: String,
''',
'''@Entity(
    tableName = "cached_conversations",
    indices = [
        Index(value = ["owner_username", "partner_username"]),
        Index(value = ["owner_username", "display_order"])
    ]
)
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "partner_username") val partnerUsername: String,
''',
    "account-scoped conversation entity"
)
cache = replace_once(
    cache,
'''@Entity(
    tableName = "cached_messages",
    indices = [Index(value = ["conversation_id", "display_order"]), Index(value = ["raw_timestamp"])]
)
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
''',
'''@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["owner_username", "conversation_id", "display_order"]),
        Index(value = ["owner_username", "raw_timestamp"])
    ]
)
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
''',
    "account-scoped message entity"
)
cache = replace_once(
    cache,
'''@Entity(
    tableName = "message_outbox",
    indices = [Index(value = ["next_retry_at"]), Index(value = ["receiver_username"])]
)
data class MessageOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "receiver_username") val receiverUsername: String,
''',
'''@Entity(
    tableName = "message_outbox",
    indices = [
        Index(value = ["owner_username", "next_retry_at"]),
        Index(value = ["owner_username", "receiver_username"])
    ]
)
data class MessageOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "receiver_username") val receiverUsername: String,
''',
    "account-scoped outbox entity"
)

cache = replace_once(
    cache,
'''    @Query("SELECT * FROM cached_conversations ORDER BY display_order ASC")
    fun observeConversations(): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_messages ORDER BY conversation_id ASC, display_order ASC")
    fun observeMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT COUNT(*) FROM message_outbox WHERE attempt_count < 5")
    fun observePendingOutboxCount(): Flow<Int>

    @Query("SELECT * FROM message_outbox WHERE attempt_count < 5 AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")
    suspend fun pendingOutbox(now: Long, limit: Int): List<MessageOutboxEntity>
''',
'''    @Query("SELECT * FROM cached_conversations WHERE owner_username = :ownerUsername ORDER BY display_order ASC")
    fun observeConversations(ownerUsername: String): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_messages WHERE owner_username = :ownerUsername ORDER BY conversation_id ASC, display_order ASC")
    fun observeMessages(ownerUsername: String): Flow<List<CachedMessageEntity>>

    @Query("SELECT COUNT(*) FROM message_outbox WHERE owner_username = :ownerUsername AND attempt_count < 5")
    fun observePendingOutboxCount(ownerUsername: String): Flow<Int>

    @Query("SELECT * FROM message_outbox WHERE owner_username = :ownerUsername AND attempt_count < 5 AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")
    suspend fun pendingOutbox(ownerUsername: String, now: Long, limit: Int): List<MessageOutboxEntity>

    @Query("UPDATE cached_conversations SET owner_username = :ownerUsername WHERE owner_username = ''")
    suspend fun claimLegacyConversations(ownerUsername: String)

    @Query("UPDATE cached_messages SET owner_username = :ownerUsername WHERE owner_username = ''")
    suspend fun claimLegacyMessages(ownerUsername: String)

    @Query("UPDATE message_outbox SET owner_username = :ownerUsername WHERE owner_username = ''")
    suspend fun claimLegacyOutbox(ownerUsername: String)

    @Transaction
    suspend fun claimLegacyChatRows(ownerUsername: String) {
        if (ownerUsername.isBlank()) return
        claimLegacyConversations(ownerUsername)
        claimLegacyMessages(ownerUsername)
        claimLegacyOutbox(ownerUsername)
    }
''',
    "account-filtered DAO queries"
)

cache = replace_once(cache, '    version = 2,\n', '    version = 3,\n', "Room database version 3")

migration_anchor = '''        fun getInstance(context: Context): BlinkDatabase =
            instance ?: synchronized(this) {
'''
migration_insert = '''        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_conversations ADD COLUMN owner_username TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_messages ADD COLUMN owner_username TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE message_outbox ADD COLUMN owner_username TEXT NOT NULL DEFAULT ''")

                db.execSQL("DROP INDEX IF EXISTS index_cached_conversations_partner_username")
                db.execSQL("DROP INDEX IF EXISTS index_cached_conversations_display_order")
                db.execSQL("DROP INDEX IF EXISTS index_cached_messages_conversation_id_display_order")
                db.execSQL("DROP INDEX IF EXISTS index_cached_messages_raw_timestamp")
                db.execSQL("DROP INDEX IF EXISTS index_message_outbox_next_retry_at")
                db.execSQL("DROP INDEX IF EXISTS index_message_outbox_receiver_username")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_owner_username_partner_username ON cached_conversations(owner_username, partner_username)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_owner_username_display_order ON cached_conversations(owner_username, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_owner_username_conversation_id_display_order ON cached_messages(owner_username, conversation_id, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_owner_username_raw_timestamp ON cached_messages(owner_username, raw_timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_owner_username_next_retry_at ON message_outbox(owner_username, next_retry_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_owner_username_receiver_username ON message_outbox(owner_username, receiver_username)")
            }
        }

        fun getInstance(context: Context): BlinkDatabase =
            instance ?: synchronized(this) {
'''
cache = replace_once(cache, migration_anchor, migration_insert, "Room 2->3 migration")
cache = replace_once(
    cache,
    '                    .addMigrations(MIGRATION_1_2)\n',
    '                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)\n',
    "register Room 2->3 migration"
)
write_changed(cache_path, cache)


# ---------------------------------------------------------------------------
# OfflineContentStore: dynamically select the active account's Room rows and
# keep a separate durable app snapshot per username. The legacy snapshot/rows
# are adopted once by the account that owned the old cache.
# ---------------------------------------------------------------------------
store_path = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"
store = store_path.read_text()

store = replace_once(
    store,
'''import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
''',
'''import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
''',
    "owner-state coroutine imports"
)

old_store_fields = '''    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()
    private val codec = OfflineContentCodec()
    private val snapshotFile = File(context.noBackupFilesDir, "blink_main_snapshot.json")
    private val metadataPrefs = context.getSharedPreferences("blink_offline_cache_meta", Context.MODE_PRIVATE)

    fun cachedOwnerUsername(): String = metadataPrefs.getString("owner_username", "").orEmpty()

    private fun rememberOwner(username: String) {
        if (username.isNotBlank()) metadataPrefs.edit().putString("owner_username", username.lowercase()).apply()
    }
'''
new_store_fields = '''    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()
    private val codec = OfflineContentCodec()
    private val snapshotDirectory = context.noBackupFilesDir
    private val legacySnapshotFile = File(snapshotDirectory, "blink_main_snapshot.json")
    private val metadataPrefs = context.getSharedPreferences("blink_offline_cache_meta", Context.MODE_PRIVATE)
    private val ownerState = MutableStateFlow(
        metadataPrefs.getString("owner_username", "").orEmpty().trim().lowercase()
    )
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val legacyOwner = ownerState.value
        if (legacyOwner.isNotBlank()) {
            maintenanceScope.launch { dao.claimLegacyChatRows(legacyOwner) }
        }
    }

    fun cachedOwnerUsername(): String = ownerState.value.ifBlank {
        metadataPrefs.getString("owner_username", "").orEmpty().trim().lowercase()
    }

    fun setActiveOwner(username: String) {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return
        val previous = ownerState.value
        metadataPrefs.edit().putString("owner_username", normalized).apply()
        ownerState.value = normalized
        // Only the first known owner adopts pre-v3 unscoped rows. Switching accounts never
        // reassigns another account's cache.
        if (previous.isBlank() || previous == normalized) {
            maintenanceScope.launch { dao.claimLegacyChatRows(normalized) }
        }
    }

    private fun rememberOwner(username: String) = setActiveOwner(username)

    private fun snapshotFileFor(ownerUsername: String): File {
        val safe = ownerUsername.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        return File(snapshotDirectory, "blink_main_snapshot_$safe.json")
    }
'''
store = replace_once(store, old_store_fields, new_store_fields, "active owner and per-account snapshot fields")

old_conversations_flow = '''    val conversations: Flow<List<ChatConversation>> =
        combine(dao.observeConversations(), dao.observeMessages()) { conversations, messages ->
            val grouped = messages.groupBy { it.conversationId }
            conversations.mapNotNull { row ->
                val base = codec.decodeConversation(row.payloadJson) ?: return@mapNotNull null
                val hydratedMessages = grouped[row.id]
                    .orEmpty()
                    .sortedBy { it.displayOrder }
                    .mapNotNull { codec.decodeMessage(it.payloadJson) }
                    .toMutableList()
                base.copy(messages = hydratedMessages)
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    val pendingOutboxCount: Flow<Int> = dao.observePendingOutboxCount()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
'''
new_conversations_flow = '''    val conversations: Flow<List<ChatConversation>> = ownerState
        .flatMapLatest { owner ->
            if (owner.isBlank()) {
                flowOf(emptyList())
            } else {
                combine(dao.observeConversations(owner), dao.observeMessages(owner)) { conversations, messages ->
                    val grouped = messages.groupBy { it.conversationId }
                    conversations.mapNotNull { row ->
                        val base = codec.decodeConversation(row.payloadJson) ?: return@mapNotNull null
                        val hydratedMessages = grouped[row.id]
                            .orEmpty()
                            .sortedBy { it.displayOrder }
                            .mapNotNull { codec.decodeMessage(it.payloadJson) }
                            .toMutableList()
                        base.copy(messages = hydratedMessages)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val pendingOutboxCount: Flow<Int> = ownerState
        .flatMapLatest { owner ->
            if (owner.isBlank()) flowOf(0) else dao.observePendingOutboxCount(owner)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
'''
store = replace_once(store, old_conversations_flow, new_conversations_flow, "account-filtered conversation flows")

old_replace_conversations_head = '''    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = "") {
        rememberOwner(ownerUsername)
        val cachedAt = System.currentTimeMillis()
'''
new_replace_conversations_head = '''    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = "") {
        val owner = ownerUsername.trim().removePrefix("@").lowercase().ifBlank { cachedOwnerUsername() }
        if (owner.isBlank()) return
        rememberOwner(owner)
        val cachedAt = System.currentTimeMillis()
'''
store = replace_once(store, old_replace_conversations_head, new_replace_conversations_head, "resolve chat cache owner")
store = replace_once(
    store,
'''                CachedConversationEntity(
                    id = conversation.id,
                    partnerUsername = conversation.partnerUsername.lowercase(),
''',
'''                CachedConversationEntity(
                    id = conversation.id,
                    ownerUsername = owner,
                    partnerUsername = conversation.partnerUsername.lowercase(),
''',
    "persist conversation owner"
)
store = replace_once(
    store,
'''                    CachedMessageEntity(
                        id = stableId,
                        conversationId = conversation.id,
''',
'''                    CachedMessageEntity(
                        id = stableId,
                        ownerUsername = owner,
                        conversationId = conversation.id,
''',
    "persist message owner"
)

old_enqueue = '''    suspend fun enqueueMessage(localId: String, receiverUsername: String, content: String) {
        dao.upsertOutbox(
            MessageOutboxEntity(
                localId = localId,
                receiverUsername = receiverUsername.trim(),
                content = content,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun pendingOutbox(limit: Int = 30): List<MessageOutboxEntity> =
        dao.pendingOutbox(System.currentTimeMillis(), limit.coerceIn(1, 100))
'''
new_enqueue = '''    suspend fun enqueueMessage(localId: String, receiverUsername: String, content: String) {
        val owner = cachedOwnerUsername()
        if (owner.isBlank()) return
        dao.upsertOutbox(
            MessageOutboxEntity(
                localId = localId,
                ownerUsername = owner,
                receiverUsername = receiverUsername.trim(),
                content = content,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun pendingOutbox(limit: Int = 30): List<MessageOutboxEntity> {
        val owner = cachedOwnerUsername()
        if (owner.isBlank()) return emptyList()
        return dao.pendingOutbox(owner, System.currentTimeMillis(), limit.coerceIn(1, 100))
    }
'''
store = replace_once(store, old_enqueue, new_enqueue, "account-scoped message outbox")

old_snapshot = '''    suspend fun loadAppSnapshot(): CachedAppSnapshot? = withContext(Dispatchers.IO) {
        if (!snapshotFile.exists()) return@withContext null
        runCatching { codec.decodeAppSnapshot(snapshotFile.readText()) }
            .onFailure { Log.w(TAG, "Unable to read cached app snapshot", it) }
            .getOrNull()
    }

    suspend fun saveAppSnapshot(snapshot: CachedAppSnapshot) = withContext(Dispatchers.IO) {
        val normalized = snapshot.copy(cachedAt = System.currentTimeMillis())
        val json = codec.encodeAppSnapshot(normalized) ?: return@withContext
        rememberOwner(normalized.ownerUsername)
        val temp = File(snapshotFile.parentFile, "${snapshotFile.name}.tmp")
        runCatching {
            temp.writeText(json)
            if (!temp.renameTo(snapshotFile)) {
                snapshotFile.writeText(json)
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "Unable to save cached app snapshot", it) }
    }
'''
new_snapshot = '''    suspend fun loadAppSnapshot(): CachedAppSnapshot? = withContext(Dispatchers.IO) {
        val owner = cachedOwnerUsername()
        if (owner.isBlank()) return@withContext null
        val accountFile = snapshotFileFor(owner)
        val source = when {
            accountFile.exists() -> accountFile
            legacySnapshotFile.exists() -> legacySnapshotFile
            else -> return@withContext null
        }
        val decoded = runCatching { codec.decodeAppSnapshot(source.readText()) }
            .onFailure { Log.w(TAG, "Unable to read cached app snapshot", it) }
            .getOrNull()
            ?: return@withContext null
        if (decoded.ownerUsername.isNotBlank() && !decoded.ownerUsername.equals(owner, true)) {
            return@withContext null
        }
        // One-time migration of the old single-account snapshot into the owner's file.
        if (source == legacySnapshotFile && !accountFile.exists()) {
            runCatching { accountFile.writeText(source.readText()) }
        }
        decoded
    }

    suspend fun saveAppSnapshot(snapshot: CachedAppSnapshot) = withContext(Dispatchers.IO) {
        val owner = snapshot.ownerUsername.trim().removePrefix("@").lowercase().ifBlank { cachedOwnerUsername() }
        if (owner.isBlank()) return@withContext
        val normalized = snapshot.copy(ownerUsername = owner, cachedAt = System.currentTimeMillis())
        val json = codec.encodeAppSnapshot(normalized) ?: return@withContext
        rememberOwner(owner)
        val snapshotFile = snapshotFileFor(owner)
        val temp = File(snapshotFile.parentFile, "${snapshotFile.name}.tmp")
        runCatching {
            temp.writeText(json)
            if (!temp.renameTo(snapshotFile)) {
                snapshotFile.writeText(json)
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "Unable to save cached app snapshot", it) }
    }
'''
store = replace_once(store, old_snapshot, new_snapshot, "per-account durable snapshot")
write_changed(store_path, store)

print("message persistence hardening patch completed")

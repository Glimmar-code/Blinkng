package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.models.ActivityItem
import com.example.data.models.ChatConversation
import com.example.data.models.ChatMessage
import com.example.data.models.ConnectHubSnapshot
import com.example.data.models.FeedPost
import com.example.data.models.LeaderboardUser
import com.example.data.models.MarketItem
import com.example.data.models.Story
import com.example.data.models.UserProfile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class CachedAppSnapshot(
    val ownerUsername: String = "",
    val myProfile: UserProfile = UserProfile(),
    val posts: List<FeedPost> = emptyList(),
    val reels: List<FeedPost> = emptyList(),
    val profiles: List<UserProfile> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val stories: List<Story> = emptyList(),
    val marketItems: List<MarketItem> = emptyList(),
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val gameLeaderboardUsers: List<LeaderboardUser> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val connectHub: ConnectHubSnapshot = ConnectHubSnapshot(),
    val mutedUsers: Set<String> = emptySet(),
    val blinkCoinBalance: Long = 0L,
    val cachedAt: Long = 0L
)

class OfflineContentStore(context: Context) {
    companion object {
        private const val TAG = "OfflineContentStore"
        private const val DEFAULT_CACHE_MAX_AGE_MS = 180L * 24L * 60L * 60L * 1000L
    }

    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()
    private val codec = OfflineContentCodec()
    private val snapshotFile = File(context.noBackupFilesDir, "blink_main_snapshot.json")
    private val metadataPrefs = context.getSharedPreferences("blink_offline_cache_meta", Context.MODE_PRIVATE)

    fun cachedOwnerUsername(): String = metadataPrefs.getString("owner_username", "").orEmpty()

    private fun rememberOwner(username: String) {
        if (username.isNotBlank()) metadataPrefs.edit().putString("owner_username", username.lowercase()).apply()
    }

    val posts: Flow<List<FeedPost>> = dao.observePosts()
        .map { rows -> rows.mapNotNull { codec.decodePost(it.payloadJson) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val reels: Flow<List<FeedPost>> = dao.observeReels()
        .map { rows -> rows.mapNotNull { codec.decodePost(it.payloadJson) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val profiles: Flow<List<UserProfile>> = dao.observeProfiles()
        .map { rows -> rows.mapNotNull { codec.decodeProfile(it.payloadJson) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val conversations: Flow<List<ChatConversation>> =
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

    suspend fun replaceFeed(posts: List<FeedPost>, reels: List<FeedPost>, ownerUsername: String = "") {
        rememberOwner(ownerUsername)
        val cachedAt = System.currentTimeMillis()
        val rows = buildList {
            posts.distinctBy { it.id }.forEachIndexed { index, post ->
                codec.encodePost(post)?.let { json ->
                    add(CachedPostEntity(post.id, false, index, json, cachedAt))
                }
            }
            reels.distinctBy { it.id }.forEachIndexed { index, reel ->
                codec.encodePost(reel)?.let { json ->
                    add(CachedPostEntity(reel.id, true, index, json, cachedAt))
                }
            }
        }
        dao.replaceFeed(rows)
    }

    suspend fun replaceProfiles(profiles: List<UserProfile>, ownerUsername: String = "") {
        rememberOwner(ownerUsername)
        val cachedAt = System.currentTimeMillis()
        val rows = profiles
            .filter { it.id.isNotBlank() || it.username.isNotBlank() }
            .distinctBy { it.id.ifBlank { it.username.lowercase() } }
            .mapIndexedNotNull { index, profile ->
                codec.encodeProfile(profile)?.let { json ->
                    CachedProfileEntity(
                        cacheKey = profile.id.ifBlank { profile.username.lowercase() },
                        username = profile.username.lowercase(),
                        displayOrder = index,
                        payloadJson = json,
                        cachedAt = cachedAt
                    )
                }
            }
        dao.replaceProfiles(rows)
    }

    suspend fun replaceConversations(conversations: List<ChatConversation>, ownerUsername: String = "") {
        rememberOwner(ownerUsername)
        val cachedAt = System.currentTimeMillis()
        val conversationRows = conversations.distinctBy { it.id }.mapIndexedNotNull { index, conversation ->
            codec.encodeConversation(conversation.copy(messages = mutableListOf()))?.let { json ->
                CachedConversationEntity(
                    id = conversation.id,
                    partnerUsername = conversation.partnerUsername.lowercase(),
                    displayOrder = index,
                    payloadJson = json,
                    cachedAt = cachedAt
                )
            }
        }
        val messageRows = conversations.flatMap { conversation ->
            conversation.messages.distinctBy { it.id }.mapIndexedNotNull { index, message ->
                val stableId = message.id.ifBlank { "${conversation.id}_${index}_${message.rawTimestamp}" }
                codec.encodeMessage(message)?.let { json ->
                    CachedMessageEntity(
                        id = stableId,
                        conversationId = conversation.id,
                        displayOrder = index,
                        rawTimestamp = message.rawTimestamp,
                        payloadJson = json,
                        cachedAt = cachedAt
                    )
                }
            }
        }
        dao.replaceConversations(conversationRows, messageRows)
    }

    suspend fun upsertProfile(profile: UserProfile) {
        if (profile.id.isBlank() && profile.username.isBlank()) return
        val json = codec.encodeProfile(profile) ?: return
        dao.insertProfiles(
            listOf(
                CachedProfileEntity(
                    cacheKey = profile.id.ifBlank { profile.username.lowercase() },
                    username = profile.username.lowercase(),
                    displayOrder = 0,
                    payloadJson = json,
                    cachedAt = System.currentTimeMillis()
                )
            )
        )
    }

    suspend fun enqueueMessage(localId: String, receiverUsername: String, content: String) {
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

    suspend fun markOutboxFailure(item: MessageOutboxEntity, error: String) {
        val nextAttempt = item.attemptCount + 1
        val delayMs = (5_000L * (1L shl nextAttempt.coerceAtMost(6))).coerceAtMost(30L * 60L * 1000L)
        dao.markOutboxFailure(
            localId = item.localId,
            attemptCount = nextAttempt,
            nextRetryAt = System.currentTimeMillis() + delayMs,
            error = error.take(400)
        )
    }

    suspend fun loadAppSnapshot(): CachedAppSnapshot? = withContext(Dispatchers.IO) {
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

    suspend fun resetOutbox(localId: String) = dao.resetOutbox(localId)
    suspend fun deleteOutbox(localId: String) = dao.deleteOutbox(localId)
    suspend fun deletePost(postId: String) = dao.deletePost(postId)

    suspend fun pruneOldCaches(maxAgeMs: Long = DEFAULT_CACHE_MAX_AGE_MS) {
        val before = System.currentTimeMillis() - maxAgeMs.coerceAtLeast(60_000L)
        dao.prunePosts(before)
        dao.pruneProfiles(before)
        dao.pruneConversations(before)
        dao.pruneMessages(before)
    }
}

internal class OfflineContentCodec {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val postAdapter: JsonAdapter<FeedPost> = moshi.adapter(FeedPost::class.java)
    private val profileAdapter: JsonAdapter<UserProfile> = moshi.adapter(UserProfile::class.java)
    private val conversationAdapter: JsonAdapter<ChatConversation> = moshi.adapter(ChatConversation::class.java)
    private val messageAdapter: JsonAdapter<ChatMessage> = moshi.adapter(ChatMessage::class.java)
    private val appSnapshotAdapter: JsonAdapter<CachedAppSnapshot> = moshi.adapter(CachedAppSnapshot::class.java)

    fun encodePost(post: FeedPost): String? = runCatching { postAdapter.toJson(post) }
        .onFailure { Log.w("OfflineContentCodec", "Unable to encode cached post", it) }
        .getOrNull()

    fun decodePost(json: String): FeedPost? = runCatching { postAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable cached post", it) }
        .getOrNull()

    fun encodeProfile(profile: UserProfile): String? = runCatching { profileAdapter.toJson(profile) }
        .onFailure { Log.w("OfflineContentCodec", "Unable to encode cached profile", it) }
        .getOrNull()

    fun decodeProfile(json: String): UserProfile? = runCatching { profileAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable cached profile", it) }
        .getOrNull()

    fun encodeConversation(conversation: ChatConversation): String? =
        runCatching { conversationAdapter.toJson(conversation) }
            .onFailure { Log.w("OfflineContentCodec", "Unable to encode cached conversation", it) }
            .getOrNull()

    fun decodeConversation(json: String): ChatConversation? =
        runCatching { conversationAdapter.fromJson(json) }
            .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable cached conversation", it) }
            .getOrNull()

    fun encodeMessage(message: ChatMessage): String? = runCatching { messageAdapter.toJson(message) }
        .onFailure { Log.w("OfflineContentCodec", "Unable to encode cached message", it) }
        .getOrNull()

    fun decodeMessage(json: String): ChatMessage? = runCatching { messageAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable cached message", it) }
        .getOrNull()

    fun encodeAppSnapshot(snapshot: CachedAppSnapshot): String? = runCatching { appSnapshotAdapter.toJson(snapshot) }
        .onFailure { Log.w("OfflineContentCodec", "Unable to encode app snapshot", it) }
        .getOrNull()

    fun decodeAppSnapshot(json: String): CachedAppSnapshot? = runCatching { appSnapshotAdapter.fromJson(json) }
        .onFailure { Log.w("OfflineContentCodec", "Ignoring an unreadable app snapshot", it) }
        .getOrNull()
}

package com.example.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "cached_feed_content",
    indices = [Index(value = ["is_reel", "display_order"])]
)
data class CachedPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "is_reel") val isReel: Boolean,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "cached_profiles",
    indices = [Index(value = ["username"])]
)
data class CachedProfileEntity(
    @PrimaryKey @ColumnInfo(name = "cache_key") val cacheKey: String,
    val username: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "cached_conversations",
    primaryKeys = ["owner_username", "id"],
    indices = [
        Index(value = ["owner_username", "partner_username"]),
        Index(value = ["owner_username", "display_order"])
    ]
)
data class CachedConversationEntity(
    val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "partner_username") val partnerUsername: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "cached_messages",
    primaryKeys = ["owner_username", "id"],
    indices = [
        Index(value = ["owner_username", "conversation_id", "display_order"]),
        Index(value = ["owner_username", "raw_timestamp"])
    ]
)
data class CachedMessageEntity(
    val id: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "raw_timestamp") val rawTimestamp: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "message_outbox",
    primaryKeys = ["owner_username", "local_id"],
    indices = [
        Index(value = ["owner_username", "next_retry_at"]),
        Index(value = ["owner_username", "receiver_username"])
    ]
)
data class MessageOutboxEntity(
    @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "owner_username") val ownerUsername: String,
    @ColumnInfo(name = "receiver_username") val receiverUsername: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long = 0L,
    @ColumnInfo(name = "last_error") val lastError: String = ""
)

@Dao
interface CachedContentDao {
    @Query("SELECT * FROM cached_feed_content WHERE is_reel = 0 ORDER BY display_order ASC")
    fun observePosts(): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_feed_content WHERE is_reel = 1 ORDER BY display_order ASC")
    fun observeReels(): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_profiles ORDER BY display_order ASC, username ASC")
    fun observeProfiles(): Flow<List<CachedProfileEntity>>

    @Query("SELECT * FROM cached_conversations WHERE owner_username = :ownerUsername ORDER BY display_order ASC")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<CachedProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<CachedConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(item: MessageOutboxEntity)

    @Query("DELETE FROM cached_feed_content")
    suspend fun deleteAllPosts()

    @Query("DELETE FROM cached_profiles")
    suspend fun deleteAllProfiles()

    // Chat rows are intentionally never bulk-deleted from the durable cache.
    // Per-user clear/delete behavior lives in Supabase user-state, not local table wipes.

    @Query("DELETE FROM cached_feed_content WHERE id = :postId")
    suspend fun deletePost(postId: String)

    // CHAT_CACHE_RECONCILIATION_V1: remove only obsolete local aliases/temp rows.
    // Real server-backed history is never pruned by these queries.
    @Query("DELETE FROM cached_messages WHERE owner_username = :ownerUsername AND conversation_id IN (SELECT id FROM cached_conversations WHERE owner_username = :ownerUsername AND substr(id, 1, 6) = 'local_' AND partner_username IN (:partnerUsernames))")
    suspend fun deleteMessagesForLocalConversationAliases(ownerUsername: String, partnerUsernames: List<String>)

    @Query("DELETE FROM cached_conversations WHERE owner_username = :ownerUsername AND substr(id, 1, 6) = 'local_' AND partner_username IN (:partnerUsernames)")
    suspend fun deleteLocalConversationAliases(ownerUsername: String, partnerUsernames: List<String>)

    @Query("DELETE FROM cached_messages WHERE owner_username = :ownerUsername AND conversation_id IN (:conversationIds) AND substr(id, 1, 5) = 'temp_'")
    suspend fun deleteTempMessagesForConversations(ownerUsername: String, conversationIds: List<String>)

    @Query("DELETE FROM message_outbox WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun deleteOutbox(ownerUsername: String, localId: String)

    @Query("UPDATE message_outbox SET attempt_count = :attemptCount, next_retry_at = :nextRetryAt, last_error = :error WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun markOutboxFailure(ownerUsername: String, localId: String, attemptCount: Int, nextRetryAt: Long, error: String)

    @Query("UPDATE message_outbox SET attempt_count = 0, next_retry_at = 0, last_error = '' WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun resetOutbox(ownerUsername: String, localId: String)

    @Query("DELETE FROM cached_feed_content WHERE cached_at < :before")
    suspend fun prunePosts(before: Long)

    @Query("DELETE FROM cached_profiles WHERE cached_at < :before")
    suspend fun pruneProfiles(before: Long)

    // Conversation and message history deliberately has no age-based pruning API.

    @Transaction
    suspend fun replaceFeed(posts: List<CachedPostEntity>) {
        // Cache-first/native behavior: a network refresh must never erase older
        // locally available feed pages. REPLACE only updates matching IDs.
        if (posts.isNotEmpty()) insertPosts(posts)
    }

    @Transaction
    suspend fun replaceProfiles(profiles: List<CachedProfileEntity>) {
        // Preserve previously seen profiles so avatars/names remain usable offline.
        if (profiles.isNotEmpty()) insertProfiles(profiles)
    }

    @Transaction
    suspend fun replaceConversations(
        conversations: List<CachedConversationEntity>,
        messages: List<CachedMessageEntity>
    ) {
        // Never blank real server history while a smaller/partial page refreshes.
        // We only clear stale local aliases/temp rows that are replaced by this snapshot.
        conversations.groupBy { it.ownerUsername }.forEach { (owner, rows) ->
            val serverPartners = rows
                .filterNot { it.id.startsWith("local_") }
                .map { it.partnerUsername }
                .filter { it.isNotBlank() }
                .distinct()
            if (serverPartners.isNotEmpty()) {
                deleteMessagesForLocalConversationAliases(owner, serverPartners)
                deleteLocalConversationAliases(owner, serverPartners)
            }

            val conversationIds = rows.map { it.id }.filter { it.isNotBlank() }.distinct()
            if (conversationIds.isNotEmpty()) {
                // Active temp messages are inserted again below from the current snapshot;
                // obsolete temp rows from older snapshots stay gone.
                deleteTempMessagesForConversations(owner, conversationIds)
            }
        }

        if (conversations.isNotEmpty()) insertConversations(conversations)
        if (messages.isNotEmpty()) insertMessages(messages)
    }
}

@Database(
    entities = [
        CachedPostEntity::class,
        CachedProfileEntity::class,
        CachedConversationEntity::class,
        CachedMessageEntity::class,
        MessageOutboxEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class BlinkDatabase : RoomDatabase() {
    abstract fun cachedContentDao(): CachedContentDao

    companion object {
        @Volatile private var instance: BlinkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cached_conversations (id TEXT NOT NULL PRIMARY KEY, partner_username TEXT NOT NULL, display_order INTEGER NOT NULL, payload_json TEXT NOT NULL, cached_at INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_partner_username ON cached_conversations(partner_username)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_conversations_display_order ON cached_conversations(display_order)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cached_messages (id TEXT NOT NULL PRIMARY KEY, conversation_id TEXT NOT NULL, display_order INTEGER NOT NULL, raw_timestamp TEXT NOT NULL, payload_json TEXT NOT NULL, cached_at INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_conversation_id_display_order ON cached_messages(conversation_id, display_order)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_messages_raw_timestamp ON cached_messages(raw_timestamp)")
                db.execSQL("CREATE TABLE IF NOT EXISTS message_outbox (local_id TEXT NOT NULL PRIMARY KEY, receiver_username TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, attempt_count INTEGER NOT NULL, next_retry_at INTEGER NOT NULL, last_error TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_next_retry_at ON message_outbox(next_retry_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_outbox_receiver_username ON message_outbox(receiver_username)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite cannot alter a primary key in place. Rebuild each account-scoped
                // chat table so the same server conversation/message can safely be cached
                // for multiple signed-in accounts on one device.
                db.execSQL("CREATE TABLE cached_conversations_v4 (id TEXT NOT NULL, owner_username TEXT NOT NULL, partner_username TEXT NOT NULL, display_order INTEGER NOT NULL, payload_json TEXT NOT NULL, cached_at INTEGER NOT NULL, PRIMARY KEY(owner_username, id))")
                db.execSQL("INSERT INTO cached_conversations_v4(id, owner_username, partner_username, display_order, payload_json, cached_at) SELECT id, owner_username, partner_username, display_order, payload_json, cached_at FROM cached_conversations")
                db.execSQL("DROP TABLE cached_conversations")
                db.execSQL("ALTER TABLE cached_conversations_v4 RENAME TO cached_conversations")

                db.execSQL("CREATE TABLE cached_messages_v4 (id TEXT NOT NULL, owner_username TEXT NOT NULL, conversation_id TEXT NOT NULL, display_order INTEGER NOT NULL, raw_timestamp TEXT NOT NULL, payload_json TEXT NOT NULL, cached_at INTEGER NOT NULL, PRIMARY KEY(owner_username, id))")
                db.execSQL("INSERT INTO cached_messages_v4(id, owner_username, conversation_id, display_order, raw_timestamp, payload_json, cached_at) SELECT id, owner_username, conversation_id, display_order, raw_timestamp, payload_json, cached_at FROM cached_messages")
                db.execSQL("DROP TABLE cached_messages")
                db.execSQL("ALTER TABLE cached_messages_v4 RENAME TO cached_messages")

                db.execSQL("CREATE TABLE message_outbox_v4 (local_id TEXT NOT NULL, owner_username TEXT NOT NULL, receiver_username TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, attempt_count INTEGER NOT NULL, next_retry_at INTEGER NOT NULL, last_error TEXT NOT NULL, PRIMARY KEY(owner_username, local_id))")
                db.execSQL("INSERT INTO message_outbox_v4(local_id, owner_username, receiver_username, content, created_at, attempt_count, next_retry_at, last_error) SELECT local_id, owner_username, receiver_username, content, created_at, attempt_count, next_retry_at, last_error FROM message_outbox")
                db.execSQL("DROP TABLE message_outbox")
                db.execSQL("ALTER TABLE message_outbox_v4 RENAME TO message_outbox")

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
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlinkDatabase::class.java,
                    "blink_offline_cache.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}

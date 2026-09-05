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
    indices = [Index(value = ["partner_username"]), Index(value = ["display_order"])]
)
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "partner_username") val partnerUsername: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "cached_messages",
    indices = [Index(value = ["conversation_id", "display_order"]), Index(value = ["raw_timestamp"])]
)
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "raw_timestamp") val rawTimestamp: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)

@Entity(
    tableName = "message_outbox",
    indices = [Index(value = ["next_retry_at"]), Index(value = ["receiver_username"])]
)
data class MessageOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "local_id") val localId: String,
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

    @Query("SELECT * FROM cached_conversations ORDER BY display_order ASC")
    fun observeConversations(): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_messages ORDER BY conversation_id ASC, display_order ASC")
    fun observeMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT COUNT(*) FROM message_outbox WHERE attempt_count < 5")
    fun observePendingOutboxCount(): Flow<Int>

    @Query("SELECT * FROM message_outbox WHERE attempt_count < 5 AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")
    suspend fun pendingOutbox(now: Long, limit: Int): List<MessageOutboxEntity>

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

    @Query("DELETE FROM cached_conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM cached_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM cached_feed_content WHERE id = :postId")
    suspend fun deletePost(postId: String)

    @Query("DELETE FROM message_outbox WHERE local_id = :localId")
    suspend fun deleteOutbox(localId: String)

    @Query("UPDATE message_outbox SET attempt_count = :attemptCount, next_retry_at = :nextRetryAt, last_error = :error WHERE local_id = :localId")
    suspend fun markOutboxFailure(localId: String, attemptCount: Int, nextRetryAt: Long, error: String)

    @Query("UPDATE message_outbox SET attempt_count = 0, next_retry_at = 0, last_error = '' WHERE local_id = :localId")
    suspend fun resetOutbox(localId: String)

    @Query("DELETE FROM cached_feed_content WHERE cached_at < :before")
    suspend fun prunePosts(before: Long)

    @Query("DELETE FROM cached_profiles WHERE cached_at < :before")
    suspend fun pruneProfiles(before: Long)

    @Query("DELETE FROM cached_conversations WHERE cached_at < :before")
    suspend fun pruneConversations(before: Long)

    @Query("DELETE FROM cached_messages WHERE cached_at < :before")
    suspend fun pruneMessages(before: Long)

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
        // Never blank chat history while a smaller/partial Supabase page refreshes.
        // Existing rows remain until the long-term prune policy removes old content.
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
    version = 2,
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

        fun getInstance(context: Context): BlinkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlinkDatabase::class.java,
                    "blink_offline_cache.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}

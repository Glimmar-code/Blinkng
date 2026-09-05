from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
cache_path = ROOT / "app/src/main/java/com/example/data/local/CachedContent.kt"
store_path = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# CachedContent.kt -----------------------------------------------------------
text = cache_path.read_text()
text = replace_once(
    text,
'''@Entity(
    tableName = "cached_conversations",
    indices = [
        Index(value = ["owner_username", "partner_username"]),
        Index(value = ["owner_username", "display_order"])
    ]
)
data class CachedConversationEntity(
    @PrimaryKey val id: String,
''',
'''@Entity(
    tableName = "cached_conversations",
    primaryKeys = ["owner_username", "id"],
    indices = [
        Index(value = ["owner_username", "partner_username"]),
        Index(value = ["owner_username", "display_order"])
    ]
)
data class CachedConversationEntity(
    val id: String,
''',
    "conversation composite primary key"
)
text = replace_once(
    text,
'''@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["owner_username", "conversation_id", "display_order"]),
        Index(value = ["owner_username", "raw_timestamp"])
    ]
)
data class CachedMessageEntity(
    @PrimaryKey val id: String,
''',
'''@Entity(
    tableName = "cached_messages",
    primaryKeys = ["owner_username", "id"],
    indices = [
        Index(value = ["owner_username", "conversation_id", "display_order"]),
        Index(value = ["owner_username", "raw_timestamp"])
    ]
)
data class CachedMessageEntity(
    val id: String,
''',
    "message composite primary key"
)
text = replace_once(
    text,
'''@Entity(
    tableName = "message_outbox",
    indices = [
        Index(value = ["owner_username", "next_retry_at"]),
        Index(value = ["owner_username", "receiver_username"])
    ]
)
data class MessageOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "local_id") val localId: String,
''',
'''@Entity(
    tableName = "message_outbox",
    primaryKeys = ["owner_username", "local_id"],
    indices = [
        Index(value = ["owner_username", "next_retry_at"]),
        Index(value = ["owner_username", "receiver_username"])
    ]
)
data class MessageOutboxEntity(
    @ColumnInfo(name = "local_id") val localId: String,
''',
    "outbox composite primary key"
)

text = replace_once(
    text,
'''    @Query("DELETE FROM cached_conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM cached_messages")
    suspend fun deleteAllMessages()

''',
'''    // Chat rows are intentionally never bulk-deleted from the durable cache.
    // Per-user clear/delete behavior lives in Supabase user-state, not local table wipes.

''',
    "remove chat bulk-delete DAO surface"
)
text = replace_once(
    text,
'''    @Query("DELETE FROM message_outbox WHERE local_id = :localId")
    suspend fun deleteOutbox(localId: String)

    @Query("UPDATE message_outbox SET attempt_count = :attemptCount, next_retry_at = :nextRetryAt, last_error = :error WHERE local_id = :localId")
    suspend fun markOutboxFailure(localId: String, attemptCount: Int, nextRetryAt: Long, error: String)

    @Query("UPDATE message_outbox SET attempt_count = 0, next_retry_at = 0, last_error = '' WHERE local_id = :localId")
    suspend fun resetOutbox(localId: String)
''',
'''    @Query("DELETE FROM message_outbox WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun deleteOutbox(ownerUsername: String, localId: String)

    @Query("UPDATE message_outbox SET attempt_count = :attemptCount, next_retry_at = :nextRetryAt, last_error = :error WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun markOutboxFailure(ownerUsername: String, localId: String, attemptCount: Int, nextRetryAt: Long, error: String)

    @Query("UPDATE message_outbox SET attempt_count = 0, next_retry_at = 0, last_error = '' WHERE owner_username = :ownerUsername AND local_id = :localId")
    suspend fun resetOutbox(ownerUsername: String, localId: String)
''',
    "owner-scoped outbox mutations"
)
text = replace_once(
    text,
'''    @Query("DELETE FROM cached_conversations WHERE cached_at < :before")
    suspend fun pruneConversations(before: Long)

    @Query("DELETE FROM cached_messages WHERE cached_at < :before")
    suspend fun pruneMessages(before: Long)

''',
'''    // Conversation and message history deliberately has no age-based pruning API.

''',
    "remove chat TTL DAO surface"
)
text = replace_once(text, '    version = 3,\n', '    version = 4,\n', "Room database v4")

migration_anchor = '''        fun getInstance(context: Context): BlinkDatabase =
            instance ?: synchronized(this) {
'''
migration = '''        val MIGRATION_3_4 = object : Migration(3, 4) {
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
'''
text = replace_once(text, migration_anchor, migration, "Room 3->4 composite-key migration")
text = replace_once(
    text,
    '                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)\n',
    '                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)\n',
    "register Room v4 migration"
)
cache_path.write_text(text)
print("patched", cache_path.relative_to(ROOT))


# OfflineContentStore.kt -----------------------------------------------------
text = store_path.read_text()
text = replace_once(
    text,
'''        dao.markOutboxFailure(
            localId = item.localId,
            attemptCount = nextAttempt,
''',
'''        dao.markOutboxFailure(
            ownerUsername = item.ownerUsername,
            localId = item.localId,
            attemptCount = nextAttempt,
''',
    "scope outbox retry update"
)
text = replace_once(
    text,
'''    suspend fun resetOutbox(localId: String) = dao.resetOutbox(localId)
    suspend fun deleteOutbox(localId: String) = dao.deleteOutbox(localId)
''',
'''    suspend fun resetOutbox(localId: String) {
        val owner = cachedOwnerUsername()
        if (owner.isNotBlank()) dao.resetOutbox(owner, localId)
    }

    suspend fun deleteOutbox(localId: String) {
        val owner = cachedOwnerUsername()
        if (owner.isNotBlank()) dao.deleteOutbox(owner, localId)
    }
''',
    "scope outbox reset/delete"
)
store_path.write_text(text)
print("patched", store_path.relative_to(ROOT))
print("account-scoped composite chat cache patch completed")

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
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.util.UUID

@Entity(
    tableName = "offline_mutations",
    indices = [Index(value = ["next_retry_at"]), Index(value = ["operation", "entity_id"])]
)
data class OfflineMutationEntity(
    @PrimaryKey val id: String,
    val operation: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long = 0L,
    @ColumnInfo(name = "last_error") val lastError: String = ""
)

@Dao
interface OfflineMutationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OfflineMutationEntity)

    @Query("SELECT * FROM offline_mutations WHERE attempt_count < 5 AND next_retry_at <= :now ORDER BY created_at ASC LIMIT :limit")
    suspend fun pending(now: Long, limit: Int): List<OfflineMutationEntity>

    @Query("DELETE FROM offline_mutations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE offline_mutations SET attempt_count=:attemptCount,next_retry_at=:nextRetryAt,last_error=:error WHERE id=:id")
    suspend fun markFailure(id: String, attemptCount: Int, nextRetryAt: Long, error: String)

    @Query("SELECT COUNT(*) FROM offline_mutations WHERE attempt_count < 5")
    suspend fun pendingCount(): Int
}

@Database(entities = [OfflineMutationEntity::class], version = 1, exportSchema = true)
abstract class OfflineMutationDatabase : RoomDatabase() {
    abstract fun dao(): OfflineMutationDao

    companion object {
        @Volatile private var instance: OfflineMutationDatabase? = null

        fun getInstance(context: Context): OfflineMutationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineMutationDatabase::class.java,
                    "blink_offline_mutations.db"
                ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}

class OfflineMutationStore(private val context: Context) {
    private val dao = OfflineMutationDatabase.getInstance(context).dao()

    suspend fun enqueuePostLike(postId: String, liked: Boolean, count: Int) {
        enqueueState(
            operation = OP_POST_LIKE,
            entityId = postId,
            payload = JSONObject().put("liked", liked).put("count", count)
        )
    }

    suspend fun enqueueBookmark(postId: String, bookmarked: Boolean) {
        enqueueState(
            operation = OP_BOOKMARK,
            entityId = postId,
            payload = JSONObject().put("bookmarked", bookmarked)
        )
    }

    suspend fun enqueueShare(postId: String) {
        dao.upsert(
            OfflineMutationEntity(
                id = "$OP_SHARE:${UUID.randomUUID()}",
                operation = OP_SHARE,
                entityId = postId,
                payloadJson = "{}"
            )
        )
        schedule(context)
    }

    suspend fun pending(limit: Int = 50): List<OfflineMutationEntity> =
        dao.pending(System.currentTimeMillis(), limit.coerceIn(1, 100))

    suspend fun complete(id: String) = dao.delete(id)

    suspend fun fail(item: OfflineMutationEntity, error: String) {
        val attempt = item.attemptCount + 1
        val retryDelay = (5_000L * (1L shl attempt.coerceAtMost(6))).coerceAtMost(30L * 60L * 1000L)
        dao.markFailure(
            id = item.id,
            attemptCount = attempt,
            nextRetryAt = System.currentTimeMillis() + retryDelay,
            error = error.take(400)
        )
    }

    suspend fun pendingCount(): Int = dao.pendingCount()

    private suspend fun enqueueState(operation: String, entityId: String, payload: JSONObject) {
        // One stable key per stateful mutation gives last-write-wins conflict resolution.
        dao.upsert(
            OfflineMutationEntity(
                id = "$operation:$entityId",
                operation = operation,
                entityId = entityId,
                payloadJson = payload.toString()
            )
        )
        schedule(context)
    }

    companion object {
        const val OP_POST_LIKE = "post_like"
        const val OP_BOOKMARK = "post_bookmark"
        const val OP_SHARE = "post_share"
        private const val UNIQUE_WORK = "blink_offline_mutation_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<OfflineMutationWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}

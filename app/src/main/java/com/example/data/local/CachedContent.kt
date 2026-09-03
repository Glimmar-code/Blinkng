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

@Dao
interface CachedContentDao {
    @Query("SELECT * FROM cached_feed_content WHERE is_reel = 0 ORDER BY display_order ASC")
    fun observePosts(): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_feed_content WHERE is_reel = 1 ORDER BY display_order ASC")
    fun observeReels(): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_profiles ORDER BY display_order ASC, username ASC")
    fun observeProfiles(): Flow<List<CachedProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<CachedProfileEntity>)

    @Query("DELETE FROM cached_feed_content")
    suspend fun deleteAllPosts()

    @Query("DELETE FROM cached_profiles")
    suspend fun deleteAllProfiles()

    @Query("DELETE FROM cached_feed_content WHERE id = :postId")
    suspend fun deletePost(postId: String)

    @Transaction
    suspend fun replaceFeed(posts: List<CachedPostEntity>) {
        deleteAllPosts()
        if (posts.isNotEmpty()) insertPosts(posts)
    }

    @Transaction
    suspend fun replaceProfiles(profiles: List<CachedProfileEntity>) {
        deleteAllProfiles()
        if (profiles.isNotEmpty()) insertProfiles(profiles)
    }
}

@Database(
    entities = [CachedPostEntity::class, CachedProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlinkDatabase : RoomDatabase() {
    abstract fun cachedContentDao(): CachedContentDao

    companion object {
        @Volatile
        private var instance: BlinkDatabase? = null

        fun getInstance(context: Context): BlinkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlinkDatabase::class.java,
                    "blink_offline_cache.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}

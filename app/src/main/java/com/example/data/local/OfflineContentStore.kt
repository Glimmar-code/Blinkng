package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class OfflineContentStore(context: Context) {
    companion object {
        private const val TAG = "OfflineContentStore"
    }

    private val dao = BlinkDatabase.getInstance(context).cachedContentDao()
    private val codec = OfflineContentCodec()

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

    suspend fun replaceFeed(posts: List<FeedPost>, reels: List<FeedPost>) {
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

    suspend fun replaceProfiles(profiles: List<UserProfile>) {
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

    suspend fun deletePost(postId: String) = dao.deletePost(postId)
}

internal class OfflineContentCodec {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val postAdapter: JsonAdapter<FeedPost> = moshi.adapter(FeedPost::class.java)
    private val profileAdapter: JsonAdapter<UserProfile> = moshi.adapter(UserProfile::class.java)

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
}

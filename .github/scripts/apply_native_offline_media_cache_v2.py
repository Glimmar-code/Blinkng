from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}")
    path.write_text(text.replace(old, new, 1))


# 1) Do not wipe durable Room content every time a refreshed page arrives.
# Keep previously cached feed/messages/profiles and upsert the newest copies.
cached = ROOT / "app/src/main/java/com/example/data/local/CachedContent.kt"
replace_once(
    cached,
    '''    @Transaction\n    suspend fun replaceFeed(posts: List<CachedPostEntity>) {\n        deleteAllPosts()\n        if (posts.isNotEmpty()) insertPosts(posts)\n    }\n\n    @Transaction\n    suspend fun replaceProfiles(profiles: List<CachedProfileEntity>) {\n        deleteAllProfiles()\n        if (profiles.isNotEmpty()) insertProfiles(profiles)\n    }\n\n    @Transaction\n    suspend fun replaceConversations(\n        conversations: List<CachedConversationEntity>,\n        messages: List<CachedMessageEntity>\n    ) {\n        deleteAllMessages()\n        deleteAllConversations()\n        if (conversations.isNotEmpty()) insertConversations(conversations)\n        if (messages.isNotEmpty()) insertMessages(messages)\n    }''',
    '''    @Transaction\n    suspend fun replaceFeed(posts: List<CachedPostEntity>) {\n        // Cache-first/native behavior: a network refresh must never erase older\n        // locally available feed pages. REPLACE only updates matching IDs.\n        if (posts.isNotEmpty()) insertPosts(posts)\n    }\n\n    @Transaction\n    suspend fun replaceProfiles(profiles: List<CachedProfileEntity>) {\n        // Preserve previously seen profiles so avatars/names remain usable offline.\n        if (profiles.isNotEmpty()) insertProfiles(profiles)\n    }\n\n    @Transaction\n    suspend fun replaceConversations(\n        conversations: List<CachedConversationEntity>,\n        messages: List<CachedMessageEntity>\n    ) {\n        // Never blank chat history while a smaller/partial Supabase page refreshes.\n        // Existing rows remain until the long-term prune policy removes old content.\n        if (conversations.isNotEmpty()) insertConversations(conversations)\n        if (messages.isNotEmpty()) insertMessages(messages)\n    }'''
)

# 2) Keep data for a full year unless the user clears app data.
offline_store = ROOT / "app/src/main/java/com/example/data/local/OfflineContentStore.kt"
replace_once(
    offline_store,
    'private const val DEFAULT_CACHE_MAX_AGE_MS = 180L * 24L * 60L * 60L * 1000L',
    'private const val DEFAULT_CACHE_MAX_AGE_MS = 365L * 24L * 60L * 60L * 1000L'
)

# 3) Coil images/avatars/media previews belong in durable app files, not cacheDir.
# Android may evict cacheDir at any time; filesDir survives normal cache cleanup.
app = ROOT / "app/src/main/java/com/example/BlinkApplication.kt"
replace_once(
    app,
    '''                DiskCache.Builder()\n                    .directory(cacheDir.resolve("image_cache"))\n                    .maxSizeBytes(100L * 1024L * 1024L)\n                    .build()''',
    '''                DiskCache.Builder()\n                    .directory(filesDir.resolve("blink_media/images").apply { mkdirs() })\n                    .maxSizeBytes(256L * 1024L * 1024L)\n                    .build()'''
)

# 4) Add a singleton persistent Media3 cache for reel playback.
media_cache = ROOT / "app/src/main/java/com/example/data/local/BlinkMediaCache.kt"
media_cache.write_text('''package com.example.data.local\n\nimport android.content.Context\nimport androidx.media3.database.StandaloneDatabaseProvider\nimport androidx.media3.datasource.DataSource\nimport androidx.media3.datasource.cache.CacheDataSource\nimport androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor\nimport androidx.media3.datasource.cache.SimpleCache\nimport java.io.File\n\n/**\n * Persistent reel cache used by every ExoPlayer instance.\n *\n * This deliberately lives in filesDir rather than cacheDir so normal Android cache\n * cleanup does not make already-played reels disappear. A bounded LRU still prevents\n * unbounded storage growth. Clearing app storage removes it, which is expected.\n */\nobject BlinkMediaCache {\n    private const val MAX_REEL_CACHE_BYTES = 512L * 1024L * 1024L\n\n    @Volatile\n    private var simpleCache: SimpleCache? = null\n\n    private fun cache(context: Context): SimpleCache {\n        simpleCache?.let { return it }\n        return synchronized(this) {\n            simpleCache ?: run {\n                val appContext = context.applicationContext\n                val directory = File(appContext.filesDir, "blink_media/reels").apply { mkdirs() }\n                val databaseProvider = StandaloneDatabaseProvider(appContext)\n                SimpleCache(\n                    directory,\n                    LeastRecentlyUsedCacheEvictor(MAX_REEL_CACHE_BYTES),\n                    databaseProvider\n                ).also { simpleCache = it }\n            }\n        }\n    }\n\n    fun dataSourceFactory(\n        context: Context,\n        upstream: DataSource.Factory\n    ): DataSource.Factory =\n        CacheDataSource.Factory()\n            .setCache(cache(context))\n            .setUpstreamDataSourceFactory(upstream)\n            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)\n}\n''')

# 5) Route reel ExoPlayer reads through the persistent cache.
reels = ROOT / "app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt"
replace_once(
    reels,
    'import com.example.data.models.FeedPost\n',
    'import com.example.data.local.BlinkMediaCache\nimport com.example.data.models.FeedPost\n'
)
replace_once(
    reels,
    '            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))',
    '            .setMediaSourceFactory(\n                DefaultMediaSourceFactory(BlinkMediaCache.dataSourceFactory(context, httpDataSource))\n            )'
)

print("Applied native offline persistence: merge-only Room cache, durable Coil cache, persistent Media3 reel cache.")

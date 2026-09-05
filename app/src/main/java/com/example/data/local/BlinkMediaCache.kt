package com.example.data.local

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Persistent reel cache used by every ExoPlayer instance.
 *
 * This deliberately lives in filesDir rather than cacheDir so normal Android cache
 * cleanup does not make already-played reels disappear. A bounded LRU still prevents
 * unbounded storage growth. Clearing app storage removes it, which is expected.
 */
object BlinkMediaCache {
    private const val MAX_REEL_CACHE_BYTES = 512L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        return synchronized(this) {
            simpleCache ?: run {
                val appContext = context.applicationContext
                val directory = File(appContext.filesDir, "blink_media/reels").apply { mkdirs() }
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                SimpleCache(
                    directory,
                    LeastRecentlyUsedCacheEvictor(MAX_REEL_CACHE_BYTES),
                    databaseProvider
                ).also { simpleCache = it }
            }
        }
    }

    fun dataSourceFactory(
        context: Context,
        upstream: DataSource.Factory
    ): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}

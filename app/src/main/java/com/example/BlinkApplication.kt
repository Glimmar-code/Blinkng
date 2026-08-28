package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.supabase.SupabaseService
import com.example.notification.BlinkNotificationHelper

class BlinkApplication :
    Application(),
    ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Give the REST/Supabase layer an application-safe Context
        // so authenticated access/refresh tokens can be persisted.
        SupabaseService.initialize(
            this
        )

        BlinkNotificationHelper
            .createNotificationChannels(
                this
            )
    }

    override fun newImageLoader():
        ImageLoader {

        return ImageLoader
            .Builder(this)

            .memoryCache {

                MemoryCache
                    .Builder(this)
                    .maxSizePercent(
                        0.25
                    )
                    .strongReferencesEnabled(
                        true
                    )
                    .build()
            }

            .diskCache {

                DiskCache
                    .Builder()
                    .directory(
                        cacheDir.resolve(
                            "image_cache"
                        )
                    )
                    .maxSizeBytes(
                        50L * 1024L * 1024L
                    )
                    .build()
            }

            .memoryCachePolicy(
                CachePolicy.ENABLED
            )

            .diskCachePolicy(
                CachePolicy.ENABLED
            )

            .networkCachePolicy(
                CachePolicy.ENABLED
            )

            .crossfade(
                true
            )

            .allowHardware(
                true
            )

            .build()
    }

    override fun onTrimMemory(
        level: Int
    ) {

        super.onTrimMemory(
            level
        )

        if (
            level >=
                TRIM_MEMORY_BACKGROUND
        ) {

            coil.Coil
                .imageLoader(this)
                .memoryCache
                ?.clear()
        }
    }
}
package com.example.data.supabase

import com.blinkng.shared.BlinkBackendDefaults
import com.example.BuildConfig

object SupabaseConfig {
    val anonKey: String = resolveConfig(
        fallback = BlinkBackendDefaults.SUPABASE_ANON_KEY,
        runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrNull(),
        runCatching { BuildConfig.VITE_SUPABASE_ANON_KEY }.getOrNull()
    )

    val url: String = resolveConfig(
        fallback = BlinkBackendDefaults.SUPABASE_URL,
        runCatching { BuildConfig.SUPABASE_URL }.getOrNull(),
        runCatching { BuildConfig.VITE_SUPABASE_URL }.getOrNull()
    )

    private fun resolveConfig(fallback: String, vararg values: String?): String {
        return values.firstOrNull {
            !it.isNullOrBlank() && !it.contains("placeholder", ignoreCase = true)
        }?.trim() ?: fallback
    }
}

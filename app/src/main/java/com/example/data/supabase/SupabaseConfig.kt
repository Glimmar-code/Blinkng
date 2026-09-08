package com.example.data.supabase

import com.blinkng.shared.BlinkBackendDefaults
import com.example.BuildConfig

object SupabaseConfig {
    private val environment: String = runCatching { BuildConfig.BLINK_ENV }
        .getOrNull()
        .orEmpty()
        .trim()
        .lowercase()

    val anonKey: String = BlinkBackendDefaults.resolveForEnvironment(
        environment = environment,
        candidate = firstConfigured(
            runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrNull(),
            runCatching { BuildConfig.VITE_SUPABASE_ANON_KEY }.getOrNull(),
        ),
        fallback = BlinkBackendDefaults.SUPABASE_ANON_KEY,
        configName = "SUPABASE_ANON_KEY",
    )

    val url: String = BlinkBackendDefaults.resolveForEnvironment(
        environment = environment,
        candidate = firstConfigured(
            runCatching { BuildConfig.SUPABASE_URL }.getOrNull(),
            runCatching { BuildConfig.VITE_SUPABASE_URL }.getOrNull(),
        ),
        fallback = BlinkBackendDefaults.SUPABASE_URL,
        configName = "SUPABASE_URL",
    )

    private fun firstConfigured(vararg values: String?): String? =
        values.firstOrNull {
            !it.isNullOrBlank() && !it.contains("placeholder", ignoreCase = true)
        }?.trim()
}

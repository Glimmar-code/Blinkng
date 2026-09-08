package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults

/** Windows adapter for the same public Supabase client configuration used by Android. */
object DesktopBackendConfig {
    private val environment: String = (
        System.getenv("BLINK_ENV")
            ?: System.getProperty("blink.env")
            ?: "production"
        ).trim().lowercase()

    val url: String = BlinkBackendDefaults.resolveForEnvironment(
        environment = environment,
        candidate = firstConfigured(
            System.getenv("SUPABASE_URL"),
            System.getenv("VITE_SUPABASE_URL"),
            System.getProperty("blink.supabase.url"),
        ),
        fallback = BlinkBackendDefaults.SUPABASE_URL,
        configName = "SUPABASE_URL",
    )

    val anonKey: String = BlinkBackendDefaults.resolveForEnvironment(
        environment = environment,
        candidate = firstConfigured(
            System.getenv("SUPABASE_ANON_KEY"),
            System.getenv("VITE_SUPABASE_ANON_KEY"),
            System.getProperty("blink.supabase.anonKey"),
        ),
        fallback = BlinkBackendDefaults.SUPABASE_ANON_KEY,
        configName = "SUPABASE_ANON_KEY",
    )

    private fun firstConfigured(vararg values: String?): String? =
        values.firstOrNull {
            !it.isNullOrBlank() && !it.contains("placeholder", ignoreCase = true)
        }?.trim()
}

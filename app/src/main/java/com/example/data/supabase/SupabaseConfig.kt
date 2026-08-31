package com.example.data.supabase

import com.example.BuildConfig

object SupabaseConfig {
    private const val DEFAULT_URL = "https://jhwgifrlxwspoedxjaly.supabase.co"
    private const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"

    val anonKey: String = resolveConfig(
        fallback = DEFAULT_ANON_KEY,
        runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrNull(),
        runCatching { BuildConfig.VITE_SUPABASE_ANON_KEY }.getOrNull()
    )

    val url: String = resolveConfig(
        fallback = DEFAULT_URL,
        runCatching { BuildConfig.SUPABASE_URL }.getOrNull(),
        runCatching { BuildConfig.VITE_SUPABASE_URL }.getOrNull()
    )

    private fun resolveConfig(fallback: String, vararg values: String?): String {
        return values.firstOrNull {
            !it.isNullOrBlank() && !it.contains("placeholder", ignoreCase = true)
        }?.trim() ?: fallback
    }
}

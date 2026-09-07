package com.blinkng.shared

/**
 * Public client-side defaults for the single Blinkng Supabase project.
 *
 * The anon key is intentionally a public client credential protected by Supabase RLS;
 * privileged service-role credentials must never be placed in Android, Windows or shared code.
 */
object BlinkBackendDefaults {
    const val SUPABASE_URL = "https://jhwgifrlxwspoedxjaly.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"

    fun resolve(candidate: String?, fallback: String): String =
        candidate
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.contains("placeholder", ignoreCase = true) }
            ?: fallback
}

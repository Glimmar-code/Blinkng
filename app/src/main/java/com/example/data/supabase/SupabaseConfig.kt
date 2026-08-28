package com.example.data.supabase

import com.example.BuildConfig

object SupabaseConfig {
    // Configured with the user's live Supabase project
    val url: String = try {
        BuildConfig.SUPABASE_URL.ifBlank { "https://jhwgifrlxwspoedxjaly.supabase.co" }
    } catch (_: Throwable) {
        "https://jhwgifrlxwspoedxjaly.supabase.co"
    }

    val anonKey: String = try {
        BuildConfig.SUPABASE_ANON_KEY.ifBlank {
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"
        }
    } catch (_: Throwable) {
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Impod2dpZnJseHdzcG9lZHhqYWx5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExMjQ3NDUsImV4cCI6MjA5NjcwMDc0NX0.-R9ITzT_lTptU8VuzRTy8co_ZZeegsUp5YkDJg1fITk"
    }
}

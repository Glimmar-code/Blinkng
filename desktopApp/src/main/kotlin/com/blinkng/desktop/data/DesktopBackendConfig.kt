package com.blinkng.desktop.data

import com.blinkng.shared.BlinkBackendDefaults

/** Windows adapter for the same public Supabase client configuration used by Android. */
object DesktopBackendConfig {
    val url: String = BlinkBackendDefaults.resolve(
        candidate = System.getenv("SUPABASE_URL") ?: System.getenv("VITE_SUPABASE_URL"),
        fallback = BlinkBackendDefaults.SUPABASE_URL,
    )

    val anonKey: String = BlinkBackendDefaults.resolve(
        candidate = System.getenv("SUPABASE_ANON_KEY") ?: System.getenv("VITE_SUPABASE_ANON_KEY"),
        fallback = BlinkBackendDefaults.SUPABASE_ANON_KEY,
    )
}

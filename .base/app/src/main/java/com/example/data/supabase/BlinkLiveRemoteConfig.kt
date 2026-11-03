package com.example.data.supabase

import com.blinkng.shared.live.BlinkLiveConfigPolicy
import com.blinkng.shared.live.BlinkLiveSetting
import com.blinkng.shared.live.BlinkLiveSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Read-only public content. On error keep the prior state, not a blank update. */
object BlinkLiveRemoteConfig {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun load(): BlinkLiveSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = SupabaseConfig.url.trimEnd('/') +
                "/rest/v1/blink_live_config?select=key,value&enabled=eq.true"
            val request = Request.Builder().url(endpoint)
                .get()
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer " + SupabaseConfig.anonKey)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string() ?: return@use null
                val rows = JSONArray(raw)
                val settings = buildList {
                    for (i in 0 until rows.length()) {
                        val item = rows.optJSONObject(i) ?: continue
                        val value = item.optJSONObject("value") ?: continue
                        val fields = mutableMapOf<String, String>()
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val entry = value.opt(key)
                            if (entry is String || entry is Boolean) fields[key] = entry.toString()
                        }
                        add(BlinkLiveSetting(item.optString("key"), fields))
                    }
                }
                BlinkLiveConfigPolicy.evaluate(settings)
            }
        }.getOrNull()
    }
}

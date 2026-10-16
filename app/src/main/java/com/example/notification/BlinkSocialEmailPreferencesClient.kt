package com.example.notification

import android.content.Context
import com.blinkng.shared.notification.BlinkSocialEmailCategory
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Server-synchronized opt-in preferences. No optimistic local-only consent:
 * a checkbox counts as enabled only after the Supabase write succeeds.
 * We never silently enable email alerts when a setting/endpoint is missing.
 */
object BlinkSocialEmailPreferencesClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun load(context: Context): Result<Map<BlinkSocialEmailCategory, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                SupabaseService.initialize(context.applicationContext)
                val token = SupabaseService.accessToken() ?: error("Sign in to manage email alerts.")
                val userId = SupabaseService().getCurrentUserId() ?: error("Sign in to manage email alerts.")
                val select = BlinkSocialEmailCategory.allColumns.joinToString(",")
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/blink_social_email_preferences?user_id=eq.${userId}&select=${select}")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Email alert settings are currently unavailable (${response.code}).")
                    val row = JSONArray(response.body?.string().orEmpty()).optJSONObject(0)
                    BlinkSocialEmailCategory.entries.associateWith { type ->
                        row?.optBoolean(type.column, false) ?: false
                    }
                }
            }
        }

    suspend fun update(
        context: Context,
        type: BlinkSocialEmailCategory,
        enabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            SupabaseService.initialize(context.applicationContext)
            val token = SupabaseService.accessToken() ?: error("Sign in to manage email alerts.")
            val userId = SupabaseService().getCurrentUserId() ?: error("Sign in to manage email alerts.")
            val payload = JSONObject()
                .put("user_id", userId)
                .put(type.column, enabled)
                .toString().toRequestBody(jsonType)
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/blink_social_email_preferences?on_conflict=user_id")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Couldn't save email preferences (${response.code}).")
            }
        }
    }
}

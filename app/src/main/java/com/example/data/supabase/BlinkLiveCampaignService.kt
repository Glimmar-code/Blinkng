package com.example.data.supabase

import com.blinkng.shared.live.BlinkLiveCampaign
import com.blinkng.shared.live.BlinkLiveCampaignPolicy
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Campaigns are published by audited admin RPCs, never by client-side flags. */
object BlinkLiveCampaignService {
    private val http=OkHttpClient.Builder()
        .connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS).build()
    private val media="application/json; charset=utf-8".toMediaType()
    private val base=SupabaseConfig.url.trimEnd('/')
    private val key=SupabaseConfig.anonKey

    suspend fun load(placement: String): List<BlinkLiveCampaign>? = withContext(Dispatchers.IO) {
        if (placement !in BlinkLiveCampaignPolicy.placements) return@withContext emptyList()
        runCatching {
            val token=SupabaseService.accessToken()?.takeIf { it.isNotBlank() } ?: key
            val body=JSONObject().put("p_placement",placement)
                .put("p_app_version",BuildConfig.VERSION_CODE)
            val request=Request.Builder().url("$base/rest/v1/rpc/get_blink_live_campaigns")
                .header("apikey",key).header("Authorization","Bearer $token")
                .post(body.toString().toRequestBody(media)).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw=response.body?.string() ?: return@use null
                val rows=JSONArray(raw)
                val result=buildList {
                    for (i in 0 until rows.length()) {
                        val j=rows.optJSONObject(i) ?: continue
                        val campaign=BlinkLiveCampaign(
                            id=j.optString("id"),kind=j.optString("kind"),
                            title=j.optString("title"),message=j.optString("message"),
                            imageUrl=j.optString("image_url").takeIf { it.isNotBlank() && it!="null" },
                            actionLabel=j.optString("action_label").takeIf { it.isNotBlank() && it!="null" },
                            actionRoute=j.optString("action_route").takeIf { it.isNotBlank() && it!="null" },
                            style=j.optString("style","standard"),accent=j.optString("accent","neutral"),
                            pinned=j.optBoolean("pinned"),priority=j.optInt("priority",0),
                            frequencyHours=j.optInt("frequency_hours",24),
                            endsAt=j.optString("ends_at").takeIf { it.isNotBlank() && it!="null" },
                        )
                        BlinkLiveCampaignPolicy.sanitize(campaign)?.let(::add)
                    }
                }
                BlinkLiveCampaignPolicy.displayFor(placement,result)
            }
        }.getOrNull()
    }

    suspend fun record(placement: String, campaignId: String, event: String): Boolean =
        withContext(Dispatchers.IO) {
            if (placement !in BlinkLiveCampaignPolicy.placements ||
                event !in setOf("impression","click","dismiss")) return@withContext false
            runCatching {
                val token=SupabaseService.accessToken()?.takeIf { it.isNotBlank() }
                    ?: return@runCatching false
                val body=JSONObject().put("p_id",campaignId).put("p_placement",placement)
                    .put("p_app_version",BuildConfig.VERSION_CODE).put("p_event",event)
                val request=Request.Builder()
                    .url("$base/rest/v1/rpc/record_blink_live_campaign_event")
                    .header("apikey",key).header("Authorization","Bearer $token")
                    .post(body.toString().toRequestBody(media)).build()
                http.newCall(request).execute().use { response ->
                    response.isSuccessful && response.body?.string()?.trim()=="true"
                }
            }.getOrDefault(false)
        }
}

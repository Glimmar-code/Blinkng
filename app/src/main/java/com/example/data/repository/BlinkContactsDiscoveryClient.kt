package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.blinkng.shared.BlinkPhonePolicy
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
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

/** Consent-based adapter: does not collect call history or contact names. */
class BlinkContactsDiscoveryClient {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val url = SupabaseConfig.url.trimEnd('/') + "/functions/v1/blink-contact-discovery"
    private val jsonType = "application/json".toMediaType()

    data class Consent(
        val syncEnabled: Boolean = false,
        val discoverable: Boolean = false,
        val joinAlerts: Boolean = false,
        val lastSyncedAt: String = ""
    )
    data class Snapshot(
        val consent: Consent,
        val people: List<UserProfile>,
        val mutualMatchingReady: Boolean = false
    )

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val token = requireNotNull(SupabaseService.accessToken()) { "Sign in to use contact discovery." }
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("apikey", SupabaseConfig.anonKey)
            .post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val label = when (response.code) {
                    409 -> "Your profile is not eligible for contact discovery yet. You can still sync contacts."
                    403 -> "Enable contact syncing first."
                    429 -> "You synced recently. Try again later."
                    503, 404 -> "Contact discovery is not available yet."
                    else -> "Contact discovery failed (" + response.code + ")."
                }
                throw IllegalStateException(label)
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun parsePeople(array: JSONArray?): List<UserProfile> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { n ->
            val item = array.optJSONObject(n) ?: return@mapNotNull null
            val id = item.optString("user_id")
            if (id.isBlank()) return@mapNotNull null
            UserProfile(
                id = id, username = item.optString("username"),
                fullName = item.optString("full_name"),
                avatarUrl = item.optString("avatar_url"),
                verificationBadge = runCatching {
                    VerificationBadge.valueOf(item.optString("verification_badge").uppercase())
                }.getOrDefault(VerificationBadge.NONE)
            )
        }

    private fun consent(body: JSONObject?): Consent = Consent(
        syncEnabled = body?.optBoolean("sync_enabled") == true,
        discoverable = body?.optBoolean("discoverable") == true,
        joinAlerts = body?.optBoolean("join_alerts") == true,
        lastSyncedAt = body?.optString("last_synced_at").orEmpty().takeUnless { it == "null" }.orEmpty()
    )

    suspend fun get(): Snapshot {
        val result = post(JSONObject().put("action", "get"))
        return Snapshot(
            consent(result.optJSONObject("settings")),
            parsePeople(result.optJSONArray("matches")),
            mutualMatchingReady = result.optString("match_policy") == "reciprocal_opt_in_v1"
        )
    }

    suspend fun set(value: Consent): Consent {
        val result = post(JSONObject().put("action", "settings")
            .put("sync_enabled", value.syncEnabled)
            .put("discoverable", value.discoverable)
            .put("join_alerts", value.joinAlerts))
        return consent(result.optJSONObject("settings"))
    }

    suspend fun delete(): Unit { post(JSONObject().put("action", "delete")) }

    suspend fun syncFromDevice(context: Context): List<UserProfile> = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) { "Contacts permission was not granted." }
        val numbers = LinkedHashSet<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        // Null means the provider failed to supply a result, NOT zero contacts.
        // Never replace a previously synced address book with an empty import here.
        val cursor = context.contentResolver.query(uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)
            ?: throw IllegalStateException("Could not read device contacts. Try again.")
        cursor.use { cursor ->
            val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (index >= 0) {
                while (cursor.moveToNext() && numbers.size < 500) {
                    val canonical = BlinkPhonePolicy.normalize(cursor.getString(index) ?: "")
                    if (canonical != null) numbers.add(canonical)
                }
            }
        }
        if (numbers.isEmpty()) {
            throw IllegalStateException("No usable phone numbers were found. Add contacts with country codes and retry.")
        }
        // Never persist raw numbers or contact names; only HTTPS to the opted-in matching service.
        val array = JSONArray()
        numbers.forEach { array.put(it) }
        val result = post(JSONObject().put("action", "sync").put("numbers", array))
        parsePeople(result.optJSONArray("matched"))
    }
}

package com.example.notification

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BlinkFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "BlinkFCM"

        /** Call after a real Supabase login so a token generated earlier is not lost. */
        fun syncCurrentToken(context: Context) {
            val firebaseApp = FirebaseApp.initializeApp(context.applicationContext)
                ?: run {
                    Log.i(TAG, "Firebase is not configured; skipping FCM token sync.")
                    return
                }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    context.getSharedPreferences("blink_push", Context.MODE_PRIVATE).edit().putString("fcm_token", token).apply()
                    CoroutineScope(Dispatchers.IO).launch { syncTokenNow(context, token) }
                }
                .addOnFailureListener { Log.w(TAG, "Unable to obtain FCM token", it) }
        }

        private fun syncTokenNow(context: Context, token: String) {
            try {
                SupabaseService.initialize(context.applicationContext)
                SupabaseService().getCurrentUserId() ?: return
                val accessToken = SupabaseService.accessToken() ?: return
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val body = JSONObject()
                    .put("p_token", token)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/register_my_fcm_token")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    // PostgREST RPC endpoints are invoked with POST. PATCH silently prevented
                    // device tokens from ever being registered, which disabled background push.
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "FCM token sync failed: ${response.code} ${response.body?.string().orEmpty().take(240)}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token sync error", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("blink_push", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        CoroutineScope(Dispatchers.IO).launch { syncTokenNow(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Blink"
        val body = data["body"] ?: message.notification?.body ?: "You have a new notification."
        val type = data["type"] ?: "social"
        val sender = data["sender_username"].orEmpty()
        val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }
        val senderAvatar = data["sender_avatar"].orEmpty()
        when {
            type.equals("message", ignoreCase = true) && sender.isNotBlank() -> {
                CoroutineScope(Dispatchers.IO).launch {
                    BlinkNotificationHelper.showChatMessageNotification(
                        this@BlinkFirebaseMessagingService,
                        sender,
                        senderName,
                        body,
                        senderAvatar
                    )
                }
            }

            type.equals("market", ignoreCase = true) -> {
                BlinkNotificationHelper.showMarketNotification(
                    context = this,
                    title = title,
                    body = body,
                    targetMarketId = data["market_id"]
                )
            }

            else -> {
                BlinkNotificationHelper.showSocialNotification(
                    context = this,
                    title = title,
                    body = body,
                    targetPostId = data["post_id"]
                )
            }
        }
    }
}

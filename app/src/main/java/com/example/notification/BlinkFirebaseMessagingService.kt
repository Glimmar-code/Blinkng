package com.example.notification

import android.content.Context
import android.util.Log
import com.example.BuildConfig
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
        private const val PUSH_PREFS = "blink_push"
        private const val TOKEN_KEY = "fcm_token"

        /**
         * Fetches the current FCM token, prints it in debug Logcat for Firebase Console
         * testing, stores it locally, and registers it with Supabase when a real user
         * session is available.
         */
        fun syncCurrentToken(context: Context) {
            val appContext = context.applicationContext
            FirebaseApp.initializeApp(appContext)
                ?: run {
                    Log.i(TAG, "Firebase is not configured; add app/google-services.json to enable FCM.")
                    return
                }

            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isBlank()) {
                        Log.w(TAG, "Firebase returned an empty FCM token.")
                        return@addOnSuccessListener
                    }

                    saveToken(appContext, token)
                    logToken("Current FCM token", token)
                    CoroutineScope(Dispatchers.IO).launch {
                        syncTokenNow(appContext, token)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to obtain FCM token", error)
                }
        }

        private fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, token)
                .apply()
        }

        private fun logToken(label: String, token: String) {
            if (BuildConfig.DEBUG) {
                // Full token is intentionally logged only in debug builds so it can be
                // copied into Firebase Console -> Send test message.
                Log.d(TAG, "$label: $token")
            } else {
                Log.i(TAG, "$label refreshed (${token.take(8)}…)")
            }
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
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(
                            TAG,
                            "FCM token sync failed: ${response.code} ${responseBody.take(240)}"
                        )
                    } else {
                        Log.d(TAG, "FCM token registered with Supabase.")
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "FCM token sync error", error)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return

        saveToken(applicationContext, token)
        logToken("New FCM token", token)
        CoroutineScope(Dispatchers.IO).launch {
            syncTokenNow(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        Log.d(
            TAG,
            "FCM message received from=${message.from} dataKeys=${data.keys.joinToString()} hasNotification=${message.notification != null}"
        )

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

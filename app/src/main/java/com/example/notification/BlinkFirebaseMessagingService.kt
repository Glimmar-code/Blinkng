package com.example.notification

import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
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
    companion object { private const val TAG = "BlinkFCM" }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("blink_push", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
        CoroutineScope(Dispatchers.IO).launch { syncToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Blink"
        val body = data["body"] ?: message.notification?.body ?: "You have a new notification."
        val type = data["type"] ?: "social"
        val sender = data["sender_username"].orEmpty()
        val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }

        if (type.equals("message", ignoreCase = true) && sender.isNotBlank()) {
            BlinkNotificationHelper.showChatMessageNotification(this, sender, senderName, body)
        } else {
            BlinkNotificationHelper.showGenericNotification(
                context = this,
                channelId = BlinkNotificationHelper.CHANNEL_SOCIAL,
                title = title,
                body = body,
                notificationId = (System.currentTimeMillis() % 2000000000L).toInt()
            )
        }
    }

    private fun syncToken(token: String) {
        try {
            val uid = SupabaseService().getCurrentUserId() ?: return
            val accessToken = SupabaseService.accessToken() ?: return
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
            val body = JSONObject().put("fcm_token", token).toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/profiles?id=eq.$uid")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.w(TAG, "FCM token sync failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM token sync error", e)
        }
    }
}

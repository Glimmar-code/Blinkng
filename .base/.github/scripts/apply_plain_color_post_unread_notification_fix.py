from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# Create Post: explicit Plain Text vs Color Text modes.
# -----------------------------------------------------------------------------
create_path = ROOT / 'app/src/main/java/com/example/ui/components/CreatePostSheet.kt'
create = create_path.read_text(encoding='utf-8')

create = replace_once(
    create,
    '    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }\n',
    '    var selectedTextStyle by rememberSaveable { mutableStateOf("aurora") }\n    var textPresentation by rememberSaveable { mutableStateOf("plain") }\n',
    'text presentation state'
)

create = replace_once(
    create,
    '            selectedTextStyle.takeIf { cleanText.isNotBlank() && selectedImages.isEmpty() && selectedVideo == null && !pollValid }\n',
    '            selectedTextStyle.takeIf {\n                textPresentation == "color" &&\n                    cleanText.isNotBlank() &&\n                    selectedImages.isEmpty() &&\n                    selectedVideo == null &&\n                    !pollValid\n            }\n',
    'submit only selected color style'
)

create = replace_once(
    create,
    '                textStyle = selectedTextStyle.takeIf { text.isNotBlank() && selectedImages.isEmpty() && selectedVideo == null && !pollValid }\n',
    '                textStyle = selectedTextStyle.takeIf {\n                    textPresentation == "color" &&\n                        text.isNotBlank() &&\n                        selectedImages.isEmpty() &&\n                        selectedVideo == null &&\n                        !pollValid\n                }\n',
    'draft only selected color style'
)

old_text_only = '''                val textOnlyComposer = selectedImages.isEmpty() && selectedVideo == null && !showPoll
                if (textOnlyComposer) {
                    ColoredTextComposer(
                        text = text,
                        onTextChanged = { if (it.length <= 5000) text = it },
                        selectedStyleKey = selectedTextStyle,
                        onStyleSelected = { selectedTextStyle = it },
                        enabled = !isSubmitting
                    )
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 5000) text = it },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        minLines = 4,
                        maxLines = 10,
                        placeholder = {
                            Text(
                                if (selectedVideo != null) "Write a caption for your reel..."
                                else "Say something about this post..."
                            )
                        },
                        supportingText = {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    if (selectedImages.isNotEmpty()) "${selectedImages.size}/10 photos selected"
                                    else "Be clear, useful, and respectful.",
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${text.length}/5000")
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
'''

new_text_only = '''                val textOnlyComposer = selectedImages.isEmpty() && selectedVideo == null && !showPoll
                if (textOnlyComposer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = textPresentation == "plain",
                            onClick = { textPresentation = "plain" },
                            enabled = !isSubmitting,
                            label = { Text("Plain text") }
                        )
                        FilterChip(
                            selected = textPresentation == "color",
                            onClick = { textPresentation = "color" },
                            enabled = !isSubmitting,
                            label = { Text("Color text") }
                        )
                    }

                    if (textPresentation == "color") {
                        ColoredTextComposer(
                            text = text,
                            onTextChanged = { if (it.length <= 5000) text = it },
                            selectedStyleKey = selectedTextStyle,
                            onStyleSelected = { selectedTextStyle = it },
                            enabled = !isSubmitting
                        )
                    } else {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= 5000) text = it },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            minLines = 7,
                            maxLines = 14,
                            placeholder = { Text("What's on your mind?") },
                            supportingText = {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        "Plain text post",
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("${text.length}/5000")
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 5000) text = it },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        minLines = 4,
                        maxLines = 10,
                        placeholder = {
                            Text(
                                if (selectedVideo != null) "Write a caption for your reel..."
                                else "Say something about this post..."
                            )
                        },
                        supportingText = {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    if (selectedImages.isNotEmpty()) "${selectedImages.size}/10 photos selected"
                                    else "Be clear, useful, and respectful.",
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${text.length}/5000")
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
'''
create = replace_once(create, old_text_only, new_text_only, 'plain/color text composer')

create = replace_once(
    create,
    '                                    selectedTextStyle = draft.textStyle ?: "aurora"\n                                    showPoll = false\n',
    '                                    textPresentation = if (draft.textStyle.isNullOrBlank()) "plain" else "color"\n                                    selectedTextStyle = draft.textStyle ?: "aurora"\n                                    showPoll = false\n',
    'draft restores text presentation'
)
create_path.write_text(create, encoding='utf-8')


# -----------------------------------------------------------------------------
# Keep Create Post open until Supabase confirms success. Failure preserves draft.
# -----------------------------------------------------------------------------
main_path = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
main = main_path.read_text(encoding='utf-8')
main = replace_once(
    main,
    '''                onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText, textStyle ->
                    // Dismiss immediately; publishing stays in the ViewModel/background flow.
                    viewModel.openCreatePost(false)
                    viewModel.addPost(
''',
    '''                onSubmitPost = { text, faculty, imageUri, videoUri, tags, mentions, poll, isReel, audience, category, location, linkUrl, allowComments, hideLikes, isPinned, isDisappearing, audioTitle, altText, textStyle ->
                    // Keep the composer alive until Supabase confirms the save. The ViewModel
                    // closes it only after success, so a failed publish keeps the user's draft.
                    viewModel.addPost(
''',
    'do not dismiss create post before server success'
)
main_path.write_text(main, encoding='utf-8')


# -----------------------------------------------------------------------------
# Shared per-account message notification ledger.
# -----------------------------------------------------------------------------
ledger_path = ROOT / 'app/src/main/java/com/example/notification/MessageNotificationLedger.kt'
ledger_path.write_text('''package com.example.notification

import android.content.Context
import com.example.data.supabase.SupabaseService

/**
 * Prevents the same direct message from alerting once via FCM and again via
 * realtime/recovery. The server remains the authority for read/unread state;
 * this ledger only removes duplicate delivery paths on the current install.
 */
object MessageNotificationLedger {
    private const val PREFS = "blink_message_notification_ledger"
    private const val MAX_IDS = 500
    private val lock = Any()

    fun claim(context: Context, messageId: String): Boolean {
        val cleanId = messageId.trim()
        if (cleanId.isBlank()) return true

        val userKey = SupabaseService().getCurrentUserId().orEmpty().ifBlank { "anonymous" }
        val key = "notified_$userKey"
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        synchronized(lock) {
            val existing = prefs.getString(key, "")
                .orEmpty()
                .split('|')
                .filter(String::isNotBlank)
            if (cleanId in existing) return false

            val updated = (existing.takeLast(MAX_IDS - 1) + cleanId).joinToString("|")
            prefs.edit().putString(key, updated).apply()
            return true
        }
    }
}
''', encoding='utf-8')


# -----------------------------------------------------------------------------
# FCM fast path: claim message ID before posting a notification.
# -----------------------------------------------------------------------------
instant_path = ROOT / 'app/src/main/java/com/example/notification/InstantChatNotification.kt'
instant = instant_path.read_text(encoding='utf-8')
instant = replace_once(
    instant,
    '''        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        BlinkNotificationHelper.createNotificationChannels(context)

        val stableConversationKey = conversationId.ifBlank { senderUsername }
''',
    '''        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        if (!MessageNotificationLedger.claim(context, messageId)) return
        BlinkNotificationHelper.createNotificationChannels(context)

        val stableConversationKey = conversationId.ifBlank { senderUsername }
''',
    'instant notification dedupe'
)
instant_path.write_text(instant, encoding='utf-8')


# -----------------------------------------------------------------------------
# Realtime local notification path: same message-ID ledger.
# -----------------------------------------------------------------------------
helper_path = ROOT / 'app/src/main/java/com/example/notification/BlinkNotificationHelper.kt'
helper = helper_path.read_text(encoding='utf-8')
helper = replace_once(
    helper,
    '''        messageText: String,
        senderAvatar: String = ""
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)
''',
    '''        messageText: String,
        senderAvatar: String = "",
        messageId: String = ""
    ) {
        if (!hasNotificationPermission(context)) return
        if (!MessageNotificationLedger.claim(context, messageId)) return
        createNotificationChannels(context)
''',
    'helper notification message id dedupe'
)
helper_path.write_text(helper, encoding='utf-8')

vm_path = ROOT / 'app/src/main/java/com/example/viewmodel/BlinkViewModel.kt'
vm = vm_path.read_text(encoding='utf-8')
vm = replace_once(
    vm,
    '''                        displayName,
                        enriched.text,
                        avatar
                    )
''',
    '''                        displayName,
                        enriched.text,
                        avatar,
                        enriched.id
                    )
''',
    'realtime notification passes message id'
)
vm_path.write_text(vm, encoding='utf-8')


# -----------------------------------------------------------------------------
# Recovery: derive chat alerts from actual unread messages, not notification rows.
# Preserve normal social-notification recovery separately.
# -----------------------------------------------------------------------------
worker_path = ROOT / 'app/src/main/java/com/example/notification/NotificationSyncWorker.kt'
worker_path.write_text('''package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gap recovery after reconnect/reinstall.
 *
 * Direct-message alerts are reconstructed only from messages that are still unread
 * for this account on Supabase. Previously read messages therefore never re-alert just
 * because local app storage or a notification cursor was lost.
 */
class NotificationSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun doWork(): Result {
        SupabaseService.initialize(applicationContext)
        val token = SupabaseService.accessToken() ?: return Result.success()
        val uid = SupabaseService().getCurrentUserId() ?: return Result.success()

        return try {
            recoverUnreadMessages(token)
            recoverSocialNotifications(token, uid)
            Result.success()
        } catch (_: UnauthorizedException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun recoverUnreadMessages(token: String) {
        val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/get_my_unread_message_notifications"
        val body = JSONObject().put("p_limit", 200).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) error("Unread message recovery failed (${response.code})")

            val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            // Avoid a wall of alerts after a long offline period: show the newest unread
            // message per conversation. The unread count/history remains visible in the app.
            val newestByConversation = linkedMapOf<String, JSONObject>()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val messageId = row.optString("message_id")
                val conversationId = row.optString("conversation_id").ifBlank { messageId }
                if (messageId.isBlank() || conversationId.isBlank()) continue
                newestByConversation[conversationId] = row
            }

            newestByConversation.values.forEach { row ->
                val senderUsername = row.optString("sender_username")
                if (senderUsername.isBlank()) return@forEach
                InstantChatNotification.show(
                    context = applicationContext,
                    senderUsername = senderUsername,
                    senderName = row.optString("sender_name").ifBlank { senderUsername },
                    messageText = row.optString("content").ifBlank { "New message" },
                    senderAvatar = row.optString("sender_avatar"),
                    conversationId = row.optString("conversation_id"),
                    messageId = row.optString("message_id")
                )
            }
        }
    }

    private fun recoverSocialNotifications(token: String, uid: String) {
        val prefs = applicationContext.getSharedPreferences("blink_notification_sync", Context.MODE_PRIVATE)
        val cursorKey = "last_social_created_at_$uid"
        val lastSeen = prefs.getString(cursorKey, "") ?: ""
        val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/notifications?select=*&is_read=eq.false&order=created_at.asc&limit=1000"
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) error("Social notification recovery failed (${response.code})")
            val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            var newest = lastSeen
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val created = row.optString("created_at")
                if (created.isNotBlank() && (newest.isBlank() || created > newest)) newest = created
                if (lastSeen.isNotBlank() && created <= lastSeen) continue

                val title = row.optString("text", "Blink notification")
                // Message rows are deliberately ignored here. Their authoritative read state
                // comes from public.messages via get_my_unread_message_notifications().
                if (title.contains(" sent you a message", ignoreCase = true)) continue

                BlinkNotificationHelper.showSocialNotification(
                    applicationContext,
                    title,
                    row.optString("sub_text", ""),
                    row.optString("post_id").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            prefs.edit().putString(cursorKey, newest).apply()
        }
    }

    private class UnauthorizedException : Exception()
}
''', encoding='utf-8')


# -----------------------------------------------------------------------------
# Every authenticated token sync schedules a one-shot unread gap reconciliation.
# -----------------------------------------------------------------------------
fcm_path = ROOT / 'app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt'
fcm = fcm_path.read_text(encoding='utf-8')
fcm = replace_once(
    fcm,
    '''                    CoroutineScope(Dispatchers.IO).launch {
                        syncTokenNow(appContext, token)
                    }
                }
''',
    '''                    CoroutineScope(Dispatchers.IO).launch {
                        syncTokenNow(appContext, token)
                    }
                    enqueueGapSync(appContext)
                }
''',
    'schedule unread gap sync after authenticated token sync'
)
fcm = replace_once(
    fcm,
    '''        CoroutineScope(Dispatchers.IO).launch {
            syncTokenNow(applicationContext, token)
        }
    }
''',
    '''        CoroutineScope(Dispatchers.IO).launch {
            syncTokenNow(applicationContext, token)
        }
        enqueueGapSync(applicationContext)
    }
''',
    'schedule unread gap sync after token refresh'
)
fcm_path.write_text(fcm, encoding='utf-8')

print('Applied plain/color post mode, reliable publish flow, and unread-only message notifications.')

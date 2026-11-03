from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f"{label}: expected text not found")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------
# Feed: swipe left anywhere on the Home feed to open the existing 3-dot menu.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/ui/screens/FeedScreen.kt"
text = read(path)
text = replace_once(
    text,
    "import androidx.compose.foundation.clickable\n",
    "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectHorizontalDragGestures\n",
    "Feed gesture import",
)
text = replace_once(
    text,
    "import androidx.compose.ui.input.nestedscroll.nestedScroll\n",
    "import androidx.compose.ui.input.nestedscroll.nestedScroll\nimport androidx.compose.ui.input.pointer.pointerInput\n",
    "Feed pointer import",
)
text = replace_once(
    text,
    """                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),""",
    """                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                            .pointerInput(selectedTopTab) {
                                var horizontalDrag = 0f
                                val openMenuThreshold = 76.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        horizontalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (horizontalDrag <= -openMenuThreshold) {
                                            onOpenMenu()
                                        }
                                        horizontalDrag = 0f
                                    },
                                    onDragCancel = { horizontalDrag = 0f }
                                )
                            },""",
    "Feed swipe-left menu",
)
write(path, text)

# ---------------------------------------------------------------------
# App 3-dot menu: keep Experience expanded so theme mode is immediately visible.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/ui/components/AppMenuSheet.kt"
text = read(path)
text = replace_once(
    text,
    'mutableStateOf(setOf("Profile", "Marketplace", "Privacy & Security", "Session"))',
    'mutableStateOf(setOf("Profile", "Experience", "Marketplace", "Privacy & Security", "Session"))',
    "Menu theme visibility",
)
write(path, text)

# ---------------------------------------------------------------------
# Realtime: fix profile presence PATCH filter and preserve conversation_id on messages.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt"
text = read(path)
text = replace_once(
    text,
    '/rest/v1/profiles?id=$uid',
    '/rest/v1/profiles?id=eq.$uid',
    "Realtime presence filter",
)
text = replace_once(
    text,
    'ChatMessage(id = record.optString("id"), senderId = senderId.ifBlank { senderUsername },',
    'ChatMessage(id = record.optString("id"), conversationId = record.optString("conversation_id"), senderId = senderId.ifBlank { senderUsername },',
    "Realtime conversation id",
)
write(path, text)

# ---------------------------------------------------------------------
# Chat repository: after the server creates a message, securely ask the Edge Function
# to notify its recipient. Push failure never makes message delivery fail.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/data/repository/ChatRepository.kt"
text = read(path)
text = replace_once(
    text,
    """                if (messageId.isBlank() || messageId == "null") {
                    return@withContext Result.failure(Exception("Message was not created."))
                }
                Result.success(""",
    """                if (messageId.isBlank() || messageId == "null") {
                    return@withContext Result.failure(Exception("Message was not created."))
                }
                SupabaseService.accessToken()?.takeIf { it.isNotBlank() }?.let { currentToken ->
                    runCatching { triggerMessagePush(messageId, currentToken) }
                }
                Result.success(""",
    "Trigger message push",
)
marker = "\n    private suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {"
if "private fun triggerMessagePush(" not in text:
    if marker not in text:
        raise RuntimeError("Chat push helper insertion point missing")
    helper = '''

    private fun triggerMessagePush(messageId: String, accessToken: String) {
        if (messageId.isBlank() || accessToken.isBlank()) return
        val body = JSONObject()
            .put("message_id", messageId)
            .toString()
            .toRequestBody(jsonMediaType)
        client.newCall(
            Request.Builder()
                .url("${SupabaseConfig.url.trimEnd('/')}/functions/v1/send-push-notification")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute().use { /* Message delivery succeeds even if push is unavailable. */ }
    }
'''
    text = text.replace(marker, helper + marker, 1)
write(path, text)

# ---------------------------------------------------------------------
# FCM service: use the authenticated token RPC, parse avatar metadata and render chat
# notifications off the main thread.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt"
text = read(path)
text = replace_once(
    text,
    'val body = JSONObject().put("fcm_token", token).toString().toRequestBody("application/json".toMediaType())',
    'val body = JSONObject().put("p_token", token).toString().toRequestBody("application/json".toMediaType())',
    "FCM RPC body",
)
text = replace_once(
    text,
    '.url("${SupabaseConfig.url.trimEnd(\'/\')}/rest/v1/profiles?id=eq.$uid")',
    '.url("${SupabaseConfig.url.trimEnd(\'/\')}/rest/v1/rpc/register_my_fcm_token")',
    "FCM token endpoint",
)
text = replace_once(
    text,
    'val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }\n',
    'val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }\n        val senderAvatar = data["sender_avatar"].orEmpty()\n',
    "FCM sender avatar",
)
text = replace_once(
    text,
    """                BlinkNotificationHelper.showChatMessageNotification(
                    this,
                    sender,
                    senderName,
                    body
                )""",
    """                CoroutineScope(Dispatchers.IO).launch {
                    BlinkNotificationHelper.showChatMessageNotification(
                        this@BlinkFirebaseMessagingService,
                        sender,
                        senderName,
                        body,
                        senderAvatar
                    )
                }""",
    "FCM rich chat notification",
)
write(path, text)

# ---------------------------------------------------------------------
# Notification helper: sender avatar, reply shortcut and avatar-aware deep link.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/notification/BlinkNotificationHelper.kt"
text = read(path)
text = replace_once(text, "import android.graphics.Color\n", "import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.graphics.Color\n", "Notification bitmap imports")
text = replace_once(text, "import androidx.core.content.ContextCompat\n", "import androidx.core.content.ContextCompat\nimport androidx.core.graphics.drawable.IconCompat\n", "Notification icon import")
text = replace_once(text, "import kotlin.math.absoluteValue\n", "import java.net.HttpURLConnection\nimport java.net.URL\nimport kotlin.math.absoluteValue\n", "Notification URL imports")
text = replace_once(
    text,
    'const val EXTRA_PARTNER_NAME = "EXTRA_PARTNER_NAME"\n',
    'const val EXTRA_PARTNER_NAME = "EXTRA_PARTNER_NAME"\n    const val EXTRA_PARTNER_AVATAR = "EXTRA_PARTNER_AVATAR"\n',
    "Notification avatar extra",
)
text = replace_once(
    text,
    """    private fun buildChatPendingIntent(
        context: Context,
        senderUsername: String,
        senderName: String
    ): PendingIntent {""",
    """    private fun buildChatPendingIntent(
        context: Context,
        senderUsername: String,
        senderName: String,
        senderAvatar: String = ""
    ): PendingIntent {""",
    "Chat pending avatar signature",
)
text = replace_once(
    text,
    """                putExtra(
                    EXTRA_PARTNER_NAME,
                    senderName
                )""",
    """                putExtra(
                    EXTRA_PARTNER_NAME,
                    senderName
                )

                putExtra(
                    EXTRA_PARTNER_AVATAR,
                    senderAvatar
                )""",
    "Chat pending avatar extra",
)

start = text.find("    fun showChatMessageNotification(")
end_marker = "\n    // ================================================================\n    // MULTI-MESSAGE CHAT SUMMARY"
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("Chat notification function boundaries missing")
new_chat_fn = '''    private fun loadAvatarBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun showChatMessageNotification(
        context: Context,
        senderUsername: String,
        senderName: String,
        messageText: String,
        senderAvatar: String = ""
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val avatarBitmap = loadAvatarBitmap(senderAvatar)
        val personBuilder = Person.Builder()
            .setName(senderName)
            .setKey(senderUsername)
        avatarBitmap?.let { personBuilder.setIcon(IconCompat.createWithBitmap(it)) }
        val person = personBuilder.build()

        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("You").setKey("blink_self").build()
        )
            .addMessage(messageText, System.currentTimeMillis(), person)
            .setConversationTitle(senderName)
            .setGroupConversation(false)

        val chatIntent = buildChatPendingIntent(context, senderUsername, senderName, senderAvatar)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(PINK_COLOR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(chatIntent)
            .addAction(android.R.drawable.ic_menu_send, "Reply", chatIntent)
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        avatarBitmap?.let { builder.setLargeIcon(it) }

        notifySafely(
            context = context,
            id = MSG_ID_BASE + positiveHash(senderUsername) % 700,
            notification = builder.build()
        )
    }
'''
text = text[:start] + new_chat_fn + text[end:]
write(path, text)

# ---------------------------------------------------------------------
# Background sync fallback: message rows become proper chat notifications, including
# profile identity/avatar, instead of generic social notices.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/notification/NotificationSyncWorker.kt"
text = read(path)
text = replace_once(text, "import org.json.JSONArray\n", "import org.json.JSONArray\nimport org.json.JSONObject\n", "Worker JSONObject import")
old_loop = '''                    if (lastSeen.isBlank() || created > lastSeen) {
                        val title = row.optString("text", "Blink notification")
                        val body = row.optString("sub_text", "")
                        BlinkNotificationHelper.showSocialNotification(applicationContext, title, body, row.optString("post_id").takeIf { it.isNotBlank() && it != "null" })
                        newlyShown++
                    }'''
new_loop = '''                    if (lastSeen.isBlank() || created > lastSeen) {
                        val title = row.optString("text", "Blink notification")
                        val body = row.optString("sub_text", "")
                        val actorId = row.optString("actor_id")
                        val looksLikeMessage = title.contains(" sent you a message", ignoreCase = true)
                        if (looksLikeMessage && actorId.isNotBlank()) {
                            val actor = fetchActorProfile(token, actorId)
                            if (actor != null) {
                                BlinkNotificationHelper.showChatMessageNotification(
                                    applicationContext,
                                    actor.optString("username"),
                                    actor.optString("full_name").ifBlank { actor.optString("username") },
                                    body,
                                    actor.optString("avatar_url")
                                )
                            } else {
                                BlinkNotificationHelper.showSocialNotification(applicationContext, title, body)
                            }
                        } else {
                            BlinkNotificationHelper.showSocialNotification(
                                applicationContext,
                                title,
                                body,
                                row.optString("post_id").takeIf { it.isNotBlank() && it != "null" }
                            )
                        }
                        newlyShown++
                    }'''
text = replace_once(text, old_loop, new_loop, "Worker chat fallback")
if "private fun fetchActorProfile(" not in text:
    insert = '''

    private fun fetchActorProfile(accessToken: String, actorId: String): JSONObject? {
        if (actorId.isBlank()) return null
        return runCatching {
            val endpoint = "${SupabaseConfig.url.trimEnd('/')}/rest/v1/profiles?id=eq.$actorId&select=username,full_name,avatar_url&limit=1"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val rows = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                rows.optJSONObject(0)
            }
        }.getOrNull()
    }
'''
    text = text.rstrip()[:-1] + insert + "\n}\n"
write(path, text)

# ---------------------------------------------------------------------
# MainActivity: request Android 13+ notification permission, sync the token after auth,
# and carry the sender avatar through notification deep links.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/MainActivity.kt"
text = read(path)
text = replace_once(
    text,
    "import android.content.Intent\nimport android.os.Bundle\n",
    "import android.Manifest\nimport android.content.Intent\nimport android.content.pm.PackageManager\nimport android.os.Build\nimport android.os.Bundle\n",
    "Main notification Android imports",
)
text = replace_once(
    text,
    "import androidx.activity.compose.BackHandler\n",
    "import androidx.activity.compose.BackHandler\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n",
    "Main permission launcher imports",
)
text = replace_once(
    text,
    "import androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "import androidx.core.content.ContextCompat\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "Main ContextCompat import",
)
text = replace_once(
    text,
    "import com.example.notification.BlinkNotificationHelper\n",
    "import com.example.notification.BlinkNotificationHelper\nimport com.example.notification.BlinkFirebaseMessagingService\n",
    "Main FCM import",
)
text = replace_once(
    text,
    """                val username = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_USERNAME).orEmpty()
                val name = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_NAME)
                if (username.isNotBlank()) {
                    viewModel.setTab(MainTab.MESSAGES)
                    viewModel.openChatWithUser(username, name, null)
                }""",
    """                val username = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_USERNAME).orEmpty()
                val name = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_NAME)
                val avatar = intent.getStringExtra(BlinkNotificationHelper.EXTRA_PARTNER_AVATAR)
                if (username.isNotBlank()) {
                    viewModel.setTab(MainTab.MESSAGES)
                    viewModel.openChatWithUser(username, name, avatar)
                }""",
    "Main chat avatar deep link",
)
text = replace_once(
    text,
    """            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
""",
    """            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
""",
    "Main notification permission launcher",
)
text = replace_once(
    text,
    """                        avatarUrl = uiState.myProfile.avatarUrl
                    )
                }
            }""",
    """                        avatarUrl = uiState.myProfile.avatarUrl
                    )
                    BlinkFirebaseMessagingService.syncCurrentToken(this@MainActivity)
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }""",
    "Main token sync and permission",
)
write(path, text)

# ---------------------------------------------------------------------
# ViewModel: resolve incoming realtime rows by sender_id/conversation_id, show an
# in-app banner and a rich system notification, and reconcile local conversations.
# ---------------------------------------------------------------------
path = "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
text = read(path)
text = replace_once(
    text,
    "import com.example.data.supabase.MessageMediaService\n",
    "import com.example.data.supabase.MessageMediaService\nimport com.example.notification.BlinkNotificationHelper\n",
    "ViewModel notification import",
)
start = text.find("    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {")
end = text.find("\n    private fun appendMessageToState", start)
if start < 0 or end < 0:
    raise RuntimeError("Realtime message handler boundaries missing")
new_handler = '''    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val myId = supabaseService.getCurrentUserId().orEmpty()
        if (msg.isFromMe || (myId.isNotBlank() && msg.senderId == myId)) return

        viewModelScope.launch {
            val initial = _uiState.value
            var senderProfile = initial.profiles.firstOrNull { it.id == msg.senderId }
            if (senderProfile == null && msg.senderId.isNotBlank()) {
                senderProfile = try {
                    profileRepository.fetchById(msg.senderId)
                } catch (_: Exception) {
                    null
                }
            }

            var serverSummary = initial.conversations.firstOrNull {
                msg.conversationId?.let { id -> id.isNotBlank() && it.id == id } == true
            }
            if (serverSummary == null) {
                val fresh = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
                serverSummary = fresh.firstOrNull {
                    msg.conversationId?.let { id -> id.isNotBlank() && it.id == id } == true
                } ?: fresh.firstOrNull { it.partnerId == msg.senderId }
            }

            val partner = senderProfile?.username?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerUsername?.takeIf { it.isNotBlank() }
                ?: msg.senderUsername.takeIf { it.isNotBlank() }
                ?: return@launch
            val displayName = senderProfile?.fullName?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerName?.takeIf { it.isNotBlank() }
                ?: partner
            val avatar = senderProfile?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: serverSummary?.partnerAvatar.orEmpty()
            val conversationId = msg.conversationId?.takeIf { it.isNotBlank() }
                ?: serverSummary?.id
                ?: "conv_$partner"
            val enriched = msg.copy(
                conversationId = conversationId,
                senderUsername = partner,
                isFromMe = false
            )

            val latest = _uiState.value
            val active = latest.activeConversationPartner?.equals(partner, true) == true
            val conversations = latest.conversations.toMutableList()
            val index = conversations.indexOfFirst {
                it.id == conversationId || it.partnerUsername.equals(partner, true)
            }
            if (index >= 0) {
                val old = conversations[index]
                val messages = old.messages.toMutableList()
                val existing = messages.indexOfFirst { it.id == enriched.id }
                if (existing >= 0) messages[existing] = enriched else messages.add(enriched)
                conversations[index] = old.copy(
                    id = if (old.id.startsWith("local_") && !conversationId.startsWith("local_")) conversationId else old.id,
                    partnerId = old.partnerId.ifBlank { msg.senderId },
                    partnerName = if (old.partnerName.isBlank() || old.partnerName.equals(old.partnerUsername, true)) displayName else old.partnerName,
                    partnerAvatar = old.partnerAvatar.ifBlank { avatar },
                    lastMessage = enriched.text,
                    lastMessageTime = enriched.timestamp,
                    lastMessageRawTime = enriched.rawTimestamp,
                    unreadCount = if (active) 0 else old.unreadCount + 1,
                    messages = messages.distinctBy { it.id }.sortedBy { it.rawTimestamp.ifBlank { it.timestamp } }.toMutableList()
                )
            } else {
                conversations.add(
                    0,
                    (serverSummary ?: ChatConversation(
                        id = conversationId,
                        partnerUsername = partner,
                        partnerId = msg.senderId,
                        partnerName = displayName,
                        partnerAvatar = avatar
                    )).copy(
                        id = conversationId,
                        partnerUsername = partner,
                        partnerId = msg.senderId,
                        partnerName = displayName,
                        partnerAvatar = avatar,
                        lastMessage = enriched.text,
                        lastMessageTime = enriched.timestamp,
                        lastMessageRawTime = enriched.rawTimestamp,
                        unreadCount = if (active) 0 else 1,
                        messages = mutableListOf(enriched)
                    )
                )
            }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()

            if (active) {
                chatRepository.markConversationRead(partner)
            } else {
                _snackBarMessages.tryEmit("💬 $displayName: ${enriched.text.take(120)}")
                withContext(Dispatchers.IO) {
                    BlinkNotificationHelper.showChatMessageNotification(
                        appContext,
                        partner,
                        displayName,
                        enriched.text,
                        avatar
                    )
                }
            }
        }
    }
'''
text = text[:start] + new_handler + text[end:]

# Surface send failures instead of silently changing only the bubble state.
text = replace_once(
    text,
    """                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        persistConversations()
                    }""",
    """                    withContext(Dispatchers.Main) {
                        updateMessageStatusInState(item.receiverUsername, item.localId, MessageStatus.FAILED)
                        persistConversations()
                        showToast(error.message ?: "Message failed. Please try again.")
                    }""",
    "Message failure feedback",
)

# After a successful first message, replace a local placeholder conversation with the real
# server conversation id/profile summary without dropping locally rendered messages.
text = replace_once(
    text,
    """                    runCatching {
                        supabaseService.recordActivity(
                            item.receiverUsername,
                            "sent you a direct message",
                            NotificationFilter.ALL,
                            targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                            previewText = item.content,
                            targetType = "CHAT"
                        )
                    }""",
    """                    runCatching {
                        supabaseService.recordActivity(
                            item.receiverUsername,
                            "sent you a direct message",
                            NotificationFilter.ALL,
                            targetUsername = supabaseService.getCurrentUsername().orEmpty(),
                            previewText = item.content,
                            targetType = "CHAT"
                        )
                    }
                    reconcileConversationSummary(item.receiverUsername)""",
    "Conversation reconciliation call",
)
marker = "\n    private fun handleRealtimeEvent(event: RealtimeEvent) {"
if "private suspend fun reconcileConversationSummary(" not in text:
    helper = '''

    private suspend fun reconcileConversationSummary(partnerUsername: String) {
        val server = runCatching { chatRepository.fetchConversations() }.getOrDefault(emptyList())
            .firstOrNull { it.partnerUsername.equals(partnerUsername, true) }
            ?: return
        withContext(Dispatchers.Main) {
            val latest = _uiState.value
            val index = latest.conversations.indexOfFirst { it.partnerUsername.equals(partnerUsername, true) }
            if (index < 0) return@withContext
            val local = latest.conversations[index]
            val merged = server.copy(messages = local.messages)
            val conversations = latest.conversations.toMutableList().apply { this[index] = merged }
            _uiState.value = latest.copy(conversations = conversations)
            persistConversations()
        }
    }
'''
    if marker not in text:
        raise RuntimeError("Conversation helper insertion point missing")
    text = text.replace(marker, helper + marker, 1)
write(path, text)

print("Applied feed menu, messaging, FCM and notification fixes.")

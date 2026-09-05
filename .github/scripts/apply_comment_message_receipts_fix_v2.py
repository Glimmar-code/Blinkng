from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "app/src/main/java/com/example/data/models/PostModel.kt"
CHAT = ROOT / "app/src/main/java/com/example/data/repository/ChatRepository.kt"
REALTIME = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt"
FCM = ROOT / "app/src/main/java/com/example/notification/BlinkFirebaseMessagingService.kt"
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
PREMIUM = ROOT / "app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        print(f"{label}: already applied")
        return text
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    print(f"{label}: applied")
    return text.replace(old, new, 1)


def add_import(text: str, anchor: str, new_import: str, label: str) -> str:
    if new_import in text:
        print(f"{label}: already applied")
        return text
    if anchor not in text:
        raise SystemExit(f"{label}: import anchor not found")
    print(f"{label}: applied")
    return text.replace(anchor, anchor + "\n" + new_import, 1)


model = MODEL.read_text()
model = replace_once(
    model,
    '''enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}''',
    '''enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}''',
    "message receipt states",
)
MODEL.write_text(model)


chat = CHAT.read_text()
chat = replace_once(
    chat,
    '''                            isRead = o.optBoolean("is_read", false),
                            status = MessageStatus.SENT,''',
    '''                            isRead = o.optBoolean("is_read", false),
                            status = when {
                                o.optBoolean("is_read", false) ||
                                    o.optString("read_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.READ
                                o.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                                else -> MessageStatus.SENT
                            },''',
    "message page receipt mapping",
)
chat = replace_once(
    chat,
    '''    suspend fun markConversationRead(partnerUsername: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.markMessagesRead(partnerUsername)
    }
}''',
    '''    suspend fun markMessageDelivered(messageId: String): Boolean {
        val cleanId = messageId.trim()
        if (cleanId.isBlank() || cleanId.startsWith("temp_")) return false
        return callReceiptRpc(
            name = "ack_message_delivered",
            body = JSONObject().put("p_message_id", cleanId)
        )
    }

    suspend fun markConversationRead(partnerUsername: String): Boolean {
        val clean = partnerUsername.trim().removePrefix("@")
        if (clean.isBlank()) return false
        return callReceiptRpc(
            name = "mark_conversation_read",
            body = JSONObject().put("p_partner_username", clean)
        )
    }

    private suspend fun callReceiptRpc(name: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            var token = SupabaseService.accessToken() ?: return@withContext false

            fun request(currentToken: String): okhttp3.Response = client.newCall(
                Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/$name")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $currentToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute()

            var response = request(token)
            if (response.code == 401) {
                response.close()
                if (!refreshSession()) return@withContext false
                token = SupabaseService.accessToken() ?: return@withContext false
                response = request(token)
            }
            response.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}''',
    "message receipt RPCs",
)
CHAT.write_text(chat)


realtime = REALTIME.read_text()
realtime = replace_once(
    realtime,
    '''isRead = record.optBoolean("is_read", false), status = MessageStatus.SENT)))''',
    '''isRead = record.optBoolean("is_read", false), status = when {
                    record.optBoolean("is_read", false) || record.optString("read_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.READ
                    record.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                    else -> MessageStatus.SENT
                })))''',
    "realtime receipt mapping",
)
REALTIME.write_text(realtime)


fcm = FCM.read_text()
fcm = add_import(
    fcm,
    "import com.example.BuildConfig",
    "import com.example.data.repository.ChatRepository",
    "FCM chat repository import",
)
fcm = replace_once(
    fcm,
    '''        val senderAvatar = data["sender_avatar"].orEmpty()

        when {''',
    '''        val senderAvatar = data["sender_avatar"].orEmpty()
        val messageId = data["message_id"].orEmpty()

        if (type.equals("message", ignoreCase = true) && messageId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    SupabaseService.initialize(applicationContext)
                    ChatRepository().markMessageDelivered(messageId)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to acknowledge delivered message $messageId", error)
                }
            }
        }

        when {''',
    "FCM delivered acknowledgement",
)
FCM.write_text(fcm)


vm = VM.read_text()
vm = replace_once(
    vm,
    '''    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val myId = supabaseService.getCurrentUserId().orEmpty()
        if (msg.isFromMe || (myId.isNotBlank() && msg.senderId == myId)) return

        viewModelScope.launch {''',
    '''    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {
        val myId = supabaseService.getCurrentUserId().orEmpty()
        val isMine = msg.isFromMe || (myId.isNotBlank() && msg.senderId == myId)
        if (isMine) {
            val state = _uiState.value
            var changed = false
            val updated = state.conversations.map { conversation ->
                var messageChanged = false
                val messages = conversation.messages.map { existing ->
                    if (existing.id == msg.id && msg.id.isNotBlank()) {
                        messageChanged = true
                        existing.copy(status = msg.status, isRead = msg.isRead)
                    } else existing
                }.toMutableList()
                if (messageChanged) {
                    changed = true
                    conversation.copy(messages = messages)
                } else conversation
            }
            if (changed) {
                _uiState.value = state.copy(conversations = updated)
                persistConversations()
            }
            return
        }

        viewModelScope.launch {
            if (msg.id.isNotBlank()) {
                runCatching { chatRepository.markMessageDelivered(msg.id) }
            }''',
    "realtime sender receipt updates",
)
VM.write_text(vm)


main = MAIN.read_text()
main = replace_once(
    main,
    '''                    onDirectMessage = { partner -> viewModel.openChatWithUser(partner) },''',
    '''                    onDirectMessage = { partner ->
                        viewModel.closeProfile()
                        viewModel.setTab(MainTab.MESSAGES)
                        viewModel.openChatWithUser(partner, profile.fullName, profile.avatarUrl)
                    },''',
    "profile to chat navigation",
)
MAIN.write_text(main)


premium = PREMIUM.read_text()
premium = replace_once(
    premium,
    '''            val metadata = when (message.status) {
                MessageStatus.SENDING -> "Sending…"
                MessageStatus.FAILED -> "Failed • tap to retry"
                MessageStatus.SENT -> message.timestamp
            }
            Text(
                metadata,
                color = if (message.status == MessageStatus.FAILED) palette.danger else palette.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )''',
    '''            val metadata = when (message.status) {
                MessageStatus.SENDING -> "Sending…"
                MessageStatus.FAILED -> "Failed • tap to retry"
                MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.READ -> message.timestamp
            }
            val receipt = if (!isMine) "" else when (message.status) {
                MessageStatus.SENT -> "✓"
                MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                else -> ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    metadata,
                    color = if (message.status == MessageStatus.FAILED) palette.danger else palette.textSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
                if (receipt.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        receipt,
                        color = if (message.status == MessageStatus.READ) Color(0xFF3B82F6) else palette.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }''',
    "message tick UI",
)
PREMIUM.write_text(premium)

print("Comment/message receipt client patch complete.")

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/viewmodel/BlinkViewModel.kt"
REALTIME = ROOT / "app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt"
MARKER = "MESSAGE_RECEIPT_EVENT_CLASSIFICATION_V1"


def require_once(text: str, old: str, label: str) -> None:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")


def patch_vm() -> None:
    text = VM.read_text()
    if MARKER in text:
        return

    old_dispatch = "            is RealtimeEvent.MessageEvent -> handleIncomingRealtimeMessage(event.message)"
    require_once(text, old_dispatch, "MessageEvent dispatch")
    text = text.replace(
        old_dispatch,
        "            is RealtimeEvent.MessageEvent -> handleIncomingRealtimeMessage(event.eventType, event.message)",
        1,
    )

    old_sig = "    private fun handleIncomingRealtimeMessage(msg: ChatMessage) {"
    require_once(text, old_sig, "Realtime message handler signature")
    text = text.replace(
        old_sig,
        "    // MESSAGE_RECEIPT_EVENT_CLASSIFICATION_V1: only INSERT creates unread/new-message UI.\n"
        "    private fun handleIncomingRealtimeMessage(eventType: String, msg: ChatMessage) {",
        1,
    )

    old_own_copy = "                        existing.copy(status = msg.status, isRead = msg.isRead)"
    require_once(text, old_own_copy, "Own message receipt copy")
    text = text.replace(
        old_own_copy,
        '''                        existing.copy(
                            text = if (msg.text.isNotBlank() || msg.deletedForEveryone) msg.text else existing.text,
                            status = msg.status,
                            isRead = msg.isRead,
                            replyToMessageId = msg.replyToMessageId ?: existing.replyToMessageId,
                            editedAt = msg.editedAt ?: existing.editedAt,
                            deletedForEveryone = msg.deletedForEveryone
                        )''',
        1,
    )

    anchor = '''            return
        }

        viewModelScope.launch {
            if (msg.id.isNotBlank()) {'''
    require_once(text, anchor, "Incoming message insert anchor")
    replacement = '''            return
        }

        // Delivery/read/edit updates for an incoming message are state changes, not a new
        // exposure. Never increment unread count or raise another notification for UPDATE.
        if (!eventType.equals("INSERT", ignoreCase = true)) {
            val state = _uiState.value
            var changed = false
            val updated = state.conversations.map { conversation ->
                var messageChanged = false
                val messages = conversation.messages.map { existing ->
                    if (existing.id == msg.id && msg.id.isNotBlank()) {
                        messageChanged = true
                        existing.copy(
                            text = if (msg.text.isNotBlank() || msg.deletedForEveryone) msg.text else existing.text,
                            status = msg.status,
                            isRead = msg.isRead,
                            replyToMessageId = msg.replyToMessageId ?: existing.replyToMessageId,
                            editedAt = msg.editedAt ?: existing.editedAt,
                            deletedForEveryone = msg.deletedForEveryone
                        )
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
            if (msg.id.isNotBlank()) {'''
    text = text.replace(anchor, replacement, 1)
    VM.write_text(text)


def patch_realtime() -> None:
    text = REALTIME.read_text()
    if MARKER in text:
        return

    old = '''                                    record.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                                    else -> MessageStatus.SENT
                                }
                            )'''
    require_once(text, old, "Realtime ChatMessage receipt fields")
    new = '''                                    record.optString("delivered_at").let { it.isNotBlank() && !it.equals("null", true) } -> MessageStatus.DELIVERED
                                    else -> MessageStatus.SENT
                                },
                                replyToMessageId = record.optString("reply_to_message_id")
                                    .takeIf { it.isNotBlank() && !it.equals("null", true) },
                                editedAt = record.optString("edited_at")
                                    .takeIf { it.isNotBlank() && !it.equals("null", true) },
                                deletedForEveryone = record.optBoolean("deleted_for_everyone", false),
                                isVoiceNote = record.optString("message_type").let {
                                    it.equals("voice", true) || it.equals("audio", true)
                                }
                            )'''
    text = text.replace(old, new, 1)

    marker_anchor = "    private fun handleIncomingMessage(text: String) {"
    require_once(text, marker_anchor, "Realtime handler marker")
    text = text.replace(
        marker_anchor,
        "    // MESSAGE_RECEIPT_EVENT_CLASSIFICATION_V1\n" + marker_anchor,
        1,
    )
    REALTIME.write_text(text)


def main() -> None:
    patch_vm()
    patch_realtime()
    print("Message receipt event classification fix applied.")


if __name__ == "__main__":
    main()

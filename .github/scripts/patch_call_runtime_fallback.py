from pathlib import Path

CALL_ACTIVITY = Path('app/src/main/java/com/example/call/CallActivity.kt')
REALTIME = Path('app/src/main/java/com/example/data/supabase/SupabaseRealtimeManager.kt')
VIEWMODEL = Path('app/src/main/java/com/example/viewmodel/BlinkViewModel.kt')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)

# 1) Preserve an Answer tap that arrives through onNewIntent while the call row is still loading.
text = CALL_ACTIVITY.read_text()
text = replace_once(
    text,
    '    private var connectedMarked = false\n    private var isCaller = false\n',
    '    private var connectedMarked = false\n    private var answerRequested = false\n    private var isCaller = false\n',
    'CallActivity answer flag field',
)
text = replace_once(
    text,
    '''    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        if (intent.action == ACTION_ANSWER && incomingRinging) answerIncoming()\n    }\n''',
    '''    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        if (intent.action == ACTION_ANSWER) {\n            // The notification Answer action can arrive before loadCall() has populated\n            // incomingRinging. Remember the user's tap and consume it as soon as the\n            // authoritative call row finishes loading instead of silently dropping it.\n            answerRequested = true\n            if (incomingRinging) answerIncoming()\n        }\n    }\n''',
    'CallActivity onNewIntent',
)
text = replace_once(
    text,
    '''        incomingRinging = !isCaller && loaded.status == CallStatus.RINGING\n        statusText = when {\n''',
    '''        incomingRinging = !isCaller && loaded.status == CallStatus.RINGING\n        if (sourceIntent.action == ACTION_ANSWER) answerRequested = true\n        statusText = when {\n''',
    'CallActivity load answer intent',
)
text = replace_once(
    text,
    '''        when {\n            sourceIntent.action == ACTION_ANSWER && incomingRinging -> answerIncoming()\n            isCaller -> requestMediaPermissions(incomingAnswer = false)\n''',
    '''        when {\n            answerRequested && incomingRinging -> answerIncoming()\n            isCaller -> requestMediaPermissions(incomingAnswer = false)\n''',
    'CallActivity answer branch',
)
text = replace_once(
    text,
    '''    private fun answerIncoming() {\n        if (!incomingRinging || ending.get()) return\n        statusText = "Connecting…"\n''',
    '''    private fun answerIncoming() {\n        if (!incomingRinging || ending.get()) return\n        answerRequested = false\n        statusText = "Connecting…"\n''',
    'CallActivity answer consume',
)
CALL_ACTIVITY.write_text(text)

# 2) Surface incoming calls through the existing authenticated app Realtime socket as a
#    foreground fallback when FCM delivery/token registration is stale.
text = REALTIME.read_text()
text = replace_once(
    text,
    '    data class FeedPostEvent(val eventType: String, val postId: String) : RealtimeEvent()\n    data class ConnectHubEvent(val eventType: String, val table: String) : RealtimeEvent()\n',
    '''    data class FeedPostEvent(val eventType: String, val postId: String) : RealtimeEvent()\n    data class IncomingCallEvent(\n        val eventType: String,\n        val callId: String,\n        val callerId: String,\n        val calleeId: String,\n        val callType: String,\n        val conversationId: String\n    ) : RealtimeEvent()\n    data class ConnectHubEvent(val eventType: String, val table: String) : RealtimeEvent()\n''',
    'Realtime incoming event model',
)
text = replace_once(
    text,
    '"study_circles","study_circle_members","roommate_profiles"',
    '"study_circles","study_circle_members","calls","roommate_profiles"',
    'Realtime calls subscription',
)
text = replace_once(
    text,
    '''                "feed_posts" -> publishEvent(RealtimeEvent.FeedPostEvent(type, record.optString("id")))\n                "roommate_profiles", "roommate_applications", "mentor_profiles", "mentor_requests",\n''',
    '''                "feed_posts" -> publishEvent(RealtimeEvent.FeedPostEvent(type, record.optString("id")))\n                "calls" -> {\n                    val calleeId = record.optString("callee_id")\n                    val status = record.optString("status")\n                    if (calleeId == activeUserId && status.equals("ringing", true)) {\n                        publishEvent(\n                            RealtimeEvent.IncomingCallEvent(\n                                eventType = type,\n                                callId = record.optString("id"),\n                                callerId = record.optString("caller_id"),\n                                calleeId = calleeId,\n                                callType = record.optString("call_type", "audio"),\n                                conversationId = record.optString("conversation_id")\n                            )\n                        )\n                    }\n                }\n                "roommate_profiles", "roommate_applications", "mentor_profiles", "mentor_requests",\n''',
    'Realtime calls handler',
)
REALTIME.write_text(text)

# 3) Convert the call event into the same notification used by FCM. Same callId means Android
#    updates the existing alert when both transports deliver instead of creating duplicates.
text = VIEWMODEL.read_text()
text = replace_once(
    text,
    'import com.example.auth.PasswordRecoveryLinkParser\n',
    '''import com.example.auth.PasswordRecoveryLinkParser\nimport com.example.call.CallRepository\nimport com.example.call.CallType\nimport com.example.call.IncomingCallNotification\n''',
    'ViewModel call imports',
)
text = replace_once(
    text,
    '''            is RealtimeEvent.NotificationEvent -> fetchSupabaseData()\n            is RealtimeEvent.ConnectHubEvent -> refreshConnectHub()\n''',
    '''            is RealtimeEvent.NotificationEvent -> fetchSupabaseData()\n            is RealtimeEvent.IncomingCallEvent -> viewModelScope.launch {\n                val currentUserId = _uiState.value.myProfile.id\n                if (\n                    currentUserId.isBlank() ||\n                    event.calleeId != currentUserId ||\n                    event.callId.isBlank() ||\n                    event.callerId.isBlank()\n                ) return@launch\n\n                val peer = CallRepository().fetchPeer(event.callerId).getOrNull()\n                IncomingCallNotification.showIncoming(\n                    context = appContext,\n                    callId = event.callId,\n                    callType = CallType.fromWire(event.callType),\n                    peerId = event.callerId,\n                    peerUsername = peer?.username.orEmpty(),\n                    peerName = peer?.name.orEmpty().ifBlank { "Blink user" },\n                    peerAvatar = peer?.avatar.orEmpty(),\n                    conversationId = event.conversationId\n                )\n            }\n            is RealtimeEvent.ConnectHubEvent -> refreshConnectHub()\n''',
    'ViewModel incoming call handler',
)
VIEWMODEL.write_text(text)

# Guardrails: fail the workflow if a future source shape makes the patch incomplete.
assert 'answerRequested && incomingRinging' in CALL_ACTIVITY.read_text()
assert '"calls" -> {' in REALTIME.read_text()
assert 'RealtimeEvent.IncomingCallEvent' in VIEWMODEL.read_text()
print('Native call runtime fallback patch applied successfully.')

package com.example.call

import org.json.JSONObject

enum class CallType(val wireValue: String) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun fromWire(value: String?): CallType =
            if (value.equals("video", ignoreCase = true)) VIDEO else AUDIO
    }
}

enum class CallStatus(val wireValue: String) {
    RINGING("ringing"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    DECLINED("declined"),
    BUSY("busy"),
    MISSED("missed"),
    CANCELLED("cancelled"),
    ENDED("ended"),
    FAILED("failed");

    val isTerminal: Boolean
        get() = this in setOf(DECLINED, BUSY, MISSED, CANCELLED, ENDED, FAILED)

    companion object {
        fun fromWire(value: String?): CallStatus =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: FAILED
    }
}

data class BlinkCall(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val type: CallType,
    val status: CallStatus,
    val createdAt: String = "",
    val ringingAt: String = "",
    val answeredAt: String? = null,
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val timeoutAt: String = "",
    val endReason: String? = null,
    val durationSeconds: Int = 0
) {
    fun otherUserId(currentUserId: String): String =
        if (currentUserId == callerId) calleeId else callerId

    companion object {
        fun fromJson(json: JSONObject): BlinkCall = BlinkCall(
            id = json.optString("id"),
            conversationId = json.optString("conversation_id"),
            callerId = json.optString("caller_id"),
            calleeId = json.optString("callee_id"),
            type = CallType.fromWire(json.optString("call_type")),
            status = CallStatus.fromWire(json.optString("status")),
            createdAt = json.optString("created_at"),
            ringingAt = json.optString("ringing_at"),
            answeredAt = json.optNullableString("answered_at"),
            connectedAt = json.optNullableString("connected_at"),
            endedAt = json.optNullableString("ended_at"),
            timeoutAt = json.optString("timeout_at"),
            endReason = json.optNullableString("end_reason"),
            durationSeconds = json.optInt("duration_seconds", 0)
        )
    }
}

data class CallSignal(
    val id: Long,
    val callId: String,
    val senderId: String,
    val kind: String,
    val payload: JSONObject,
    val createdAt: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject): CallSignal = CallSignal(
            id = json.optLong("id"),
            callId = json.optString("call_id"),
            senderId = json.optString("sender_id"),
            kind = json.optString("kind"),
            payload = json.optJSONObject("payload") ?: JSONObject(),
            createdAt = json.optString("created_at")
        )
    }
}

data class CallPeer(
    val id: String,
    val username: String = "",
    val name: String = "Blink user",
    val avatar: String = ""
)

private fun JSONObject.optNullableString(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

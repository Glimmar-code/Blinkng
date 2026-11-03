package com.blinkng.shared.communication

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Shared Supabase REST/RPC adapter compiled by both Android and Windows.
 * It never accepts a service-role key. All requests use the signed-in user's JWT + publishable/anon key.
 */
class BlinkCommunicationApi(
    baseUrl: String,
    private val apiKey: String,
    private val accessTokenProvider: () -> String?,
    private val currentUserIdProvider: () -> String?,
    private val refreshSession: (() -> Boolean)? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val root = baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Draft(
        val conversationId: String,
        val deviceId: String,
        val content: String,
        val replyToMessageId: String? = null,
        val attachmentDraftJson: String = "[]",
    )

    fun sendMessage(request: IdempotentMessageRequest): SentMessageIdentity {
        requireUuid(request.clientMessageId, "clientMessageId")
        val receiver = MessageTextRules.normalizeUsername(request.receiverUsername)
        val content = MessageTextRules.normalizeMessage(request.content)
        require(receiver.isNotBlank()) { "Recipient is required" }
        require(MessageTextRules.isSendable(content)) { "Message is empty or too long" }

        val v3Body = JSONObject()
            .put("p_receiver_username", receiver)
            .put("p_content", content)
            .put("p_client_message_id", request.clientMessageId)
            .put("p_reply_to_message_id", request.replyToMessageId ?: JSONObject.NULL)
        var response = rpc("send_message_v3", v3Body)
        var legacy = false

        // Safe staged rollout: old production backends continue using v2 until the new migration lands.
        if (response.status == 404) {
            legacy = true
            response = rpc(
                "send_message_v2",
                JSONObject().put("p_receiver_username", receiver).put("p_content", content),
            )
        }
        response.requireSuccess("Unable to send message")

        val rows = JSONArray(response.body.ifBlank { "[]" })
        if (rows.length() == 0) throw CommunicationApiException("Message identity was not returned", response.status)
        val row = rows.getJSONObject(0)
        val identity = SentMessageIdentity(
            messageId = row.optString("message_id"),
            conversationId = row.optString("conversation_id"),
            createdAt = row.optString("created_at"),
            sequenceNo = row.optLong("sequence_no").takeIf { row.has("sequence_no") && !row.isNull("sequence_no") },
            usedLegacyFallback = legacy,
        )
        if (identity.messageId.isBlank() || identity.conversationId.isBlank()) {
            throw CommunicationApiException("Message confirmation was incomplete", response.status)
        }

        if (legacy && !request.replyToMessageId.isNullOrBlank()) {
            rpc(
                "set_message_reply",
                JSONObject()
                    .put("p_message_id", identity.messageId)
                    .put("p_reply_to_message_id", request.replyToMessageId),
            ).requireSuccess("Message sent, but reply reference could not be attached")
        }
        return identity
    }

    fun startCall(request: IdempotentCallRequest): JSONObject {
        requireUuid(request.conversationId, "conversationId")
        requireUuid(request.calleeId, "calleeId")
        requireUuid(request.clientCallId, "clientCallId")
        require(request.callType in setOf("audio", "video")) { "callType must be audio or video" }

        var response = rpc(
            "start_call_v2",
            JSONObject()
                .put("p_conversation_id", request.conversationId)
                .put("p_callee_id", request.calleeId)
                .put("p_call_type", request.callType)
                .put("p_client_call_id", request.clientCallId),
        )
        if (response.status == 404) {
            response = rpc(
                "start_call",
                JSONObject()
                    .put("p_conversation_id", request.conversationId)
                    .put("p_callee_id", request.calleeId)
                    .put("p_call_type", request.callType),
            )
        }
        response.requireSuccess("Unable to start call")
        return parseRpcObject(response.body)
    }

    fun heartbeatPresence(conversationId: String, deviceId: String, state: PresenceState, ttlSeconds: Int = 30): Boolean {
        requireUuid(conversationId, "conversationId")
        require(deviceId.isNotBlank()) { "deviceId is required" }
        val response = rpc(
            "upsert_conversation_presence",
            JSONObject()
                .put("p_conversation_id", conversationId)
                .put("p_device_id", deviceId.take(200))
                .put("p_state", state.wireValue)
                .put("p_ttl_seconds", ttlSeconds.coerceIn(5, CommunicationRetryPolicy.maxPresenceTtlSeconds)),
        )
        response.requireSuccess("Unable to update presence")
        return response.booleanResult()
    }

    fun acknowledgeReceipt(messageId: String, deviceId: String, read: Boolean): Boolean {
        requireUuid(messageId, "messageId")
        require(deviceId.isNotBlank()) { "deviceId is required" }
        val response = rpc(
            "ack_message_receipt",
            JSONObject()
                .put("p_message_id", messageId)
                .put("p_device_id", deviceId.take(200))
                .put("p_read", read),
        )
        response.requireSuccess("Unable to acknowledge message")
        return response.booleanResult()
    }

    fun updatePrivacy(settings: ChatPrivacySettings): Boolean {
        val response = rpc(
            "update_chat_privacy",
            JSONObject()
                .put("p_who_can_message", settings.whoCanMessage.wireValue)
                .put("p_who_can_call", settings.whoCanCall.wireValue)
                .put("p_who_can_group_invite", settings.whoCanGroupInvite.wireValue)
                .put("p_show_online", settings.showOnline)
                .put("p_show_last_seen", settings.showLastSeen)
                .put("p_send_read_receipts", settings.sendReadReceipts)
                .put("p_show_typing", settings.showTyping)
                .put("p_show_recording", settings.showRecording)
                .put("p_show_profile_photo_in_chat", settings.showProfilePhotoInChat)
                .put("p_allow_link_previews", settings.allowLinkPreviews)
                .put("p_notification_preview", settings.notificationPreview.wireValue),
        )
        response.requireSuccess("Unable to update chat privacy")
        return response.booleanResult()
    }

    fun saveDraft(draft: Draft): Boolean {
        requireUuid(draft.conversationId, "conversationId")
        require(draft.deviceId.isNotBlank()) { "deviceId is required" }
        val userId = currentUserIdProvider()?.takeIf { it.isNotBlank() }
            ?: throw CommunicationApiException("No signed-in user", 401)
        val payload = JSONObject()
            .put("conversation_id", draft.conversationId)
            .put("user_id", userId)
            .put("device_id", draft.deviceId.take(200))
            .put("content", draft.content.take(MessageTextRules.maxMessageCharacters))
            .put("reply_to_message_id", draft.replyToMessageId ?: JSONObject.NULL)
            .put("attachment_draft", parseJsonArray(draft.attachmentDraftJson))
            .put("updated_at", java.time.Instant.now().toString())
        val response = request(
            method = "POST",
            path = "/rest/v1/conversation_drafts?on_conflict=conversation_id,user_id,device_id",
            body = payload.toString(),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        response.requireSuccess("Unable to save draft")
        return true
    }

    fun deleteDraft(conversationId: String, deviceId: String): Boolean {
        requireUuid(conversationId, "conversationId")
        val userId = currentUserIdProvider()?.takeIf { it.isNotBlank() }
            ?: throw CommunicationApiException("No signed-in user", 401)
        val response = request(
            method = "DELETE",
            path = "/rest/v1/conversation_drafts?conversation_id=eq.$conversationId&user_id=eq.$userId&device_id=eq.${urlValue(deviceId.take(200))}",
            body = null,
        )
        response.requireSuccess("Unable to clear draft")
        return true
    }

    fun updateNotificationPreferences(conversationId: String, preferences: ConversationNotificationPreferences): Boolean {
        requireUuid(conversationId, "conversationId")
        val userId = currentUserIdProvider()?.takeIf { it.isNotBlank() }
            ?: throw CommunicationApiException("No signed-in user", 401)
        val payload = JSONObject()
            .put("conversation_id", conversationId)
            .put("user_id", userId)
            .put("messages_enabled", preferences.messagesEnabled)
            .put("calls_enabled", preferences.callsEnabled)
            .put("mentions_only", preferences.mentionsOnly)
            .put("preview_mode", preferences.previewMode)
            .put("sound_key", preferences.soundKey ?: JSONObject.NULL)
            .put("ringtone_key", preferences.ringtoneKey ?: JSONObject.NULL)
            .put("vibration_enabled", preferences.vibrationEnabled)
            .put("updated_at", java.time.Instant.now().toString())
        val response = request(
            "POST",
            "/rest/v1/conversation_notification_preferences?on_conflict=conversation_id,user_id",
            payload.toString(),
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        response.requireSuccess("Unable to update conversation notifications")
        return true
    }

    fun createGroupInvite(conversationId: String, expiresAt: String? = null, maxUses: Int? = null): String {
        requireUuid(conversationId, "conversationId")
        val response = rpc(
            "create_group_invite",
            JSONObject()
                .put("p_conversation_id", conversationId)
                .put("p_expires_at", expiresAt ?: JSONObject.NULL)
                .put("p_max_uses", maxUses ?: JSONObject.NULL),
        )
        response.requireSuccess("Unable to create group invite")
        return response.scalarString().also { requireUuid(it, "invite token") }
    }

    fun acceptGroupInvite(token: String): String {
        requireUuid(token, "invite token")
        val response = rpc("accept_group_invite", JSONObject().put("p_token", token))
        response.requireSuccess("Unable to accept group invite")
        return response.scalarString()
    }

    fun respondGroupJoinRequest(requestId: String, approve: Boolean): Boolean {
        requireUuid(requestId, "requestId")
        val response = rpc(
            "respond_group_join_request",
            JSONObject().put("p_request_id", requestId).put("p_approve", approve),
        )
        response.requireSuccess("Unable to resolve group join request")
        return response.booleanResult()
    }

    fun setGroupMemberRole(conversationId: String, targetUserId: String, role: ConversationRole): Boolean {
        require(role != ConversationRole.OWNER) { "Use transferGroupOwnership to assign owner" }
        val response = rpc(
            "set_group_member_role",
            JSONObject()
                .put("p_conversation_id", conversationId)
                .put("p_target_user_id", targetUserId)
                .put("p_role", role.wireValue),
        )
        response.requireSuccess("Unable to update group role")
        return response.booleanResult()
    }

    fun transferGroupOwnership(conversationId: String, newOwnerId: String): Boolean {
        val response = rpc(
            "transfer_group_ownership",
            JSONObject().put("p_conversation_id", conversationId).put("p_new_owner_id", newOwnerId),
        )
        response.requireSuccess("Unable to transfer group ownership")
        return response.booleanResult()
    }

    fun leaveGroup(conversationId: String): Boolean {
        val response = rpc("leave_group_conversation", JSONObject().put("p_conversation_id", conversationId))
        response.requireSuccess("Unable to leave group")
        return response.booleanResult()
    }

    fun inviteCallParticipant(callId: String, userId: String): Boolean {
        val response = rpc(
            "invite_call_participant",
            JSONObject().put("p_call_id", callId).put("p_user_id", userId),
        )
        response.requireSuccess("Unable to invite call participant")
        return response.booleanResult()
    }

    fun recordCallQuality(callId: String, deviceId: String, sample: CallQualitySnapshot): Long {
        val metadata = runCatching { JSONObject(sample.metadataJson) }.getOrElse { JSONObject() }
        val response = rpc(
            "record_call_quality_sample",
            JSONObject()
                .put("p_call_id", callId)
                .put("p_device_id", deviceId.take(200))
                .put("p_connection_state", sample.connectionState)
                .put("p_network_type", sample.networkType ?: JSONObject.NULL)
                .put("p_round_trip_ms", sample.roundTripMs ?: JSONObject.NULL)
                .put("p_jitter_ms", sample.jitterMs ?: JSONObject.NULL)
                .put("p_packets_lost", sample.packetsLost.coerceAtLeast(0))
                .put("p_packets_received", sample.packetsReceived.coerceAtLeast(0))
                .put("p_audio_bitrate_bps", sample.audioBitrateBps.coerceAtLeast(0))
                .put("p_video_bitrate_bps", sample.videoBitrateBps.coerceAtLeast(0))
                .put("p_frames_per_second", sample.framesPerSecond ?: JSONObject.NULL)
                .put("p_frame_width", sample.frameWidth ?: JSONObject.NULL)
                .put("p_frame_height", sample.frameHeight ?: JSONObject.NULL)
                .put("p_frames_dropped", sample.framesDropped.coerceAtLeast(0))
                .put("p_local_candidate_type", sample.localCandidateType ?: JSONObject.NULL)
                .put("p_remote_candidate_type", sample.remoteCandidateType ?: JSONObject.NULL)
                .put("p_turn_used", sample.turnUsed)
                .put("p_quality_score", sample.qualityScore)
                .put("p_metadata", metadata),
        )
        response.requireSuccess("Unable to record call quality")
        return response.scalarLong()
    }

    fun recordCallNetworkEvent(
        callId: String,
        deviceId: String,
        event: CallNetworkEvent,
        fromValue: String? = null,
        toValue: String? = null,
        metadataJson: String = "{}",
    ): Long {
        val response = rpc(
            "record_call_network_event",
            JSONObject()
                .put("p_call_id", callId)
                .put("p_device_id", deviceId.take(200))
                .put("p_event_type", event.wireValue)
                .put("p_from_value", fromValue ?: JSONObject.NULL)
                .put("p_to_value", toValue ?: JSONObject.NULL)
                .put("p_metadata", runCatching { JSONObject(metadataJson) }.getOrElse { JSONObject() }),
        )
        response.requireSuccess("Unable to record call network event")
        return response.scalarLong()
    }

    fun callQualitySummary(callId: String): JSONObject {
        val response = rpc("get_call_quality_summary", JSONObject().put("p_call_id", callId))
        response.requireSuccess("Unable to load call quality summary")
        val array = JSONArray(response.body.ifBlank { "[]" })
        return if (array.length() > 0) array.getJSONObject(0) else JSONObject()
    }

    private data class WireResponse(val status: Int, val body: String) {
        fun requireSuccess(fallback: String) {
            if (status in 200..299) return
            val detail = runCatching {
                val json = JSONObject(body)
                json.optString("message").ifBlank { json.optString("error_description") }.ifBlank { json.optString("error") }
            }.getOrDefault("")
            throw CommunicationApiException(detail.ifBlank { "$fallback ($status)" }, status)
        }

        fun booleanResult(): Boolean = body.trim().trim('"').equals("true", ignoreCase = true)
        fun scalarString(): String = body.trim().trim('"')
        fun scalarLong(): Long = body.trim().trim('"').toLongOrNull() ?: 0L
    }

    private fun rpc(name: String, payload: JSONObject): WireResponse =
        request("POST", "/rest/v1/rpc/$name", payload.toString())

    private fun request(
        method: String,
        path: String,
        body: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): WireResponse {
        fun execute(token: String): WireResponse {
            val builder = Request.Builder()
                .url("$root$path")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
            extraHeaders.forEach { (name, value) -> builder.addHeader(name, value) }
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
                "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonType))
                "DELETE" -> if (body == null) builder.delete() else builder.delete(body.toRequestBody(jsonType))
                "GET" -> builder.get()
                else -> error("Unsupported method $method")
            }
            return client.newCall(builder.build()).execute().use { response ->
                WireResponse(response.code, response.body?.string().orEmpty())
            }
        }

        var token = accessTokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw CommunicationApiException("No authenticated Supabase session", 401)
        var response = execute(token)
        if (response.status == 401 && refreshSession?.invoke() == true) {
            token = accessTokenProvider()?.takeIf { it.isNotBlank() }
                ?: throw CommunicationApiException("Session refresh returned no access token", 401)
            response = execute(token)
        }
        return response
    }

    private fun parseRpcObject(body: String): JSONObject {
        val value = body.trim()
        if (value.startsWith("[")) {
            val array = JSONArray(value)
            if (array.length() == 0) return JSONObject()
            return array.getJSONObject(0)
        }
        return JSONObject(value.ifBlank { "{}" })
    }

    private fun parseJsonArray(raw: String): JSONArray = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

    private fun requireUuid(value: String, label: String) {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "$label must be a UUID" }
    }

    private fun urlValue(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

class CommunicationApiException(message: String, val statusCode: Int) : RuntimeException(message)

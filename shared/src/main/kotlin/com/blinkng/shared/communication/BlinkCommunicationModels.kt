package com.blinkng.shared.communication

import kotlin.math.roundToInt

/** Platform-neutral contracts compiled into both Android and Windows. */
enum class ChatPrivacyMode(val wireValue: String) {
    EVERYONE("everyone"), FOLLOWERS("followers"), MUTUALS("mutuals"), CONNECTIONS("connections"), NOBODY("nobody")
}

enum class NotificationPreviewMode(val wireValue: String) {
    FULL("full"), SENDER_ONLY("sender_only"), HIDDEN("hidden")
}

enum class ConversationRole(val wireValue: String) {
    OWNER("owner"), ADMIN("admin"), MEMBER("member")
}

enum class PresenceState(val wireValue: String) {
    ONLINE("online"), TYPING("typing"), RECORDING("recording"), UPLOADING("uploading")
}

enum class MessageAttachmentKind(val wireValue: String) {
    IMAGE("image"), VIDEO("video"), AUDIO("audio"), VOICE("voice"), DOCUMENT("document"),
    GIF("gif"), STICKER("sticker"), CONTACT("contact"), LOCATION("location"), POST("post"),
    REEL("reel"), PROFILE("profile"), MARKETPLACE("marketplace"), GIFT("gift")
}

enum class DeliveryState { QUEUED, SENDING, SENT, DELIVERED, READ, FAILED, PERMANENT_FAILURE }

enum class CallNetworkEvent(val wireValue: String) {
    NETWORK_CHANGED("network_changed"), ICE_RESTART("ice_restart"), RECONNECTING("reconnecting"),
    RECONNECTED("reconnected"), TURN_FALLBACK("turn_fallback"), AUDIO_ROUTE_CHANGED("audio_route_changed"),
    BACKGROUNDED("backgrounded"), FOREGROUNDED("foregrounded"), MEDIA_INTERRUPTED("media_interrupted"),
    MEDIA_RESUMED("media_resumed")
}

data class ChatPrivacySettings(
    val whoCanMessage: ChatPrivacyMode = ChatPrivacyMode.EVERYONE,
    val whoCanCall: ChatPrivacyMode = ChatPrivacyMode.EVERYONE,
    val whoCanGroupInvite: ChatPrivacyMode = ChatPrivacyMode.EVERYONE,
    val showOnline: Boolean = true,
    val showLastSeen: Boolean = true,
    val sendReadReceipts: Boolean = true,
    val showTyping: Boolean = true,
    val showRecording: Boolean = true,
    val showProfilePhotoInChat: Boolean = true,
    val allowLinkPreviews: Boolean = true,
    val notificationPreview: NotificationPreviewMode = NotificationPreviewMode.FULL,
)

data class ConversationNotificationPreferences(
    val messagesEnabled: Boolean = true,
    val callsEnabled: Boolean = true,
    val mentionsOnly: Boolean = false,
    val previewMode: String = "inherit",
    val soundKey: String? = null,
    val ringtoneKey: String? = null,
    val vibrationEnabled: Boolean = true,
)

data class IdempotentMessageRequest(
    val receiverUsername: String,
    val content: String,
    val clientMessageId: String,
    val replyToMessageId: String? = null,
)

data class SentMessageIdentity(
    val messageId: String,
    val conversationId: String,
    val createdAt: String,
    val sequenceNo: Long? = null,
    val usedLegacyFallback: Boolean = false,
)

data class IdempotentCallRequest(
    val conversationId: String,
    val calleeId: String,
    val callType: String,
    val clientCallId: String,
)

data class AttachmentDescriptor(
    val kind: MessageAttachmentKind,
    val displayName: String?,
    val mimeType: String?,
    val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val sha256: String? = null,
)

data class AttachmentValidation(
    val accepted: Boolean,
    val reason: String? = null,
)

data class CallQualitySnapshot(
    val connectionState: String,
    val networkType: String?,
    val roundTripMs: Double?,
    val jitterMs: Double?,
    val packetsLost: Long,
    val packetsReceived: Long,
    val audioBitrateBps: Long = 0,
    val videoBitrateBps: Long = 0,
    val framesPerSecond: Double? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val framesDropped: Long = 0,
    val localCandidateType: String? = null,
    val remoteCandidateType: String? = null,
    val turnUsed: Boolean = false,
    val metadataJson: String = "{}",
) {
    val packetLossPercent: Double
        get() {
            val total = packetsLost + packetsReceived
            return if (total <= 0) 0.0 else packetsLost.toDouble() * 100.0 / total.toDouble()
        }

    val qualityScore: Double get() = CallQualityScorer.score(this)
}

object CallQualityScorer {
    /** 0-100 user-experience score intended for product telemetry, not a network standard. */
    fun score(snapshot: CallQualitySnapshot): Double {
        var score = 100.0
        val rtt = snapshot.roundTripMs ?: 0.0
        val jitter = snapshot.jitterMs ?: 0.0
        val loss = snapshot.packetLossPercent

        score -= when {
            rtt <= 150 -> 0.0
            rtt <= 300 -> (rtt - 150) / 15.0
            else -> 10.0 + ((rtt - 300) / 20.0).coerceAtMost(20.0)
        }
        score -= (jitter / 5.0).coerceAtMost(20.0)
        score -= (loss * 4.0).coerceAtMost(40.0)
        if (snapshot.connectionState.equals("failed", true)) score -= 40.0
        if (snapshot.connectionState.equals("disconnected", true)) score -= 20.0
        return score.coerceIn(0.0, 100.0)
    }

    fun label(score: Double): String = when {
        score >= 90 -> "Excellent"
        score >= 75 -> "Good"
        score >= 55 -> "Fair"
        score >= 35 -> "Poor"
        else -> "Very poor"
    }
}

object CommunicationRetryPolicy {
    const val maxMessageAttempts = 6
    const val maxCallReconnectAttempts = 5
    const val maxPresenceTtlSeconds = 120

    fun delayMillis(attempt: Int): Long {
        if (attempt <= 0) return 0
        val capped = attempt.coerceAtMost(8)
        return (500L shl (capped - 1)).coerceAtMost(60_000L)
    }

    fun shouldDeadLetter(attempt: Int): Boolean = attempt >= maxMessageAttempts
}

object AttachmentPolicy {
    const val maxBytes: Long = 512L * 1024L * 1024L
    private val blockedExtensions = setOf(
        "apk", "aab", "exe", "msi", "bat", "cmd", "com", "scr", "ps1", "vbs", "js", "jar", "dll", "sh"
    )
    private val blockedMimes = setOf(
        "application/x-msdownload", "application/x-dosexec", "application/x-sh", "application/x-executable"
    )

    fun validate(item: AttachmentDescriptor): AttachmentValidation {
        if (item.byteSize < 0) return AttachmentValidation(false, "Invalid attachment size")
        if (item.byteSize > maxBytes) return AttachmentValidation(false, "Attachment is larger than 512 MB")
        val extension = item.displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (extension in blockedExtensions) return AttachmentValidation(false, "Executable files are not allowed in chat")
        if (item.mimeType?.lowercase() in blockedMimes) return AttachmentValidation(false, "Executable files are not allowed in chat")
        if (item.width != null && item.width <= 0) return AttachmentValidation(false, "Invalid media width")
        if (item.height != null && item.height <= 0) return AttachmentValidation(false, "Invalid media height")
        if (item.durationMs != null && item.durationMs < 0) return AttachmentValidation(false, "Invalid media duration")
        return AttachmentValidation(true)
    }
}

enum class LinkRisk { SAFE_HTTPS, INSECURE_HTTP, UNSUPPORTED_SCHEME, SUSPICIOUS_HOST, INVALID }

object LinkSafety {
    private val suspiciousHostTokens = setOf("xn--", "@", "%00")

    fun classify(raw: String): LinkRisk {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 4096) return LinkRisk.INVALID
        val scheme = value.substringBefore(':', "").lowercase()
        if (scheme !in setOf("http", "https")) return LinkRisk.UNSUPPORTED_SCHEME
        val hostPart = value.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        if (hostPart.isBlank()) return LinkRisk.INVALID
        if (suspiciousHostTokens.any { hostPart.contains(it) }) return LinkRisk.SUSPICIOUS_HOST
        return if (scheme == "https") LinkRisk.SAFE_HTTPS else LinkRisk.INSECURE_HTTP
    }
}

object MessageTextRules {
    const val maxMessageCharacters = 20_000
    const val maxReportCharacters = 500

    fun normalizeUsername(value: String): String = value.trim().removePrefix("@").lowercase()
    fun normalizeMessage(value: String): String = value.trim().take(maxMessageCharacters)
    fun isSendable(value: String): Boolean = value.any { !it.isWhitespace() } && value.length <= maxMessageCharacters

    fun estimatedReadingSeconds(value: String): Int {
        val words = value.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return ((words / 3.3).roundToInt()).coerceAtLeast(if (words > 0) 1 else 0)
    }
}

package com.blinkng.shared.notification

/** Shared Android + Windows authoring rules. The server rechecks all authorization. */
enum class BlinkAnnouncementDelivery(val label: String) {
    IN_APP("In-app only"),
    PUSH("Phone notification"),
    BOTH("Phone + in-app"),
}

enum class BlinkAnnouncementAudience(val label: String) {
    EVERYONE("Everyone"),
    USER("Selected user"),
    UNIVERSITY("University"),
}

data class BlinkAdminAnnouncementDraft(
    val title: String,
    val body: String,
    val delivery: BlinkAnnouncementDelivery = BlinkAnnouncementDelivery.IN_APP,
    val audience: BlinkAnnouncementAudience = BlinkAnnouncementAudience.EVERYONE,
    val userId: String? = null,
    val university: String? = null,
    val sendLater: Boolean = false,
) {
    fun formattedMessage(): String =
        listOf(title.trim(), body.trim()).filter(String::isNotEmpty).joinToString(" — ")

    fun validate(): String? {
        if (title.isBlank() || title.length > 120) return "Enter an announcement title (up to 120 characters)."
        if (body.isBlank() || body.length > 1_600) return "Enter a message (up to 1,600 characters)."
        if (formattedMessage().length > 2_000) return "Announcement exceeds 2,000 characters."
        when (audience) {
            BlinkAnnouncementAudience.EVERYONE -> Unit
            BlinkAnnouncementAudience.USER -> {
                if (!userId.orEmpty().matches(Regex("[0-9a-fA-F-]{36}")))
                    return "Select a BLINK user from the search results."
            }
            BlinkAnnouncementAudience.UNIVERSITY -> {
                if (university.isNullOrBlank() || university.length > 120)
                    return "Select or enter a university."
            }
        }
        return null
    }

    /** Existing server RPC supports immediate in-app notifications only. */
    fun canPublishWithExistingBackend(): Boolean =
        validate() == null && delivery == BlinkAnnouncementDelivery.IN_APP && !sendLater

    fun unavailableReason(): String? = when {
        sendLater -> "Scheduled delivery needs a verified server-side scheduler; no announcement has been queued."
        delivery != BlinkAnnouncementDelivery.IN_APP ->
            "Phone push and combined delivery require a verified server push dispatcher. They are not activated yet."
        else -> null
    }
}

package com.blinkng.shared

/**
 * User-initiated creator tools. Prompts prepare drafts for the existing BLINK AI
 * service, which may be unavailable, rate limited or billed independently.
 * Nothing is published, scheduled, purchased, or sent without confirmation.
 */
object BlinkVerifiedCreatorTools {
    val promptKinds = listOf("Post ideas", "Reel script", "Channel announcement", "Event plan")

    fun prompt(kind: String, topic: String, audience: String): String {
        val safeTopic = topic.trim().take(160).ifBlank { "my latest campus update" }
        val safeAudience = audience.trim().take(80).ifBlank { "students" }
        return when (kind) {
            "Post ideas" -> "Create 5 original social post ideas about $safeTopic for $safeAudience. " +
                "Give accessible captions and relevant hashtags. Avoid made-up facts and engagement bait."
            "Reel script" -> "Draft a concise 30-second Reel script about $safeTopic for $safeAudience. " +
                "Provide a hook, three scenes, on-screen captions and a non-misleading closing."
            "Channel announcement" -> "Draft a clear BLINK Channel announcement about $safeTopic " +
                "for $safeAudience. Include summary, date/venue placeholders and a factual next step. " +
                "Do not invent event times or claim official approval."
            "Event plan" -> "Prepare a safe, accessible event-planning checklist for $safeTopic, " +
                "aimed at $safeAudience. Include venue approval, schedule, capacity, accessibility, " +
                "moderation, privacy and emergency contact planning. Do not invent confirmations."
            else -> throw IllegalArgumentException("Unknown creator prompt")
        }
    }

    /** Private draft preview; venue/date are supplied by the organizer, not guessed. */
    fun eventInvite(title: String, whenText: String, venue: String, audience: String): String {
        require(title.trim().length in 3..120) { "Add a clear event name" }
        require(whenText.trim().isNotBlank()) { "Add a confirmed event date and time" }
        require(venue.trim().isNotBlank()) { "Add a confirmed venue or safe online meeting format" }
        return buildString {
            appendLine("BLINK Event · ${title.trim().take(120)}")
            appendLine("When: ${whenText.trim().take(120)}")
            appendLine("Where: ${venue.trim().take(160)}")
            if (audience.isNotBlank()) appendLine("For: ${audience.trim().take(80)}")
            appendLine("Contact the organizer for accessibility, entry and any changes.")
            appendLine("Organizer draft — confirm details before sharing. Not an RSVP ticket.")
        }.trim()
    }

    /** Public portfolio copy only; no private phone/email, fake metrics or external API. */
    fun portfolio(
        username: String, displayName: String, headline: String,
        bio: String, topTopic: String?
    ): String {
        val handle = username.trim().removePrefix("@").take(40)
        val title = displayName.trim().take(100).ifBlank { "@$handle" }
        return buildString {
            appendLine(title)
            if (handle.isNotEmpty()) appendLine("@$handle on BLINK")
            if (headline.isNotBlank()) appendLine(headline.trim().take(140))
            if (bio.isNotBlank()) appendLine(bio.trim().take(260))
            topTopic?.trim()?.take(70)?.takeIf { it.isNotEmpty() }?.let {
                appendLine("Featured topic: $it")
            }
            appendLine("Explore my BLINK profile for published work and updates.")
        }.trim()
    }
}
